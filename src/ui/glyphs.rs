//! BitChord's own icon set, drawn from `BitChordIcons`' path data.
//!
//! The paths come from the desktop app's shared icon file and are baked to PNG at
//! 256px (Chromium's own rasteriser, so the arcs and the two-contour outlines are
//! exactly as Compose fills them), then uploaded once per process with mipmaps.
//! Every glyph keeps its published viewport, so drawing it at the size the desktop
//! app's `Icon(…, Modifier.size(n.dp))` uses gives the same optical weight.

use egui::{Color32, ColorImage, Pos2, Rect, TextureHandle, TextureOptions, Ui, pos2, vec2};

pub const SHUFFLE: &[u8] = include_bytes!("../../assets/icon/glyphs/shuffle.png");
pub const PREVIOUS: &[u8] = include_bytes!("../../assets/icon/glyphs/prev.png");
pub const PLAY: &[u8] = include_bytes!("../../assets/icon/glyphs/play.png");
pub const PAUSE: &[u8] = include_bytes!("../../assets/icon/glyphs/pause.png");
pub const NEXT: &[u8] = include_bytes!("../../assets/icon/glyphs/next.png");
pub const REPEAT: &[u8] = include_bytes!("../../assets/icon/glyphs/repeat.png");
pub const REPEAT_ONE: &[u8] = include_bytes!("../../assets/icon/glyphs/repeat_one.png");
pub const STAR: &[u8] = include_bytes!("../../assets/icon/glyphs/star.png");
pub const STAR_FILLED: &[u8] = include_bytes!("../../assets/icon/glyphs/star_filled.png");
pub const LYRICS: &[u8] = include_bytes!("../../assets/icon/glyphs/lyrics.png");
pub const LYRICS_OFF: &[u8] = include_bytes!("../../assets/icon/glyphs/lyrics_off.png");
pub const QUEUE: &[u8] = include_bytes!("../../assets/icon/glyphs/queue.png");
pub const MAXIMIZE_2: &[u8] = include_bytes!("../../assets/icon/glyphs/maximize2.png");
pub const VOLUME: [&[u8]; 4] = [
    include_bytes!("../../assets/icon/glyphs/volume_0.png"),
    include_bytes!("../../assets/icon/glyphs/volume_1.png"),
    include_bytes!("../../assets/icon/glyphs/volume_2.png"),
    include_bytes!("../../assets/icon/glyphs/volume_3.png"),
];

/// The sidebar's navigation marks: Apple's own silhouettes for the three library
/// sections, and the same Lucide pen the desktop app uses for everything else.
pub const HOME: &[u8] = include_bytes!("../../assets/icon/glyphs/home.png");
pub const EXPLORE: &[u8] = include_bytes!("../../assets/icon/glyphs/explore.png");
pub const LIBRARY: &[u8] = include_bytes!("../../assets/icon/glyphs/library.png");
pub const SEARCH: &[u8] = include_bytes!("../../assets/icon/glyphs/search.png");
pub const DOWNLOAD: &[u8] = include_bytes!("../../assets/icon/glyphs/download.png");
pub const USERS: &[u8] = include_bytes!("../../assets/icon/glyphs/users.png");
pub const SETTINGS: &[u8] = include_bytes!("../../assets/icon/glyphs/settings.png");
pub const PERSON_CIRCLE: &[u8] = include_bytes!("../../assets/icon/glyphs/person_circle.png");
pub const MENU: &[u8] = include_bytes!("../../assets/icon/glyphs/menu.png");
pub const SPARKLES: &[u8] = include_bytes!("../../assets/icon/glyphs/sparkles.png");

/// The size the desktop app draws an Apple silhouette at (`DesktopAppleGlyph`).
pub const APPLE_GLYPH: f32 = 24.0;
/// The size it draws a Lucide mark at (`DesktopLucideGlyph`).
pub const LUCIDE_GLYPH: f32 = 18.0;

fn texture(ui: &Ui, glyph: &'static [u8]) -> Option<egui::TextureId> {
    let ctx = ui.ctx();
    // Each const has its own address, so it doubles as the cache key.
    let id = egui::Id::new(("bc-glyph", glyph.as_ptr()));
    if let Some(t) = ctx.data(|d| d.get_temp::<TextureHandle>(id)) {
        return Some(t.id());
    }
    let img = image::load_from_memory(glyph).ok()?.into_rgba8();
    let color = ColorImage::from_rgba_unmultiplied([img.width() as usize, img.height() as usize], img.as_raw());
    let options = TextureOptions {
        magnification: egui::TextureFilter::Linear,
        minification: egui::TextureFilter::Linear,
        wrap_mode: egui::TextureWrapMode::ClampToEdge,
        mipmap_mode: Some(egui::TextureFilter::Linear),
    };
    let tex = ctx.load_texture("bc-glyph", color, options);
    ctx.data_mut(|d| d.insert_temp(id, tex.clone()));
    Some(tex.id())
}

/// Draws `glyph` centred on `center` in a `size` square, tinted `tint`.
pub fn draw(ui: &Ui, center: Pos2, size: f32, glyph: &'static [u8], tint: Color32) {
    if tint.a() == 0 {
        return;
    }
    let Some(id) = texture(ui, glyph) else { return };
    let rect = Rect::from_center_size(center, vec2(size, size));
    ui.painter().image(id, rect, Rect::from_min_max(pos2(0.0, 0.0), pos2(1.0, 1.0)), tint);
}

/// How many of the speaker's three arcs are lit at `volume`, as the desktop app counts them.
pub fn volume_step(volume: f32) -> usize {
    if volume <= 0.0 {
        0
    } else if volume < 1.0 / 3.0 {
        1
    } else if volume < 2.0 / 3.0 {
        2
    } else {
        3
    }
}
