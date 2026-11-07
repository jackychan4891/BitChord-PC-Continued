//! Handwritten artist signatures, from the listener's own signature service.
//!
//! Nothing is asked of anywhere until a link is typed into Settings. The contract is
//! the one BitChord's desktop build has always used: `{artist}` in the link is
//! replaced with the encoded name, a bare host gets `/v1/signatures`, and anything
//! else has `artist=…&type=handwritten` appended.

use serde_json::Value;
use unicode_normalization::UnicodeNormalization;

/// A signature ready to be drawn: pixels whitened, alpha kept, so it reads as ink
/// over the artist's banner.
pub struct Signature {
    pub width: usize,
    pub height: usize,
    pub rgba: Vec<u8>,
}

/// The first address in whatever was pasted, or nothing at all.
///
/// A link often arrives out of a chat message with its prose still attached, so the
/// field is read for the address rather than taken whole.
pub fn endpoint(raw: &str) -> String {
    let Some(start) = raw.find("http") else { return String::new() };
    let rest = &raw[start..];
    if !(rest.starts_with("http://") || rest.starts_with("https://")) {
        return String::new();
    }
    let end = rest.find(|c: char| c.is_whitespace() || matches!(c, '<' | '>' | '"' | '\'' | '\\')).unwrap_or(rest.len());
    rest[..end].trim_end_matches(['/', '.', ',', ';', ':']).to_owned()
}

/// The request for one artist against `endpoint`, with `encoded` already encoded.
pub fn request_url(endpoint: &str, encoded: &str) -> String {
    let path = endpoint.split_once("://").map_or(endpoint, |(_, rest)| rest);
    let path = path.split('?').next().unwrap_or(path).trim_end_matches('/');
    if endpoint.contains("{artist}") {
        endpoint.replace("{artist}", encoded)
    } else if !path.contains('/') {
        format!("{}/v1/signatures?artist={encoded}&type=handwritten", endpoint.trim_end_matches('/'))
    } else {
        let sep = if endpoint.contains('?') { '&' } else { '?' };
        format!("{endpoint}{sep}artist={encoded}&type=handwritten")
    }
}

/// A name reduced to what two spellings of it share: no accents, case or punctuation.
pub fn key(name: &str) -> String {
    name.nfd()
        .filter(|c| !unicode_normalization::char::is_combining_mark(*c))
        .flat_map(char::to_lowercase)
        .filter(|c| c.is_alphanumeric())
        .collect()
}

/// Looks one artist up and returns their signature, if the service has a drawable one.
pub async fn lookup(http: &reqwest::Client, endpoint: &str, name: &str) -> Option<Signature> {
    if endpoint.is_empty() || key(name).is_empty() {
        return None;
    }
    let encoded: String = url::form_urlencoded::byte_serialize(name.as_bytes()).collect();
    let url = request_url(endpoint, &encoded);
    let body = match http.get(&url).send().await.and_then(|r| r.error_for_status()) {
        Ok(r) => r.bytes().await.ok()?,
        Err(e) => {
            log::debug!("artist signature: the service did not answer ({e})");
            return None;
        }
    };
    let root: Value = match serde_json::from_slice(&body) {
        Ok(v) => v,
        Err(e) => {
            log::debug!("artist signature: the answer is not JSON ({e})");
            return None;
        }
    };
    let asset = match pick_url(&root) {
        Some(a) => a,
        None => {
            log::debug!("artist signature: nothing in the answer for '{name}' can be drawn");
            return None;
        }
    };
    let bytes = match http.get(&asset).send().await.and_then(|r| r.error_for_status()) {
        Ok(r) => r.bytes().await.ok()?,
        Err(e) => {
            log::debug!("artist signature: the asset did not download ({e})");
            return None;
        }
    };
    let img = match image::load_from_memory(&bytes) {
        Ok(i) => i.to_rgba8(),
        Err(e) => {
            log::debug!("artist signature: the asset is not an image ({e})");
            return None;
        }
    };
    // A 960 px Wikimedia raster is more than the header ever draws: cap it so a
    // handful of visited artists can't hold a page's worth of textures.
    let img = if img.width() > 720 {
        let h = (u32::from(img.height()) * 720 / img.width()).max(1) as u32;
        image::imageops::resize(&img, 720, h, image::imageops::FilterType::Lanczos3)
    } else {
        img
    };
    Some(ink(&img))
}

/// White pixels carrying their own shape in alpha: a signature is ink, not a picture.
///
/// Most services send transparent PNGs, where alpha is the ink. One that flattens the
/// signature onto white paper has no transparency to trust, so its darkness is taken
/// as the ink instead.
fn ink(img: &image::RgbaImage) -> Signature {
    let mut rgba = img.as_raw().clone();
    let transparent = rgba.chunks_exact(4).any(|p| p[3] < 250);
    for p in rgba.chunks_exact_mut(4) {
        let a = if transparent {
            p[3]
        } else {
            let luma = (u32::from(p[0]) * 299 + u32::from(p[1]) * 587 + u32::from(p[2]) * 114) / 1000;
            (255 - luma) as u8
        };
        p.copy_from_slice(&[255, 255, 255, a]);
    }
    Signature { width: img.width() as usize, height: img.height() as usize, rgba }
}

