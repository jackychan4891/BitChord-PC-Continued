//! Parallel, seek-aware HTTP audio buffer.
//!
//! The file is split into fixed blocks fetched by a few concurrent range requests.
//! Workers always claim the first missing block at or after the one a reader is
//! waiting on, so playback start and seeks are served first and the rest fills in
//! around them. Reads of a missing block block until it arrives (or the download fails).
//!
//! Several readers may share one buffer (the audio engine opens a second, seekable
//! decoder when the user seeks); the download stops once the last reader is dropped.

use std::future::Future;
use std::io::{self, Read, Seek, SeekFrom};
use std::pin::Pin;
use std::sync::{Arc, Condvar, Mutex};
use std::time::Duration;

use bytes::Bytes;

const BLOCK: u64 = 256 * 1024;
const WORKERS: usize = 4;
/// Tries per block before the download gives up, waiting a little longer each time
/// (about half a minute in all): long enough to ride out a dropped connection.
const RETRIES: u32 = 6;
/// A response that delivers nothing for this long is dead; a slow one that keeps
/// delivering is fine, however long the block takes.
const STALL: Duration = Duration::from_secs(20);
/// googlevideo occasionally starts rejecting a URL partway through (HTTP 403);
/// a freshly resolved URL for the same video resumes the download.
const MAX_REFRESHES: u32 = 2;
/// A prefetch only fetches this much (~16 s of audio) until playback actually opens it:
/// enough to start at once, without holding a whole extra song in memory.
const HEAD_BLOCKS: usize = 2;
/// Files larger than this (about 20 minutes at 256 kbps: long mixes, whole albums in
/// one video) aren't kept whole. Each reader keeps a window around where it reads,
/// `AHEAD` blocks forward (about 5 minutes at 256 kbps) and `BEHIND` back; the rest is dropped and
/// fetched again if a seek goes back to it.
const WINDOWED_FROM: u64 = 40 * 1024 * 1024;
const AHEAD: usize = 40;
const BEHIND: usize = 8;

/// Resolves a fresh stream URL for the same video.
pub type Refresh = Arc<dyn Fn() -> Pin<Box<dyn Future<Output = anyhow::Result<String>> + Send>> + Send + Sync>;

