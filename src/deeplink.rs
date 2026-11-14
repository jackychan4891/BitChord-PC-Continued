//! BitChord party deep links: `bitchord://party/CODE` and the invite page
//! `https://bitchord.kushagrasingh.in/invite/CODE` (a relay typed in Settings replaces the
//! host and carries its own address in `?server=`).
//!
//! Clicking a link launches a new process with the URL as its argument. If another
//! instance is already running, the new one hands the link over a named pipe and
//! exits; the running instance queues it here and wakes the UI, which drains the
//! queue with `take()` (the same pattern as `tray.rs`).

use std::sync::Mutex;

use crate::jam::normalize_code as normalize;

/// A link: the party to join and, for a relay other than BitChord's, where that relay is.
#[derive(Clone, Debug, PartialEq, Eq)]
pub struct Link {
    pub code: String,
    pub server: Option<String>,
}

impl Link {
    /// The custom-scheme form, which is also what one instance sends another over the pipe.
    pub fn url(&self) -> String {
        match &self.server {
            Some(s) => format!("bitchord://party/{}?server={}", self.code, encode(s)),
            None => format!("bitchord://party/{}", self.code),
        }
    }
}

static PENDING: Mutex<Vec<Link>> = Mutex::new(Vec::new());
/// A plain later launch asked for the window (see `forward_show`).
static SHOW: std::sync::atomic::AtomicBool = std::sync::atomic::AtomicBool::new(false);
/// What a plain later launch sends instead of a link.
#[cfg_attr(not(windows), allow(dead_code))]
const SHOW_MSG: &str = "show";
/// The updater's relaunch passes this: the old copy may still be exiting, so don't hand over to it.
pub const RELAUNCH_FLAG: &str = "--relaunch";

/// The party in a launch argument, if it is a link. Accepted shapes:
/// `bitchord://party/CODE`, `bitchord://party?code=CODE` and `…/invite/CODE` over http(s),
/// each optionally carrying `?server=<relay>`.
pub fn parse(arg: &str) -> Option<Link> {
    let arg = arg.trim().trim_matches('"');
    let (scheme, rest) = arg.split_once("://")?;
    let rest = rest.split('#').next().unwrap_or("");
    let (path, query) = rest.split_once('?').unwrap_or((rest, ""));
    let server = query_param(query, "server")
        .map(decode)
        .map(|s| s.trim().trim_end_matches('/').to_owned())
        .filter(|s| !s.is_empty());
    if scheme.eq_ignore_ascii_case("bitchord") {
        // `party/CODE`, `party/CODE/`, or `party?code=CODE`.
        let path = path.trim_end_matches('/');
        let (host, code) = path.split_once('/').unwrap_or((path, ""));
        if !host.eq_ignore_ascii_case("party") || code.contains('/') {
            return None;
        }
        let code = if code.is_empty() { query_param(query, "code").unwrap_or_default() } else { code };
        return normalize(code).map(|code| Link { code, server });
    }
    if scheme.eq_ignore_ascii_case("https") || scheme.eq_ignore_ascii_case("http") {
        // The invite page: `…/invite/CODE`, in any case, with or without an index.
        let lower = path.to_ascii_lowercase();
        let lower = lower.trim_end_matches('/').trim_end_matches("/index.html");
        let code = lower.rsplit('/').next().filter(|_| lower.contains("/invite/"))?;
        return normalize(code).map(|code| Link { code, server });
    }
    None
}

fn query_param<'a>(query: &'a str, name: &str) -> Option<&'a str> {
    query.split('&').find_map(|kv| {
        let (k, v) = kv.split_once('=').unwrap_or((kv, ""));
        (k.eq_ignore_ascii_case(name)).then_some(v)
    })
}

