//! Listen together: a party on BitChord's own relay, so this app and BitChord on a phone
//! end up in the same room.
//!
//! The relay — not any one device — is the authority. It holds the party's queue, the
//! playing song and the position within it, and tells every member the same thing; each
//! device follows that, streaming the song itself, so no audio goes through the server.
//! An action here (pause, skip, add a song) is sent as a control frame, the server
//! applies it, and broadcasts the new state back — to us too.
//!
//! The position is a playhead, not a snapshot: `positionMs` was where the room was at
//! `anchorMs` *in the server's clock*, so the two clocks are lined up by pinging the
//! server and keeping the quickest round trip. `api.bitchord.kushagrasingh.in` answers the
//! same wire protocol as `BitChord-main/backend`, which the phone apps speak.
//!
//! The socket lives on the backend's tokio runtime; the UI talks to it through channels
//! and is woken when something arrives.

use std::collections::VecDeque;
use std::sync::Arc;
use std::sync::atomic::{AtomicBool, AtomicI64, Ordering};
use std::sync::{Mutex, OnceLock};
use std::time::{Duration, Instant, SystemTime, UNIX_EPOCH};

use crossbeam_channel::{Receiver, Sender, unbounded};
use futures_util::{SinkExt, StreamExt};
use serde::{Deserialize, Serialize};
use serde_json::{Value, json};
use tokio::sync::mpsc;
use tokio_tungstenite::tungstenite::Message;
use tokio_tungstenite::tungstenite::client::IntoClientRequest;

use crate::model::{Link, Thumb, Track};

/// BitChord's relay. `YTM_JAM_SERVER` or the address in Settings replaces it.
pub const SERVER: &str = "https://api.bitchord.kushagrasingh.in";
/// The website invite links point at. The API host answers JSON, not the page.
const INVITE: &str = "https://bitchord.kushagrasingh.in";

/// Party codes: six of these. `I`, `L` and `O` are left out because they are read as
/// `1`, `1` and `0`, and typing them is taken the same way — so a code copied off a phone
/// in lowercase, with dashes, or with a look-alike letter still reaches the same room.
pub const ALPHABET: &str = "0123456789ABCDEFGHJKMNPQRSTUVWXYZ";
pub const CODE_LEN: usize = 6;
/// What the relay allows a party's size to be.
pub const MIN_MEMBERS: usize = 2;
/// The size a party we create starts at; the relay's own default.
pub const DEFAULT_MEMBERS: usize = 5;
/// Songs a member may have waiting in the queue at once.
pub const MAX_UPCOMING: usize = 25;

/// The relay's API root: the environment's, then the address typed in Settings, then
/// BitChord's own. A bare host name is enough.
pub fn server_url(saved: &str) -> String {
    let saved = std::env::var("YTM_JAM_SERVER").unwrap_or_else(|_| saved.trim().to_owned());
    if saved.is_empty() {
        return SERVER.to_owned();
    }
    let s = saved.trim_end_matches('/').to_owned();
    if s.starts_with("http://") || s.starts_with("https://") {
        s
    } else if let Some(h) = s.strip_prefix("wss://") {
        format!("https://{h}")
    } else if let Some(h) = s.strip_prefix("ws://") {
        format!("http://{h}")
    } else {
        format!("https://{s}")
    }
}

/// The link to share: BitChord's invite page, which opens the app on a device that has it
/// and shows the code on one that hasn't.
pub fn share_link(saved: &str, code: &str) -> String {
    let api = server_url(saved);
    let site = if api == SERVER { INVITE.to_owned() } else { api };
    format!("{site}/invite/{}", code.to_ascii_uppercase())
}

/// `code` cleaned up (case, spaces, dashes, look-alike letters) if it is a valid code.
/// Only for typing and links: the relay picks the code when a party is started.
pub fn normalize_code(code: &str) -> Option<String> {
    let c: String = code
        .chars()
        .filter(|ch| ch.is_ascii_alphanumeric())
        .map(|ch| match ch.to_ascii_uppercase() {
            'I' | 'L' => '1',
            'O' => '0',
            u => u,
        })
        .collect();
    (c.len() == CODE_LEN && c.chars().all(|ch| ALPHABET.contains(ch))).then_some(c)
}

/// A song on the wire: what it looks like, not how to stream it. Each device finds its own
/// copy of the audio, which is what lets a phone and a desktop share a song.
#[derive(Clone, Debug, Default, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct Item {
    pub video_id: String,
    #[serde(default)]
    pub title: String,
    #[serde(default)]
    pub artist: String,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub thumbnail_url: Option<String>,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub duration_ms: Option<u64>,
    #[serde(default)]
    pub from_autoplay: bool,
}

pub fn item_of(t: &Track) -> Item {
    Item {
        video_id: t.id.clone(),
        title: t.title.clone(),
        artist: t.artist_line(),
        thumbnail_url: crate::model::pick(&t.thumbs, 400).map(str::to_owned),
        duration_ms: t.duration.map(|d| d as u64 * 1000),
        from_autoplay: false,
    }
}

pub fn track_of(i: &Item) -> Track {
    Track {
        id: i.video_id.clone(),
        title: i.title.clone(),
        artists: if i.artist.is_empty() { vec![] } else { vec![Link { name: i.artist.clone(), id: None }] },
        album: None,
        duration: i.duration_ms.map(|d| d.div_ceil(1000) as u32),
        thumbs: i.thumbnail_url.iter().map(|u| Thumb { url: u.clone(), width: 400, height: 400 }).collect(),
        track_nr: None,
        plays: None,
        music_video: false,
    }
}

