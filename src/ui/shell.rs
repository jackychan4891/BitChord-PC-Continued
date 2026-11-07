//! App chrome: background, sidebar, top bar, player bar, queue drawer, toasts.

use egui::{Align2, Color32, Pos2, Rect, Sense, Ui, Vec2, pos2, vec2};

use super::widgets::{self, icon_at, icon_button, named, paint_cover, text_at};
use super::{Action, App, LibTab, NpTab, Page, Repeat, fmt_time, glyphs};
use crate::model::Target;
use crate::shader::Params;
use crate::theme::{self, icon};

// ------------------------------------------------------------------ background

/// Vertical gradient (top colour → bottom colour) over `rect`.
pub fn vgradient(painter: &egui::Painter, rect: Rect, top: Color32, bottom: Color32) {
    gradient(painter, rect, [top, top, bottom, bottom]);
}

/// Horizontal gradient (left colour → right colour) over `rect`.
pub fn hgradient(painter: &egui::Painter, rect: Rect, left: Color32, right: Color32) {
    gradient(painter, rect, [left, right, left, right]);
}

/// Corner colours: left top, right top, left bottom, right bottom.
fn gradient(painter: &egui::Painter, rect: Rect, c: [Color32; 4]) {
    let mut mesh = egui::Mesh::default();
    mesh.colored_vertex(rect.left_top(), c[0]);
    mesh.colored_vertex(rect.right_top(), c[1]);
    mesh.colored_vertex(rect.left_bottom(), c[2]);
    mesh.colored_vertex(rect.right_bottom(), c[3]);
    mesh.add_triangle(0, 1, 2);
    mesh.add_triangle(1, 3, 2);
    painter.add(egui::Shape::mesh(mesh));
}

/// The background mode browse pages use: the theme's own choice, else whatever now
/// playing uses.
fn page_mode(look: &crate::preset::Preset) -> crate::preset::BgMode {
    use crate::preset::{BgMode, PageBg};
    match look.app.page_bg {
        PageBg::Same => look.background.mode,
        PageBg::Static => BgMode::CoverStatic,
        PageBg::Accent => BgMode::AccentGradient,
        PageBg::Solid => BgMode::Solid,
    }
}

/// Whether the pages' background moves while music plays (static themes need no frames).
pub fn pages_bg_moves(look: &crate::preset::Preset) -> bool {
    use crate::preset::BgMode;
    let b = &look.background;
    matches!(page_mode(look), BgMode::Kawarp | BgMode::CoverFullbleed) && b.speed > 0.0 && b.intensity > 0.0
}

/// `only`: paint just this part (the rest is covered by now playing).
/// The background's flat colour, under its art and gradients.
pub fn base_color(app: &App) -> Color32 {
    app.look().solid()
}

pub fn background(app: &mut App, ui: &mut Ui, screen: Rect, now: f64, only: Option<Rect>) {
    use crate::preset::BgMode;
    let dt = (now - app.last_frame).clamp(0.0, 0.1);
    app.last_frame = now;
    let look = app.look();
    let b = &look.background;
    let mode = page_mode(look);
    // Light pages are the art at full strength under a white wash, a pale tint of the cover.
    let light = theme::light();
    let dim = if light { 1.0 } else { look.app.page_dim.unwrap_or(b.page_dim).clamp(0.02, 1.0) };
    // Now playing animates the shader itself, so keep the clock running for it too.
    let animated = matches!(b.mode, BgMode::Kawarp | BgMode::CoverFullbleed) && b.speed > 0.0;
    let moving = app.anim_bg && animated && app.playing();
    if moving {
        app.bg_time += dt * b.speed as f64;
    }
    let painter = match only {
        Some(r) => ui.painter().with_clip_rect(r),
        None => ui.painter().clone(),
    };
    painter.rect_filled(screen, 0.0, base_color(app));
    let art = matches!(mode, BgMode::Kawarp | BgMode::CoverStatic | BgMode::CoverFullbleed);
    if let Some(bg) = app.bg.as_ref().filter(|_| art) {
        let blend = theme::ease_in_out(((now - app.art_changed) / 1.4) as f32);
        if blend < 1.0 {
            // The crossfade to new art finishes even while paused.
            ui.ctx().request_repaint_after(std::time::Duration::from_millis(33));
        }
        let level = if app.reactive_bg && app.playing() { app.audio.status.level() * b.reactive } else { 0.0 };
        let still = mode == BgMode::CoverStatic;
        bg.paint(
            &painter,
            screen,
            Params {
                time: app.bg_time as f32,
                blend,
                intensity: if still { 0.0 } else { b.intensity * 0.9 },
                saturation: b.saturation * 0.9,
                dim,
                scale: (b.scale - 0.08).max(1.0) + if still { 0.0 } else { level.min(0.5) * 0.08 },
            },
        );
    } else if mode == BgMode::Solid {
        // Plain colour: nothing over it.
    } else {
        vgradient(&painter, screen, theme::with_alpha(app.accent_color(), 0.18 * dim / 0.5), look.solid());
    }
    if light {
        painter.rect_filled(screen, 0.0, Color32::from_white_alpha(212));
    } else if mode != BgMode::Solid {
        // Keep text readable: darken toward the bottom and under the sidebar.
        vgradient(&painter, screen, theme::shade(0.12), theme::shade(0.55));
    }
}

// ------------------------------------------------------------------ sidebar

/// How the theme draws nav items.
#[derive(Clone, Copy)]
struct NavLook {
    style: crate::preset::NavStyle,
    radius: f32,
    accent: Color32,
}

impl NavLook {
    fn of(app: &App) -> Self {
        Self { style: app.look().app.nav, radius: (app.look().chrome.radius * 0.6).clamp(4.0, 16.0), accent: app.accent_color() }
    }
}

/// A nav item's background in the theme's nav style; returns its icon colour.
/// `h` and `s` are the hover and selected animations (0..1).
fn nav_bg(ui: &Ui, look: NavLook, rect: Rect, h: f32, s: f32) -> Color32 {
    use crate::preset::NavStyle;
    let (nav_r, accent) = (look.radius, look.accent);
    let icon_fg = theme::lerp_color(theme::text_dim(), theme::text(), (h + s).min(1.0));
    match look.style {
        NavStyle::Pill => {
            ui.painter().rect_filled(rect, nav_r, theme::glass(0.05 * h + 0.10 * s));
            icon_fg
        }
        NavStyle::Accent => {
            ui.painter().rect_filled(rect, nav_r, theme::glass(0.05 * h));
            ui.painter().rect_filled(rect, nav_r, theme::with_alpha(accent, 0.22 * s));
            theme::lerp_color(icon_fg, accent, s)
        }
        NavStyle::Bar => {
            ui.painter().rect_filled(rect, nav_r, theme::glass(0.05 * h));
            let bar = Rect::from_center_size(pos2(rect.left() + 2.0, rect.center().y), vec2(3.0, 20.0 * s));
            ui.painter().rect_filled(bar, 2.0, accent);
            icon_fg
        }
    }
}

/// Sidebar width expanded and collapsed to an icon rail (points).
pub const SIDEBAR_W: f32 = 236.0;
pub const RAIL_W: f32 = 76.0;

/// How collapsed the sidebar is, 0 (full) to 1 (icon rail), eased over the toggle.
pub fn sidebar_collapse(app: &App, ctx: &egui::Context) -> f32 {
    let t = ctx.animate_bool_with_time(egui::Id::unique("sidebar-collapse"), app.sidebar_collapsed, 0.28 * theme::SPEED);
    // Ease in-out: starts and lands gently in both directions.
    t * t * (3.0 - 2.0 * t)
}

