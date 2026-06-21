# Vast Browser - Design & Architecture Overview

Vast Browser is a modern, high-performance web browser built completely from the ground up for large-format displays—specifically Smart TVs and spatial computing platforms.

---

## 1. Motivation & Philosophy
The web was originally designed for desktop monitors and later heavily optimized for mobile screens. However, there is a significant void when it comes to browsing the web on large-format displays—specifically Smart TVs and the emerging spatial computing platforms (like Meta Quest 3 and Apple Vision Pro). Existing TV browsers are often outdated, clunky, or poorly maintained forks of mobile browsers.

The motivation behind this project is to build a modern, high-performance web browser completely decoupled from the constraints of mobile and laptop form factors. By leveraging the power of modern Mozilla Android Components, we aim to deliver a premium, immersive browsing experience natively designed for the living room and the spatial canvas.

The name **Vast Browser** was chosen to capture the expansive, boundless feel of large TV screens and infinite spatial computing environments. It is short, memorable, and immediately conveys a sense of large-scale immersion, separating it from the claustrophobic constraints of mobile browsing.

---

## 2. Inspirations
- **Legacy Firefox TV:** Served as the architectural foundation and proof-of-concept for running Gecko/Mozilla components in a Leanback environment.
- **VisionOS & Spatial Computing:** Apple Vision Pro and Meta Quest interfaces inspire the floating, lightweight, and glassmorphic UI patterns we intend to adopt for later stages.
- **Modern TV OS (Google TV / Apple TV / Fire TV):** Inspirations for intuitive D-Pad navigation, smooth focus micro-animations, and cinematic home screen layouts.

---

## 3. Independent Two-Module Architecture

To maximize performance, package size efficiency, and stability, the codebase is structured as two **fully independent** application modules. Rather than supporting engine-switching at runtime (which bloats APK size and introduces runtime complexity), each variant targets a specific engine compile-time.

```
VastBrowser/
  ├── vast-browser/          (App Module) - "Vast Browser"
  │   └── Uses SystemEngine (Android Native WebView)
  │
  ├── vast-browser-pro/      (App Module) - "Vast Browser Pro"
  │   └── Uses GeckoEngine (Mozilla GeckoView v150.0.2)
  │
  ├── build.gradle.kts       (Root Gradle Config)
  └── settings.gradle.kts    (Submodule Registry)
```

### A. Vast Browser (`:vast-browser`)
- **Engine:** SystemEngine (Android Native WebView).
- **Application ID:** `com.mangodevelopers.vastbrowser.tv` (Debug: `com.mangodevelopers.vastbrowser.tv.debug`).
- **Extensions:** Lightweight custom content-blocking rules injected via JavaScript.
- **Branding:** Teal/green themed leanback home banner and standardized launcher icon.
- **Target Case:** Low-resource streaming sticks (e.g. Fire TV Stick Lite) requiring a minimal memory and storage footprint (~2MB APK).

### B. Vast Browser Pro (`:vast-browser-pro`)
- **Engine:** GeckoEngine (Mozilla GeckoView v150.0.2).
- **Application ID:** `com.mangodevelopers.vastbrowser.tv.pro` (Debug: `com.mangodevelopers.vastbrowser.tv.pro.debug`).
- **Extensions:** Supports actual desktop-grade Firefox WebExtensions (`.xpi` bundles) installed natively.
- **Branding:** Deep purple/blue themed leanback home banner with "PRO" label below the "V" logo, and standardized launcher icon.
- **Target Case:** High-end TV devices and consoles with sufficient memory where extension compliance is paramount.

---

## 4. Engineering Modernization

### 4.1 Toolchain & Compiler
- **Build System:** Migrated from Gradle 5.x to **Gradle 8.13**.
- **Android Gradle Plugin (AGP):** Upgraded to **8.9.3**.
- **Language:** Fully upgraded to **Kotlin 2.3.20** utilizing modern `compilerOptions` DSL in `build.gradle.kts`.
- **Dependency Isolation:** Pulls `mozilla-central-FIREFOX_NIGHTLY_150` component binaries directly from Maven rather than compiling custom engine binaries locally.

### 4.2 State Management & DI
- **BrowserStore (Redux-Style):** Replaced the legacy RxJava `SessionManager` with a robust `BrowserStore` pattern native to modern Android-Components. State transitions (tabs, navigation states, load progress) are predictable, immutable, and centralized.
- **Dependency Injection:** Stripped out custom `ServiceLocator` anti-patterns in favor of a clean, lazy-loaded `Components` provider attached to the `BrowserApplication` context.
- **Engine Direct Instantiation:** Each app module creates its respective `Engine` directly inside its own `Components.kt` class:
  - `vast-browser` directly instantiates `SystemEngine(context, settings)`.
  - `vast-browser-pro` directly instantiates `GeckoEngine(context, settings, runtime)`.

### 4.3 Engineering Patches
- **PDF Viewer Support isolation:** `EngineMiddleware` automatically registers a `PdfStateMiddleware` which crashes under standard WebViews due to missing internal hook points. The `Components.kt` of the Lite version manually filters out the `PdfStateMiddleware` to prevent crashes.
- **Default Desktop Spoofing:** To prevent mobile redirects (e.g., `m.youtube.com`), the default user-agent is overridden to a Windows Desktop Chrome string (`Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36...`). This forces web servers to serve high-quality desktop layouts natively readable on large displays.
