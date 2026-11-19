//! Bit-perfect output: lossless tracks straight to the selected DAC in WASAPI
//! exclusive mode, with no Windows mixer and no app DSP in the path.
//!
//! Like the Android app, this is for lossless PCM only. It carries one to two
//! channels at the track's own sample rate; the device is asked for the source's
//! bit depth (or the closest exact representation). If the DAC can't take that
//! format, the caller falls back to the normal output instead of resampling.
//!
//! Volume, EQ, normalization, fades, crossfade and Automix are not applied:
//! the samples go to the device untouched. Volume belongs to the DAC.

use std::sync::Arc;
#[cfg(windows)]
use std::sync::atomic::Ordering;
use std::time::Duration;

use crossbeam_channel::Sender;
#[cfg(windows)]
use crossbeam_channel::{Receiver, bounded, unbounded};

use crate::audio::{AudioEvent, AudioStatus};
use crate::stream::{Format, Shared, TrackDecoder};

/// Whether this stream is one bit-perfect output can take: lossless PCM, one or two
/// channels. Lossy streams (YouTube's Opus/AAC) and decoded Atmos stay on the normal
/// path, as on Android.
pub fn accepts(shared: &Shared, decoder: &TrackDecoder) -> bool {
    if !cfg!(windows) || !lossless(shared) {
        return false;
    }
    let channels = rodio::Source::channels(decoder).get();
    (1..=2).contains(&channels) && rodio::Source::sample_rate(decoder).get() > 0
}

fn lossless(shared: &Shared) -> bool {
    if shared.info.as_ref().is_some_and(|info| info.atmos) {
        return false;
    }
    if shared.info.as_ref().is_some_and(|info| info.lossless()) {
        return true;
    }
    matches!(&shared.format,
        Format::File { hint, .. } if matches!(hint.to_ascii_lowercase().as_str(), "flac" | "alac" | "wav" | "aiff"))
}

/// What the caller gets back once the exclusive stream is running.
pub struct Started {
    pub output: BitPerfect,
    pub rate: u32,
    pub bits: u32,
}

/// A running exclusive stream. Dropping it stops and joins the render thread.
pub struct BitPerfect {
    #[cfg(windows)]
    tx: Option<Sender<Cmd>>,
    #[cfg(windows)]
    thread: Option<std::thread::JoinHandle<()>>,
}

impl BitPerfect {
    pub fn pause(&self) {
        #[cfg(windows)]
        if let Some(tx) = &self.tx {
            let _ = tx.send(Cmd::Pause);
        }
    }

    pub fn resume(&self) {
        #[cfg(windows)]
        if let Some(tx) = &self.tx {
            let _ = tx.send(Cmd::Resume);
        }
    }

    pub fn seek(&self, at: Duration) {
        #[cfg(windows)]
        if let Some(tx) = &self.tx {
            let _ = tx.send(Cmd::Seek(at));
        }
    }

    pub fn stop(&mut self) {
        #[cfg(windows)]
        {
            if let Some(tx) = self.tx.take() {
                let _ = tx.send(Cmd::Stop);
            }
            if let Some(thread) = self.thread.take() {
                let _ = thread.join();
            }
        }
    }
}

impl Drop for BitPerfect {
    fn drop(&mut self) {
        self.stop();
    }
}

