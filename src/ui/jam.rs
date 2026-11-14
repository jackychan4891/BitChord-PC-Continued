//! Listen together: the page (start, join, the room) and the two halves of a party —
//! telling the room what changed here, and getting ourselves to where the room is.
//! The connection and the relay's own words are `crate::jam`.
//!
//! The relay is the authority, not any one device. So every frame compares the local
//! player with the mirror of the room and sends only the difference, then does the same
//! in reverse and moves the player. Nothing is "sent to the host": the host is a member
//! like anyone else, and what one device announces, that device follows too.

use std::hash::{Hash, Hasher};
use std::sync::atomic::Ordering;
use std::time::{Duration, Instant};

use egui::{Align2, RichText, Sense, Ui, pos2, vec2};

use super::pages::{divider, page_title, section_label, setting_row, settings_card, settings_field, toggle};
use super::widgets::{icon_at, pill};
use super::{Action, App, Page};
use crate::audio::AudioCmd;
use crate::jam::{Event, Ids, Jam, MAX_UPCOMING, Party, Playhead};
use crate::model::Target;
use crate::theme::{self, icon};

/// Further than this from the room, twice running, and we jump to where it is.
const DRIFT_LIMIT: i64 = 1200;
const DRIFT_STRIKES: u8 = 2;
/// After a jump, nothing else is jumped for this long — so a hiccup doesn't stack.
const DRIFT_COOLDOWN: Duration = Duration::from_secs(6);
/// The room moved: line up with it, however far off we are, once it's worth it.
const ALIGN_TOLERANCE: i64 = 120;
/// Paused: the position only matters this closely.
const PAUSED_TOLERANCE: i64 = 400;
/// After sending something, wait this long for the room to agree before sending again.
/// Two devices answering each other is what this stops.
const QUIET: Duration = Duration::from_millis(4500);
/// How often to look at the room, and how often to tell it where we are.
const FOLLOW_EVERY: Duration = Duration::from_millis(700);
const REPORT_EVERY: Duration = Duration::from_secs(10);
/// Songs of the local queue kept behind the playing one, as the relay keeps them.
const HISTORY: usize = 5;

#[derive(Default)]
pub struct Session {
    conn: Option<Jam>,
    /// We started this room (the relay still gets to name the code).
    created: bool,
    /// The room's own words, kept from the last frame.
    party: Party,
    /// In the room, and the mirror is current.
    ready: bool,
    /// The good news has been said once; reconnects don't repeat it.
    joined_once: bool,
    reconnecting: bool,
    /// The code typed on the page.
    code_input: String,
    /// The name others see (saved); empty: the account's name.
    pub name: String,
    error: Option<String>,

    // what we've asked the room for
    /// The room's counters our last sends have to reach before we speak again.
    await_pb: i64,
    await_queue: i64,
    quiet: Option<Instant>,

    // following
    /// The song we started for the room, and when we asked for it.
    loaded: Option<String>,
    /// The last song reported to YouTube as played, so pauses don't count it twice.
    reported: Option<String>,
    load_started: Option<Instant>,
    /// When we last asked our own player to catch up with a room that's playing. It answers
    /// on its own time, and asking every frame is how one song ending turns into a storm.
    resume_ask: Option<Instant>,
    /// How long songs take to start here, so they're opened that far ahead.
    load_ms: u64,
    /// The room's counter we last lined up with, and the drift since.
    aligned_seq: i64,
    drift_strikes: u8,
    drift_cooldown: Option<Instant>,
    next_report: Option<Instant>,
    /// Our own play or pause, kept until the room agrees with it. `None`: we're following.
    pub intent: Option<bool>,
    /// Where the person moved the needle here, until the room takes it.
    pub pending_seek: Option<u64>,
    /// A song this device started with its own hand, and when. `publish` says nothing while
    /// the player loads, and the room's still-older head would pull the queue back before
    /// the ask was ever sent — which is how a skip vanished without trace.
    pub pending_song: Option<(String, Instant)>,
    /// The window this device showed the room last time we looked, and whether a hand moved
    /// it since — kept until the room hears it, which may wait on the player loading.
    window_fp: Option<u64>,
    hand_list: Option<Instant>,
    /// When we last asked the relay for a queue we seem to be missing.
    asked_queue: Option<Instant>,
    /// Steps of the YTM_JAM_SCRIPT dev hook already run.
    script_done: usize,
}

impl Session {
    pub fn active(&self) -> bool {
        self.conn.is_some()
    }

    pub fn is_host(&self) -> bool {
        self.active() && self.party.you_host()
    }

    /// In someone else's room: the next song is the room's business, so wait for it
    /// rather than advancing on our own.
    pub fn follows(&self) -> bool {
        self.active() && !self.party.you.is_empty() && !self.is_host()
    }

    /// In a room only the host drives, and we aren't the host: the relay turns down
    /// whatever we'd send, so don't send it.
    pub fn locked(&self) -> bool {
        self.active() && self.party.silent()
    }

