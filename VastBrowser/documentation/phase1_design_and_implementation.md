# Vast Browser - Phase 1 MVP

## 1. Overview and Objectives
The goal of Phase 1 was to take an outdated 2019-era "Firefox for FireTV" codebase, fully modernize its toolchain, and stabilize the core browsing engine using modern Mozilla Components. The focus was to prove that we could decouple the app from legacy `WebView` limitations and `ServiceLocator` anti-patterns, while ensuring a stable, fast, and crash-free rendering shell for a TV form factor.

## 2. System Design and Architecture
### 2.1 Toolchain Modernization
- **Build System:** Migrated from Gradle 5.x to **Gradle 8.13**.
- **Android Gradle Plugin (AGP):** Upgraded to **8.9.3**.
- **Language:** Fully upgraded to **Kotlin 2.3.20** utilizing modern `compilerOptions` DSL in `build.gradle.kts`.
- **Dependency Management:** Configured repositories to pull `mozilla-central-FIREFOX_NIGHTLY_150` component binaries directly from Maven rather than compiling complex Gecko engines locally. This slashed build times and improved developer ergonomics.

### 2.2 Core Components & State Management
- **BrowserStore (Redux-Style):** Replaced the legacy ad-hoc RxJava `SessionManager` with a robust `BrowserStore` pattern native to modern Android-Components. State changes are predictable and centralized.
- **EngineMiddleware:** Introduced `EngineMiddleware` to tie the `BrowserStore` directly to the `SystemEngine`. 
- **SessionFeature:** Used `SessionFeature` in the UI layer (`BrowserFragment`) to seamlessly bridge the `EngineView` and the `BrowserStore`, managing back/forward stack and reload capabilities natively.
- **Dependency Injection:** Stripped out the complex, custom `ServiceLocator` in favor of a clean, lazy-loaded `Components` instance attached to the Application Context.

## 3. Implementation Hurdles & Fixes
### 3.1 PDF Viewer Crash on TV
**Issue:** When the `SystemEngine` (backed by Android WebView) spun up, the app crashed immediately with a "Blue Screen".
**Root Cause:** The default `EngineMiddleware` attempts to initialize `PdfStateMiddleware`, which calls `checkForPdfViewer()`. Since Android's `SystemEngine` natively lacks this Gecko-specific capability, it threw an `UnsupportedOperationException`.
**Fix:** We manually initialized the `EngineMiddleware` array in `Components.kt` and explicitly stripped out the offending `PdfStateMiddleware`.

### 3.2 Focus and D-Pad Traps
**Issue:** The D-Pad on the emulator was unable to interact with the URL bar, and the on-screen keyboard failed to appear.
**Root Cause:** `SystemEngineView` swallowed all DPAD navigation events, preventing users from backing out into the Toolbar UI. Additionally, Android TV (Leanback) requires explicit invocation of the `InputMethodManager` for `EditText` elements.
**Fix:** 
1. Overrode `dispatchKeyEvent` in `BrowserActivity` to intercept `DPAD_UP` when the engine is focused, throwing focus back to the URL bar.
2. Hooked up an `OnFocusChangeListener` and `OnClickListener` to the URL `EditText` to explicitly invoke `showSoftInput` to guarantee the Leanback keyboard triggers.

### 3.3 Mobile Redirects
**Issue:** Browsing to `youtube.com` redirected to `m.youtube.com`, throwing a "Device Not Supported" error due to TV constraints.
**Fix:** Changed the core `DefaultSettings` user-agent string to spoof a Windows Desktop Chrome environment (`Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36...`). This overrides mobile heuristics and forces servers to return their full-fledged desktop UI or TV-specific (`/tv`) clients.

## 4. Verification and Testing
Testing was done heavily via automation and `adb` debugging against a local Android 14 TV emulator (`1080p`, API 34).

1. **Build Validation:** A clean `./gradlew app:assembleDebug` builds cleanly with no compiler errors.
2. **Crash-Free Start:** The app launches and maintains stability without dropping into a `FATAL EXCEPTION`.
3. **Engine Validation:** `about:blank` correctly renders search results with the desktop user-agent.
4. **Media Validation:** Loading `youtube.com/tv` triggers the TV interface, and the H264 hardware-decoding hooks show successful video rendering in `logcat`.
5. **Input Validation:** The hardware keyboard and standard D-Pad explicitly route focus to the URL bar, trigger the keyboard correctly, and allow seamless URL injection and browsing.
