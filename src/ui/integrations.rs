//! Discord, Last.fm, the system media controls, the app icon, updates and the tray.

use std::sync::atomic::Ordering;
use std::time::Duration;

use crate::backend;

use super::{App, dev_var, notes, updatable};

impl App {
    pub fn discord_config(&self) -> crate::discord::Config {
        crate::discord::Config {
            use_details: self.discord_use_details,
            show_audio_quality: self.discord_show_audio_quality,
            activity_type: self.discord_activity_type,
            activity_name: self.discord_activity_name.clone(),
            button1_visible: self.discord_button1_visible,
            button1_text: self.discord_button1_text.clone(),
            button2_visible: self.discord_button2_visible,
            button2_text: self.discord_button2_text.clone(),
        }
    }

    pub(super) fn set_discord_enabled(&mut self, enabled: bool) {
        self.discord_enabled = enabled;
        if enabled {
            if self.discord.is_none() {
                self.discord = crate::discord::Discord::spawn();
            }
            self.discord_sent = None;
            self.discord_sent_config = None;
        } else {
            if let Some(discord) = self.discord.take() {
                discord.clear();
            }
            self.discord_sent = None;
            self.discord_sent_config = None;
        }
    }

    pub fn current_audio_quality(&self) -> Option<String> {
        let stats = self.stream_stats.as_ref().map(|(_, s)| s);
        let hires = self.audio_info.as_ref().map(|(_, i)| i);
        let mut parts = Vec::new();
        if let Some(h) = hires {
            if h.atmos {
                parts.push("◗◖ Dolby Atmos".to_string());
            } else if h.lossless() {
                let is_hires = stats.map_or(false, |s| s.sample_rate > 48_000 || s.bit_depth.unwrap_or(0) > 16)
                    || h.sample_rate.unwrap_or(0) > 48_000
                    || h.bit_depth.unwrap_or(0) > 16;
                if is_hires {
                    parts.push("Hi-Res Lossless".to_string());
                } else {
                    parts.push("Lossless".to_string());
                }
            }
        }
        if let Some(s) = stats {
            parts.push(s.codec.clone());
            if let Some(bits) = s.bit_depth {
                parts.push(format!("{bits}-bit"));
            }
            let khz = s.sample_rate as f32 / 1000.0;
            if (khz.fract()).abs() < f32::EPSILON {
                parts.push(format!("{:.0} kHz", khz));
            } else {
                parts.push(format!("{:.1} kHz", khz));
            }
            if let Some(kbps) = s.bitrate_kbps {
                parts.push(format!("{} kbps", kbps));
            }
            match s.channels {
                1 => parts.push("Mono".to_string()),
                2 => parts.push("Stereo".to_string()),
                n => parts.push(format!("{n} ch")),
            }
        } else if let Some(h) = hires {
            if !h.codec.is_empty() {
                parts.push(h.codec.clone());
            }
            if let Some(bits) = h.bit_depth {
                parts.push(format!("{bits}-bit"));
            }
            if let Some(rate) = h.sample_rate {
                let khz = rate as f32 / 1000.0;
                if (khz.fract()).abs() < f32::EPSILON {
                    parts.push(format!("{:.0} kHz", khz));
                } else {
                    parts.push(format!("{:.1} kHz", khz));
                }
            }
            if let Some(kbps) = h.bitrate_kbps {
                parts.push(format!("{} kbps", kbps));
            }
        }
        if parts.is_empty() {
            None
        } else {
            Some(parts.join(" · "))
        }
    }

    /// Mirror playback into Discord. Cheap enough to run every frame: it only sends on a
    /// new song, play/pause, a seek, or a newly learned duration.
    pub(super) fn sync_discord(&mut self) {
        let Some(d) = &self.discord else { return };
        let track = self.playing_track().filter(|_| self.playing() && !self.buffering);
        let now = std::time::SystemTime::now().duration_since(std::time::UNIX_EPOCH).unwrap_or_default().as_millis() as i64;
        let start_ms = now - self.audio.status.position().as_millis() as i64;
        let dur = self.audio.status.duration_ms.load(Ordering::Relaxed) as i64;
        let cfg = self.discord_config();
        let audio_quality = if cfg.show_audio_quality { self.current_audio_quality() } else { None };

        let same_track = match (track, &self.discord_sent) {
            (Some(t), Some(b)) => {
                b.video_id == t.id
                    && (start_ms - b.start_ms).abs() < 2000
                    && b.end_ms.is_some() == (dur > 0)
                    && b.audio_quality == audio_quality
            }
            (None, None) => true,
            _ => false,
        };

        if same_track && self.discord_sent_config.as_ref() == Some(&cfg) {
            return;
        }

        let want = track.map(|t| crate::discord::Presence {
            video_id: t.id.clone(),
            title: t.title.clone(),
            artist: t.artist_line(),
            album: t.album.as_ref().map(|a| a.name.clone()),
            art: backend::art_url(t),
            audio_quality,
            start_ms,
            end_ms: (dur > 0).then_some(start_ms + dur),
        });

        d.set(want.clone(), cfg.clone());
        self.discord_sent = want;
        self.discord_sent_config = Some(cfg);
    }

