# Vast Browser - Design & Architecture

## 1. Motivation
The web was originally designed for desktop monitors, and later heavily optimized for mobile screens. However, there is a significant void when it comes to browsing the web on large-format displays—specifically Smart TVs and the emerging spatial computing platforms (like Meta Quest 3 and Apple Vision Pro). Existing TV browsers are often outdated, clunky, or poorly maintained forks of mobile browsers. 

The motivation behind this project is to build a modern, high-performance web browser completely decoupled from the constraints of mobile and laptop form factors. By leveraging the power of modern Mozilla components, we aim to deliver a premium, immersive browsing experience natively designed for the living room and the spatial canvas.

## 2. Project Name: "Vast Browser"
The name **Vast Browser** was chosen to capture the expansive, boundless feel of large TV screens and infinite spatial computing environments. It is short, memorable, and immediately conveys a sense of large-scale immersion, separating it from the claustrophobic constraints of mobile browsing.

## 3. Inspirations
- **Legacy Firefox TV:** Served as the architectural foundation and proof-of-concept for running Gecko/Mozilla components in a Leanback environment.
- **VisionOS & Spatial Computing:** Apple Vision Pro and Meta Quest interfaces inspire the floating, lightweight, and glassmorphic UI patterns we intend to adopt for later stages.
- **Modern TV OS (Google TV / Apple TV):** Inspirations for intuitive D-Pad navigation, smooth focus micro-animations, and cinematic home screen layouts.

## 4. The 3-Phase Development Plan

### Phase 1: Foundation & Minimal Viable Product (MVP)
*Focus: Modernization, Toolchain, and Core Rendering*
- Take the legacy 2019-era Firefox TV codebase and completely modernize the build system (Gradle 8, Kotlin 2.3).
- Rip out deprecated components and wire up the modern Mozilla Android Components (v150) using a Redux-style `BrowserStore`.
- Stabilize the core rendering engine (`SystemEngineView`), ensuring D-Pad inputs map correctly to UI focus.
- Spoof desktop user-agents to force websites to serve high-quality desktop or TV layouts (e.g., `youtube.com/tv`) rather than unsupported mobile views.

### Phase 2: TV Leanback UX & Custom Interface
*Focus: Premium TV User Experience*
- Build out the custom TV interface, including a dedicated Home Screen overlay with Pinned Tiles and Channels.
- Implement the "virtual cursor" system for navigating complex web pages using a standard D-Pad.
- Introduce premium modern aesthetics: deep dark themes, subtle gradients, and reactive micro-animations when focusing elements.
- Optimize media playback hooks and full-screen video transitions.

### Phase 3: Spatial Computing & WebXR Expansion
*Focus: Virtual Reality and Augmented Reality*
- Expand the platform target beyond standard Android TV to include Meta Quest 3 and future Apple Vision interfaces.
- Enable and optimize WebXR capabilities within the engine for immersive 3D content.
- Support spatial UI paradigms: multi-window floating panels, hand-tracking interactions, and unbounded canvases.

## 5. Current State of Development
**Status:** **Phase 1 Complete.**

The foundation is rock solid. We have successfully ported the browser shell to the modern Mozilla v150 architecture. The app compiles seamlessly, the Redux state management correctly handles tabs and navigation, and we have patched engine-level crashes regarding PDF middlewares that plagued TV deployments. Hardware keyboards and TV D-Pads successfully navigate the shell, and the engine seamlessly streams hardware-accelerated video from desktop-class web applications.

We are currently transitioning into **Phase 2**, where the focus will shift entirely to user interface design and Leanback navigation patterns.
