# Vast Browser - Testing & Verification Guide

This guide describes how to configure local testing environments (emulators and ADB) and execute verification suites for Android TV D-Pad focus mapping and pointer navigation.

---

## 1. Emulator (AVD) Configuration

To accurately test TV remote controls and cursor modes, set up an Android TV emulator in Android Studio:

1. **Create the AVD:**
   - In Android Studio, open **Device Manager** -> **Create Device**.
   - Under Category, select **TV** and pick **Android TV (1080p)**.
   - Select a system image running **Android 14 (API 34)** or higher.

2. **Bypass TV Virtual Keyboard (Enable PC Keyboard):**
   - By default, Android TV emulators disable physical keyboard input. To allow typing URLs directly from your computer keyboard, edit the AVD configuration file.
   - Locate the `config.ini` file for your emulator (usually found in `~/.android/avd/YOUR_AVD_NAME.avd/config.ini`).
   - Find the property `hw.keyboard` and change it:
     ```ini
     hw.keyboard = yes
     ```
   - Restart the emulator.

---

## 2. ADB Key Event Code Reference

Use `adb shell input keyevent <keycode>` to simulate TV remote presses on a connected device:

| Remote Button | Android Keycode | ADB Command |
|---------------|-----------------|-------------|
| **D-Pad UP** | `19` | `adb shell input keyevent 19` |
| **D-Pad DOWN** | `20` | `adb shell input keyevent 20` |
| **D-Pad LEFT** | `21` | `adb shell input keyevent 21` |
| **D-Pad RIGHT** | `22` | `adb shell input keyevent 22` |
| **D-Pad CENTER (OK)** | `23` | `adb shell input keyevent 23` |
| **BACK** | `4` | `adb shell input keyevent 4` |
| **PLAY** | `126` | `adb shell input keyevent 126` |
| **PAUSE** | `127` | `adb shell input keyevent 127` |
| **PLAY_PAUSE** | `85` | `adb shell input keyevent 85` |

---

## 3. Manual Verification Scenarios

### Scenario 1: Initial Page Focus
1. Launch either application module and navigate to a complex webpage (e.g., `https://wikipedia.org`).
2. Verify that as soon as the page finishes loading (progress bar hides), the topmost clickable HTML element is immediately highlighted with a **4px solid orange (#FF9800)** outline.
3. Press D-Pad arrow keys to verify the highlight moves dynamically between adjacent HTML elements.

### Scenario 2: Toolbar Toggle & Focus Hand-off
1. With the toolbar visible, focus the URL input bar.
2. Press the D-Pad **DOWN** key 3 times quickly (within 500ms between key presses).
3. Verify that:
   - The toolbar slides up and hides.
   - The virtual keyboard disappears.
   - Focus is correctly transferred back to the native webpage rendering layer.
   - Subsequent single arrow presses scroll the page or navigate between webpage links.
4. Press the D-Pad **UP** key 3 times quickly.
5. Verify that the toolbar slides down and focus returns directly to the URL input bar.

### Scenario 3: Media Control Interception
1. Load a website containing HTML5 video content (e.g. YouTube).
2. Start video playback.
3. Send media key inputs:
   - Command: `adb shell input keyevent 85` (Play/Pause Toggle)
4. Verify the video toggles playback state synchronously.

---

## 4. Troubleshooting Navigation Blocks

- **Focus Lost / "Black Hole":** If arrow key events stop reacting after hiding the toolbar, verify in logcat that `getWebView()?.requestFocus()` was invoked. If a parent container view intercepts focus instead of the inner rendering core, focus gets trapped.
- **Washed-out SDR UI:** If playing HDR video causes the address bar colors to fade, ensure `window.colorMode` is set programmatically to default inside `BrowserActivity.kt` on create.