/// The small chevron pair that walks the page history, on the logo's line at the rail's
/// top right.
fn history_button(ui: &mut Ui, glyph: char, name: &str) -> bool {
    let (rect, resp) = ui.allocate_exact_size(Vec2::splat(22.0), Sense::click());
    let resp = named(resp, name).on_hover_text(name).on_hover_cursor(egui::CursorIcon::PointingHand);
    let h = theme::anim_bool(ui.ctx(), resp.id.with("h"), resp.hovered(), 0.15);
    if h > 0.0 {
        ui.painter().circle_filled(rect.center(), 11.0, theme::glass(0.10 * h));
    }
    icon_at(ui, rect.center(), glyph, 16.0, theme::lerp_color(theme::text_dim(), theme::text(), h));
    resp.clicked()
}

/// One layout for both states, so collapsing animates: the panel narrows while icons
/// and covers keep their column (24 pt from the left) and the labels fade and clip.
pub fn sidebar(app: &mut App, ui: &mut Ui) {
    const ICON_X: f32 = 24.0;
    let t = sidebar_collapse(app, ui.ctx());
    let collapsed = app.sidebar_collapsed;
    // Labels fade out over the first two thirds of collapsing (and back in late).
    let a = ((1.0 - t) * 1.5 - 0.5).clamp(0.0, 1.0);
    // Content width when expanded: text keeps its truncation while the panel narrows.
    let full_w = ui.available_width() + (SIDEBAR_W - RAIL_W) * t;

    // Logo, with the page history right-aligned on the same line.
    ui.horizontal(|ui| {
        ui.spacing_mut().item_spacing.x = 0.0;
        ui.add_space(8.0);
        let (r, resp) = ui.allocate_exact_size(vec2(32.0, 32.0), Sense::click());
        mark(ui, Rect::from_center_size(r.center(), vec2(30.0, 20.0)), theme::with_alpha(theme::text(), a));
        let resp = named(resp, "Home");
        let resp = if collapsed { resp } else { resp.on_hover_cursor(egui::CursorIcon::PointingHand) };
        if resp.clicked() && !collapsed {
            app.actions.push(Action::Go(Page::Home));
        }
        // Drifts to the rail's edge as it collapses.
        ui.add_space(((ui.available_width() - 48.0) * (1.0 - t)).max(0.0));
        ui.spacing_mut().item_spacing.x = 4.0;
        if history_button(ui, icon::CHEVRON_LEFT, "Back") {
            app.nav_back();
        }
        if history_button(ui, icon::CHEVRON_RIGHT, "Forward") {
            app.nav_forward();
        }
    });
    ui.add_space(14.0);

    let mut go_to: Option<Page> = None;
    let look = NavLook::of(app);
    let nav = |ui: &mut Ui, glyph: &'static [u8], size: f32, label: &str, page: Page, selected: bool, go_to: &mut Option<Page>| {
        let (rect, resp) = ui.allocate_exact_size(vec2(ui.available_width(), 42.0), Sense::click());
        let mut resp = named(resp, label).on_hover_cursor(egui::CursorIcon::PointingHand);
        if collapsed {
            resp = resp.on_hover_text(label);
        }
        let h = theme::anim_bool(ui.ctx(), resp.id.with("h"), resp.hovered(), 0.15);
        let s = theme::anim_bool(ui.ctx(), resp.id.with("s"), selected, 0.2);
        let icon_fg = nav_bg(ui, look, rect, h, s);
        glyphs::draw(ui, pos2(rect.left() + ICON_X, rect.center().y), size, glyph, icon_fg);
        if a > 0.0 {
            let fg = theme::lerp_color(theme::text_dim(), theme::text(), (h + s).min(1.0));
            let font = if selected { theme::semibold(15.0) } else { theme::regular(15.0) };
            ui.painter().text(pos2(rect.left() + 48.0, rect.center().y), Align2::LEFT_CENTER, label, font, theme::with_alpha(fg, a));
        }
        if resp.clicked() {
            *go_to = Some(page);
        }
    };
    let p = app.page.clone();
    const APPLE: f32 = glyphs::APPLE_GLYPH;
    const LUCIDE: f32 = glyphs::LUCIDE_GLYPH;
    nav(ui, glyphs::SEARCH, APPLE, "Search", Page::Search(String::new()), matches!(p, Page::Search(_)), &mut go_to);
    nav(ui, glyphs::HOME, APPLE, "Home", Page::Home, p == Page::Home, &mut go_to);
    nav(ui, glyphs::EXPLORE, APPLE, "New", Page::New, p == Page::New, &mut go_to);
    nav(ui, glyphs::LIBRARY, APPLE, "Library", Page::Library(LibTab::Playlists), matches!(p, Page::Library(_)), &mut go_to);
    nav(ui, glyphs::DOWNLOAD, APPLE, "Downloads", Page::Downloads, p == Page::Downloads, &mut go_to);

    ui.add_space(14.0);
    let (line, _) = ui.allocate_exact_size(vec2(ui.available_width(), 1.0), Sense::hover());
    ui.painter().rect_filled(line, 0.0, theme::glass(0.08));
    ui.add_space(14.0 - 4.0 * t);

    // Playlists that appeared since the order was last changed join it at the end, so a
    // drag can place any of them.
    if let Some(l) = &app.library {
        for c in &l.playlists {
            if let Target::Playlist(id) = &c.target
                && !app.playlist_order.contains(id)
            {
                app.playlist_order.push(id.clone());
            }
        }
    }
    // Drag-to-reorder and right-click-to-hide results, applied once the rows are done.
    let mut reorder: Option<(String, String)> = None;
    let mut hide: Option<String> = None;

    let transferring = super::transfer::sidebar_visible(app, ui.input(|i| i.time));
    // Room under the list: Settings, the app's sign-off, and the transfer card.
    let bottom_h = if transferring { 112.0 } else { 48.0 };
    let list_h = (ui.available_height() - bottom_h).max(0.0);
    ui.allocate_ui(vec2(ui.available_width(), list_h), |ui| {
        if !app.logged_in {
            // Too wide to squeeze: cross-fade the message and the rail's icon.
            if t < 0.5 {
                ui.multiply_opacity(1.0 - 2.0 * t);
                ui.label(egui::RichText::new("Sign in to see your playlists").color(theme::text_dim()));
                ui.add_space(8.0);
                if widgets::pill(ui, "Sign in", None, false).clicked() {
                    app.start_sign_in(ui.ctx());
                }
            } else {
                ui.multiply_opacity(2.0 * t - 1.0);
                nav(ui, glyphs::PERSON_CIRCLE, LUCIDE, "Sign in", Page::Settings, false, &mut go_to);
            }
            return;
        }
        // The heading folds away with the labels.
        let (head, _) = ui.allocate_exact_size(vec2(ui.available_width(), 20.0 * (1.0 - t)), Sense::hover());
        if a > 0.0 {
            ui.painter().text(head.left_top() + vec2(8.0, 0.0), Align2::LEFT_TOP, "PLAYLISTS", theme::semibold(11.0), theme::with_alpha(theme::text_faint(), a));
        }
        let bars = if t > 0.0 { egui::scroll_area::ScrollBarVisibility::AlwaysHidden } else { egui::scroll_area::ScrollBarVisibility::VisibleWhenNeeded };
        egui::ScrollArea::vertical().id_salt("sidebar-pl").auto_shrink(false).scroll_bar_visibility(bars).show(ui, |ui| {
            // Last launch's list until the library arrives.
            let Some(playlists) = app.library.as_ref().map(|l| &l.playlists).or(app.sidebar_cache.as_ref()) else {
                // The spinner animates every frame: give up on a request that never answered.
                if app.library_requested.elapsed() < std::time::Duration::from_secs(20) {
                    ui.horizontal(|ui| {
                        ui.add_space(ICON_X - 8.0);
                        ui.spinner();
                    });
                } else if t < 0.5 {
                    ui.multiply_opacity(1.0 - 2.0 * t);
                    ui.label(egui::RichText::new("Couldn't load your playlists").color(theme::text_dim()));
                    ui.add_space(8.0);
                    if widgets::pill(ui, "Try again", None, false).clicked() {
                        app.request_library();
                    }
                }
                return;
            };
            let mut visible: Vec<&crate::model::Card> = playlists
                .iter()
                .filter(|c| !matches!(&c.target, Target::Playlist(id) if app.hidden_playlists.contains(id)))
                .collect();
            visible.sort_by_key(|c| match &c.target {
                Target::Playlist(id) => app.playlist_order.iter().position(|x| x == id).unwrap_or(usize::MAX),
                _ => usize::MAX,
            });
            let drag_key = egui::Id::new("sidebar-pl-drag");
            let dragging: Option<String> = ui.data(|d| d.get_temp(drag_key));
            let down = ui.input(|i| i.pointer.any_down());
            let pointer = ui.input(|i| i.pointer.interact_pos());
            for c in &visible {
                let (rect, resp) = ui.allocate_exact_size(vec2(ui.available_width(), 48.0), Sense::click_and_drag());
                let id = match &c.target {
                    Target::Playlist(id) => id.clone(),
                    _ => continue,
                };
                if !ui.is_rect_visible(rect) {
                    continue;
                }
                let is_dragged = dragging.as_deref() == Some(id.as_str());
                let mut resp = named(resp, &c.title).on_hover_cursor(if is_dragged { egui::CursorIcon::Grabbing } else { egui::CursorIcon::PointingHand });
                if collapsed {
                    resp = resp.on_hover_text(&c.title);
                }
                if resp.drag_started() {
                    ui.data_mut(|d| d.insert_temp(drag_key, id.clone()));
                }
                if let Some(did) = &dragging
                    && did != &id
                    && down
                    && pointer.is_some_and(|p| rect.contains(p))
                {
                    reorder = Some((did.clone(), id.clone()));
                }
                let h = theme::anim_bool(ui.ctx(), resp.id.with("h"), resp.hovered(), 0.15);
                let selected = matches!(&c.target, Target::Playlist(id) if app.page == Page::Playlist(id.clone()));
                let lift = if is_dragged { 0.10 } else { 0.0 };
                ui.painter().rect_filled(rect, 8.0, theme::glass(0.05 * h + if selected { 0.08 } else { 0.0 } + lift));
                let img = Rect::from_center_size(pos2(rect.left() + ICON_X, rect.center().y), Vec2::splat(36.0));
                paint_cover(ui, img, &c.thumbs, theme::thumb_radius(), 1.0);
                if a > 0.0 {
                    let w = full_w - 58.0;
                    text_at(ui, pos2(img.right() + 10.0, rect.top() + 7.0), &c.title, theme::semibold(13.5), theme::with_alpha(theme::text(), a), w);
                    text_at(ui, pos2(img.right() + 10.0, rect.top() + 26.0), &c.subtitle, theme::regular(12.0), theme::with_alpha(theme::text_dim(), a), w);
                }
                egui::Popup::context_menu(&resp).show(|ui| {
                    ui.set_min_width(190.0);
                    if ui.button("Hide from sidebar").clicked() {
                        hide = Some(id.clone());
                        ui.close();
                    }
                });
                if resp.clicked() && !is_dragged {
                    app.actions.push(Action::Open(c.target.clone()));
                }
            }
            if ui.input(|i| i.pointer.any_released()) {
                ui.data_mut(|d| d.remove_temp::<String>(drag_key));
            }
        });
    });
    if let Some(id) = hide {
        app.hidden_playlists.insert(id);
    }
    if let Some((dragged, target)) = reorder {
        if let (Some(from), Some(to)) = (app.playlist_order.iter().position(|x| x == &dragged), app.playlist_order.iter().position(|x| x == &target)) {
            let id = app.playlist_order.remove(from);
            app.playlist_order.insert(to.min(app.playlist_order.len()), id);
        }
    }
    let ready = app.updater.as_ref().and_then(|u| match u.state() {
        crate::update::State::Ready { version, .. } => Some(version),
        _ => None,
    });
    let mut restart = false;
    ui.with_layout(egui::Layout::bottom_up(egui::Align::Min), |ui| {
        nav(ui, glyphs::SETTINGS, LUCIDE, "Settings", Page::Settings, p == Page::Settings, &mut go_to);
        if transferring {
            super::transfer::sidebar_card(app, ui, collapsed, a, ICON_X);
        }
        if let Some(v) = &ready {
            ui.add_space(6.0);
            let (rect, resp) = ui.allocate_exact_size(vec2(ui.available_width(), 52.0), Sense::click());
            let mut resp = named(resp, "Restart to update").on_hover_cursor(egui::CursorIcon::PointingHand);
            if collapsed {
                resp = resp.on_hover_text(format!("Restart to update to BitChord {v}"));
            }
            let h = theme::anim_bool(ui.ctx(), resp.id.with("h"), resp.hovered(), 0.15);
            ui.painter().rect_filled(rect, 12.0, theme::with_alpha(look.accent, 0.22 + 0.12 * h));
            glyphs::draw(ui, pos2(rect.left() + ICON_X, rect.center().y), LUCIDE, glyphs::SPARKLES, theme::text());
            if a > 0.0 {
                ui.painter().text(pos2(rect.left() + 46.0, rect.top() + 17.0), Align2::LEFT_CENTER, "Restart to update", theme::semibold(13.5), theme::with_alpha(theme::text(), a));
                ui.painter().text(pos2(rect.left() + 46.0, rect.top() + 35.0), Align2::LEFT_CENTER, format!("BitChord {v}"), theme::regular(12.0), theme::with_alpha(theme::text(), 0.75 * a));
            }
            restart = resp.clicked();
        }
    });
    if restart {
        app.restart_to_update(ui.ctx());
    }
    if let Some(page) = go_to {
        if matches!(page, Page::Search(_)) {
            // Search lands on the search page with the caret in the box; already there,
            // it just brings the box back.
            app.search_refocus = true;
            if !matches!(app.page, Page::Search(_)) {
                app.actions.push(Action::Go(Page::Search(String::new())));
            }
        } else {
            app.actions.push(Action::Go(page));
        }
    }
}