#[derive(Clone, Debug, Default)]
pub struct Member {
    pub id: String,
    pub name: String,
    pub host: bool,
    pub connected: bool,
}

#[derive(Deserialize, Default)]
#[serde(rename_all = "camelCase", default)]
struct MemberWire {
    member_id: String,
    display_name: String,
    is_host: bool,
    connected: bool,
}

impl From<MemberWire> for Member {
    fn from(m: MemberWire) -> Member {
        Member { id: m.member_id, name: m.display_name, host: m.is_host, connected: m.connected }
    }
}

/// The party as the relay describes it, mirrored from its frames.
#[derive(Clone, Debug, Default)]
pub struct Party {
    pub code: String,
    /// This device's member id, and the host's.
    pub you: String,
    pub host: String,
    /// Our name as others see it; our own actions don't need a notice.
    pub my_name: String,
    pub members: Vec<Member>,
    pub max_members: usize,
    /// Only the host drives playback and the queue.
    pub host_only: bool,
    pub queue: Vec<Track>,
    /// Where the playing song sits in `queue`, as the queue frame said; -1 while nothing is loaded.
    pub index: i64,
    /// The queue's version, from the queue frame.
    pub queue_seq: i64,
    /// Where the state frame says the playing song sits, and the queue version it read that
    /// against. Fresher than the two above, which only move when the queue is resent.
    pub pb_index: i64,
    pub pb_queue_seq: i64,
    pub vid: String,
    pub title: String,
    pub playing: bool,
    /// Where the room was at `anchor_ms`, both in the server's clock.
    pub pos_ms: u64,
    pub anchor_ms: i64,
    pub duration_ms: u64,
    pub seq: i64,
    /// Who started the playing song.
    pub who: Option<String>,
}

impl Party {
    pub fn you_host(&self) -> bool {
        !self.you.is_empty() && self.you == self.host
    }

    /// In a room only the host drives, and we aren't the host.
    pub fn silent(&self) -> bool {
        self.host_only && !self.you_host()
    }

    /// Where the room is in the song now, given the server's clock.
    pub fn playhead_ms(&self, server_ms: i64) -> u64 {
        if !self.playing {
            return self.pos_ms;
        }
        let pos = self.pos_ms.saturating_add((server_ms - self.anchor_ms).max(0) as u64);
        if self.duration_ms > 0 { pos.min(self.duration_ms) } else { pos }
    }

    /// Where `vid` stands in `queue`, read the way every BitChord device reads it.
    ///
    /// A party queue keeps its history, so the same song can sit in it twice: once played,
    /// once now. The state frame's own index is the freshest word, so it wins whenever it
    /// names this song and was taken against the queue version we hold. Failing that the
    /// copy nearest the hint wins, ahead of it before behind it.
    pub fn position_of(&self, vid: &str) -> Option<usize> {
        if vid.is_empty() {
            return None;
        }
        let hint = if self.pb_queue_seq == self.queue_seq { self.pb_index } else { self.index };
        if hint >= 0 && (hint as usize) < self.queue.len() && self.queue[hint as usize].id == vid {
            return Some(hint as usize);
        }
        let mut best = None;
        let mut best_distance = i64::MAX;
        for (i, t) in self.queue.iter().enumerate() {
            if t.id != vid {
                continue;
            }
            let distance = if i as i64 >= hint { i as i64 - hint } else { (hint - i as i64) * 2 + 1 };
            if distance < best_distance {
                best = Some(i);
                best_distance = distance;
            }
        }
        best
    }
}

/// Where the room is, for the local player to line up with.
#[derive(Clone, Debug, PartialEq)]
pub struct Playhead {
    pub vid: String,
    pub ms: u64,
    pub playing: bool,
    pub duration_ms: u64,
    /// The room aims at one instant for a resume; this is how long is left to wait for it.
    /// Starting before it lands puts this device permanently ahead.
    pub lead_ms: u64,
}

#[derive(Debug)]
pub enum Event {
    /// In the room, and the mirror is current.
    Joined,
    /// Who is here, or who hosts, changed.
    Members,
    /// The queue or the playing song moved.
    Music,
    /// "Sam Skipped to the next song."
    Activity(String),
    /// Something the relay turned down: a sentence to show, the party carries on.
    Notice(String),
    /// Out of the room for good: the reason to show.
    Closed(String),
    Reconnecting,
}

/// What this device is called on the relay. Both ids are stable: the relay matches a
/// reconnecting member by the device id, which is how a party keeps our place.
pub struct Ids {
    pub user: String,
    pub device: String,
    pub avatar: Option<String>,
}

enum Out {
    Text(String),
    /// Out of the room.
    Leave,
}

pub struct Jam {
    out: mpsc::UnboundedSender<Out>,
    events: Receiver<Event>,
    party: Arc<Mutex<Party>>,
    /// Server clock minus ours, in ms.
    offset: Arc<AtomicI64>,
    /// Whether that comes from real round trips rather than the first guess.
    synced: Arc<AtomicBool>,
}