/// The best transparent signature in an answer: a known licence beats a review, an
/// SVG beats a PNG, a verified hand beats an unverified one, and a wide mark suits
/// the header. Ties go to the larger artwork.
pub fn pick_url(root: &Value) -> Option<String> {
    let signatures = root.get("signatures")?.as_array()?;
    let mut best: Option<(u32, u64, String)> = None;
    for s in signatures {
        let Some(asset) = s.get("asset") else { continue };
        let Some(url) = asset.get("url").and_then(Value::as_str).filter(|u| u.starts_with("https://")) else { continue };
        let format = asset.get("format").and_then(Value::as_str).map(str::to_ascii_lowercase);
        let format_score = match format.as_deref() {
            Some("svg") => 2,
            Some("png") => 1,
            _ => continue,
        };
        let license = match s.pointer("/license/status").and_then(Value::as_str) {
            Some("known") => 2,
            Some("requires_review") => 1,
            _ => 0,
        };
        let verified = match s.get("verification").and_then(Value::as_str) {
            Some("artist_verified") => 3,
            Some("verified") => 2,
            Some("unverified") => 1,
            _ => 0,
        };
        let width = asset.get("width").and_then(Value::as_u64).unwrap_or(0);
        let height = asset.get("height").and_then(Value::as_u64).unwrap_or(0);
        let panoramic = u32::from(height > 0 && width * 10 >= height * 14);
        let score = license * 1000 + format_score * 100 + verified * 10 + panoramic;
        let Some(raster) = (if format.as_deref() == Some("svg") { wikimedia_raster(url) } else { Some(url.split('?').next().unwrap_or(url).to_owned()) }) else { continue };
        let area = width.saturating_mul(height);
        if best.as_ref().is_none_or(|(s, a, _)| (score, area) > (*s, *a)) {
            best = Some((score, area, raster));
        }
    }
    best.map(|(_, _, url)| url)
}

/// Wikimedia renders SVGs to PNG thumbnails, but only under `/thumb/`.
pub fn wikimedia_raster(url: &str) -> Option<String> {
    let path = url.split('?').next().unwrap_or(url);
    let prefix = "https://upload.wikimedia.org/wikipedia/commons/";
    let rel = path.strip_prefix(prefix)?;
    if rel.starts_with("thumb/") {
        return None;
    }
    let file = rel.rsplit('/').next()?;
    Some(format!("{prefix}thumb/{rel}/960px-{file}.png"))
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn a_pasted_sentence_leaves_its_address() {
        assert_eq!(endpoint("try this https://sig.example.net/api, thanks!"), "https://sig.example.net/api");
        assert_eq!(endpoint("https://sig.example.net/."), "https://sig.example.net");
        assert_eq!(endpoint("no link here"), "");
    }

    #[test]
    fn requests_follow_the_three_shapes() {
        assert_eq!(request_url("https://sig.example.net/api/{artist}", "Slay%20yyyter"), "https://sig.example.net/api/Slay%20yyyter");
        assert_eq!(request_url("https://sig.example.net", "A"), "https://sig.example.net/v1/signatures?artist=A&type=handwritten");
        assert_eq!(request_url("https://sig.example.net/api?x=1", "A"), "https://sig.example.net/api?x=1&artist=A&type=handwritten");
    }

    #[test]
    fn keys_ignore_accents_case_and_punctuation() {
        assert_eq!(key("Beyoncé"), "beyonce");
        assert_eq!(key("Tyler, The Creator"), "tylerthecreator");
    }

    #[test]
    fn the_best_drawable_signature_wins() {
        let root = serde_json::json!({"signatures": [
            {"asset": {"url": "https://x/a.png", "format": "png", "width": 100, "height": 40}, "license": {"status": "known"}, "verification": "verified"},
            {"asset": {"url": "https://x/b.svg", "format": "svg", "width": 100, "height": 40}, "license": {"status": "requires_review"}, "verification": "unverified"},
            {"asset": {"url": "https://x/c.jpg", "format": "jpeg", "width": 999, "height": 400}, "license": {"status": "known"}, "verification": "artist_verified"}
        ]});
        // The known-licence PNG outranks the review-pending SVG; the JPEG is skipped.
        assert_eq!(pick_url(&root).as_deref(), Some("https://x/a.png"));
    }

    #[test]
    fn wikimedia_marks_get_a_thumbnail_url() {
        assert_eq!(
            wikimedia_raster("https://upload.wikimedia.org/wikipedia/commons/a/ab/Signature.svg").as_deref(),
            Some("https://upload.wikimedia.org/wikipedia/commons/thumb/a/ab/Signature.svg/960px-Signature.svg.png"),
        );
        assert_eq!(wikimedia_raster("https://example.com/Signature.svg"), None);
    }

    #[test]
    fn a_transparent_mark_keeps_its_alpha() {
        let img = image::RgbaImage::from_pixel(2, 1, image::Rgba([12, 30, 200, 128]));
        let s = ink(&img);
        assert_eq!(s.rgba, vec![255, 255, 255, 128, 255, 255, 255, 128]);
    }

    #[test]
    fn a_scan_on_paper_takes_its_darkness_as_ink() {
        let dark = ink(&image::RgbaImage::from_pixel(1, 1, image::Rgba([0, 0, 0, 255])));
        assert_eq!(dark.rgba, vec![255, 255, 255, 255]);
        let paper = ink(&image::RgbaImage::from_pixel(1, 1, image::Rgba([255, 255, 255, 255])));
        assert_eq!(paper.rgba, vec![255, 255, 255, 0]);
    }
}
