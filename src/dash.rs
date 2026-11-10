//! Audio out of a DASH manifest: the addon hands over an MPD whose Dolby Atmos (E-AC-3
//! JOC) or hi-res FLAC rendition is fetched whole, decoded to stereo PCM, so the player
//! seeks and mixes it like any other file. Windows has no E-AC-3 decoder of its own and
//! symphonia doesn't carry one; filmcraft-ac3 does that decode here, symphonia the FLAC.

use std::time::Duration;

use filmcraft_ac3::{Decoder, parse_header};
use futures_util::StreamExt;
use symphonia::core::audio::SampleBuffer;
use symphonia::core::codecs::DecoderOptions;
use symphonia::core::errors::Error as SymphoniaError;
use symphonia::core::formats::FormatOptions;
use symphonia::core::io::MediaSourceStream;
use symphonia::core::meta::MetadataOptions;
use symphonia::core::probe::Hint;

const FETCH_TIMEOUT: Duration = Duration::from_secs(20);
/// A rendition bigger than this isn't a song; don't hold it in memory.
const MAX_BYTES: usize = 96 * 1024 * 1024;
const PARALLEL: usize = 6;

pub struct Audio {
    /// Interleaved stereo 16-bit PCM, little-endian.
    pub pcm: Vec<u8>,
    pub sample_rate: u32,
    pub duration_ms: u64,
    pub bitrate_kbps: u32,
    /// Bits per sample as the manifest states them (24 for hi-res FLAC).
    pub bit_depth: u32,
    pub codec: &'static str,
    pub atmos: bool,
}

/// Fetch the manifest's Dolby (E-)AC-3 or FLAC rendition and decode it to stereo PCM.
pub async fn fetch(http: &reqwest::Client, mpd_url: &str) -> anyhow::Result<Audio> {
    let text = http.get(mpd_url).timeout(FETCH_TIMEOUT).header("User-Agent", crate::hires::USER_AGENT).send().await.map_err(wire)?.error_for_status().map_err(wire)?.text().await.map_err(wire)?;
    let plan = Plan::parse(&text, mpd_url)?;
    let mut bytes = Vec::new();
    let init = get(http, &plan.init).await?;
    bytes.extend_from_slice(&init);
    let mut stream = futures_util::stream::iter(plan.segments.into_iter().map(|u| {
        let http = http.clone();
        async move { get(&http, &u).await }
    })).buffered(PARALLEL);
    while let Some(part) = stream.next().await {
        let part = part?;
        anyhow::ensure!(bytes.len() + part.len() <= MAX_BYTES, "the rendition is too large");
        bytes.extend_from_slice(&part);
    }
    let kind = plan.kind;
    let stated_rate = plan.sample_rate;
    let bit_depth = plan.bit_depth;
    let bandwidth = plan.bandwidth;
    let (pcm, sample_rate) = tokio::task::spawn_blocking(move || match kind {
        Kind::Dolby => decode(&bytes),
        Kind::Flac => decode_flac(&bytes),
    }).await??;
    anyhow::ensure!(!pcm.is_empty(), "the rendition decoded to nothing");
    let sample_rate = if sample_rate > 0 { sample_rate } else { stated_rate };
    anyhow::ensure!(sample_rate > 0, "the rendition states no sample rate");
    let duration_ms = pcm.len() as u64 / 2 * 1000 / sample_rate as u64;
    let mut pcm_bytes = Vec::with_capacity(pcm.len() * 2);
    for s in pcm {
        pcm_bytes.extend_from_slice(&s.to_le_bytes());
    }
    Ok(Audio {
        pcm: pcm_bytes,
        sample_rate,
        duration_ms,
        bitrate_kbps: bandwidth / 1000,
        bit_depth,
        codec: match kind {
            Kind::Dolby => "E-AC-3",
            Kind::Flac => "FLAC",
        },
        atmos: matches!(kind, Kind::Dolby),
    })
}

async fn get(http: &reqwest::Client, url: &str) -> anyhow::Result<Vec<u8>> {
    let r = http.get(url).timeout(FETCH_TIMEOUT).header("User-Agent", crate::hires::USER_AGENT).send().await.map_err(wire)?.error_for_status().map_err(wire)?;
    Ok(r.bytes().await.map_err(wire)?.to_vec())
}

