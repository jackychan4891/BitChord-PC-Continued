//! The keys that do things: what each one is bound to out of the box, how a binding is
//! kept between runs, and how Settings changes one.
//!
//! A binding is a key plus the modifiers that have to be held with it, so `M` mutes and
//! `⌘ M` (or `Ctrl M`) opens the mini player without the two getting in each other's way.
//! The `command` modifier is ⌘ on a Mac and Ctrl everywhere else, which is what lets the
//! same defaults read correctly on either keyboard.

use std::collections::HashMap;

use egui::{Context, Event, Key, Modifiers};

const MAC: bool = cfg!(target_os = "macos");

/// One thing a key or combination can do. A new command needs a row in `INFOS`, a case in
/// [`crate::ui::actions::App::shortcuts`], or a widget that asks [`hit`].
#[derive(Clone, Copy, Debug, PartialEq, Eq, Hash)]
pub enum Cmd {
    PlayPause,
    Next,
    Previous,
    VolumeUp,
    VolumeDown,
    Mute,
    Search,
    Lyrics,
    Queue,
    Fullscreen,
    Back,
    Forward,
    Close,
    MiniPlayer,
}

impl Cmd {
    pub const ALL: &'static [Self] = &[
        Self::PlayPause,
        Self::Next,
        Self::Previous,
        Self::VolumeUp,
        Self::VolumeDown,
        Self::Mute,
        Self::Search,
        Self::Lyrics,
        Self::Queue,
        Self::Fullscreen,
        Self::Back,
        Self::Forward,
        Self::Close,
        Self::MiniPlayer,
    ];

    pub fn info(self) -> &'static Info {
        INFOS.iter().find(|i| i.cmd == self).expect("every command has a row in INFOS")
    }
}

/// Where a press counts: anywhere, not while a box is taking letters, and not at all while
/// a text box has the keyboard.
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum Guard {
    Anywhere,
    NotTyping,
    NotEditing,
}

impl Guard {
    fn allows(self, typing: bool, editing: bool) -> bool {
        match self {
            Self::Anywhere => true,
            Self::NotTyping => !typing,
            Self::NotEditing => !editing,
        }
    }
}

/// A key and the modifiers that have to be held with it.
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub struct Combo {
    pub key: Key,
    pub command: bool,
    pub alt: bool,
    pub shift: bool,
}

/// A command as the settings page and the save file know it.
pub struct Info {
    pub cmd: Cmd,
    pub label: &'static str,
    /// What a fresh install has, and what the row's reset goes back to.
    pub default: Combo,
    pub guard: Guard,
    /// Held down: does it keep firing (the volume) or start once (everything else)?
    pub repeat: bool,
}

impl Info {
    /// The name it's saved under.
    pub fn id(&self) -> &'static str {
        match self.cmd {
            Cmd::PlayPause => "play_pause",
            Cmd::Next => "next",
            Cmd::Previous => "previous",
            Cmd::VolumeUp => "volume_up",
            Cmd::VolumeDown => "volume_down",
            Cmd::Mute => "mute",
            Cmd::Search => "search",
            Cmd::Lyrics => "lyrics",
            Cmd::Queue => "queue",
            Cmd::Fullscreen => "fullscreen",
            Cmd::Back => "back",
            Cmd::Forward => "forward",
            Cmd::Close => "close",
            Cmd::MiniPlayer => "mini_player",
        }
    }
}

const fn combo(key: Key, command: bool, alt: bool, shift: bool) -> Combo {
    Combo { key, command, alt, shift }
}

pub const INFOS: &[Info] = &[
    Info { cmd: Cmd::PlayPause, label: "Play / pause", default: combo(Key::Space, false, false, false), guard: Guard::NotTyping, repeat: false },
    Info { cmd: Cmd::Next, label: "Next song", default: combo(Key::ArrowRight, true, false, false), guard: Guard::NotEditing, repeat: false },
    Info { cmd: Cmd::Previous, label: "Previous song", default: combo(Key::ArrowLeft, true, false, false), guard: Guard::NotEditing, repeat: false },
    Info { cmd: Cmd::VolumeUp, label: "Volume up", default: combo(Key::ArrowUp, true, false, false), guard: Guard::NotEditing, repeat: true },
    Info { cmd: Cmd::VolumeDown, label: "Volume down", default: combo(Key::ArrowDown, true, false, false), guard: Guard::NotEditing, repeat: true },
    Info { cmd: Cmd::Mute, label: "Mute", default: combo(Key::M, false, false, false), guard: Guard::NotTyping, repeat: false },
    Info { cmd: Cmd::Search, label: "Search", default: combo(Key::F, true, false, false), guard: Guard::Anywhere, repeat: false },
    Info { cmd: Cmd::Lyrics, label: "Lyrics", default: combo(Key::L, false, false, false), guard: Guard::NotTyping, repeat: false },
    Info { cmd: Cmd::Queue, label: "Up next", default: combo(Key::Q, false, false, false), guard: Guard::NotTyping, repeat: false },
    Info { cmd: Cmd::Fullscreen, label: "Full-screen lyrics (in now playing)", default: combo(Key::F, false, false, false), guard: Guard::NotTyping, repeat: false },
    Info { cmd: Cmd::Back, label: "Back", default: combo(Key::ArrowLeft, false, true, false), guard: Guard::Anywhere, repeat: false },
    Info { cmd: Cmd::Forward, label: "Forward", default: combo(Key::ArrowRight, false, true, false), guard: Guard::Anywhere, repeat: false },
    Info { cmd: Cmd::Close, label: "Close now playing", default: combo(Key::Escape, false, false, false), guard: Guard::Anywhere, repeat: false },
    Info { cmd: Cmd::MiniPlayer, label: "Mini player", default: combo(Key::M, true, false, false), guard: Guard::Anywhere, repeat: false },
];