/// Percent-encoding, just enough for a URL inside a query parameter.
fn encode(s: &str) -> String {
    let mut out = String::with_capacity(s.len());
    for b in s.as_bytes() {
        match b {
            b'A'..=b'Z' | b'a'..=b'z' | b'0'..=b'9' | b'-' | b'.' | b'_' | b'~' => out.push(*b as char),
            _ => out.push_str(&format!("%{b:02X}")),
        }
    }
    out
}

fn decode(s: &str) -> String {
    let bytes = s.as_bytes();
    /// The byte `%XX` at `i` stands for, if it is one.
    fn hex(bytes: &[u8], i: usize) -> Option<u8> {
        let esc = std::str::from_utf8(&bytes[i + 1..i + 3]).ok()?;
        u8::from_str_radix(esc, 16).ok()
    }
    let mut out = Vec::with_capacity(bytes.len());
    let mut i = 0;
    while i < bytes.len() {
        match (bytes[i], if i + 2 < bytes.len() { hex(bytes, i) } else { None }) {
            (b'%', Some(b)) => {
                out.push(b);
                i += 3;
            }
            _ => {
                out.push(bytes[i]);
                i += 1;
            }
        }
    }
    String::from_utf8_lossy(&out).into_owned()
}

/// The first party link among the process arguments.
pub fn from_args() -> Option<Link> {
    std::env::args_os().skip(1).find_map(|a| parse(&a.to_string_lossy()))
}

/// Queue a link for the UI (e.g. from the launch arguments).
pub fn push(link: Link) {
    PENDING.lock().unwrap().push(link);
}

/// Links received since the last call, oldest first.
pub fn take() -> Vec<Link> {
    std::mem::take(&mut *PENDING.lock().unwrap())
}

/// Whether a later launch asked for the window since the last call.
pub fn take_show() -> bool {
    SHOW.swap(false, std::sync::atomic::Ordering::Relaxed)
}

/// A plain launch while another copy runs: bring that one's window up instead, so only
/// one copy uses (and refreshes) the Google session. Two copies refreshing it apart
/// leave one with old cookies, which Google treats as stolen and signs out. True if
/// a running copy took it.
pub fn forward_show() -> bool {
    if std::env::args_os().any(|a| a == RELAUNCH_FLAG) {
        return false;
    }
    // Windows only lets a background process take the foreground when the one the user
    // just started allows it.
    #[cfg(windows)]
    unsafe {
        use windows::Win32::UI::WindowsAndMessaging::{ASFW_ANY, AllowSetForegroundWindow};
        let _ = AllowSetForegroundWindow(ASFW_ANY);
    }
    forward_to_running(SHOW_MSG)
}

/// Per-user pipe name. A dev profile (`YTM_DATA_DIR`) gets its own, so test runs
/// never talk to the installed copy.
#[cfg_attr(not(windows), allow(dead_code))]
fn pipe_name() -> String {
    let user: String = std::env::var("USERNAME")
        .unwrap_or_default()
        .chars()
        .map(|c| if c.is_ascii_alphanumeric() || c == '-' || c == '_' { c } else { '_' })
        .collect();
    let mut name = format!(r"\\.\pipe\bitchord-party-{user}");
    if let Some(dir) = std::env::var_os("YTM_DATA_DIR") {
        use std::hash::{Hash, Hasher};
        let mut h = std::collections::hash_map::DefaultHasher::new();
        dir.hash(&mut h);
        name.push_str(&format!("-{:016x}", h.finish()));
    }
    name
}

#[cfg(windows)]
pub use win::{forward_to_running, listen, register_protocol};

#[cfg(not(windows))]
pub fn forward_to_running(_msg: &str) -> bool {
    false
}

