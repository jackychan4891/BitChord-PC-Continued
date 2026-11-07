//! Romanization and translation of lyric lines, the way the better-lyrics extension gets
//! them (translation.ts): Unison's translate endpoint first, which answers both at once,
//! then Google Translate's keyless `gtx` endpoint for whatever is still missing. Lyrics
//! that ship their own (Apple-style TTML) use those instead; see the renderer.
//! Results are cached on disk per song, source and target language.

use std::path::{Path, PathBuf};
use std::time::Duration;

use serde::{Deserialize, Serialize};
use serde_json::Value;

const UNISON_TRANSLATE: &str = "https://unison.betterlyrics.org/translate";
/// Same API under two client IDs. Google sometimes answers the first with a 429
/// "automated queries" page for a while; the Chrome dictionary extension's client
/// keeps working when it does.
const ENDPOINTS: [(&str, &str); 2] = [
    ("https://translate.googleapis.com/translate_a/single", "gtx"),
    ("https://clients5.google.com/translate_a/single", "dict-chrome-ex"),
];
const BATCH_SEPARATOR: &str = "\n\n;\n\n";

/// Per-line extras, index-aligned with the lines they were asked for.
#[derive(Clone, Debug, Default, Serialize, Deserialize)]
pub struct Extras {
    pub roman: Vec<Option<String>>,
    pub trans: Vec<Option<String>>,
}

pub struct Client {
    http: reqwest::Client,
    dir: PathBuf,
}

impl Client {
    pub fn new(http: reqwest::Client, data_dir: &Path) -> Self {
        Self { http, dir: data_dir.join("translations") }
    }

    /// Romanization (non-Latin lines only) and translation into `target` for `lines`.
    /// `cache_key` names the song and source; `lang` is the language the lyrics say they're in.
    pub async fn extras(&self, cache_key: &str, lines: &[String], lang: Option<&str>, target: &str) -> Option<Extras> {
        let file = self.dir.join(format!("{}.json", safe_name(&format!("{cache_key}_{target}"))));
        if let Some(e) = std::fs::read(&file).ok().and_then(|b| serde_json::from_slice::<Extras>(&b).ok())
            && e.roman.len() == lines.len()
        {
            return Some(e);
        }
        // Distinct non-empty lines only; choruses repeat.
        let mut uniq: Vec<&str> = Vec::new();
        for l in lines.iter().map(|l| l.trim()).filter(|l| !l.is_empty() && *l != "♪") {
            if !uniq.contains(&l) {
                uniq.push(l);
            }
        }
        if uniq.is_empty() {
            return None;
        }
        let mut roman: Vec<Option<String>> = vec![None; uniq.len()];
        let mut trans: Vec<Option<String>> = vec![None; uniq.len()];
        let mut complete = true;

        match self.unison(&uniq, lang, target).await {
            Ok(rows) => {
                for (i, (t, r)) in rows.into_iter().enumerate() {
                    if let Some(t) = t.filter(|t| !t.eq_ignore_ascii_case(uniq[i])) {
                        trans[i] = Some(t);
                    }
                    if let Some(r) = r.filter(|r| non_latin(uniq[i]) && !r.eq_ignore_ascii_case(uniq[i])) {
                        roman[i] = Some(r);
                    }
                }
            }
            Err(e) => log::info!("unison translate: {e:#}"),
        }

        // Google for the rest.
        let need_roman: Vec<usize> = (0..uniq.len()).filter(|&i| roman[i].is_none() && non_latin(uniq[i])).collect();
        if !need_roman.is_empty() {
            let texts: Vec<&str> = need_roman.iter().map(|&i| uniq[i]).collect();
            let rl = romanization_language(lang, &texts);
            match self.romanize(&texts, &rl).await {
                Ok(out) => {
                    for (k, &i) in need_roman.iter().enumerate() {
                        roman[i] = out.get(k).cloned().flatten().filter(|r| !r.eq_ignore_ascii_case(uniq[i]));
                    }
                }
                Err(e) => {
                    complete = false;
                    log::warn!("romanization failed: {e:#}");
                }
            }
        }
        let need_trans: Vec<usize> = (0..uniq.len()).filter(|&i| trans[i].is_none()).collect();
        if !need_trans.is_empty() && lang.is_none_or(|l| !same_lang(l, target) || need_trans.iter().any(|&i| non_latin(uniq[i]))) {
            // Non-Latin lines first, like the extension, so one language leads detection.
            let mut order = need_trans.clone();
            order.sort_by_key(|&i| !non_latin(uniq[i]));
            let texts: Vec<&str> = order.iter().map(|&i| uniq[i]).collect();
            match self.translate(&texts, target).await {
                Ok(out) => {
                    for (k, &i) in order.iter().enumerate() {
                        trans[i] = out.get(k).cloned().flatten().filter(|t| !t.eq_ignore_ascii_case(uniq[i]));
                    }
                }
                Err(e) => {
                    complete = false;
                    log::warn!("translation failed: {e:#}");
                }
            }
        }

        let pick = |table: &[Option<String>], l: &str| uniq.iter().position(|u| *u == l.trim()).and_then(|i| table[i].clone());
        let e = Extras { roman: lines.iter().map(|l| pick(&roman, l)).collect(), trans: lines.iter().map(|l| pick(&trans, l)).collect() };
        if complete {
            let _ = std::fs::create_dir_all(&self.dir);
            let _ = std::fs::write(&file, serde_json::to_vec(&e).unwrap_or_default());
        }
        Some(e)
    }