/// Open the selected device (or the default one) exclusively at the track's own
/// format and start rendering. On failure the decoder is handed back so the caller
/// can play the track through the normal output.
pub fn open(
    device: Option<&str>,
    decoder: TrackDecoder,
    shared: Arc<Shared>,
    status: Arc<AudioStatus>,
    events: Sender<AudioEvent>,
    generation: u64,
    at: Duration,
) -> Result<Started, (Option<TrackDecoder>, anyhow::Error)> {
    #[cfg(not(windows))]
    {
        let _ = (device, shared, status, events, generation, at);
        return Err((Some(decoder), anyhow::anyhow!("bit-perfect output is Windows-only")));
    }

    #[cfg(windows)]
    {
        let rate = rodio::Source::sample_rate(&decoder).get();
        let channels = rodio::Source::channels(&decoder).get() as usize;
        let bits = shared.info.as_ref().and_then(|info| info.bit_depth);
        let slot = Arc::new(std::sync::Mutex::new(Some(decoder)));
        let (cmd_tx, cmd_rx) = unbounded();
        let (ready_tx, ready_rx) = bounded(1);
        let thread = std::thread::Builder::new()
            .name("bit-perfect".into())
            .spawn({
                let slot = slot.clone();
                let device = device.map(str::to_owned);
                let status = status.clone();
                move || render(device, rate, channels, bits, slot, cmd_rx, ready_tx, status, events, generation, at)
            })
            .map_err(|e| (None, anyhow::anyhow!("spawn bit-perfect output: {e}")))?;
        match ready_rx.recv_timeout(Duration::from_secs(5)) {
            Ok(Ok((rate, bits))) => {
                // The renderer owns the decoder from here on.
                Ok(Started { output: BitPerfect { tx: Some(cmd_tx), thread: Some(thread) }, rate, bits })
            }
            Ok(Err(e)) => {
                let _ = cmd_tx.send(Cmd::Stop);
                let _ = thread.join();
                let decoder = slot.lock().unwrap().take();
                Err((decoder, anyhow::anyhow!(e)))
            }
            Err(_) => {
                let _ = cmd_tx.send(Cmd::Stop);
                let _ = thread.join();
                let decoder = slot.lock().unwrap().take();
                Err((decoder, anyhow::anyhow!("the DAC did not open in 5 s")))
            }
        }
    }
}

#[derive(Clone, Copy)]
enum Cmd {
    Pause,
    Resume,
    Seek(Duration),
    Stop,
}

#[cfg(windows)]
type Ready = Result<(u32, u32), String>;

#[cfg(windows)]
#[allow(clippy::too_many_arguments)]
fn render(
    device: Option<String>,
    rate: u32,
    channels: usize,
    source_bits: Option<u32>,
    decoder_slot: Arc<std::sync::Mutex<Option<TrackDecoder>>>,
    cmd_rx: Receiver<Cmd>,
    ready: Sender<Ready>,
    status: Arc<AudioStatus>,
    events: Sender<AudioEvent>,
    generation: u64,
    at: Duration,
) {
    match render_inner(device, rate, channels, source_bits, &decoder_slot, &cmd_rx, &ready, &status, &events, generation, at) {
        Ok(()) => {}
        Err(e) => {
            // Wake `open` at once if this failed before the stream was ready.
            let _ = ready.send(Err(e.to_string()));
            status.playing.store(false, Ordering::Relaxed);
            status.bit_perfect.store(false, Ordering::Relaxed);
            let _ = events.send(AudioEvent::Error(format!("Bit-perfect output stopped: {e}")));
        }
    }
}

