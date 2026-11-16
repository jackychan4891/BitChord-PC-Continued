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

/// The name the profile card shows, in place of the Discord app's registered name.
pub const APP_NAME: &str = "BitChord PC Continued";

const RETRY: Duration = Duration::from_secs(15);

#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum ActivityTypeChoice {
    Listening,
    Playing,
    Watching,
    Competing,
}

impl ActivityTypeChoice {
    pub const ALL: [Self; 4] = [
        Self::Listening,
        Self::Playing,
        Self::Watching,
        Self::Competing,
    ];

    pub fn label(&self) -> &'static str {
        match self {
            Self::Listening => "Listening",
            Self::Playing => "Playing",
            Self::Watching => "Watching",
            Self::Competing => "Competing",
        }
    }

    pub fn verb(&self) -> &'static str {
        match self {
            Self::Listening => "Listening to",
            Self::Playing => "Playing",
            Self::Watching => "Watching",
            Self::Competing => "Competing in",
        }
    }

    pub fn key(&self) -> &'static str {
        match self {
            Self::Listening => "listening",
            Self::Playing => "playing",
            Self::Watching => "watching",
            Self::Competing => "competing",
        }
    }

    pub fn from_key(key: &str) -> Self {
        match key {
            "playing" => Self::Playing,
            "watching" => Self::Watching,
            "competing" => Self::Competing,
            _ => Self::Listening,
        }
    }

    pub fn to_discord(&self) -> ActivityType {
        match self {
            Self::Listening => ActivityType::Listening,
            Self::Playing => ActivityType::Playing,
            Self::Watching => ActivityType::Watching,
            Self::Competing => ActivityType::Competing,
        }
    }
}

#[derive(Clone, Debug, PartialEq)]
pub struct Config {
    pub use_details: bool,
    pub show_audio_quality: bool,
    pub activity_type: ActivityTypeChoice,
    pub activity_name: String,
    pub button1_visible: bool,
    pub button1_text: String,
    pub button2_visible: bool,
    pub button2_text: String,
}

impl Default for Config {
    fn default() -> Self {
        Self {
            use_details: false,
            show_audio_quality: true,
            activity_type: ActivityTypeChoice::Listening,
            activity_name: String::new(),
            button1_visible: true,
            button1_text: "Listen on YouTube Music".to_owned(),
            button2_visible: true,
            button2_text: "BitChord".to_owned(),
        }
    }
}

#[derive(Clone, Debug, PartialEq)]
pub struct Presence {
    pub video_id: String,
    pub title: String,
    pub artist: String,
    pub album: Option<String>,
    pub art: Option<String>,
    pub audio_quality: Option<String>,
    /// Unix ms when the song (virtually) started, so Discord draws elapsed time.
    pub start_ms: i64,
    pub end_ms: Option<i64>,
}

#[derive(Clone, Debug, PartialEq)]
pub struct State {
    pub presence: Option<Presence>,
    pub config: Config,
}

pub struct Discord {
    tx: Sender<State>,
}

impl Discord {
    pub fn spawn() -> Option<Self> {
        let id = std::env::var("YTM_DISCORD_ID").ok().filter(|s| !s.is_empty()).unwrap_or_else(|| CLIENT_ID.to_owned());
        let (tx, rx) = crossbeam_channel::unbounded();
        std::thread::Builder::new().name("discord".into()).spawn(move || run(&id, rx)).ok()?;
        Some(Self { tx })
    }

    /// `presence: None` clears the status (paused / stopped).
    pub fn set(&self, presence: Option<Presence>, config: Config) {
        let _ = self.tx.send(State { presence, config });
    }

    pub fn clear(&self) {
        let _ = self.tx.send(State { presence: None, config: Config::default() });
    }
}