    /// Whether the song playing here is one this person started by hand and the room
    /// hasn't heard about yet. Past two quiet windows the ask is stale, so follow the room.
    fn unannounced(&self, vid: Option<&str>) -> bool {
        self.pending_song.as_ref().is_some_and(|(id, at)| Some(id.as_str()) == vid && at.elapsed() < QUIET * 2)
    }
}

/// Note the song a person started here, so the room waits for it instead of pulling us
/// back to wherever it still was. Host and guest alike: until the ask is sent, the room's
/// own head is a song we no longer mean to play, and `reconcile` would walk us back to it.
pub fn hand_picked(app: &mut App) {
    if app.jam.active() && !app.jam.locked() {
        app.jam.pending_song = app.current().map(|t| (t.id.clone(), Instant::now()));
    }
}

/// Who this device is to the relay. Both ids are stable, which is how a reconnecting
/// member keeps its place in the room instead of arriving as a stranger.
fn ids(app: &mut App) -> Ids {
    if app.jam_user.is_empty() {
        let mut user = std::collections::hash_map::DefaultHasher::new();
        (app.backend.data_dir().to_string_lossy().to_string(), std::env::var("USERNAME").or_else(|_| std::env::var("USER")).unwrap_or_default()).hash(&mut user);
        app.jam_user = format!("{:016x}", user.finish());
        let mut device = std::collections::hash_map::DefaultHasher::new();
        (app.jam_user.clone(), "device").hash(&mut device);
        app.jam_device = format!("{:016x}", device.finish());
    }
    Ids { user: app.jam_user.clone(), device: app.jam_device.clone(), avatar: app.account_avatar.clone().filter(|a| !a.is_empty()) }
}

fn my_name(app: &App) -> String {
    [Some(app.jam.name.clone()), app.account_name.clone()]
        .into_iter()
        .flatten()
        .map(|n| n.trim().to_owned())
        .find(|n| !n.is_empty())
        .unwrap_or_else(|| "Guest".to_owned())
}

/// A new session, keeping what the page holds: the typed code, the name, and what we've
/// learned about how long songs take to start on this machine.
fn fresh(app: &mut App, created: bool) {
    let (code_input, name, load_ms) = (std::mem::take(&mut app.jam.code_input), std::mem::take(&mut app.jam.name), app.jam.load_ms);
    app.jam = Session { created, code_input, name, load_ms: load_ms.max(800), ..Default::default() };
}

pub fn start(app: &mut App) {
    leave(app);
    fresh(app, true);
    let (name, server, ids) = (my_name(app), app.jam_server.clone(), ids(app));
    app.jam.conn = Some(Jam::start(&app.backend, true, String::new(), name, server, ids));
}

pub fn join(app: &mut App, code: &str) {
    let Some(code) = crate::jam::normalize_code(code) else {
        app.jam.error = Some("A party code is six letters or numbers.".into());
        return;
    };
    if app.jam.conn.as_ref().is_some_and(|c| c.code() == code) {
        return;
    }
    leave(app);
    fresh(app, false);
    let (name, server, ids) = (my_name(app), app.jam_server.clone(), ids(app));
    app.jam.conn = Some(Jam::start(&app.backend, false, code, name, server, ids));
}

/// Leave the room. The relay has no "end the party": a room goes with its last member,
/// so a host who is done simply leaves too. Playback carries on here.
pub fn leave(app: &mut App) {
    app.jam.conn = None;
    fresh(app, false);
}

/// Dev hooks: YTM_JAM_HOST starts a room, YTM_JAM_JOIN=<code> joins one. The relay names
/// a room we start, so there's no code for the host's hook to carry.
pub fn dev_hooks(app: &mut App) {
    if super::dev_var("YTM_JAM_HOST").is_some() {
        app.go(Page::Settings);
        start(app);
        app.jam_open = true;
    } else if let Some(code) = super::dev_var("YTM_JAM_JOIN") {
        app.go(Page::Settings);
        join(app, &code);
        app.jam_open = true;
    }
}

