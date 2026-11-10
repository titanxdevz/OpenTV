<div align="center">

# 📺 OpenTV

### A clean, Apple TV 4K–style home screen for Android TV

**No ads. No recommendations row. No telemetry. Just your apps.**

[![Android](https://img.shields.io/badge/platform-Android%20TV-3DDC84?logo=android)](https://developer.android.com/tv)
[![License](https://img.shields.io/badge/license-MIT-blue.svg)](LICENSE)
[![Min SDK](https://img.shields.io/badge/min%20SDK-26-3DDC84.svg)](https://developer.android.com)
[![Target SDK](https://img.shields.io/badge/target%20SDK-34-3DDC84.svg)](https://developer.android.com)
[![Language](https://img.shields.io/badge/language-Java-orange.svg)](app/src/main/java)

</div>

---

## ✨ Preview

<div align="center">
  <img src="screenshot.png" alt="OpenTV launcher home screen" width="900">
</div>

A time-of-day sky gradient, a live clock, and a single horizontal row of your
apps. Focus the tile and the background, glow and hero title all re-theme
themselves to match that app's artwork.

---

## 🎯 Why OpenTV?

Stock Android TV launchers are built to show you **ads**. Google TV ships a
recommendations row, a sponsored app shelf and background telemetry. OpenTV
replaces all of it with one screen and nothing else.

| | Stock Google TV | OpenTV |
|---|---|---|
| Ads & sponsored rows | Yes | **None** |
| Recommendations feed | Yes | **None** |
| Background telemetry | Yes | **None** |
| App launch time | Slow (heavy background services) | **Fast — pure Java, no services** |
| Memory footprint | ~200 MB+ | **~60 MB** |
| Ad-blocking DNS required | No | **Works great with it** |

OpenTV is a single `Activity`, a handful of custom `View`s and a
`RecyclerView`. There is no background service, no `WorkManager`, no
analytics SDK and no network permission in the manifest.

---

## ✨ Features

- **🕐 Live clock + greeting** — `TextClock`-style time-of-day greeting
  (*Good Morning / Afternoon / Evening / Night*) that refreshes every minute.
- **🎨 Adaptive theming** — the focused app's artwork is palette-sampled; its
  accent colour drives a soft radial ambient glow blended into the sky
  gradient.
- **🖼️ Cinematic backdrop** — the focused app's banner is blurred and stretched
  full-screen as a subtle backdrop behind the UI.
- **✨ Spring focus highlight** — a custom `GlassView` overlay animates between
  tiles with an overshoot spring curve.
- **🎬 Zoom launch animation** — pressing OK on a tile zooms that tile to
  full-screen while the app opens.
- **📺 Input switcher** — lists real hardware inputs (HDMI, tuner) via
  `TvInputManager` and switches to them directly.
- **📶 Wi-Fi status** — live connection state and SSID, shown in-app.
- **⚙️ Settings overlay** — a slide-in panel for Wi-Fi, inputs, display and
  system settings that never leaves the launcher for basic actions.
- **🧭 Full D-pad navigation** — top bar ↔ app row, with focus state restored
  across launches, rotation and app switches.
- **🖼️ Banner or icon tiles** — uses the app's TV banner when present, falling
  back to the launcher icon.
- **📦 Lightweight** — Glide disk-caches all artwork, so icons render instantly
  on the second boot. ~1 GB RAM TVs are the target.

---

## 📸 Screens

| Home | Focus |
|---|---|
| ![Home](screenshot.png) | *The hero title and ambient glow re-theme to the focused app.* |

---

## 🛠️ Build from source

### Requirements

| Tool | Version |
|---|---|
| JDK | 17 |
| Android SDK Platform | 34 (`compileSdk`) |
| Gradle | 8.2+ (wrapper included) |
| Min device | Android 8.0 (API 26) |

### 1. Clone

```bash
git clone https://github.com/titanxdevz/OpenTV.git
cd OpenTV
```

### 2. Point Gradle at your SDK

Create `local.properties` in the project root (it is git-ignored on purpose):

```properties
sdk.dir=/path/to/Android/sdk
```

### 3. Build

**macOS / Linux**

```bash
chmod +x gradlew
./gradlew assembleDebug
```

**Windows**

```bat
gradlew.bat assembleDebug
```

The APK lands at:

```
app/build/outputs/apk/debug/app-debug.apk
```

---

## 📲 Install on your TV

### Enable ADB over the network

Most Android TVs ship **ADB over USB only**. The usual options are:

1. **Developer options → Network ADB / ADB over Wi-Fi** (Android TV 11+ on
   some OEMs), or
2. A third-party **"ADB TV" / "ADB Connect"** app from the Play Store, which
   opens a small HTTP page and enables port `5555`.

### Connect and install

```bash
adb connect <TV_IP>:5555
adb devices -l          # expect: device product:DVB91RM

adb push app/build/outputs/apk/debug/app-debug.apk /data/local/tmp/opentv.apk
adb shell pm install -r /data/local/tmp/opentv.apk
adb shell input keyevent KEYCODE_HOME
```

OpenTV registers as the `HOME` activity, so the **Home** key now opens it.

### Uninstall / roll back to the stock launcher

```bash
adb shell pm uninstall com.clean.launcher
```

---

## 🧩 How it works

```
app/src/main/java/com/clean/launcher/
├── MainActivity.java        Entry point — app discovery, D-pad routing, focus restore
├── AppInfo.java             App record (package, label, icon, banner, cached backdrop)
├── AppRowAdapter.java       Row adapter — Glide loading, palette sampling, blur generation
├── AppGridAdapter.java      Grid variant of the row adapter
├── DockAdapter.java         Bottom dock adapter
├── SkyView.java             Time-of-day gradient background
├── AmbientView.java         Soft radial accent glow
├── GlassView.java           Spring-animated floating focus highlight
├── FocusHighlightView.java  Focus scale/elevation helper
├── LaunchAnimator.java      Zoom-to-fullscreen app launch
├── RowIndicatorView.java    Slim scroll-position indicator
├── SmoothRowLayoutManager.java  Custom 90ms/px scroll physics
├── InputsDropdown.java      Hardware input picker
└── SettingsPopup.java       Slide-in settings overlay
```

**Rendering pipeline on focus**

```
focus event
   ├──▶ hero title cross-fade        (synchronous, ~80 ms)
   ├──▶ GlassView springs to tile    (synchronous)
   └──▶ palette thread
          ├──▶ sample accent colour  ──▶ AmbientView + SkyView blend
          └──▶ blur banner            ──▶ full-screen cinematic backdrop
```

Artwork is decoded at reduced size with `RGB_565` (half the memory of
`ARGB_8888`) and disk-cached by Glide — the important constraint on a 1 GB TV.

---

## 📦 Dependencies

| Library | Purpose |
|---|---|
| `androidx.appcompat` | Activity & theming |
| `androidx.recyclerview` | Horizontal app row & settings lists |
| `androidx.palette` | Accent colour extraction from app artwork |
| `androidx.constraintlayout` | Layout constraints |
| `com.github.bumptech.glide` | Image loading + disk cache |

---

## 📄 License

Released under the [MIT License](LICENSE).

---

## 👤 Author

**titanxdevz** — <inkmcontop@gmail.com>

> Inspired by [CleanYourTV](https://github.com/Gathin23/cleanyourtv) by Gathin23.

---

## ⚠️ Notes

- OpenTV is not affiliated with, endorsed by, or sponsored by Google, Netflix,
  Amazon or any other app whose icons it displays.
- App icons and banners belong to their respective owners and are loaded at
  runtime by the system.
- Tested on a **Realme AI Point** (Android 11, 1280×720). It works on any
  Android TV / Leanback device running API 26+.
