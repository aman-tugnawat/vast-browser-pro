# Vast Browser

Vast Browser is a modern, high-performance web browser designed completely from the ground up for large-format displays—specifically Smart TVs and spatial computing platforms (such as Meta Quest and Apple Vision). 

Unlike other TV browsers that are often clunky or outdated forks of mobile software, Vast Browser leverages modern Mozilla Android Components to deliver a premium, immersive, and fast browsing experience natively tailored for the living room and spatial canvas.

---

## 🏗️ Multi-Module Architecture

To maximize performance, package size efficiency, and stability, Vast Browser is split into two independent application modules:

1. **Vast Browser (`/vast-browser`):**
   - Built on the robust **Mozilla GeckoView** engine.
   - Supports native Firefox WebExtensions (includes preloaded uBlock Origin and Privacy Badger).
   - Ideal for devices with sufficient resources where complete extension support is needed.
2. **Vast Browser Lite (`/vast-browser-light`):**
   - Built on the native **System WebView** engine.
   - Extremely lightweight (~2MB APK).
   - Uses lightweight JavaScript content blocking plugins.
   - Ideal for low-resource streaming sticks (e.g. Fire TV Stick Lite).

---

## 📚 Project Documentation

Detailed architecture blueprints, interaction guides, and logs are organized in the top-level [/documentation](file:///Users/aman/Code/firefox4tv/documentation/) folder:

1. [Architecture & Design Overview](file:///Users/aman/Code/firefox4tv/documentation/01-architecture-overview.md) — Motivation, design goals, and compiler/dependency modular split details.
2. [User Interaction & Controls](file:///Users/aman/Code/firefox4tv/documentation/02-user-interaction.md) — Spatial D-Pad highlight navigation, simulated cursor, hover detection, virtual keyboard (IME), and remote media controls.
3. [Testing & Verification Guide](file:///Users/aman/Code/firefox4tv/documentation/03-testing-guide.md) — Android TV emulator configuration, PC keyboard mapping, and manual keyevent testing scenarios.
4. [Project Status & Release Log](file:///Users/aman/Code/firefox4tv/documentation/04-release-summary.md) — Release notes for v0.3.0, patches, bug fixes, and weekly CI/CD workflows.

---

## 🚀 Getting Started & Local Testing

### 1. Build Requirements
- **JDK:** Version 17
- **Android SDK:** API Level 36 (target SDK 34)
- **IDE:** Android Studio (Jellyfish or newer recommended). Open the nested `/VastBrowser` folder directly in Android Studio, *not* the root repo folder.

### 2. Compile debug APKs
Run the Gradle wrapper inside the `VastBrowser` directory to compile either variant:
```bash
# Build Gecko version
./gradlew :vast-browser:assembleDebug

# Build Lite version
./gradlew :vast-browser-light:assembleDebug
```

### 3. Deploy and Launch via ADB
With your Android TV connected via ADB, run the following commands:

#### For Vast Browser (Gecko):
```bash
# Install (pick correct ABI, e.g. armeabi-v7a)
adb install -r vast-browser/build/outputs/apk/debug/vast-browser-armeabi-v7a-debug.apk

# Launch a webpage
adb shell am start -n com.mangodevelopers.vastbrowser.tv.debug/com.mangodevelopers.vastbrowser.tv.BrowserActivity -d "https://www.cineby.at/tv/60625/3/8"
```

#### For Vast Browser Lite (WebView):
```bash
# Install
adb install -r vast-browser-light/build/outputs/apk/debug/vast-browser-light-armeabi-v7a-debug.apk

# Launch a webpage
adb shell am start -n com.mangodevelopers.vastbrowser.tv.lite.debug/com.mangodevelopers.vastbrowser.tv.BrowserActivity -d "https://www.cineby.at/tv/60625/3/8"
```
