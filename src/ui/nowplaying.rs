//! Now-playing overlay: big art + synced lyrics / up next, over the full-strength
//! animated background. The lyrics themselves are drawn by `karaoke` (a port of the
//! better-lyrics renderer); this adds their toolbar and the plain-text fallback.

use egui::{Color32, Rect, Sense, Ui, Vec2, pos2, vec2};

use super::widgets::{self, icon_button, named, text_at};
use super::{Action, App, NpTab, Repeat, karaoke};
use crate::lyrics::{Lyrics, Provider, Sync, SyncKind};
use crate::shader::Params;
use crate::theme::{self, icon};

pub fn overlay(app: &mut App, ctx: &egui::Context, now: f64) {
    use crate::preset::BgMode;
    let t = theme::anim_bool(ctx, "np-open", app.np_open && app.current().is_some(), 0.42);
    if t <= 0.001 {
        return;
    }
    let screen = ctx.content_rect();
    // The player brings its own transport, so it takes the whole window: the capsule
    // under it belongs to the pages it covers.
    let full = screen;
    let rect = full.translate(vec2(0.0, full.height() * (1.0 - t)));
    let look = app.look();
    let b = &look.background;
    egui::Area::new(egui::Id::unique("now-playing"))
        .order(egui::Order::Middle)
        .fixed_pos(rect.min)
        .show(ctx, |ui| {
            // Swallow input so the page underneath doesn't react.
            ui.allocate_rect(rect, Sense::click_and_drag());
            // It covers the window's top too: that still moves the window.
            if !app.fullscreen {
                super::titlebar::drag_area(ui, Rect::from_min_size(rect.min, vec2(rect.width(), super::titlebar::HEIGHT)), "now-playing");
            }
            let painter = ui.painter_at(rect);
            let art = matches!(b.mode, BgMode::Kawarp | BgMode::CoverStatic);
            if b.mode == BgMode::CoverFullbleed {
                // The cover itself fills the screen behind a scrim. With the lyrics-only
                // layout on a wide window it fills the height on the left instead and
                // fades out toward the lyrics (reCover).
                let solid = look.solid();
                painter.rect_filled(rect, 0.0, solid);
                if let Some(track) = app.shown() {
                    let side_by_side = look.cover.layout == crate::preset::Layout::LyricsOnly && rect.width() >= 900.0;
                    let area = if side_by_side { Rect::from_min_size(rect.min, Vec2::splat(rect.height())) } else { rect };
                    let mut child = ui.new_child(egui::UiBuilder::new().max_rect(area));
                    child.set_clip_rect(area);
                    widgets::paint_cover_fill(&child, area, &track.thumbs, Color32::from_gray((b.np_dim.clamp(0.0, 1.0) * 255.0) as u8));
                    if side_by_side {
                        let fade = Rect::from_min_max(pos2(area.center().x - area.width() * 0.1, area.top()), area.max);
                        super::shell::hgradient(&painter, fade, Color32::TRANSPARENT, solid);
                    }
                }
            } else if let Some(bg) = app.bg.as_ref().filter(|_| art) {
                let blend = theme::ease_in_out(((now - app.art_changed) / 1.4) as f32);
                let level = if app.reactive_bg && app.playing() { app.audio.status.level() * b.reactive } else { 0.0 };
                bg.paint(
                    &painter,
                    rect,
                    Params {
                        time: app.bg_time as f32,
                        blend,
                        intensity: if b.mode == BgMode::CoverStatic { 0.0 } else { b.intensity },
                        saturation: b.saturation,
                        dim: b.np_dim,
                        scale: b.scale + level.min(0.5) * 0.12,
                    },
                );
            } else if b.mode == BgMode::Solid {
                painter.rect_filled(rect, 0.0, look.solid());
            } else {
                painter.rect_filled(rect, 0.0, theme::lerp_color(theme::bg(), app.accent_color(), 0.25));
            }
            let k = b.scrim / 0.3;
            super::shell::vgradient(ui.painter(), rect, theme::shade((0.18 * k).min(0.95)), theme::shade((0.42 * k).min(0.95)));
            let mut child = ui.new_child(egui::UiBuilder::new().max_rect(rect.shrink2(vec2(32.0, 20.0))));
            body(app, &mut child, now);
        });
}

