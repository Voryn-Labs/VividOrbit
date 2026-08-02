# VividOrbit Live TV

[![Android SDK](https://img.shields.io/badge/Android%20SDK-28%2B-brightgreen.svg)](https://developer.android.com)
[![Target SDK](https://img.shields.io/badge/Target%20SDK-34-blue.svg)](https://developer.android.com)
[![Language](https://img.shields.io/badge/Language-Kotlin-orange.svg)](https://kotlinlang.org/)
[![Platform](https://img.shields.io/badge/Platform-Android%20TV-red.svg)](https://developer.android.com/tv)

**VividOrbit** is a modern, high-performance Live TV application engineered for Android TV and set-top box hardware. Built directly on top of Android's `TvView` and `TvContract` framework, VividOrbit provides a seamless, television-native tuning and channel management experience with hardware tuner integration (Dtvkit / Droidlogic).

---

## 🌟 Key Features

* 📺 **Native Live TV Playback**: Built on `android.media.tv.TvView`, offering smooth playback, low latency, and native hardware decoder support.
* 🛡️ **Automated Audio Watchdog**: Features proactive audio track validation (`TvViewHelper`) that detects and recovers from silent stream renegotiations or audio glitches common in hardware tuner HALs.
* 🎮 **Android TV Leanback UI**: Custom DPAD-optimized layout with interactive sidebar guide, numeric channel entry card, and channel banner overlays.
* 🎯 **Precision DPAD & Focus Centering**: Smooth scrolling channel lists with custom `RecyclerViewFocusCentering` to maintain optimal focus positioning during remote navigation.
* 🔢 **Smart Channel Sorting & Numeric Tuning**: Automated database queries via `TvContract`, sorting numerical channel numbers first, with instant direct-number tuning support via remote control.
* 🖼️ **Asynchronous Logo Decoding**: Asynchronous fetching and caching of channel logos using `ChannelLogoLoader`.
* 📻 **MediaSession Integration**: TV key mapping for Channel Up/Down, Volume, Media Session controls, and auto-sleep prevention.

---

## 🛠️ Architecture & Tech Stack

* **Language**: Kotlin 1.9+
* **Minimum SDK**: 28 (Android 9.0)
* **Target SDK**: 34 (Android 14)
* **Java Version**: JDK 17
* **Core Libraries**:
  * `androidx.leanback:leanback:1.0.0` - TV components and Leanback interfaces
  * `androidx.recyclerview:recyclerview:1.3.2` - Optimized list rendering for remote navigation
  * `androidx.cardview:cardview:1.0.0` - Elevated UI overlay cards
  * `org.jetbrains.kotlinx:kotlinx-coroutines-android` - Asynchronous channel fetching & UI updates
* **Hardware Tuner Integration**:
  * `libs/mochitif-release.aar` - Native hardware tuner integration library (`DtvkitTvInput`)

---

## 📁 Repository Structure

```
VividOrbit/
├── app/
│   ├── src/main/
│   │   ├── java/com/vividorbit/livetv/
│   │   │   ├── MainActivity.kt               # Central Activity handling UI state & remote events
│   │   │   ├── data/
│   │   │   │   ├── Channel.kt                # Channel data class
│   │   │   │   └── ChannelRepository.kt      # TvContract query & channel list manager
│   │   │   ├── player/
│   │   │   │   └── TvViewHelper.kt           # TvView callbacks & audio recovery watchdog
│   │   │   └── ui/
│   │   │       ├── ChannelAdapter.kt         # Leanback TV channel list adapter
│   │   │       ├── ChannelLogoLoader.kt      # Channel logo loader utility
│   │   │       └── RecyclerViewFocusCentering.kt  # Focus-centering layout manager for TV DPAD
│   │   └── res/                             # Layouts, styles, and TV drawables
│   └── libs/
│       └── mochitif-release.aar              # Native tuner interface
├── build.gradle                              # Root Gradle config
├── settings.gradle                           # Project settings
└── README.md
```

---

## 🚀 Building & Running

### Prerequisites

1. **Android Studio** (Hedgehog or newer recommended)
2. **JDK 17** configured in Android Studio / Gradle
3. **Android TV Device or Emulator** (API 28+) with a configured TV Input Service (`TvInputService`)

### Build Commands

To build the debug APK via CLI:

```bash
./gradlew assembleDebug
```

To build the release APK:

```bash
./gradlew assembleRelease
```

---

## 🎮 Navigation & Remote Control Shortcuts

| Remote Key / Input | Action |
| :--- | :--- |
| **DPAD UP / DOWN** | Navigate channel guide / Switch channels |
| **DPAD CENTER / SELECT** | Tune selected channel / Toggle channel guide |
| **Numeric Keys (0-9)** | Direct channel number entry |
| **CHANNEL UP / DOWN** | Skip to Next / Previous Channel |

---

## 📜 License

Distributed under standard terms. See workspace files for details.
