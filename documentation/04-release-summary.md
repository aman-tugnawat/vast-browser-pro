# Project Status & Release Log: Version 0.3.0

**Project Name:** Vast Browser (Leanback TV)  
**Version:** `0.3.0`  
**Target Architecture:** Multi-module independent app package structure.  

This document tracks all features, optimizations, refactoring, and bug fixes implemented from project inception through the v0.3.0 milestone.

---

## 1. Repository Split (Standard vs. Pro)

To eliminate runtime switching overhead, package size inefficiencies, and allow focused development, the project has been split into two independent repositories:
- **`vast-browser-pro` (GeckoEngine) (This repository):** Native Firefox engine with desktop-grade WebExtensions, currently in early development.
- **`vast-browser` (SystemEngine) (Separate repository):** Ultra-lightweight ~2MB build using the native Android WebView. This version is the primary focus of active development and stabilization.

---

## 2. Dynamic Content Blocking Architectures

### A. WebView JS Content-Script Injection (Standard Module)
- **uBlock Origin Plugin:** Parses bundled `easylist_slim.txt` + `easyprivacy_slim.txt` on thread startup. Injects CSS hiding rules and overrides `XMLHttpRequest`/`fetch`/`createElement` to abort ad-server requests.
- **Privacy Badger Plugin:** Blocks third-party tracker cookies using `privacy_badger_seed.json`. Strips UTM and social query params (e.g. `fbclid`, `gclid`).

### B. Gecko Engine Native WebExtensions (Pro Module)
- **Offline Bundling:** Bundles `.xpi` WebExtension binaries under assets. Copies them to local storage on the first load and triggers standard WebExtension controller installation.
- **AMO Search Integration:** Embeds a settings prompt that calls the Mozilla Add-on (AMO) API, allowing users to query, download, and install third-party Gecko extensions.
- **WebExtension Management:** Active list display with toggle controls to enable, disable, or completely uninstall extensions.

---

## 3. UI/UX Customizations & Brand Rebranding

- **Rebranded Assets:** Removed all references to "Firefox TV" or "Firefox WebExtensions". Replaced them with the **Vast Browser** visual system.
- **Widescreen Leanback Banners:** Added proper 16:9 widescreen launch banners (`app_banner.png` in `drawable-xhdpi`) for Google TV and Fire TV home screen integration.
- **Adaptive Icons:** Configured adaptive launcher icons (`ic_launcher.xml` in `mipmap-anydpi-v26` pointing to corresponding mipmap background/foreground layers) utilizing the standardized icon pack.
- **Cursor Speed Control:** Settings options allowing users to configure the D-Pad cursor velocity (**Slow, Medium, Fast, Faster, Fastest**).
- **Custom Scroll Mode:** Long press triggers scroll mode with adjustable hold delays.

---

## 4. Key Patches & Bug Fixes

- **PDF Viewer Support (Lite module):** Filtered out `PdfStateMiddleware` in `Components.kt` to prevent runtime `UnsupportedOperationException` blue-screen crashes.
- **URL Bar Re-Sync:** Added `refreshUrlBar()` triggered on toolbar show to keep the displayed URL synchronized, resolving the bug where address bars didn't update after in-page link clicks.
- **HDR Color Washout Workaround:** Set `window.colorMode = COLOR_MODE_DEFAULT` programmatically inside `BrowserActivity` to prevent SDR colors from washing out when decoding HDR video content.
- **Video Playback Black-Screen Fix:** Removed forced offscreen hardware drawing layers (`setLayerType(LAYER_TYPE_HARDWARE)`) on the `engineContainer` in both modules, allowing WebView and GeckoView to render video surfaces directly to the window viewport.

---

## 5. Automated Build Workflows (CI/CD)

- **`build-release.yml`:** Weekly pipeline executing local checks, compiling target split APKs (`armeabi-v7a`, `arm64-v8a`, `x86`, `x86_64`), and creating draft releases on GitHub.
- **`version-check.yml`:** Automated nightly checking of Mozilla Maven repos. Auto-creates Pull Requests if updated Gecko components are detected.
- **Background Update Notification:** Client background worker that queries the GitHub Release API and reveals an indicator on the settings icon when a new APK update is published.