fn run(id: &str, rx: Receiver<State>) {
    let mut client: Option<DiscordIpcClient> = None;
    let mut want: Option<State> = None;
    let mut dirty = false;
    let mut next_try = Instant::now();
    loop {
        match rx.recv_timeout(RETRY) {
            Ok(s) => {
                // Let a burst (skip, skip, seek, typing) settle, then send only the last state.
                std::thread::sleep(Duration::from_millis(400));
                want = rx.try_iter().last().or(Some(s));
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
            Some(State { presence: Some(p), config }) => c.set_activity(activity(p, config)),
            _ => c.clear_activity(),
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

pub fn resolve_variables(template: &str, title: &str, artist: &str, album: Option<&str>) -> String {
    let mut s = template.to_owned();
    s = s.replace("{song_name}", title);
    s = s.replace("{artist_name}", artist);
    s = s.replace("{album_name}", album.unwrap_or(""));
    s
}

pub fn button_label(s: &str, fallback: &str) -> String {
    let trimmed = s.trim();
    let text = if trimmed.is_empty() { fallback } else { trimmed };
    let mut out: String = text.chars().take(32).collect();
    if out.is_empty() {
        out = fallback.chars().take(32).collect();
    }
    out
}

fn activity<'a>(p: &'a Presence, cfg: &'a Config) -> Activity<'a> {
    let mut ts = Timestamps::new().start(p.start_ms);
    if let Some(end) = p.end_ms {
        ts = ts.end(end);
    }
    let mut assets = Assets::new();
    if let Some(art) = p.art.as_deref().filter(|u| u.len() <= 256) {
        assets = assets.large_image(art);
        let quality = if cfg.show_audio_quality { p.audio_quality.as_deref() } else { None };
        let tooltip = match (&p.album, quality) {
            (Some(album), Some(q)) if !q.is_empty() => format!("{album} · {q}"),
            (Some(album), _) => album.clone(),
            (None, Some(q)) if !q.is_empty() => q.to_string(),
            (None, _) => String::new(),
        };
        if !tooltip.is_empty() {
            assets = assets.large_text(field(&tooltip));
        }
    }
    let url = format!("https://music.youtube.com/watch?v={}", p.video_id);
    let app_name = if cfg.activity_name.trim().is_empty() {
        APP_NAME
    } else {
        cfg.activity_name.trim()
    };

    let mut act = Activity::new()
        // What the profile card calls the app: the Discord app's own name is fixed by
        // the client ID, so the activity overrides it.
        .name(field(app_name))
        .activity_type(cfg.activity_type.to_discord())
        .status_display_type(if cfg.use_details {
            StatusDisplayType::Details
        } else {
            StatusDisplayType::State
        })
        .details(field(&p.title))
        .details_url(url.clone())
        .state(field(&p.artist))
        .timestamps(ts)
        .assets(assets);

    let mut buttons = Vec::new();
    if cfg.button1_visible {
        let text = resolve_variables(&cfg.button1_text, &p.title, &p.artist, p.album.as_deref());
        let label = button_label(&text, "Listen on YouTube Music");
        buttons.push(Button::new(label, url));
    }
    if cfg.button2_visible {
        let text = resolve_variables(&cfg.button2_text, &p.title, &p.artist, p.album.as_deref());
        let label = button_label(&text, "BitChord");
        buttons.push(Button::new(label, "https://github.com/jackychan4891/BitChord-PC-Continued"));
    }
    if !buttons.is_empty() {
        act = act.buttons(buttons);
    }
    act
}

/// Discord rejects text fields shorter than 2 or longer than 128 characters.
pub fn field(s: &str) -> String {
    let mut out: String = s.chars().take(128).collect();
    while out.chars().count() < 2 {
        out.push('\u{2800}');
    }
    out
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn variable_resolution() {
        let tmpl = "Playing {song_name} by {artist_name} from {album_name}";
        let res = resolve_variables(tmpl, "Get Lucky", "Daft Punk", Some("Random Access Memories"));
        assert_eq!(res, "Playing Get Lucky by Daft Punk from Random Access Memories");

        let tmpl_no_album = "Listen to {song_name}";
        let res2 = resolve_variables(tmpl_no_album, "Song A", "Artist B", None);
        assert_eq!(res2, "Listen to Song A");
    }

    #[test]
    fn button_label_formatting() {
        assert_eq!(button_label("   ", "Fallback"), "Fallback");
        assert_eq!(button_label("Normal Label", "Fallback"), "Normal Label");
        let long_str = "A".repeat(50);
        assert_eq!(button_label(&long_str, "Fallback").len(), 32);
    }

    #[test]
    fn discord_field_padding_and_truncation() {
        assert_eq!(field(""), "\u{2800}\u{2800}");
        assert_eq!(field("A"), "A\u{2800}");
        assert_eq!(field("Valid"), "Valid");
        let long_field = "B".repeat(200);
        assert_eq!(field(&long_field).len(), 128);
    }

    #[test]
    fn activity_type_choice_keys() {
        assert_eq!(ActivityTypeChoice::from_key("listening"), ActivityTypeChoice::Listening);
        assert_eq!(ActivityTypeChoice::from_key("playing"), ActivityTypeChoice::Playing);
        assert_eq!(ActivityTypeChoice::from_key("watching"), ActivityTypeChoice::Watching);
        assert_eq!(ActivityTypeChoice::from_key("competing"), ActivityTypeChoice::Competing);
        assert_eq!(ActivityTypeChoice::from_key("unknown"), ActivityTypeChoice::Listening);
    }

    #[test]
    fn build_activity_with_buttons_and_quality() {
        let p = Presence {
            video_id: "dQw4w9WgXcQ".into(),
            title: "Song A".into(),
            artist: "Rick Astley".into(),
            album: Some("Whenever You Need Somebody".into()),
            art: Some("https://example.com/art.jpg".into()),
            audio_quality: Some("FLAC · 24-bit · 96 kHz".into()),
            start_ms: 1000,
            end_ms: Some(215000),
        };
        let cfg = Config {
            use_details: true,
            show_audio_quality: true,
            activity_type: ActivityTypeChoice::Listening,
            activity_name: "My BitChord".into(),
            button1_visible: true,
            button1_text: "Listen to {song_name}".into(),
            button2_visible: false,
            button2_text: "".into(),
        };
        let act = activity(&p, &cfg);
        // serialize to check json representation
        let json = serde_json::to_value(&act).unwrap();
        assert_eq!(json["name"], "My BitChord");
        assert_eq!(json["details"], "Song A");
        assert_eq!(json["state"], "Rick Astley");
        assert_eq!(json["type"], 2); // Listening
        assert_eq!(json["status_display_type"], 2); // Details
        assert_eq!(json["assets"]["large_text"], "Whenever You Need Somebody · FLAC · 24-bit · 96 kHz");
        let buttons = json["buttons"].as_array().unwrap();
        assert_eq!(buttons.len(), 1);
        assert_eq!(buttons[0]["label"], "Listen to Song A");
    }
}