impl Jam {
    /// Ask the relay for a room: `create` starts one — the code is the server's to pick —
    /// otherwise join `code`. `server` is what's typed in Settings, empty for BitChord's.
    pub fn start(backend: &crate::backend::Backend, create: bool, code: String, name: String, server: String, ids: Ids) -> Jam {
        let (out_tx, out_rx) = mpsc::unbounded_channel();
        let (ev_tx, ev_rx) = unbounded();
        let party = Arc::new(Mutex::new(Party { code: code.clone(), max_members: DEFAULT_MEMBERS, index: -1, ..Default::default() }));
        let offset = Arc::new(AtomicI64::new(0));
        let synced = Arc::new(AtomicBool::new(false));
        let wake = backend.clone();
        let task = Conn {
            http: backend.http(),
            base: server_url(&server),
            create,
            code,
            token: String::new(),
            name,
            avatar: ids.avatar,
            user: ids.user,
            device: ids.device,
            events: ev_tx,
            wake: Arc::new(move || wake.wake()),
            party: party.clone(),
            offset: offset.clone(),
            synced: synced.clone(),
        };
        backend.spawn(task.run(out_rx));
        Jam { out: out_tx, events: ev_rx, party, offset, synced }
    }

    pub fn try_recv(&self) -> Option<Event> {
        self.events.try_recv().ok()
    }

    /// The room as we last heard of it.
    pub fn party(&self) -> Party {
        self.party.lock().unwrap().clone()
    }

    pub fn code(&self) -> String {
        self.party.lock().unwrap().code.clone()
    }

    /// Where the room is, or None until the relay has named a song.
    pub fn position(&self) -> Option<Playhead> {
        let p = self.party.lock().unwrap();
        if p.vid.is_empty() {
            return None;
        }
        let server = now(&self.offset);
        let lead_ms = if p.playing { (p.anchor_ms - server).max(0) as u64 } else { 0 };
        Some(Playhead { vid: p.vid.clone(), ms: p.playhead_ms(server), playing: p.playing, duration_ms: p.duration_ms, lead_ms })
    }

    /// Whether the clock is lined up yet; before that, positions are a guess.
    pub fn synced(&self) -> bool {
        self.synced.load(Ordering::Relaxed)
    }

    fn action(&self, action: &str, extra: Value) {
        let mut v = json!({ "type": "control", "action": action });
        if let Some(map) = extra.as_object() {
            for (k, val) in map {
                v[k] = val.clone();
            }
        }
        let _ = self.out.send(Out::Text(v.to_string()));
    }

    pub fn play(&self, pos_ms: u64) {
        self.action("play", json!({ "positionMs": pos_ms }));
    }

    pub fn pause(&self, pos_ms: u64) {
        self.action("pause", json!({ "positionMs": pos_ms }));
    }

    pub fn seek(&self, pos_ms: u64) {
        self.action("seek", json!({ "positionMs": pos_ms }));
    }

    /// The playing song. `index` is where it sits in the room's queue, which only makes
    /// sense once that queue has been sent — so it travels with [Jam::share_queue].
    pub fn start_track(&self, t: &Track, index: Option<usize>, pos_ms: u64, playing: bool) {
        let mut extra = json!({ "track": item_of(t), "positionMs": pos_ms, "isPlaying": playing });
        if let Some(i) = index {
            extra["queueIndex"] = i.into();
        }
        self.action("setTrack", extra);
    }

    /// What a member shares: the songs around the one it's playing. The relay keeps five
    /// songs of history plus [MAX_UPCOMING] to come, and everyone else follows that.
    pub fn share_queue(&self, queue: &[Track], index: usize) {
        let from = index.saturating_sub(5);
        let to = (index + 1 + MAX_UPCOMING).min(queue.len()).max(from);
        let items: Vec<Item> = queue[from..to].iter().map(item_of).collect();
        self.action("setQueue", json!({ "queue": items, "queueIndex": index - from }));
    }