#[cfg(windows)]
#[allow(clippy::too_many_arguments)]
fn render_inner(
    device: Option<String>,
    rate: u32,
    channels: usize,
    source_bits: Option<u32>,
    decoder_slot: &Arc<std::sync::Mutex<Option<TrackDecoder>>>,
    cmd_rx: &Receiver<Cmd>,
    ready: &Sender<Ready>,
    status: &Arc<AudioStatus>,
    events: &Sender<AudioEvent>,
    generation: u64,
    at: Duration,
) -> anyhow::Result<()> {
    use wasapi::{DeviceEnumerator, Direction, StreamMode, initialize_mta};
    use windows::Win32::Media::Audio::AUDCLNT_E_BUFFER_SIZE_NOT_ALIGNED;

    initialize_mta().ok().map_err(|e| anyhow::anyhow!("COM: {e}"))?;
    let enumerator = DeviceEnumerator::new().map_err(|e| anyhow::anyhow!("WASAPI device list: {e}"))?;
    let device = match device.as_deref() {
        Some(id) => {
            let id = id.strip_prefix("wasapi:").unwrap_or(id);
            enumerator.get_device(id).or_else(|_| enumerator.get_default_device(Direction::Render))
        }
        None => enumerator.get_default_device(Direction::Render),
    }
    .map_err(|e| anyhow::anyhow!("open DAC: {e}"))?;
    let mut client = device.get_iaudioclient().map_err(|e| anyhow::anyhow!("WASAPI client: {e}"))?;

    let (format, kind) = choose_format(&client, rate as usize, channels, source_bits).map_err(|e| anyhow::anyhow!("{e}"))?;
    let rate = format.get_samplespersec();
    let bits = format.get_validbitspersample().max(1) as u32;
    let (default_period, min_period) = client.get_device_period().map_err(|e| anyhow::anyhow!("device period: {e}"))?;
    let period = client.calculate_aligned_period_near(3 * min_period / 2, Some(128), &format).unwrap_or(default_period);
    let mode = StreamMode::EventsExclusive { period_hns: period };
    if let Err(e) = client.initialize_client(&format, Direction::Render, mode, None) {
        if let wasapi::WasapiError::Windows(werr) = &e
            && werr.code() == AUDCLNT_E_BUFFER_SIZE_NOT_ALIGNED
        {
            let frames = client.get_buffer_size().map_err(|e| anyhow::anyhow!("buffer size: {e}"))?;
            let aligned = wasapi::calculate_period_100ns(frames as i64, format.get_samplespersec() as i64);
            let mut retry = device.get_iaudioclient().map_err(|e| anyhow::anyhow!("WASAPI client: {e}"))?;
            retry
                .initialize_client(&format, Direction::Render, StreamMode::EventsExclusive { period_hns: aligned }, None)
                .map_err(|e| anyhow::anyhow!("exclusive mode: {e}"))?;
            client = retry;
        } else {
            return Err(anyhow::anyhow!("exclusive mode: {e}"));
        }
    }
    let event = client.set_get_eventhandle().map_err(|e| anyhow::anyhow!("event handle: {e}"))?;
    let renderer = client.get_audiorenderclient().map_err(|e| anyhow::anyhow!("render client: {e}"))?;
    let frame_bytes = kind.bytes_per_frame(channels) as usize;
    let blockalign = format.get_blockalign() as usize;
    if blockalign != frame_bytes {
        return Err(anyhow::anyhow!("unexpected block alignment {blockalign} for {frame_bytes}"));
    }
    // Start from silence; the loop writes the first real frames on the first event.
    let space = client.get_available_space_in_frames().map_err(|e| anyhow::anyhow!("buffer: {e}"))? as usize;
    if space > 0 {
        renderer.write_to_device(&vec![0u8; space * blockalign], None).map_err(|e| anyhow::anyhow!("write: {e}"))?;
    }
    client.start_stream().map_err(|e| anyhow::anyhow!("start: {e}"))?;
    let mut decoder = decoder_slot
        .lock()
        .unwrap()
        .take()
        .ok_or_else(|| anyhow::anyhow!("the stream went away before it started"))?;
    status.bit_perfect.store(true, Ordering::Relaxed);
    status.bit_perfect_rate.store(rate, Ordering::Relaxed);
    status.bit_perfect_bits.store(bits, Ordering::Relaxed);
    status.playing.store(true, Ordering::Relaxed);
    status.set_position(at.as_millis() as u64);
    let _ = ready.send(Ok((rate, bits)));

    let mut base = at;
    let mut written: u64 = 0;
    let mut paused = false;
    let mut done = false;
    let mut buf = Vec::new();
    loop {
        while let Ok(cmd) = cmd_rx.try_recv() {
            match cmd {
                Cmd::Stop => {
                    let _ = client.stop_stream();
                    status.playing.store(false, Ordering::Relaxed);
                    status.bit_perfect.store(false, Ordering::Relaxed);
                    return Ok(());
                }
                Cmd::Pause => {
                    if !paused {
                        let _ = client.stop_stream();
                        paused = true;
                        status.playing.store(false, Ordering::Relaxed);
                    }
                }
                Cmd::Resume => {
                    if paused {
                        let _ = client.start_stream();
                        paused = false;
                        status.playing.store(true, Ordering::Relaxed);
                    }
                }
                Cmd::Seek(target) => {
                    if seek(&mut decoder, target) {
                        base = target;
                        written = 0;
                        status.set_position(target.as_millis() as u64);
                    } else {
                        log::warn!("bit-perfect: the decoder won't seek to {target:?}");
                    }
                }
            }
        }
        if paused {
            std::thread::sleep(Duration::from_millis(20));
            continue;
        }
        let _ = event.wait_for_event(50);
        let Ok(space) = client.get_available_space_in_frames() else { continue };
        let space = space as usize;
        if space == 0 {
            continue;
        }
        buf.clear();
        buf.reserve(space * blockalign);
        let mut real = 0usize;
        for _ in 0..space {
            let mut frame = [0.0f32; 2];
            let mut got = true;
            for channel in 0..channels {
                match decoder.next() {
                    Some(sample) => frame[channel] = sample,
                    None => {
                        got = false;
                        break;
                    }
                }
            }
            for channel in 0..channels {
                kind.write(&mut buf, if got { frame[channel] } else { 0.0 });
            }
            if got {
                real += 1;
            } else {
                done = true;
            }
        }
        if real > 0 {
            renderer.write_to_device(&buf, None).map_err(|e| anyhow::anyhow!("write: {e}"))?;
            written += real as u64;
            status.set_position(base.as_millis() as u64 + written * 1000 / rate as u64);
        } else if !done {
            // Nothing decoded yet: keep the buffer fed with silence.
            renderer.write_to_device(&buf, None).map_err(|e| anyhow::anyhow!("write: {e}"))?;
        }
        if done {
            // Let the last real frames in the device buffer play out before closing.
            std::thread::sleep(Duration::from_secs_f64(space as f64 / rate as f64));
            let _ = client.stop_stream();
            status.playing.store(false, Ordering::Relaxed);
            status.bit_perfect.store(false, Ordering::Relaxed);
            let _ = events.send(AudioEvent::Finished { generation });
            return Ok(());
        }
    }
}