/// Dev hook: YTM_JAM_SCRIPT="<secs>:<action>,…" presses buttons at those app times, as
/// whoever this instance is in the room. Actions: skip, prev, toggle, seek=<secs>, tail,
/// add=<videoId>, move=up|down, peek, control=on|off.
pub fn dev_script(app: &mut App, ctx: &egui::Context, now: f64) {
    let Some(script) = super::dev_var("YTM_JAM_SCRIPT") else { return };
    for (i, step) in script.split(',').enumerate() {
        let Some((t, act)) = step.split_once(':') else { continue };
        let Ok(t) = t.parse::<f64>() else { continue };
        if i < app.jam.script_done {
            continue;
        }
        if now < t {
            // A paused app doesn't redraw on its own.
            ctx.request_repaint_after(Duration::from_secs_f64(t - now));
            break;
        }
        app.jam.script_done = i + 1;
        log::info!("jam: script {act}");
        match act {
            "skip" => app.next(),
            "prev" => app.prev(),
            "toggle" => app.toggle(),
            "peek" => {
                let p = &app.jam.party;
                let ids = |t: &[crate::model::Track]| t.iter().take(6).map(|x| x.id.as_str()).collect::<Vec<_>>().join(" ");
                log::info!("jam: peek ours={} idx={:?} room={} ridx={} vid={} play={} seq={} q=[{}] rq=[{}]", app.queue.tracks.len(), app.queue.index, p.queue.len(), p.index, p.vid, p.playing, p.seq, ids(&app.queue.tracks), ids(&p.queue));
            }
            "control=on" => set_host_only(app, true),
            "control=off" => set_host_only(app, false),
            "near_end" => {
                // Park twelve seconds short of the finish, so the song ends on its own while
                // the test is still watching — the one change nobody asks the room for.
                let d = app.audio.status.duration_ms.load(Ordering::Relaxed);
                if d > 20_000 {
                    app.apply(Action::Seek(((d - 12_000) / 1000) as f32));
                } else {
                    log::info!("jam: near_end skipped, length unknown ({d} ms)");
                }
            }
            a if a.starts_with("move=") => {
                // Slide a song still to come one place, as the queue panel's arrows do.
                if let Some(cur) = app.queue.index {
                    let (i, dir) = if &a[5..] == "up" { (cur + 2, -1) } else { (cur + 1, 1) };
                    app.apply(Action::QueueMove(i, dir));
                }
            }
            a if a.starts_with("add=") => {
                let track = crate::model::Track { id: a[4..].to_owned(), title: "Test song".into(), artists: vec![], album: None, duration: None, thumbs: vec![], track_nr: None, plays: None, music_video: false };
                app.apply(Action::Enqueue(track));
            }
            a if a.starts_with("seek=") => {
                if let Ok(s) = a[5..].parse() {
                    app.apply(Action::Seek(s));
                }
            }
            "tail" => {
                // Park twelve seconds short of the finish: that way the song ends by itself
                // while the test is still watching, which is the one change nobody asks for.
                let d = app.audio.status.duration_ms.load(Ordering::Relaxed);
                if d > 20_000 {
                    log::info!("jam: tail of a {d} ms song");
                    app.apply(Action::Seek(((d - 12_000) / 1000) as f32));
                } else {
                    log::info!("jam: tail skipped, length unknown ({d} ms)");
                }
            }
            _ => {}
        }
    }
}

/// A link (or a code from another launch) asked to join a room. A link naming its own
/// relay is a party on a server of the host's choosing, so follow it there.
pub fn open_link(app: &mut App, code: String, server: Option<String>) {
    if let Some(server) = server {
        app.jam_server = server;
    }
    app.go(Page::Settings);
    join(app, &code);
    app.jam_open = true;
}

/// Every frame: take what the relay said, then tell it our differences and follow where
/// the room is.
pub fn tick(app: &mut App, ctx: &egui::Context) {
    let (events, party, head, clock) = {
        let Some(conn) = &app.jam.conn else { return };
        let mut events = Vec::new();
        while let Some(e) = conn.try_recv() {
            events.push(e);
        }
        (events, conn.party(), conn.position(), conn.synced())
    };
    let promoted = app.jam.ready && !app.jam.party.you_host() && party.you_host();
    for e in events {
        on_event(app, e);
    }
    if promoted {
        app.toast("You're the host now");
    }
    app.jam.party = party.clone();
    if !app.jam.ready || app.jam.reconnecting {
        return;
    }
    // Once the room's numbers include what we sent — or nobody answered — we can speak again.
    if let Some(at) = app.jam.quiet
        && (party.seq >= app.jam.await_pb && party.queue_seq >= app.jam.await_queue || at.elapsed() > QUIET + Duration::from_secs(4))
    {
        app.jam.quiet = None;
    }
    watch_load(app);
    if clock {
        // Did a hand move this device's list since the last look? Only something changed here
        // is ours to tell the room; a list that merely differs because the room's moved is one
        // to take, not to hand back — that's what made two devices trade orders forever and a
        // reorder never land. Measured before either half runs, and again after the room's own
        // changes have been taken, so following the room never reads as ours.
        let fp = window_fp(app);
        if app.jam.window_fp.is_some_and(|last| last != fp) {
            app.jam.hand_list = Some(Instant::now());
        }
        app.jam.window_fp = Some(fp);
        publish(app, &party, head.as_ref());
        reconcile(app, ctx, &party, head.as_ref());
        app.jam.window_fp = Some(window_fp(app));
        if app.jam.hand_list.is_some_and(|t| t.elapsed() > QUIET * 2) {
            app.jam.hand_list = None;
        }
        report(app, head.as_ref());
    }
    // The room's position moves with its own clock, so keep looking at it.
    ctx.request_repaint_after(FOLLOW_EVERY);
}