    /// The room's queue again, please. It is sent whole in a welcome and on every change; a
    /// frame lost in a reconnect leaves the room playing a song our copy has never heard of,
    /// and only a `queue` frame can name it.
    pub fn ask_queue(&self) {
        let _ = self.out.send(Out::Text(r#"{"type":"syncQueue"}"#.to_owned()));
    }

    /// Who may drive the room: only the host, or everyone.
    pub fn set_host_only(&self, on: bool) {
        self.action("setHostOnlyControl", json!({ "enabled": on }));
    }

    /// Our own position, for the relay's drift log.
    pub fn report(&self, pos_ms: u64, playing: bool) {
        let _ = self.out.send(Out::Text(json!({ "type": "report", "positionMs": pos_ms, "isPlaying": playing }).to_string()));
    }

    /// Out of the room. The relay has no "close the party": a room empties and expires, so
    /// a host who is done simply leaves, and the room goes with the last member out.
    pub fn leave(&self) {
        let _ = self.out.send(Out::Leave);
    }
}

impl Drop for Jam {
    fn drop(&mut self) {
        self.leave();
    }
}

/// Our monotonic clock in ms, which is what the relay's `clientMs` is compared against.
fn base() -> &'static Instant {
    static B: OnceLock<Instant> = OnceLock::new();
    B.get_or_init(Instant::now)
}

fn mono_ms() -> i64 {
    Instant::now().duration_since(*base()).as_millis() as i64
}

/// The server's clock as we understand it.
fn now(offset: &AtomicI64) -> i64 {
    mono_ms() + offset.load(Ordering::Relaxed)
}

fn wall_ms() -> i64 {
    SystemTime::now().duration_since(UNIX_EPOCH).map_or(0, |d| d.as_millis() as i64)
}

fn num(v: &Value, k: &str) -> i64 {
    v.get(k).and_then(Value::as_i64).unwrap_or(0)
}

fn flag(v: &Value, k: &str) -> bool {
    v.get(k).and_then(Value::as_bool).unwrap_or(false)
}

fn text(v: &Value, k: &str) -> String {
    v.get(k).and_then(Value::as_str).unwrap_or_default().to_owned()
}

fn members_of(v: &Value) -> Vec<Member> {
    v.get("members").and_then(Value::as_array).map(|a| a.iter().filter_map(|m| serde_json::from_value::<MemberWire>(m.clone()).ok()).map(Member::from).collect()).unwrap_or_default()
}

/// The playing song and its position. False when the frame is older than what we hold.
fn apply_playback(p: &mut Party, pb: &Value) -> bool {
    if !pb.is_object() {
        return false;
    }
    let seq = num(pb, "seq");
    if seq != 0 && seq < p.seq {
        return false;
    }
    p.seq = seq;
    let was = p.playing;
    p.playing = flag(pb, "isPlaying");
    if was != p.playing {
        log::debug!("jam: the room goes {} at {} ms (seq {seq})", if p.playing { "playing" } else { "holding" }, p.pos_ms);
    }
    p.pos_ms = num(pb, "positionMs").max(0) as u64;
    p.anchor_ms = num(pb, "anchorMs");
    p.pb_queue_seq = num(pb, "queueSeq");
    p.pb_index = num(pb, "queueIndex");
    p.who = pb.get("startedByName").and_then(Value::as_str).filter(|s| !s.is_empty()).map(str::to_owned);
    match pb.get("track") {
        Some(t) if t.is_object() => {
            if let Ok(item) = serde_json::from_value::<Item>(t.clone()) {
                p.vid = item.video_id.clone();
                p.title = item.title.clone();
                p.duration_ms = item.duration_ms.unwrap_or(0);
                // A song the relay has no length for: the queue's copy may.
                if p.duration_ms == 0 {
                    p.duration_ms = p.queue.iter().find(|x| x.id == p.vid).and_then(|x| x.duration).map_or(0, |d| d as u64 * 1000);
                }
            }
        }
        _ => {
            p.vid.clear();
            p.title.clear();
            p.duration_ms = 0;
        }
    }
    true
}

/// The room's queue. False when the frame is older than what we hold.
fn apply_queue(p: &mut Party, q: &Value) -> bool {
    if !q.is_object() {
        return false;
    }
    let seq = num(q, "seq");
    if seq != 0 && seq < p.queue_seq {
        return false;
    }
    p.queue_seq = seq;
    let items: Vec<Item> = q.get("items").and_then(Value::as_array).map(|a| a.iter().filter_map(|i| serde_json::from_value::<Item>(i.clone()).ok()).collect()).unwrap_or_default();
    p.queue = items.iter().map(track_of).collect();
    p.index = num(q, "index");
    if p.index < -1 || (p.index as usize) >= p.queue.len() {
        // The playing song isn't in what arrived; the next state frame says where it is.
        p.index = -1;
    }
    true
}

/// A refusal in a sentence. Codes and bodies stay in the log.
fn party_words(code: &str, msg: &str, base: &str) -> String {
    let host = base.replace("https://", "").replace("http://", "");
    match code {
        "no_such_party" | "not_found" => "No party with that code. Check it and try again".to_owned(),
        "party_full" | "full" => "That party is full".to_owned(),
        "host_only" => "Only the host can do that in this party".to_owned(),
        "rate_limited" | "create_rate_limited" => "Slow down a little and try again".to_owned(),
        "queue_full" => format!("That party's queue holds {MAX_UPCOMING} songs to come"),
        "bad_token" | "unauthorized" => format!("{host} won't let this device into that party any more"),
        "server_full" => "The party server is full; try again in a moment".to_owned(),
        "server_error" => format!("{host} had trouble just then; try again"),
        _ if !msg.is_empty() => msg.to_owned(),
        _ => code.to_owned(),
    }
}

/// The body of a relay answer: JSON, or a note about whatever came back instead.
async fn reply(resp: reqwest::Response) -> (reqwest::StatusCode, Value) {
    let status = resp.status();
    let body = resp.text().await.unwrap_or_default();
    let t = body.trim_start();
    if !(t.starts_with('{') || t.starts_with('[')) {
        log::debug!("jam: {status} from the relay, not JSON: {}", body.chars().take(160).collect::<String>());
        return (status, Value::Null);
    }
    (status, serde_json::from_str(&body).unwrap_or(Value::Null))
}

/// Why a connection ended.
enum End {
    /// Left, or the room is over: nothing to retry.
    Done(Option<String>),
    /// The relay said no (no such code, full): show it, don't retry.
    Refused(String),
    /// The network gave up: try again.
    Dropped(String),
}

struct Sample {
    offset: i64,
    rtt: i64,
    at: i64,
}

struct Conn {
    http: reqwest::Client,
    base: String,
    create: bool,
    code: String,
    token: String,
    name: String,
    avatar: Option<String>,
    user: String,
    device: String,
    events: Sender<Event>,
    wake: Arc<dyn Fn() + Send + Sync>,
    party: Arc<Mutex<Party>>,
    offset: Arc<AtomicI64>,
    synced: Arc<AtomicBool>,
}

impl Conn {
    fn emit(&self, e: Event) {
        let _ = self.events.send(e);
        (self.wake)();
    }

