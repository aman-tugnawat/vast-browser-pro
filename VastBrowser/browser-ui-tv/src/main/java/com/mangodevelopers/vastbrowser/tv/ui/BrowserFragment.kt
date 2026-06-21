/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package com.mangodevelopers.vastbrowser.tv.ui

import android.transition.TransitionManager
import android.transition.TransitionSet
import android.transition.Slide
import android.transition.ChangeBounds
import android.view.Gravity
import android.view.MotionEvent
import android.view.ViewTreeObserver
import android.os.Bundle
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import mozilla.components.browser.state.action.TabListAction
import mozilla.components.browser.state.selector.selectedTab
import mozilla.components.browser.state.state.createTab
import mozilla.components.concept.engine.EngineView
import mozilla.components.feature.session.SessionFeature
import mozilla.components.lib.state.ext.flow
import com.mangodevelopers.vastbrowser.tv.R
import com.mangodevelopers.vastbrowser.tv.components
import com.mangodevelopers.vastbrowser.tv.databinding.FragmentBrowserBinding

private const val ARG_URL = "initial_url"
private const val HOME_URL = "about:blank"

/**
 * Fragment containing the browser engine view and URL bar.
 * Wires the EngineView to the BrowserStore via SessionFeature.
 */
class BrowserFragment : Fragment() {

    companion object {
        fun create(initialUrl: String? = null) = BrowserFragment().apply {
            arguments = Bundle().apply {
                putString(ARG_URL, initialUrl)
            }
        }
    }

    private var _binding: FragmentBrowserBinding? = null
    private val binding get() = _binding!!

    private var sessionFeature: SessionFeature? = null

    private val components by lazy { requireContext().components }

    private var isCursorNearTop = false
    private var isToolbarVisible = true

    private val focusChangeListener = ViewTreeObserver.OnGlobalFocusChangeListener { _, _ ->
        updateToolbarVisibility()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentBrowserBinding.inflate(inflater, container, false)
        return binding.root
    }

    private lateinit var engineView: EngineView
    private lateinit var engineNativeView: View

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        engineView = components.engine.createView(requireContext())
        engineNativeView = engineView.asView()
        engineNativeView.isFocusable = true
        engineNativeView.isFocusableInTouchMode = true
        binding.engineContainer.addView(
            engineNativeView,
            ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        )

        // Create initial tab if none exists
        val prefs = requireContext().getSharedPreferences("vast_browser_prefs", android.content.Context.MODE_PRIVATE)
        val lastOpenPage = prefs.getString("pref_last_open_page", null)
        val homePageUrl = lastOpenPage ?: prefs.getString("pref_home_page", HOME_URL) ?: HOME_URL
        
        var initialUrl = arguments?.getString(ARG_URL)
        if (initialUrl == null || initialUrl == HOME_URL) {
            initialUrl = homePageUrl
        }
        
        if (components.store.state.selectedTabId == null) {
            val tab = createTab(url = initialUrl)
            components.store.dispatch(TabListAction.AddTabAction(tab, select = true))
        }

        // Wire EngineView ↔ BrowserStore
        sessionFeature = SessionFeature(
            store = components.store,
            goBackUseCase = components.sessionUseCases.goBack,
            goForwardUseCase = components.sessionUseCases.goForward,
            engineView = engineView,
        )

        setupUrlBar()
        observeState()

        // Listen to focus changes to show/hide the toolbar
        binding.root.viewTreeObserver.addOnGlobalFocusChangeListener(focusChangeListener)

