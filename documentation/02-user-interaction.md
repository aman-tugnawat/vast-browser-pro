# Vast Browser - User Interaction & Navigation

This document outlines the user interface layout, TV remote D-Pad navigation, Bluetooth pointer controls, on-screen keyboard (IME) integration, and media controls for **Vast Browser**.

---

## 1. Floating Toolbar Layout & Rendering

To prevent layout reflows and stuttering on high-resolution TVs, the address bar is implemented as a floating overlay.

- **Full-Screen Viewport:** The web content view (`engineView`) is declared first in the XML layout and fills the entire screen.
- **Floating Header Overlay:** The toolbar and progress bar overlay on top of the web content.
  - **Hidden State:** Translates visually off-screen using hardware-accelerated translation: `translationY = -toolbarHeight`.
  - **Visible State:** Slides down smoothly: `translationY = 0f`.
- **Zero Layout Reflows:** Showing or hiding the address bar never resizes the webpage viewport.
- **Direct Rendering Isolation (Video Playback Fix):** Android WebView renders hardware-decoded video via native SurfaceView overlays. Forcing offscreen layers (like `LAYER_TYPE_HARDWARE` or `LAYER_TYPE_SOFTWARE`) on the WebView or its parent layout container blocks the video decoder surface from drawing directly to the window, causing a black video screen. The engine container leaves layer rendering to the default window composition pipeline, ensuring video streams decode and draw correctly.

---

## 2. Bluetooth Pointer Hover Interception

For mouse/trackpad pointer devices connected via Bluetooth:
- **Global Interception:** WebViews consume generic motion events to handle hover actions, which blocks standard view hover listeners. Pointer events are globally captured at the Activity level via `dispatchGenericMotionEvent` before child views process them.
- **Top 5% Screen Height Reveal:** The toolbar is automatically revealed when the pointer coordinate is in the top 5% of the screen's measured height:
  $$\text{thresholdPx} = \text{rootHeight} \times 0.05$$
- **Auto-Hide:** Moving the cursor below the top 5% boundary automatically slides the toolbar up and out of view.

---

## 3. D-Pad Highlight Navigation (Standard TV Remote)

Vast Browser supports two toggleable navigation modes for TV remotes:

### 3.1 Element Surfing Mode
Focus jumps between focusable webpage elements (buttons, links, inputs).
- **Visual Selection Indicator:** A CSS style ruleset is injected on page load to draw a prominent outline around the selected element:
  - Outline uses an `inset box-shadow` (`box-shadow: inset 0 0 0 4px #FF9800`) to prevent clipping by parent elements that have `overflow: hidden`.
  - Focused items undergo a slight `transform: scale(1.03)` and are given `z-index: 9999` to stand out.
  - Generates synthetic `mouseenter` / `mouseover` events to trigger webpage hover styles (e.g. YouTube card hover scaling).
- **Per-Element Visibility Check:** Clickable elements are verified using `document.elementFromPoint()` to prevent D-pad focus from trapping behind overlays or modals.
- **Focus Restoration:** Hiding the toolbar automatically restores focus to the last highlighted webpage element.

### 3.2 D-Pad Cursor Mode
Simulates a mouse pointer on the screen using the D-Pad.
- Users can customize the cursor speed via Settings (**Slow, Medium, Fast, Faster, Fastest**).
- **Scroll Mode:** Users can trigger scroll mode by holding down the D-Pad **CENTER/ENTER** button. A customizable delay (1s, 2s, 3s, 5s) determines how long the button must be held to start/end scroll mode.

### 3.3 Quick Toolbar Toggle (Triple Press)
To support natural webpage scrolling while preventing accidental layout shifts:
- **Triple UP to Show:** When the toolbar is hidden and focus is on the webpage, pressing the D-Pad **UP** key three times quickly (within 500ms intervals) slides the toolbar down and focuses the URL bar.
- **Triple DOWN to Hide:** When the toolbar is visible, pressing the D-Pad **DOWN** key three times quickly slides the toolbar up and returns focus to the webpage.

---

## 4. On-Screen Keyboard (IME) Summoning Rules

The virtual keyboard is only displayed on deliberate, intentional actions:
- **Focus vs Edit Mode:** Highlighting the URL bar using the D-Pad arrow keys slides the toolbar down but does **not** show the keyboard (to keep the screen clear).
- **Explicit Activation:** The virtual keyboard is summoned only when:
  1. The user presses the D-Pad **OK/Center** key while the URL input is focused.
  2. The user explicitly clicks the URL input using a pointer or touch.
- **Animation-Aware Delay:** Keyboards are summoned after a `300ms` delay to allow the toolbar slide-down animation to fully complete, preventing Android's `InputMethodManager` from rejecting the request.

---

## 5. Exit & Escape Flows

- **One-Click BACK Dismissal:** Pressing the **BACK** key while the toolbar is active hides the keyboard, clears URL focus, returns focus to the webpage, and slides the toolbar off-screen.
- **Unified Exit Dialog:** Pressing **BACK** when focus is on the webpage prompts a custom dialog:
  - **Exit App (Default):** Saves the current tab's active URL, clears cache, and terminates.
  - **Previous Webpage:** Goes back one step in browser history.
- **Three-Press Emergency Exit:** Pressing the **BACK** button 3 times in quick succession (each within 2s) immediately triggers the exit flow (saving session, clearing cache, finishing activity), bypassing the dialog.

---

## 6. Remote Media Playback Controls

Standard TV remote media keys are intercepted at the Activity level and processed using DOM JavaScript injection:
- **Play (`KEYCODE_MEDIA_PLAY`):** Plays HTML5 video/audio elements.
- **Pause (`KEYCODE_MEDIA_PAUSE`):** Pauses HTML5 video/audio elements.
- **Toggle (`KEYCODE_MEDIA_PLAY_PAUSE`):** Toggles playback state.
- **Fast Forward (`KEYCODE_MEDIA_FAST_FORWARD` / `KEYCODE_MEDIA_NEXT`):** Seeks forward 10 seconds.
- **Rewind (`KEYCODE_MEDIA_REWIND` / `KEYCODE_MEDIA_PREVIOUS`):** Seeks backward 10 seconds.