/// The container and codec a stream carries.
#[derive(Clone, Debug, PartialEq)]
pub enum Format {
    /// AAC in MP4: YouTube's streams, and saved songs.
    Aac,
    /// Opus in WebM: YouTube without Premium, and addons that serve it.
    Opus,
    /// A file rodio opens with a hint and MIME type: FLAC, ALAC, WAV, MP3, Vorbis.
    File { hint: &'static str, mime: Option<String> },
    /// Interleaved signed 16-bit little-endian PCM (decoded in-process, e.g. Atmos).
    Pcm { rate: u32, channels: u16 },
}

impl Format {
    pub fn from_opus(opus: bool) -> Self {
        if opus { Self::Opus } else { Self::Aac }
    }
}

/// How a source takes byte ranges: YouTube's URLs carry them in the query string,
/// addon servers expect the standard `Range` header.
#[derive(Clone, Copy, Debug, PartialEq)]
pub enum Ranging {
    Query,
    Header,
}

struct Inner {
    blocks: Vec<Option<Bytes>>,
    in_flight: Vec<bool>,
    /// Block a reader most recently needed; workers prioritize from here.
    want: usize,
    error: Option<String>,
    readers: usize,
    /// Set once a reader existed and all were dropped.
    cancelled: bool,
    /// Blocks the workers may fetch; lifted to all of them when a reader opens.
    limit: usize,
    /// Each open reader's id and the block it last read (windowed files keep only
    /// what's near these).
    wants: Vec<(u64, usize)>,
    windowed: bool,
    /// Blocks fetched at least once, and how many: the download counts as finished
    /// once each was, even if a windowed file has dropped some since.
    ever: Vec<bool>,
    fetched: usize,
}

impl Inner {
    /// Whether block `i` belongs in memory now.
    fn in_window(&self, i: usize) -> bool {
        !self.windowed || self.wants.is_empty() || self.wants.iter().any(|&(_, w)| i + BEHIND >= w && i < w + AHEAD)
    }
}

enum Claim {
    Block(usize),
    /// Everything allowed so far is fetched or in flight; wait for the limit to lift.
    Wait,
    Done,
}

pub struct Shared {
    inner: Mutex<Inner>,
    ready: Condvar,
    pub total: u64,
    /// Track length reported by the stream metadata (0 if unknown).
    pub duration_ms: u64,
    /// YouTube's loudness for this stream, in dB above its -14 LUFS target.
    pub loudness_db: Option<f32>,
    pub tempo: Arc<std::sync::OnceLock<Option<f32>>>,
    /// The container and codec this buffer holds.
    pub format: Format,
    /// What the player can say about this stream, when its source reported it (addons).
    pub info: Option<crate::hires::Info>,
    /// Flips to true once the download task has stopped (complete, failed or cancelled).
    done: tokio::sync::watch::Sender<bool>,
    /// Wakes waiting workers: `limit` lifted, a reader moved or left, or cancelled.
    changed: tokio::sync::Notify,
}

impl Shared {
    /// A buffer for `total` bytes. `head_only` (a prefetch) fetches just the start until
    /// a reader opens it.
    pub fn new(total: u64, duration_ms: u64, loudness_db: Option<f32>, format: Format, info: Option<crate::hires::Info>, head_only: bool) -> Arc<Self> {
        let n = total.div_ceil(BLOCK) as usize;
        let limit = if head_only { HEAD_BLOCKS.min(n) } else { n };
        Arc::new(Self {
            inner: Mutex::new(Inner {
                blocks: vec![None; n],
                in_flight: vec![false; n],
                want: 0,
                error: None,
                readers: 0,
                cancelled: false,
                limit,
                wants: Vec::new(),
                windowed: total > WINDOWED_FROM,
                ever: vec![false; n],
                fetched: 0,
            }),
            ready: Condvar::new(),
            total,
            duration_ms,
            loudness_db,
            tempo: Arc::new(std::sync::OnceLock::new()),
            format,
            info,
            done: tokio::sync::watch::Sender::new(false),
            changed: tokio::sync::Notify::new(),
        })
    }

    /// A buffer that already holds the whole file (a downloaded song).
    pub fn from_bytes(data: Vec<u8>, duration_ms: u64, loudness_db: Option<f32>) -> Arc<Self> {
        // Saved songs are AAC (downloads take that stream).
        Self::from_bytes_with_format(data, duration_ms, loudness_db, Format::Aac, None)
    }

    /// A buffer that's already whole, in whatever container it was decoded to.
    pub fn from_bytes_with_format(data: Vec<u8>, duration_ms: u64, loudness_db: Option<f32>, format: Format, info: Option<crate::hires::Info>) -> Arc<Self> {
        let total = data.len() as u64;
        let data = Bytes::from(data);
        let blocks: Vec<Option<Bytes>> =
            (0..total.div_ceil(BLOCK)).map(|i| Some(data.slice(i as usize * BLOCK as usize..((i + 1) * BLOCK).min(total) as usize))).collect();
        let n = blocks.len();
        let shared = Self::new(total, duration_ms, loudness_db, format, info, false);
        {
            let mut inner = shared.inner.lock().unwrap();
            inner.blocks = blocks;
            inner.windowed = false;
            inner.ever = vec![true; n];
            inner.fetched = n;
        }
        shared.done.send_replace(true);
        shared
    }

    /// The whole file, once every block has arrived (a download nobody's reading).
    pub fn bytes(&self) -> Option<Vec<u8>> {
        let inner = self.inner.lock().unwrap();
        let mut out = Vec::with_capacity(self.total as usize);
        for b in &inner.blocks {
            out.extend_from_slice(b.as_ref()?);
        }
        Some(out)
    }

    /// Every block is in memory: readers never wait and the buffer can be replayed.
    pub fn is_complete(&self) -> bool {
        self.inner.lock().unwrap().blocks.iter().all(Option::is_some)
    }

