//! High-resolution audio from a compatible addon server.
//!
//! The addon protocol is three GET endpoints returning JSON: `{base}/manifest.json`,
//! `{base}/search?q=…&quality=LOSSLESS` and `{base}/stream/{trackId}?quality=LOSSLESS`.
//! A track found there plays from the addon's own URL instead of YouTube Music; when
//! the addon has no copy, or the answer isn't playable, playback falls back to YouTube.

use std::collections::{BTreeSet, HashMap};
use std::sync::Arc;
use std::sync::atomic::{AtomicBool, Ordering};
use std::time::Duration;

use serde_json::Value;

use crate::stream::Format;

/// What the addon server is asked to recognise us as. Addons allow-list the desktop
/// release line's agent (`BitChord/v1.x.y`) and answer `403` to anything else, so this
/// build's own name goes first and the release line's is what a refusal falls back to.
pub const USER_AGENT: &str = "Bitchord-PC";
const RELEASE_AGENT: &str = "BitChord/v1.9.34";
/// Set once an addon has refused our own name: it keeps the release line's agent for the
/// rest of the session instead of being refused on every request.
static RELEASE_LINE: AtomicBool = AtomicBool::new(false);

/// The agent to ask with right now.
pub fn agent() -> &'static str {
    if RELEASE_LINE.load(Ordering::Relaxed) { RELEASE_AGENT } else { USER_AGENT }
}

/// Moves this session's requests over to the release line's agent.
fn use_release_agent() {
    RELEASE_LINE.store(true, Ordering::Relaxed);
}

const TIER: &str = "LOSSLESS";
const REQUEST_TIMEOUT: Duration = Duration::from_secs(8);
/// `/stream` makes the addon resolve the song with its own upstream, which takes several
/// seconds on a cold lookup; cutting it at the search's timeout throws away good copies.
const STREAM_TIMEOUT: Duration = Duration::from_secs(18);
const PROBE_TIMEOUT: Duration = Duration::from_secs(5);
const SEARCH_LIMIT: usize = 25;
/// How many of the search's candidates get a `/stream` call before giving up. They're
/// asked together: one addon answers a cold lookup in the time three sequential ones take.
const STREAM_TRIES: usize = 3;
/// How far a copy's length may sit from the song's before it's a different version of it.
/// Silence and fades stay inside this; an edit, a loop cut or a re-tempo'd one doesn't.
const VERSION_TOLERANCE_SECS: u32 = 3;
const VERSION_TOLERANCE_MS: u64 = VERSION_TOLERANCE_SECS as u64 * 1000;
/// The size given a stream whose addon and server don't say how big it is.
pub const UNKNOWN_SIZE: u64 = 512 * 1024 * 1024;
/// With Atmos on, a lossless copy already in hand waits this long for an Atmos answer
/// before it plays; the whole race also stays inside the playback path's own budget.
const ATMOS_GRACE: Duration = Duration::from_secs(5);
const RACE_BUDGET: Duration = Duration::from_secs(19);
/// The song a pasted link is tested with, to see what the addon actually serves.
const CHECK_QUERY: &str = "Blinding Lights The Weeknd";

/// What the player says about the stream it is playing.
#[derive(Clone, Debug, PartialEq)]
pub struct Info {
    /// The addon's own name (or its host when it publishes none).
    pub source: String,
    /// The host the audio comes from, without the path (it can carry a token).
    pub host: String,
    /// `FLAC`, `ALAC`, `MP3`, … as stated by the addon or read off the URL.
    pub codec: String,
    /// The quality label the addon sent, when it sent one.
    pub quality: String,
    pub bitrate_kbps: Option<u32>,
    pub sample_rate: Option<u32>,
    pub bit_depth: Option<u32>,
    pub channels: Option<u8>,
    /// The stream is a Dolby Atmos rendition.
    pub atmos: bool,
    pub size: u64,
    pub duration_ms: u64,
}

impl Info {
    pub fn lossless(&self) -> bool {
        matches!(self.codec.to_ascii_lowercase().as_str(), "flac" | "alac" | "wav" | "aiff") || has_lossless_word(&self.quality)
    }

    /// More than CD: better than 48 kHz or more than 16 bits.
    pub fn hires(&self) -> bool {
        self.lossless() && (self.sample_rate.is_some_and(|r| r > 48_000) || self.bit_depth.is_some_and(|b| b > 16))
    }

    /// The quality mark's text, worded as Apple Music words it.
    pub fn badge(&self) -> &'static str {
        if self.atmos {
            "Dolby Atmos"
        } else if self.hires() {
            "Hi-Res Lossless"
        } else if self.lossless() {
            "Lossless"
        } else if self.bitrate_kbps.is_some_and(|k| k >= 320) {
            "320 kbps"
        } else {
            "High"
        }
    }

    /// The one-line audio-quality string the player's track-info and Discord both show:
    /// the badge, then each format detail that is known, joined with " · ".
    /// "Hi-Res Lossless · FLAC · 1641 kbps · 24-bit · 44.1 kHz · Stereo".
    pub fn details_line(&self) -> String {
        let mut parts: Vec<String> = vec![self.badge().to_owned()];
        let codec = self.codec.trim();
        if !codec.is_empty() {
            parts.push(codec.to_ascii_uppercase());
        }
        if let Some(k) = self.bitrate_kbps.filter(|k| *k > 0) {
            parts.push(format!("{k} kbps"));
        }
        if let Some(bits) = self.bit_depth {
            parts.push(format!("{bits}-bit"));
        }
        if let Some(hz) = self.sample_rate.filter(|r| *r > 0) {
            parts.push(if hz % 1000 == 0 { format!("{} kHz", hz / 1000) } else { format!("{:.1} kHz", hz as f64 / 1000.0) });
        }
        if let Some(n) = self.channels.filter(|c| *c > 0) {
            parts.push(match n {
                1 => "Mono".to_owned(),
                2 => "Stereo".to_owned(),
                _ => format!("{n} ch"),
            });
        }
        parts.join(" · ")
    }

    /// Like `details_line` but without the sample rate: Discord shows bitrate, not Hz.
    pub fn rpc_line(&self) -> String {
        let mut parts: Vec<String> = vec![self.badge().to_owned()];
        let codec = self.codec.trim();
        if !codec.is_empty() {
            parts.push(codec.to_ascii_uppercase());
        }
        if let Some(k) = self.bitrate_kbps.filter(|k| *k > 0) {
            parts.push(format!("{k} kbps"));
        }
        if let Some(bits) = self.bit_depth {
            parts.push(format!("{bits}-bit"));
        }
        if let Some(n) = self.channels.filter(|c| *c > 0) {
            parts.push(match n {
                1 => "Mono".to_owned(),
                2 => "Stereo".to_owned(),
                _ => format!("{n} ch"),
            });
        }
        parts.join(" · ")
    }
}

/// One playable answer from the addon.
pub struct Stream {
    pub url: String,
    pub size: u64,
    pub duration_ms: u64,
    pub format: Format,
    pub info: Info,
    /// Already-decoded audio (Dolby Atmos out of DASH): no download to run.
    pub data: Option<Vec<u8>>,
}

/// One addon server, with its manifest and recent searches remembered.
pub struct Client {
    base: String,
    manifest: tokio::sync::Mutex<Option<Manifest>>,
    searches: tokio::sync::Mutex<HashMap<String, Vec<Candidate>>>,
}