fn on_event(app: &mut App, e: Event) {
    match e {
        Event::Joined => {
            let (first, created) = (!app.jam.ready, app.jam.created);
            app.jam.ready = true;
            app.jam.reconnecting = false;
            app.jam.error = None;
            if first && !app.jam.joined_once {
                app.jam.joined_once = true;
                app.toast(if created { "Party started: share the code or link" } else { "Joined the party" });
            }
        }
        // The mirror is already current; this frame reads it.
        Event::Members | Event::Music => {}
        Event::Activity(m) | Event::Notice(m) => {
            log::info!("jam: {m}");
            app.toast(m);
        }
        Event::Reconnecting => app.jam.reconnecting = true,
        Event::Closed(msg) => {
            let first = !app.jam.ready;
            leave(app);
            if first {
                app.jam.error = Some(msg);
            } else {
                app.toast(msg);
            }
        }
    }
}

/// Where the player is, as the room counts it.
fn position_ms(app: &App) -> u64 {
    let live = app.audio.status.playing_gen.load(Ordering::Relaxed) == app.generation;
    match app.resume_at {
        // A parked position describes a song that hasn't been loaded yet. Once this generation
        // really is the one running, the player's own needle is the truth — otherwise a park
        // left by an earlier room state is told to the party as the new song's position, and
        // everybody opens the next song minutes in.
        Some(at) if !live => at.as_millis() as u64,
        _ => app.audio.status.position_smooth().as_millis() as u64,
    }
}

/// A fingerprint of the window this device shows the room: the playing song and what's
/// coming after it. It only changes because of something done here.
fn window_fp(app: &App) -> u64 {
    let Some(index) = app.queue.index else { return 0 };
    let mut h = std::collections::hash_map::DefaultHasher::new();
    app.queue.tracks[index..].iter().take(1 + MAX_UPCOMING).for_each(|t| t.id.hash(&mut h));
    h.finish()
}

// ------------------------------------------------------------------- the room hears us

/// Send what's different: the queue around the playing song, the song, play or pause, a
/// seek. Then wait for the room to agree before sending anything else.
fn publish(app: &mut App, party: &Party, head: Option<&Playhead>) {
    if app.jam.locked() || app.jam.quiet.is_some() || app.buffering {
        return;
    }
    let Some(index) = app.queue.index else { return };
    let Some(track) = app.current().cloned() else { return };
    let (cur, pos_ms) = (track.id.clone(), position_ms(app));
    if party.vid != cur && app.jam.loaded.as_deref() == Some(cur.as_str()) {
        // The room's head has moved to a song we don't hold yet and `reconcile` is asking
        // for it. Say nothing meanwhile, or we hand the room back the song we were listening
        // to a moment ago and its own new one never lands.
        return;
    }
    // Play and pause reach the room only as a deliberate ask from this device. Our own
    // player's flag lags a beat behind what the room just told us to do, and reporting that
    // lag is how two devices spend each other's play and pause flipping forever.
    let ask = app.jam.intent;
    // The window the room is shown: this song and the ones after it.
    let local: Vec<&str> = app.queue.tracks[index..].iter().take(1 + MAX_UPCOMING).map(|t| t.id.as_str()).collect();
    let room: Vec<&str> = party.position_of(&cur).map_or_else(Vec::new, |i| party.queue[i..].iter().take(1 + MAX_UPCOMING).map(|t| t.id.as_str()).collect());
    let (mut playback, mut queue) = (0i64, 0i64);
    // Ours goes to the room when we changed it — or when the room has never heard the song
    // playing here at all, which is a room with nothing to argue about.
    if !carries(&room, &local) && (app.jam.hand_list.is_some() || party.position_of(&cur).is_none()) {
        queue += 1;
        app.jam.hand_list = None;
        if let Some(c) = &app.jam.conn {
            log::debug!("jam: sharing {} songs from {index}", local.len());
            c.share_queue(&app.queue.tracks, index);
        }
    }
    // Where this song sits in the queue the room was just given.
    let from = index.saturating_sub(HISTORY);
    if party.vid != cur {
        playback += 1;
        // The new song goes to the room at our position, so that ask is answered too. The
        // hand pause it may have carried described the song before this one.
        app.jam.pending_seek = None;
        app.jam.intent = None;
        if let Some(c) = &app.jam.conn {
            log::info!("jam: the room plays \"{}\" at {pos_ms} ms", track.title);
            c.start_track(&track, Some(index - from), pos_ms, app.playing());
        }
    } else if let Some(want) = ask.filter(|want| *want != party.playing) {
        playback += 1;
        if let Some(c) = &app.jam.conn {
            log::debug!("jam: we ask the room to {} at {pos_ms} ms", if want { "play" } else { "hold" });
            if want {
                c.play(pos_ms);
            } else {
                c.pause(pos_ms);
            }
        }
    } else if let Some(ms) = app.jam.pending_seek {
        // A needle this device moved, told to the room until the room agrees. Only a hand
        // seek counts: a position that slipped away from the room on its own is drift, and
        // answering that would drag everybody else back to us. Paused counts too — moving
        // the position while the room holds still is a seek the room has to hear.
        if head.is_none_or(|h| (ms as i64 - h.ms as i64).abs() > PAUSED_TOLERANCE) {
            playback += 1;
            if let Some(c) = &app.jam.conn {
                log::debug!("jam: we ask the room for {ms} ms");
                c.seek(ms);
            }
        } else {
            app.jam.pending_seek = None;
        }
    }
    if playback + queue > 0 {
        app.jam.await_pb = party.seq + playback;
        app.jam.await_queue = party.queue_seq + queue;
        app.jam.quiet = Some(Instant::now());
    }
}

