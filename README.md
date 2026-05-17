# Vast Browser

Vast Browser is a modern, high-performance web browser designed completely from the ground up for large-format displays—specifically Smart TVs and spatial computing platforms (such as Meta Quest and Apple Vision). 

Unlike other TV browsers that are often clunky or outdated forks of mobile software, Vast Browser leverages modern Mozilla Android Components to deliver a premium, immersive, and fast browsing experience natively tailored for the living room and spatial canvas.

## Features
- **TV-First Navigation:** Optimized for standard D-Pad remotes with seamless focus mapping.
- **Desktop-Class Rendering:** Bypasses mobile constraints to serve full desktop interfaces (e.g., `youtube.com/tv`), ensuring you get the best experience possible on a large screen.
- **Modern Architecture:** Built on Kotlin 2.3, Gradle 8, and Redux-style state management (`BrowserStore`).

---

## Getting Started (Personal Use)

If you just want to install and use Vast Browser on your TV:

1. **Download the APK:** Grab the latest `app-debug.apk` from the [Releases](#) page (or build it yourself using the instructions below).
2. **Enable Developer Options:** On your Android TV, go to Settings -> Device Preferences -> About, and click "Build" 7 times.
3. **Install the APK:** 
   - **Via ADB:** Connect to your TV over the network and run:
     ```bash
     adb connect <TV_IP_ADDRESS>
     adb install app-debug.apk
     ```
   - **Via USB / Cloud Drive:** Transfer the APK to a thumb drive or cloud storage app (like Send Files to TV) and install it using a file manager on your TV.
4. **Launch:** Open Vast Browser and use your TV remote's D-Pad to navigate the UI, click the URL bar, and browse the web!

---

## Development and Local Testing

The active development environment is isolated within the `VastBrowser` folder. The parent directory also contains a `reference-code` folder storing legacy assets and Mozilla central snapshots.

### Requirements
- **Android Studio:** Latest stable release (Jellyfish or newer recommended).
- **Java Development Kit (JDK):** Version 17.
- **Android SDK:** API Level 34.

### Build Setup
1. Clone the repository.
2. **Important:** Open the `VastBrowser/` folder directly in Android Studio, *not* the root repository folder. This ensures the Gradle sync works correctly.
3. Let Gradle sync the dependencies.

### Local Testing (Emulator)
To test the TV interface accurately, you should use an Android TV emulator.

1. **Create an AVD:** In Android Studio's Device Manager, create a new Virtual Device using the **Android TV (1080p)** profile with an Android 14 (API 34) system image.
2. **Enable Hardware Keyboard:** 
   - By default, the emulator disables your physical keyboard. To fix this, locate the AVD's `config.ini` file (usually in `~/.android/avd/YOUR_AVD_NAME.avd/config.ini`).
   - Change `hw.keyboard = no` to `hw.keyboard = yes`.
3. **Run the App:** 
   - Start the emulator.
   - Click **Run 'app'** in Android Studio to build and deploy the debug APK.
   - Use your physical keyboard arrows or the emulator's virtual D-Pad to test focus navigation.

## Project Structure
- `VastBrowser/`: The active, modern codebase for the Android application.
- `documentation/`: Contains detailed design docs, architecture breakdowns, and phase planning.
- `reference-code/`: Unmaintained legacy code and foundational binaries used for research and fallback.