    /// Wait until the download stops, however it ends.
    pub async fn finished(&self) {
        let _ = self.done.subscribe().wait_for(|d| *d).await;
    }

    /// Why the download stopped early, if it failed.
    pub fn error(&self) -> Option<String> {
        self.inner.lock().unwrap().error.clone()
    }

    /// Stop downloading (e.g. a prefetch that was superseded).
    pub fn cancel(&self) {
        self.inner.lock().unwrap().cancelled = true;
        self.ready.notify_all();
        self.changed.notify_waiters();
    }

    fn claim(&self) -> Claim {
        let mut inner = self.inner.lock().unwrap();
        if inner.cancelled || inner.error.is_some() {
            return Claim::Done;
        }
        let n = inner.blocks.len();
        let start = inner.want.min(n.saturating_sub(1));
        let mut missing = false;
        for i in (start..n).chain(0..start) {
            if inner.blocks[i].is_some() || inner.in_flight[i] {
                continue;
            }
            missing = true;
            if i < inner.limit && inner.in_window(i) {
                inner.in_flight[i] = true;
                return Claim::Block(i);
            }
        }
        // A windowed file may need dropped blocks again while it's being read.
        if missing || (inner.windowed && inner.readers > 0) { Claim::Wait } else { Claim::Done }
    }

    fn put(&self, idx: usize, data: Bytes) {
        let mut inner = self.inner.lock().unwrap();
        inner.blocks[idx] = Some(data);
        inner.in_flight[idx] = false;
        if !std::mem::replace(&mut inner.ever[idx], true) {
            inner.fetched += 1;
            if inner.fetched == inner.blocks.len() {
                self.done.send_replace(true);
            }
        }
        if inner.windowed && !inner.wants.is_empty() {
            for i in 0..inner.blocks.len() {
                if inner.blocks[i].is_some() && !inner.in_window(i) {
                    inner.blocks[i] = None;
                }
            }
        }
        self.ready.notify_all();
    }

    fn fail(&self, err: String) {
        log::error!("download failed: {err}");
        FAIL_FROM.store(usize::MAX, std::sync::atomic::Ordering::Relaxed);
        self.inner.lock().unwrap().error = Some(err);
        self.ready.notify_all();
    }