#[cfg(windows)]
fn choose_format(
    client: &wasapi::AudioClient,
    rate: usize,
    channels: usize,
    source_bits: Option<u32>,
) -> anyhow::Result<(wasapi::WaveFormat, SampleKind)> {
    use wasapi::{SampleType, WaveFormat};

    let candidates: &[(usize, usize, SampleType)] = match source_bits {
        Some(16) => &[(16, 16, SampleType::Int), (32, 32, SampleType::Float)],
        Some(24) => &[(24, 24, SampleType::Int), (32, 24, SampleType::Int), (32, 32, SampleType::Float)],
        Some(32) => &[(32, 32, SampleType::Int), (32, 32, SampleType::Float)],
        _ => &[(32, 32, SampleType::Float), (32, 32, SampleType::Int), (24, 24, SampleType::Int)],
    };
    let mut last = String::from("no format tried");
    for &(store, valid, sample_type) in candidates {
        let wanted = WaveFormat::new(store, valid, sample_type, rate, channels, None);
        match client.is_supported_exclusive_with_quirks(&wanted) {
            Ok(format) => {
                if format.get_samplespersec() as usize != rate || format.get_nchannels() as usize != channels {
                    last = "the DAC offered a different rate or channel count".into();
                    continue;
                }
                match SampleKind::of(&format) {
                    Some(kind) => return Ok((format, kind)),
                    None => last = "the DAC offered a sample layout this output can't write".into(),
                }
            }
            Err(e) => last = e.to_string(),
        }
    }
    anyhow::bail!("{rate} Hz, {channels} ch exclusive format not accepted by the DAC ({last})")
}