/// Manifest and segment URLs can carry a token: a failure names its kind, never the
/// URL the reqwest error would print.
fn wire(e: reqwest::Error) -> anyhow::Error {
    if let Some(s) = e.status() {
        anyhow::anyhow!("the rendition answered HTTP {}", s.as_u16())
    } else if e.is_timeout() {
        anyhow::anyhow!("the rendition timed out")
    } else if e.is_connect() {
        anyhow::anyhow!("the rendition host didn't connect")
    } else {
        anyhow::anyhow!("the rendition download failed")
    }
}

#[derive(Clone, Copy, PartialEq, Eq)]
enum Kind {
    Dolby,
    Flac,
}

/// The rendition's segment addresses and format, read out of the MPD.
struct Plan {
    init: String,
    segments: Vec<String>,
    bandwidth: u32,
    kind: Kind,
    sample_rate: u32,
    bit_depth: u32,
}

impl Plan {
    fn parse(xml: &str, mpd_url: &str) -> anyhow::Result<Self> {
        let doc = roxmltree::Document::parse(xml)?;
        let mpd = doc.root_element();
        anyhow::ensure!(mpd.tag_name().name() == "MPD", "not a DASH manifest");
        let base = url::Url::parse(mpd_url)?;
        let period = mpd.children().find(|n| n.has_tag_name("Period")).ok_or_else(|| anyhow::anyhow!("the manifest has no period"))?;
        let period_ms = duration_attr(mpd.attribute("mediaPresentationDuration")).or_else(|| duration_attr(period.attribute("duration"))).unwrap_or(0);
        // The rendition to play: E-AC-3 (JOC) first, plain AC-3 second, hi-res FLAC last.
        let mut reps: Vec<(roxmltree::Node, roxmltree::Node)> = period.children().filter(|n| n.has_tag_name("AdaptationSet")).flat_map(|a| a.children().filter(|n| n.has_tag_name("Representation")).map(move |r| (a, r))).filter(|(a, r)| {
            let mime = r.attribute("mimeType").or_else(|| a.attribute("mimeType")).unwrap_or("");
            mime.is_empty() || mime.starts_with("audio/")
        }).collect();
        let rank = |codecs: &str| match codecs {
            c if c.contains("ec-3") => 0,
            c if c.contains("ac-3") => 1,
            c if c.contains("flac") => 2,
            _ => 3,
        };
        reps.sort_by_key(|(_, r)| rank(r.attribute("codecs").unwrap_or("")));
        let (set, rep) = reps.into_iter().find(|(_, r)| rank(r.attribute("codecs").unwrap_or("")) < 3).ok_or_else(|| anyhow::anyhow!("the manifest has no Dolby or FLAC rendition"))?;
        let codecs = rep.attribute("codecs").unwrap_or("");
        let kind = if codecs.contains("ec-3") || codecs.contains("ac-3") { Kind::Dolby } else { Kind::Flac };
        let bandwidth = rep.attribute("bandwidth").and_then(|v| v.parse().ok()).unwrap_or(0);
        // The representation id carries `NAME,samplerate,bits` when the attributes don't.
        let id_field = |n: usize| rep.attribute("id").and_then(|id| id.split(',').nth(n)).and_then(|v| v.trim().parse::<u32>().ok());
        let sample_rate = rep.attribute("audioSamplingRate").or_else(|| set.attribute("audioSamplingRate")).and_then(|v| v.parse().ok()).or_else(|| id_field(1)).unwrap_or(match kind { Kind::Dolby => 48_000, Kind::Flac => 44_100 });
        let bit_depth = rep.attribute("audioBitsPerSample").or_else(|| set.attribute("audioBitsPerSample")).and_then(|v| v.parse().ok()).or_else(|| id_field(2)).unwrap_or(16);
        // SegmentTemplate and BaseURL may sit on any level from the representation up.
        fn up<'a, 'i>(n: roxmltree::Node<'a, 'i>, tag: &str) -> Option<roxmltree::Node<'a, 'i>> {
            let mut cur = Some(n);
            while let Some(c) = cur {
                if let Some(found) = c.children().find(|k| k.has_tag_name(tag)) {
                    return Some(found);
                }
                cur = c.parent();
            }
            None
        }
        let template = up(rep, "SegmentTemplate").ok_or_else(|| anyhow::anyhow!("the rendition has no segment template"))?;
        let base = {
            let mut cur = Some(rep);
            let mut found = None;
            while let Some(c) = cur {
                if let Some(b) = c.children().find(|k| k.has_tag_name("BaseURL")).and_then(|b| b.text()) {
                    found = Some(b.trim().to_owned());
                    break;
                }
                cur = c.parent();
            }
            match found {
                Some(b) => base.join(&b)?,
                None => base,
            }
        };
        let address = |pattern: &str, number: u64, time: u64| -> anyhow::Result<String> {
            let mut s = pattern.replace("$RepresentationID$", rep.attribute("id").unwrap_or(""));
            s = s.replace("$Bandwidth$", &bandwidth.to_string());
            s = s.replace("$Time$", &time.to_string());
            // $Number$ or $Number%05d$.
            while let Some(start) = s.find("$Number") {
                let end = s[start + 1..].find('$').map(|i| start + i + 2).ok_or_else(|| anyhow::anyhow!("unclosed $Number$"))?;
                let token = &s[start + 1..end - 1];
                let width = token.split('%').nth(1).and_then(|w| w.trim_start_matches('0').trim_end_matches('d').parse::<usize>().ok());
                let text = match width {
                    Some(w) => format!("{number:0w$}"),
                    None => number.to_string(),
                };
                s.replace_range(start..end, &text);
            }
            Ok(if url::Url::parse(&s).is_ok() { s } else { base.join(&s)?.into() })
        };
        let timescale: u64 = template.attribute("timescale").and_then(|v| v.parse().ok()).unwrap_or(1);
        let start: u64 = template.attribute("startNumber").and_then(|v| v.parse().ok()).unwrap_or(1);
        let init = address(template.attribute("initialization").ok_or_else(|| anyhow::anyhow!("no initialization segment"))?, start, 0)?;
        let media = template.attribute("media").ok_or_else(|| anyhow::anyhow!("no media template"))?;
        let mut segments = Vec::new();
        if let Some(timeline) = template.children().find(|n| n.has_tag_name("SegmentTimeline")) {
            let (mut time, mut number) = (0u64, start);
            for s in timeline.children().filter(|n| n.has_tag_name("S")) {
                if let Some(t) = s.attribute("t").and_then(|v| v.parse::<u64>().ok()) {
                    time = t;
                }
                let d: u64 = s.attribute("d").and_then(|v| v.parse().ok()).ok_or_else(|| anyhow::anyhow!("a segment has no duration"))?;
                let r: i64 = s.attribute("r").and_then(|v| v.parse().ok()).unwrap_or(0);
                let count = if r < 0 {
                    // Repeats until the period ends.
                    ((period_ms as u128 * timescale as u128 / 1000).saturating_sub(time as u128) / d as u128) as u64
                } else {
                    r as u64 + 1
                };
                for _ in 0..count {
                    segments.push(address(media, number, time)?);
                    time += d;
                    number += 1;
                }
            }
        } else if let Some(d) = template.attribute("duration").and_then(|v| v.parse::<u64>().ok()) {
            let total = period_ms as u128 * timescale as u128 / 1000;
            let count = total.div_ceil(d as u128) as u64;
            anyhow::ensure!(count > 0 && count < 100_000, "the rendition's length is unknown");
            for i in 0..count {
                segments.push(address(media, start + i, i * d)?);
            }
        }
        anyhow::ensure!(!segments.is_empty(), "the rendition lists no segments");
        Ok(Self { init, segments, bandwidth, kind, sample_rate, bit_depth })
    }
}