struct Manifest {
    name: Option<String>,
    defaults: Vec<(String, String)>,
    quality: String,
}

#[derive(Clone)]
pub(crate) struct Candidate {
    id: String,
    title: String,
    artist: String,
    duration: Option<u32>,
    quality: String,
    format: String,
    /// Which of the addon's own upstreams the row came from (`Tidal`, `Qobuz`, …).
    provider: String,
    atmos: bool,
    stream_url: Option<String>,
}

impl Client {
    /// A client for whatever the listener pasted. `None` when it isn't an http(s) URL.
    pub fn new(raw: &str) -> Option<Self> {
        let base = normalize_base(raw);
        let parsed = url::Url::parse(&base).ok()?;
        if !matches!(parsed.scheme(), "http" | "https") || parsed.host_str().is_none_or(str::is_empty) {
            return None;
        }
        Some(Self { base, manifest: Default::default(), searches: Default::default() })
    }

    /// The addon's host, for a log line (the link's path may carry a token).
    pub fn host(&self) -> String {
        host_of(&self.base)
    }

    /// The rows that could be `track`, best first. `atmos` also asks the addon for its
    /// Dolby Atmos renditions, so the caller can prefer one across every source.
    pub async fn picks(&self, http: &reqwest::Client, track: &crate::model::Track, atmos: bool) -> anyhow::Result<Vec<Candidate>> {
        let manifest = self.manifest(http).await?;
        let artist = track.artists.first().map(|a| a.name.clone()).unwrap_or_else(|| track.artist_line());
        let title = track.title.clone();
        let mut queries = Vec::new();
        let searchable = searchable_title(&title, &artist);
        let primary = primary_artist(&artist);
        if !searchable.is_empty() {
            queries.push(if primary.is_empty() { searchable.clone() } else { format!("{searchable} {primary}") });
            if !primary.is_empty() && queries.first().is_none_or(|q| q != &searchable) {
                queries.push(searchable);
            }
        }
        // The Atmos-aware search marks rows the plain one can't (an Atmos rendition is
        // often a separate, low-bitrate lossy row), then the plain pass fills in the
        // stereo lossless copies for when no Atmos row turns up.
        let passes: &[(bool, bool)] = if atmos { &[(true, true), (false, false)] } else { &[(false, false)] };
        let mut out: Vec<Candidate> = Vec::new();
        let mut seen: BTreeSet<String> = BTreeSet::new();
        let mut last_err = None;
        for &(search_atmos, prefer_atmos) in passes {
            for query in &queries {
                let candidates = match self.search(http, &manifest, query, search_atmos).await {
                    Ok(c) => c,
                    Err(e) => { last_err = Some(e); continue; }
                };
                for candidate in rank(candidates, &title, &artist, track.duration, prefer_atmos) {
                    if seen.insert(candidate.id.clone()) {
                        out.push(candidate);
                    }
                }
            }
        }
        if let (true, Some(e)) = (out.is_empty(), last_err) {
            return Err(e);
        }
        Ok(out)
    }

    /// The playable stream behind one `picks` row, probed for its size (Dolby Atmos
    /// included). A miss is `None`, not a fault.
    pub async fn open(&self, http: &reqwest::Client, candidate: &Candidate, atmos: bool, prefetch: bool) -> anyhow::Result<Option<Stream>> {
        let manifest = self.manifest(http).await?;
        self.stream(http, &manifest, candidate, atmos, prefetch).await
    }

    async fn manifest(&self, http: &reqwest::Client) -> anyhow::Result<Manifest> {
        let mut slot = self.manifest.lock().await;
        if let Some(m) = slot.as_ref() {
            return Ok(Manifest { name: m.name.clone(), defaults: m.defaults.clone(), quality: m.quality.clone() });
        }
        let url = self.endpoint(&["manifest.json"], &[])?;
        let json = get_json(http, &url, REQUEST_TIMEOUT).await?;
        let id = json.get("id").and_then(Value::as_str).unwrap_or("").trim();
        anyhow::ensure!(!id.is_empty(), "that URL answered, but not with an addon manifest");
        let resources: Vec<&str> = json.get("resources").and_then(Value::as_array).map(|a| a.iter().filter_map(Value::as_str).collect()).unwrap_or_default();
        anyhow::ensure!(resources.is_empty() || resources.iter().any(|r| r.eq_ignore_ascii_case("search")), "the addon doesn't offer search");
        let name = json.get("name").and_then(Value::as_str).map(str::trim).filter(|s| !s.is_empty()).map(str::to_owned);
        let (defaults, quality) = settings(json.get("settings"));
        let manifest = Manifest { name, defaults, quality };
        *slot = Some(Manifest { name: manifest.name.clone(), defaults: manifest.defaults.clone(), quality: manifest.quality.clone() });
        Ok(manifest)
    }

    async fn search(&self, http: &reqwest::Client, manifest: &Manifest, query: &str, atmos: bool) -> anyhow::Result<Vec<Candidate>> {
        let key = format!("{}|{query}|{atmos}", manifest.quality);
        if let Some(hit) = self.searches.lock().await.get(&key) {
            return Ok(hit.clone());
        }
        let mut params = manifest.defaults.clone();
        params.push(("quality".to_owned(), manifest.quality.clone()));
        if atmos {
            params.push(("atmos".to_owned(), "auto".to_owned()));
        }
        params.push(("q".to_owned(), query.to_owned()));
        let url = self.endpoint(&["search"], &params)?;
        let json = get_json(http, &url, REQUEST_TIMEOUT).await?;
        let tracks = json.get("tracks").and_then(Value::as_array).cloned().unwrap_or_default();
        let found: Vec<Candidate> = tracks.iter().filter_map(candidate).take(400).collect();
        let mut cache = self.searches.lock().await;
        if cache.len() > 64 {
            cache.clear();
        }
        cache.insert(key, found.clone());
        Ok(found)
    }