    async fn run(mut self, mut out: mpsc::UnboundedReceiver<Out>) {
        let mut fails = 0u32;
        loop {
            // A room we've been in is rejoined, not re-created.
            if self.token.is_empty() {
                match self.membership(&mut out).await {
                    Ok(true) => {
                        self.emit(Event::Joined);
                        self.emit(Event::Members);
                        self.emit(Event::Music);
                    }
                    Ok(false) => return,
                    Err(End::Refused(m)) => {
                        self.emit(Event::Closed(m));
                        return;
                    }
                    Err(End::Dropped(e)) => {
                        log::warn!("jam: the party server didn't answer: {e}");
                        if !self.backoff(&mut out, &mut fails).await {
                            return;
                        }
                        continue;
                    }
                    Err(End::Done(m)) => {
                        self.emit(Event::Closed(m.unwrap_or_default()));
                        return;
                    }
                }
            }
            let started = Instant::now();
            let end = self.session(&mut out).await;
            // Breaks spread over a long party shouldn't add up to giving up.
            if started.elapsed() > Duration::from_secs(120) {
                fails = 0;
            }
            match end {
                End::Done(m) => {
                    self.token.clear();
                    self.emit(Event::Closed(m.unwrap_or_default()));
                    return;
                }
                End::Refused(m) => {
                    self.token.clear();
                    self.emit(Event::Closed(m));
                    return;
                }
                End::Dropped(e) => {
                    log::warn!("jam: connection dropped: {e}");
                    self.create = false;
                    // The token may be what's wrong — the relay forgot the room, or took us
                    // out of it. After a couple of tries, ask for a slot again.
                    if fails >= 2 {
                        self.token.clear();
                    }
                    if !self.backoff(&mut out, &mut fails).await {
                        return;
                    }
                }
            }
        }
    }

    /// Waiting before the next try. False means stop: given up, or asked to leave.
    async fn backoff(&mut self, out: &mut mpsc::UnboundedReceiver<Out>, fails: &mut u32) -> bool {
        *fails += 1;
        if *fails > 10 {
            self.emit(Event::Closed("Lost the connection to the party".into()));
            return false;
        }
        self.emit(Event::Reconnecting);
        let wait = (1u64 << (*fails - 1).min(5)).min(20);
        if *fails >= 3 {
            self.emit(Event::Notice(format!("Lost the party for a moment, trying again every {wait} seconds")));
        }
        let nap = tokio::time::sleep(Duration::from_secs(wait));
        tokio::pin!(nap);
        loop {
            tokio::select! {
                _ = &mut nap => return true,
                m = out.recv() => match m {
                    Some(Out::Leave) => {
                        self.exit().await;
                        return false;
                    }
                    None => return false,
                    // Nothing to send while the socket is down.
                    Some(Out::Text(_)) => {}
                }
            }
        }
    }

    /// Create the room, or take our slot in it. Ok(false): asked to leave meanwhile.
    async fn membership(&mut self, out: &mut mpsc::UnboundedReceiver<Out>) -> Result<bool, End> {
        let url = if self.create { format!("{}/api/parties", self.base) } else { format!("{}/api/parties/{}/join", self.base, self.code) };
        let mut body = self.join_body();
        if self.create {
            body["maxMembers"] = DEFAULT_MEMBERS.into();
        }
        let resp = self.http.post(&url).json(&body).send().await.map_err(|e| End::Dropped(e.to_string()))?;
        // Leaving while the request was out: don't arrive after saying goodbye.
        if let Ok(Out::Leave) = out.try_recv() {
            self.exit().await;
            return Ok(false);
        }
        let (status, v) = reply(resp).await;
        if !status.is_success() {
            let (code, msg) = (text(&v, "error"), text(&v, "message"));
            log::warn!("jam: {status} {code} {msg}");
            if code.is_empty() || matches!(status.as_u16(), 429 | 500..=599) {
                return Err(End::Dropped(format!("{status} {code}")));
            }
            return Err(End::Refused(party_words(&code, &msg, &self.base)));
        }
        self.code = text(&v, "code");
        self.token = text(&v, "token");
        self.apply(&v);
        log::info!("jam: in party {}", self.code);
        Ok(true)
    }

    fn join_body(&self) -> Value {
        let clip = |s: &str, n: usize| -> String { s.trim().chars().take(n).collect() };
        let mut v = json!({
            "userId": clip(&self.user, 128),
            "deviceId": clip(&self.device, 128),
            "displayName": clip(&self.name, 80),
        });
        if let Some(a) = self.avatar.as_ref().filter(|a| a.starts_with("http")) {
            v["avatarUrl"] = a.chars().take(1000).collect::<String>().into();
        }
        v
    }

    /// Mirror a party snapshot: the create/join answer and the welcome frame both carry one,
    /// with who we are in it.
    fn apply(&mut self, v: &Value) {
        let you = v.get("you").cloned().unwrap_or(Value::Null);
        let party = v.get("party").cloned().unwrap_or(Value::Null);
        let server_ms = v.get("serverMs").and_then(Value::as_i64).unwrap_or_else(wall_ms);
        let code = text(&party, "code");
        let members = members_of(&party);
        let max = num(&party, "maxMembers").max(MIN_MEMBERS as i64) as usize;
        let host_only = flag(&party, "hostOnlyControl");
        let playback = party.get("playback").cloned().unwrap_or(Value::Null);
        let queue = party.get("queue").cloned().unwrap_or(Value::Null);
        {
            let mut p = self.party.lock().unwrap();
            if !code.is_empty() {
                p.code = code;
            }
            p.max_members = max;
            p.host_only = host_only;
            p.you = text(&you, "memberId");
            p.my_name = text(&you, "displayName");
            p.host = members.iter().find(|m| m.host).map(|m| m.id.clone()).unwrap_or_default();
            p.members = members;
            apply_queue(&mut p, &queue);
            apply_playback(&mut p, &playback);
        }
        // A first guess at the clock, until the pings answer.
        self.offset.store(server_ms - mono_ms(), Ordering::Relaxed);
        self.synced.store(false, Ordering::Relaxed);
    }