fn body(app: &mut App, ui: &mut Ui, now: f64) {
    use crate::preset::{Layout, Side};
    // An Automix handoff keeps the outgoing song here until the next one is the louder
    // of the two (see `MixFrom`): its track, seek line and lyrics lag the queue's switch.
    let hold = app.mix_hold().map(|h| (h.total, h.mix, app.mix_hold_pos(h)));
    let Some(track) = app.current().cloned() else { return };
    let track = match &hold {
        Some(_) => app.shown().cloned().unwrap_or(track),
        None => track,
    };
    let heard = if hold.is_some() { track.clone() } else { app.playing_track().cloned().unwrap_or_else(|| track.clone()) };
    let look = app.look();
    let cs = &look.cover;
    let area = ui.max_rect();
    let no_lyrics = app.no_lyrics();
    // The content is this area less its margins: a cover column beside the panel needs
    // 860 points, and below that the cover gives way to the panel.
    let narrow = area.width() - 64.0 < 860.0;
    // Header: on the window buttons' line, when the app draws them.
    let lift = 20.0 - f32::from(super::titlebar::top_margin(20, 44.0));
    let header = Rect::from_min_size(area.min - vec2(0.0, if app.fullscreen { 0.0 } else { lift }), vec2(area.width(), 44.0));
    let mut hui = ui.new_child(egui::UiBuilder::new().max_rect(header).layout(egui::Layout::left_to_right(egui::Align::Center)));
    // The player's own close, top left; the window buttons keep the top right.
    if icon_button(&mut hui, icon::CHEVRON_DOWN, 22.0, "Close now playing", false).clicked() {
        app.np_open = false;
        if app.fullscreen {
            app.set_fullscreen(ui.ctx(), false);
        }
    }
    hui.with_layout(egui::Layout::right_to_left(egui::Align::Center), |ui| {
        ui.add_space(super::titlebar::right_gap(ui.ctx(), ui.max_rect(), 0.0));
        // Its actions (like, download, share…) are for what's heard: a song version
        // standing in for the music video. It lives beside the star under the cover;
        // only when the cover column is hidden (a narrow window showing lyrics) does
        // it stay up here.
        let cover_hidden = (narrow || cs.layout == Layout::LyricsOnly) && app.np_tab != NpTab::Art && !no_lyrics;
        if cover_hidden {
            let (r, _) = ui.allocate_exact_size(vec2(34.0, 34.0), Sense::hover());
            options_menu(app, ui, r, &heard, app.np_tab == NpTab::Lyrics && !no_lyrics);
        }
    });

    let content = Rect::from_min_max(pos2(area.left(), header.bottom() + 12.0), area.max);
    // The side panel (lyrics / up next) slides in and out when its chip is toggled.
    // A song without lyrics shows just the artwork, with no transition.
    let last_id = egui::Id::unique("np-last-panel");
    let panel_tab = if app.np_tab == NpTab::Art {
        ui.ctx().data(|d| d.get_temp(last_id)).unwrap_or(NpTab::Lyrics)
    } else {
        ui.ctx().data_mut(|d| d.insert_temp(last_id, app.np_tab));
        app.np_tab
    };
    // Linear 0..1 over the transition; each part eases its own slice of it.
    let shown = ui.ctx().animate_bool_with_time(egui::Id::unique("np-panel"), app.np_tab != NpTab::Art, 0.6 * theme::SPEED);
    if shown > 0.0 && shown < 1.0 {
        // Every frame while sliding, not the now-playing view's usual 30 fps.
        ui.ctx().request_repaint();
    }
    let ppp = ui.ctx().pixels_per_point();
    let p = if panel_tab == NpTab::Lyrics && no_lyrics { 0.0 } else { shown };
    // Whichever is leaving slides and fades out over the first 45%, then the other
    // comes in over the last 70%, both easing out, in either direction.
    let (panel_vis, art_vis) = if app.np_tab != NpTab::Art {
        (theme::ease_out((p - 0.3) / 0.7), 1.0 - theme::ease_out(p / 0.45))
    } else {
        let q = 1.0 - p;
        (1.0 - theme::ease_out(q / 0.45), theme::ease_out((q - 0.3) / 0.7))
    };
    // The cover gliding between its two places (wide layouts): a gentle S-curve.
    let glide = p * p * (3.0 - 2.0 * p);
    let lyrics_only = narrow || cs.layout == Layout::LyricsOnly;
    // reCover: the backdrop cover sits on the left, so the lyrics take the right side.
    let beside_cover = !narrow && cs.layout == Layout::LyricsOnly && look.background.mode == crate::preset::BgMode::CoverFullbleed;
    let centered = lyrics_only && !beside_cover;
    // Artwork alone: centered.
    let col_art = Rect::from_center_size(content.center(), vec2(content.width().min(content.height() * 0.9), content.height()));
    let slide = 72.0;
    let right = if lyrics_only {
        // No cover column next to the panel: the artwork fades in from the left as the panel leaves.
        let px = (art_side(cs, col_art) * ppp) as u32;
        if art_vis > 0.001 {
            let mut cui = ui.new_child(egui::UiBuilder::new().max_rect(content));
            cui.multiply_opacity(art_vis);
            cover_column(app, &mut cui, col_art.translate(vec2(-slide * (1.0 - art_vis), 0.0)), &track, no_lyrics, now, px);
        } else {
            // Hidden behind the lyrics: have it loaded for when they're turned off.
            widgets::preload_cover(ui.ctx(), &track.thumbs, px);
        }
        if beside_cover { Rect::from_min_max(pos2(content.left() + content.width() * 0.46, content.top()), content.max) } else { content }
    } else {
        // Cover column, on the preset's side; it glides to the center as the panel leaves.
        let frac = if cs.layout == Layout::CoverHero { 0.5 } else { 0.42 };
        let col_w = content.width() * frac;
        let col_panel = if cs.side == Side::Right {
            Rect::from_min_size(pos2(content.right() - col_w, content.top()), vec2(col_w, content.height()))
        } else {
            Rect::from_min_size(content.min, vec2(col_w, content.height()))
        };
        let col = Rect::from_min_max(col_art.min.lerp(col_panel.min, glide), col_art.max.lerp(col_panel.max, glide));
        // One image size for the whole glide, the bigger of its two ends.
        let px = (art_side(cs, col_art).max(art_side(cs, col_panel)) * ppp) as u32;
        cover_column(app, ui, col, &track, no_lyrics, now, px);
        if cs.side == Side::Right {
            Rect::from_min_max(content.min, pos2(col_panel.left() - 28.0, content.bottom()))
        } else {
            Rect::from_min_max(pos2(col_panel.right() + 28.0, content.top()), content.max)
        }
    };
    if panel_vis > 0.001 {
        let toward = if !lyrics_only && cs.side == Side::Right { -slide } else { slide };
        let rect = right.translate(vec2(toward * (1.0 - panel_vis), 0.0));
        // Switching between Lyrics and Up next: the old one fades out over the first 55%,
        // the new one fades in and rises into place from a third of the way.
        for (tab, key) in [(NpTab::Lyrics, "np-tab-lyrics"), (NpTab::UpNext, "np-tab-upnext")] {
            let w = ui.ctx().animate_bool_with_time(egui::Id::unique(key), panel_tab == tab, 0.45 * theme::SPEED);
            if w > 0.0 && w < 1.0 {
                ui.ctx().request_repaint();
            }
            let vis = if panel_tab == tab {
                theme::ease_out((w - 0.35) / 0.65)
            } else {
                let x = ((w - 0.45) / 0.55).clamp(0.0, 1.0);
                x * x * (3.0 - 2.0 * x)
            };
            if vis <= 0.001 || (tab == NpTab::Lyrics && no_lyrics) {
                continue;
            }
            let rise = if panel_tab == tab { 18.0 * (1.0 - vis) } else { 0.0 };
            let r = rect.translate(vec2(0.0, rise));
            let mut rui = ui.new_child(egui::UiBuilder::new().max_rect(r));
            rui.set_clip_rect(content.intersect(ui.clip_rect()));
            rui.multiply_opacity(panel_vis * vis);
            match tab {
                NpTab::Lyrics => lyrics_panel(app, &mut rui, r, now, centered),
                _ => super::shell::queue_list(app, &mut rui, "np"),
            }
        }
    }
}

/// Size of the artwork laid out in `col`: what is left once the sleeve's information,
/// its bar and the transport underneath it have taken their room.
fn art_side(cs: &crate::preset::CoverStyle, col: Rect) -> f32 {
    (col.width() * 0.82 * cs.size.clamp(0.5, 1.35)).min(col.height() - 190.0).max(160.0)
}