    async fn stream(&self, http: &reqwest::Client, manifest: &Manifest, candidate: &Candidate, atmos: bool, prefetch: bool) -> anyhow::Result<Option<Stream>> {
        let mut params = manifest.defaults.clone();
        params.push(("quality".to_owned(), manifest.quality.clone()));
        if atmos {
            params.push(("atmos".to_owned(), "auto".to_owned()));
        }
        let url = self.endpoint(&["stream", &candidate.id], &params)?;
        let json = match get_json(http, &url, STREAM_TIMEOUT).await {
            Ok(j) => j,
            // A miss, not a fault: the row may still carry its own URL.
            Err(e) if e.to_string().contains("not found") => Value::Null,
            Err(e) => return Err(e),
        };
        let stated_url = json.get("url").and_then(Value::as_str).unwrap_or("").trim().to_owned();
        let stated_url = if stated_url.is_empty() { candidate.stream_url.clone().unwrap_or_default() } else { stated_url };
        anyhow::ensure!(!stated_url.is_empty(), "the addon returned no stream URL");
        anyhow::ensure!(playable_url(&stated_url), "the addon returned a URL that isn't http(s)");
        anyhow::ensure!(!encrypted(&json), "the addon returned an encrypted stream");
        let stated = json.get("format").and_then(Value::as_str).map(str::trim).unwrap_or("");
        let transport = json.get("manifest").and_then(Value::as_str).or(json.get("mediaType").and_then(Value::as_str)).map(str::trim).filter(|s| !s.is_empty()).unwrap_or(stated);
        let quality = json.get("quality").and_then(Value::as_str).or(json.get("streamQuality").and_then(Value::as_str)).or(json.get("audioQuality").and_then(Value::as_str)).unwrap_or("").trim().to_owned();
        let label = format!("{quality} {} {}", candidate.quality, candidate.format);
        let codec = json.get("codec").and_then(Value::as_str).or(json.get("fileCodec").and_then(Value::as_str)).map(str::trim).filter(|s| !s.is_empty()).map(str::to_owned).unwrap_or_default();
        let container = json.get("container").and_then(Value::as_str).or(json.get("containerFormat").and_then(Value::as_str)).map(str::trim).filter(|s| !s.is_empty()).unwrap_or("");
        // An addon that names no container puts the file format in `format`, which is the
        // transport keyword instead when the answer is a manifest (handled above).
        let container = if container.is_empty() && !is_manifest(stated) { stated } else { container };
        let mime = json.get("mimeType").and_then(Value::as_str).unwrap_or("");
        if is_manifest(transport) {
            // A prefetch only needs somewhere to start. Fetching and decoding a whole
            // rendition here would hold a second song's PCM beside the playing one; the
            // play itself fetches it, so the copy still arrives.
            anyhow::ensure!(!prefetch, "a manifest rendition is fetched when the song plays");
            // DASH arrives as an MPD, not a file: the Atmos E-AC-3 rendition (or the
            // hi-res FLAC one) is fetched whole and decoded to PCM, so it plays and
            // seeks like any other stream.
            let dash = matches!(transport.trim().to_ascii_lowercase().as_str(), "dash" | "mpd" | "application/dash+xml");
            anyhow::ensure!(dash, "the addon returned a streaming manifest, not a file");
            let audio = crate::dash::fetch(http, &stated_url).await?;
            let size = audio.pcm.len() as u64;
            let info = Info {
                source: manifest.name.clone().unwrap_or_else(|| host_of(&self.base)),
                host: host_of(&stated_url),
                codec: audio.codec.to_owned(),
                quality: if quality.is_empty() { candidate.quality.clone() } else { quality },
                bitrate_kbps: Some(audio.bitrate_kbps).filter(|k| *k > 0),
                sample_rate: Some(audio.sample_rate),
                bit_depth: Some(audio.bit_depth).filter(|b| *b > 0),
                channels: Some(2),
                atmos: audio.atmos,
                size,
                duration_ms: audio.duration_ms,
            };
            return Ok(Some(Stream { url: stated_url, size, duration_ms: audio.duration_ms, format: Format::Pcm { rate: audio.sample_rate, channels: 2 }, info, data: Some(audio.pcm) }));
        }
        let Some(format) = format_of(&codec, container, mime, &stated_url, &label) else {
            anyhow::bail!("the addon's format isn't playable ({codec} {container} {mime})");
        };
        let mut size = json_size(&json);
        if size == 0 {
            size = content_size(http, &stated_url).await.unwrap_or(0);
        }
        // Some addon URLs only reveal their size once the stream starts; use a placeholder
        // large enough for ranged reads and let the download determine the real end.
        if size == 0 {
            log::info!("hi-res: stream size unknown for \"{}\", using ranged placeholder", candidate.title);
            size = UNKNOWN_SIZE;
        }
        let sample_rate = json.get("sampleRate").and_then(Value::as_f64).filter(|r| *r > 0.0).map(|r| if r < 1000.0 { (r * 1000.0) as u32 } else { r as u32 })
            .or_else(|| number_before(&label, "khz").map(|r| (r * 1000.0) as u32));
        let bit_depth = json.get("bitDepth").and_then(Value::as_f64).filter(|b| *b > 0.0).map(|b| b as u32)
            .or_else(|| number_before(&label, "bit").filter(|b| (8.0..=32.0).contains(b)).map(|b| b as u32));
        let bitrate_kbps = json.get("bitrate").and_then(Value::as_f64).filter(|k| *k > 0.0).map(|k| if k > 3000.0 { (k / 1000.0) as u32 } else { k as u32 })
            .or_else(|| number_before(&label, "kbps").map(|k| k as u32));
        let duration_ms = candidate.duration.map_or(0, |s| s as u64 * 1000);
        let atmos = candidate.atmos || json_is_atmos(&json);
        let info = Info {
            source: manifest.name.clone().unwrap_or_else(|| host_of(&self.base)),
            host: host_of(&stated_url),
            codec: codec_name(&codec, &format),
            quality: if quality.is_empty() { candidate.quality.clone() } else { quality },
            bitrate_kbps,
            sample_rate,
            bit_depth,
            channels: None,
            atmos,
            size,
            duration_ms,
        };
        Ok(Some(Stream { url: stated_url, size, duration_ms, format, info, data: None }))
    }

    fn endpoint(&self, segments: &[&str], params: &[(String, String)]) -> anyhow::Result<String> {
        let mut url = url::Url::parse(&self.base)?;
        {
            let mut path = url.path_segments_mut().map_err(|_| anyhow::anyhow!("that addon address can't take paths"))?;
            path.pop_if_empty();
            for segment in segments {
                path.push(segment);
            }
        }
        {
            let mut query = url.query_pairs_mut();
            for (key, value) in params {
                query.append_pair(key, value);
            }
        }
        Ok(url.into())
    }
}

/// The best copy across every source. Each source searches and opens its stream at the
/// same time; with Atmos off the first usable (lossless) answer plays, and with Atmos on
/// the first Atmos rendition wins — otherwise the first lossless copy is held for a short
/// grace while the rest answer, then played. `None` means no source has a copy (YouTube plays).
pub async fn find_all(http: &reqwest::Client, sources: &[Arc<Client>], track: &crate::model::Track, atmos: bool, prefetch: bool) -> Option<Stream> {
    let mut tasks = tokio::task::JoinSet::new();
    for client in sources.iter().cloned() {
        let (http, track) = (http.clone(), track.clone());
        tasks.spawn(async move {
            let found = match client.picks(&http, &track, atmos).await {
                Ok(picks) => open_first(&http, &client, &picks, atmos, track.duration, prefetch).await,
                Err(e) => Err(e),
            };
            (client, found)
        });
    }
    let mut lossless: Option<Stream> = None;
    let started = tokio::time::Instant::now();
    let mut held_at: Option<tokio::time::Instant> = None;
    loop {
        // A lossless copy in hand: give Atmos a bounded grace, then play what's held
        // rather than stall on a slow source (the playback path has its own outer wait).
        let joined = match held_at {
            Some(t) => {
                let deadline = (t + ATMOS_GRACE).min(started + RACE_BUDGET);
                match tokio::time::timeout_at(deadline, tasks.join_next()).await {
                    Ok(joined) => joined,
                    Err(_) => break,
                }
            }
            None => tasks.join_next().await,
        };
        let Some(joined) = joined else { break };
        let (client, found) = match joined {
            Ok(v) => v,
            Err(e) => {
                log::info!("hi-res: a lookup stopped: {e}");
                continue;
            }
        };
        match found {
            Ok(Some(s)) if s.info.atmos => {
                log::info!("hi-res: \"{}\" plays Atmos ({})", track.title, client.host());
                tasks.abort_all();
                return Some(s);
            }
            Ok(Some(s)) if usable(&s) => {
                if !atmos {
                    log::info!("hi-res: \"{}\" plays {} ({})", track.title, s.info.badge(), client.host());
                    tasks.abort_all();
                    return Some(s);
                }
                // No Atmos answer yet: keep the lossless copy in case none turns up.
                held_at.get_or_insert_with(tokio::time::Instant::now);
                lossless = lossless.or(Some(s));
            }
            Ok(_) => {}
            Err(e) => log::info!("hi-res {}: \"{}\": {e:#}", client.host(), track.title),
        }
    }
    if let Some(s) = &lossless {
        log::info!("hi-res: \"{}\" plays {} ({})", track.title, s.info.badge(), s.info.host);
    }
    lossless
}