/// `PT3M23.648S` and friends, in milliseconds.
fn duration_attr(v: Option<&str>) -> Option<u64> {
    let s = v?.strip_prefix("PT")?;
    let (mut ms, mut num) = (0u64, String::new());
    for c in s.chars() {
        match c {
            '0'..='9' | '.' => num.push(c),
            'H' => { ms += (num.parse::<f64>().ok()? * 3_600_000.0) as u64; num.clear(); }
            'M' => { ms += (num.parse::<f64>().ok()? * 60_000.0) as u64; num.clear(); }
            'S' => { ms += (num.parse::<f64>().ok()? * 1000.0) as u64; num.clear(); }
            _ => return None,
        }
    }
    Some(ms)
}

/// The E-AC-3 syncframes inside the fMP4's mdat boxes, decoded and downmixed to
/// interleaved stereo i16. Dependent substreams (the JOC objects beyond the 5.1
/// bed) carry no program of their own and are skipped.
fn decode(bytes: &[u8]) -> anyhow::Result<(Vec<i16>, u32)> {
    let mut decoder = Decoder::new();
    let mut pcm: Vec<i16> = Vec::new();
    let mut rate = 0u32;
    for payload in mdat_payloads(bytes) {
        let mut i = 0usize;
        while i + 7 <= payload.len() {
            if payload[i] != 0x0B || payload[i + 1] != 0x77 {
                i += 1;
                continue;
            }
            let Ok(h) = parse_header(&payload[i..]) else { i += 1; continue };
            if h.frame_bytes == 0 || i + h.frame_bytes > payload.len() {
                i += 1;
                continue;
            }
            let frame = &payload[i..i + h.frame_bytes];
            i += h.frame_bytes;
            if !h.is_primary() {
                continue;
            }
            match decoder.decode(frame) {
                Ok(d) => {
                    if rate == 0 {
                        rate = d.header.sample_rate;
                    }
                    downmix(&d.channels, &mut pcm);
                }
                // Enhanced coupling / AHT and the like: this copy can't play, so the
                // caller falls back to the addon's next one.
                Err(filmcraft_ac3::Error::Unsupported(why)) => anyhow::bail!("the Atmos stream uses {why}"),
                Err(_) => {}
            }
        }
    }
    anyhow::ensure!(rate > 0, "no Dolby syncframes in the rendition");
    Ok((pcm, rate))
}