    /// Tell Last.fm what's playing, and scrobble it once it has played half its length
    /// or 4 minutes (songs under 30 s don't count). Runs every frame, like Discord's.
    pub(super) fn sync_lastfm(&mut self) {
        let result = self.lastfm_result.lock().unwrap().take();
        if let Some(r) = result {
            match r {
                Ok(name) => {
                    self.toast(format!("Scrobbling to Last.fm as {name}"));
                    self.lastfm_user = Some(name);
                    self.scrobble = None;
                }
                Err(e) => self.toast(format!("Last.fm sign-in failed: {e}")),
            }
        }
        if self.lastfm_user.is_none() {
            return;
        }
        let Some(t) = self.playing_track().filter(|_| self.playing() && !self.buffering) else { return };
        let pos = self.audio.status.position();
        let dur = Duration::from_millis(self.audio.status.duration_ms.load(Ordering::Relaxed));
        let now = std::time::SystemTime::now().duration_since(std::time::UNIX_EPOCH).unwrap_or_default().as_secs() as i64;
        let play = crate::lastfm::Scrobble {
            artist: t.artists.first().map(|a| a.name.clone()).unwrap_or_default(),
            track: t.title.clone(),
            album: t.album.as_ref().map(|a| a.name.clone()),
            duration: (!dur.is_zero()).then_some(dur.as_secs() as u32),
            timestamp: now - pos.as_secs() as i64,
        };
        if play.artist.is_empty() {
            return;
        }
        let http = self.backend.http();
        if self.scrobble.is_none_or(|(g, _)| g != self.generation) {
            self.scrobble = Some((self.generation, false));
            let p = play.clone();
            self.backend.spawn(async move { crate::lastfm::now_playing(&http, &p).await });
            return;
        }
        if dur >= Duration::from_secs(30) && pos >= (dur / 2).min(Duration::from_secs(240)) && self.scrobble.is_some_and(|(_, done)| !done) {
            self.scrobble = Some((self.generation, true));
            self.backend.spawn(async move { crate::lastfm::scrobble(&http, play).await });
        }
    }

    pub(super) fn lastfm_sign_in(&mut self) {
        if self.lastfm_busy.swap(true, Ordering::Relaxed) {
            return;
        }
        let (busy, result, http, backend) = (self.lastfm_busy.clone(), self.lastfm_result.clone(), self.backend.http(), self.backend.clone());
        self.backend.spawn(async move {
            let r = crate::lastfm::sign_in(&http).await.map_err(|e| format!("{e:#}"));
            *result.lock().unwrap() = Some(r);
            busy.store(false, Ordering::Relaxed);
            backend.wake();
        });
    }

    pub(super) fn lastfm_sign_out(&mut self) {
        crate::lastfm::sign_out();
        self.lastfm_user = None;
        self.scrobble = None;
    }

    /// Update checks: shortly after launch, then every six hours. A downloaded update is
    /// only offered as a notice; nothing installs until the user restarts for it.
    pub(super) fn tick_updates(&mut self, ctx: &egui::Context, now: f64) {
        use crate::update::{State, Updater};
        if self.updater.is_none() {
            let c = ctx.clone();
            self.updater = Some(Updater::new(self.backend.data_dir(), move || c.request_repaint()));
        }
        let Some(u) = self.updater.clone() else { return };
        let due = match self.update_checked {
            None => now > 8.0,
            Some(t) => now - t > 6.0 * 3600.0 && !matches!(u.state(), State::Ready { .. }),
        };
        let enabled = updatable() && dev_var("YTM_BACKGROUND").is_none_or(|_| dev_var("YTM_UPDATE_FORCE").is_some() || dev_var("YTM_UPDATE_FEED").is_some());
        if enabled && due {
            self.update_checked = Some(now);
            u.check();
        } else if enabled {
            // An idle app doesn't repaint on its own: wake up when the next check is due.
            let next = self.update_checked.map_or(8.0, |t| t + 6.0 * 3600.0);
            ctx.request_repaint_after(Duration::from_secs_f64((next - now).max(0.5) + 0.1));
        }
        if let State::Ready { version, .. } = u.state()
            && !self.update_announced
        {
            self.update_announced = true;
            self.notes_dialog = Some(notes::NotesDialog { version, ready: true });
        }
    }