/// The best playable row of one source: Atmos rows first when the preference is on (a
/// miss leaves the first lossless copy held), then the plain rows. A stream measured more
/// than `duration` tolerance off the song is a different cut of it, so the next row is
/// opened instead.
async fn open_first(http: &reqwest::Client, client: &Client, candidates: &[Candidate], atmos: bool, duration: Option<u32>, prefetch: bool) -> anyhow::Result<Option<Stream>> {
    use futures_util::StreamExt;
    let rounds: &[bool] = if atmos { &[true, false] } else { &[false] };
    let mut held: Option<Stream> = None;
    for &round in rounds {
        // Asked together rather than one after another: an addon resolves a cold lookup in
        // roughly the time three sequential ones take, and the first good answer ends it.
        let mut pending = futures_util::stream::FuturesUnordered::new();
        for candidate in candidates.iter().filter(|c| !atmos || c.atmos == round).take(STREAM_TRIES) {
            pending.push(async move { (candidate, client.open(http, candidate, atmos && candidate.atmos, prefetch).await) });
        }
        if pending.is_empty() {
            continue;
        }
        while let Some((candidate, found)) = pending.next().await {
            match found {
                Ok(Some(s)) if usable(&s) => {
                    if let Some(off) = off_version(s.duration_ms, duration) {
                        log::info!("hi-res: candidate {} of {} is {off} s from the song's length, skipped", candidate.id, client.host());
                        continue;
                    }
                    if s.info.atmos {
                        return Ok(Some(s));
                    }
                    held.get_or_insert(s);
                    if !atmos {
                        return Ok(held);
                    }
                }
                Ok(_) => {}
                Err(e) => log::info!("hi-res {}: candidate {} skipped: {e:#}", client.host(), candidate.id),
            }
        }
    }
    Ok(held)
}

/// How far a stream's measured length sits outside the song's, in whole seconds, or
/// `None` when it matches. Only a measured length can say: an addon row promises the song
/// and sometimes serves an edit or a remix that kept its title.
fn off_version(measured_ms: u64, duration: Option<u32>) -> Option<u64> {
    let (Some(wanted), true) = (duration, measured_ms > 0) else {
        return None;
    };
    let diff = (wanted as u64 * 1000).abs_diff(measured_ms);
    (diff > VERSION_TOLERANCE_MS).then(|| (diff + 500) / 1000)
}

/// The chain is lossless or Atmos; a lossy copy from an addon isn't better than YouTube.
fn usable(stream: &Stream) -> bool {
    stream.info.atmos || stream.info.lossless()
}

/// What a pasted link really is, for the settings row that tests it: the addon's own name,
/// the upstreams it lists, and what one of its lossless rows actually streams. A row can
/// promise lossless and deliver 320 kbps, so the test opens one rather than trusting it.
/// Neither the answer nor an error names the link: it carries a token.
pub async fn check(http: &reqwest::Client, raw: &str) -> anyhow::Result<String> {
    let Some(client) = Client::new(raw) else { anyhow::bail!("that isn't an http(s) link") };
    let manifest = client.manifest(http).await?;
    let name = manifest.name.clone().unwrap_or_else(|| client.host());
    let rows = client.search(http, &manifest, CHECK_QUERY, false).await?;
    anyhow::ensure!(!rows.is_empty(), "{name} answered its search with nothing");
    let mut providers: Vec<String> = Vec::new();
    for row in &rows {
        let provider = if row.provider.is_empty() { row.id.split(':').next().unwrap_or("").to_owned() } else { row.provider.clone() };
        if !provider.is_empty() && !providers.iter().any(|p| p.eq_ignore_ascii_case(&provider)) {
            providers.push(provider);
        }
    }
    let served = providers.join(", ");
    match client.open(http, &rows[0], false, false).await {
        Ok(Some(s)) if usable(&s) => Ok(format!("{name} · {served} · plays {}", s.info.badge())),
        Ok(Some(s)) => anyhow::bail!("{name} streamed {} where its row promised lossless", s.info.details_line()),
        Ok(None) => anyhow::bail!("{name} sent no stream for a test song"),
        Err(e) => anyhow::bail!("{name}: {e}"),
    }
}

/// The base URL with a trailing slash or a pasted `…/manifest.json` taken off.
pub fn normalize_base(raw: &str) -> String {
    let trimmed = raw.trim();
    let (path, query) = match trimmed.split_once('?') {
        Some((path, query)) => (path, Some(query)),
        None => (trimmed, None),
    };
    if !path.contains("://") {
        return trimmed.to_owned();
    }
    let last = path.rsplit('/').next().unwrap_or("");
    let base = if last.to_ascii_lowercase().ends_with(".json") { &path[..path.len() - last.len()] } else { path };
    let base = base.trim_end_matches('/');
    match query {
        Some(query) => format!("{base}?{query}"),
        None => base.to_owned(),
    }
}

fn host_of(url: &str) -> String {
    url::Url::parse(url).ok().and_then(|u| u.host_str().map(str::to_owned)).unwrap_or_default()
}

fn settings(value: Option<&Value>) -> (Vec<(String, String)>, String) {
    let mut defaults = Vec::new();
    let mut quality = TIER.to_owned();
    let Some(list) = value.and_then(Value::as_array) else { return (defaults, quality) };
    for entry in list {
        let Some(key) = entry.get("key").and_then(Value::as_str).map(str::trim).filter(|k| !k.is_empty()) else { continue };
        let Some(default) = entry.get("default").and_then(query_value) else { continue };
        if key.eq_ignore_ascii_case("quality") {
            let options: Vec<String> = entry.get("options").and_then(Value::as_array).map(|a| a.iter().filter_map(|o| o.get("value").and_then(query_value)).collect()).unwrap_or_default();
            quality = match_tier(&options);
        } else {
            defaults.push((key.to_owned(), default));
        }
    }
    (defaults, quality)
}

fn query_value(v: &Value) -> Option<String> {
    match v {
        Value::String(s) => Some(s.clone()).filter(|s| !s.is_empty()),
        Value::Number(n) => Some(n.to_string()),
        Value::Bool(b) => Some(b.to_string()),
        _ => None,
    }
}