/// The FLAC track inside the (fragmented) MP4, decoded to interleaved stereo i16.
fn decode_flac(bytes: &[u8]) -> anyhow::Result<(Vec<i16>, u32)> {
    let mss = MediaSourceStream::new(Box::new(std::io::Cursor::new(bytes.to_vec())), Default::default());
    let mut hint = Hint::new();
    hint.mime_type("audio/mp4");
    let probed = symphonia::default::get_probe().format(&hint, mss, &FormatOptions::default(), &MetadataOptions::default())?;
    let mut format = probed.format;
    let track = format.default_track().ok_or_else(|| anyhow::anyhow!("the FLAC rendition has no track"))?;
    let track_id = track.id;
    // Don't seed the rate from `codec_params.sample_rate`: a fragmented MP4's init often
    // states the container timescale there (48 kHz), not the FLAC's true rate, and the
    // player would then pull 44.1 kHz samples at 48 kHz — a song that plays sped up.
    // The decoded frames' spec is authoritative, so read the rate from them.
    let mut rate = 0u32;
    let mut decoder = symphonia::default::get_codecs().make(&track.codec_params, &DecoderOptions::default())?;
    let mut pcm: Vec<i16> = Vec::new();
    loop {
        let packet = match format.next_packet() {
            Ok(p) => p,
            Err(SymphoniaError::IoError(e)) if e.kind() == std::io::ErrorKind::UnexpectedEof => break,
            Err(SymphoniaError::ResetRequired) => continue,
            Err(e) => return Err(e.into()),
        };
        if packet.track_id() != track_id {
            continue;
        }
        let decoded = match decoder.decode(&packet) {
            Ok(d) => d,
            // A damaged packet shouldn't lose the rest of the song.
            Err(SymphoniaError::DecodeError(_)) => continue,
            Err(e) => return Err(e.into()),
        };
        let spec = *decoded.spec();
        // The decoded frame's own spec is the ground truth for the rate; use it over the
        // container's declared value so the PCM's length and its rate always agree.
        rate = spec.rate;
        let channels = spec.channels.count();
        let mut buf = SampleBuffer::<f32>::new(decoded.capacity() as u64, spec);
        buf.copy_interleaved_ref(decoded);
        let samples = buf.samples();
        if channels < 2 {
            for &s in samples {
                let v = (s.clamp(-1.0, 1.0) * 32767.0) as i16;
                pcm.extend_from_slice(&[v, v]);
            }
        } else {
            for frame in samples.chunks_exact(channels) {
                pcm.push((frame[0].clamp(-1.0, 1.0) * 32767.0) as i16);
                pcm.push((frame[1].clamp(-1.0, 1.0) * 32767.0) as i16);
            }
        }
    }
    anyhow::ensure!(rate > 0, "the FLAC rendition states no sample rate");
    Ok((pcm, rate))
}

