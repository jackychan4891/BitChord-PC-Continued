//! Apple's motion sleeves, played locally.
//!
//! The clip is HLS, but each variant's media playlist is a single byte-ranged
//! fragmented MP4, so it downloads as one file and Media Foundation decodes it
//! off the UI thread. Only the newest frame is kept: the UI turns it into a
//! texture and draws it in place of the still sleeve.

use std::collections::HashMap;
use std::path::PathBuf;
use std::sync::atomic::{AtomicBool, AtomicU64, Ordering};
use std::sync::{Arc, LazyLock, Mutex, Weak};
use std::time::{Duration, Instant};

pub struct Frame {
    pub w: usize,
    pub h: usize,
    pub rgba: Vec<u8>,
}

pub struct Motion {
    url: String,
    latest: Mutex<Option<Arc<Frame>>>,
    version: AtomicU64,
    stop: Arc<AtomicBool>,
}

impl Motion {
    fn new(url: &str) -> Self {
        Self { url: url.to_owned(), latest: Mutex::new(None), version: AtomicU64::new(0), stop: Arc::new(AtomicBool::new(false)) }
    }

    /// The newest frame, with the counter that says it changed.
    pub fn latest(&self) -> Option<(u64, Arc<Frame>)> {
        let frame = self.latest.lock().ok()?.clone()?;
        Some((self.version.load(Ordering::Acquire), frame))
    }

    pub fn url(&self) -> &str {
        &self.url
    }

    /// Stop decoding: the page that showed this clip is gone.
    pub fn stop(&self) {
        self.stop.store(true, Ordering::Relaxed);
    }

    fn publish(&self, ctx: &egui::Context, w: usize, h: usize, rgba: Vec<u8>) {
        if let Ok(mut slot) = self.latest.lock() {
            *slot = Some(Arc::new(Frame { w, h, rgba }));
        }
        self.version.fetch_add(1, Ordering::Release);
        ctx.request_repaint();
    }
}

impl Drop for Motion {
    fn drop(&mut self) {
        self.stop.store(true, Ordering::Relaxed);
    }
}

static STREAMS: LazyLock<Mutex<HashMap<String, Weak<Motion>>>> = LazyLock::new(Default::default);

/// Start (or reuse) the clip at `master`, decoded no wider than `max_width`.
pub fn play(ctx: &egui::Context, master: &str, max_width: u32) -> Arc<Motion> {
    let key = format!("{max_width}:{master}");
    if let Ok(mut streams) = STREAMS.lock() {
        if let Some(m) = streams.get(&key).and_then(Weak::upgrade) {
            return m;
        }
        let motion = Arc::new(Motion::new(master));
        streams.insert(key, Arc::downgrade(&motion));
        let (this, ctx, url) = (motion.clone(), ctx.clone(), master.to_owned());
        let _ = std::thread::Builder::new().name("motion".into()).spawn(move || run(this, ctx, url, max_width));
        return motion;
    }
    Arc::new(Motion::new(master))
}

fn run(motion: Arc<Motion>, ctx: egui::Context, master: String, max_width: u32) {
    let Some(path) = fetch(&master, max_width) else {
        log::info!("apple: no motion clip downloaded");
        return;
    };
    decode(&motion, &path, &ctx);
}

/// Pick a variant and save the clip to the cache; a saved copy is reused.
fn fetch(master: &str, max_width: u32) -> Option<PathBuf> {
    let rt = tokio::runtime::Builder::new_current_thread().enable_all().build().ok()?;
    rt.block_on(async {
        use sha1::{Digest, Sha1};
        let http = crate::proxy::http();
        let media = crate::apple::variant(&http, master, max_width).await?;
        let file = crate::apple::media_file(&http, &media).await?;
        let dir = crate::data_dir().join("motion");
        std::fs::create_dir_all(&dir).ok()?;
        let name = format!("{:x}", Sha1::digest(file.as_bytes()));
        let path = dir.join(format!("{}.mp4", &name[..32]));
        if std::fs::metadata(&path).map(|m| m.len()).unwrap_or(0) > 1024 {
            return Some(path);
        }
        let bytes = http
            .get(&file)
            .timeout(Duration::from_secs(30))
            .send()
            .await
            .ok()?
            .error_for_status()
            .ok()?
            .bytes()
            .await
            .ok()?;
        let tmp = path.with_extension("part");
        std::fs::write(&tmp, &bytes).ok()?;
        std::fs::rename(&tmp, &path).ok()?;
        log::info!("apple: motion clip {:.0} KB cached", bytes.len() as f64 / 1024.0);
        Some(path)
    })
}