impl Combo {
    /// Only a key, no modifiers.
    const fn key(key: Key) -> Self {
        Combo { key, command: false, alt: false, shift: false }
    }

    /// Held with exactly these modifiers.
    fn held(self, m: &Modifiers) -> bool {
        let command = if MAC { m.command } else { m.ctrl };
        command == self.command && m.alt == self.alt && m.shift == self.shift
    }

    /// `command+alt+left` — what the save file holds, and what [`Combo::parse`] reads back.
    pub fn code(self) -> String {
        let mut parts: Vec<String> = Vec::new();
        if self.command {
            parts.push("command".into());
        }
        if self.alt {
            parts.push("alt".into());
        }
        if self.shift {
            parts.push("shift".into());
        }
        parts.push(self.key.name().to_lowercase());
        parts.join("+")
    }

    /// A saved binding, if it names a key we can bind at all.
    pub fn parse(s: &str) -> Option<Self> {
        let parts: Vec<&str> = s.split('+').map(str::trim).filter(|p| !p.is_empty()).collect();
        let (key, mods) = parts.split_last()?;
        let mut out = Self::key(from_name(key)?);
        for m in mods {
            match m.to_ascii_lowercase().as_str() {
                "command" | "cmd" | "ctrl" | "control" | "meta" | "super" => out.command = true,
                "alt" | "option" => out.alt = true,
                "shift" => out.shift = true,
                _ => return None,
            }
        }
        Some(out)
    }

    /// The keycaps Settings draws: ⌘ then ⇧ then the key on a Mac, Ctrl, Alt, Shift elsewhere.
    pub fn caps(&self) -> Vec<String> {
        let mut out = Vec::new();
        if self.command {
            out.push(if MAC { "⌘" } else { "Ctrl" }.into());
        }
        if self.alt {
            out.push(if MAC { "⌥" } else { "Alt" }.into());
        }
        if self.shift {
            out.push(if MAC { "⇧" } else { "Shift" }.into());
        }
        out.push(name_of(self.key));
        out
    }
}

fn from_name(s: &str) -> Option<Key> {
    Key::ALL.iter().copied().find(|k| k.name().eq_ignore_ascii_case(s))
}

fn name_of(key: Key) -> String {
    match key {
        Key::ArrowLeft => "←".into(),
        Key::ArrowRight => "→".into(),
        Key::ArrowUp => "↑".into(),
        Key::ArrowDown => "↓".into(),
        Key::Escape => "Esc".into(),
        k => k.name().into(),
    }
}

/// What the user changed, with the factory bindings underneath it.
#[derive(Clone, Debug, Default)]
pub struct Map {
    over: HashMap<&'static str, Combo>,
}

impl Map {
    pub fn get(&self, cmd: Cmd) -> Combo {
        self.over.get(cmd.info().id()).copied().unwrap_or(cmd.info().default)
    }

    pub fn set(&mut self, cmd: Cmd, combo: Combo) {
        self.over.insert(cmd.info().id(), combo);
    }

    pub fn reset(&mut self, cmd: Cmd) {
        self.over.remove(cmd.info().id());
    }

    pub fn is_default(&self, cmd: Cmd) -> bool {
        self.get(cmd) == cmd.info().default
    }

    /// A different command already bound to this combination.
    pub fn clash(&self, cmd: Cmd, combo: Combo) -> Option<Cmd> {
        Cmd::ALL.iter().copied().filter(|other| *other != cmd).find(|other| self.get(*other) == combo)
    }

    /// Only what the user changed; an untouched keyboard saves as `{}`.
    pub fn json(&self) -> String {
        let out: HashMap<&str, String> = self.over.iter().map(|(id, c)| (*id, c.code())).collect();
        serde_json::to_string(&out).unwrap_or_default()
    }