/// The option that best answers a lossless request.
fn match_tier(options: &[String]) -> String {
    if options.is_empty() {
        return TIER.to_owned();
    }
    if let Some(exact) = options.iter().find(|o| o.eq_ignore_ascii_case(TIER)) {
        return exact.clone();
    }
    const WORDS: [&str; 7] = ["lossless", "flac", "hifi", "hi-res", "hires", "max", "best"];
    options.iter().find(|o| WORDS.iter().any(|w| o.to_ascii_lowercase().contains(w))).cloned().unwrap_or_else(|| options[0].clone())
}

fn candidate(v: &Value) -> Option<Candidate> {
    let id = v.get("id").and_then(Value::as_str).unwrap_or("").trim();
    let title = v.get("title").and_then(Value::as_str).unwrap_or("").trim();
    if id.is_empty() || title.is_empty() {
        return None;
    }
    Some(Candidate {
        id: id.to_owned(),
        title: title.to_owned(),
        artist: v.get("artist").and_then(Value::as_str).unwrap_or("").trim().to_owned(),
        duration: seconds(v.get("duration").unwrap_or(&Value::Null)),
        quality: v.get("audioQuality").and_then(Value::as_str).unwrap_or("").trim().to_owned(),
        format: v.get("format").and_then(Value::as_str).unwrap_or("").trim().to_owned(),
        provider: v.get("provider").and_then(Value::as_str).unwrap_or("").trim().to_owned(),
        atmos: json_is_atmos(v),
        stream_url: v.get("streamURL").and_then(Value::as_str).map(str::trim).filter(|s| !s.is_empty()).map(str::to_owned),
    })
}

fn seconds(v: &Value) -> Option<u32> {
    if let Some(n) = v.as_f64() {
        return (n > 0.0).then_some(n as u32);
    }
    let text = v.as_str()?.trim();
    if let Some((m, s)) = text.split_once(':') {
        let m: u32 = m.parse().ok()?;
        let s: f64 = s.parse().ok()?;
        return Some(m * 60 + s as u32);
    }
    text.parse::<f64>().ok().filter(|n| *n > 0.0).map(|n| n as u32)
}

fn json_size(v: &Value) -> u64 {
    for key in ["fileSize", "filesize", "size", "contentLength", "content_length", "length", "bytes"] {
        if let Some(n) = v.get(key) {
            if let Some(u) = n.as_u64().filter(|u| *u > 0) {
                return u;
            }
            if let Some(f) = n.as_f64().filter(|f| *f > 0.0) {
                return f as u64;
            }
            if let Some(s) = n.as_str().and_then(|s| s.trim().parse::<u64>().ok()).filter(|u| *u > 0) {
                return s;
            }
        }
    }
    0
}

/// A row or stream that says it's Dolby Atmos, under any of the names addons use for it.
fn json_is_atmos(v: &Value) -> bool {
    const KEYS: [&str; 7] = ["audioMode", "audioModes", "quality", "streamQuality", "audioQuality", "format", "codec"];
    KEYS.iter().any(|key| v.get(*key).is_some_and(value_is_atmos))
}

fn value_is_atmos(v: &Value) -> bool {
    match v {
        Value::String(s) => {
            let s = s.to_ascii_lowercase();
            ["atmos", "dolby", "ac-3", "ac3", "eac3", "ec-3"].iter().any(|w| s.contains(w))
        }
        Value::Array(items) => items.iter().any(value_is_atmos),
        _ => false,
    }
}

fn encrypted(v: &Value) -> bool {
    match v.get("encrypted") {
        Some(Value::Bool(b)) => *b,
        Some(Value::String(s)) => {
            let s = s.trim().to_ascii_lowercase();
            !s.is_empty() && s != "false" && s != "none" && s != "0"
        }
        Some(Value::Number(n)) => n.as_f64().is_some_and(|n| n != 0.0),
        _ => false,
    }
}

fn is_manifest(transport: &str) -> bool {
    matches!(transport.trim().to_ascii_lowercase().as_str(), "hls" | "m3u8" | "dash" | "mpd" | "application/x-mpegurl" | "application/vnd.apple.mpegurl" | "application/dash+xml")
}

fn playable_url(url: &str) -> bool {
    url::Url::parse(url).is_ok_and(|u| matches!(u.scheme(), "http" | "https") && u.host_str().is_some_and(|h| !h.is_empty()))
}

/// The container/codec rodio will be handed.
fn format_of(codec: &str, container: &str, mime: &str, url: &str, label: &str) -> Option<Format> {
    let codec = codec.to_ascii_lowercase();
    let container = container.to_ascii_lowercase();
    let mime = mime.to_ascii_lowercase();
    let ext = url.split(['?', '#']).next().unwrap_or(url).rsplit('.').next().unwrap_or("").to_ascii_lowercase();
    let all = format!("{codec} {container} {mime} {ext}");
    // Symphonia has no AC-3 family decoder, so an Atmos rendition in E-AC-3/TrueHD isn't
    // playable and the next copy is tried instead.
    if ["ac-3", "ac3", "eac-3", "eac3", "ec-3", "truehd", "mlp"].iter().any(|w| all.contains(w)) {
        return None;
    }
    let file = |hint: &'static str, mime: &str| Format::File { hint, mime: Some(mime.to_owned()) };
    if all.contains("flac") {
        return Some(file("flac", "audio/flac"));
    }
    if (all.contains("alac") || ext == "m4a" || ext == "mp4" || mime.contains("mp4")) && has_lossless_word(label) {
        return Some(file("m4a", "audio/mp4"));
    }
    if all.contains("wav") && !all.contains("wave") {
        return Some(file("wav", "audio/wav"));
    }
    if all.contains("mp3") || mime.contains("mpeg") {
        return Some(file("mp3", "audio/mpeg"));
    }
    if all.contains("vorbis") || ext == "ogg" || mime.contains("ogg") {
        return Some(file("ogg", "audio/ogg"));
    }
    if all.contains("opus") || ext == "webm" || mime.contains("webm") {
        return Some(Format::Opus);
    }
    if all.contains("aac") || all.contains("m4a") || ext == "m4a" {
        return Some(file("m4a", "audio/mp4"));
    }
    if has_lossless_word(label) {
        // A lossless row that names no codec is FLAC in practice.
        return Some(file("flac", "audio/flac"));
    }
    None
}

fn codec_name(codec: &str, format: &Format) -> String {
    let codec = codec.trim();
    if !codec.is_empty() {
        return codec.to_ascii_uppercase();
    }
    match format {
        Format::Aac => "AAC",
        Format::Opus => "OPUS",
        Format::Pcm { .. } => "PCM",
        Format::File { hint, .. } => match *hint {
            "flac" => "FLAC",
            "wav" => "WAV",
            "mp3" => "MP3",
            "ogg" => "VORBIS",
            _ => "ALAC",
        },
    }
    .to_owned()
}

fn has_lossless_word(text: &str) -> bool {
    let text = text.to_ascii_lowercase();
    ["lossless", "flac", "hifi", "hi-res", "hires", "master", "24bit", "24-bit"].iter().any(|w| text.contains(w))
}

