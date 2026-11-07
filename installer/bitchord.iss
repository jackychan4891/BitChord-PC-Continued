; Inno Setup script for BitChord.
; Build: cargo build --release, then
;   iscc installer\bitchord.iss
; Output: installer\Output\bitchord-setup-<version>.exe
; ARM64: cargo build --release --target aarch64-pc-windows-msvc, then
;   iscc /DArm64 installer\bitchord.iss  ->  bitchord-setup-<version>-arm64.exe

#define AppName "BitChord"
#define AppVersion "2.0.0"
#define AppExe "bitchord.exe"
#ifdef Arm64
  #define Arch "arm64"
  #define Suffix "-arm64"
  #define BinDir "..\target\aarch64-pc-windows-msvc\release"
#else
  #define Arch "x64compatible"
  #define Suffix ""
  #define BinDir "..\target\release"
#endif

[Setup]
AppId={{6E0B7C4A-2D1F-4C8E-9B3A-7F5D1E2A9C40}
AppName={#AppName}
AppVersion={#AppVersion}
AppPublisher=jackychan4891
AppPublisherURL=https://github.com/jackychan4891/BitChord-PC-Continued
AppSupportURL=https://github.com/jackychan4891/BitChord-PC-Continued/issues
DefaultDirName={autopf}\{#AppName}
DefaultGroupName={#AppName}
DisableProgramGroupPage=yes
; Per-user install by default (no UAC prompt); the user can pick all-users instead.
PrivilegesRequired=lowest
PrivilegesRequiredOverridesAllowed=dialog
ArchitecturesAllowed={#Arch}
ArchitecturesInstallIn64BitMode={#Arch}
MinVersion=10.0
OutputBaseFilename=bitchord-setup-{#AppVersion}{#Suffix}
Compression=lzma2/ultra
SolidCompression=yes
WizardStyle=modern
SetupIconFile=..\assets\icon\icon.ico
UninstallDisplayIcon={app}\{#AppExe}
CloseApplications=yes

[Tasks]
Name: "desktopicon"; Description: "{cm:CreateDesktopIcon}"; GroupDescription: "{cm:AdditionalIcons}"; Flags: unchecked

[Files]
Source: "{#BinDir}\{#AppExe}"; DestDir: "{app}"; Flags: ignoreversion
Source: "..\README.md"; DestDir: "{app}"; Flags: ignoreversion
Source: "..\LICENSE"; DestDir: "{app}"; Flags: ignoreversion

[Icons]
Name: "{group}\{#AppName}"; Filename: "{app}\{#AppExe}"
Name: "{group}\Uninstall {#AppName}"; Filename: "{uninstallexe}"
Name: "{autodesktop}\{#AppName}"; Filename: "{app}\{#AppExe}"; Tasks: desktopicon

[Registry]
; ytm-rs:// links (jam invites). HKA is HKCU for per-user installs, HKLM for all-users.
; The installed app also re-registers itself under HKCU on launch.
Root: HKA; Subkey: "Software\Classes\ytm-rs"; ValueType: string; ValueName: ""; ValueData: "URL:ytm-rs Protocol"; Flags: uninsdeletekey
Root: HKA; Subkey: "Software\Classes\ytm-rs"; ValueType: string; ValueName: "URL Protocol"; ValueData: ""
Root: HKA; Subkey: "Software\Classes\ytm-rs\DefaultIcon"; ValueType: string; ValueName: ""; ValueData: """{app}\{#AppExe}"",0"
Root: HKA; Subkey: "Software\Classes\ytm-rs\shell\open\command"; ValueType: string; ValueName: ""; ValueData: """{app}\{#AppExe}"" ""%1"""

[UninstallDelete]
; Installers downloaded by the in-app updater.
Type: filesandordirs; Name: "{userappdata}\ytm-rs\data\updates"

[Run]
Filename: "{app}\{#AppExe}"; Description: "{cm:LaunchProgram,{#AppName}}"; Flags: nowait postinstall skipifsilent