/// The sleeve: art, title and artists, the seek line with its two times, then the
/// transport — the order the reference keeps them in. `px`: the size to fetch the image
/// at (fixed while the column glides).
#[allow(clippy::too_many_arguments)]
fn cover_column(app: &mut App, ui: &mut Ui, col: Rect, track: &crate::model::Track, no_lyrics: bool, now: f64, px: u32) {
    use crate::preset::Shape;
    // A handoff's outgoing song is still what's heard: its star, menu and seek are its own.
    let hold = app.mix_hold().map(|h| (h.total, h.mix, app.mix_hold_pos(h)));
    let heard = if hold.is_some() { track.clone() } else { app.playing_track().cloned().unwrap_or_else(|| track.clone()) };
    let look = app.look();
    let cs = &look.cover;
    let side = art_side(cs, col);
    let art = Rect::from_center_size(pos2(col.center().x, col.top() + side / 2.0 + (col.height() - side - 190.0).max(0.0) * 0.42), Vec2::splat(side));
    let round = cs.shape != Shape::Rounded;
    let radius = if round { side / 2.0 } else { cs.radius.clamp(0.0, side / 2.0) };
    let cr = egui::CornerRadius::same(radius.min(255.0) as u8);
    if cs.glow > 0.0 {
        let s = egui::epaint::Shadow { offset: [0, 16], blur: 90, spread: 6, color: theme::with_alpha(app.accent_color(), cs.glow.min(1.0) * 0.7) };
        ui.painter().add(s.as_shape(art, cr));
    }
    if cs.shadow > 0.0 {
        widgets::soft_shadow(ui, art, radius.min(255.0), 60.0, cs.shadow.min(1.0));
    }
    let tint = Color32::from_gray((cs.brightness.clamp(0.1, 1.0) * 255.0) as u8);
    if round {
        if cs.shape == Shape::Record && app.playing() {
            let dt = ui.input(|i| i.stable_dt).min(0.1) as f64;
            app.record_angle = (app.record_angle + dt * std::f64::consts::TAU / cs.spin_secs.max(4.0) as f64) % std::f64::consts::TAU;
        }
        let angle = if cs.shape == Shape::Record { app.record_angle as f32 } else { 0.0 };
        widgets::paint_disc(ui, egui::Id::unique("np-art"), art.center(), side / 2.0, &track.thumbs, angle, tint, px);
        if cs.shape == Shape::Record {
            // Vinyl: faint grooves and a spindle hole.
            let p = ui.painter();
            for i in 1..6 {
                p.circle_stroke(art.center(), side / 2.0 * (0.42 + i as f32 * 0.1), egui::Stroke::new(1.0, theme::shade(0.18)));
            }
            p.circle_filled(art.center(), side * 0.09, theme::shade(0.35));
            p.circle_filled(art.center(), side * 0.022, Color32::from_rgb(10, 10, 12));
        }
    } else {
        widgets::paint_cover_xfade_px(ui, egui::Id::unique("np-art"), art, &track.thumbs, radius, 1.0, px);
        // Apple's motion sleeve, over the still until its first frame arrives.
        if let Some(album) = track.album.as_ref().map(|a| a.name.clone()).filter(|a| !a.is_empty()) {
            let artist = track.artists.first().map(|a| a.name.clone()).unwrap_or_else(|| track.artist_line());
            app.apple_cover(&album, &artist, Some(&track.title));
            if let Some(s) = app.apple_sprite(ui.ctx(), &album, &artist, px) {
                widgets::paint_motion(ui, art, radius, &s);
            }
        }
        if cs.brightness < 0.99 {
            ui.painter().rect_filled(art, cr, theme::shade(1.0 - cs.brightness));
        }
    }
    // Title and artists: 17 semibold over 15 secondary, left-aligned to the art.
    let w = side;
    let x0 = art.left();
    let info_y = art.bottom() + 22.0;
    // The star and the ⋯ menu share the information line's right end.
    let text_w = w - 76.0;
    text_at(ui, pos2(x0, info_y), &track.title, theme::semibold(17.0), Color32::WHITE, text_w);
    // Each artist opens their own page (features included); the album follows the
    // artist behind an em dash, as the reference writes the line.
    let mut line = track.artist_line();
    if let Some(album) = &track.album
        && !album.name.is_empty()
    {
        line = format!("{line} — {}", album.name);
    }
    let line_w = text_w;
    let line_rect = widgets::linked_text(ui, pos2(x0, info_y + 21.0), &line, &track.artists, theme::regular(15.0), theme::text_dim(), line_w, &mut app.actions);
    // The album after the em dash opens its page too, underlined like the artists.
    if let Some(album) = track.album.as_ref().filter(|a| !a.name.is_empty())
        && let Some(id) = album.id.clone()
    {
        let g = widgets::galley(ui, &line, theme::regular(15.0), theme::text_dim(), line_w);
        if let Some(at) = g.job.text.rfind(album.name.as_str()) {
            let c0 = g.job.text[..at].chars().count();
            let c1 = c0 + album.name.chars().count();
            let a0 = g.pos_from_cursor(egui::text::CCursor::new(c0)).min.x;
            let a1 = g.pos_from_cursor(egui::text::CCursor::new(c1)).min.x;
            if a1 - a0 >= 2.0 {
                let r = Rect::from_min_max(pos2(line_rect.left() + a0, line_rect.top()), pos2(line_rect.left() + a1, line_rect.bottom()));
                let resp = ui.interact(r, ui.scope_id().with(("album-link", &album.name)), Sense::click());
                let resp = named(resp, &album.name).on_hover_cursor(egui::CursorIcon::PointingHand);
                if resp.hovered() {
                    ui.painter().hline(r.x_range(), r.bottom() - 1.0, egui::Stroke::new(1.0, theme::text_dim()));
                }
                if resp.clicked() {
                    app.actions.push(Action::Open(super::Target::Album(id)));
                }
            }
        }
    }
    // The star: a 30 px disc that washes under the pointer and fills in the accent
    // once liked. The ⋯ menu sits at the line's end, as on music.youtube.com.
    let liked = app.liked.contains(&heard.id);
    let star = Rect::from_center_size(pos2(art.right() - 55.0, info_y + 14.0), Vec2::splat(30.0));
    let sr = ui.interact(star, ui.scope_id().with(("np-like", &heard.id)), Sense::click());
    let sr = named(sr, if liked { "Remove from favourites" } else { "Add to favourites" }).on_hover_cursor(egui::CursorIcon::PointingHand);
    let sh = theme::anim_bool(ui.ctx(), sr.id.with("h"), sr.hovered(), 0.1);
    let pop = theme::anim_bool(ui.ctx(), ("liked", &heard.id), liked, 0.3);
    // The same translucent disc as the ⋯ beside it, a little brighter once liked.
    let disc = (0.09 + 0.07 * sh + 0.05 * pop).min(0.30);
    if disc > 0.0 {
        ui.painter().circle_filled(star.center(), 15.0, theme::glass(disc));
    }
    let dim = 0.85 + 0.15 * sh;
    let tint = theme::lerp_color(theme::with_alpha(Color32::WHITE, dim), app.accent_color(), pop);
    super::glyphs::draw(ui, star.center(), 19.0, super::glyphs::STAR, tint);
    if pop > 0.0 {
        super::glyphs::draw(ui, star.center(), 19.0, super::glyphs::STAR_FILLED, theme::with_alpha(app.accent_color(), pop));
    }
    if sr.clicked() {
        app.actions.push(Action::Like(heard.clone(), !liked));
    }
    // The song's options, beside the star.
    let options = Rect::from_center_size(pos2(art.right() - 15.0, info_y + 14.0), Vec2::splat(34.0));
    options_menu(app, ui, options, &heard, app.np_tab == NpTab::Lyrics && !no_lyrics);

    // Seek: a 4 px line that grows under the pointer, its times below.
    let status = app.audio.status.clone();
    let held = hold.is_some();
    let (total, mix) = match &hold {
        Some((total, mix, _)) => (*total, *mix),
        None => {
            let total = status
                .duration()
                .map(|d| d.as_secs_f32())
                .or(track.duration.map(|d| d as f32))
                .unwrap_or(0.0);
            let next = app.queue.upcoming().is_some() || app.autoplay;
            let mix = match (total > 0.0, next, app.queue.repeat) {
                (true, true, repeat) if repeat != Repeat::One => {
                    status.mix_secs(status.duration().or_else(|| track.duration.map(|s| std::time::Duration::from_secs(s as u64)))).min(total * 0.5)
                }
                _ => 0.0,
            };
            (total, mix)
        }
    };
    let smooth = match &hold {
        Some((_, _, pos)) => *pos,
        None => status.position_smooth().as_secs_f32(),
    };
    let pos = app.seek_drag.unwrap_or(smooth).min(total.max(0.0));
    let hit = Rect::from_min_size(pos2(x0, info_y + 55.0), vec2(w, 16.0));
    // Held for the handoff's first half: its last stretch is where the pointer is,
    // and seeking the queue's song from here would cut the mix.
    let sresp = ui.interact(hit, ui.scope_id().with("np-seek"), if held { Sense::hover() } else { Sense::click_and_drag() });
    let sresp = if total > 0.0 && !held { sresp.on_hover_cursor(egui::CursorIcon::PointingHand) } else { sresp };
    sresp.widget_info(|| egui::WidgetInfo::slider(total > 0.0 && !held, pos as f64, "Seek"));
    let focus = theme::anim_bool(ui.ctx(), "np-seek-h", total > 0.0 && !held && (sresp.hovered() || sresp.dragged()), 0.18);
    let thick = 4.0 + 4.0 * focus;
    let bar = Rect::from_min_max(pos2(x0, hit.center().y - thick / 2.0), pos2(x0 + w, hit.center().y + thick / 2.0));
    ui.painter().rect_filled(bar, thick / 2.0, Color32::from_white_alpha(38));
    // Automix: the handoff window tints to the song's colour with its start ticked, so
    // the blend has a place it visibly begins at.
    if mix > 0.5 {
        let edge = x0 + w * ((total - mix) / total);
        let band = Rect::from_min_max(pos2(edge, bar.top()), bar.max);
        ui.painter().rect_filled(band, thick / 2.0, theme::with_alpha(app.accent_color(), 0.32));
        let tick = Rect::from_min_size(pos2(edge - 1.0, bar.top()), vec2(2.0, thick));
        ui.painter().rect_filled(tick, 1.0, theme::with_alpha(app.accent_color(), 0.9));
    }
    let frac = if total > 0.0 { (pos / total).clamp(0.0, 1.0) } else { 0.0 };
    if frac > 0.0 {
        let fill = theme::lerp_color(Color32::from_white_alpha(163), Color32::from_white_alpha(240), focus);
        ui.painter().rect_filled(Rect::from_min_size(bar.min, vec2(bar.width() * frac, thick)), thick / 2.0, fill);
    }
    let at = |p: egui::Pos2| (((p.x - x0) / w.max(1.0)).clamp(0.0, 1.0) * total).max(0.0);
    let tc = theme::with_alpha(Color32::WHITE, 0.62);
    let g = widgets::galley(ui, &super::fmt_time(pos as u32), theme::semibold(11.0), tc, 80.0);
    ui.painter().galley(pos2(x0, hit.bottom() + 2.0), g, tc);
    let left = super::fmt_time((total - pos).max(0.0) as u32);
    let g = widgets::galley(ui, &format!("-{left}"), theme::semibold(11.0), tc, 80.0);
    ui.painter().galley(pos2(x0 + w - g.size().x, hit.bottom() + 2.0), g, tc);
    // The quality mark sits between the two times, as the reference draws it.
    let badge_w = audio_badge_width(ui, app, false);
    if badge_w > 0.0 {
        audio_badge(app, ui, pos2(x0 + (w - badge_w) / 2.0, hit.bottom() + 1.0), false);
    }
    // While the two songs blend, its name sits between the two times.
    let mixed = theme::anim_bool(ui.ctx(), "np-automix-on", app.mixing(), 0.3);
    if mixed > 0.01 && badge_w == 0.0 {
        let accent = theme::with_alpha(app.accent_color(), mixed);
        let g = widgets::galley(ui, "Mixing", theme::semibold(10.5), accent, 80.0);
        ui.painter().galley(pos2(x0 + (w - g.size().x) / 2.0, hit.bottom() + 3.0), g, accent);
    }
    if total > 0.0
        && let Some(p) = sresp.interact_pointer_pos()
    {
        if sresp.dragged() {
            app.seek_drag = Some(at(p));
        }
        if sresp.drag_stopped() || sresp.clicked() {
            app.seek_drag = None;
            app.actions.push(Action::Seek(at(p)));
        }
    }

    // Transport: the reference's row — shuffle, previous, play, next, repeat — 52 tall,
    // no wider than 315, its buttons 36/48/56/48/36 with icons of 30/40/48/40/30.
    let ty = info_y + 99.0 + 26.0;
    let row_w = w.min(315.0);
    let sizes = [36.0f32, 48.0, 56.0, 48.0, 36.0];
    let icons = [30.0f32, 40.0, 48.0, 40.0, 30.0];
    let gap = ((row_w - sizes.iter().sum::<f32>()) / 4.0).max(4.0);
    let mut xs = [0.0f32; 5];
    let mut cx = art.center().x - row_w / 2.0;
    for (i, s) in sizes.iter().enumerate() {
        xs[i] = cx + s / 2.0;
        cx += s + gap;
    }
    // The marks are primary white, an off shuffle or repeat tertiary; a pressed button
    // shrinks to 90%, as the reference's does.
    let ink = |h: f32, base: f32, on: f32| theme::with_alpha(theme::lerp_color(theme::with_alpha(Color32::WHITE, base), Color32::WHITE, h), on);
    let enabled = app.current().is_some();
    let dim = if enabled { 1.0 } else { 0.4 };
    let btn = |ui: &mut Ui, i: usize, key: &str, name: &str, live: bool| -> (egui::Response, f32, f32) {
        let r = Rect::from_center_size(pos2(xs[i], ty), Vec2::splat(sizes[i]));
        let resp = ui.interact(r, ui.scope_id().with(("np-t", key)), if live { Sense::click() } else { Sense::hover() });
        let resp = named(resp, name);
        let resp = if live { resp.on_hover_cursor(egui::CursorIcon::PointingHand) } else { resp };
        let h = theme::anim_bool(ui.ctx(), resp.id.with("h"), live && resp.hovered(), 0.1);
        let p = theme::anim_bool(ui.ctx(), resp.id.with("p"), live && resp.is_pointer_button_down_on(), 0.1);
        if h > 0.0 {
            ui.painter().circle_filled(pos2(xs[i], ty), sizes[i] / 2.0 * (1.0 - 0.1 * p), Color32::from_white_alpha((0.1 * h * 255.0) as u8));
        }
        (resp, h, p)
    };
    let (r, h, p) = btn(ui, 0, "shuffle", "Shuffle up next", true);
    super::glyphs::draw(ui, r.rect.center(), icons[0] * (1.0 - 0.1 * p), super::glyphs::SHUFFLE, ink(h, 0.55, 1.0));
    if r.clicked() {
        app.shuffle_upcoming();
    }
    let (r, h, p) = btn(ui, 1, "prev", "Previous", enabled);
    super::glyphs::draw(ui, r.rect.center(), icons[1] * (1.0 - 0.1 * p), super::glyphs::PREVIOUS, ink(h, 1.0, dim));
    if r.clicked() && enabled {
        app.prev();
    }
    let (r, h, p) = btn(ui, 2, "play", if app.playing() || app.buffering { "Pause" } else { "Play" }, enabled);
    let c = r.rect.center();
    let size = icons[2] * (1.0 - 0.1 * p);
    let mark = ink(h, 1.0, dim);
    let morph = theme::anim_bool(ui.ctx(), "np-play-morph", app.playing() || app.buffering, 0.18);
    if app.buffering {
        let a = now as f32 * 6.0;
        let pts: Vec<egui::Pos2> = (0..16).map(|i| c + vec2((a + i as f32 * 0.24).cos(), (a + i as f32 * 0.24).sin()) * 11.0).collect();
        ui.painter().add(egui::Shape::Path(egui::epaint::PathShape::line(pts, egui::epaint::PathStroke::new(2.4, mark))));
        ui.ctx().request_repaint_after(std::time::Duration::from_millis(33));
    } else {
        // The two marks cross-fade: the reference keeps its pause up through a buffer,
        // and a hard cut between them flickers.
        if morph < 1.0 {
            super::glyphs::draw(ui, c, size, super::glyphs::PLAY, theme::with_alpha(mark, 1.0 - morph));
        }
        if morph > 0.0 {
            super::glyphs::draw(ui, c, size, super::glyphs::PAUSE, theme::with_alpha(mark, morph));
        }
    }
    if r.clicked() && enabled {
        app.toggle();
    }
    let (r, h, p) = btn(ui, 3, "next", "Next", enabled);
    super::glyphs::draw(ui, r.rect.center(), icons[3] * (1.0 - 0.1 * p), super::glyphs::NEXT, ink(h, 1.0, dim));
    if r.clicked() && enabled {
        app.next();
    }
    let (glyph, on) = match app.queue.repeat {
        Repeat::Off => (super::glyphs::REPEAT, false),
        Repeat::All => (super::glyphs::REPEAT, true),
        Repeat::One => (super::glyphs::REPEAT_ONE, true),
    };
    let (r, h, p) = btn(ui, 4, "repeat", "Repeat", true);
    super::glyphs::draw(ui, r.rect.center(), icons[4] * (1.0 - 0.1 * p), glyph, if on { app.accent_color() } else { ink(h, 0.55, 1.0) });
    if r.clicked() {
        app.queue.repeat = match app.queue.repeat {
            Repeat::Off => Repeat::All,
            Repeat::All => Repeat::One,
            Repeat::One => Repeat::Off,
        };
    }

    if no_lyrics && app.np_tab == NpTab::Lyrics {
        // A brief note, faded out after a few seconds.
        let since = ui.ctx().memory_mut(|m| *m.data.get_temp_mut_or_insert_with(egui::Id::unique(("no-lyrics", &track.id)), || now));
        let a = (1.0 - ((now - since - 3.0) / 1.0) as f32).clamp(0.0, 1.0);
        if a > 0.0 {
            let c = theme::with_alpha(theme::text_dim(), a * 0.8);
            text_at(ui, pos2(x0, ty + 40.0), "No lyrics for this song", theme::regular(14.0), c, w);
            // Frames only for the fade-out.
            let wait = (since + 3.0 - now).max(0.0);
            ui.ctx().request_repaint_after(std::time::Duration::from_secs_f64(if wait > 0.0 { wait } else { 0.033 }));
        }
    }
}