    /// Unison answers translation and romanization for every line in one request.
    async fn unison(&self, lines: &[&str], lang: Option<&str>, target: &str) -> anyhow::Result<Vec<(Option<String>, Option<String>)>> {
        let mut body = serde_json::json!({ "lines": lines, "to": target });
        if let Some(l) = lang {
            body["from"] = l.into();
        }
        let v: Value = self.http.post(UNISON_TRANSLATE).json(&body).timeout(Duration::from_secs(15)).send().await?.error_for_status()?.json().await?;
        let rows = v["lines"].as_array().ok_or_else(|| anyhow::anyhow!("{}", v["error"].as_str().unwrap_or("no lines")))?;
        anyhow::ensure!(rows.len() == lines.len(), "unison answered {} lines for {}", rows.len(), lines.len());
        Ok(rows
            .iter()
            .map(|r| {
                let t = r["translation"].as_str().filter(|_| r["needsTranslation"].as_bool().unwrap_or(true)).map(|s| s.trim().to_owned()).filter(|s| !s.is_empty());
                let ro = r["romanization"].as_str().map(|s| s.trim().to_owned()).filter(|s| !s.is_empty());
                (t, ro)
            })
            .collect())
    }

    async fn call(&self, query: &[(&str, &str)], text: &str) -> anyhow::Result<Value> {
        let mut last = None;
        for (url, client) in ENDPOINTS {
            let mut q = vec![("client", client)];
            q.extend_from_slice(query);
            let resp = self.http.post(url).query(&q).form(&[("q", text)]).timeout(Duration::from_secs(15)).send().await?;
            match resp.error_for_status() {
                Ok(r) => return Ok(r.json().await?),
                Err(e) => last = Some(e),
            }
        }
        Err(last.expect("at least one endpoint").into())
    }

    /// Romanization comes back as one run-on string, so lines are joined with a numeric
    /// separator that survives (`romanizeBatch`).
    async fn romanize(&self, lines: &[&str], lang: &str) -> anyhow::Result<Vec<Option<String>>> {
        let mut sep = "0000".to_owned();
        while lines.iter().any(|l| l.contains(&sep)) {
            sep.push('0');
        }
        let latn = format!("{lang}-Latn");
        let v = self.call(&[("sl", lang), ("tl", &latn), ("dt", "t"), ("dt", "rm")], &lines.join(&format!(" {sep} "))).await?;
        let mut full = String::new();
        for part in v[0].as_array().into_iter().flatten() {
            if let Some(r) = part[3].as_str().or_else(|| part[2].as_str()) {
                full.push_str(r);
            }
        }
        let out: Vec<Option<String>> = full.split(sep.as_str()).map(|s| Some(s.trim().to_owned()).filter(|s| !s.is_empty())).collect();
        anyhow::ensure!(out.len() == lines.len(), "romanization split into {} parts, expected {}", out.len(), lines.len());
        Ok(out)
    }

    /// `translateBatch`'s Google pass: lines joined with a separator Google keeps.
    async fn translate(&self, lines: &[&str], target: &str) -> anyhow::Result<Vec<Option<String>>> {
        let v = self.call(&[("sl", "auto"), ("tl", target), ("dt", "t")], &lines.join(BATCH_SEPARATOR)).await?;
        let text: String = v[0].as_array().into_iter().flatten().filter_map(|s| s[0].as_str()).collect();
        let mut out: Vec<String> = text.split(BATCH_SEPARATOR).map(str::to_owned).collect();
        if out.len() < lines.len() {
            let semi: Vec<String> = text.split(';').filter(|l| !l.trim().is_empty()).map(str::to_owned).collect();
            let nl: Vec<String> = text.lines().filter(|l| !l.trim().is_empty()).map(str::to_owned).collect();
            if semi.len() == lines.len() {
                out = semi;
            } else if nl.len() == lines.len() {
                out = nl;
            } else if out.len() == 1 && lines.len() > 1 {
                anyhow::bail!("translation failed to split: expected {}, got 1", lines.len());
            }
        }
        Ok((0..lines.len()).map(|i| out.get(i).map(|s| s.trim().to_owned()).filter(|s| !s.is_empty())).collect())
    }
}