/// Whether `room` already holds everything `local` has, in the same order. A room with
/// more to come than we have is normal — someone else's queue is longer, or the relay kept
/// its history — and reading that as a difference makes two devices hand the whole queue
/// back and forth, one shortening it and the next lengthening it, forever.
fn carries(room: &[&str], local: &[&str]) -> bool {
    room.len() >= local.len() && room[..local.len()] == local[..]
}

/// Whether only the host may drive the room.
fn set_host_only(app: &mut App, on: bool) {
    app.jam.party.host_only = on;
    if let Some(c) = &app.jam.conn {
        c.set_host_only(on);
    }
}

/// A song we started for the room has begun: learn how long that took, so the next one is
/// opened far enough ahead to land where the room is.
fn watch_load(app: &mut App) {
    let Some(t) = app.jam.load_started else { return };
    if app.buffering || !app.playing() {
        return;
    }
    app.jam.load_started = None;
    let took = t.elapsed().as_millis() as u64;
    app.jam.load_ms = ((app.jam.load_ms + took.min(8000)) / 2).max(200);
    log::info!("jam: the song started after {took} ms");
}

/// Our position, for the relay's drift log.
fn report(app: &mut App, head: Option<&Playhead>) {
    if head.is_none() || !app.jam.next_report.is_some_and(|t| t.elapsed() >= REPORT_EVERY) {
        return;
    }
    app.jam.next_report = Some(Instant::now());
    if let Some(c) = &app.jam.conn {
        c.report(position_ms(app), app.playing());
    }
}

// --------------------------------------------------------------------- we hear the room