// ------------------------------------------------------------------ top bar

pub fn top_bar(app: &mut App, ui: &mut Ui) {
    // The search field lives on the Search page: nothing here to anchor suggestions to.
    // Its focus flag is left alone here: the page still has to see it before the box
    // is drawn, or it would keep overwriting what's being typed with the page's query.
    app.search_rect = None;
    // Painted over the panel's top-right rather than laid out: the page's heading can
    // then start on the title bar's line instead of under a row kept for the avatar.
    let panel = ui.max_rect();
    let row = Rect::from_min_size(panel.min, vec2(panel.width(), 44.0));
    let right = panel.right() - super::titlebar::right_gap(ui.ctx(), row, 20.0);
    let r = Rect::from_min_max(pos2(right - 36.0, panel.top()), pos2(right, panel.top() + 36.0));
    let resp = ui.interact(r, ui.scope_id().with("account"), Sense::click());
    let resp = named(resp, "Account").on_hover_cursor(egui::CursorIcon::PointingHand);
    let initial = app.account_name.as_deref().and_then(|n| n.chars().next()).unwrap_or('?');
    if app.logged_in {
        ui.painter().circle_filled(r.center(), 18.0, theme::lerp_color(app.accent_color(), Color32::BLACK, 0.35));
        if let Some(avatar) = &app.account_avatar {
            widgets::paint_remote(ui, avatar, r, 18.0, Rect::from_min_max(pos2(0.0, 0.0), pos2(1.0, 1.0)), Color32::WHITE);
        } else {
            ui.painter().text(r.center(), Align2::CENTER_CENTER, initial, theme::bold(16.0), Color32::WHITE);
        }
    } else {
        ui.painter().circle_filled(r.center(), 18.0, theme::glass(0.12));
        icon_at(ui, r.center(), icon::USER, 18.0, theme::text_dim());
    }
    if resp.clicked() {
        app.actions.push(Action::Go(Page::Settings));
    }
    if app.loading {
        let spin = Rect::from_min_size(pos2(r.left() - 34.0, r.top() + 8.0), Vec2::splat(20.0));
        let mut cui = ui.new_child(egui::UiBuilder::new().max_rect(spin));
        cui.spinner();
    }
}