/// The number just before `unit` in free text (`24-bit`, `96 kHz`, `1411kbps`).
fn number_before(text: &str, unit: &str) -> Option<f64> {
    let lower = text.to_ascii_lowercase();
    let mut from = 0;
    while let Some(found) = lower[from..].find(unit) {
        let at = from + found;
        let mut end = at;
        // `24-bit`, `96 kHz` and `1411 kbps` all read the same.
        while end > 0 && matches!(lower.as_bytes()[end - 1], b' ' | b'-') {
            end -= 1;
        }
        let mut start = end;
        while start > 0 && (lower.as_bytes()[start - 1].is_ascii_digit() || lower.as_bytes()[start - 1] == b'.') {
            start -= 1;
        }
        if start < end
            && let Ok(n) = lower[start..end].trim_matches('.').parse::<f64>()
            && n > 0.0
        {
            return Some(n);
        }
        from = at + unit.len();
        if from >= lower.len() {
            break;
        }
    }
    None
}

// ── Matching ─────────────────────────────────────────────────────────────

fn rank(mut candidates: Vec<Candidate>, title: &str, artist: &str, duration: Option<u32>, prefer_atmos: bool) -> Vec<Candidate> {
    let mut scored: Vec<(Candidate, i32)> = candidates.drain(..).filter_map(|c| score(&c, title, artist, duration, prefer_atmos).map(|s| (c, s))).collect();
    scored.sort_by(|a, b| b.1.cmp(&a.1));
    scored.into_iter().map(|(c, _)| c).collect()
}

fn score(candidate: &Candidate, title: &str, artist: &str, duration: Option<u32>, prefer_atmos: bool) -> Option<i32> {
    let wanted = title_parts(title, artist);
    let got = title_parts(&candidate.title, &candidate.artist);
    if wanted.0.is_empty() || wanted.0 != got.0 || wanted.1 != got.1 {
        return None;
    }
    let target_artists = artist_names(artist);
    let candidate_artists = artist_names(&candidate.artist);
    // A copy that doesn't name the artist is not the same song: never swap the
    // listener's stream for it.
    if !target_artists.is_empty()
        && (candidate_artists.is_empty() || !target_artists.iter().any(|w| candidate_artists.iter().any(|g| same_artist(w, g))))
    {
        return None;
    }
    // Same recording, not a remix, an edit or a sped-up copy that kept the title: the
    // length has to track the YouTube duration to within a few seconds. Trailing silence
    // and fades live inside that; a different cut of the song does not.
    if let (Some(wanted), Some(got)) = (duration, candidate.duration)
        && wanted.abs_diff(got) > VERSION_TOLERANCE_SECS
    {
        return None;
    }
    let artist_score = match (target_artists.is_empty() || candidate_artists.is_empty(), target_artists == candidate_artists) {
        (true, _) => 0,
        (false, true) => 30,
        (false, false) => 20,
    };
    let duration_score = match (duration, candidate.duration) {
        (Some(wanted), Some(got)) => (20 - wanted.abs_diff(got) as i32).max(0),
        _ => 0,
    };
    // With Atmos preferred, a marked Atmos copy always outranks a plain one: the
    // listener asked for it, and every candidate here is already the same song.
    let atmos_score = if prefer_atmos && candidate.atmos { 1000 } else { 0 };
    Some(100 + artist_score + duration_score + atmos_score)
}

/// The title with the packaging taken off, version markers kept.
fn searchable_title(title: &str, artist: &str) -> String {
    let (words, versions) = title_parts(title, artist);
    let mut out = words;
    for version in versions {
        out.push(' ');
        out.push_str(&version);
    }
    out.trim().to_owned()
}

fn primary_artist(artist: &str) -> String {
    artist.to_ascii_lowercase().split([',', '&', '/', ';', '·', '|']).next().unwrap_or("").trim().to_owned()
}

type TitleParts = (String, Vec<String>);

/// A title split into its core words and its version markers.
fn title_parts(raw: &str, artist: &str) -> TitleParts {
    let mut versions = BTreeSet::new();
    let mut text = raw.to_ascii_lowercase().replace('&', " and ");
    for _ in 0..3 {
        let Some((start, end)) = bracket_span(&text) else { break };
        classify(&text[start + 1..end], &mut versions);
        text.replace_range(start..=end, " ");
    }
    if let Some(at) = text.find(['(', '[']) {
        classify(&text[at..], &mut versions);
        text.truncate(at);
    }
    for _ in 0..3 {
        let Some(at) = text.find(" - ") else { break };
        let (head, tail) = (text[..at].to_owned(), text[at + 3..].to_owned());
        if is_artist_name(&head, artist) {
            classify(&head, &mut versions);
            text = tail;
        } else if is_artist_name(&tail, artist) {
            classify(&tail, &mut versions);
            text = head;
        } else {
            classify(&tail, &mut versions);
            text = head;
        }
    }
    for marker in [" feat.", " feat ", " ft.", " ft ", " featuring ", " with "] {
        if let Some(at) = text.find(marker) {
            text.truncate(at);
        }
    }
    let mut words: Vec<String> = text.split([' ', '.', '·']).map(clean).filter(|w| !w.is_empty() && w != "and").collect();
    while words.len() > 1 && matches!(words.last().map(String::as_str), Some("song" | "songs" | "video" | "audio" | "lyrics" | "official" | "full" | "hd" | "hq" | "4k" | "mp3" | "ost" | "soundtrack")) {
        words.pop();
    }
    (words.join(" "), versions.into_iter().collect())
}

fn bracket_span(text: &str) -> Option<(usize, usize)> {
    let open = text.find(['(', '['])?;
    let close = text[open..].find([')', ']'])? + open;
    Some((open, close))
}

fn classify(segment: &str, versions: &mut BTreeSet<String>) {
    let words: Vec<String> = segment.split([' ', '.', '·']).map(clean).filter(|w| !w.is_empty()).collect();
    if words.is_empty() {
        return;
    }
    let joined = words.join("");
    if matches!(joined.as_str(), "albumversion" | "originalversion" | "originalmix" | "singleversion" | "radioversion" | "radioedit" | "stereoversion" | "monoversion" | "studioversion" | "fullversion" | "standardversion" | "explicitversion" | "deluxeversion" | "originaltrack") {
        return;
    }
    for word in words {
        if VERSION_WORDS.contains(&word.as_str()) {
            versions.insert(word);
        }
    }
}

fn is_artist_name(text: &str, artist: &str) -> bool {
    if artist.is_empty() {
        return false;
    }
    let words: Vec<String> = text.split([' ', '.', '·']).map(clean).filter(|w| !w.is_empty()).collect();
    if words.is_empty() {
        return false;
    }
    let credited: Vec<String> = artist.to_ascii_lowercase().split([' ', '.', '·']).map(clean).filter(|w| !w.is_empty()).collect();
    words.iter().all(|w| credited.contains(w))
}

fn artist_names(value: &str) -> Vec<Vec<String>> {
    let normalized = value.to_ascii_lowercase().trim().to_owned();
    if normalized.is_empty() || matches!(normalized.as_str(), "unknown" | "unknown artist" | "unknown artist(s)") {
        return Vec::new();
    }
    normalized
        .split([',', '&', '/', ';', '·', '|'])
        .map(|name| {
            let mut parts: Vec<String> = name.split([' ', '.']).map(clean).filter(|w| w.len() > 1).collect();
            if parts.is_empty() {
                parts = vec![name.trim().to_owned()];
            }
            parts
        })
        .collect::<Vec<Vec<String>>>()
        .into_iter()
        .filter(|parts| parts.iter().any(|w| !w.is_empty()))
        .collect()
}

