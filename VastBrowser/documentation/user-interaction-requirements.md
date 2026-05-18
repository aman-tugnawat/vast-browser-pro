# Vast Browser User Interaction Requirements

This document outlines the input handling and user interface specifications for the floating overlay address bar, D-pad controllers, Bluetooth pointers, and on-screen keyboard (IME) integration inside **Vast Browser**.

---

## 🖥️ 1. Layout & Z-Ordering Architecture

To prevent screen stutter and layout reflows on high-resolution TVs, the address bar is implemented as a floating overlay.

* **Full-Screen Viewport:** The web content view (`engineView`) is declared first in the XML and constrained to the top and bottom of the parent layout. It is permanently full-screen.
* **Floating Header Overlay:** The `toolbar` and `progressBar` overlay directly on top of the web content. 
* **State Animations:**
  * **Hidden State:** The toolbar translates visually off-screen using hardware-accelerated translation: `translationY = -toolbarHeight`.
  * **Visible State:** The toolbar slides down smoothly: `translationY = 0f`.
* **Zero Layout Reflows:** Showing or hiding the address bar must **never** force the webpage to resize or redraw.

---

## 🖱️ 2. Bluetooth Pointer Hover Interception

To facilitate mouse/trackpad interaction on TV or spatial computing platforms:

* **Global Interception:** WebViews consume hover events to handle internal web styling, which blocks standard view hover listeners. Pointer events must be globally captured at the Activity level via `dispatchGenericMotionEvent` before child views process them.
* **Top 5% Screen Height Threshold:** The toolbar is automatically revealed when the pointer coordinate is in the top **5%** of the screen's measured height:
  $$\text{thresholdPx} = \text{rootHeight} \times 0.05$$
* **Auto-Hide:** Moving the cursor below the top 5% boundary automatically slides the toolbar up and out of view.

---

## 🎮 3. D-Pad Highlight Navigation

To keep the TV viewing experience premium and uncluttered:

* **Focus-Only Navigation:** Navigating to the address bar or navigation buttons via D-pad directional/arrow keys (**UP, DOWN, LEFT, RIGHT**) slides the toolbar down smoothly but **does not summon the soft keyboard**. The keyboard remains hidden so the user can freely check the URL or highlight adjacent toolbar buttons.
* **D-Pad UP & DOWN Triple-Press Triggers:** To prevent accidental layout shifts and support natural webpage scrolling:
  * **Triple D-Pad UP:** When focus is inside the web page (`engineView`) and the toolbar is hidden, pressing the **D-Pad UP** key **three times in quick succession** (each click within 500ms) slides the toolbar down and places focus directly into the URL input bar. Normal single or double presses scroll the webpage up normally.
  * **Triple D-Pad DOWN:** When the toolbar is visible, pressing the **D-Pad DOWN** key **three times in quick succession** (each click within 500ms) slides the toolbar up, dismisses the virtual keyboard, and returns focus directly to the webpage viewport. Normal single or double presses move focus down normally.

---

## ⌨️ 4. Keyboard (IME) Summoning Rules

The on-screen virtual keyboard is only displayed on deliberate, intentional actions:

* **Explicit Triggers:** The soft keyboard is **only** summoned under the following two conditions:
  1. The address bar currently has focus, and the user presses the remote's **D-Pad OK/Center** button (`KEYCODE_DPAD_CENTER`).
  2. The user explicitly **clicks** (via mouse or touch) on the address bar.
* **Animation-Aware Delay:** If the toolbar is sliding down, keyboard summoning is safely posted with a `300ms` delay (matching the translation animation duration) to guarantee the view is fully settled on-screen. This ensures Android's `InputMethodManager` never rejects the keyboard request.

---

## ❌ 5. Keyboard Dismissal & Focus Escape

Getting out of input mode must feel extremely fast and clean.

* **One-Click BACK Button Dismissal:** Pressing the remote's **BACK** button (`KEYCODE_BACK`) while the toolbar or keyboard has focus instantly:
  1. Hides the on-screen keyboard.
  2. Clears focus from the toolbar.
  3. Returns focus directly to the main web page viewport (`engineView`).
  4. Slides the toolbar smoothly back up off-screen.
* **Click-Outside Dismissal:** Clicking or touching anywhere on the web page viewport (`engineView`) outside the toolbar space automatically transfers focus to the web viewport and dismisses the soft keyboard.
* **Search / Enter Action:** Pressing "Enter", "Search", or "Go" on either a virtual or hardware keyboard:
  1. Navigates the browser to the entered address.
  2. Dismisses the keyboard.
  3. Returns focus back to the webpage.

---

## 🏠 6. Home & Settings Toolbar Buttons

To improve rapid navigation and system adjustment:

* **Home Button:** Positioned at the right end of the address bar within the horizontal toolbar. 
  * **Default Address:** Clicking or arrow-selecting and activating the Home button loads `about:blank` as the default home page in the active tab session.
* **Settings Button:** Positioned immediately to the right of the Home button.
  * **Default Action:** Clicking or activating it displays a short Toast notification confirming MVP Mode (to be integrated with full configuration menus in Phase 2).

---

## 📺 7. TV Remote Physical Media Playback Controls

To provide full hardware-based control over playing video and audio elements on websites (such as YouTube, Netflix, custom streams, etc.):

* **Media Key Interception:** Standard TV remote media events are captured at the Activity level and dispatched directly to the active web session's underlying Android `WebView`.
* **DOM JavaScript Injection:** Media control runs high-performance JS queries across all video and audio tags in the document:
  * **Play (`KEYCODE_MEDIA_PLAY`):** Invokes `play()` on all HTML5 media elements.
  * **Pause (`KEYCODE_MEDIA_PAUSE`):** Invokes `pause()` on all HTML5 media elements.
  * **Toggle Play/Pause (`KEYCODE_MEDIA_PLAY_PAUSE`):** Toggles `play()` or `pause()` depending on the element's current `.paused` state.
  * **Fast Forward (`KEYCODE_MEDIA_FAST_FORWARD`, `KEYCODE_MEDIA_NEXT`):** Fast forwards all active media playback elements by **10 seconds** (`currentTime += 10`).
  * **Rewind (`KEYCODE_MEDIA_REWIND`, `KEYCODE_MEDIA_PREVIOUS`):** Rewinds all active media playback elements by **10 seconds** (`currentTime -= 10`).

---

## 🎯 8. D-Pad Webpage Spatial Navigation

To ensure a seamless browsing experience when the address bar is hidden:

* **Focus Delegation:** When the floating toolbar is hidden, focus is returned entirely to the `engineView` (Android `WebView`).
* **Spatial Navigation:** Users can use the D-pad arrows to freely navigate between clickable HTML elements (e.g., links, buttons, inputs) on the webpage.
* **Visual Selection Outline:** A global CSS rule is automatically injected into every loaded webpage to provide a highly visible focus indicator:
  * Selected elements are highlighted with a prominent **4px solid orange (#FF9800)** outline with a shadow, ensuring clear visibility on TVs from a distance.
* **Automatic Focus Restoration:** When the toolbar is hidden:
  * **Last Selected Element:** If the user has already navigated the webpage, focus is instantly restored to the **last selected clickable HTML element** to resume browsing smoothly.
  * **Topmost Element:** If the page has just loaded (or no element has been focused yet), focus is automatically assigned to the **topmost focusable HTML element** (e.g., the first link or button on the page) so D-pad navigation can begin instantly.