        // Request initial focus to URL bar to ensure D-Pad has a starting point
        binding.urlInput.requestFocus()
    }

    override fun onStart() {
        super.onStart()
        sessionFeature?.start()
    }

    override fun onStop() {
        super.onStop()
        sessionFeature?.stop()
    }

    override fun onDestroyView() {
        _binding?.root?.viewTreeObserver?.removeOnGlobalFocusChangeListener(focusChangeListener)
        sessionFeature = null
        _binding = null
        super.onDestroyView()
    }

    /**
     * Attempt to go back in browser history.
     * @return true if back was handled
     */
    fun onBackPressed(): Boolean {
        if (isScrollModeActive) {
            isScrollModeActive = false
            updateCursorVisuals()
            return true
        }
        if (binding.toolbar.hasFocus()) {
            engineNativeView.requestFocus()
            val imm = requireContext().getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
            imm.hideSoftInputFromWindow(binding.urlInput.windowToken, 0)
            return true
        }
        if (currentBackDialog?.isShowing == true) {
            currentBackDialog?.dismiss()
            return true
        }
        showBackDialog()
        return true
    }

    fun exitAppAndSaveState() {
        val context = context ?: return
        val currentUrl = components.store.state.selectedTab?.content?.url ?: ""
        val prefs = context.getSharedPreferences("vast_browser_prefs", android.content.Context.MODE_PRIVATE)
        if (currentUrl.isNotEmpty() && currentUrl != "about:blank") {
            prefs.edit().putString("pref_last_open_page", currentUrl).commit()
        }
        
        // Clear cache
        try {
            components.engine.clearData(mozilla.components.concept.engine.Engine.BrowsingData.Companion.allCaches())
        } catch (e: Exception) {
            android.util.Log.e("BrowserFragment", "Failed to clear engine cache", e)
        }
        
        // Exit
        requireActivity().finish()
    }

    private fun showBackDialog() {
        val context = context ?: return
        
        val dialog = androidx.appcompat.app.AlertDialog.Builder(context)
            .setTitle("Vast Browser")
            .setMessage("What would you like to do?")
            .setPositiveButton("Exit App") { _, _ ->
                exitAppAndSaveState()
            }
            .setNegativeButton("Previous Webpage") { _, _ ->
                components.sessionUseCases.goBack()
            }
            .create()
            
        dialog.setOnShowListener {
            dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).requestFocus()
        }
        dialog.setOnDismissListener {
            currentBackDialog = null
        }
        
        currentBackDialog = dialog
        dialog.show()
    }

    fun loadUrl(url: String) {
        if (url.isNotBlank()) {
            components.sessionUseCases.loadUrl(url)
        }
    }

    private fun setupUrlBar() {
        binding.urlInput.setOnEditorActionListener { textView, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_GO ||
                actionId == EditorInfo.IME_ACTION_DONE ||
                actionId == EditorInfo.IME_ACTION_SEARCH ||
                actionId == EditorInfo.IME_ACTION_UNSPECIFIED ||
                event?.keyCode == KeyEvent.KEYCODE_ENTER
            ) {
                val rawInput = textView.text.toString().trim()
                val url = normalizeUrl(rawInput)
                loadUrl(url)

                // Hide keyboard
                val imm = requireContext().getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
                imm.hideSoftInputFromWindow(binding.urlInput.windowToken, 0)

                // Clear focus back to the engine view
                engineNativeView.requestFocus()
                true
            } else {
                false
            }
        }

        // On TV, EditText doesn't always automatically summon the Leanback IME on focus/click
        val showIme = {
            if (isAdded && _binding != null) {
                val imm = requireContext().getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
                binding.urlInput.requestFocus()
                imm.showSoftInput(binding.urlInput, android.view.inputmethod.InputMethodManager.SHOW_FORCED)
            }
        }
        
        binding.urlInput.setOnClickListener { showIme() }
        
        // Listen to DPAD OK/Center button to explicitly trigger the virtual keyboard
        binding.urlInput.setOnKeyListener { _, keyCode, event ->
            if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER && event.action == KeyEvent.ACTION_DOWN) {
                showIme()
                true
            } else {
                false
            }
        }
        binding.urlInput.setOnFocusChangeListener { _, hasFocus -> 
            if (!hasFocus && isAdded && _binding != null) {
                val imm = requireContext().getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
                imm.hideSoftInputFromWindow(binding.urlInput.windowToken, 0)
            }
        }

        engineNativeView.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_DOWN) {
                if (_binding != null && binding.urlInput.hasFocus()) {
                    engineNativeView.requestFocus()
                }
            }
            false
        }

        // Navigation button listeners
        binding.buttonBack.setOnClickListener {
            components.sessionUseCases.goBack()
        }
        binding.buttonForward.setOnClickListener {
            components.sessionUseCases.goForward()
        }
        binding.buttonReload.setOnClickListener {
            components.sessionUseCases.reload()
        }
        binding.buttonHome.setOnClickListener {
            val prefs = requireContext().getSharedPreferences("vast_browser_prefs", android.content.Context.MODE_PRIVATE)
            val homePageUrl = prefs.getString("pref_home_page", "about:blank") ?: "about:blank"
            loadUrl(homePageUrl)
        }
        binding.buttonSettings.setOnClickListener {
            requireActivity().supportFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, SettingsFragment())
                .addToBackStack(null)
                .commit()
        }
    }

    private fun observeState() {
        viewLifecycleOwner.lifecycleScope.launch {
            // Observe URL changes and update the URL bar
            components.store.flow(viewLifecycleOwner)
                .map { state -> state.selectedTab?.content?.url ?: "" }
                .distinctUntilChanged()
                .collect { url ->
                    if (!binding.urlInput.isFocused) {
                        binding.urlInput.setText(url)
                    }
                }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            // Observe loading state for progress bar
            components.store.flow(viewLifecycleOwner)
                .map { state -> state.selectedTab?.content?.loading ?: false }
                .distinctUntilChanged()
                .collect { loading ->
                    binding.progressBar.visibility = if (loading) View.VISIBLE else View.GONE
                    if (!loading) {
                        injectFocusCss()
                        // Inject plugin content scripts (ad blocker, tracker blocker)
                        val currentUrl = components.store.state.selectedTab?.content?.url ?: ""
                        getWebView()?.let { wv ->
                            components.pluginManager.injectPluginScripts(wv, currentUrl)
                        }
                        if (!isToolbarVisible && !isCursorMode) {
                            getWebView()?.requestFocus()
                            restoreWebpageFocus()
                        }
                    }
                }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            // Observe progress
            components.store.flow(viewLifecycleOwner)
                .map { state -> state.selectedTab?.content?.progress ?: 0 }
                .distinctUntilChanged()
                .collect { progress ->
                    binding.progressBar.progress = progress
                }
        }
    }

    /**
     * Normalizes user input into a valid URL.
     * If it looks like a search query, wraps in a DuckDuckGo search.
     * If it looks like a domain, prepends https://.
     */
    private fun normalizeUrl(input: String): String {
        if (input.startsWith("http://") || input.startsWith("https://") || input.startsWith("about:")) {
            return input
        }
        // Simple heuristic: if it contains a dot and no spaces, treat as URL
        if (input.contains(".") && !input.contains(" ")) {
            return "https://$input"
        }
        // Otherwise, treat as a search query
        return "https://duckduckgo.com/?q=${java.net.URLEncoder.encode(input, "UTF-8")}"
    }

    /**
     * Show the top toolbar, optionally requesting focus to the URL bar.
     */
    fun showToolbar(focusUrlBar: Boolean = false) {
        val binding = _binding ?: return
        if (!isToolbarVisible) {
            isToolbarVisible = true
            animateToolbar(true)
        }
        if (focusUrlBar) {
            binding.urlInput.requestFocus()
        }
    }

    fun isToolbarVisible(): Boolean {
        return isToolbarVisible
    }

    fun isInScrollMode(): Boolean {
        return isScrollModeActive
    }

    /**
     * Hide the top toolbar, dismiss keyboard, and focus webpage.
     */
    fun hideToolbar() {
        val binding = _binding ?: return
        if (isToolbarVisible) {
            isToolbarVisible = false
            animateToolbar(false)
        }
        getWebView()?.requestFocus()
        val imm = requireContext().getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
        imm.hideSoftInputFromWindow(binding.urlInput.windowToken, 0)
        
        if (!isCursorMode) {
            restoreWebpageFocus()
        }
    }

    /**
     * Update the visibility of the top toolbar based on focus and cursor position.
     */
    private fun updateToolbarVisibility() {
        val binding = _binding ?: return
        val shouldShow = binding.toolbar.hasFocus() || isCursorNearTop
        if (shouldShow != isToolbarVisible) {
            isToolbarVisible = shouldShow
            animateToolbar(shouldShow)
            if (!shouldShow && !isCursorMode) {
                restoreWebpageFocus()
            }
        }
    }

    /**
     * Slide the toolbar and progress bar up or down smoothly.
     */
    private fun animateToolbar(show: Boolean) {
        val binding = _binding ?: return
        
        val transition = TransitionSet()
            .addTransition(Slide(Gravity.TOP).addTarget(binding.toolbar).addTarget(binding.progressBar))
            .addTransition(ChangeBounds().addTarget(engineNativeView))
            .setDuration(300)
            
        TransitionManager.beginDelayedTransition(binding.root as ViewGroup, transition)
        
        binding.toolbar.visibility = if (show) View.VISIBLE else View.GONE
        
        // Ensure progress bar hides completely if toolbar hides, else let the state observer manage it.
        if (!show) {
            binding.progressBar.visibility = View.GONE
        } else {
            binding.progressBar.visibility = if (components.store.state.selectedTab?.content?.loading == true) View.VISIBLE else View.GONE
        }
    }

    /**
     * Handle global generic motion events (like mouse hovers) forwarded from the Activity.
     */
    fun handleHoverEvent(event: MotionEvent) {
        val binding = _binding ?: return
        val action = event.actionMasked
        if (action == MotionEvent.ACTION_HOVER_MOVE || action == MotionEvent.ACTION_HOVER_ENTER) {
            val location = IntArray(2)
            binding.root.getLocationOnScreen(location)
            val relativeY = event.rawY - location[1]

            val rootHeight = binding.root.height
            val thresholdPx = if (rootHeight > 0) rootHeight * 0.05f else 100 * resources.displayMetrics.density

            val nearTop = relativeY < thresholdPx
            if (isCursorNearTop != nearTop) {
                isCursorNearTop = nearTop
                updateToolbarVisibility()
            }
        } else if (action == MotionEvent.ACTION_HOVER_EXIT) {
            if (isCursorNearTop) {
                isCursorNearTop = false
                updateToolbarVisibility()
            }
        }
    }

    private fun findWebView(view: android.view.View): android.webkit.WebView? {
        if (view is android.webkit.WebView) {
            return view
        }
        if (view is android.view.ViewGroup) {
            for (i in 0 until view.childCount) {
                val child = view.getChildAt(i)
                val wv = findWebView(child)
                if (wv != null) {
                    return wv
                }
            }
        }
        return null
    }

    private fun getWebView(): android.webkit.WebView? {
        val binding = _binding ?: return null
        return findWebView(engineNativeView)
    }

    /**
     * Evaluate JavaScript to play, pause, fast forward, or rewind HTML5 media in the active tab.
     */
    fun executeMediaAction(action: String) {
        val webView = getWebView() ?: return
        val js = when (action) {
            "play" -> """
                (function() {
                    var mediaElements = document.querySelectorAll('video, audio');
                    for (var i = 0; i < mediaElements.length; i++) {
                        mediaElements[i].play();
                    }
                })()
            """.trimIndent()
            "pause" -> """
                (function() {
                    var mediaElements = document.querySelectorAll('video, audio');
                    for (var i = 0; i < mediaElements.length; i++) {
                        mediaElements[i].pause();
                    }
                })()
            """.trimIndent()
            "toggle" -> """
                (function() {
                    var mediaElements = document.querySelectorAll('video, audio');
                    for (var i = 0; i < mediaElements.length; i++) {
                        if (mediaElements[i].paused) {
                            mediaElements[i].play();
                        } else {
                            mediaElements[i].pause();
                        }
                    }
                })()
            """.trimIndent()
            "forward" -> """
                (function() {
                    var mediaElements = document.querySelectorAll('video, audio');
                    for (var i = 0; i < mediaElements.length; i++) {
                        mediaElements[i].currentTime += 10;
                    }
                })()
            """.trimIndent()
            "backward" -> """
                (function() {
                    var mediaElements = document.querySelectorAll('video, audio');
                    for (var i = 0; i < mediaElements.length; i++) {
                        mediaElements[i].currentTime -= 10;
                    }
                })()
            """.trimIndent()
            else -> return
        }

        webView.evaluateJavascript(js, null)
    }

    /**
     * Injects a global CSS style rule into the WebView so that any HTML element
     * currently focused by the D-pad receives a prominent visual outline.
     * Also sets up a listener to track the last focused element.
     */
    private fun injectFocusCss() {
        val webView = getWebView() ?: return
        val js = """
            (function() {
                if (document.getElementById('tv-focus-style')) {
                    document.getElementById('tv-focus-style').remove();
                }
                var style = document.createElement('style');
                style.id = 'tv-focus-style';
                style.innerHTML = `
                    /* TV highlight uses a custom class so input fields can be
                       visually highlighted WITHOUT activating text input mode.
                       Text input only activates on explicit OK/Enter press. */
                    .tv-highlighted {
                        box-shadow: inset 0 0 0 4px #FF9800, 0 0 12px 2px rgba(255,152,0,0.5) !important;
                        outline: none !important;
                        transform: scale(1.03) !important;
                        z-index: 9999 !important;
                        position: relative !important;
                        transition: transform 0.15s ease, box-shadow 0.15s ease !important;
                    }
                `;
                document.head.appendChild(style);

                if (!window.hasTvFocusTracker) {
                    window.hasTvFocusTracker = true;
                    window.lastFocusedElement = null;
                    window.prevFocusedElement = null;
                    window.tvHighlightedElement = null;
                }

                if (!window.hasTvSpatialNav) {
                    window.hasTvSpatialNav = true;

                    var FOCUSABLE = 'a, button, input, select, textarea, [tabindex]:not([tabindex="-1"]), [role="button"], [role="link"], [role="menuitem"], [role="tab"], [onclick]';
                    var INPUT_TAGS = {'INPUT':1, 'TEXTAREA':1, 'SELECT':1};

                    function getFocusableElements() {
                        var all = Array.from(document.querySelectorAll(FOCUSABLE))
                            .filter(function(el) {
                                var rect = el.getBoundingClientRect();
                                if (rect.width <= 0 || rect.height <= 0) return false;
                                var cs = window.getComputedStyle(el);
                                if (cs.visibility === 'hidden' || cs.display === 'none' || cs.pointerEvents === 'none') return false;
                                if (el.disabled) return false;
                                if (rect.width < 5 && rect.height < 5) return false;
                                return true;
                            });

                        return all.filter(function(el) {
                            var rect = el.getBoundingClientRect();
                            var cx = rect.left + rect.width / 2;
                            var cy = rect.top + rect.height / 2;
                            if (cx < 0 || cy < 0 || cx > window.innerWidth || cy > window.innerHeight) return false;
                            var topEl = document.elementFromPoint(cx, cy);
                            if (!topEl) return false;
                            return el === topEl || el.contains(topEl) || topEl.contains(el);
                        });
                    }

                    function dedup(elements) {
                        var out = [];
                        var ALWAYS_KEEP = {'INPUT':1, 'SELECT':1, 'TEXTAREA':1, 'BUTTON':1};
                        for (var i = 0; i < elements.length; i++) {
                            var el = elements[i];
                            if (ALWAYS_KEEP[el.tagName]) {
                                out.push(el);
                                continue;
                            }
                            var hasChildFocusable = false;
                            for (var j = 0; j < elements.length; j++) {
                                if (i !== j && el.contains(elements[j]) && el !== elements[j]) {
                                    hasChildFocusable = true;
                                    break;
                                }
                            }
                            if (!hasChildFocusable) out.push(el);
                        }
                        return out;
                    }

                    function findNextElement(direction) {
                        var current = window.tvHighlightedElement || document.activeElement;
                        var elements = dedup(getFocusableElements());
                        if (elements.length === 0) return null;

                        if (!current || current === document.body || current === document.documentElement) {
                            return elements[0];
                        }

                        var resolved = current;
                        while (resolved && !elements.includes(resolved) && resolved !== document.body) {
                            resolved = resolved.parentElement;
                        }
                        if (!resolved || resolved === document.body) return elements[0];

                        var aRect = resolved.getBoundingClientRect();
                        var aCx = aRect.left + aRect.width / 2;
                        var aCy = aRect.top + aRect.height / 2;
                        var bestMatch = null;
                        var bestScore = Infinity;

                        for (var i = 0; i < elements.length; i++) {
                            var el = elements[i];
                            if (el === resolved) continue;
                            var rect = el.getBoundingClientRect();
                            var cx = rect.left + rect.width / 2;
                            var cy = rect.top + rect.height / 2;
                            var dx = cx - aCx;
                            var dy = cy - aCy;

                            var ok = false;
                            if (direction === 'ArrowDown')  ok = dy > 5;
                            if (direction === 'ArrowUp')    ok = dy < -5;
                            if (direction === 'ArrowRight') ok = dx > 5;
                            if (direction === 'ArrowLeft')  ok = dx < -5;
                            if (!ok) continue;

                            var score;
                            if (direction === 'ArrowDown' || direction === 'ArrowUp') {
                                score = Math.abs(dy) + Math.abs(dx) * 3;
                            } else {
                                score = Math.abs(dx) + Math.abs(dy) * 3;
                            }

                            if (score < bestScore) {
                                bestScore = score;
                                bestMatch = el;
                            }
                        }
                        return bestMatch;
                    }

                    function simulateHover(el, prevEl) {
                        if (prevEl && prevEl !== el) {
                            prevEl.dispatchEvent(new MouseEvent('mouseleave', {bubbles: true, cancelable: true}));
                            prevEl.dispatchEvent(new MouseEvent('mouseout',   {bubbles: true, cancelable: true}));
                        }
                        el.dispatchEvent(new MouseEvent('mouseenter', {bubbles: true, cancelable: true}));
                        el.dispatchEvent(new MouseEvent('mouseover',  {bubbles: true, cancelable: true}));
                    }

                    /* Highlight an element visually without activating input fields */
                    function highlightElement(el) {
                        var prev = window.tvHighlightedElement;
                        if (prev) {
                            prev.classList.remove('tv-highlighted');
                        }
                        el.classList.add('tv-highlighted');
                        window.tvHighlightedElement = el;
                        window.lastFocusedElement = el;

                        /* For non-input elements, call .focus() so Enter/OK triggers click.
                           For input/textarea/select, do NOT call .focus() to avoid
                           activating text input mode — just highlight visually. */
                        if (!INPUT_TAGS[el.tagName]) {
                            if (!el.hasAttribute('tabindex')) {
                                el.setAttribute('tabindex', '-1');
                            }
                            el.focus({preventScroll: true});
                        } else {
                            /* Blur any previously focused input */
                            if (document.activeElement && INPUT_TAGS[document.activeElement.tagName]) {
                                document.activeElement.blur();
                            }
                        }

                        simulateHover(el, prev);
                        el.scrollIntoView({block: 'nearest', inline: 'nearest', behavior: 'smooth'});
                    }

                    /* Arrow key handler — navigate between elements */
                    window.addEventListener('keydown', function(e) {
                        if (['ArrowUp', 'ArrowDown', 'ArrowLeft', 'ArrowRight'].includes(e.key)) {
                            var nextEl = findNextElement(e.key);
                            if (nextEl) {
                                e.preventDefault();
                                e.stopImmediatePropagation();
                                highlightElement(nextEl);
                            }
                        }

                        /* OK / Enter handler — activate input fields when pressed */
                        if (e.key === 'Enter') {
                            var highlighted = window.tvHighlightedElement;
                            if (highlighted && INPUT_TAGS[highlighted.tagName]) {
                                e.preventDefault();
                                e.stopImmediatePropagation();
                                highlighted.focus();
                            }
                        }
                    }, true);
                }
            })();
        """.trimIndent()
        webView.evaluateJavascript(js, null)
    }

    /**
     * Focuses the last selected webpage HTML element, or the topmost focusable element
     * if the page was just loaded or has no previous focus.
     */
    fun restoreWebpageFocus() {
        val webView = getWebView() ?: return
        val js = """
            (function() {
                window.focus();
                var target = null;
                if (window.tvHighlightedElement && document.body.contains(window.tvHighlightedElement)) {
                    target = window.tvHighlightedElement;
                } else if (window.lastFocusedElement && document.body.contains(window.lastFocusedElement)) {
                    target = window.lastFocusedElement;
                } else {
                    var focusables = document.querySelectorAll('a, button, input, select, textarea, [tabindex="0"]');
                    if (focusables.length > 0) {
                        target = focusables[0];
                    }
                }
                if (target && typeof highlightElement === 'function') {
                    highlightElement(target);
                } else if (target) {
                    /* Fallback: add class directly if highlightElement isn't available yet */
                    target.classList.add('tv-highlighted');
                    window.tvHighlightedElement = target;
                    window.lastFocusedElement = target;
                    var INPUT_TAGS = {'INPUT':1, 'TEXTAREA':1, 'SELECT':1};
                    if (!INPUT_TAGS[target.tagName]) {
                        target.focus({preventScroll: true});
                    }
                }
            })();
        """.trimIndent()
        webView.evaluateJavascript(js, null)
    }

    private var cursorX = 0f
    private var cursorY = 0f
    private var cursorSpeed = 45f
    private var isCursorMode = false
    private var isScrollModeActive = false
    private var cursorTimeoutMs = 3000L
    private var scrollHoldDurationMs = 2000L
    private var currentBackDialog: androidx.appcompat.app.AlertDialog? = null
    private val cursorHideRunnable = Runnable {
        if (!isScrollModeActive) {
            val binding = _binding
            if (binding != null) {
                binding.cursorGroup.visibility = View.GONE
                binding.cursorTooltip.visibility = View.GONE
                
                // Dispatch native hover exit outside the view boundaries to clear hover states
                dispatchNativeHoverExit(-1000f, -1000f)
                
                getWebView()?.evaluateJavascript("""
                    (function() {
                        // Dispatch mousemove outside viewport to trigger mouseout/mouseleave on any element (including iframes)
                        try {
                            var exitEvent = new MouseEvent('mousemove', { bubbles: true, cancelable: true, view: window, clientX: -1000, clientY: -1000 });
                            window.dispatchEvent(exitEvent);
                        } catch(e) {}
                        
                        var lastEl = window.tvLastHoveredElement;
                        if (lastEl) {
                            try {
                                var outEvent = new MouseEvent('mouseout', { bubbles: true, cancelable: true, view: window });
                                lastEl.dispatchEvent(outEvent);
                                var leaveEvent = new MouseEvent('mouseleave', { bubbles: false, cancelable: true, view: window });
                                lastEl.dispatchEvent(leaveEvent);
                            } catch(e) {}
                            window.tvLastHoveredElement = null;
                        }
                    })()
                """.trimIndent(), null)
            }
        }
    }
    
    private val hoverRunnable = Runnable {
        updateHoverTooltip()
    }
    
    private var isCenterDown = false
    private val enterScrollModeRunnable = Runnable {
        if (isCenterDown && _binding != null) {
            isScrollModeActive = !isScrollModeActive
            updateCursorVisuals()
            // Show status text
            showScrollModeText(if (isScrollModeActive) "Scroll mode started." else "Scroll mode ended.")
            // Vibrate
            try {
                val vibrator = requireContext().getSystemService(android.content.Context.VIBRATOR_SERVICE) as? android.os.Vibrator
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    vibrator?.vibrate(android.os.VibrationEffect.createOneShot(50, android.os.VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator?.vibrate(50)
                }
            } catch (e: Exception) {
                // Ignore vibration failure
            }
        }
    }
    
    private fun showScrollModeText(message: String) {
        val binding = _binding ?: return
        binding.scrollModeText.text = message
        binding.scrollModeText.alpha = 1f
        binding.scrollModeText.animate()
            .alpha(0f)
            .setStartDelay(500)
            .setDuration(500)
            .start()
    }
    
    private fun updateCursorVisuals() {
        val binding = _binding ?: return
        val cursor = binding.cursorGroup
        if (isScrollModeActive) {
            binding.cursorRing.visibility = View.GONE
            binding.cursorDisk.visibility = View.VISIBLE
            // Keep cursor always visible in scroll mode
            cursor.visibility = View.VISIBLE
            cursor.removeCallbacks(cursorHideRunnable)
            // Animate all arrows to fade out and scale up
            val arrows = listOf(binding.cursorArrowUp, binding.cursorArrowDown, binding.cursorArrowLeft, binding.cursorArrowRight)
            for (arrow in arrows) {
                arrow.animate().cancel()
                arrow.alpha = 1f
                arrow.scaleX = 0.5f
                arrow.scaleY = 0.5f
                arrow.animate().alpha(0f).scaleX(1.5f).scaleY(1.5f).setDuration(1000).start()
            }
        } else {
            binding.cursorRing.visibility = View.VISIBLE
            binding.cursorDisk.visibility = View.GONE
            // Restart the hide timeout
            cursor.removeCallbacks(cursorHideRunnable)
            cursor.postDelayed(cursorHideRunnable, cursorTimeoutMs)
        }
    }
    
    private fun animateScrollArrow(keyCode: Int) {
        val binding = _binding ?: return
        val arrow = when (keyCode) {
            KeyEvent.KEYCODE_DPAD_UP -> binding.cursorArrowUp
            KeyEvent.KEYCODE_DPAD_DOWN -> binding.cursorArrowDown
            KeyEvent.KEYCODE_DPAD_LEFT -> binding.cursorArrowLeft
            KeyEvent.KEYCODE_DPAD_RIGHT -> binding.cursorArrowRight
            else -> null
        } ?: return
        
        arrow.animate().cancel()
        arrow.alpha = 1f
        arrow.scaleX = 0.5f
        arrow.scaleY = 0.5f
        arrow.animate().alpha(0f).scaleX(1.5f).scaleY(1.5f).setDuration(500).start()
    }

    override fun onResume() {
        super.onResume()
        val prefs = requireContext().getSharedPreferences("vast_browser_prefs", android.content.Context.MODE_PRIVATE)
        isCursorMode = prefs.getString("pref_nav_method", "dpad_cursor") == "dpad_cursor"
        cursorTimeoutMs = prefs.getInt("pref_cursor_timeout", 3000).toLong()
        scrollHoldDurationMs = prefs.getInt("pref_scroll_hold_duration", 2000).toLong()
        val speedOption = prefs.getString("pref_cursor_speed", "fast") ?: "fast"
        cursorSpeed = when (speedOption) {
            "slow" -> 15f
            "medium" -> 30f
            "fast" -> 45f
            "faster" -> 60f
            "fastest" -> 75f
            else -> 45f
        }
        
        if (!isCursorMode) {
            _binding?.cursorRing?.visibility = View.GONE
            _binding?.cursorTooltip?.visibility = View.GONE
            engineNativeView.isFocusable = true
        } else {
            // In cursor mode, the engineView shouldn't trap DPAD focus natively
            engineNativeView.isFocusable = false
        }

        // Check for update notification dot on settings button
        updateSettingsNotificationDot()
    }

    /**
     * Show or hide a notification dot on the Settings toolbar button
     * indicating a new app version is available.
     */
    private fun updateSettingsNotificationDot() {
        val binding = _binding ?: return
        val updatePrefs = requireContext().getSharedPreferences("vast_browser_updates", android.content.Context.MODE_PRIVATE)
        val availableVersion = updatePrefs.getString("update_available_version", null)
        val notificationDot = binding.settingsNotificationDot
        
        if (availableVersion != null) {
            try {
                val currentVersion = com.mangodevelopers.vastbrowser.tv.BuildConfig.VERSION_NAME
                val availParts = availableVersion.removePrefix("v").split("-")[0].split(".").map { it.toIntOrNull() ?: 0 }
                val currentParts = currentVersion.removePrefix("v").split("-")[0].split(".").map { it.toIntOrNull() ?: 0 }
                var isNewer = false
                for (i in 0 until maxOf(availParts.size, currentParts.size)) {
                    val a = availParts.getOrElse(i) { 0 }
                    val c = currentParts.getOrElse(i) { 0 }
                    if (a > c) { isNewer = true; break }
                    if (a < c) break
                }
                notificationDot.visibility = if (isNewer) View.VISIBLE else View.GONE
            } catch (_: Exception) {
                notificationDot.visibility = View.GONE
            }
        } else {
            notificationDot.visibility = View.GONE
        }
    }

    fun handleDpadEvent(event: KeyEvent): Boolean {
        if (!isCursorMode) return false
        val binding = _binding ?: return false

        if (binding.toolbar.hasFocus()) {
            if (event.keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                // User is pressing DOWN to leave toolbar. Clear focus so cursor can move.
                binding.toolbar.clearFocus()
            } else {
                return false
            }
        }

        val cursor = binding.cursorGroup

        if (event.keyCode == KeyEvent.KEYCODE_DPAD_CENTER || event.keyCode == KeyEvent.KEYCODE_ENTER) {
            if (event.action == KeyEvent.ACTION_DOWN) {
                if (event.repeatCount == 0) {
                    isCenterDown = true
                    cursor.postDelayed(enterScrollModeRunnable, scrollHoldDurationMs)
                }
                return true
            } else if (event.action == KeyEvent.ACTION_UP) {
                isCenterDown = false
                cursor.removeCallbacks(enterScrollModeRunnable)
                
                if (!isScrollModeActive) {
                    // Perform normal click - coordinates must be relative to engineView
                    val touchX = cursorX + binding.cursorGroup.width / 2f
                    val touchY = cursorY + binding.cursorGroup.height / 2f - engineNativeView.top
                    val uptime = android.os.SystemClock.uptimeMillis()
                    val downEvent = MotionEvent.obtain(
                        uptime, uptime,
                        MotionEvent.ACTION_DOWN,
                        touchX, touchY, 0
                    )
                    engineNativeView.dispatchTouchEvent(downEvent)
                    downEvent.recycle()

                    val upEvent = MotionEvent.obtain(
                        uptime, uptime,
                        MotionEvent.ACTION_UP,
                        touchX, touchY, 0
                    )
                    engineNativeView.dispatchTouchEvent(upEvent)
                    upEvent.recycle()
                }
                return true
            }
        }

        if (event.action != KeyEvent.ACTION_DOWN) return true

        cursor.visibility = View.VISIBLE
        cursor.removeCallbacks(cursorHideRunnable)
        if (!isScrollModeActive) {
            cursor.postDelayed(cursorHideRunnable, cursorTimeoutMs)
        }

        var dx = 0f
        var dy = 0f

        if (isScrollModeActive) {
            val scrollChunk = 50
            when (event.keyCode) {
                KeyEvent.KEYCODE_DPAD_UP -> { dy = -scrollChunk.toFloat(); animateScrollArrow(event.keyCode) }
                KeyEvent.KEYCODE_DPAD_DOWN -> { dy = scrollChunk.toFloat(); animateScrollArrow(event.keyCode) }
                KeyEvent.KEYCODE_DPAD_LEFT -> { dx = -scrollChunk.toFloat(); animateScrollArrow(event.keyCode) }
                KeyEvent.KEYCODE_DPAD_RIGHT -> { dx = scrollChunk.toFloat(); animateScrollArrow(event.keyCode) }
                else -> return false
            }
            
            // Use fractional coordinates so JS can convert to CSS pixels accurately
            val engineW = engineNativeView.width.toFloat().coerceAtLeast(1f)
            val engineH = engineNativeView.height.toFloat().coerceAtLeast(1f)
            val fracX = (cursorX + binding.cursorGroup.width / 2f) / engineW
            val fracY = (cursorY + binding.cursorGroup.height / 2f - engineNativeView.top) / engineH
            val scrollDx = if (dx != 0f) dx.toInt() else 0
            val scrollDy = if (dy != 0f) dy.toInt() else 0
            
            val js = """
                (function() {
                    var cssX = $fracX * window.innerWidth;
                    var cssY = $fracY * window.innerHeight;
                    var el = document.elementFromPoint(cssX, cssY);
                    while (el && el !== document.body && el !== document.documentElement) {
                        var style = window.getComputedStyle(el);
                        var ov = style.overflowY;
                        var oh = style.overflowX;
                        var scrollableY = el.scrollHeight > el.clientHeight + 1 && (ov === 'auto' || ov === 'scroll' || ov === 'overlay');
                        var scrollableX = el.scrollWidth > el.clientWidth + 1 && (oh === 'auto' || oh === 'scroll' || oh === 'overlay');
                        if (scrollableY || scrollableX) {
                            el.scrollBy($scrollDx, $scrollDy);
                            return 'scrolled-element';
                        }
                        el = el.parentElement;
                    }
                    window.scrollBy($scrollDx, $scrollDy);
                    return 'scrolled-window';
                })();
            """.trimIndent()
            
            binding.cursorTooltip.visibility = View.GONE
            getWebView()?.evaluateJavascript(js, null)
            return true
        }

        when (event.keyCode) {
            KeyEvent.KEYCODE_DPAD_UP -> {
                val newY = cursorY - cursorSpeed
                if (newY < 0f) {
                    dy = newY
                    cursorY = 0f
                } else {
                    cursorY = newY
                }
            }
            KeyEvent.KEYCODE_DPAD_DOWN -> {
                val max = binding.root.height.toFloat() - binding.cursorGroup.height
                val newY = cursorY + cursorSpeed
                if (newY > max) {
                    dy = newY - max
                    cursorY = max
                } else {
                    cursorY = newY
                }
            }
            KeyEvent.KEYCODE_DPAD_LEFT -> {
                val newX = cursorX - cursorSpeed
                if (newX < 0f) {
                    dx = newX
                    cursorX = 0f
                } else {
                    cursorX = newX
                }
            }
            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                val max = binding.root.width.toFloat() - binding.cursorGroup.width
                val newX = cursorX + cursorSpeed
                if (newX > max) {
                    dx = newX - max
                    cursorX = max
                } else {
                    cursorX = newX
                }
            }
            else -> return false
        }

        if (dx != 0f || dy != 0f) {
            getWebView()?.evaluateJavascript("window.scrollBy(${dx}, ${dy});", null)
        }

        cursor.translationX = cursorX
        cursor.translationY = cursorY
        
        binding.cursorTooltip.visibility = View.GONE
        binding.root.removeCallbacks(hoverRunnable)
        binding.root.postDelayed(hoverRunnable, 100)
        
        return true
    }

    private fun updateHoverTooltip() {
        val binding = _binding ?: return
        val webView = getWebView() ?: return
        if (!isCursorMode) return

        val touchX = cursorX + binding.cursorGroup.width / 2f
        val touchY = cursorY + binding.cursorGroup.height / 2f - engineNativeView.top
        dispatchNativeHoverMove(touchX, touchY)

        val engineW = engineNativeView.width.toFloat().coerceAtLeast(1f)
        val engineH = engineNativeView.height.toFloat().coerceAtLeast(1f)
        val fracX = (cursorX + binding.cursorGroup.width / 2f) / engineW
        val fracY = (cursorY + binding.cursorGroup.height / 2f - engineNativeView.top) / engineH

        val js = """
            (function() {
                var cssX = $fracX * window.innerWidth;
                var cssY = $fracY * window.innerHeight;
                var el = document.elementFromPoint(cssX, cssY);
                
                var lastEl = window.tvLastHoveredElement;
                if (el !== lastEl) {
                    if (lastEl) {
                        try {
                            var outEvent = new MouseEvent('mouseout', { bubbles: true, cancelable: true, view: window });
                            lastEl.dispatchEvent(outEvent);
                            var leaveEvent = new MouseEvent('mouseleave', { bubbles: false, cancelable: true, view: window });
                            lastEl.dispatchEvent(leaveEvent);
                        } catch(e) {}
                    }
                    window.tvLastHoveredElement = el;
                    if (el) {
                        try {
                            var overEvent = new MouseEvent('mouseover', { bubbles: true, cancelable: true, view: window });
                            el.dispatchEvent(overEvent);
                            var enterEvent = new MouseEvent('mouseenter', { bubbles: false, cancelable: true, view: window });
                            el.dispatchEvent(enterEvent);
                        } catch(e) {}
                    }
                }
                if (el) {
                    try {
                        var moveEvent = new MouseEvent('mousemove', { bubbles: true, cancelable: true, view: window, clientX: cssX, clientY: cssY });
                        el.dispatchEvent(moveEvent);
                    } catch(e) {}
                }
                
                var curr = el;
                var tooltip = "";
                for (var i = 0; i < 5 && curr; i++) {
                    if (curr.tagName === 'BODY' || curr.tagName === 'HTML') break;
                    
                    var title = curr.getAttribute('title');
                    if (title && title.trim()) {
                        tooltip = title.trim();
                        break;
                    }
                    var aria = curr.getAttribute('aria-label');
                    if (aria && aria.trim()) {
                        tooltip = aria.trim();
                        break;
                    }
                    var placeholder = curr.getAttribute('placeholder');
                    if (placeholder && placeholder.trim()) {
                        tooltip = placeholder.trim();
                        break;
                    }
                    var alt = curr.getAttribute('alt');
                    if (alt && alt.trim()) {
                        tooltip = alt.trim();
                        break;
                    }
                    curr = curr.parentElement;
                }
                return tooltip;
            })()
        """.trimIndent()

        webView.evaluateJavascript(js) { value ->
            val context = context ?: return@evaluateJavascript
            val binding = _binding ?: return@evaluateJavascript
            val tooltipText = if (value != null && value != "null" && value != "\"\"") {
                var s = value
                if (s.startsWith("\"") && s.endsWith("\"") && s.length >= 2) {
                    s = s.substring(1, s.length - 1)
                }
                s = s.replace("\\\\", "\\")
                     .replace("\\\"", "\"")
                     .replace("\\n", "\n")
                     .replace("\\t", "\t")
                s.trim()
            } else {
                ""
            }

            val tooltip = binding.cursorTooltip
            if (tooltipText.isNotEmpty()) {
                tooltip.text = tooltipText
                tooltip.visibility = View.VISIBLE
                
                tooltip.measure(
                    View.MeasureSpec.makeMeasureSpec(binding.root.width / 2, View.MeasureSpec.AT_MOST),
                    View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
                )
                val tooltipW = tooltip.measuredWidth
                val tooltipH = tooltip.measuredHeight
                
                val cursorW = binding.cursorGroup.width
                val cursorH = binding.cursorGroup.height
                
                var tx = cursorX + (cursorW - tooltipW) / 2f
                val maxX = binding.root.width.toFloat() - tooltipW
                tx = tx.coerceIn(0f, maxX)
                
                var ty = cursorY + cursorH + 8f
                val maxY = binding.root.height.toFloat() - tooltipH
                if (ty > maxY) {
                    ty = cursorY - tooltipH - 8f
                }
                
                tooltip.translationX = tx
                tooltip.translationY = ty
            } else {
                tooltip.visibility = View.GONE
            }
        }
    }

    private fun dispatchNativeHoverMove(touchX: Float, touchY: Float) {
        val binding = _binding ?: return
        val uptime = android.os.SystemClock.uptimeMillis()
        
        val properties = arrayOf(android.view.MotionEvent.PointerProperties().apply {
            id = 0
            toolType = android.view.MotionEvent.TOOL_TYPE_MOUSE
        })
        val coords = arrayOf(android.view.MotionEvent.PointerCoords().apply {
            x = touchX
            y = touchY
        })
        
        val hoverEvent = android.view.MotionEvent.obtain(
            uptime, uptime,
            android.view.MotionEvent.ACTION_HOVER_MOVE,
            1, properties, coords,
            0, 0, 1f, 1f, 0, 0,
            android.view.InputDevice.SOURCE_MOUSE, 0
        )
        
        engineNativeView.dispatchGenericMotionEvent(hoverEvent)
        hoverEvent.recycle()
    }

    private fun dispatchNativeHoverExit(touchX: Float, touchY: Float) {
        val binding = _binding ?: return
        val uptime = android.os.SystemClock.uptimeMillis()
        
        val properties = arrayOf(android.view.MotionEvent.PointerProperties().apply {
            id = 0
            toolType = android.view.MotionEvent.TOOL_TYPE_MOUSE
        })
        val coords = arrayOf(android.view.MotionEvent.PointerCoords().apply {
            x = touchX
            y = touchY
        })
        
        // 1. Move to exit coordinates
        val moveEvent = android.view.MotionEvent.obtain(
            uptime, uptime,
            android.view.MotionEvent.ACTION_HOVER_MOVE,
            1, properties, coords,
            0, 0, 1f, 1f, 0, 0,
            android.view.InputDevice.SOURCE_MOUSE, 0
        )
        engineNativeView.dispatchGenericMotionEvent(moveEvent)
        moveEvent.recycle()

        // 2. Dispatch hover exit
        val exitEvent = android.view.MotionEvent.obtain(
            uptime, uptime,
            android.view.MotionEvent.ACTION_HOVER_EXIT,
            1, properties, coords,
            0, 0, 1f, 1f, 0, 0,
            android.view.InputDevice.SOURCE_MOUSE, 0
        )
        engineNativeView.dispatchGenericMotionEvent(exitEvent)
        exitEvent.recycle()
    }
}
