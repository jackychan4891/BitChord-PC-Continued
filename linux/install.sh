#!/bin/sh
# Install bitchord for this user: the app in ~/.local/lib/bitchord, a `bitchord` command in
# ~/.local/bin, and a menu entry with its icon. Installed this way, the app updates
# itself. Libraries it needs that are missing get installed with the system's package
# manager (that step asks for your password).
#
# Run it from the extracted download.
# `install.sh --uninstall` removes it again.
set -e

data=${XDG_DATA_HOME:-$HOME/.local/share}
lib=$HOME/.local/lib/bitchord
bin=$HOME/.local/bin
desktop=$data/applications/bitchord.desktop
icon=$data/icons/hicolor/256x256/apps/bitchord.png

refresh() {
    update-desktop-database -q "$data/applications" 2>/dev/null || true
    gtk-update-icon-cache -q -t "$data/icons/hicolor" 2>/dev/null || true
}

if [ "$1" = "--uninstall" ]; then
    rm -rf "$lib"
    rm -f "$desktop" "$icon"
    [ -L "$bin/bitchord" ] && rm -f "$bin/bitchord"
    refresh
    echo "bitchord is uninstalled. Settings are still in $data/ytm-rs, and sign-ins in your keyring."
    exit 0
fi

# The files to install: beside this script, or (run from the web) the latest release.
here=$(cd "$(dirname "$0")" && pwd)
[ -f "$here/bitchord" ] || { echo "Run install.sh from the extracted download."; exit 1; }

mkdir -p "$lib" "$bin" "$data/applications" "$(dirname "$icon")"
# Put the new binary next to the old one and rename it over, so a copy that's
# running keeps going.
cp "$here/bitchord" "$lib/bitchord.new"
chmod 755 "$lib/bitchord.new"
mv -f "$lib/bitchord.new" "$lib/bitchord"
cp "$here/install.sh" "$lib/install.sh"
chmod 755 "$lib/install.sh"
echo "Installed by install.sh. This file lets bitchord update itself." > "$lib/install.txt"
ln -sf "$lib/bitchord" "$bin/bitchord"
sed "s|^Exec=.*|Exec=$lib/bitchord|" "$here/bitchord.desktop" > "$desktop"
cp "$here/bitchord.png" "$icon"
refresh

# Libraries: WebKitGTK (sign-in, lyrics), GTK, the tray's appindicator and ALSA.
missing=$(ldd "$lib/bitchord" 2>/dev/null | awk '/not found/ { print $1 }' | tr '\n' ' ')
# (Loaded at run time, so ldd doesn't list it. ldconfig is in /sbin, off a user's PATH on Debian.)
ldconfig=$(command -v ldconfig || echo /sbin/ldconfig)
appindicator=$("$ldconfig" -p 2>/dev/null | grep -c 'libayatana-appindicator3\|libappindicator3' || true)
if [ -n "$missing" ] || [ "$appindicator" = 0 ]; then
    sudo=""
    [ "$(id -u)" = 0 ] || sudo=sudo
    echo "Installing the libraries bitchord needs${missing:+ ($missing)}"
    if command -v apt-get >/dev/null; then
        $sudo apt-get install -y libwebkit2gtk-4.1-0 libgtk-3-0 libayatana-appindicator3-1 libasound2
    elif command -v dnf >/dev/null; then
        $sudo dnf install -y webkit2gtk4.1 gtk3 libayatana-appindicator-gtk3 alsa-lib
    elif command -v pacman >/dev/null; then
        $sudo pacman -S --needed --noconfirm webkit2gtk-4.1 gtk3 libayatana-appindicator alsa-lib
    elif command -v zypper >/dev/null; then
        $sudo zypper --non-interactive install libwebkit2gtk-4_1-0 libgtk-3-0 libayatana-appindicator3-1 alsa
    else
        echo "Install these with your package manager: $missing"
    fi
fi

echo "bitchord is installed. Open BitChord from your app menu, or run bitchord."
echo "To uninstall: $lib/install.sh --uninstall"