#[cfg(windows)]
fn decode(motion: &Arc<Motion>, path: &std::path::Path, ctx: &egui::Context) {
    use windows::Win32::Media::MediaFoundation::{MFShutdown, MFStartup, MFSTARTUP_FULL, MF_VERSION};
    use windows::Win32::System::Com::{COINIT_MULTITHREADED, CoInitializeEx, CoUninitialize};
    unsafe {
        if CoInitializeEx(None, COINIT_MULTITHREADED).is_err() {
            return;
        }
        if MFStartup(MF_VERSION, MFSTARTUP_FULL).is_err() {
            CoUninitialize();
            return;
        }
        let mut wide: Vec<u16> = path.to_string_lossy().encode_utf16().collect();
        wide.push(0);
        let url = windows::core::PCWSTR(wide.as_ptr());
        // Each pass plays the clip once; then it starts again from the top.
        for _ in 0..100 {
            if motion.stop.load(Ordering::Relaxed) || !play_once(motion, ctx, url) {
                break;
            }
        }
        let _ = MFShutdown();
        CoUninitialize();
    }
}

#[cfg(windows)]
unsafe fn play_once(motion: &Motion, ctx: &egui::Context, url: windows::core::PCWSTR) -> bool {
    use windows::Win32::Media::MediaFoundation::*;
    let played = unsafe {
        (|| -> windows::core::Result<bool> {
        let mut attrs = None;
        MFCreateAttributes(&mut attrs, 1)?;
        let attrs = attrs.unwrap();
        // Lets the reader hand back RGB32 rather than the decoder's NV12.
        attrs.SetUINT32(&MF_SOURCE_READER_ENABLE_VIDEO_PROCESSING, 1)?;
        let reader = MFCreateSourceReaderFromURL(url, &attrs)?;
        let stream = MF_SOURCE_READER_FIRST_VIDEO_STREAM.0 as u32;
        reader.SetStreamSelection(MF_SOURCE_READER_ALL_STREAMS.0 as u32, false)?;
        reader.SetStreamSelection(stream, true)?;
        let ty = MFCreateMediaType()?;
        ty.SetGUID(&MF_MT_MAJOR_TYPE, &MFMediaType_Video)?;
        ty.SetGUID(&MF_MT_SUBTYPE, &MFVideoFormat_RGB32)?;
        reader.SetCurrentMediaType(stream, None, &ty)?;
        let current = reader.GetCurrentMediaType(stream)?;
        let size = current.GetUINT64(&MF_MT_FRAME_SIZE)?;
        let (w, h) = ((size >> 32) as usize, (size & 0xffff_ffff) as usize);
        if w == 0 || h == 0 {
            return Ok(false);
        }
        let stride = current.GetUINT32(&MF_MT_DEFAULT_STRIDE).map(|s| s as i32).unwrap_or(-(w as i32 * 4));
        let row = w * 4;
        let start = Instant::now();
        let mut base: Option<i64> = None;
        loop {
            if motion.stop.load(Ordering::Relaxed) {
                return Ok(false);
            }
            let (mut flags, mut ts) = (0u32, 0i64);
            let mut sample: Option<IMFSample> = None;
            reader.ReadSample(stream, 0, None, Some(&mut flags), Some(&mut ts), Some(&mut sample))?;
            if flags & MF_SOURCE_READERF_ERROR.0 as u32 != 0 {
                return Ok(false);
            }
            if flags & MF_SOURCE_READERF_ENDOFSTREAM.0 as u32 != 0 {
                return Ok(true);
            }
            let Some(sample) = sample else { continue };
            let base = *base.get_or_insert(ts);
            // Timestamps are in 100 ns units.
            let due = Duration::from_micros(((ts - base).max(0) / 10) as u64);
            let elapsed = start.elapsed();
            if due > elapsed {
                std::thread::sleep(due - elapsed);
            }
            let buffer = sample.ConvertToContiguousBuffer()?;
            let (mut src, mut len) = (std::ptr::null_mut(), 0u32);
            buffer.Lock(&mut src, None, Some(&mut len))?;
            let mut rgba = vec![0u8; row * h];
            if !src.is_null() && len as usize >= row * h {
                let data = std::slice::from_raw_parts(src, len as usize);
                let (stride, bottom_up) = layout(w, h, data.len(), stride);
                for y in 0..h {
                    let o = if bottom_up { h - 1 - y } else { y } * stride;
                    if o + row > data.len() {
                        break;
                    }
                    for x in 0..w {
                        let i = o + x * 4;
                        let j = (y * w + x) * 4;
                        rgba[j] = data[i + 2];
                        rgba[j + 1] = data[i + 1];
                        rgba[j + 2] = data[i];
                        rgba[j + 3] = 255;
                    }
                }
            }
            buffer.Unlock()?;
            if motion.version.load(Ordering::Acquire) == 0 {
                log::info!("apple: motion playing at {w}x{h}");
            }
            motion.publish(ctx, w, h, rgba);
        }
    })()
    };
    match played {
        Ok(ok) => ok,
        Err(e) => {
            log::warn!("apple: motion decode failed: {e}");
            false
        }
    }
}