    async fn session(&mut self, out: &mut mpsc::UnboundedReceiver<Out>) -> End {
        let url = format!("{}/ws/parties/{}", ws_url(&self.base), self.code);
        // Through the proxy setting, like everything else. The token rides in a header,
        // which is how the relay knows which member this socket is.
        let token = self.token.clone();
        let connect = async {
            let u = url::Url::parse(&url)?;
            let host = u.host_str().unwrap_or_default().to_owned();
            let port = u.port_or_known_default().unwrap_or(443);
            let tcp = crate::proxy::connect(&host, port).await?;
            let mut req = url.as_str().into_client_request()?;
            if let Ok(h) = reqwest::header::HeaderValue::from_str(&format!("Bearer {token}")) {
                req.headers_mut().insert("Authorization", h);
            }
            anyhow::Ok(tokio_tungstenite::client_async_tls_with_config(req, tcp, None, None).await?)
        };
        let ws = match tokio::time::timeout(Duration::from_secs(10), connect).await {
            Ok(Ok((ws, _))) => ws,
            Ok(Err(e)) => return End::Dropped(e.to_string()),
            Err(_) => return End::Dropped("timed out".into()),
        };
        let (mut tx, mut rx) = ws.split();
        let mut samples: VecDeque<Sample> = VecDeque::new();
        let mut send: Vec<String> = Vec::new();
        // A burst of pings to find the quickest path, then one every 15 seconds.
        let mut burst = tokio::time::interval(Duration::from_millis(300));
        burst.set_missed_tick_behavior(tokio::time::MissedTickBehavior::Delay);
        burst.tick().await;
        let mut bursts = 4u32;
        let mut beat = tokio::time::interval(Duration::from_secs(15));
        beat.tick().await;
        let mut last_rx = Instant::now();
        loop {
            tokio::select! {
                msg = out.recv() => match msg {
                    Some(Out::Text(t)) => {
                        if let Err(e) = tx.send(Message::text(t)).await {
                            return End::Dropped(e.to_string());
                        }
                    }
                    Some(Out::Leave) | None => return self.exit().await,
                },
                _ = burst.tick(), if bursts > 0 => {
                    bursts -= 1;
                    let _ = tx.send(Message::text(json!({ "type": "ping", "clientMs": mono_ms() }).to_string())).await;
                }
                _ = beat.tick() => {
                    // The relay answers every ping; a minute and a half of silence is a dead
                    // socket, not a quiet room.
                    if last_rx.elapsed() > Duration::from_secs(90) {
                        return End::Dropped("no reply from the server".into());
                    }
                    let _ = tx.send(Message::text(json!({ "type": "ping", "clientMs": mono_ms() }).to_string())).await;
                }
                msg = rx.next() => {
                    last_rx = Instant::now();
                    let text = match msg {
                        Some(Ok(Message::Text(t))) => t,
                        Some(Ok(Message::Ping(p))) => { let _ = tx.send(Message::Pong(p)).await; continue; }
                        Some(Ok(Message::Close(_))) | None => return End::Dropped("closed by server".into()),
                        Some(Ok(_)) => continue,
                        Some(Err(e)) => return End::Dropped(e.to_string()),
                    };
                    send.clear();
                    if let Some(e) = self.frame(&text, &mut samples, &mut send) {
                        return e;
                    }
                    for t in send.iter() {
                        if let Err(e) = tx.send(Message::text(t.clone())).await {
                            return End::Dropped(e.to_string());
                        }
                    }
                }
            }
        }
    }

    /// Out of the room, and off the relay's member list. The relay has no "close the party":
    /// a room empties and expires, so leaving is all there is.
    async fn exit(&mut self) -> End {
        let url = format!("{}/api/parties/{}/leave", self.base, self.code);
        let _ = self.http.post(&url).bearer_auth(&self.token).send().await;
        self.token.clear();
        End::Done(None)
    }