    fn range(&self, idx: usize) -> (u64, u64) {
        let start = idx as u64 * BLOCK;
        (start, (start + BLOCK).min(self.total) - 1)
    }
}

/// The current URL plus how many times it has been replaced.
struct UrlSlot {
    url: tokio::sync::Mutex<(String, u32)>,
    refresh: Option<Refresh>,
}

impl UrlSlot {
    /// Replace the URL if `seen` is still current; returns false when out of refreshes.
    async fn renew(&self, seen: u32) -> bool {
        let mut slot = self.url.lock().await;
        if slot.1 != seen {
            return true; // another worker already refreshed it
        }
        let Some(refresh) = self.refresh.as_ref().filter(|_| slot.1 < MAX_REFRESHES) else { return false };
        match refresh().await {
            Ok(url) => {
                log::info!("stream URL refreshed after 403");
                *slot = (url, slot.1 + 1);
                true
            }
            Err(e) => {
                log::warn!("stream URL refresh failed: {e:#}");
                false
            }
        }
    }
}

/// Download into `shared` with parallel range requests; returns when complete,
/// failed, or every reader was dropped.
pub async fn download(http: reqwest::Client, url: String, ua: String, shared: Arc<Shared>, refresh: Option<Refresh>, ranging: Ranging) {
    // Addon servers may ignore ranges and answer 200 with the whole file: then one
    // sequential download fills the buffer, rather than every worker pulling the file.
    if ranging == Ranging::Header && !ranges_supported(&http, &url, &ua).await {
        download_whole(&http, &url, &ua, &shared).await;
        return;
    }
    let slot = Arc::new(UrlSlot { url: tokio::sync::Mutex::new((url, 0)), refresh });
    let mut set = tokio::task::JoinSet::new();
    for _ in 0..WORKERS {
        let (http, ua, shared, slot) = (http.clone(), ua.clone(), shared.clone(), slot.clone());
        set.spawn(async move {
            loop {
                // Registered before claiming, so a change in between isn't missed.
                let changed = shared.changed.notified();
                tokio::pin!(changed);
                changed.as_mut().enable();
                let idx = match shared.claim() {
                    Claim::Block(idx) => idx,
                    Claim::Wait => {
                        changed.await;
                        continue;
                    }
                    Claim::Done => break,
                };
                let (a, b) = shared.range(idx);
                let mut attempt = 0;
                loop {
                    let (url, version) = slot.url.lock().await.clone();
                    // A refreshed URL is tied to the address that resolved it, which is the
                    // current shared client's (a proxy may have switched exits since).
                    let client = if version > 0 { crate::proxy::http() } else { http.clone() };
                    let res = if idx >= FAIL_FROM.load(std::sync::atomic::Ordering::Relaxed) {
                        Err(anyhow::anyhow!("simulated network failure"))
                    } else {
                        match ranging {
                            Ranging::Query => fetch(&client, &format!("{url}&range={a}-{b}"), &ua, b - a + 1).await,
                            Ranging::Header => fetch_range(&client, &url, &ua, a, b, shared.total).await,
                        }
                    };
                    match res {
                        Ok(data) => {
                            shared.put(idx, data);
                            break;
                        }
                        Err(e) if is_forbidden(&e) && slot.renew(version).await => {}
                        Err(e) if attempt < RETRIES && !is_forbidden(&e) => {
                            attempt += 1;
                            log::warn!("range {a}-{b}: {e:#} (retry {attempt})");
                            tokio::time::sleep(Duration::from_millis(400 * (attempt * attempt) as u64)).await;
                        }
                        Err(e) => {
                            shared.fail(format!("range {a}-{b}: {e:#}"));
                            return;
                        }
                    }
                }
            }
        });
    }
    while set.join_next().await.is_some() {}
    shared.done.send_replace(true);
}

fn is_forbidden(e: &anyhow::Error) -> bool {
    e.downcast_ref::<reqwest::Error>()
        .and_then(reqwest::Error::status)
        .is_some_and(|s| s == reqwest::StatusCode::FORBIDDEN)
}

/// Dev hook: YTM_STREAM_FAIL=<block> makes the first download fail from that block on,
/// as if the connection died mid-song; later downloads work.
static FAIL_FROM: std::sync::atomic::AtomicUsize = std::sync::atomic::AtomicUsize::new(usize::MAX);

pub fn init_dev_hooks() {
    if let Some(b) = std::env::var("YTM_STREAM_FAIL").ok().and_then(|v| v.parse().ok()) {
        FAIL_FROM.store(b, std::sync::atomic::Ordering::Relaxed);
    }
}

async fn fetch(http: &reqwest::Client, url: &str, ua: &str, expect: u64) -> anyhow::Result<Bytes> {
    let stalled = || anyhow::anyhow!("no data for {} s", STALL.as_secs());
    // Errors leave the URL out: it carries the client's IP address and signatures (logs).
    let mut resp = tokio::time::timeout(STALL, http.get(url).header("User-Agent", ua).send())
        .await
        .map_err(|_| stalled())?
        .and_then(reqwest::Response::error_for_status)
        .map_err(reqwest::Error::without_url)?;
    let mut buf = bytes::BytesMut::with_capacity(expect as usize);
    while let Some(chunk) = tokio::time::timeout(STALL, resp.chunk()).await.map_err(|_| stalled())?.map_err(reqwest::Error::without_url)? {
        buf.extend_from_slice(&chunk);
    }
    anyhow::ensure!(buf.len() as u64 == expect, "short read: {} of {expect} bytes", buf.len());
    Ok(buf.freeze())
}

/// A block from a server that takes standard `Range` headers. A 200 (the server ignored
/// the range) still yields this block out of the whole body.
async fn fetch_range(http: &reqwest::Client, url: &str, ua: &str, a: u64, b: u64, total: u64) -> anyhow::Result<Bytes> {
    let stalled = || anyhow::anyhow!("no data for {} s", STALL.as_secs());
    let mut resp = tokio::time::timeout(
        STALL,
        http.get(url)
            .header("User-Agent", ua)
            .header("Range", format!("bytes={a}-{b}"))
            .header("Accept-Encoding", "identity")
            .send(),
    )
    .await
    .map_err(|_| stalled())?
    .and_then(reqwest::Response::error_for_status)
    .map_err(reqwest::Error::without_url)?;
    let partial = resp.status() == reqwest::StatusCode::PARTIAL_CONTENT;
    let expect = if partial { b - a + 1 } else { total };
    let mut buf = bytes::BytesMut::with_capacity(expect as usize);
    while let Some(chunk) = tokio::time::timeout(STALL, resp.chunk()).await.map_err(|_| stalled())?.map_err(reqwest::Error::without_url)? {
        buf.extend_from_slice(&chunk);
    }
    anyhow::ensure!(buf.len() as u64 == expect, "short read: {} of {expect} bytes", buf.len());
    if partial {
        return Ok(buf.freeze());
    }
    Ok(buf.freeze().slice(a as usize..=b as usize))
}

/// Whether the server honours a `Range` header with a 206. A server that answers 200
/// gets one sequential download instead. A probe that can't connect leaves the workers
/// to try ranges themselves (a 200 there is still handled).
async fn ranges_supported(http: &reqwest::Client, url: &str, ua: &str) -> bool {
    match tokio::time::timeout(STALL, http.get(url).header("User-Agent", ua).header("Range", "bytes=0-0").header("Accept-Encoding", "identity").send()).await {
        Ok(Ok(r)) => r.status() == reqwest::StatusCode::PARTIAL_CONTENT,
        _ => true,
    }
}

/// One sequential download for a server that won't serve ranges, filling `shared` block
/// by block as bytes arrive so playback can start before the file is complete.
async fn download_whole(http: &reqwest::Client, url: &str, ua: &str, shared: &Arc<Shared>) {
    let stalled = || anyhow::anyhow!("no data for {} s", STALL.as_secs());
    let result = async {
        let mut resp = tokio::time::timeout(
            STALL,
            http.get(url).header("User-Agent", ua).header("Accept-Encoding", "identity").send(),
        )
        .await
        .map_err(|_| stalled())?
        .and_then(reqwest::Response::error_for_status)
        .map_err(reqwest::Error::without_url)?;
        let block = BLOCK as usize;
        let blocks = shared.inner.lock().unwrap().blocks.len();
        let mut buf = bytes::BytesMut::with_capacity(block);
        let (mut idx, mut received) = (0usize, 0u64);
        while let Some(chunk) = tokio::time::timeout(STALL, resp.chunk()).await.map_err(|_| stalled())?.map_err(reqwest::Error::without_url)? {
            received += chunk.len() as u64;
            buf.extend_from_slice(&chunk);
            while buf.len() >= block && idx < blocks {
                shared.put(idx, buf.split_to(block).freeze());
                idx += 1;
            }
            if shared.inner.lock().unwrap().cancelled {
                return anyhow::Ok(());
            }
        }
        anyhow::ensure!(received == shared.total, "short read: {received} of {} bytes", shared.total);
        if idx < blocks {
            shared.put(idx, buf.split().freeze());
        }
        anyhow::Ok(())
    }
    .await;
    match result {
        Ok(()) => {
            shared.done.send_replace(true);
        }
        Err(e) => {
            if !shared.inner.lock().unwrap().cancelled {
                shared.fail(format!("stream: {e:#}"));
            }
        }
    }
}

pub struct StreamReader {
    shared: Arc<Shared>,
    pos: u64,
    id: u64,
    /// The block this reader last read.
    block: usize,
}

impl StreamReader {
    pub fn new(shared: Arc<Shared>) -> Self {
        static NEXT: std::sync::atomic::AtomicU64 = std::sync::atomic::AtomicU64::new(0);
        let id = NEXT.fetch_add(1, std::sync::atomic::Ordering::Relaxed);
        {
            let mut inner = shared.inner.lock().unwrap();
            inner.readers += 1;
            inner.limit = inner.blocks.len();
            inner.wants.push((id, 0));
        }
        shared.changed.notify_waiters();
        Self { shared, pos: 0, id, block: 0 }
    }
}

impl Drop for StreamReader {
    fn drop(&mut self) {
        let mut inner = self.shared.inner.lock().unwrap();
        inner.readers -= 1;
        inner.wants.retain(|&(id, _)| id != self.id);
        if inner.readers == 0 {
            // Stops the workers once their current request finishes.
            inner.cancelled = true;
        }
        self.shared.changed.notify_waiters();
    }
}

impl Read for StreamReader {
    fn read(&mut self, out: &mut [u8]) -> io::Result<usize> {
        if out.is_empty() || self.pos >= self.shared.total {
            return Ok(0);
        }
        let idx = (self.pos / BLOCK) as usize;
        let mut inner = self.shared.inner.lock().unwrap();
        inner.want = idx;
        if idx != self.block {
            self.block = idx;
            if let Some(w) = inner.wants.iter_mut().find(|(id, _)| *id == self.id) {
                w.1 = idx;
            }
            if inner.windowed {
                self.shared.changed.notify_waiters();
            }
        }
        while inner.blocks[idx].is_none() {
            if let Some(e) = &inner.error {
                return Err(io::Error::other(e.clone()));
            }
            if inner.cancelled {
                return Err(io::Error::other("download cancelled"));
            }
            inner = self.shared.ready.wait(inner).unwrap();
        }
        let block = inner.blocks[idx].as_ref().unwrap();
        let off = (self.pos - idx as u64 * BLOCK) as usize;
        let n = out.len().min(block.len() - off);
        out[..n].copy_from_slice(&block[off..off + n]);
        self.pos += n as u64;
        Ok(n)
    }
}

impl Seek for StreamReader {
    fn seek(&mut self, to: SeekFrom) -> io::Result<u64> {
        let total = self.shared.total as i64;
        let new = match to {
            SeekFrom::Start(p) => p as i64,
            SeekFrom::End(off) => total + off,
            SeekFrom::Current(off) => self.pos as i64 + off,
        };
        if new < 0 {
            return Err(io::Error::new(io::ErrorKind::InvalidInput, "seek before start"));
        }
        self.pos = new as u64;
        Ok(self.pos)
    }
}

/// Open a decoder over `shared`. Non-seekable AAC decoders start after the first
/// fragment; seekable ones index every fragment first (i.e. effectively wait for the
/// download). Opus ones are always seekable: WebM's index is at the start. Raw PCM
/// (decoded in-process, e.g. Dolby Atmos) plays straight from the buffer.
pub fn decoder(shared: Arc<Shared>, seekable: bool) -> anyhow::Result<TrackDecoder> {
    let size = shared.total;
    match shared.format {
        Format::Opus => {
            let duration_ms = shared.duration_ms;
            Ok(TrackDecoder::Opus(Box::new(crate::opus::OpusSource::new(StreamReader::new(shared), size, duration_ms)?)))
        }
        Format::Pcm { rate, channels } => Ok(TrackDecoder::Pcm(PcmSource::new(shared, rate, channels))),
        Format::Aac => {
            let builder = rodio::Decoder::builder()
                .with_data(StreamReader::new(shared.clone()))
                .with_byte_len(size)
                .with_seekable(seekable)
                .with_mime_type("audio/mp4")
                .with_hint("m4a");
            Ok(TrackDecoder::Rodio(builder.build()?))
        }
        Format::File { ref hint, ref mime } => {
            let hint = *hint;
            let mime = mime.clone();
            let builder = rodio::Decoder::builder()
                .with_data(StreamReader::new(shared))
                .with_byte_len(size)
                .with_seekable(seekable)
                .with_hint(hint);
            let builder = match mime.as_deref() {
                Some(mime) => builder.with_mime_type(mime),
                None => builder,
            };
            Ok(TrackDecoder::Rodio(builder.build()?))
        }
    }
}

/// A song's decoder: Opus (our own), in-process PCM (decoded Atmos), or a file
/// rodio decodes (AAC/MP4 included).
pub enum TrackDecoder {
    Rodio(rodio::Decoder<StreamReader>),
    /// Boxed: its decoder state is about 11 KB.
    Opus(Box<crate::opus::OpusSource>),
    Pcm(PcmSource),
}

impl Iterator for TrackDecoder {
    type Item = rodio::Sample;