    pub fn load(s: Option<String>) -> Map {
        let mut map = Map::default();
        let Some(saved) = s.and_then(|s| serde_json::from_str::<HashMap<String, String>>(&s).ok()) else {
            return map;
        };
        for (id, code) in saved {
            if let Some(info) = INFOS.iter().find(|i| i.id() == id)
                && let Some(combo) = Combo::parse(&code)
            {
                map.over.insert(info.id(), combo);
            }
        }
        map
    }
}

/// The commands whose key went down since the last frame, in binding order.
pub fn pressed(ctx: &Context, map: &Map) -> Vec<Cmd> {
    let typing = ctx.egui_wants_keyboard_input();
    let editing = ctx.text_edit_focused();
    let mut out: Vec<Cmd> = Vec::new();
    ctx.input(|i| {
        for event in &i.events {
            let Event::Key { key, pressed: true, repeat, modifiers, .. } = event else { continue };
            for info in INFOS {
                let combo = map.get(info.cmd);
                if combo.key != *key || !combo.held(modifiers) || !info.guard.allows(typing, editing) {
                    continue;
                }
                if *repeat && !info.repeat {
                    continue;
                }
                if !out.contains(&info.cmd) {
                    out.push(info.cmd);
                }
            }
        }
    });
    out
}

/// Did this command's key go down since the last frame? For a widget that handles its own
/// (the search box keeps its focus to itself).
pub fn hit(ctx: &Context, map: &Map, cmd: Cmd) -> bool {
    pressed(ctx, map).contains(&cmd)
}

/// What the settings page's recorder got this frame.
pub enum Typed {
    Nothing,
    /// Escape alone: give up on the row.
    Cancel,
    Combo(Combo),
}

/// A combination typed since the last frame, as the recorder wants it. A lone modifier
/// isn't a binding, so holding Ctrl and then F reads as `⌘/Ctrl + F`.
pub fn typed(ctx: &Context) -> Typed {
    ctx.input(|i| {
        for event in &i.events {
            let Event::Key { key, pressed: true, modifiers, .. } = event else { continue };
            if !Key::ALL.contains(key) {
                continue;
            }
            if *key == Key::Escape && !modifiers.command && !modifiers.ctrl && !modifiers.alt && !modifiers.shift {
                return Typed::Cancel;
            }
            return Typed::Combo(Combo {
                key: *key,
                command: if MAC { modifiers.command } else { modifiers.ctrl },
                alt: modifiers.alt,
                shift: modifiers.shift,
            });
        }
        Typed::Nothing
    })
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn every_command_has_a_unique_default() {
        for a in INFOS {
            for b in INFOS {
                if a.cmd != b.cmd {
                    assert_ne!(a.default, b.default, "{} and {} share a binding", a.id(), b.id());
                }
            }
        }
        assert_eq!(INFOS.len(), Cmd::ALL.len());
    }

    #[test]
    fn a_binding_survives_the_save_file() {
        for key in Key::ALL {
            let c = Combo { key: *key, command: true, alt: true, shift: true };
            assert_eq!(Combo::parse(&c.code()), Some(c), "{}", c.code());
        }
        assert_eq!(Combo::parse("space"), Some(Combo::key(Key::Space)));
        assert_eq!(Combo::parse("ctrl+shift+right"), Combo::parse("command+shift+right"));
        assert_eq!(Combo::parse("menu+fish"), None);
        assert_eq!(Combo::parse(""), None);
    }

    #[test]
    fn a_changed_binding_is_the_only_thing_saved() {
        let mut map = Map::default();
        assert!(map.is_default(Cmd::Mute));
        assert_eq!(Map::load(Some(map.json())).get(Cmd::Mute), Cmd::Mute.info().default);
        map.set(Cmd::Mute, Combo::key(Key::N));
        let back = Map::load(Some(map.json()));
        assert_eq!(back.get(Cmd::Mute), Combo::key(Key::N));
        assert_eq!(back.get(Cmd::Lyrics), Cmd::Lyrics.info().default);
        assert_eq!(back.clash(Cmd::Mute, Cmd::Lyrics.info().default), Some(Cmd::Lyrics));
        let mut cleared = back.clone();
        cleared.reset(Cmd::Mute);
        assert!(cleared.is_default(Cmd::Mute));
        assert_eq!(cleared.json(), "{}");
    }

    #[test]
    fn mute_and_the_mini_player_share_a_key_but_not_a_binding() {
        let map = Map::default();
        assert_ne!(map.get(Cmd::Mute), map.get(Cmd::MiniPlayer));
        assert!(map.get(Cmd::MiniPlayer).command);
        assert!(!map.get(Cmd::Mute).command);
    }
}