/// The search pill: typeahead, suggestions and Enter to search. The Search page draws
/// it; `app.search_rect` anchors the suggestions under it.
pub fn search_field(app: &mut App, ui: &mut Ui, width: f32) {
    // Anywhere on the pill focuses the box, not just on the text area inside it.
    let (rect, field) = ui.allocate_exact_size(vec2(width, 42.0), Sense::click());
    let field = field.on_hover_cursor(egui::CursorIcon::Text);
    let focus = theme::anim_bool(ui.ctx(), "search-focus", app.search_focused, 0.2);
    ui.painter().rect_filled(rect, 21.0, theme::glass(0.09 + 0.05 * focus));
    if focus > 0.0 {
        ui.painter().rect_stroke(rect, 21.0, egui::Stroke::new(1.0, theme::glass(0.25 * focus)), egui::StrokeKind::Inside);
    }
    icon_at(ui, pos2(rect.left() + 22.0, rect.center().y), icon::SEARCH, 17.0, theme::text_dim());
    let clearable = !app.search_text.is_empty();
    let inner = Rect::from_min_max(pos2(rect.left() + 42.0, rect.top() + 4.0), pos2(rect.right() - if clearable { 46.0 } else { 16.0 }, rect.bottom() - 4.0));
    // Up and Down walk the suggestions (past either end back to what was typed). Taken
    // before the box sees them, where they'd move the caret. Only bare arrows (Shift
    // selects text), and only suggestions for what's typed now, not ones still being replaced.
    let fresh = app.suggestions.0 == app.search_text.trim();
    let n = if fresh { app.suggestions.1.len() } else { 0 };
    if app.search_focused && n > 0 && ui.input(|i| i.modifiers.is_none()) {
        ui.input_mut(|i| {
            if i.consume_key(egui::Modifiers::NONE, egui::Key::ArrowDown) {
                app.suggest_sel = match app.suggest_sel {
                    None => Some(0),
                    Some(s) if s + 1 < n => Some(s + 1),
                    Some(_) => None,
                };
            }
            if i.consume_key(egui::Modifiers::NONE, egui::Key::ArrowUp) {
                app.suggest_sel = match app.suggest_sel {
                    None => Some(n - 1),
                    Some(0) => None,
                    Some(s) => Some(s - 1),
                };
            }
        });
    }
    let edit = ui.put(
        inner,
        egui::TextEdit::singleline(&mut app.search_text)
            .hint_text(egui::RichText::new("Search songs, albums, artists, podcasts").color(theme::text_faint()))
            .font(theme::regular(15.0))
            .frame(egui::Frame::NONE)
            .align(egui::Align2::LEFT_CENTER)
            .desired_width(inner.width()),
    );
    app.search_rect = Some(rect);
    if std::mem::take(&mut app.search_refocus) {
        edit.request_focus();
    }
    app.search_focused = edit.has_focus();
    let mut cleared = false;
    if clearable {
        let x = Rect::from_center_size(pos2(rect.right() - 25.0, rect.center().y), Vec2::splat(30.0));
        let resp = named(ui.interact(x, ui.scope_id().with("search-clear"), Sense::click()), "Clear search").on_hover_cursor(egui::CursorIcon::PointingHand);
        let h = theme::anim_bool(ui.ctx(), resp.id.with("h"), resp.hovered(), 0.15);
        if h > 0.0 {
            ui.painter().circle_filled(x.center(), 15.0, theme::glass(0.10 * h));
        }
        icon_at(ui, x.center(), icon::X, 16.0, theme::lerp_color(theme::text_dim(), theme::text(), h));
        if resp.clicked() {
            // Back to the empty search page: the old results go with the text.
            app.search_text.clear();
            app.search_synced = String::new();
            app.suggestions = Default::default();
            app.suggest_sel = None;
            cleared = true;
            app.actions.push(Action::Go(Page::Search(String::new())));
            edit.request_focus();
        }
    }
    if field.clicked() || ui.input(|i| i.modifiers.command && i.key_pressed(egui::Key::F)) {
        edit.request_focus();
    }
    if edit.changed() || edit.gained_focus() || cleared {
        app.suggest_sel = None;
        let now = ui.input(|i| i.time);
        // Show cached results immediately, then refresh after a short typing pause.
        let first = app.suggestions.1.is_empty();
        let query = app.search_text.trim();
        app.suggestions = app.suggest_cache.iter()
            .find(|(q, _)| q == query)
            .cloned()
            .unwrap_or_default();
        let delay = if first || cleared || edit.gained_focus() { 0.0 } else { 0.06 };
        app.suggest_due = Some(now + delay);
    }
    // The event loop can otherwise sleep past the debounce deadline.
    if let Some(due) = app.suggest_due {
        let now = ui.input(|i| i.time);
        ui.ctx().request_repaint_after(std::time::Duration::from_secs_f64((due - now).max(0.0)));
    }
    if edit.lost_focus() && ui.input(|i| i.key_pressed(egui::Key::Enter)) {
        let picked = app.suggest_sel.filter(|_| fresh).and_then(|s| app.suggestions.1.get(s)).map(|s| s.text.clone());
        let q = picked.unwrap_or_else(|| app.search_text.trim().to_owned());
        if !q.is_empty() {
            app.suggestions = Default::default();
            app.actions.push(Action::Go(Page::Search(q)));
        }
    }
}

