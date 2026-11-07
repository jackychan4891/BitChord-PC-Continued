//! OS media integration (Windows SMTC / MPRIS / macOS Now Playing) via souvlaki,
//! plus the previous / play-pause / next buttons on the Windows taskbar thumbnail.

#[cfg(windows)]
mod thumbbar;

use std::sync::Arc;

use crossbeam_channel::Sender;
use souvlaki::{MediaControlEvent, MediaControls, MediaMetadata, MediaPlayback, PlatformConfig};

pub struct Media {
    controls: MediaControls,
    /// The play state last sent, so the per-frame sync only reports changes.
    playing: Option<bool>,
    #[cfg(windows)]
    thumbbar: Option<Box<std::cell::RefCell<Option<thumbbar::ThumbBar>>>>,
}

impl Media {
    pub fn new(
        hwnd: Option<*mut std::ffi::c_void>,
        tx: Sender<MediaControlEvent>,
        wake: impl Fn() + Send + Sync + 'static,
    ) -> Option<Self> {
        let wake: Arc<dyn Fn() + Send + Sync> = Arc::new(wake);
        let config = PlatformConfig { display_name: "YT Music", dbus_name: "ytm_rs", hwnd };
        let mut controls = MediaControls::new(config)
            .map_err(|e| log::warn!("media controls unavailable: {e:?}"))
            .ok()?;
        let (smtc_tx, smtc_wake) = (tx.clone(), wake.clone());
        controls
            .attach(move |ev| {
                let _ = smtc_tx.send(ev);
                smtc_wake();
            })
            .map_err(|e| log::warn!("media controls attach failed: {e:?}"))
            .ok()?;
        #[cfg(windows)]
        let thumbbar = hwnd.and_then(|h| thumbbar::ThumbBar::install(h, tx, wake));
        #[cfg(not(windows))]
        let _ = (tx, wake);
        Some(Self {
            controls,
            playing: None,
            #[cfg(windows)]
            thumbbar,
        })
    }

    pub fn set_track(&mut self, title: &str, artist: &str, album: Option<&str>, cover: Option<&str>, secs: Option<u32>) {
        let _ = self.controls.set_metadata(MediaMetadata {
            title: Some(title),
            artist: Some(artist),
            album,
            cover_url: cover,
            duration: secs.map(|s| std::time::Duration::from_secs(s as u64)),
        });
    }

    /// Also called every frame with the player's state, so pausing from anywhere (a
    /// headset, the Windows media flyout, a song ending) updates SMTC and the taskbar.
    pub fn set_playing(&mut self, playing: bool) {
        if self.playing == Some(playing) {
            return;
        }
        self.playing = Some(playing);
        let _ = self.controls.set_playback(if playing {
            MediaPlayback::Playing { progress: None }
        } else {
            MediaPlayback::Paused { progress: None }
        });
        #[cfg(windows)]
        if let Some(bar) = self.thumbbar.as_ref().and_then(|c| c.try_borrow_mut().ok()).as_mut().and_then(|b| b.as_mut()) {
            bar.set_playing(playing);
        }
    }
}