// ------------------------------------------------------------------ lyrics

fn lyrics_panel(app: &mut App, ui: &mut Ui, rect: Rect, now: f64, centered: bool) {
    // Lent out for the frame rather than cloned (the drawing needs `app` mutably).
    let Some((id, set)) = app.lyrics.take() else {
        ui.add_space(rect.height() * 0.35);
        ui.vertical_centered(|ui| {
            ui.spinner();
            ui.label(egui::RichText::new("Loading lyrics…").color(theme::text_dim()));
        });
        return;
    };
    if let Some(lyrics) = app.lyric_prefs.choose(&id, &set).and_then(|p| set.get(p)) {
        let panel = Rect::from_min_max(rect.min, pos2(rect.right(), rect.bottom() - 4.0));
        if lyrics.sync == Sync::None {
            plain_lyrics(app, ui, panel, lyrics);
        } else {
            synced(app, ui, panel, &id, lyrics, now, centered);
        }
    } else {
        ui.add_space(rect.height() * 0.4);
        ui.vertical_centered(|ui| {
            ui.label(egui::RichText::new("No lyrics found for this song").font(theme::semibold(20.0)).color(theme::text_dim()));
        });
    }
    app.lyrics = Some((id, set));
}

/// Romanization and translation for each line, the way the extension picks them: what
/// the lyrics ship (Apple-style TTML) first, then Unison/Google.
fn decorations<'a>(
    show_roman: bool,
    show_trans: bool,
    target: &'a str,
    extras: Option<&'a (String, String, String, crate::translate::Extras)>,
    id: &str,
    lyrics: &'a Lyrics,
) -> (Vec<Option<karaoke::Roman<'a>>>, Vec<Option<&'a str>>) {
    let extras = extras.filter(|(v, s, t, _)| v == id && s == lyrics.provider.key() && t == target).map(|(_, _, _, e)| e);
    let same = |a: &str, b: &str| a.trim().eq_ignore_ascii_case(b.trim());
    let roman = lyrics
        .lines
        .iter()
        .enumerate()
        .map(|(i, l)| {
            if !show_roman || l.instrumental {
                return None;
            }
            if let Some(r) = l.romanization.as_deref().filter(|r| !same(r, &l.text)) {
                return Some(if l.timed_roman.is_empty() { karaoke::Roman::Text(r) } else { karaoke::Roman::Timed(&l.timed_roman, r) });
            }
            let r = extras?.roman.get(i)?.as_deref()?;
            (!same(r, &l.text)).then_some(karaoke::Roman::Text(r))
        })
        .collect();
    let trans = lyrics
        .lines
        .iter()
        .enumerate()
        .map(|(i, l)| {
            if !show_trans || l.instrumental {
                return None;
            }
            let t = super::lyric_prefs::shipped_translation(l, target).or_else(|| extras?.trans.get(i)?.as_deref())?;
            (!same(t, &l.text)).then_some(t)
        })
        .collect();
    (roman, trans)
}