/// Search suggestions dropdown under the search box.
pub fn suggestions(app: &mut App, ctx: &egui::Context) {
    let Some(rect) = app.search_rect else { return };
    // Through a press on a remove button too: its release briefly takes the box's focus.
    let show = (app.search_focused || app.forget_held) && !app.suggestions.1.is_empty() && !app.np_open;
    let t = theme::anim_bool(ctx, "suggest-open", show, 0.15);
    if t <= 0.0 {
        return;
    }
    egui::Area::new(egui::Id::unique("suggestions"))
        .order(egui::Order::Foreground)
        .fixed_pos(rect.left_bottom() + vec2(0.0, 6.0 - 6.0 * (1.0 - t)))
        .show(ctx, |ui| {
            ui.set_opacity(t);
            egui::Frame::new()
                .fill(theme::menu_fill())
                .corner_radius(14)
                .inner_margin(6)
                .shadow(egui::epaint::Shadow { offset: [0, 10], blur: 30, spread: 0, color: theme::shade(0.5) })
                .show(ui, |ui| {
                    ui.set_width(rect.width() - 12.0);
                    if !ui.input(|i| i.pointer.any_down() || i.pointer.any_released()) {
                        app.forget_held = false;
                    }
                    // Releasing that press is a click outside the box, which takes its focus
                    // (and this list) away: give it back until the press is over.
                    if app.forget_held {
                        app.search_refocus = true;
                    }
                    let now = ui.input(|i| i.time);
                    for (i, s) in app.suggestions.1.clone().into_iter().enumerate() {
                        let term = s.text;
                        // The removed one folds away, and the rows below slide up into its place.
                        if let Some((_, at)) = app.forgetting.as_ref().filter(|(t, _)| s.forget.as_deref() == Some(t.as_str())) {
                            let p = ((now - at) / 0.18).clamp(0.0, 1.0) as f32;
                            let e = 1.0 - (1.0 - p).powi(3);
                            let gap = ui.spacing().item_spacing.y;
                            // Its spacing folds away with it, so nothing jumps at the end.
                            ui.spacing_mut().item_spacing.y = 0.0;
                            let (r, _) = ui.allocate_exact_size(vec2(ui.available_width(), (40.0 + gap) * (1.0 - e)), Sense::hover());
                            ui.spacing_mut().item_spacing.y = gap;
                            ui.scope(|ui| {
                                ui.set_clip_rect(r.intersect(ui.clip_rect()));
                                ui.set_opacity(1.0 - e);
                                let cy = r.top() + 20.0;
                                icon_at(ui, pos2(r.left() + 20.0, cy), icon::HISTORY, 15.0, theme::text_dim());
                                suggestion_text(ui, pos2(r.left() + 42.0, cy - 9.0), &term, &app.suggestions.0, r.width() - 88.0);
                            });
                            ui.ctx().request_repaint();
                            continue;
                        }
                        let (r, resp) = ui.allocate_exact_size(vec2(ui.available_width(), 40.0), Sense::click());
                        let resp = named(resp, &term).on_hover_cursor(egui::CursorIcon::PointingHand);
                        let h = theme::anim_bool(ui.ctx(), resp.id.with("h"), resp.contains_pointer() || app.suggest_sel == Some(i), 0.1);
                        ui.painter().rect_filled(r, 8.0, theme::glass(0.08 * h));
                        let glyph = if s.forget.is_some() { icon::HISTORY } else { icon::SEARCH };
                        icon_at(ui, pos2(r.left() + 20.0, r.center().y), glyph, 15.0, theme::text_dim());
                        let room = if s.forget.is_some() { 88.0 } else { 52.0 };
                        suggestion_text(ui, pos2(r.left() + 42.0, r.center().y - 9.0), &term, &app.suggestions.0, r.width() - room);
                        // A past search can be removed from the account's history.
                        if let Some(token) = s.forget {
                            let x = Rect::from_center_size(pos2(r.right() - 22.0, r.center().y), Vec2::splat(30.0));
                            let xr = named(ui.interact(x, resp.id.with("forget"), Sense::click()), "Remove from recent searches")
                                .on_hover_text("Remove")
                                .on_hover_cursor(egui::CursorIcon::PointingHand);
                            let xh = theme::anim_bool(ui.ctx(), xr.id.with("h"), xr.hovered(), 0.1);
                            if xh > 0.0 {
                                ui.painter().circle_filled(x.center(), 15.0, theme::glass(0.12 * xh));
                            }
                            icon_at(ui, x.center(), icon::TRASH_2, 15.0, theme::lerp_color(theme::text_dim(), theme::text(), xh));
                            // On the press, and one per press: the next row's button slides
                            // under the held pointer.
                            if (xr.is_pointer_button_down_on() || xr.clicked()) && !app.forget_held {
                                app.forget_held = true;
                                // One still folding away is done.
                                if let Some((t, _)) = app.forgetting.take() {
                                    app.suggestions.1.retain(|s| s.forget.as_deref() != Some(t.as_str()));
                                }
                                app.forgetting = Some((token.clone(), now));
                                app.suggest_sel = None;
                                app.suggest_round += 1;
                                app.suggest_cache.clear();
                                app.forgets_pending += 1;
                                app.search_refocus = true;
                                app.backend.request(crate::backend::Req::ForgetSearch(token));
                            }
                        }
                        // Pointer-down, not click: the text box loses focus (hiding this) first.
                        if resp.is_pointer_button_down_on() || resp.clicked() {
                            app.suggestions = Default::default();
                            app.search_focused = false;
                            app.actions.push(Action::Go(Page::Search(term)));
                            break;
                        }
                    }
                    if let Some((t, _)) = app.forgetting.take_if(|(_, at)| now - *at >= 0.18) {
                        app.suggestions.1.retain(|s| s.forget.as_deref() != Some(t.as_str()));
                    }
                });
        });
}

/// A suggestion with what's typed in bold and the rest dimmed, as on music.youtube.com.
fn suggestion_text(ui: &Ui, pos: Pos2, text: &str, typed: &str, width: f32) {
    let n = text.chars().zip(typed.chars()).take_while(|(a, b)| a.to_lowercase().eq(b.to_lowercase())).count();
    let split = if n == typed.chars().count() { text.char_indices().nth(n).map_or(text.len(), |(i, _)| i) } else { 0 };
    let (bold, rest) = text.split_at(split);
    let mut job = egui::text::LayoutJob::default();
    job.append(bold, 0.0, egui::TextFormat { font_id: theme::semibold(15.0), color: theme::text(), ..Default::default() });
    job.append(rest, 0.0, egui::TextFormat { font_id: theme::regular(15.0), color: theme::text_dim(), ..Default::default() });
    job.wrap = egui::text::TextWrapping::truncate_at_width(width.max(10.0));
    let g = ui.fonts_mut(|f| f.layout_job(job));
    ui.painter().galley(pos, g, theme::text());
}

// ------------------------------------------------------------------ player bar

