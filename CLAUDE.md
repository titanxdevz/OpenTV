# CleanLauncher — Agent Handbook

> Read this before touching any file. It tells you exactly what this project is,
> what the TV is, how to push code, and what still needs fixing.

---

## 1. Project Overview

A custom Android TV launcher for the **Realme AI Point** (model `AI_PONT`, device `ikebukuro`,
manufacturer Changhong/Homwee, Android 11, SDK 30).

**Goal:** Replace the bloated stock Google TV launcher with a clean, Apple TV 4K–style home screen.
Inspired by the open-source [CleanYourTV launcher](https://github.com/Gathin23/cleanyourtv/tree/main/launcher).

**Package:** `com.clean.launcher`  
**Project root:** `C:\Users\acer\AtvLauncher\`  
**APK output:** `app\build\outputs\apk\debug\app-debug.apk`

---

## 2. TV Device Details

| Field | Value |
|---|---|
| Model | Realme AI Point |
| Android codename | `ikebukuro` |
| Product | `DVB91RM` |
| Android version | 11 (SDK 30) |
| RAM | ~1 GB |
| Resolution | 1280×720 |
| Last known IP | `10.196.79.191` (changes — check ADB TV app on TV) |
| ADB port | `5555` |

### How to get the TV IP
The TV IP changes when it reconnects to WiFi. To find the current IP:
1. TV → open the **"ADB TV"** app (installed from Play Store)
2. The app shows the current IP:port
3. OR: TV Settings → Device Preferences → About → Status → IP address

### How to enable ADB on this TV
This TV does **not** have "ADB over network" in Developer Options — only "USB debugging".
The only way to enable network ADB is via the **"ADB TV"** app from Play Store.
1. Open ADB TV app on TV
2. It opens an HTTP interface at `http://<TV_IP>:8080/<token>`
3. This enables port 5555 — then `adb connect <TV_IP>:5555`

---

## 3. How to Connect (ADB)

```powershell
$adb = "$env:USERPROFILE\platform-tools\adb.exe"

# Reset server first if needed
& $adb kill-server; Start-Sleep -Seconds 2; & $adb start-server

# Connect (replace IP if changed)
& $adb connect 10.196.79.191:5555

# Verify — should show "device product:DVB91RM"
& $adb devices -l
```

**First connection:** TV will show "Allow USB debugging?" popup — user must tap Allow/OK.

**If "unauthorized":** User needs to tap Allow on TV screen.

**If timeout (10060):** ADB not enabled. User needs to open ADB TV app on TV first.

**If connection refused (10061):** Port closed. TV rebooted and ADB app needs to be reopened.

---

## 4. How to Build

```powershell
cd "$env:USERPROFILE\AtvLauncher"
$env:ANDROID_HOME = "$env:USERPROFILE\android-sdk"
$env:JAVA_HOME    = "C:\Program Files\Eclipse Adoptium\jdk-17.0.20.101-hotspot"

.\gradlew.bat assembleDebug --no-daemon
```

**Prerequisites already installed:**
- JDK 17 at `C:\Program Files\Eclipse Adoptium\jdk-17.0.20.101-hotspot`
- Android SDK at `C:\Users\acer\android-sdk`
- Platform-tools (adb) at `C:\Users\acer\platform-tools\`
- Outfit font TTFs at `app\src\main\res\font\` (outfit_light.ttf, outfit_regular.ttf)

---

## 5. How to Push & Install

```powershell
$adb = "$env:USERPROFILE\platform-tools\adb.exe"

# Push APK to TV temp storage
& $adb push "app\build\outputs\apk\debug\app-debug.apk" /data/local/tmp/launcher.apk

# Install (the -r flag replaces existing install)
& $adb shell pm install -r /data/local/tmp/launcher.apk

# Press HOME to launch
& $adb shell input keyevent KEYCODE_HOME
```

**One-liner build + push:**
```powershell
cd "$env:USERPROFILE\AtvLauncher"
$env:ANDROID_HOME = "$env:USERPROFILE\android-sdk"
$env:JAVA_HOME    = "C:\Program Files\Eclipse Adoptium\jdk-17.0.20.101-hotspot"
$adb = "$env:USERPROFILE\platform-tools\adb.exe"
.\gradlew.bat assembleDebug --no-daemon && & $adb push "app\build\outputs\apk\debug\app-debug.apk" /data/local/tmp/launcher.apk && & $adb shell pm install -r /data/local/tmp/launcher.apk && & $adb shell input keyevent KEYCODE_HOME
```

---

## 6. How to Take a Screenshot

```powershell
$adb = "$env:USERPROFILE\platform-tools\adb.exe"
& $adb shell screencap -p /sdcard/screen.png
& $adb pull /sdcard/screen.png "$env:USERPROFILE\screen.png"
# Then read the file to view it
```

---

## 7. How to Check Logs

```powershell
$adb = "$env:USERPROFILE\platform-tools\adb.exe"
& $adb logcat -d -s "CleanLauncher" 2>&1 | Select-Object -Last 50
```

---

## 8. Project Structure

```
AtvLauncher/
├── CLAUDE.md                          ← This file
├── app/
│   ├── build.gradle                   ← minSdk 26, targetSdk 34, compileSdk 34
│   └── src/main/
│       ├── AndroidManifest.xml        ← HOME intent, LEANBACK_LAUNCHER, queries
│       ├── java/com/clean/launcher/
│       │   ├── MainActivity.java      ← Main activity, all nav logic
│       │   ├── AppInfo.java           ← Data class (packageName, label, icon, banner)
│       │   ├── AppRowAdapter.java     ← Horizontal row RecyclerView adapter
│       │   ├── GlassView.java         ← Spring-animated floating focus highlight
│       │   ├── SkyView.java           ← Time-of-day gradient background
│       │   └── SettingsPopup.java     ← Right-panel settings overlay
│       └── res/
│           ├── layout/
│           │   ├── activity_main.xml  ← Root layout
│           │   ├── item_row_app.xml   ← Single app tile
│           │   ├── overlay_settings.xml ← Settings popup panel
│           │   └── item_input.xml     ← HDMI/input row item
│           ├── drawable/              ← Icons, pill bg, panel bg, etc.
│           ├── font/                  ← outfit_light.ttf, outfit_regular.ttf
│           ├── color/                 ← pill_text.xml (state-based)
│           └── values/
│               ├── themes.xml         ← Theme.Launcher + PillButton style
│               ├── colors.xml
│               └── strings.xml
├── tv_backup/                         ← Debloat restore scripts
│   ├── restore.bat                    ← Re-enables all disabled packages
│   ├── build-and-install.bat          ← Build + push shortcut
│   └── README.md                      ← Full debloat documentation
└── platform-tools/                    ← adb.exe lives here
```

---

## 9. Disabled Packages (Debloat)

These were disabled with `pm disable-user --user 0`. They persist across reboots.

| Package | What it was |
|---|---|
| `tv.anoki.acr.controller` | Audio fingerprinting / ACR telemetry |
| `com.homwee.help` | Realme help (ad referrer) |
| `com.homwee.store` | Realme app store |
| `com.homwee.aipont` | Realme AI Point UI / channel ads |
| `com.homwee.howtocast` | "How to Cast" promo app |
| `com.homwee.mmp.screenshare` | Screen share (hpplay) |
| `com.homwee.multiscreenshare` | Duplicate screen share |
| `com.homwee.mmp.fileexplorer` | File explorer |
| `com.homwee.mmp.imageplayer` | Image/music player |
| `com.yablio.sendfilestotv` | Send files to TV (ad-supported) |
| `com.google.android.tvrecommendations` | Google ads/recommendation rows |
| `com.google.android.backdrop` | Ambient backdrop screensaver |
| `com.mediatek.testpq` | MediaTek PQ test tool |
| `com.google.android.tvlauncher` | Stock Google TV launcher (replaced) |

**To re-enable any package:**
```powershell
& $adb shell pm enable --user 0 <package.name>
```

**Full restore:** Run `C:\Users\acer\tv_backup\restore.bat`

---

## 10. Active DNS

AdGuard Private DNS is active:
```
private_dns_mode = hostname
private_dns_specifier = dns.adguard-dns.com
```
**To undo:** `adb shell settings put global private_dns_mode opportunistic`

---

## 11. What Was Added (v1.1)

| Feature | File | Notes |
|---|---|---|
| **Glide disk cache** | `AppRowAdapter.java`, `build.gradle` | Banners/icons cached to disk — instant on second boot |
| **Launch zoom animation** | `LaunchAnimator.java` | Apple TV zoom-to-fullscreen when pressing OK on a tile |
| **Smooth scroll physics** | `SmoothRowLayoutManager.java` | Custom 90ms/px scroll speed, capped 120–400ms per step |
| **Row position indicator** | `RowIndicatorView.java`, `activity_main.xml` | Slim white bar under row showing scroll position |
| **Settings popup fix** | `MainActivity.java`, `SettingsPopup.java` | `onResume()` checks `mSettingsOpen` — no more jump-back bug |
| **WiFi stays in-app** | `SettingsPopup.java` | WiFi shows SSID info inline, never calls `startActivity` for WiFi |
| **GlassView post fix** | `MainActivity.java` | `mGlass.post(() -> mGlass.moveTo(v))` — no zero-width race |
| **Initial hero name** | `MainActivity.java` | First app name shown immediately on load, not waiting for focus |

---

## 12. Known Issues & TODO

### Bug: Settings/WiFi button navigates back to launcher
**Symptom:** Pressing Settings or WiFi pill opens the settings, but pressing Back
returns to the launcher home screen unexpectedly (or the activity restarts).

**Root cause:** `startActivity()` for Settings/WiFi causes launcher to go through
`onPause()` → `onResume()`. In `onResume()`, `restoreFocus()` triggers too early
before the RecyclerView is laid out, causing the view to snap back to top.

**Fix needed in:** `MainActivity.java` → `onResume()` and `SettingsPopup.java`
- Settings popup should be a full overlay (already implemented) — do NOT call
  `startActivity()` for Settings from the popup. Instead show inline UI.
- WiFi should NOT call `startActivity(Settings.ACTION_WIFI_SETTINGS)` — this
  leaves the launcher entirely. Show WiFi status in the popup panel instead.
- `onResume()` should check `mSettingsOpen` flag before calling `restoreFocus()`.

### Missing: Hero name on initial load
**Symptom:** When launcher first opens, the large app name below the clock is empty
until the user moves D-pad to focus an app.

**Fix:** In `setupRow()`, after setting the adapter, manually trigger focus on
position 0 and show its name. The `restoreFocus()` delay needs to be ≥400ms.

### Missing: GlassView on initial focus
**Symptom:** GlassView doesn't appear until second focus change.

**Fix:** `GlassView.moveTo()` is called before the view is laid out (width=0).
Call `mGlass.post(() -> mGlass.moveTo(v))` instead of direct call.

### UI Polish TODO
- Settings panel: add blur/frosted glass behind panel (use dimmed scrim, already done)
- WiFi section: show signal strength bars
- Inputs section: show currently active input with a checkmark
- Add smooth scroll snap to app row (LinearSnapHelper)
- Hero art: add a subtle gradient scrim so text stays readable over bright banners

---

## 12. Design Reference

Based on [CleanYourTV launcher](https://github.com/Gathin23/cleanyourtv/tree/main/launcher):

- **Background:** `SkyView` — time-of-day gradient + app accent color blended in
- **Clock:** `TextClock` (auto-updates, no Handler needed)
- **Focus highlight:** `GlassView` — overshoot spring animation (OvershootInterpolator 1.4f)
- **App tiles:** 200×112dp, banner if available else icon, no borders
- **Hero name:** 48sp Outfit Light, cross-fades on focus change
- **Cinematic art:** App banner stretched full-screen at ~8% alpha
- **Ambient glow:** Palette-sampled dark color fills full screen at ~55% alpha
- **Pill buttons:** Frosted (semi-transparent), white+dark text on focus

---

## 13. Quickstart for New Agent

```
1. Read this file fully
2. Check TV is reachable: ping 10.196.79.191
3. If not reachable: ask user to open ADB TV app on TV and give new IP
4. Connect: adb connect <IP>:5555
5. If unauthorized: ask user to tap Allow on TV
6. Build: cd AtvLauncher && .\gradlew.bat assembleDebug --no-daemon
7. Push: adb push app\build\outputs\apk\debug\app-debug.apk /data/local/tmp/launcher.apk
8. Install: adb shell pm install -r /data/local/tmp/launcher.apk
9. Test: adb shell input keyevent KEYCODE_HOME
10. Screenshot: adb shell screencap -p /sdcard/s.png && adb pull /sdcard/s.png
```