fn synced(app: &mut App, ui: &mut Ui, rect: Rect, id: &str, lyrics: &Lyrics, now: f64, narrow: bool) {
    let style = &app.look().lyrics;
    let accent = app.accent_color();
    let centered = narrow || style.align == crate::preset::Align::Center;
    let playing = if app.mix_hold().is_some() { app.audio.status.outgoing_playing() } else { app.playing() };
    // Audible position: the device plays what the player hands it a buffer later. A
    // handoff's first half is still the outgoing song's tail, which lines up with it.
    let pos = match app.mix_hold() {
        Some(h) => app.mix_hold_pos(h) as f64 - app.audio.status.latency().as_secs_f64(),
        None => app.audio.status.position_smooth().as_secs_f64() - app.audio.status.latency().as_secs_f64(),
    };
    let pos = app.lyric_clock.tick(pos, playing, now);
    let t = pos - app.lyric_prefs.offset(id, lyrics.provider, lyrics.sync == Sync::Word);
    let (roman, trans) = decorations(app.show_roman, app.show_trans, &app.lyric_prefs.target, app.lyrics_extras.as_ref(), id, lyrics);
    let key = {
        use std::hash::{Hash, Hasher};
        let mut h = std::collections::hash_map::DefaultHasher::new();
        id.hash(&mut h);
        lyrics.provider.key().hash(&mut h);
        app.theme_id.hash(&mut h);
        for r in &roman {
            match r {
                Some(karaoke::Roman::Timed(_, s)) | Some(karaoke::Roman::Text(s)) => s.hash(&mut h),
                None => 0u8.hash(&mut h),
            }
        }
        trans.hash(&mut h);
        h.finish()
    };
    let scale = app.lyric_prefs.text_scale;
    let frame = karaoke::Frame { lyrics, key, roman, trans, t, playing, now, rect, style, accent, centered, scale };
    // The view lives in `app`; the frame borrows `app` for decorations, so swap it out.
    let mut view = std::mem::take(&mut app.lyric_view);
    let out = if karaoke::dev_off("view") { karaoke::Out { seek: None, animating: true, user_scrolling: false } } else { view.show(ui, frame) };
    app.lyric_view = view;
    if let Some(s) = out.seek {
        app.actions.push(Action::Seek(s as f32));
    }
    if out.animating {
        ui.ctx().request_repaint();
    }
    // The extension's `.autoscroll-resume-button` (YouTube Music's rem is 10 px): a frosted
    // pill under the top edge in the lyrics font at 600, 1.75rem; padding .5rem 1rem;
    // white 5% fill, 1px white 10% border, radius 1rem, over a 5px backdrop blur (which
    // reads as about twice those on screen). It slides down from -3em to 2em as it fades
    // in, and its text glows on hover.
    let shown = theme::anim_bool(ui.ctx(), "lyr-resume", out.user_scrolling, 0.2);
    if shown > 0.0 {
        let font = theme::lyric_font(&style.font, crate::preset::Weight::Semibold, 17.5);
        let g = widgets::galley(ui, "Resume Autoscroll", font, Color32::WHITE, rect.width() - 40.0);
        let size = g.size() + vec2(20.0, 10.0) + Vec2::splat(2.0);
        let em = 17.5;
        let y = rect.top() + 8.0 + egui::lerp(-3.0 * em..=0.5 * em, theme::ease_out(shown));
        let r = Rect::from_min_size(pos2(rect.center().x - size.x / 2.0, y), size);
        let resp = ui.interact(r, ui.scope_id().with("lyr-resume-btn"), if out.user_scrolling { Sense::click() } else { Sense::hover() });
        let resp = named(resp, "Resume Autoscroll").on_hover_cursor(egui::CursorIcon::PointingHand);
        let hover = theme::anim_bool(ui.ctx(), "lyr-resume-h", resp.hovered() && out.user_scrolling, 0.25);
        let p = ui.painter().with_clip_rect(rect.expand2(vec2(0.0, 4.0)));
        p.rect_filled(r, 10.0, theme::with_alpha(Color32::WHITE, 0.10 * shown));
        p.rect_stroke(r, 10.0, egui::Stroke::new(1.0, theme::with_alpha(Color32::WHITE, 0.2 * shown)), egui::StrokeKind::Inside);
        let at = r.center() - g.size() / 2.0;
        if hover > 0.0
            && let Some((tex, glow_rect, uv)) = app.lyric_view.text_glow(ui.ctx(), g.clone(), at, 15.0)
        {
            // `text-shadow: 0 0 1.5rem var(--blyrics-glow-color)`: a real blur of the letters.
            let glow = crate::preset::paint(&style.glow_color, accent);
            p.image(tex, glow_rect, uv, theme::with_alpha(glow, hover * shown));
        }
        p.galley_with_override_text_color(at, g, theme::with_alpha(Color32::WHITE, shown));
        if shown < 1.0 {
            ui.ctx().request_repaint();
        }
        if resp.clicked() {
            app.lyric_view.resume();
        }
    }
}