/// The player: one frosted capsule floating over the bottom of the window, as in the
/// BitChord desktop app. Transport on the left, the sleeve with its titles and seek
/// line in the middle, favourites / lyrics / queue / volume on the right.
pub fn player_bar(app: &mut App, ui: &mut Ui, now: f64, frosted: bool) {
    let full = ui.max_rect();
    if frosted {
        // The page scrolls on under this strip: fade it out rather than end it at the
        // player's edge. Clicks in the fade stay with the player, not the rows beneath.
        vgradient(ui.painter(), full, theme::panel(0.0), theme::panel(0.66));
        ui.interact(full, ui.id().with("player-strip"), Sense::hover());
    }
    const H: f32 = 56.0;
    let w = (full.width() - 24.0).min(668.0);
    let cap = Rect::from_min_size(pos2(full.center().x - w / 2.0, full.bottom() - 16.0 - H), vec2(w, H));
    let ctx = ui.ctx().clone();
    widgets::soft_shadow(ui, cap, H / 2.0, 20.0, 0.45);
    // Frosted: the page's colours show through a milky pane, with a lit top half and
    // a bright rim standing in for the blur egui can't do.
    ui.painter().rect_filled(cap, H / 2.0, theme::panel(0.88));
    let top = Rect::from_min_max(cap.min, pos2(cap.max.x, cap.min.y + H * 0.55));
    ui.painter().rect_filled(top, egui::CornerRadius { nw: 28, ne: 28, sw: 0, se: 0 }, theme::glass(0.07));
    ui.painter().rect_stroke(cap, H / 2.0, egui::Stroke::new(1.0, theme::glass(0.2)), egui::StrokeKind::Inside);
    ui.painter().hline(cap.x_range().shrink(26.0), cap.top() + 1.0, egui::Stroke::new(1.0, theme::glass(0.24)));

    let status = app.audio.status.clone();
    // An Automix handoff keeps the outgoing song on this strip until the next one is
    // the louder of the two.
    let hold = app.mix_hold().map(|h| (h.total, h.mix, app.mix_hold_pos(h)));
    let track = match &hold {
        Some(_) => app.shown().cloned(),
        None => app.current().cloned(),
    };
    let enabled = track.is_some();
    let playing = app.playing();
    let accent = app.accent_color();
    let cy = cap.center().y;
    let narrow = cap.width() < 550.0;
    let pad = 16.0;
    let run_w = if narrow { 112.0 } else { 184.0 };
    let right_w = 132.0;
    let run_l = cap.left() + pad;
    let right_l = cap.right() - pad - right_w;
    let lcd_l = run_l + run_w + 8.0;
    let cover = Rect::from_center_size(pos2(lcd_l + 17.0, cy), Vec2::splat(34.0));
    let text_l = cover.right() + 8.0;
    let text_r = right_l - 6.0;
    let text_w = (text_r - text_l).max(20.0);
    let ink = |h: f32| {
        let c = theme::lerp_color(theme::text_dim(), theme::text(), h);
        if enabled { c } else { c.gamma_multiply(0.45) }
    };

    // Transport: shuffle | previous · play · next | repeat. The glyphs alone carry the
    // state; no button fills.
    let tbtn = |ui: &mut Ui, x: f32, key: &str, name: &str| -> egui::Response {
        let r = Rect::from_center_size(pos2(x, cy), Vec2::splat(28.0));
        let resp = ui.interact(r, ui.scope_id().with(("bar-t", key)), Sense::click());
        named(resp, name).on_hover_text(name).on_hover_cursor(egui::CursorIcon::PointingHand)
    };
    let mid = run_l + (run_w - 8.0) / 2.0;
    if !narrow {
        let r = tbtn(ui, run_l + 14.0, "shuffle", "Shuffle up next");
        let h = theme::anim_bool(&ctx, r.id.with("h"), r.hovered(), 0.12);
        glyphs::draw(ui, r.rect.center(), 28.0, glyphs::SHUFFLE, ink(h));
        if r.clicked() {
            app.shuffle_upcoming();
        }
    }
    let r = tbtn(ui, mid - 33.0, "prev", "Previous");
    let h = theme::anim_bool(&ctx, r.id.with("h"), r.hovered(), 0.12);
    glyphs::draw(ui, r.rect.center(), 26.0, glyphs::PREVIOUS, ink(h));
    if r.clicked() && enabled {
        app.prev();
    }
    let r = tbtn(ui, mid, "play", if playing { "Pause" } else { "Play" });
    let h = theme::anim_bool(&ctx, r.id.with("h"), r.hovered(), 0.12);
    let c = r.rect.center();
    let morph = theme::anim_bool(&ctx, "bar-play-morph", playing || app.buffering, 0.18);
    if app.buffering {
        let a = now as f32 * 6.0;
        let pts: Vec<Pos2> = (0..16).map(|i| c + vec2((a + i as f32 * 0.24).cos(), (a + i as f32 * 0.24).sin()) * 8.0).collect();
        ui.painter().add(egui::Shape::Path(egui::epaint::PathShape::line(pts, egui::epaint::PathStroke::new(2.2, ink(0.6)))));
        ctx.request_repaint_after(std::time::Duration::from_millis(33));
    } else {
        // The glyphs cross-fade rather than swap: the site keeps its pause mark up
        // through a buffer, and a hard cut between the two weights flickers.
        let fg = ink(0.55 + 0.45 * h);
        if morph < 1.0 {
            glyphs::draw(ui, c, 28.0, glyphs::PLAY, theme::with_alpha(fg, 1.0 - morph));
        }
        if morph > 0.0 {
            glyphs::draw(ui, c, 28.0, glyphs::PAUSE, theme::with_alpha(fg, morph));
        }
    }
    if r.clicked() && enabled {
        app.toggle();
    }
    let r = tbtn(ui, mid + 33.0, "next", "Next");
    let h = theme::anim_bool(&ctx, r.id.with("h"), r.hovered(), 0.12);
    glyphs::draw(ui, r.rect.center(), 26.0, glyphs::NEXT, ink(h));
    if r.clicked() && enabled {
        app.next();
    }
    if !narrow {
        let (glyph, on) = match app.queue.repeat {
            Repeat::Off => (glyphs::REPEAT, false),
            Repeat::All => (glyphs::REPEAT, true),
            Repeat::One => (glyphs::REPEAT_ONE, true),
        };
        let r = tbtn(ui, run_l + run_w - 8.0 - 14.0, "repeat", "Repeat");
        let h = theme::anim_bool(&ctx, r.id.with("h"), r.hovered(), 0.12);
        glyphs::draw(ui, r.rect.center(), 28.0, glyph, if on { accent } else { ink(h) });
        if r.clicked() {
            app.queue.repeat = match app.queue.repeat {
                Repeat::Off => Repeat::All,
                Repeat::All => Repeat::One,
                Repeat::One => Repeat::Off,
            };
        }
    }

    // Seek: a thin line under the sleeve and titles, thicker while it has the pointer.
    let held = hold.is_some();
    let (total, mix) = match &hold {
        Some((total, mix, _)) => (*total, *mix),
        None => {
            let total = status
                .duration()
                .map(|d| d.as_secs_f32())
                .or(track.as_ref().and_then(|t| t.duration).map(|d| d as f32))
                .unwrap_or(0.0);
            let next = app.queue.upcoming().is_some() || app.autoplay;
            let mix = match (total > 0.0, next, app.queue.repeat) {
                (true, true, repeat) if repeat != Repeat::One => {
                    status.mix_secs(status.duration().or_else(|| track.as_ref().and_then(|t| t.duration).map(|s| std::time::Duration::from_secs(s as u64)))).min(total * 0.5)
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
    let seek_hit = Rect::from_min_max(pos2(text_l, cap.bottom() - 13.0), pos2(text_r, cap.bottom() - 2.0));
    let sresp = ui.interact(seek_hit, ui.scope_id().with("bar-seek"), if held { Sense::hover() } else { Sense::click_and_drag() });
    let sresp = if total > 0.0 && !held { sresp.on_hover_cursor(egui::CursorIcon::PointingHand) } else { sresp };
    sresp.widget_info(|| egui::WidgetInfo::slider(total > 0.0 && !held, pos as f64, "Seek"));
    let focus = theme::anim_bool(&ctx, "bar-seek-h", total > 0.0 && !held && (sresp.hovered() || sresp.dragged()), 0.15);
    let (sx0, sx1) = (text_l, text_r);
    let sy = cap.bottom() - 7.0;
    let thick = 2.0 + 2.0 * focus;
    let line_r = Rect::from_min_max(pos2(sx0, sy - thick / 2.0), pos2(sx1, sy + thick / 2.0));
    ui.painter().rect_filled(line_r, thick / 2.0, theme::glass(0.18));
    // Automix's handoff window, tinted on the part still to play.
    if mix > 0.5 {
        let edge = sx0 + (sx1 - sx0) * ((total - mix) / total);
        let band = Rect::from_min_max(pos2(edge, line_r.top()), line_r.max);
        ui.painter().rect_filled(band, thick / 2.0, theme::with_alpha(accent, 0.30));
        let tick = Rect::from_min_size(pos2(edge - 0.75, line_r.top()), vec2(1.5, thick));
        ui.painter().rect_filled(tick, 0.75, theme::with_alpha(accent, 0.9));
    }
    let frac = if total > 0.0 { (pos / total).clamp(0.0, 1.0) } else { 0.0 };
    if frac > 0.0 {
        ui.painter().rect_filled(Rect::from_min_size(line_r.min, vec2(line_r.width() * frac, thick)), thick / 2.0, accent);
    }
    let at = |p: Pos2| (((p.x - sx0) / (sx1 - sx0)).clamp(0.0, 1.0) * total).max(0.0);

    // Sleeve: the mark when nothing is playing; a click goes up into the player.
    match &track {
        Some(t) => {
            widgets::paint_cover_xfade(ui, egui::Id::unique("player-bar-art"), cover, &t.thumbs, 6.0, 1.0);
            let ar = ui.interact(cover, ui.scope_id().with("bar-art"), Sense::click());
            let ar = named(ar, "Open now playing").on_hover_cursor(egui::CursorIcon::PointingHand);
            let ah = theme::anim_bool(&ctx, ar.id.with("h"), ar.hovered(), 0.15);
            if ah > 0.0 {
                // egui has no blur of what's already drawn: frost the sleeve with a milky
                // pane and a scrim, so the art reads soft under the expand mark.
                ui.painter().rect_filled(cover, 6.0, theme::glass(0.16 * ah));
                ui.painter().rect_filled(cover, 6.0, theme::shade(0.3 * ah));
                glyphs::draw(ui, cover.center(), 14.0, glyphs::MAXIMIZE_2, theme::with_alpha(Color32::WHITE, ah));
            }
            if ar.clicked() {
                app.actions.push(Action::OpenNowPlaying(true));
            }
        }
        None => {
            ui.painter().rect_filled(cover, 6.0, theme::glass(0.08));
            paint_mark(ui, cover.shrink(8.0));
        }
    }

    // Titles, dimmed while the seek line has the pointer; the artist line gives way to
    // the times, which take the same row so nothing overlaps.
    let (title, _) = match &track {
        Some(t) => (t.title.clone(), ()),
        None => ("BitChord".to_owned(), ()),
    };
    let fade = 1.0 - 0.7 * focus;
    let afade = 1.0 - focus;
    let tr = text_at(ui, pos2(text_l, cy - 15.0), &title, theme::semibold(13.0), theme::with_alpha(theme::text(), fade), text_w);
    if let Some(t) = &track {
        let tre = ui.interact(tr, ui.scope_id().with("bar-title"), Sense::click());
        if named(tre, &t.title).on_hover_text(&t.title).on_hover_cursor(egui::CursorIcon::PointingHand).clicked() {
            app.actions.push(Action::OpenNowPlaying(true));
        }
    }
    let mut ax = text_l;
    if afade > 0.01 && let Some(t) = &track {
        for (i, a) in t.artists.iter().enumerate() {
            let s = if i + 1 < t.artists.len() { format!("{}, ", a.name) } else { a.name.clone() };
            let r = text_at(ui, pos2(ax, cy + 2.0), &s, theme::regular(11.5), theme::with_alpha(theme::text_dim(), afade), (text_l + text_w - ax).max(10.0));
            if let Some(id) = &a.id {
                let resp = ui.interact(r, ui.scope_id().with(("bar-artist", i)), Sense::click());
                let resp = named(resp, &a.name).on_hover_cursor(egui::CursorIcon::PointingHand);
                if resp.hovered() {
                    ui.painter().hline(r.x_range(), r.bottom(), egui::Stroke::new(1.0, theme::text_dim()));
                }
                if resp.clicked() {
                    app.actions.push(Action::Open(Target::Artist(id.clone())));
                }
            }
            ax = r.right();
            if ax > text_l + text_w {
                break;
            }
        }
    }
    if focus > 0.0 && total > 0.0 {
        let col = theme::with_alpha(theme::text(), focus);
        let g = widgets::galley(ui, &fmt_time(pos as u32), theme::semibold(11.5), theme::text(), 80.0);
        ui.painter().galley(pos2(text_l, cy + 2.0), g, col);
        let left = fmt_time((total - pos).max(0.0) as u32);
        let g = widgets::galley(ui, &format!("-{left}"), theme::semibold(11.5), theme::text(), 80.0);
        ui.painter().galley(pos2(text_r - g.size().x, cy + 2.0), g, col);
    }

    // Right: favourite, lyrics, up next, volume. Hovering the volume slides a slider
    // over the other three, as in the reference.
    let vol = app.audio.volume();
    let rslot = |i: usize| right_l + 21.0 + i as f32 * 33.0;
    let vol_r = Rect::from_center_size(pos2(rslot(3), cy), Vec2::splat(28.0));
    let slider = Rect::from_min_size(pos2(cap.right() - pad - 33.0 - 96.0, cy - 10.0), vec2(96.0, 20.0));
    let open_id = ui.scope_id().with("bar-vol");
    let was = ui.data(|d| d.get_temp::<bool>(open_id).unwrap_or(false));
    // Pointer position, not `rect_contains_pointer`: the window's own title-bar layers
    // sit above this bar, so egui would report the pointer as being over them.
    let hover = |r: Rect| ui.input(|i| i.pointer.hover_pos()).is_some_and(|p| r.contains(p));
    let band = Rect::from_min_max(pos2(slider.left() - 3.0, cy - 14.0), pos2(vol_r.right(), cy + 14.0));
    let open = hover(vol_r) || (was && (hover(band) || ui.input(|i| i.pointer.any_down())));
    ui.data_mut(|d| d.insert_temp(open_id, open));
    let reveal = theme::anim_bool(&ctx, "bar-vol-reveal", open, 0.15);
    let rbtn = |ui: &mut Ui, i: usize, key: &str, name: &str| -> egui::Response {
        let r = Rect::from_center_size(pos2(rslot(i), cy), Vec2::splat(28.0));
        let resp = ui.interact(r, ui.scope_id().with(("bar-r", key)), Sense::click());
        named(resp, name).on_hover_text(name).on_hover_cursor(egui::CursorIcon::PointingHand)
    };
    if reveal < 0.5 {
        // What's heard, like Now playing's menu: a song version standing in for the video.
        let heard = if held { track.clone() } else { app.playing_track().cloned().or_else(|| track.clone()) };
        let liked = heard.as_ref().is_some_and(|t| app.liked.contains(&t.id));
        let r = rbtn(ui, 0, "like", if liked { "Remove from favourites" } else { "Add to favourites" });
        let h = theme::anim_bool(&ctx, r.id.with("h"), r.hovered(), 0.15);
        let pop = heard.as_ref().map_or(0.0, |t| theme::anim_bool(&ctx, ("liked", &t.id), liked, 0.3));
        let dim = if heard.is_some() { 1.0 } else { 0.4 };
        // The filled star grows in under the ring rather than replacing it, as the
        // reference's own two-weight mark does.
        let tint = theme::lerp_color(ink(h), accent, pop).gamma_multiply(dim);
        glyphs::draw(ui, r.rect.center(), 18.0, glyphs::STAR, tint);
        if pop > 0.0 {
            glyphs::draw(ui, r.rect.center(), 18.0, glyphs::STAR_FILLED, theme::with_alpha(tint, tint.a() as f32 / 255.0 * pop));
        }
        if r.clicked()
            && let Some(t) = heard
        {
            app.actions.push(Action::Like(t, !liked));
        }
        let lyrics_on = app.np_open && app.np_tab == NpTab::Lyrics && !app.no_lyrics();
        let r = rbtn(ui, 1, "lyrics", "Lyrics");
        let h = theme::anim_bool(&ctx, r.id.with("h"), r.hovered(), 0.15);
        glyphs::draw(ui, r.rect.center(), 18.0, if lyrics_on { glyphs::LYRICS } else { glyphs::LYRICS_OFF }, if lyrics_on { accent } else { ink(h) });
        if r.clicked() {
            if lyrics_on {
                app.np_open = false;
            } else {
                app.np_tab = NpTab::Lyrics;
                app.actions.push(Action::OpenNowPlaying(true));
            }
        }
        let queue_open = if app.np_open { app.np_tab == NpTab::UpNext } else { app.queue_open };
        let r = rbtn(ui, 2, "queue", "Up next");
        let h = theme::anim_bool(&ctx, r.id.with("h"), r.hovered(), 0.15);
        glyphs::draw(ui, r.rect.center(), 18.0, glyphs::QUEUE, if queue_open { accent } else { ink(h) });
        if r.clicked() {
            if app.np_open {
                app.np_tab = if queue_open { NpTab::Art } else { NpTab::UpNext };
            } else {
                app.queue_open = !app.queue_open;
            }
        }
    } else {
        volume_slider(app, ui, slider, "bar");
    }
    let r = rbtn(ui, 3, "vol", "Mute");
    let h = theme::anim_bool(&ctx, r.id.with("h"), r.hovered(), 0.15);
    // Three arcs, not two: the desktop app lights them a third at a time.
    let step = glyphs::volume_step(vol);
    glyphs::draw(ui, r.rect.center(), 18.0, glyphs::VOLUME[step], theme::lerp_color(theme::text_dim(), theme::text(), h));
    if r.hovered() {
        let scroll = ui.input(|i| i.smooth_scroll_delta.y);
        if scroll != 0.0 {
            app.audio.set_volume((vol + scroll / 600.0).clamp(0.0, 1.0));
        }
    }
    if r.clicked() {
        let stash = unmute_id();
        if vol > 0.0 {
            ui.data_mut(|d| d.insert_temp(stash, vol));
            app.audio.set_volume(0.0);
        } else {
            app.audio.set_volume(ui.data(|d| d.get_temp::<f32>(stash)).unwrap_or(0.6));
        }
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
}

/// The BitChord mark, white, fitted inside `r` (the sleeve slot when nothing plays).
fn paint_mark(ui: &Ui, r: Rect) {
    mark(ui, r, Color32::from_white_alpha(170));
}

/// The BitChord mark, fitted inside `r`, tinted `tint`.
fn mark(ui: &Ui, r: Rect, tint: Color32) {
    const MARK: &[u8] = include_bytes!("../../assets/icon/mark.png");
    const W: f32 = 256.0;
    const H: f32 = 171.0;
    let key = egui::Id::unique("bitchord-mark");
    let tex = match ui.ctx().data(|d| d.get_temp::<egui::TextureHandle>(key)) {
        Some(t) => t,
        None => {
            let Ok(img) = image::load_from_memory(MARK) else { return };
            let img = img.into_rgba8();
            let color = egui::ColorImage::from_rgba_unmultiplied([img.width() as usize, img.height() as usize], img.as_raw());
            let t = ui.ctx().load_texture("bitchord-mark", color, egui::TextureOptions::LINEAR);
            ui.ctx().data_mut(|d| d.insert_temp(key, t.clone()));
            t
        }
    };
    let k = (r.width() / W).min(r.height() / H);
    let dst = Rect::from_center_size(r.center(), vec2(W * k, H * k));
    ui.painter().image(tex.id(), dst, Rect::from_min_max(pos2(0.0, 0.0), pos2(1.0, 1.0)), tint);
}

/// Where Mute keeps the volume to go back to (the player bar's and the mini player's).
pub fn unmute_id() -> egui::Id {
    egui::Id::unique("unmute")
}

/// The volume track: click or drag to set, scroll over it to nudge.
fn volume_slider(app: &mut App, ui: &mut Ui, slider: Rect, salt: &str) {
    let vol = app.audio.volume();
    let vresp = ui.interact(slider, ui.scope_id().with(salt), Sense::click_and_drag()).on_hover_cursor(egui::CursorIcon::PointingHand);
    vresp.widget_info(|| egui::WidgetInfo::slider(true, vol as f64, "Volume"));
    let vh = theme::anim_bool(ui.ctx(), ui.scope_id().with((salt, "h")), vresp.hovered() || vresp.dragged(), 0.15);
    let track_r = Rect::from_center_size(slider.center(), vec2(slider.width(), 4.0));
    ui.painter().rect_filled(track_r, 2.0, theme::glass(0.15));
    let fill = Rect::from_min_size(track_r.min, vec2(track_r.width() * vol, 4.0));
    ui.painter().rect_filled(fill, 2.0, theme::lerp_color(theme::text_dim(), theme::text(), vh));
    ui.painter().circle_filled(pos2(fill.right(), track_r.center().y), 2.0 + 4.5 * vh, theme::text());
    if let Some(p) = vresp.interact_pointer_pos()
        && (vresp.dragged() || vresp.clicked())
    {
        app.audio.set_volume(((p.x - track_r.left()) / track_r.width()).clamp(0.0, 1.0));
    }
    if vresp.hovered() {
        let scroll = ui.input(|i| i.smooth_scroll_delta.y);
        if scroll != 0.0 {
            app.audio.set_volume((vol + scroll / 600.0).clamp(0.0, 1.0));
        }
    }
}

// ------------------------------------------------------------------ queue drawer

pub fn queue_drawer(app: &mut App, ui: &mut Ui) {
    ui.horizontal(|ui| {
        ui.label(egui::RichText::new("Up next").font(theme::bold(20.0)));
        ui.with_layout(egui::Layout::right_to_left(egui::Align::Center), |ui| {
            // Left of the window buttons, which sit over the drawer's top right.
            ui.add_space(super::titlebar::right_gap(ui.ctx(), ui.max_rect(), 0.0));
            if icon_button(ui, icon::X, 16.0, "Close queue", false).clicked() {
                app.queue_open = false;
            }
        });
    });
    ui.add_space(8.0);
    queue_list(app, ui, "drawer");
}

/// Current track plus everything after it. Shared by the drawer and now playing.
pub fn queue_list(app: &mut App, ui: &mut Ui, salt: &str) {
    let rows = super::pages::Rows::new(app, ui.input(|i| i.time));
    egui::ScrollArea::vertical().id_salt(("queue", salt)).auto_shrink(false).show(ui, |ui| {
        if app.queue.tracks.is_empty() {
            ui.label(egui::RichText::new("Nothing queued yet").color(theme::text_dim()));
            return;
        }
        let start = app.queue.index.unwrap_or(0);
        ui.checkbox(&mut app.autoplay, egui::RichText::new("Autoplay similar songs").color(theme::text_dim())).on_hover_cursor(egui::CursorIcon::PointingHand);
        ui.add_space(6.0);
        let (queue, liked, acts, has_current, by) = (&app.queue.tracks, &app.liked, &mut app.actions, app.queue.index.is_some(), &app.jam.by);
        widgets::visible_rows(ui, queue.len() - start, |ui, k| {
            let i = start + k;
            let t = &queue[i];
            // The first row is the current song.
            let current = k == 0 && has_current;
            let o = widgets::RowOpts { current, added_by: by.get(&t.id).cloned(), ..rows.opts(liked, t, None, false) };
            let resp = widgets::track_row(ui, t, &o, acts);
            if resp.clicked() {
                // The playing row pauses/resumes instead of doing nothing.
                acts.push(if current { Action::TogglePlay } else { Action::QueueJump(i) });
            }
            if !current && ui.rect_contains_pointer(Rect::from_min_max(resp.rect.min, pos2(resp.rect.right() + 42.0, resp.rect.bottom()))) {
                // Remove button over the duration column.
                let x = Rect::from_center_size(resp.rect.right_center() - vec2(26.0, 0.0), Vec2::splat(28.0));
                let xr = named(ui.interact(x, resp.id.with("rm"), Sense::click()), "Remove from queue").on_hover_cursor(egui::CursorIcon::PointingHand);
                ui.painter().circle_filled(x.center(), 14.0, theme::panel(0.6));
                icon_at(ui, x.center(), icon::X, 14.0, theme::text());
                if xr.clicked() {
                    acts.push(Action::QueueRemove(i));
                }
            }
        });
    });
}

// ------------------------------------------------------------------ toast

pub fn toast(app: &mut App, ctx: &egui::Context, now: f64) {
    let Some((msg, shown)) = &app.toast else { return };
    if *shown < 0.0 {
        return;
    }
    let age = (now - shown) as f32;
    let t = theme::ease_out(age / 0.25) * (1.0 - ((age - 4.1) / 0.4).clamp(0.0, 1.0));
    let screen = ctx.content_rect();
    egui::Area::new(egui::Id::unique("toast"))
        .order(egui::Order::Tooltip)
        .pivot(Align2::CENTER_BOTTOM)
        // Above the player bar; in the mini player, over the strip's middle.
        .fixed_pos(Pos2::new(screen.center().x, if screen.height() < 200.0 { screen.center().y + 22.0 } else { screen.bottom() - 104.0 } + 14.0 * (1.0 - t)))
        .show(ctx, |ui| {
            ui.set_opacity(t);
            egui::Frame::new()
                .fill(theme::menu_fill())
                .corner_radius(12)
                .inner_margin(egui::Margin::symmetric(18, 12))
                .shadow(egui::epaint::Shadow { offset: [0, 8], blur: 24, spread: 0, color: theme::shade(0.45) })
                .show(ui, |ui| {
                    ui.set_max_width(520.0);
                    ui.label(egui::RichText::new(msg.as_str()).font(theme::regular(14.0)).color(theme::text()));
                });
        });
    // Frames only while it fades in or out; in between it just waits.
    if (0.25..4.1).contains(&age) {
        ctx.request_repaint_after(std::time::Duration::from_secs_f32(4.1 - age));
    } else {
        ctx.request_repaint();
    }
}
