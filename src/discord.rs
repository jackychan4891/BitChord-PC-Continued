//! Discord Rich Presence: "Listening to <song>" with cover art and a progress bar.
//!
//! A worker thread owns the IPC pipe so a missing or restarting Discord never blocks the
//! UI. The UI sends the wanted state whenever it changes; the worker coalesces bursts
//! (Discord rate-limits activity updates) and reconnects every 15 s while Discord is
//! closed.

use std::time::{Duration, Instant};

use crossbeam_channel::{Receiver, RecvTimeoutError, Sender};
use discord_rich_presence::activity::{Activity, ActivityType, Assets, Button, StatusDisplayType, Timestamps};
use discord_rich_presence::{DiscordIpc, DiscordIpcClient};

/// Application ID from the Discord Developer Portal. Its name is what Discord shows
/// as "Listening to <name>" in the profile card.
const CLIENT_ID: &str = "1521756469433991299";

const RETRY: Duration = Duration::from_secs(15);

/// The name after the verb, until the card is customised: the head of the card reads
/// "Listening to BitChord PC Continued", which is what the app is called.
const DEFAULT_NAME: &str = "BitChord PC Continued";

#[derive(Clone, Debug, PartialEq)]
pub struct Presence {
    pub video_id: String,
    pub title: String,
    pub artist: String,
    pub album: Option<String>,
    pub art: Option<String>,
    /// The audio quality mark beside the artist ("Dolby Atmos", "Lossless", …).
    pub quality: Option<String>,
    /// Unix ms when the song (virtually) started, so Discord draws elapsed time.
    pub start_ms: i64,
    pub end_ms: Option<i64>,
}

/// The verb Discord puts above the card ("Listening to …").
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum ActivityKind {
    Listening,
    Playing,
    Watching,
    Competing,
}

impl ActivityKind {
    pub fn label(self) -> &'static str {
        match self {
            Self::Listening => "Listening",
            Self::Playing => "Playing",
            Self::Watching => "Watching",
            Self::Competing => "Competing",
        }
    }

    fn parse(s: &str) -> Self {
        match s {
            "playing" => Self::Playing,
            "watching" => Self::Watching,
            "competing" => Self::Competing,
            _ => Self::Listening,
        }
    }

    fn key(self) -> &'static str {
        match self {
            Self::Listening => "listening",
            Self::Playing => "playing",
            Self::Watching => "watching",
            Self::Competing => "competing",
        }
    }

    fn activity(self) -> ActivityType {
        match self {
            Self::Listening => ActivityType::Listening,
            Self::Playing => ActivityType::Playing,
            Self::Watching => ActivityType::Watching,
            Self::Competing => ActivityType::Competing,
        }
    }
}

/// How the card is worded, as the desktop app's Discord settings. The defaults
/// reproduce the plain card: the app's name above it, the song on the bold line, the
/// artist under it, the quality beside the cover, and the two fixed buttons.
#[derive(Clone, Debug, PartialEq)]
pub struct Prefs {
    /// Which of the song and the artist Discord repeats beside your name in a channel.
    pub lead_with_song: bool,
    /// Let a measured premium format earn the cover's line.
    pub show_quality: bool,
    /// Reveal the wording fields in Settings.
    pub advanced: bool,
    pub activity: ActivityKind,
    /// What follows the verb; empty leaves it to Discord.
    pub name: String,
}

impl Default for Prefs {
    fn default() -> Self {
        Self { lead_with_song: true, show_quality: true, advanced: false, activity: ActivityKind::Listening, name: DEFAULT_NAME.to_owned() }
    }
}

impl Prefs {
    pub fn load(get: impl Fn(&str) -> Option<String>) -> Self {
        let flag = |k: &str, default: bool| get(k).map_or(default, |v| v == "1");
        Self {
            lead_with_song: flag("discord_lead_with_song", true),
            show_quality: flag("discord_show_quality", true),
            advanced: flag("discord_advanced", false),
            activity: ActivityKind::parse(&get("discord_activity").unwrap_or_default()),
            name: get("discord_name").filter(|s| !s.trim().is_empty()).unwrap_or_else(|| DEFAULT_NAME.to_owned()),
        }
    }

    pub fn save(&self, s: &mut dyn eframe::Storage) {
        let b = |v: bool| if v { "1" } else { "0" }.to_owned();
        s.set_string("discord_lead_with_song", b(self.lead_with_song));
        s.set_string("discord_show_quality", b(self.show_quality));
        s.set_string("discord_advanced", b(self.advanced));
        s.set_string("discord_activity", self.activity.key().to_owned());
        s.set_string("discord_name", self.name.clone());
    }

    /// The plain card again: the activity and its name back to their defaults, with the
    /// two switches above them untouched. Settings calls this when "Customize the card"
    /// is switched off.
    pub fn reset_card(&mut self) {
        let default = Self::default();
        self.advanced = false;
        self.activity = default.activity;
        self.name = default.name;
    }
}

const DEFAULT_BUTTON1: &str = "Listen on YouTube Music";
const DEFAULT_BUTTON2: &str = "Visit BitChord PC Continued";
const PROJECT_URL: &str = "https://github.com/jackychan4891/BitChord-PC-Continued";
/// The cover when a song has no artwork URL: the card always has one, as the old app's did.
const FALLBACK_ART: &str = "https://raw.githubusercontent.com/kushagrasinghx/BitChord/main/app/src/main/ic_launcher-playstore.png";