fn plain_lyrics(app: &mut App, ui: &mut Ui, rect: Rect, lyrics: &Lyrics) {
    let mut child = ui.new_child(egui::UiBuilder::new().max_rect(rect));
    egui::ScrollArea::vertical().id_salt("plain-lyrics").auto_shrink(false).show(&mut child, |ui| {
        ui.label(egui::RichText::new("These lyrics aren't synced").font(theme::regular(13.0)).color(theme::text_faint()));
        ui.add_space(14.0);
        let style = &app.look().lyrics;
        let size = (32.0 * style.size.clamp(0.6, 1.6)).min(rect.width() / 12.5).max(18.0);
        for l in &lyrics.lines {
            let font = theme::lyric_font_for(&style.font, style.weight, size, &l.text);
            let color = theme::with_alpha(Color32::WHITE, 0.85);
            if crate::bidi::has_rtl(&l.text) {
                // Wrapped in reading order, each row in display order; RTL lines start on the right.
                let width = ui.available_width();
                let g = super::widgets::galley_wrapped(ui, &l.text, font, color, width, usize::MAX);
                let (r, _) = ui.allocate_exact_size(vec2(width, g.size().y), Sense::hover());
                let x = if crate::bidi::rtl_base(&l.text) { r.right() - g.size().x } else { r.left() };
                ui.painter().galley(pos2(x, r.top()), g, color);
            } else {
                ui.label(egui::RichText::new(&l.text).font(font).color(color));
            }
            ui.add_space(size * 0.25);
        }
        ui.add_space(80.0);
    });
}

/// Sync-type colours from the extension's dock (syllable, word, line, unsynced).
fn sync_color(k: SyncKind) -> Color32 {
    match k {
        SyncKind::Syllable => Color32::from_rgb(196, 160, 255),
        SyncKind::Word => Color32::from_rgb(130, 200, 255),
        SyncKind::Line => Color32::from_rgb(255, 205, 110),
        SyncKind::Unsynced => Color32::from_rgb(170, 170, 170),
    }
}

fn sync_label(k: SyncKind) -> &'static str {
    match k {
        SyncKind::Syllable => "Syllable synced",
        SyncKind::Word => "Word synced",
        SyncKind::Line => "Line synced",
        SyncKind::Unsynced => "Not synced",
    }
}

/// The ⋯ menu: the song's actions, and with lyrics showing, the source picker,
/// translation and romanization toggles, and this song's timing offset above them.
fn options_menu(app: &mut App, ui: &mut Ui, rect: Rect, track: &crate::model::Track, lyrics: bool) {
    let resp = ui.interact(rect, ui.scope_id().with("np-options"), Sense::click());
    let resp = named(resp, "Options").on_hover_cursor(egui::CursorIcon::PointingHand);
    let open = egui::Popup::is_id_open(ui.ctx(), egui::Popup::default_response_id(&resp));
    let h = theme::anim_bool(ui.ctx(), resp.id.with("h"), resp.hovered() || open, 0.12);
    ui.painter().circle_filled(rect.center(), 17.0, theme::glass(0.09 + 0.07 * h));
    for dx in [-6.0, 0.0, 6.0] {
        ui.painter().circle_filled(rect.center() + vec2(dx, 0.0), 1.9, theme::with_alpha(Color32::WHITE, 0.75 + 0.25 * h));
    }
    egui::Popup::menu(&resp).close_behavior(egui::PopupCloseBehavior::CloseOnClickOutside).show(|ui| {
        ui.set_width(270.0);
        ui.spacing_mut().item_spacing = vec2(0.0, 0.0);
        ui.add_space(4.0);
        // Lyrics first: the source, translation and timing are what the menu is for
        // while the lyrics are up.
        let liked = app.liked.contains(&track.id);
        if lyrics {
            lyric_options(app, ui);
            menu_divider(ui);
        }
        widgets::song_actions(ui, track, liked, None, &mut app.actions);
        menu_divider(ui);
        if widgets::menu_action(ui, Some(icon::LIST_PLUS), "Play next") {
            app.actions.push(Action::PlayNext(track.clone()));
        }
        if widgets::menu_action(ui, Some(icon::LIST_MUSIC), "Add to queue") {
            app.actions.push(Action::Enqueue(track.clone()));
        }
        let queue = app.np_tab == NpTab::UpNext;
        if widgets::menu_action(ui, Some(icon::LIST_MUSIC), if queue { "View lyrics" } else { "View queue" }) {
            app.np_tab = if queue { NpTab::Lyrics } else { NpTab::UpNext };
        }
        if widgets::menu_action(ui, Some(icon::RADIO), "Start radio") {
            app.actions.push(Action::Radio(track.clone()));
        }
        // Quality toggle at the bottom: revert when hi-res is playing, upgrade when not.
        let hires_playing = app.audio_info.as_ref().is_some_and(|(_, i)| i.lossless() || i.atmos);
        if hires_playing || !app.hires_sources.is_empty() {
            menu_divider(ui);
            if hires_playing {
                if widgets::menu_action(ui, Some(icon::MUSIC), "Revert quality") {
                    app.actions.push(Action::RevertQuality);
                }
            } else if widgets::menu_action(ui, Some(icon::MUSIC), "Upgrade quality") {
                app.actions.push(Action::UpgradeQuality);
            }
        }
        ui.add_space(4.0);
    });
}

fn lyric_options(app: &mut App, ui: &mut Ui) {
    let Some((id, set)) = app.lyrics.as_ref() else { return };
    let id = id.clone();
    let available = app.lyric_prefs.available(set);
    let Some(shown) = app.lyric_prefs.choose(&id, set) else { return };
    let n = available.len();
    let mut pick = None;
    menu_caption(ui, if n > 1 { "LYRICS SOURCE" } else { "LYRICS SOURCE · ONLY ONE FOUND" });
    for p in &available {
        if source_row(ui, p.name(), p.sync(), *p == shown) {
            pick = Some(*p);
        }
    }
    menu_divider(ui);
    let tip = format!("Translation · {}", lang_name(&app.lyric_prefs.target));
    switch_row(ui, &tip, &mut app.show_trans);
    switch_row(ui, "Romanization", &mut app.show_roman);
    // This song's timing for the shown source: ‹ +0.0s ›
    let off = app.lyric_prefs.song_offset(&id, shown);
    let (row, _) = ui.allocate_exact_size(vec2(ui.available_width(), 40.0), Sense::hover());
    ui.painter().text(pos2(row.left() + 12.0, row.center().y), egui::Align2::LEFT_CENTER, "Timing", theme::regular(14.0), theme::text());
    let mut sub = ui.new_child(egui::UiBuilder::new().max_rect(row.shrink2(vec2(8.0, 0.0))).layout(egui::Layout::right_to_left(egui::Align::Center)));
    sub.spacing_mut().item_spacing.x = 2.0;
    if mini_icon(&mut sub, icon::CHEVRON_RIGHT, "Lyrics later (+0.1 s)", true).clicked() {
        app.lyric_prefs.set_song_offset(&id, shown, off + 0.1);
    }
    let label = format!("{off:+.1}s").replace("+0.0", "0.0");
    if mini_text(&mut sub, &label, false, "This song's lyrics timing. Double-click to reset.").double_clicked() {
        app.lyric_prefs.set_song_offset(&id, shown, 0.0);
    }
    if mini_icon(&mut sub, icon::CHEVRON_LEFT, "Lyrics earlier (−0.1 s)", true).clicked() {
        app.lyric_prefs.set_song_offset(&id, shown, off - 0.1);
    }
    if let Some(p) = pick.filter(|p| *p != shown) {
        app.lyric_prefs.pick(&id, set, p);
        log::info!("lyrics: showing {} ({}/{}) by choice", p.key(), available.iter().position(|x| *x == p).map_or(0, |i| i + 1), n);
        app.lyric_view.reset();
    }
}

fn menu_caption(ui: &mut Ui, text: &str) {
    let (r, _) = ui.allocate_exact_size(vec2(ui.available_width(), 26.0), Sense::hover());
    ui.painter().text(pos2(r.left() + 12.0, r.center().y + 2.0), egui::Align2::LEFT_CENTER, text, theme::semibold(11.0), theme::text_faint());
}