#[cfg(not(windows))]
fn decode(_motion: &Arc<Motion>, _path: &std::path::Path, _ctx: &egui::Context) {}

/// The frame buffer's real row length in bytes, and whether its rows run bottom-up. Media
/// Foundation's video processor pads the frame (a 1080 px clip comes back 1088 px wide, rows
/// 4352 bytes apart) but still reports the unpadded stride, which would shear every row by
/// 8 px; the buffer's length gives the padded one away.
#[cfg(windows)]
fn layout(w: usize, h: usize, len: usize, reported: i32) -> (usize, bool) {
    let row = w * 4;
    let bottom_up = reported < 0;
    let reported = if reported == 0 { row as i32 } else { reported }.unsigned_abs() as usize;
    let fits = |s: usize| s >= row && len % s == 0 && (h..h + 64).contains(&(len / s));
    if fits(reported) {
        return (reported, bottom_up);
    }
    for rows in h..h + 64 {
        let s = len / rows;
        if len % rows == 0 && s >= row && s <= row + 256 {
            return (s, bottom_up);
        }
    }
    (reported.max(row), bottom_up)
}

/// Media Foundation decoding of a real cached sleeve. Needs a clip in the cache (play
/// something in the app first): `cargo test --release -- --ignored decodes_a_cached_clip`.
#[cfg(all(test, windows))]
mod tests {
    use super::*;

    #[test]
    #[ignore]
    fn decodes_a_cached_clip() {
        let dir = crate::data_dir().join("motion");
        let path = std::fs::read_dir(&dir)
            .expect("no motion cache")
            .flatten()
            .map(|e| e.path())
            .find(|p| p.extension().is_some_and(|e| e == "mp4"))
            .expect("no cached clip");
        let ctx = egui::Context::default();
        let motion = Arc::new(Motion::new("test"));
        let this = motion.clone();
        std::thread::spawn(move || decode(&this, &path, &ctx));
        let start = Instant::now();
        while motion.latest().is_none() && start.elapsed() < Duration::from_secs(15) {
            std::thread::sleep(Duration::from_millis(50));
        }
        motion.stop();
        let (_, frame) = motion.latest().expect("no frame decoded");
        assert!(frame.w > 0 && frame.h > 0, "empty frame");
        assert_eq!(frame.rgba.len(), frame.w * frame.h * 4);
    }
}