/// Get the player to where the room is: its song, its position, its play or pause.
fn reconcile(app: &mut App, ctx: &egui::Context, party: &Party, head: Option<&Playhead>) {
    let Some(head) = head else { return };
    // The room has come round to what we asked for: from here we're simply following it.
    if app.jam.intent == Some(head.playing) {
        log::debug!("jam: the room agrees with our {}", if head.playing { "play" } else { "pause" });
        app.jam.intent = None;
    }
    // A song of our own that the room has now taken: from here we follow it again, and it's
    // already running here — reopening it would set the whole party back by a load.
    if app.jam.pending_song.as_ref().is_some_and(|(id, _)| *id == head.vid) {
        app.jam.pending_song = None;
        app.jam.loaded = Some(head.vid.clone());
    }
    // What we asked the room for holds until it answers — which is also what keeps our own
    // pause from being walked straight back by the room's still-unmoved state. In a room
    // only the host drives, it stays ours alone.
    let mut playing = app.jam.intent.unwrap_or(head.playing);
    let target = Duration::from_millis(head.ms);

    // 1. The room's song.
    if app.current_id() != Some(head.vid.as_str()) {
        if app.jam.unannounced(app.current_id()) {
            // A song this device started by hand hasn't reached the room yet: it's still
            // loading and `publish` waits for the load. Following the room now would cancel
            // the ask before it was ever sent, so the room waits for us instead.
            return;
        }
        if let Some(i) = app.queue.tracks.iter().position(|t| t.id == head.vid) {
            app.queue.index = Some(i);
        } else {
            // Not in our list at all: take the room's window, from where it plays.
            let Some(i) = party.position_of(&head.vid) else {
                // The room is mid-something our copy of its queue never held. Only a
                // `queue` frame can tell us what it is, so ask for one again.
                if app.jam.asked_queue.is_none_or(|t| t.elapsed() > Duration::from_secs(2)) {
                    app.jam.asked_queue = Some(Instant::now());
                    if let Some(c) = &app.jam.conn {
                        c.ask_queue();
                    }
                }
                return;
            };
            app.queue_source = None;
            app.queue.fill = None;
            app.play_pending = None;
            app.queue.tracks = party.queue[i..].iter().take(1 + MAX_UPCOMING).cloned().collect();
            app.queue.index = Some(0);
        }
        app.jam.loaded = None;
        // A new song is the room speaking: follow it, pause or no pause, and forget the
        // position we wanted in the last one, or the song we started with our own hand.
        app.jam.intent = None;
        app.jam.pending_song = None;
        app.jam.pending_seek = None;
        app.jam.hand_list = None;
        playing = head.playing;
    }

    // 1.5 The room's list whole. Taking only the window that was current when a song first
    // arrived leaves a device holding one song with nothing after it, and a skip then does
    // nothing at all. So the room's window becomes ours: what it has coming that we haven't
    // is appended, and what it has in another order — a song someone moved — replaces ours.
    // Not while something done here is still on its way to the room: taking its answer back
    // before it hears us is how a change vanished without trace.
    let ours = app.jam.hand_list.is_none() && !app.buffering && app.jam.quiet.is_none() && !app.jam.unannounced(app.current_id());
    let take = match (ours, app.queue.index, party.position_of(&head.vid)) {
        (true, Some(at), Some(from)) => {
            let local: Vec<&str> = app.queue.tracks[at..].iter().take(1 + MAX_UPCOMING).map(|t| t.id.as_str()).collect();
            let room: Vec<&str> = party.queue[from..].iter().take(1 + MAX_UPCOMING).map(|t| t.id.as_str()).collect();
            let carried = carries(&room, &local);
            (!carried || room.len() > local.len()).then(|| (from, from + room.len(), at, !carried))
        }
        _ => None,
    };
    if let Some((from, to, at, remade)) = take {
        log::debug!("jam: taking the room's {} songs from its {}", to - from, if remade { "new order" } else { "list" });
        app.queue.tracks.splice(at.., party.queue[from..to].iter().cloned());
        if remade {
            // The party's order is the room's now; a playlist still loading can't add over it.
            app.queue.fill = None;
        }
    }

    // 2. Play it, or hold it paused at the room's position.
    let started = app.jam.loaded.as_deref() == Some(head.vid.as_str());
    if playing {
        if !started || app.resume_at.is_some() {
            // Opened a little ahead: by the time it starts, the room is there.
            let report = app.jam.reported.as_deref() != Some(head.vid.as_str());
            app.jam.reported = Some(head.vid.clone());
            app.jam.loaded = Some(head.vid.clone());
            app.jam.load_started = Some(Instant::now());
            app.play_current_at(target + Duration::from_millis(app.jam.load_ms), report);
            return;
        }
        if app.buffering {
            return;
        }
        if head.lead_ms > 0 {
            // The room starts at a moment that hasn't arrived yet: start with it, not now.
            ctx.request_repaint_after(Duration::from_millis(head.lead_ms + 25));
            return;
        }
        // The room moves on its own clock, so keep looking at it. Our own player answering
        // ends the ask: the next time it lags, it gets asked again.
        if app.playing() {
            app.jam.resume_ask = None;
        }
        if !app.playing() && app.jam.resume_ask.is_none_or(|t| t.elapsed() >= DRIFT_COOLDOWN) {
            app.jam.resume_ask = Some(Instant::now());
            log::debug!("jam: the room plays (seq {}), so do we", party.seq);
            app.audio.send(AudioCmd::Resume);
        }
    } else if !started {
        // Paused and never loaded: show the song at the room's position without audio.
        app.audio.send(AudioCmd::Stop);
        app.jam.loaded = None;
        app.resume_at = Some(target);
        let length = head.duration_ms.max(app.current().and_then(|t| t.duration).map_or(0, |d| d as u64 * 1000));
        app.audio.status.position_ms.store(head.ms, Ordering::Relaxed);
        app.audio.status.duration_ms.store(length, Ordering::Relaxed);
        app.request_art();
        return;
    } else {
        if app.playing() {
            log::debug!("jam: the room holds (seq {}), so we do", party.seq);
            app.audio.send(AudioCmd::Pause);
        }
        if app.jam.intent.is_none()
            && !app.buffering
            && (app.audio.status.position_smooth().as_millis() as i64 - target.as_millis() as i64).abs() > PAUSED_TOLERANCE
        {
            app.audio.send(AudioCmd::Seek(target));
        }
        return;
    }

    // 3. Steady drift: two bad readings in a row jump the needle. A room that hasn't
    // answered us yet isn't drifting — it's about to catch up with what we just did.
    if app.buffering {
        return;
    }
    let off = app.audio.status.position_smooth().as_millis() as i64 - target.as_millis() as i64;
    let cooling = app.jam.drift_cooldown.is_some_and(|t| t.elapsed() < DRIFT_COOLDOWN) || app.jam.quiet.is_some();
    let (jump, aligned, strikes, cooldown) = decide_seek(off, party.seq, app.jam.aligned_seq, app.jam.drift_strikes, cooling);
    app.jam.aligned_seq = aligned;
    app.jam.drift_strikes = strikes;
    if !cooldown {
        if off.abs() > 200 {
            log::debug!("jam: {off:+} ms from the room");
        }
        return;
    }
    debug_assert!(jump);
    app.jam.drift_cooldown = Some(Instant::now());
    log::info!("jam: {off:+} ms from the room, seeking");
    app.audio.send(AudioCmd::SeekLive { pos: target, at: Instant::now() });
}

/// Whether to jump to the room, and the counters that go with the answer. The drift is
/// real when it's big twice running; but the room moving at all is worth lining up with
/// straight away — and after a jump the next one waits out the cooldown.
fn decide_seek(off: i64, seq: i64, aligned: i64, strikes: u8, cooling: bool) -> (bool, i64, u8, bool) {
    let d = off.abs();
    if seq != aligned {
        return (d > ALIGN_TOLERANCE, seq, 0, d > ALIGN_TOLERANCE);
    }
    if d <= DRIFT_LIMIT {
        return (false, aligned, 0, false);
    }
    if strikes + 1 >= DRIFT_STRIKES && !cooling {
        return (true, aligned, 0, true);
    }
    (false, aligned, strikes + 1, false)
}

// ---------------------------------------------------------------------- the page