    /// One inbound frame. `Some(end)` finishes the connection; `send` collects the frames to
    /// write back — the relay expects a `syncQueue` when a state frame names a queue we
    /// haven't been given.
    fn frame(&mut self, raw: &str, samples: &mut VecDeque<Sample>, send: &mut Vec<String>) -> Option<End> {
        let Ok(v) = serde_json::from_str::<Value>(raw) else {
            log::warn!("jam: unreadable message {}", raw.chars().take(160).collect::<String>());
            return None;
        };
        match v.get("type").and_then(Value::as_str).unwrap_or_default() {
            "welcome" => {
                self.apply(&v);
                self.create = false;
                self.emit(Event::Joined);
                self.emit(Event::Members);
                self.emit(Event::Music);
            }
            "state" => {
                let pb = v.get("playback").cloned().unwrap_or(Value::Null);
                let mut p = self.party.lock().unwrap();
                let stale = pb.is_object() && num(&pb, "queueSeq") != p.queue_seq;
                let applied = apply_playback(&mut p, &pb);
                drop(p);
                if applied {
                    self.emit(Event::Music);
                    if stale {
                        send.push(r#"{"type":"syncQueue"}"#.to_owned());
                    }
                }
            }
            "queue" => {
                let q = v.get("queue").cloned().unwrap_or(Value::Null);
                let mut p = self.party.lock().unwrap();
                let ok = apply_queue(&mut p, &q);
                drop(p);
                if ok {
                    self.emit(Event::Music);
                }
            }
            "members" => {
                {
                    let mut p = self.party.lock().unwrap();
                    p.max_members = num(&v, "maxMembers").max(MIN_MEMBERS as i64) as usize;
                    p.host_only = flag(&v, "hostOnlyControl");
                    p.members = members_of(&v);
                    // Being made host is only ever announced here: find ourselves again.
                    p.host = p.members.iter().find(|m| m.host).map(|m| m.id.clone()).unwrap_or_default();
                }
                self.emit(Event::Members);
                self.emit(Event::Music);
            }
            "pong" => {
                let client = num(&v, "clientMs");
                let server = num(&v, "serverMs");
                let rtt = mono_ms() - client;
                // A wild round trip (a laptop woken from sleep) would lock the clock to
                // nonsense; the quickest one still in date wins.
                if (0..30_000).contains(&rtt) {
                    samples.push_back(Sample { offset: server - (client + rtt / 2), rtt, at: mono_ms() });
                    while samples.len() > 12 {
                        samples.pop_front();
                    }
                    if let Some(best) = samples.iter().filter(|s| mono_ms() - s.at < 120_000).min_by_key(|s| s.rtt) {
                        self.offset.store(best.offset, Ordering::Relaxed);
                        self.synced.store(true, Ordering::Relaxed);
                    }
                }
            }
            "activity" => {
                let by = text(&v, "by");
                let detail = text(&v, "detail");
                let mine = {
                    let p = self.party.lock().unwrap();
                    !by.is_empty() && by == p.my_name
                };
                if !mine && !detail.is_empty() {
                    self.emit(Event::Activity(format!("{by} {detail}")));
                }
            }
            "error" => {
                let (code, msg) = (text(&v, "error"), text(&v, "message"));
                log::warn!("jam: the relay said {code}: {msg}");
                if matches!(code.as_str(), "bad_token" | "unauthorized") {
                    // Ask for a slot again instead of sitting out the party.
                    self.token.clear();
                    return Some(End::Dropped(format!("error {code}")));
                }
                if code == "no_such_party" {
                    return Some(End::Done(Some(party_words(&code, &msg, &self.base))));
                }
                self.emit(Event::Notice(party_words(&code, &msg, &self.base)));
            }
            "bye" => {
                let reason = text(&v, "reason");
                let msg = text(&v, "message");
                self.token.clear();
                return Some(End::Done(Some(if reason == "kicked" && !msg.is_empty() { msg } else { "You're out of the party".into() })));
            }
            other => log::debug!("jam: a message we don't use: {other}"),
        }
        None
    }
}

/// The same host on the socket scheme the relay listens for.
fn ws_url(base: &str) -> String {
    if let Some(s) = base.strip_prefix("https://") {
        return format!("wss://{s}");
    }
    if let Some(s) = base.strip_prefix("http://") {
        return format!("ws://{s}");
    }
    base.to_owned()
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn codes_follow_the_relay() {
        assert_eq!(normalize_code(" 50zm32 "), Some("50ZM32".into()));
        // Read-alike letters land on the digits the relay actually uses.
        assert_eq!(normalize_code("oil1o0"), Some("011100".into()));
        assert_eq!(normalize_code("ABC234"), Some("ABC234".into()));
        assert_eq!(normalize_code("ABC2345"), None);
        assert_eq!(normalize_code("ABC_23"), None);
        // Every code the relay hands out passes.
        for ch in ALPHABET.chars() {
            let c = format!("{ch}00000");
            assert_eq!(normalize_code(&c), Some(c), "{ch}");
        }
    }

    #[test]
    fn links_point_at_bitchord() {
        if std::env::var_os("YTM_JAM_SERVER").is_some() {
            return;
        }
        assert_eq!(server_url(""), SERVER);
        assert_eq!(server_url("jam.example.com/"), "https://jam.example.com");
        assert_eq!(server_url("ws://127.0.0.1:8787"), "http://127.0.0.1:8787");
        assert_eq!(share_link("", "abc123"), format!("{INVITE}/invite/ABC123"));
        assert_eq!(share_link("jam.example.com", "ABC123"), "https://jam.example.com/invite/ABC123");
        assert_eq!(ws_url(SERVER), "wss://api.bitchord.kushagrasingh.in");
    }

    #[test]
    fn songs_are_thin_on_the_wire() {
        let t = Track {
            id: "dQw4w9WgXcQ".into(),
            title: "Test Song".into(),
            artists: vec![Link { name: "Someone".into(), id: Some("UC1".into()) }],
            album: Some(Link { name: "An Album".into(), id: None }),
            duration: Some(200),
            thumbs: vec![Thumb { url: "https://i.ytimg.com/vi/x/hqdefault.jpg".into(), width: 480, height: 360 }],
            track_nr: Some(3),
            plays: None,
            music_video: true,
        };
        let i = item_of(&t);
        let w = serde_json::to_value(&i).unwrap();
        assert_eq!(w["videoId"], "dQw4w9WgXcQ");
        assert_eq!(w["artist"], "Someone");
        assert_eq!(w["durationMs"], 200_000);
        let back = track_of(&i);
        assert_eq!(back.id, t.id);
        assert_eq!(back.artist_line(), "Someone");
        assert_eq!(back.duration, Some(200));
        // Nothing about how to stream it travels: no album, no browse ids.
        assert!(back.album.is_none());
        assert_eq!(back.artists[0].id, None);
    }

    #[test]
    fn playheads_are_the_servers_clock() {
        let mut p = Party::default();
        let pb: Value = serde_json::from_str(
            r#"{"seq":3,"track":{"videoId":"x","title":"Song","artist":"Who","durationMs":200000},
                "queueSeq":2,"queueIndex":1,"isPlaying":true,"positionMs":5000,"anchorMs":1000,
                "autoplayEnabled":true,"startedByName":"Sam"}"#,
        )
        .unwrap();
        assert!(apply_playback(&mut p, &pb));
        assert_eq!(p.vid, "x");
        assert_eq!(p.duration_ms, 200_000);
        assert_eq!(p.who.as_deref(), Some("Sam"));
        assert_eq!(p.playhead_ms(11_000), 15_000);
        assert_eq!(p.playhead_ms(1_000_000), 200_000);
        // An older frame doesn't move us back.
        let old: Value = serde_json::from_str(r#"{"seq":2,"isPlaying":false,"positionMs":100,"anchorMs":100,"track":{"videoId":"y"}}"#).unwrap();
        assert!(!apply_playback(&mut p, &old));
        assert_eq!(p.vid, "x");
        // Paused: the position is the position.
        let pause: Value = serde_json::from_str(r#"{"seq":4,"isPlaying":false,"positionMs":9000,"anchorMs":20000,"track":{"videoId":"x"}}"#).unwrap();
        assert!(apply_playback(&mut p, &pause));
        assert_eq!(p.playhead_ms(60_000), 9000);
    }

    #[test]
    fn a_shared_queue_is_held_in_order() {
        let mut p = Party::default();
        let q: Value = serde_json::from_str(r#"{"seq":7,"index":0,"items":[{"videoId":"a","title":"A"},{"videoId":"b","title":"B","durationMs":61000}]}"#).unwrap();
        assert!(apply_queue(&mut p, &q));
        assert_eq!(p.queue.len(), 2);
        assert_eq!(p.index, 0);
        assert_eq!(p.queue[1].duration, Some(61));
        // An index past the end isn't trusted.
        let bad: Value = serde_json::from_str(r#"{"seq":8,"index":9,"items":[{"videoId":"a"}]}"#).unwrap();
        assert!(apply_queue(&mut p, &bad));
        assert_eq!(p.index, -1);
        // An older queue doesn't replace a newer one.
        let old: Value = serde_json::from_str(r#"{"seq":1,"index":0,"items":[]}"#).unwrap();
        assert!(!apply_queue(&mut p, &old));
        assert_eq!(p.queue.len(), 1);
    }

    #[test]
    fn the_playing_copy_of_a_repeated_song_wins() {
        let mut p = Party::default();
        let q: Value = serde_json::from_str(r#"{"seq":3,"index":2,"items":[{"videoId":"a"},{"videoId":"b"},{"videoId":"a"},{"videoId":"c"}]}"#).unwrap();
        assert!(apply_queue(&mut p, &q));
        // The song played once already: the queue's own index names the copy that is playing.
        assert_eq!(p.position_of("a"), Some(2));
        assert_eq!(p.position_of("d"), None);
        // A state frame read against this queue version is trusted first.
        let pb: Value = serde_json::from_str(r#"{"seq":4,"queueSeq":3,"queueIndex":0,"track":{"videoId":"a"}}"#).unwrap();
        assert!(apply_playback(&mut p, &pb));
        assert_eq!(p.position_of("a"), Some(0));
        // And when nothing names the song at all, the first copy is the fallback.
        p.index = -1;
        p.pb_queue_seq = 9;
        assert_eq!(p.position_of("a"), Some(0));
    }

    #[test]
    fn what_the_host_shares_fits_the_relay() {
        // Five songs of history, the current one, and 25 to come.
        let queue: Vec<Track> = (0..60).map(|i| track_of(&Item { video_id: format!("v{i}"), title: format!("S{i}"), ..Default::default() })).collect();
        let (index, from) = (20usize, 15usize);
        let to = (index + 1 + MAX_UPCOMING).min(queue.len()).max(from);
        assert_eq!((from, to), (15, 46));
        assert_eq!(to - from, 6 + MAX_UPCOMING);
        // Near the top and the bottom, the window just shrinks.
        assert_eq!((0usize).saturating_sub(5), 0);
        let tail = (59 + 1 + MAX_UPCOMING).min(queue.len());
        assert_eq!(tail, 60);
    }

    #[test]
    fn refusals_read_as_sentences() {
        assert_eq!(party_words("no_such_party", "No party with that code.", SERVER), "No party with that code. Check it and try again");
        assert_eq!(party_words("host_only", "Only the host can control the music in this party.", SERVER), "Only the host can do that in this party");
        assert_eq!(party_words("queue_full", "Queue is full (maximum 25 upcoming songs).", SERVER), "That party's queue holds 25 songs to come");
        // Never an HTML page or a bare code.
        assert_eq!(party_words("validation_error", "displayName must be between 1 and 80 characters.", SERVER), "displayName must be between 1 and 80 characters.");
        assert!(party_words("bad_token", "", SERVER).contains("api.bitchord.kushagrasingh.in"));
    }

    #[test]
    fn host_only_leaves_the_host_alone() {
        let mut p = Party { you: "m1".into(), host: "m2".into(), host_only: true, ..Default::default() };
        assert!(p.silent());
        p.host = "m1".into();
        assert!(!p.silent());
        assert!(!Party { you: "m1".into(), host: "m1".into(), host_only: false, ..Default::default() }.silent());
    }

    #[test]
    fn members_and_host_come_from_the_members_frame() {
        let v: Value = serde_json::from_str(r#"{"type":"members","members":[{"memberId":"m1","displayName":"Sam","isHost":true,"connected":true},{"memberId":"m2","displayName":"Ana","isHost":false,"connected":false}],"maxMembers":8,"hostOnlyControl":true,"serverMs":1}"#).unwrap();
        let m = members_of(&v);
        assert_eq!(m.len(), 2);
        assert!(m[0].host);
        assert!(!m[1].connected);
        assert_eq!(m[1].name, "Ana");
    }
}
