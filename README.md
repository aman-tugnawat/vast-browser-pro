# Vast Browser Pro

Vast Browser Pro is a modern, high-performance web browser designed completely from the ground up for large-format displays—specifically Smart TVs and spatial computing platforms (such as Meta Quest and Apple Vision). This repository contains the **Pro** version, which leverages Mozilla GeckoView to support full Firefox WebExtensions.

Unlike other TV browsers that are often clunky or outdated forks of mobile software, Vast Browser Pro leverages modern Mozilla Android Components and GeckoView to deliver a premium, immersive, and fast browsing experience natively tailored for the living room and spatial canvas.

The standard version (built on native System WebView) is located in the [Vast Browser (Regular)](https://github.com/aman-tugnawat/vast-browser.git) repository.

---

## 🏗️ Architecture

This repository focuses on:
- **Vast Browser Pro (`/vast-browser-pro`):**
  - Built on the robust **Mozilla GeckoView** engine.
  - Supports native Firefox WebExtensions (includes preloaded uBlock Origin and Privacy Badger).
  - Ideal for devices with sufficient resources where complete extension support is needed.

---

## 📚 Project Documentation

Detailed architecture blueprints, interaction guides, and logs are organized in the top-level [/documentation](documentation/) folder:

1. [Architecture & Design Overview](documentation/01-architecture-overview.md) — Motivation, design goals, and compiler/dependency modular split details.
2. [User Interaction & Controls](documentation/02-user-interaction.md) — Spatial D-Pad highlight navigation, simulated cursor, hover detection, virtual keyboard (IME), and remote media controls.
3. [Testing & Verification Guide](documentation/03-testing-guide.md) — Android TV emulator configuration, PC keyboard mapping, and manual keyevent testing scenarios.
4. [Project Status & Release Log](documentation/04-release-summary.md) — Release notes for v0.3.0, patches, bug fixes, and weekly CI/CD workflows.

---

## 🚀 Getting Started & Local Testing

### 1. Build Requirements
- **JDK:** Version 17
- **Android SDK:** API Level 36 (target SDK 34)
- **IDE:** Android Studio (Jellyfish or newer recommended). Open the nested `/VastBrowser` folder directly in Android Studio, *not* the root repo folder.

### 2. Compile debug APK
Run the Gradle wrapper inside the `VastBrowser` directory to compile the Pro variant:
```bash
# Build Pro Gecko version
./gradlew :vast-browser-pro:assembleDebug
```

### 3. Deploy and Launch via ADB
With your Android TV connected via ADB, run the following commands:

#### For Vast Browser Pro (Gecko):
```bash
# Install (pick correct ABI, e.g. armeabi-v7a)
adb install -r vast-browser-pro/build/outputs/apk/debug/vast-browser-pro-armeabi-v7a-debug.apk

# Launch a webpage
adb shell am start -n com.mangodevelopers.vastbrowser.tv.pro.debug/com.mangodevelopers.vastbrowser.tv.BrowserActivity -d "https://www.cineby.at/tv/60625/3/8"
```