/// Whether an action would change the room. In a room only the host drives, the relay
/// turns these down for us, so the app says so instead of clicking into nothing.
pub fn refused(app: &App, a: &Action) -> bool {
    match a {
        // The song already playing is a pause, and pausing is ours to do here.
        Action::Play(tracks, i) => !tracks.get(*i).is_some_and(|t| app.current_id() == Some(t.id.as_str())),
        Action::PlayCollection(..)
        | Action::PlayNext(_)
        | Action::Enqueue(_)
        | Action::Radio(_)
        | Action::Open(Target::Song(_))
        | Action::QueueJump(_)
        | Action::QueueRemove(_)
        | Action::QueueMove(..)
        | Action::Seek(_) => true,
        _ => false,
    }
}

/// Settings › Account › Listen together: the party controls as a section of their own,
/// with one line back to Account. Returns true when that line is pressed.
pub fn subpage(app: &mut App, ui: &mut Ui) -> bool {
    ui.add_space(12.0);
    let back = pill(ui, "Account", Some(icon::CHEVRON_LEFT), false).clicked();
    page_title(ui, "Listen together");
    if app.jam.active() {
        room(app, ui);
    } else {
        lobby(app, ui);
    }
    back
}

fn dim(ui: &mut Ui, text: &str) {
    ui.label(RichText::new(text).font(theme::regular(13.5)).color(theme::text_dim()));
}

fn lobby(app: &mut App, ui: &mut Ui) {
    dim(ui, &format!("Listen to the same music at the same moment, on up to {} devices. Anyone in the party can play, pause and skip, unless the host turns that off.", crate::jam::DEFAULT_MEMBERS));
    if app.account_name.is_none() {
        ui.add_space(8.0);
        dim(ui, "A party uses your YouTube Music account for your name. Sign in above to bring it along.");
    }
    section_label(ui, "Start a party", None);
    settings_card(ui, |ui| {
        setting_row(ui, "Whatever you're playing comes with you", Some("You get a code and a link to share."), |ui| {
            if pill(ui, "Start", Some(icon::USERS), true).clicked() {
                start(app);
            }
        });
    });
    section_label(ui, "Join with a code", None);
    settings_card(ui, |ui| {
        ui.add_space(12.0);
        ui.label(RichText::new("Party code").font(theme::semibold(14.5)).color(theme::text()));
        ui.add_space(3.0);
        dim(ui, "Type the code from the host, or open the link they shared.");
        ui.add_space(10.0);
        let mut join_now = false;
        // Button on the right edge, the field filling the rest, both one height.
        ui.allocate_ui_with_layout(vec2(ui.available_width(), 42.0), egui::Layout::right_to_left(egui::Align::Center), |ui| {
            join_now |= pill(ui, "Join", None, true).clicked();
            ui.add_space(10.0);
            let (rect, _) = ui.allocate_exact_size(vec2(ui.available_width(), 42.0), Sense::hover());
            ui.painter().rect_filled(rect, 10.0, theme::glass(0.08));
            let inner = rect.shrink2(vec2(14.0, 4.0));
            let edit = ui.put(
                inner,
                egui::TextEdit::singleline(&mut app.jam.code_input)
                    .hint_text(RichText::new("ABC234").color(theme::text_faint()))
                    .font(theme::semibold(16.0))
                    .char_limit(crate::jam::CODE_LEN)
                    .frame(egui::Frame::NONE)
                    .align(egui::Align2::LEFT_CENTER)
                    .desired_width(inner.width()),
            );
            if edit.changed() {
                app.jam.error = None;
            }
            join_now |= edit.lost_focus() && ui.input(|i| i.key_pressed(egui::Key::Enter));
        });
        if join_now {
            let code = app.jam.code_input.clone();
            join(app, &code);
        }
        if let Some(e) = &app.jam.error {
            ui.add_space(8.0);
            ui.label(RichText::new(e).font(theme::regular(13.0)).color(theme::RED));
        }
        ui.add_space(14.0);
    });
    section_label(ui, "You", None);
    settings_card(ui, |ui| {
        let hint = app.account_name.clone().unwrap_or_else(|| "Guest".to_owned());
        setting_row(ui, "Name in parties", Some("What others see. Empty uses your account name."), |ui| {
            settings_field(ui, &mut app.jam.name, &hint, 32);
        });
        divider(ui);
        setting_row(ui, "Party server", Some("Empty uses BitChord's. Whoever joins your party goes to the server it's on."), |ui| {
            settings_field(ui, &mut app.jam_server, "jam.example.com", 96);
        });
    });
}