fn safe_name(s: &str) -> String {
    s.chars().map(|c| if c.is_ascii_alphanumeric() || matches!(c, '-' | '_') { c } else { '_' }).collect()
}

fn same_lang(a: &str, b: &str) -> bool {
    let base = |s: &str| s.split(['-', '_']).next().unwrap_or_default().to_ascii_lowercase();
    base(a) == base(b)
}

/// Languages Google romanizes (`ROMANIZATION_LANGUAGES`).
const ROMANIZATION_LANGUAGES: &[&str] = &["ja", "ko", "zh", "ru", "hi", "ar", "th", "el", "he", "bn", "ta", "te", "ml", "kn", "gu", "pa", "si", "my", "ka", "km", "lo", "uk", "bg", "sr", "fa", "ur"];

/// `resolveRomanizationLanguage`: the lyrics' language if Google romanizes it, else
/// the script most lines are written in.
fn romanization_language(lang: Option<&str>, lines: &[&str]) -> String {
    if let Some(l) = lang.filter(|l| ROMANIZATION_LANGUAGES.iter().any(|r| same_lang(l, r))) {
        return l.to_owned();
    }
    let mut tally: Vec<(&str, usize)> = Vec::new();
    for l in lines {
        if let Some(d) = script_language(l) {
            match tally.iter_mut().find(|(k, _)| *k == d) {
                Some((_, n)) => *n += 1,
                None => tally.push((d, 1)),
            }
        }
    }
    tally.into_iter().max_by_key(|(_, n)| *n).map_or("auto".to_owned(), |(k, _)| k.to_owned())
}

/// `detectNonLatinLanguage`: the language a script points to, in the same order.
#[allow(clippy::single_range_in_vec_init)]
pub fn script_language(s: &str) -> Option<&'static str> {
    let has = |r: &[std::ops::RangeInclusive<u32>]| s.chars().any(|c| r.iter().any(|r| r.contains(&(c as u32))));
    let table: &[(&[std::ops::RangeInclusive<u32>], &str)] = &[
        (&[0x3040..=0x309F, 0x30A0..=0x30FF, 0x31F0..=0x31FF, 0xFF66..=0xFF9F], "ja"),
        (&[0xAC00..=0xD7AF, 0x1100..=0x11FF, 0x3130..=0x318F], "ko"),
        (&[0x4E00..=0x9FFF, 0x3400..=0x4DBF, 0xF900..=0xFAFF, 0x20000..=0x2A6DF], "zh"),
        (&[0x0400..=0x04FF, 0x0500..=0x052F], "ru"),
        (&[0x0900..=0x097F], "hi"),
        (&[0x0600..=0x06FF, 0x0750..=0x077F], "ar"),
        (&[0x0E00..=0x0E7F], "th"),
        (&[0x0370..=0x03FF], "el"),
        (&[0x0590..=0x05FF], "he"),
        (&[0x0980..=0x09FF], "bn"),
        (&[0x0B80..=0x0BFF], "ta"),
        (&[0x0C00..=0x0C7F], "te"),
        (&[0x0D00..=0x0D7F], "ml"),
        (&[0x0C80..=0x0CFF], "kn"),
        (&[0x0A80..=0x0AFF], "gu"),
        (&[0x0A00..=0x0A7F], "pa"),
        (&[0x0D80..=0x0DFF], "si"),
        (&[0x1000..=0x109F], "my"),
        (&[0x10A0..=0x10FF], "ka"),
        (&[0x1780..=0x17FF], "km"),
        (&[0x0E80..=0x0EFF], "lo"),
    ];
    table.iter().find(|(r, _)| has(r)).map(|(_, l)| *l)
}

/// Whether a line has letters outside the Latin script (kana, hangul, Cyrillic, ...).
pub fn non_latin(s: &str) -> bool {
    s.chars().any(|c| c.is_alphabetic() && !matches!(c as u32, 0..=0x024F | 0x1E00..=0x1EFF | 0x2C60..=0x2C7F | 0xA720..=0xA7FF | 0xFF21..=0xFF5A))
}