    fn next(&mut self) -> Option<rodio::Sample> {
        match self {
            Self::Rodio(d) => d.next(),
            Self::Opus(d) => d.next(),
            Self::Pcm(d) => d.next(),
        }
    }
}

impl rodio::Source for TrackDecoder {
    fn current_span_len(&self) -> Option<usize> {
        match self {
            Self::Rodio(d) => d.current_span_len(),
            Self::Opus(d) => d.current_span_len(),
            Self::Pcm(d) => d.current_span_len(),
        }
    }
    fn channels(&self) -> rodio::ChannelCount {
        match self {
            Self::Rodio(d) => d.channels(),
            Self::Opus(d) => d.channels(),
            Self::Pcm(d) => d.channels(),
        }
    }
    fn sample_rate(&self) -> rodio::SampleRate {
        match self {
            Self::Rodio(d) => d.sample_rate(),
            Self::Opus(d) => d.sample_rate(),
            Self::Pcm(d) => d.sample_rate(),
        }
    }
    fn total_duration(&self) -> Option<Duration> {
        match self {
            Self::Rodio(d) => d.total_duration(),
            Self::Opus(d) => d.total_duration(),
            Self::Pcm(d) => d.total_duration(),
        }
    }
    fn try_seek(&mut self, pos: Duration) -> Result<(), rodio::source::SeekError> {
        match self {
            Self::Rodio(d) => d.try_seek(pos),
            Self::Opus(d) => d.try_seek(pos),
            Self::Pcm(d) => d.try_seek(pos),
        }
    }
}

/// Interleaved signed 16-bit little-endian PCM read from the stream buffer.
pub struct PcmSource {
    reader: StreamReader,
    rate: u32,
    channels: u16,
    duration: Option<Duration>,
}

impl PcmSource {
    fn new(shared: Arc<Shared>, rate: u32, channels: u16) -> Self {
        let duration_ms = shared.duration_ms;
        Self { reader: StreamReader::new(shared), rate, channels, duration: (duration_ms > 0).then(|| Duration::from_millis(duration_ms)) }
    }
}

impl Iterator for PcmSource {
    type Item = rodio::Sample;
    fn next(&mut self) -> Option<rodio::Sample> {
        let mut buf = [0u8; 2];
        if self.reader.read_exact(&mut buf).is_err() {
            return None;
        }
        Some(i16::from_le_bytes(buf) as f32 / 32768.0)
    }
}

impl rodio::Source for PcmSource {
    fn current_span_len(&self) -> Option<usize> { None }
    fn channels(&self) -> rodio::ChannelCount { std::num::NonZeroU16::new(self.channels).unwrap_or(std::num::NonZeroU16::MIN) }
    fn sample_rate(&self) -> rodio::SampleRate { std::num::NonZeroU32::new(self.rate).unwrap_or(std::num::NonZeroU32::MIN) }
    fn total_duration(&self) -> Option<Duration> { self.duration }
    fn try_seek(&mut self, pos: Duration) -> Result<(), rodio::source::SeekError> {
        let frame = (pos.as_secs_f64() * self.rate as f64) as u64;
        let byte = frame * self.channels as u64 * 2;
        self.reader.seek(SeekFrom::Start(byte)).map_err(|e| rodio::source::SeekError::Other(std::sync::Arc::new(e)))?;
        Ok(())
    }
}