/// Top-level mdat payloads of an (fragmented) MP4.
fn mdat_payloads(bytes: &[u8]) -> Vec<&[u8]> {
    let mut out = Vec::new();
    let mut i = 0usize;
    while i + 8 <= bytes.len() {
        let size = u32::from_be_bytes([bytes[i], bytes[i + 1], bytes[i + 2], bytes[i + 3]]) as usize;
        let kind = &bytes[i + 4..i + 8];
        let (header, size) = match size {
            1 if i + 16 <= bytes.len() => (16, u64::from_be_bytes(bytes[i + 8..i + 16].try_into().unwrap()) as usize),
            0 => (8, bytes.len() - i),
            _ => (8, size),
        };
        if size < header || i + size > bytes.len() {
            break;
        }
        if kind == b"mdat" {
            out.push(&bytes[i + header..i + size]);
        }
        i += size;
    }
    out
}

/// The 5.1 bed (WAV order L R C LFE Ls Rs) folded to stereo, centre and surrounds
/// at -3 dB, LFE left out; smaller layouts keep their front pair.
fn downmix(channels: &[Vec<f32>], pcm: &mut Vec<i16>) {
    let n = channels.first().map_or(0, Vec::len);
    let mut push = |l: f32, r: f32| {
        pcm.push((l.clamp(-1.0, 1.0) * 32767.0) as i16);
        pcm.push((r.clamp(-1.0, 1.0) * 32767.0) as i16);
    };
    const C: f32 = std::f32::consts::FRAC_1_SQRT_2;
    match channels.len() {
        0 => {}
        1 => for i in 0..n { push(channels[0][i], channels[0][i]); },
        2 => for i in 0..n { push(channels[0][i], channels[1][i]); },
        // L R C
        3 => for i in 0..n { push(channels[0][i] + C * channels[2][i], channels[1][i] + C * channels[2][i]); },
        // L R Ls Rs
        4 => for i in 0..n { push(channels[0][i] + C * channels[2][i], channels[1][i] + C * channels[3][i]); },
        // L R C Ls Rs
        5 => for i in 0..n { push(channels[0][i] + C * (channels[2][i] + channels[3][i]), channels[1][i] + C * (channels[2][i] + channels[4][i])); },
        // L R C LFE Ls Rs
        _ => for i in 0..n { push(channels[0][i] + C * (channels[2][i] + channels[4][i]), channels[1][i] + C * (channels[2][i] + channels[5][i])); },
    }
}

