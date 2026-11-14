//! yt-dlp's Windows JS-runtime discovery calls Python's `os.path.realpath` on
//! every PATH directory. An unrelated junction can raise WinError 448 (untrusted
//! mount point) and abort stream resolution. Resolve the child's PATH here so
//! usable junctions become direct paths and inaccessible entries are skipped.

use std::env;
use std::ffi::{OsStr, OsString};
use std::io;
use std::path::{Path, PathBuf};

pub fn resolve(path: &OsStr) -> Result<OsString, env::JoinPathsError> {
    resolve_with(path, |dir| std::fs::canonicalize(dir))
}

fn resolve_with(path: &OsStr, mut canonicalize: impl FnMut(&Path) -> io::Result<PathBuf>) -> Result<OsString, env::JoinPathsError> {
    // Keep the original search order. yt-dlp already ignores empty PATH entries
    // and searches its own directory and the working directory separately.
    env::join_paths(env::split_paths(path).filter(|dir| !dir.as_os_str().is_empty()).filter_map(|dir| canonicalize(&dir).ok()))
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn untrusted_mount_does_not_hide_later_runtime_directories() {
        let system = PathBuf::from(r"C:\Windows\System32");
        let untrusted = PathBuf::from(r"C:\Users\test\AppData\Local\Programs\OpenAI\Codex\bin");
        let node = PathBuf::from(r"C:\Program Files\nodejs");
        let bun = PathBuf::from(r"C:\tools\bun");
        let path = env::join_paths([&system, &untrusted, &node, &bun]).unwrap();
        let resolved = resolve_with(&path, |dir| if dir == untrusted { Err(io::Error::from_raw_os_error(448)) } else { Ok(dir.to_path_buf()) }).unwrap();

        assert_eq!(env::split_paths(&resolved).collect::<Vec<_>>(), [system, node, bun]);
    }

    #[test]
    fn usable_junction_is_replaced_with_its_target() {
        let alias = PathBuf::from(r"C:\nvm4w\nodejs");
        let target = PathBuf::from(r"C:\Users\test\AppData\Local\nvm\v23.6.1");
        let path = env::join_paths([&alias]).unwrap();
        let resolved = resolve_with(&path, |dir| {
            assert_eq!(dir, alias);
            Ok(target.clone())
        })
        .unwrap();

        assert_eq!(env::split_paths(&resolved).collect::<Vec<_>>(), [target]);
    }

    #[test]
    fn empty_and_missing_entries_do_not_abort_resolution() {
        let missing = PathBuf::from(r"C:\missing");
        let node = PathBuf::from(r"C:\nodejs");
        let path = env::join_paths([Path::new(""), &missing, &node]).unwrap();
        let resolved = resolve_with(&path, |dir| {
            assert!(!dir.as_os_str().is_empty());
            if dir == missing { Err(io::Error::from(io::ErrorKind::NotFound)) } else { Ok(dir.to_path_buf()) }
        })
        .unwrap();

        assert_eq!(env::split_paths(&resolved).collect::<Vec<_>>(), [node]);
    }

    #[test]
    fn all_inaccessible_entries_produce_an_empty_path() {
        let path = env::join_paths([r"C:\untrusted", r"C:\missing"]).unwrap();
        let resolved = resolve_with(&path, |_| Err(io::Error::from_raw_os_error(448))).unwrap();

        assert!(resolved.is_empty());
    }

    #[test]
    fn current_directory_is_resolved_without_changing_the_parent_path() {
        let parent_path = env::var_os("PATH");
        let resolved = resolve(OsStr::new(".")).unwrap();

        assert_eq!(env::split_paths(&resolved).collect::<Vec<_>>(), [std::fs::canonicalize(".").unwrap()]);
        assert_eq!(env::var_os("PATH"), parent_path);
    }
}