#[cfg(not(windows))]
pub fn listen(_wake: impl Fn() + Send + 'static) {}

#[cfg(not(windows))]
pub fn register_protocol() {}

#[cfg(windows)]
mod win {
    use std::io::Write;

    use windows::Win32::Foundation::{CloseHandle, ERROR_PIPE_CONNECTED, HANDLE};
    use windows::Win32::Storage::FileSystem::{FILE_FLAG_FIRST_PIPE_INSTANCE, PIPE_ACCESS_INBOUND, ReadFile};
    use windows::Win32::System::Pipes::{
        ConnectNamedPipe, CreateNamedPipeW, DisconnectNamedPipe, PIPE_READMODE_BYTE, PIPE_REJECT_REMOTE_CLIENTS, PIPE_TYPE_BYTE,
        PIPE_WAIT,
    };
    use windows::Win32::System::Registry::{HKEY, HKEY_CURRENT_USER, REG_SZ, RegSetKeyValueW};
    use windows::core::HSTRING;

    use super::{pipe_name, push};

    /// Hand `msg` (a link, or `show`) to a running instance. True if one took it.
    pub fn forward_to_running(msg: &str) -> bool {
        let name = pipe_name();
        // The server has one pipe instance; between clients it is briefly busy.
        for _ in 0..20 {
            match std::fs::OpenOptions::new().write(true).open(&name) {
                Ok(mut pipe) => {
                    return pipe.write_all(format!("{msg}\n").as_bytes()).and_then(|_| pipe.flush()).is_ok();
                }
                // ERROR_PIPE_BUSY: another client is mid-handoff.
                Err(e) if e.raw_os_error() == Some(231) => std::thread::sleep(std::time::Duration::from_millis(50)),
                // No server (ERROR_FILE_NOT_FOUND) or anything else: nobody to hand to.
                Err(_) => return false,
            }
        }
        false
    }

    struct Pipe(HANDLE);
    // SAFETY: a pipe handle may be used from any thread.
    unsafe impl Send for Pipe {}
    impl Drop for Pipe {
        fn drop(&mut self) {
            unsafe {
                let _ = CloseHandle(self.0);
            }
        }
    }

    /// Serve the pipe on a background thread: each link received is queued and
    /// `wake` called. Only the first instance gets the pipe; any later one (a plain
    /// second launch) does nothing here.
    pub fn listen(wake: impl Fn() + Send + 'static) {
        let name = pipe_name();
        let handle = unsafe {
            CreateNamedPipeW(
                &HSTRING::from(name.as_str()),
                PIPE_ACCESS_INBOUND | FILE_FLAG_FIRST_PIPE_INSTANCE,
                PIPE_TYPE_BYTE | PIPE_READMODE_BYTE | PIPE_WAIT | PIPE_REJECT_REMOTE_CLIENTS,
                1,
                0,
                512,
                0,
                None,
            )
        };
        if handle.is_invalid() {
            // ERROR_ACCESS_DENIED here means another instance already serves it.
            log::info!("party link pipe not served by this instance: {}", windows::core::Error::from_thread());
            return;
        }
        let pipe = Pipe(handle);
        let spawned = std::thread::Builder::new().name("deeplink".into()).spawn(move || serve(pipe, wake));
        if let Err(e) = spawned {
            log::warn!("party link listener: {e}");
        }
    }

    fn serve(pipe: Pipe, wake: impl Fn()) {
        loop {
            let connected = unsafe { ConnectNamedPipe(pipe.0, None) };
            if let Err(e) = connected {
                // A client that connected between create/disconnect and this call.
                if e.code() != ERROR_PIPE_CONNECTED.to_hresult() {
                    log::warn!("party link pipe: {e}");
                    std::thread::sleep(std::time::Duration::from_millis(200));
                    continue;
                }
            }
            let mut msg = Vec::new();
            let mut buf = [0u8; 64];
            loop {
                let mut n = 0u32;
                let ok = unsafe { ReadFile(pipe.0, Some(&mut buf), Some(&mut n), None) };
                if ok.is_err() || n == 0 {
                    break; // ERROR_BROKEN_PIPE: the client wrote and closed.
                }
                msg.extend_from_slice(&buf[..n as usize]);
                if msg.len() > 256 {
                    break;
                }
            }
            unsafe {
                let _ = DisconnectNamedPipe(pipe.0);
            }
            let msg = String::from_utf8_lossy(&msg).trim().to_owned();
            if msg == super::SHOW_MSG {
                log::info!("another launch asked for the window");
                super::SHOW.store(true, std::sync::atomic::Ordering::Relaxed);
                wake();
                continue;
            }
            match super::parse(&msg) {
                Some(link) => {
                    log::info!("party link received from another launch: {}", link.code);
                    push(link);
                    wake();
                }
                None => log::warn!("party link pipe: ignored an invalid message"),
            }
        }
    }

    /// Register the `bitchord:` URL protocol for this user, pointing at this exe.
    /// Call only from an installed copy, so dev builds don't take the protocol over.
    pub fn register_protocol() {
        match std::env::current_exe() {
            Ok(exe) => {
                if let Err(e) = register_at(HKEY_CURRENT_USER, r"Software\Classes\bitchord", &exe.to_string_lossy()) {
                    log::warn!("registering the bitchord: protocol: {e}");
                }
            }
            Err(e) => log::warn!("registering the bitchord: protocol: {e}"),
        }
    }

    pub(super) fn register_at(root: HKEY, key: &str, exe: &str) -> windows::core::Result<()> {
        let set = |sub: &str, name: &str, value: &str| -> windows::core::Result<()> {
            let path = if sub.is_empty() { key.to_string() } else { format!(r"{key}\{sub}") };
            let data: Vec<u16> = value.encode_utf16().chain(Some(0)).collect();
            let name = HSTRING::from(name);
            unsafe {
                RegSetKeyValueW(
                    root,
                    &HSTRING::from(path),
                    if name.is_empty() { windows::core::PCWSTR::null() } else { windows::core::PCWSTR(name.as_ptr()) },
                    REG_SZ.0,
                    Some(data.as_ptr().cast()),
                    (data.len() * 2) as u32,
                )
            }
            .ok()
        };
        set("", "", "URL:bitchord Protocol")?;
        set("", "URL Protocol", "")?;
        set("DefaultIcon", "", &format!("\"{exe}\",0"))?;
        set(r"shell\open\command", "", &format!("\"{exe}\" \"%1\""))
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn app_protocol() {
        assert_eq!(parse("bitchord://party/ABC234"), Some(Link { code: "ABC234".into(), server: None }));
        assert_eq!(parse("bitchord://party/abc234/"), Some(Link { code: "ABC234".into(), server: None }));
        assert_eq!(parse("BITCHORD://PARTY/aBc234"), Some(Link { code: "ABC234".into(), server: None }));
        assert_eq!(parse("\"bitchord://party/ABC234\""), Some(Link { code: "ABC234".into(), server: None }));
        assert_eq!(parse("bitchord://party?code=xyz789"), Some(Link { code: "XYZ789".into(), server: None }));
    }

    #[test]
    fn invite_pages() {
        let code = |l: &Link| l.code.clone();
        assert_eq!(code(&parse("https://bitchord.kushagrasingh.in/invite/ABC234").unwrap()), "ABC234");
        assert_eq!(code(&parse("https://bitchord.kushagrasingh.in/invite/abc234?x=1").unwrap()), "ABC234");
        assert_eq!(parse("https://bitchord.kushagrasingh.in/invite/abc234/#top").unwrap(), Link { code: "ABC234".into(), server: None });
        // A code read off a phone may hold 0, 1 or a look-alike letter.
        assert_eq!(code(&parse("https://bitchord.kushagrasingh.in/invite/AB0IL1").unwrap()), "AB0111");
    }

    #[test]
    fn a_relay_other_than_bitchords() {
        assert_eq!(
            parse("bitchord://party/ABC234?server=https%3A%2F%2Fapi.example.com%3A8443%2Fparty").unwrap(),
            Link { code: "ABC234".into(), server: Some("https://api.example.com:8443/party".into()) }
        );
        // The invite page a custom relay hands out names itself and its relay.
        assert_eq!(
            parse("https://example.com/invite/ABC234?server=api.example.com/").unwrap(),
            Link { code: "ABC234".into(), server: Some("api.example.com".into()) }
        );
        assert_eq!(parse("bitchord://party/ABC234?server=").unwrap().server, None);
    }

    #[test]
    fn a_link_survives_the_pipe() {
        for link in [
            Link { code: "ABC234".into(), server: None },
            Link { code: "XY01P1".into(), server: Some("https://api.example.com/party".into()) },
        ] {
            assert_eq!(parse(&link.url()).as_ref(), Some(&link));
        }
    }

    #[test]
    fn rejects() {
        for bad in [
            "",
            "%1",
            "ABC234",
            "bitchord://party/",
            "bitchord://party/ABC23",      // too short
            "bitchord://party/ABC2345",    // too long
            "bitchord://party/ABC234/xyz", // extra path
            "bitchord://room/ABC234",
            "bitchord://party?code=ABC",
            "https://example.com/invite/ABC2345",
            "https://example.com/join/ABC234",
            "https://bitchord.kushagrasingh.in/invite/",
            "https://grapedjuice.github.io/ytm-rs/jam/?c=ABC234",
            "ytm-rs://jam/ABC234",
            "--login",
        ] {
            assert_eq!(parse(bad), None, "{bad}");
        }
    }

    #[test]
    fn queue() {
        push(Link { code: "ABC234".into(), server: None });
        push(Link { code: "XYZ789".into(), server: Some("api.example.com".into()) });
        assert_eq!(
            take(),
            [Link { code: "ABC234".into(), server: None }, Link { code: "XYZ789".into(), server: Some("api.example.com".into()) }]
        );
        assert!(take().is_empty());
    }

    #[cfg(windows)]
    #[test]
    fn register_under_scratch_key() {
        use windows::Win32::System::Registry::{HKEY_CURRENT_USER, RRF_RT_REG_SZ, RegDeleteTreeW, RegGetValueW};
        use windows::core::HSTRING;
        let key = format!(r"Software\bitchord-deeplink-test-{}", std::process::id());
        win::register_at(HKEY_CURRENT_USER, &key, r"C:\x\bitchord.exe").unwrap();
        let read = |sub: &str, name: &str| -> String {
            let mut buf = [0u16; 256];
            let mut len = (buf.len() * 2) as u32;
            let path = if sub.is_empty() { key.clone() } else { format!(r"{key}\{sub}") };
            let r = unsafe {
                RegGetValueW(
                    HKEY_CURRENT_USER,
                    &HSTRING::from(path),
                    &HSTRING::from(name),
                    RRF_RT_REG_SZ,
                    None,
                    Some(buf.as_mut_ptr().cast()),
                    Some(&mut len),
                )
            };
            assert!(r.is_ok(), "{sub}\\{name}: {r:?}");
            String::from_utf16_lossy(&buf[..(len as usize / 2).saturating_sub(1)])
        };
        let results = (read("", ""), read("", "URL Protocol"), read("DefaultIcon", ""), read(r"shell\open\command", ""));
        unsafe {
            let _ = RegDeleteTreeW(HKEY_CURRENT_USER, &HSTRING::from(key.as_str()));
            let _ = windows::Win32::System::Registry::RegDeleteKeyW(HKEY_CURRENT_USER, &HSTRING::from(key.as_str()));
        }
        assert_eq!(results.0, "URL:bitchord Protocol");
        assert_eq!(results.1, "");
        assert_eq!(results.2, r#""C:\x\bitchord.exe",0"#);
        assert_eq!(results.3, r#""C:\x\bitchord.exe" "%1""#);
    }
}