#[derive(Clone, Copy, Debug, PartialEq)]
enum SampleKind {
    I16,
    /// 24 bits packed into three bytes.
    I24,
    /// 24 valid bits in a four-byte container, left aligned.
    I24In32,
    I32,
    F32,
}

impl SampleKind {
    #[cfg(windows)]
    fn of(format: &wasapi::WaveFormat) -> Option<Self> {
        use wasapi::SampleType;
        let store = format.get_bitspersample() as usize;
        let valid = format.get_validbitspersample() as usize;
        match (format.get_subformat().ok()?, store, valid) {
            (SampleType::Float, 32, _) => Some(Self::F32),
            (SampleType::Int, 16, _) => Some(Self::I16),
            (SampleType::Int, 24, _) => Some(Self::I24),
            (SampleType::Int, 32, 24) => Some(Self::I24In32),
            (SampleType::Int, 32, _) => Some(Self::I32),
            _ => None,
        }
    }

    fn bytes_per_frame(self, channels: usize) -> u32 {
        let sample = match self {
            Self::I16 => 2,
            Self::I24 => 3,
            Self::I24In32 | Self::I32 | Self::F32 => 4,
        };
        (sample * channels) as u32
    }

    fn write(self, out: &mut Vec<u8>, sample: f32) {
        let sample = sample.clamp(-1.0, 1.0);
        match self {
            Self::I16 => {
                let v = (sample * 32768.0).round().clamp(-32768.0, 32767.0) as i16;
                out.extend_from_slice(&v.to_le_bytes());
            }
            Self::I24 => {
                let v = (sample * 8_388_608.0).round().clamp(-8_388_608.0, 8_388_607.0) as i32;
                let bytes = v.to_le_bytes();
                out.extend_from_slice(&bytes[..3]);
            }
            Self::I24In32 => {
                let v = ((sample * 8_388_608.0).round().clamp(-8_388_608.0, 8_388_607.0) as i32) << 8;
                out.extend_from_slice(&v.to_le_bytes());
            }
            Self::I32 => {
                let v = (sample as f64 * 2_147_483_648.0).round().clamp(-2_147_483_648.0, 2_147_483_647.0) as i32;
                out.extend_from_slice(&v.to_le_bytes());
            }
            Self::F32 => out.extend_from_slice(&sample.to_le_bytes()),
        }
    }
}

fn seek(decoder: &mut TrackDecoder, at: Duration) -> bool {
    match decoder {
        TrackDecoder::Rodio(d) => rodio::Source::try_seek(d, at).is_ok(),
        // The custom Opus and decoded-PCM sources have no seek; a lossless
        // stream always takes the Rodio decoder above.
        TrackDecoder::Opus(_) | TrackDecoder::Pcm(_) => false,
    }
}

#[cfg(test)]
mod tests {
    use super::{SampleKind, lossless};

    #[test]
    fn sample_layouts_write_the_bytes_wasapi_expects() {
        let mut out = Vec::new();
        SampleKind::I16.write(&mut out, 0.5);
        assert_eq!(out, [0x00, 0x40]);
        out.clear();
        SampleKind::I24.write(&mut out, 0.5);
        assert_eq!(out, [0x00, 0x00, 0x40]);
        out.clear();
        SampleKind::I24In32.write(&mut out, 0.5);
        assert_eq!(out, [0x00, 0x00, 0x00, 0x40]);
        out.clear();
        SampleKind::F32.write(&mut out, 0.5);
        assert_eq!(out, 0.5f32.to_le_bytes());
    }

    #[test]
    fn only_lossless_streams_are_bit_perfect() {
        let shared = |format, info| crate::stream::Shared::new(1, 0, None, format, info, false);
        assert!(!lossless(&shared(crate::stream::Format::Opus, None)));
        assert!(!lossless(&shared(crate::stream::Format::Aac, None)));
        assert!(lossless(&shared(crate::stream::Format::File { hint: "flac", mime: None }, None)));
        assert!(!lossless(&shared(crate::stream::Format::File { hint: "mp3", mime: None }, None)));
    }
}