    /// Close the app so the downloaded update installs, then relaunch it.
    pub(super) fn restart_to_update(&mut self, ctx: &egui::Context) {
        self.restart_for_update = true;
        self.quit(ctx);
    }

    /// Close and start again, e.g. so a new proxy setting takes effect.
    pub(super) fn restart(&mut self, ctx: &egui::Context) {
        self.restart_app = true;
        self.quit(ctx);
    }

    /// Close the app for real, even with "Close to tray" on.
    pub(super) fn quit(&mut self, ctx: &egui::Context) {
        self.exiting = true;
        ctx.send_viewport_cmd(egui::ViewportCommand::Close);
    }

    /// "Close to tray": × hides the window behind a tray icon; the icon brings it
    /// back or quits. Holding Shift while clicking × quits immediately.
    pub(super) fn tray_window(&mut self, ctx: &egui::Context) {
        let shift = ctx.input(|i| i.modifiers.shift);
        if ctx.input(|i| i.viewport().close_requested()) && shift && !self.exiting {
            self.quit(ctx);
            return;
        }
        if ctx.input(|i| i.viewport().close_requested()) && self.close_to_tray && !self.exiting {
            if self.tray.is_none() {
                let c = ctx.clone();
                match crate::tray::Tray::new(crate::appicon::get(self.app_icon), move || c.request_repaint()) {
                    Ok(t) => {
                        ctx.send_viewport_cmd(egui::ViewportCommand::CancelClose);
                        ctx.send_viewport_cmd(egui::ViewportCommand::Visible(false));
                        self.tray = Some(t);
                    }
                    // Without an icon there'd be no way back: just close.
                    Err(e) => log::warn!("tray icon unavailable: {e:#}"),
                }
            } else {
                ctx.send_viewport_cmd(egui::ViewportCommand::CancelClose);
                ctx.send_viewport_cmd(egui::ViewportCommand::Visible(false));
            }
        }
        if self.tray.is_none() {
            return;
        }
        // The first click decides; the icon goes away with it.
        match crate::tray::take().into_iter().next() {
            Some(crate::tray::Cmd::Show) => self.show_window(ctx),
            Some(crate::tray::Cmd::Quit) => {
                self.tray = None;
                self.quit(ctx);
            }
            None => {}
        }
    }

    /// Bring the window back (from the tray, or minimized) and to the front.
    pub(super) fn show_window(&mut self, ctx: &egui::Context) {
        self.tray = None;
        ctx.send_viewport_cmd(egui::ViewportCommand::Visible(true));
        ctx.send_viewport_cmd(egui::ViewportCommand::Minimized(false));
        ctx.send_viewport_cmd(egui::ViewportCommand::Focus);
    }

    /// Download the media controls' cover through our own client, into a file.
    pub(super) fn fetch_media_cover(&self, video_id: String, url: String) {
        let (slot, b) = (self.media_cover.clone(), self.backend.clone());
        let dir = self.backend.data_dir().join("media-cover");
        self.backend.spawn(async move {
            let res = async {
                let bytes = b.http().get(&url).send().await?.error_for_status()?.bytes().await?;
                // A new name per song, so Windows doesn't show a cached older cover.
                let _ = std::fs::remove_dir_all(&dir);
                std::fs::create_dir_all(&dir)?;
                let path = dir.join(format!("{video_id}.jpg"));
                std::fs::write(&path, &bytes)?;
                anyhow::Ok(path)
            }
            .await;
            match res {
                Ok(path) => {
                    *slot.lock().unwrap() = Some((video_id, format!("file://{}", path.display())));
                    b.wake();
                }
                Err(e) => log::debug!("media cover: {e:#}"),
            }
        });
    }
}