fn same_artist(first: &[String], second: &[String]) -> bool {
    if contains_words(first, second) || contains_words(second, first) {
        return true;
    }
    // Addons sometimes drop a middle name; accept only when every token of the shorter
    // credited name appears in order in the longer one.
    ordered_subset(first, second) || ordered_subset(second, first)
}

fn ordered_subset(shorter: &[String], longer: &[String]) -> bool {
    if shorter.is_empty() || shorter.len() >= longer.len() {
        return false;
    }
    let mut at = 0;
    for word in shorter {
        let Some(pos) = longer[at..].iter().position(|w| w == word) else { return false };
        at += pos + 1;
    }
    true
}

fn contains_words(outer: &[String], inner: &[String]) -> bool {
    !inner.is_empty() && inner.len() <= outer.len() && (0..=outer.len() - inner.len()).any(|start| outer[start..start + inner.len()] == *inner)
}

fn clean(word: &str) -> String {
    word.chars().filter(|c| c.is_ascii_alphanumeric()).collect()
}

const VERSION_WORDS: &[&str] = &[
    "remix", "remixes", "rmx", "refix", "flip", "bootleg", "mashup", "medley", "live", "concert", "unplugged", "acoustic", "instrumental", "karaoke", "vocals", "vocal",
    "acapella", "acappella", "backing", "stems", "stem", "cover", "demo", "reprise", "remake", "rework", "extended", "edit", "version", "mix", "dub", "vip", "session",
    "sessions", "sped", "slowed", "reverb", "nightcore", "lofi", "orchestral", "symphonic", "part", "pt", "chapter",
];

// ── HTTP ─────────────────────────────────────────────────────────────────

async fn get_json(http: &reqwest::Client, url: &str, timeout: Duration) -> anyhow::Result<Value> {
    // Twice at most: a refusal is asked again under the release line's agent, which the
    // addon does answer, and a second refusal is the addon's real answer to us.
    for attempt in 0..2 {
        let response = http.get(url).timeout(timeout).header("Accept", "application/json").header("User-Agent", agent()).send().await.map_err(addon_err)?;
        let status = response.status();
        if status == reqwest::StatusCode::NOT_FOUND {
            anyhow::bail!("not found");
        }
        if !status.is_success() {
            if status == reqwest::StatusCode::FORBIDDEN {
                if attempt == 0 {
                    use_release_agent();
                    continue;
                }
                anyhow::bail!("the addon doesn't answer this app");
            }
            let body = response.text().await.unwrap_or_default();
            if status == reqwest::StatusCode::UNAUTHORIZED {
                let msg = serde_json::from_str::<Value>(&body).ok()
                    .and_then(|v| v.get("message").and_then(Value::as_str).map(str::to_owned))
                    .unwrap_or_default();
                if msg.contains("official") || body.contains("bitchord_auth") {
                    anyhow::bail!("this addon only works in the official BitChord app (it requires device verification this client can't provide)");
                }
                anyhow::bail!("the addon refused the request (sign-in required)");
            }
            anyhow::bail!("HTTP {}", status.as_u16());
        }
        let bytes = response.bytes().await.map_err(addon_err)?;
        anyhow::ensure!(!bytes.is_empty(), "the addon answered with nothing");
        return Ok(serde_json::from_slice(&bytes)?);
    }
    anyhow::bail!("the addon doesn't answer this app");
}

/// Addon links carry a token: a request failure names its kind, never the URL the
/// reqwest error would print.
fn addon_err(e: reqwest::Error) -> anyhow::Error {
    if e.is_timeout() {
        anyhow::anyhow!("the addon timed out")
    } else if e.is_connect() {
        anyhow::anyhow!("the addon didn't connect")
    } else {
        anyhow::anyhow!("the addon request failed")
    }
}