fn menu_divider(ui: &mut Ui) {
    ui.add_space(5.0);
    let (r, _) = ui.allocate_exact_size(vec2(ui.available_width(), 1.0), Sense::hover());
    ui.painter().rect_filled(r.shrink2(vec2(8.0, 0.0)), 0.0, theme::glass(0.08));
    ui.add_space(5.0);
}

// ------------------------------------------------------------------ audio quality

fn badge_font(small: bool) -> egui::FontId {
    theme::semibold(if small { 9.0 } else { 11.0 })
}

fn glyph_side(small: bool) -> f32 {
    if small { 11.0 } else { 13.0 }
}

/// Whether the mark leads with an icon: Apple's lossless wave, or Dolby's double-D.
fn has_quality_glyph(info: &crate::hires::Info) -> bool {
    info.atmos || info.lossless()
}

/// Apple's own marks, from its "Symbols used in Music" page.
fn quality_icon(atmos: bool) -> &'static [u8] {
    if atmos {
        include_bytes!("../../assets/quality/atmos.png")
    } else {
        include_bytes!("../../assets/quality/lossless.png")
    }
}

fn quality_icon_aspect(atmos: bool) -> f32 {
    if atmos { 78.0 / 60.0 } else { 86.0 / 60.0 }
}

/// The icon as white pixels with its own alpha, so it takes the badge's tint.
fn quality_icon_texture(ui: &Ui, atmos: bool) -> egui::TextureHandle {
    let ctx = ui.ctx();
    let id = egui::Id::new(("quality-icon", atmos));
    if let Some(tex) = ctx.data(|d| d.get_temp::<egui::TextureHandle>(id)) {
        return tex;
    }
    let rgba = image::load_from_memory(quality_icon(atmos)).expect("quality icon").to_rgba8();
    let mut img = egui::ColorImage::from_rgba_unmultiplied([rgba.width() as usize, rgba.height() as usize], rgba.as_raw());
    for p in &mut img.pixels {
        *p = Color32::from_white_alpha(p.a());
    }
    let tex = ctx.load_texture(if atmos { "apple-atmos" } else { "apple-lossless" }, img, egui::TextureOptions::LINEAR);
    ctx.data_mut(|d| d.insert_temp(id, tex.clone()));
    tex
}

fn paint_quality_icon(ui: &Ui, center: egui::Pos2, height: f32, atmos: bool, tint: Color32) {
    let w = height * quality_icon_aspect(atmos);
    let rect = Rect::from_center_size(center, vec2(w, height));
    let uv = Rect::from_min_max(egui::pos2(0.0, 0.0), egui::pos2(1.0, 1.0));
    ui.painter().image(quality_icon_texture(ui, atmos).id(), rect, uv, tint);
}

/// How wide the quality mark is, so a line can leave room for it.
pub(super) fn audio_badge_width(ui: &Ui, app: &App, small: bool) -> f32 {
    let Some((_, info)) = app.audio_info.as_ref() else { return 0.0 };
    let label = if app.mixing() { "Mixing" } else { info.badge() };
    let text = widgets::galley(ui, label, badge_font(small), Color32::WHITE, 200.0).size().x;
    if has_quality_glyph(info) { text + glyph_side(small) * quality_icon_aspect(info.atmos) + 5.0 } else { text }
}

/// The quality mark — a glyph and the stream's name, tinted with the theme — drawn at
/// `at`; a click opens its details. Returns how much room it took.
pub(super) fn audio_badge(app: &App, ui: &mut Ui, at: egui::Pos2, small: bool) -> f32 {
    let Some((_, info)) = app.audio_info.as_ref() else { return 0.0 };
    let font = badge_font(small);
    // A mix in progress wears its own name over the quality mark; the details keep the truth.
    let g = widgets::galley(ui, if app.mixing() { "Mixing" } else { info.badge() }, font, Color32::WHITE, 200.0);
    let gh = glyph_side(small);
    let glyph = has_quality_glyph(info);
    let w = audio_badge_width(ui, app, small);
    let h = g.size().y.max(if glyph { gh } else { 0.0 });
    let rect = Rect::from_min_size(at, vec2(w, h));
    let resp = ui.interact(rect, ui.scope_id().with("audio-badge"), Sense::click());
    let resp = named(resp, "Audio quality").on_hover_cursor(egui::CursorIcon::PointingHand);
    let open = egui::Popup::is_id_open(ui.ctx(), egui::Popup::default_response_id(&resp));
    let hover = theme::anim_bool(ui.ctx(), resp.id.with("h"), resp.hovered() || open, 0.12);
    // Lossless and Atmos wear the song's colour; anything lossy stays quiet.
    let (ink, mark) = if info.lossless() || info.atmos {
        let accent = app.accent_color();
        (theme::lerp_color(accent, Color32::WHITE, 0.10 + 0.30 * hover), theme::with_alpha(accent, 0.85 + 0.15 * hover))
    } else {
        (theme::with_alpha(theme::text_dim(), 0.85 + 0.15 * hover), theme::text_dim())
    };
    let mut x = at.x;
    if glyph {
        let icon_w = gh * quality_icon_aspect(info.atmos);
        paint_quality_icon(ui, pos2(x + icon_w / 2.0, at.y + h / 2.0), gh, info.atmos, mark);
        x += icon_w + 5.0;
    }
    ui.painter().galley(pos2(x, at.y + (h - g.size().y) / 2.0), g, ink);
    egui::Popup::menu(&resp).close_behavior(egui::PopupCloseBehavior::CloseOnClickOutside).show(|ui| audio_info_menu(ui, info));
    w
}

/// Just the quality glyph, centred on `center`, for the mini player's artist line.
pub(super) fn audio_badge_icon_width(app: &App, small: bool) -> f32 {
    let Some((_, info)) = app.audio_info.as_ref().filter(|(_, i)| has_quality_glyph(i)) else { return 0.0 };
    glyph_side(small) * quality_icon_aspect(info.atmos)
}

pub(super) fn audio_badge_icon(app: &App, ui: &mut Ui, center: egui::Pos2, small: bool) {
    let Some((_, info)) = app.audio_info.as_ref().filter(|(_, i)| has_quality_glyph(i)) else { return };
    let gh = glyph_side(small);
    let rect = Rect::from_center_size(center, vec2(gh * quality_icon_aspect(info.atmos), gh));
    let resp = ui.interact(rect, ui.scope_id().with("audio-badge"), Sense::click());
    let resp = named(resp, "Audio quality").on_hover_cursor(egui::CursorIcon::PointingHand);
    let open = egui::Popup::is_id_open(ui.ctx(), egui::Popup::default_response_id(&resp));
    let hover = theme::anim_bool(ui.ctx(), resp.id.with("h"), resp.hovered() || open, 0.12);
    let accent = app.accent_color();
    let mark = theme::with_alpha(accent, 0.85 + 0.15 * hover);
    paint_quality_icon(ui, center, gh, info.atmos, mark);
    egui::Popup::menu(&resp).close_behavior(egui::PopupCloseBehavior::CloseOnClickOutside).show(|ui| audio_info_menu(ui, info));
}