fn wav(pcm: &[i16], sample_rate: u32) -> Vec<u8> {
    let data = (pcm.len() * 2) as u32;
    let mut out = Vec::with_capacity(44 + pcm.len() * 2);
    out.extend_from_slice(b"RIFF");
    out.extend_from_slice(&(36 + data).to_le_bytes());
    out.extend_from_slice(b"WAVEfmt ");
    out.extend_from_slice(&16u32.to_le_bytes());
    out.extend_from_slice(&1u16.to_le_bytes()); // PCM
    out.extend_from_slice(&2u16.to_le_bytes()); // stereo
    out.extend_from_slice(&sample_rate.to_le_bytes());
    out.extend_from_slice(&(sample_rate * 4).to_le_bytes());
    out.extend_from_slice(&4u16.to_le_bytes());
    out.extend_from_slice(&16u16.to_le_bytes());
    out.extend_from_slice(b"data");
    out.extend_from_slice(&data.to_le_bytes());
    for s in pcm {
        out.extend_from_slice(&s.to_le_bytes());
    }
    out
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn iso_durations() {
        assert_eq!(duration_attr(Some("PT3M23.648S")), Some(203_648));
        assert_eq!(duration_attr(Some("PT1H2M3S")), Some(3_723_000));
        assert_eq!(duration_attr(Some("PT0S")), Some(0));
        assert_eq!(duration_attr(None), None);
    }

    #[test]
    fn a_tidal_style_mpd() {
        let xml = r#"<MPD xmlns="urn:mpeg:dash:schema:mpd:2011" mediaPresentationDuration="PT4S">
          <Period><AdaptationSet mimeType="audio/mp4">
            <Representation id="EAC3_JOC" codecs="ec-3" bandwidth="769208" audioSamplingRate="48000">
              <SegmentTemplate timescale="48000" startNumber="1" initialization="init.mp4" media="seg-$Number$.m4s">
                <SegmentTimeline><S d="192000" r="0"/></SegmentTimeline>
              </SegmentTemplate>
            </Representation>
          </AdaptationSet></Period>
        </MPD>"#;
        let plan = Plan::parse(xml, "https://cdn.example.com/album/manifest.mpd").unwrap();
        assert_eq!(plan.init, "https://cdn.example.com/album/init.mp4");
        assert_eq!(plan.segments, vec!["https://cdn.example.com/album/seg-1.m4s"]);
        assert_eq!(plan.bandwidth, 769_208);
    }

    #[test]
    fn mdat_walk() {
        let mut bytes = Vec::new();
        bytes.extend_from_slice(&16u32.to_be_bytes());
        bytes.extend_from_slice(b"moov");
        bytes.extend_from_slice(&[0; 8]);
        bytes.extend_from_slice(&12u32.to_be_bytes());
        bytes.extend_from_slice(b"mdat");
        bytes.extend_from_slice(&[1, 2, 3, 4]);
        assert_eq!(mdat_payloads(&bytes), vec![&[1u8, 2, 3, 4][..]]);
    }

    #[test]
    fn the_decoded_wav_plays_through_the_players_decoder() {
        let pcm: Vec<i16> = (0..480).flat_map(|i| [i as i16 * 10, -(i as i16) * 10]).collect();
        let data = wav(&pcm, 48_000);
        assert_eq!(&data[0..4], b"RIFF");
        assert_eq!(&data[8..12], b"WAVE");
        assert_eq!(&data[12..16], b"fmt ");
        assert_eq!(&data[36..40], b"data");
        // Symphonia probe directly.
        use symphonia::core::io::MediaSourceStream;
        use symphonia::core::probe::Hint;
        let mss = MediaSourceStream::new(Box::new(std::io::Cursor::new(data.clone())), Default::default());
        let mut hint = Hint::new();
        hint.with_extension("wav");
        let probed = symphonia::default::get_probe().format(&hint, mss, &Default::default(), &Default::default());
        assert!(probed.is_ok(), "symphonia wav probe failed: {:?}", probed.err());
    }

    #[test]
    #[ignore]
    fn live_blinding_lights_atmos_decodes_to_playable_pcm() {
        // Reads the local hires source and fetches a real Tidal Atmos stream.
        // Never committed with secrets; run manually when debugging.
        let data = std::fs::read(directories::ProjectDirs::from("", "", "ytm-rs").unwrap().data_dir().join("app.ron")).unwrap();
        let text = String::from_utf8_lossy(&data);
        let base = text.split("\"hires_source\":").nth(1).and_then(|s| s.split('"').nth(1)).unwrap().replace("\\n", "\n").lines().next().unwrap().to_owned();
        let rt = tokio::runtime::Runtime::new().unwrap();
        rt.block_on(async {
            let http = reqwest::Client::new();
            // Search + stream via the same endpoints the app uses.
            let mk = |q: &str| { let mut u = url::Url::parse(&base).unwrap(); { let mut p = u.path_segments_mut().unwrap(); p.pop_if_empty(); p.push("search"); } u.query_pairs_mut().append_pair("q", q).append_pair("quality", "LOSSLESS").append_pair("atmos", "auto"); u };
            let r = http.get(mk("Blinding Lights The Weeknd")).header("User-Agent", crate::hires::USER_AGENT).send().await.unwrap();
            let j: serde_json::Value = r.json().await.unwrap();
            let items = j["tracks"].as_array().or(j["results"].as_array()).cloned().unwrap_or_default();
            let hit = items.iter().find(|t| t["artist"].as_str().unwrap_or("").to_lowercase().contains("weeknd") && t["title"].as_str().unwrap_or("").eq_ignore_ascii_case("Blinding Lights")).unwrap();
            let id = hit["id"].as_str().unwrap();
            let mut su = url::Url::parse(&base).unwrap();
            { let mut p = su.path_segments_mut().unwrap(); p.pop_if_empty(); p.push("stream"); p.push(id); }
            su.query_pairs_mut().append_pair("quality", "LOSSLESS").append_pair("atmos", "auto");
            let sr = http.get(su).header("User-Agent", crate::hires::USER_AGENT).send().await.unwrap();
            let sj: serde_json::Value = sr.json().await.unwrap();
            assert!(sj["atmos"].as_bool().unwrap_or(false), "not atmos: {:?}", sj["codec"]);
            let mpd = sj["url"].as_str().unwrap();
            let audio = fetch(&http, mpd).await.unwrap();
            assert!(!audio.pcm.is_empty());
            let shared = crate::stream::Shared::from_bytes_with_format(audio.pcm, audio.duration_ms, None, crate::stream::Format::Pcm { rate: audio.sample_rate, channels: 2 }, None);
            let mut dec = crate::stream::decoder(shared, false).unwrap();
            assert!(dec.by_ref().take(4800).count() == 4800);
        });
    }
}