/// How many bytes the stream holds, from a HEAD or a one-byte range request.
async fn content_size(http: &reqwest::Client, url: &str) -> Option<u64> {
    if let Ok(response) = http.head(url).timeout(PROBE_TIMEOUT).header("User-Agent", agent()).send().await
        && response.status().is_success()
        && let Some(size) = response.content_length().filter(|n| *n > 0)
    {
        return Some(size);
    }
    let response = http.get(url).timeout(PROBE_TIMEOUT).header("User-Agent", agent()).header("Range", "bytes=0-0").header("Accept-Encoding", "identity").send().await.ok()?;
    if let Some(range) = response.headers().get(reqwest::header::CONTENT_RANGE).and_then(|v| v.to_str().ok())
        && let Some(total) = range.rsplit('/').next().and_then(|s| s.trim().parse::<u64>().ok())
    {
        return Some(total);
    }
    response.content_length().filter(|n| *n > 0)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn base_is_normalized_like_the_addon_protocol() {
        assert_eq!(normalize_base(" https://music.example/addon/ "), "https://music.example/addon");
        assert_eq!(normalize_base("https://music.example/addon/manifest.json"), "https://music.example/addon");
        assert_eq!(normalize_base("https://music.example/addon/MANIFEST.JSON"), "https://music.example/addon");
        assert_eq!(normalize_base("https://music.example"), "https://music.example");
        // A manifest link with a token query is still a manifest link.
        assert_eq!(normalize_base("https://music.example/addon/manifest.json?t=abc"), "https://music.example/addon?t=abc");
    }

    #[test]
    fn quality_labels_are_read() {
        assert_eq!(number_before("Hi-Res FLAC 24-bit 96 kHz", "khz"), Some(96.0));
        assert_eq!(number_before("Hi-Res FLAC 24-bit 96 kHz", "bit"), Some(24.0));
        assert_eq!(number_before("320 kbps", "kbps"), Some(320.0));
        assert_eq!(number_before("lossless", "kbps"), None);
    }

    #[test]
    fn a_row_matches_only_its_own_recording() {
        let target = |title: &str| Candidate {
            id: "1".into(),
            title: title.into(),
            artist: "Carly Rae Jepsen".into(),
            duration: Some(200),
            quality: String::new(),
            format: String::new(),
            provider: String::new(),
            atmos: false,
            stream_url: None,
        };
        let pick = |title: &str| rank(vec![target(title)], "Call Me Maybe", "Carly Rae Jepsen", Some(202), false).len();
        assert_eq!(pick("Call Me Maybe"), 1);
        assert_eq!(pick("Call Me Maybe (Live at Wembley)"), 0);
        assert_eq!(pick("Call Me Maybe (Official Audio)"), 1);
    }

    #[test]
    fn a_copy_without_the_artist_is_not_the_song() {
        let row = |artist: &str| Candidate {
            id: "1".into(),
            title: "Call Me Maybe".into(),
            artist: artist.into(),
            duration: Some(200),
            quality: String::new(),
            format: String::new(),
            provider: String::new(),
            atmos: false,
            stream_url: None,
        };
        let pick = |artist: &str| rank(vec![row(artist)], "Call Me Maybe", "Carly Rae Jepsen", Some(202), false).len();
        assert_eq!(pick(""), 0);
        assert_eq!(pick("Someone Else"), 0);
        assert_eq!(pick("Carly Rae Jepsen"), 1);
        assert_eq!(pick("Carly Jepsen"), 1);
    }

    #[test]
    fn a_retempoed_copy_of_the_same_song_is_not_the_song() {
        // Same title and artist, only the length differs: an unlabelled sped-up or
        // slowed version must not replace the listener's stream.
        let row = |secs: u32| Candidate {
            id: "1".into(),
            title: "Call Me Maybe".into(),
            artist: "Carly Rae Jepsen".into(),
            duration: Some(secs),
            quality: String::new(),
            format: String::new(),
            provider: String::new(),
            atmos: false,
            stream_url: None,
        };
        let pick = |secs: u32| rank(vec![row(secs)], "Call Me Maybe", "Carly Rae Jepsen", Some(202), false).len();
        assert_eq!(pick(200), 1); // a couple of seconds of trailing silence: the same take
        assert_eq!(pick(205), 1); // still inside the three-second tolerance
        assert_eq!(pick(206), 0); // past it, even for a song this long
        assert_eq!(pick(160), 0); // sped-up: a shorter take of the same title
        assert_eq!(pick(250), 0); // slowed: a longer one
        // The tolerance is seconds, not a slice, so it doesn't loosen for a short song.
        assert_eq!(rank(vec![row(93)], "Call Me Maybe", "Carly Rae Jepsen", Some(90), false).len(), 1);
        assert_eq!(rank(vec![row(94)], "Call Me Maybe", "Carly Rae Jepsen", Some(90), false).len(), 0);
    }

    #[test]
    fn a_stream_measured_off_the_song_is_the_wrong_cut_of_it() {
        assert_eq!(off_version(0, Some(202)), None); // no measurement to trust
        assert_eq!(off_version(202_000, Some(202)), None);
        assert_eq!(off_version(204_900, Some(202)), None); // inside the tolerance
        assert_eq!(off_version(205_600, Some(202)), Some(4)); // past it, rounded
        assert_eq!(off_version(180_000, Some(202)), Some(22)); // a longer edit
        assert_eq!(off_version(202_000, None), None);
    }

    #[test]
    fn a_preferred_atmos_copy_leads_even_against_a_better_matched_plain_one() {
        let row = |id: &str, artist: &str, atmos: bool| Candidate {
            id: id.into(),
            title: "Call Me Maybe".into(),
            artist: artist.into(),
            duration: Some(200),
            quality: String::new(),
            format: String::new(),
            provider: String::new(),
            atmos,
            stream_url: None,
        };
        let rows = vec![row("plain", "Carly Rae Jepsen", false), row("atmos", "Carly Jepsen", true)];
        let ranked = rank(rows, "Call Me Maybe", "Carly Rae Jepsen", Some(202), true);
        assert_eq!(ranked[0].id, "atmos");
        // Without the preference the exact artist spelling still wins.
        let rows = vec![row("plain", "Carly Rae Jepsen", false), row("atmos", "Carly Jepsen", true)];
        assert_eq!(rank(rows, "Call Me Maybe", "Carly Rae Jepsen", Some(202), false)[0].id, "plain");
    }

    #[test]
    fn the_badge_says_what_the_stream_is() {
        let info = |codec: &str, rate: Option<u32>, bits: Option<u32>| Info {
            source: "Addon".into(),
            host: "example".into(),
            codec: codec.into(),
            quality: String::new(),
            bitrate_kbps: None,
            sample_rate: rate,
            bit_depth: bits,
            channels: None,
            atmos: false,
            size: 1,
            duration_ms: 1,
        };
        assert_eq!(info("FLAC", Some(44_100), Some(16)).badge(), "Lossless");
        assert_eq!(info("FLAC", Some(96_000), Some(24)).badge(), "Hi-Res Lossless");
        assert_eq!(info("MP3", None, None).badge(), "High");
        let atmos = Info { atmos: true, ..info("EAC3", None, None) };
        assert_eq!(atmos.badge(), "Dolby Atmos");
    }

    #[test]
    fn the_quality_line_is_the_badge_then_the_known_numbers() {
        let info = |codec: &str, rate: Option<u32>, bits: Option<u32>| Info {
            source: "Addon".into(),
            host: "example".into(),
            codec: codec.into(),
            quality: String::new(),
            bitrate_kbps: None,
            sample_rate: rate,
            bit_depth: bits,
            channels: None,
            atmos: false,
            size: 1,
            duration_ms: 1,
        };
        let hi_res = Info {
            bitrate_kbps: Some(1641),
            channels: Some(2),
            ..info("FLAC", Some(44_100), Some(24))
        };
        assert_eq!(hi_res.details_line(), "Hi-Res Lossless · FLAC · 1641 kbps · 24-bit · 44.1 kHz · Stereo");
        let atmos = Info {
            atmos: true,
            bitrate_kbps: Some(768),
            channels: Some(6),
            ..info("E-AC-3 JOC", Some(48_000), None)
        };
        assert_eq!(atmos.details_line(), "Dolby Atmos · E-AC-3 JOC · 768 kbps · 48 kHz · 6 ch");
    }

    #[tokio::test]
    #[ignore]
    async fn live_chain_picks_atmos_then_lossless() {
        let Ok(link) = std::env::var("YTM_HIRES_TEST") else { return };
        let http = reqwest::Client::builder().user_agent(USER_AGENT).build().unwrap();
        let client = std::sync::Arc::new(Client::new(&link).expect("an addon link"));
        // The settings row's test, first: it says what the addon really serves.
        match check(&http, &link).await {
            Ok(summary) => println!("check: {summary}"),
            Err(e) => println!("check failed: {e:#}"),
        }
        let track = |title: &str, artist: &str, duration: u32| -> crate::model::Track {
            serde_json::from_value(serde_json::json!({
                "id": format!("{title}-{artist}"),
                "title": title,
                "artists": [{"name": artist, "id": null}],
                "album": null,
                "duration": duration,
                "thumbs": [],
                "track_nr": null,
                "plays": null,
            }))
            .unwrap()
        };
        for (title, artist, secs) in [("Apple", "Charli xcx", 152u32), ("Good Luck, Babe!", "Chappell Roan", 218), ("Sailing Ships", "Riverside Drift", 247)] {
            for atmos in [true, false] {
                // The picks probe says why a lookup missed (errors are URL-free by then).
                match client.picks(&http, &track(title, artist, secs), atmos).await {
                    Ok(picks) => println!("atmos={atmos} {title}: {} candidate(s)", picks.len()),
                    Err(e) => println!("atmos={atmos} {title}: picks failed: {e:#}"),
                }
                match find_all(&http, std::slice::from_ref(&client), &track(title, artist, secs), atmos, false).await {
                    Some(s) => println!("atmos={atmos} {title}: atmos={} badge={} codec={} host={}", s.info.atmos, s.info.badge(), s.info.codec, s.info.host),
                    None => println!("atmos={atmos} {title}: nothing"),
                }
            }
        }
    }

    #[test]
    fn atmos_rows_are_read_off_the_names_addons_use() {
        let row = |json: Value| json_is_atmos(&json);
        assert!(row(serde_json::json!({"audioMode": "DOLBY_ATMOS"})));
        assert!(row(serde_json::json!({"audioModes": ["STEREO", "DOLBY_ATMOS"]})));
        assert!(row(serde_json::json!({"quality": "E-AC-3 JOC"})));
        assert!(!row(serde_json::json!({"audioMode": "STEREO", "format": "FLAC"})));
    }
}