/// What the stream is: its source, format and the numbers under those.
fn audio_info_menu(ui: &mut Ui, info: &crate::hires::Info) {
    ui.set_width(300.0);
    ui.spacing_mut().item_spacing = vec2(0.0, 0.0);
    ui.add_space(4.0);
    menu_caption(ui, "AUDIO QUALITY");
    let (r, _) = ui.allocate_exact_size(vec2(ui.available_width(), 34.0), Sense::hover());
    let g = widgets::galley(ui, info.badge(), theme::semibold(17.0), theme::text(), 200.0);
    let mut x = r.left() + 12.0;
    if has_quality_glyph(info) {
        let ih = 17.0;
        let icon_w = ih * quality_icon_aspect(info.atmos);
        paint_quality_icon(ui, pos2(x + icon_w / 2.0, r.center().y), ih, info.atmos, theme::text());
        x += icon_w + 8.0;
    }
    ui.painter().galley(pos2(x, r.center().y - g.size().y / 2.0), g.clone(), theme::text());
    menu_divider(ui);
    info_row(ui, "Source", if info.source.is_empty() { &info.host } else { &info.source });
    if !info.codec.is_empty() {
        info_row(ui, "Codec", &info.codec.to_ascii_uppercase());
    }
    if let Some(kbps) = bitrate(info) {
        info_row(ui, "Bitrate", &format!("{} kbps", thousands(kbps)));
    }
    if let Some(hz) = info.sample_rate {
        info_row(ui, "Sample rate", &rate_text(hz));
    }
    if let Some(bits) = info.bit_depth {
        info_row(ui, "Bit depth", &format!("{bits}-bit"));
    }
    if let Some(n) = info.channels {
        let text = match n {
            1 => "Mono".to_owned(),
            2 => "Stereo".to_owned(),
            _ => format!("{n} channels"),
        };
        info_row(ui, "Channels", &text);
    }
    if info.size > 0 {
        info_row(ui, "Size", &size_text(info.size));
    }
    if info.duration_ms > 0 {
        info_row(ui, "Duration", &super::fmt_time((info.duration_ms / 1000) as u32));
    }
    ui.add_space(6.0);
}

fn info_row(ui: &mut Ui, label: &str, value: &str) {
    let (r, _) = ui.allocate_exact_size(vec2(ui.available_width(), 30.0), Sense::hover());
    ui.painter().text(pos2(r.left() + 12.0, r.center().y), egui::Align2::LEFT_CENTER, label, theme::regular(13.0), theme::text_dim());
    let g = widgets::galley(ui, value, theme::semibold(13.0), theme::text(), 190.0);
    ui.painter().galley(pos2(r.right() - 12.0 - g.size().x, r.center().y - g.size().y / 2.0), g, theme::text());
}

/// The stated bitrate, or the one the file's size and length imply.
fn bitrate(info: &crate::hires::Info) -> Option<u32> {
    info.bitrate_kbps
        .or_else(|| (info.duration_ms > 0 && info.size > 0).then(|| (info.size * 8 / info.duration_ms) as u32))
        .filter(|k| *k > 0)
}

fn thousands(n: u32) -> String {
    let s = n.to_string();
    let mut out = String::with_capacity(s.len() + s.len() / 3);
    for (i, c) in s.chars().enumerate() {
        if i > 0 && (s.len() - i) % 3 == 0 {
            out.push(',');
        }
        out.push(c);
    }
    out
}

fn rate_text(hz: u32) -> String {
    if hz % 1000 == 0 { format!("{} kHz", hz / 1000) } else { format!("{:.1} kHz", hz as f64 / 1000.0) }
}

fn size_text(bytes: u64) -> String {
    if bytes >= 1024 * 1024 { format!("{:.1} MB", bytes as f64 / (1024.0 * 1024.0)) } else { format!("{:.0} KB", bytes as f64 / 1024.0) }
}

/// A lyrics source: its sync type's dot, name and sync type, with a check on the one shown.
fn source_row(ui: &mut Ui, name: &str, sync: SyncKind, current: bool) -> bool {
    let (rect, resp) = ui.allocate_exact_size(vec2(ui.available_width(), 44.0), Sense::click());
    let resp = named(resp, &format!("Lyrics source {name}")).on_hover_cursor(egui::CursorIcon::PointingHand);
    let h = theme::anim_bool(ui.ctx(), resp.id.with("h"), resp.hovered(), 0.12);
    if h > 0.0 || current {
        ui.painter().rect_filled(rect, 8.0, theme::glass(0.05 * current as u8 as f32 + 0.08 * h));
    }
    ui.painter().circle_filled(pos2(rect.left() + 19.0, rect.center().y), 3.5, sync_color(sync));
    let x = rect.left() + 40.0;
    text_at(ui, pos2(x, rect.top() + 6.0), name, theme::semibold(13.5), theme::text(), rect.right() - x - 34.0);
    text_at(ui, pos2(x, rect.top() + 24.0), sync_label(sync), theme::regular(12.0), theme::text_dim(), rect.right() - x - 34.0);
    if current {
        widgets::icon_at(ui, pos2(rect.right() - 18.0, rect.center().y), icon::CHECK, 16.0, theme::text());
    }
    resp.clicked()
}

/// A label with an on/off switch; the whole row toggles.
fn switch_row(ui: &mut Ui, label: &str, on: &mut bool) {
    let (rect, resp) = ui.allocate_exact_size(vec2(ui.available_width(), 40.0), Sense::click());
    let resp = named(resp, label).on_hover_cursor(egui::CursorIcon::PointingHand);
    if resp.clicked() {
        *on = !*on;
    }
    let h = theme::anim_bool(ui.ctx(), resp.id.with("h"), resp.hovered(), 0.12);
    if h > 0.0 {
        ui.painter().rect_filled(rect, 8.0, theme::glass(0.08 * h));
    }
    ui.painter().text(pos2(rect.left() + 12.0, rect.center().y), egui::Align2::LEFT_CENTER, label, theme::regular(14.0), theme::text());
    let t = theme::anim_bool(ui.ctx(), resp.id.with("t"), *on, 0.16);
    let track = Rect::from_center_size(pos2(rect.right() - 30.0, rect.center().y), vec2(36.0, 20.0));
    let accent = ui.ctx().data(|d| d.get_temp::<Color32>(egui::Id::unique("accent"))).unwrap_or(theme::RED);
    ui.painter().rect_filled(track, 10.0, theme::lerp_color(theme::glass(0.16 + 0.06 * h), accent, t));
    let knob = pos2(egui::lerp(track.left() + 10.0..=track.right() - 10.0, t), track.center().y);
    ui.painter().circle_filled(knob, 8.0, Color32::WHITE);
}

/// A small icon button (24 × 24).
fn mini_icon(ui: &mut Ui, glyph: char, tip: &str, enabled: bool) -> egui::Response {
    let (rect, resp) = ui.allocate_exact_size(vec2(22.0, 24.0), if enabled { Sense::click() } else { Sense::hover() });
    let resp = named(resp, tip).on_hover_text(tip);
    let resp = if enabled { resp.on_hover_cursor(egui::CursorIcon::PointingHand) } else { resp };
    let h = theme::anim_bool(ui.ctx(), resp.id.with("h"), resp.hovered() && enabled, 0.12);
    if h > 0.0 {
        ui.painter().rect_filled(rect, 7.0, theme::glass(0.12 * h));
    }
    let a = if enabled { 0.6 + 0.35 * h } else { 0.25 };
    widgets::icon_at(ui, rect.center(), glyph, 14.0, theme::with_alpha(Color32::WHITE, a));
    resp
}

/// A small text toggle or label (24 px tall), highlighted when `on`.
fn mini_text(ui: &mut Ui, text: &str, on: bool, tip: &str) -> egui::Response {
    let font = theme::semibold(12.0);
    let g = ui.fonts_mut(|f| f.layout_no_wrap(text.to_owned(), font, Color32::WHITE));
    let (rect, resp) = ui.allocate_exact_size(vec2(g.size().x + 12.0, 24.0), Sense::click());
    let resp = named(resp, tip).on_hover_text(tip).on_hover_cursor(egui::CursorIcon::PointingHand);
    let h = theme::anim_bool(ui.ctx(), resp.id.with("h"), resp.hovered(), 0.12);
    let gold = Color32::from_rgb(255, 214, 120);
    let fill = if on { theme::with_alpha(Color32::from_rgb(255, 196, 64), 0.16 + 0.06 * h) } else { theme::glass(0.12 * h) };
    if fill.a() > 0 {
        ui.painter().rect_filled(rect, 7.0, fill);
    }
    let color = if on { gold } else { theme::with_alpha(Color32::WHITE, 0.6 + 0.35 * h) };
    ui.painter().galley_with_override_text_color(rect.center() - g.size() / 2.0, g, color);
    resp
}

fn lang_name(code: &str) -> &str {
    super::lyric_prefs::LANGUAGES.iter().find(|(c, _)| *c == code).map_or(code, |(_, n)| n)
}