fn room(app: &mut App, ui: &mut Ui) {
    let (code, host, max, people) = (app.jam.party.code.clone(), app.jam.is_host(), app.jam.party.max_members.max(2), app.jam.party.members.len());
    let status = if !app.jam.ready {
        Some("Connecting…")
    } else if app.jam.reconnecting {
        Some("Connection lost, reconnecting…")
    } else {
        None
    };
    let (song, started_by, mine) = (app.jam.party.title.clone(), app.jam.party.who.clone(), app.jam.party.my_name.clone());
    settings_card(ui, |ui| {
        ui.add_space(16.0);
        dim(ui, if host { "Share this code or link with friends" } else { "You're in the party" });
        ui.add_space(6.0);
        let spaced: String = code.chars().map(|c| c.to_string()).collect::<Vec<_>>().join(" ");
        ui.label(RichText::new(if spaced.is_empty() { "…".to_owned() } else { spaced }).font(theme::bold(40.0)).color(theme::text()));
        if let Some(s) = status {
            ui.horizontal(|ui| {
                ui.spinner();
                dim(ui, s);
            });
        }
        if !song.is_empty() {
            match started_by {
                Some(who) if who != mine => dim(ui, &format!("{song} · from {who}")),
                _ => dim(ui, &song),
            }
        }
        ui.add_space(10.0);
        let link = crate::jam::share_link(&app.jam_server, &code);
        if pill(ui, "Copy link", Some(icon::LINK), true).clicked() {
            app.share_pending = Some(link.clone());
            app.toast("Link copied");
        }
        ui.add_space(8.0);
        if pill(ui, "Copy code", Some(icon::COPY), false).clicked() {
            app.share_pending = Some(code.clone());
            app.toast("Code copied");
        }
        ui.add_space(8.0);
        dim(ui, &link);
        ui.add_space(14.0);
    });

    section_label(ui, "Party playback", None);
    settings_card(ui, |ui| {
        if host {
            let mut on = app.jam.party.host_only;
            setting_row(ui, "Only I control the party", Some("Off: everyone plays, pauses, skips and adds songs."), |ui| {
                if toggle(ui, &mut on, "Only I control the party").changed() {
                    set_host_only(app, on);
                }
            });
        } else {
            let desc = if app.jam.party.host_only { "The host controls the party. Pausing here only pauses here." } else { "Everyone controls the party." };
            setting_row(ui, "Following the party", Some(desc), |_| {});
        }
        divider(ui);
        setting_row(ui, "Leave the party", Some("The music keeps playing here, on your own."), |ui| {
            if pill(ui, "Leave", Some(icon::LOG_OUT), false).clicked() {
                leave(app);
            }
        });
    });

    section_label(ui, "People", Some(&format!("{people} of {max} listening")));
    settings_card(ui, |ui| {
        ui.add_space(6.0);
        let (members, you, host_id) = (app.jam.party.members.clone(), app.jam.party.you.clone(), app.jam.party.host.clone());
        for m in &members {
            let (rect, _) = ui.allocate_exact_size(vec2(ui.available_width(), 40.0), Sense::hover());
            let is_host = !host_id.is_empty() && m.id == host_id;
            icon_at(ui, pos2(rect.left() + 12.0, rect.center().y), if is_host { icon::CROWN } else { icon::HEADPHONES }, 17.0, if is_host { app.accent } else { theme::text_dim() });
            let name = format!("{}{}{}", m.name, if m.id == you { " (you)" } else { "" }, if m.connected { "" } else { " · away" });
            ui.painter().text(pos2(rect.left() + 34.0, rect.center().y), Align2::LEFT_CENTER, name, theme::semibold(14.5), if m.connected { theme::text() } else { theme::text_dim() });
            if is_host {
                ui.painter().text(pos2(rect.right() - 4.0, rect.center().y), Align2::RIGHT_CENTER, "Host", theme::regular(12.5), theme::text_dim());
            }
        }
        ui.add_space(6.0);
    });
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn a_hiccup_does_not_jump_the_needle() {
        // Far off, but only once: a strike, no jump.
        assert_eq!(decide_seek(2000, 4, 4, 0, false), (false, 4, 1, false));
        // Far off twice running: jump, and start the cooldown.
        assert_eq!(decide_seek(2000, 4, 4, 1, false), (true, 4, 0, true));
        // Still far off during the cooldown: another strike, but no second jump.
        assert_eq!(decide_seek(2000, 4, 4, 0, true), (false, 4, 1, false));
        // Back in step: the strikes are forgotten.
        assert_eq!(decide_seek(900, 4, 4, 1, false), (false, 4, 0, false));
        // The room moved: line up with it, at once if the gap is real.
        assert_eq!(decide_seek(300, 5, 4, 0, false), (true, 5, 0, true));
        // The room moved and we were already close: nothing to do.
        assert_eq!(decide_seek(90, 6, 4, 0, false), (false, 6, 0, false));
    }

    #[test]
    fn a_longer_room_is_not_a_different_room() {
        // A whole playlist against the party's 26: the room holds everything we have, in
        // order, and more. Sharing it back would only make the other device answer.
        assert!(carries(&["a", "b", "c", "d"], &["a", "b", "c"]));
        assert!(carries(&["a", "b", "c"], &["a", "b", "c"]));
        assert!(carries(&["a"], &[]));
        // Shorter, or the same songs in another order: the room has to be told.
        assert!(!carries(&["a", "b"], &["a", "b", "c"]));
        assert!(!carries(&["a", "c", "b"], &["a", "b", "c"]));
        assert!(!carries(&[], &["a"]));
    }
}