/// What the worker is told: a new presence, or new wording for the card.
enum Msg {
    Presence(Option<Presence>),
    Prefs(Prefs),
}

pub struct Discord {
    tx: Sender<Msg>,
}

impl Discord {
    pub fn spawn() -> Option<Self> {
        let id = std::env::var("YTM_DISCORD_ID").ok().filter(|s| !s.is_empty()).unwrap_or_else(|| CLIENT_ID.to_owned());
        let (tx, rx) = crossbeam_channel::unbounded();
        std::thread::Builder::new().name("discord".into()).spawn(move || run(&id, rx)).ok()?;
        Some(Self { tx })
    }

    /// `None` clears the status (paused / stopped).
    pub fn set(&self, p: Option<Presence>) {
        let _ = self.tx.send(Msg::Presence(p));
    }

    /// New wording for the card; re-sends the current presence with it.
    pub fn set_prefs(&self, p: Prefs) {
        let _ = self.tx.send(Msg::Prefs(p));
    }
}

fn run(id: &str, rx: Receiver<Msg>) {
    let mut client: Option<DiscordIpcClient> = None;
    let mut want: Option<Presence> = None;
    let mut prefs = Prefs::default();
    let mut dirty = false;
    let mut next_try = Instant::now();
    loop {
        match rx.recv_timeout(RETRY) {
            Ok(m) => {
                // Let a burst (skip, skip, seek) settle, then send only the last state.
                std::thread::sleep(Duration::from_millis(400));
                let mut apply = |m: Msg| match m {
                    Msg::Presence(p) => want = p,
                    Msg::Prefs(p) => prefs = p,
                };
                apply(m);
                while let Ok(m) = rx.try_recv() {
                    apply(m);
                }
                dirty = true;
            }
            Err(RecvTimeoutError::Timeout) => {}
            Err(RecvTimeoutError::Disconnected) => break,
        }
        if !dirty {
            continue;
        }
        if client.is_none() {
            if Instant::now() < next_try {
                continue;
            }
            let mut c = DiscordIpcClient::new(id);
            match c.connect() {
                Ok(()) => {
                    log::info!("discord: connected");
                    client = Some(c);
                }
                Err(_) => {
                    next_try = Instant::now() + RETRY;
                    continue;
                }
            }
        }
        let c = client.as_mut().expect("connected above");
        let res = match &want {
            Some(p) => c.set_activity(activity(p, &prefs)),
            None => c.clear_activity(),
        };
        // Read Discord's reply: it reports rejected payloads there, and leaving
        // replies unread would fill the pipe over a long session.
        let res = res.and_then(|()| c.recv());
        match res {
            Ok((_, reply)) => {
                if reply["evt"] == "ERROR" {
                    log::warn!("discord rejected activity: {}", reply["data"]);
                } else {
                    log::debug!("discord: activity set");
                }
                dirty = false;
            }
            Err(e) => {
                log::warn!("discord: {e}; reconnecting");
                let _ = c.close();
                client = None;
                next_try = Instant::now() + RETRY;
            }
        }
    }
    if let Some(mut c) = client {
        let _ = c.close();
    }
}

fn activity<'a>(p: &'a Presence, prefs: &Prefs) -> Activity<'a> {
    let mut ts = Timestamps::new().start(p.start_ms);
    if let Some(end) = p.end_ms {
        ts = ts.end(end);
    }
    // What the cover is labelled with: a measured premium format when the card is set to
    // show one, otherwise the album.
    let art_line = match (prefs.show_quality, p.quality.as_deref()) {
        (true, Some(quality)) => Some(quality.to_owned()),
        _ => p.album.clone(),
    };
    // The card always carries a cover, so a song with no artwork URL still reads whole.
    let art = p.art.as_deref().filter(|u| u.len() <= 256).unwrap_or(FALLBACK_ART);
    let mut assets = Assets::new().large_image(art);
    if let Some(line) = &art_line {
        assets = assets.large_text(field(line));
    }
    // The card reads the way the player does: the app's name above it, the song as the
    // bold line, the artist under that, the quality or album on the cover. `name` is the
    // app's own, so it comes from the settings field and never from the song.
    // Discord's own name for this application ID is not the app's, so the card always
    // carries ours: a blank field falls back to the default rather than to Discord's.
    let name = if prefs.name.trim().is_empty() { DEFAULT_NAME } else { prefs.name.trim() };
    let act = Activity::new()
        .activity_type(prefs.activity.activity())
        .name(field(name))
        .details(field(&p.title))
        .state(field(&p.artist))
        // Which of the two lines Discord repeats beside your name in a channel list.
        .status_display_type(if prefs.lead_with_song { StatusDisplayType::Details } else { StatusDisplayType::State })
        .timestamps(ts)
        .assets(assets);
    let url = format!("https://music.youtube.com/watch?v={}", p.video_id);
    // The old app's two buttons, always there and always the same: the song on YouTube
    // Music, and the project. Discord shows them to other people, never to you.
    act.buttons(vec![Button::new(DEFAULT_BUTTON1, url), Button::new(DEFAULT_BUTTON2, PROJECT_URL)])
}

/// Discord rejects text fields shorter than 2 or longer than 128 characters.
fn field(s: &str) -> String {
    let mut out: String = s.chars().take(128).collect();
    while out.chars().count() < 2 {
        out.push('\u{2800}');
    }
    out
}
