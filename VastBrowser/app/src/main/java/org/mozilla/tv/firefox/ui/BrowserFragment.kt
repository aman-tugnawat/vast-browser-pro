/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.tv.firefox.ui

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
import org.mozilla.tv.firefox.R
import org.mozilla.tv.firefox.components
import org.mozilla.tv.firefox.databinding.FragmentBrowserBinding

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

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val engineView = binding.engineView as EngineView

        // Create initial tab if none exists
        val initialUrl = arguments?.getString(ARG_URL) ?: HOME_URL
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
        if (binding.toolbar.hasFocus()) {
            binding.engineView.requestFocus()
            val imm = requireContext().getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
            imm.hideSoftInputFromWindow(binding.urlInput.windowToken, 0)
            return true
        }
        return sessionFeature?.onBackPressed() == true
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
                binding.engineView.requestFocus()
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

        binding.engineView.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_DOWN) {
                if (_binding != null && binding.urlInput.hasFocus()) {
                    binding.engineView.requestFocus()
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
            loadUrl("about:blank")
        }
        binding.buttonSettings.setOnClickListener {
            android.widget.Toast.makeText(requireContext(), "Settings coming soon!", android.widget.Toast.LENGTH_SHORT).show()
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

    /**
     * Hide the top toolbar, dismiss keyboard, and focus webpage.
     */
    fun hideToolbar() {
        val binding = _binding ?: return
        if (isToolbarVisible) {
            isToolbarVisible = false
            animateToolbar(false)
        }
        binding.engineView.requestFocus()
        val imm = requireContext().getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
        imm.hideSoftInputFromWindow(binding.urlInput.windowToken, 0)
        restoreWebpageFocus()
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
            if (!shouldShow) {
                restoreWebpageFocus()
            }
        }
    }

    /**
     * Slide the toolbar and progress bar up or down smoothly.
     */
    private fun animateToolbar(show: Boolean) {
        val binding = _binding ?: return
        val toolbarHeight = if (binding.toolbar.height > 0) {
            binding.toolbar.height.toFloat()
        } else {
            48 * resources.displayMetrics.density
        }

        val targetTranslationY = if (show) 0f else -toolbarHeight

        binding.toolbar.animate()
            .translationY(targetTranslationY)
            .setDuration(300)
            .start()

        binding.progressBar.animate()
            .translationY(targetTranslationY)
            .setDuration(300)
            .start()
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
        return findWebView(binding.engineView)
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
                if (!document.getElementById('tv-focus-style')) {
                    var style = document.createElement('style');
                    style.id = 'tv-focus-style';
                    style.innerHTML = `
                        *:focus {
                            outline: 4px solid #FF9800 !important;
                            outline-offset: 2px !important;
                            box-shadow: 0 0 10px #FF9800 !important;
                            transition: outline 0.1s ease-in-out !important;
                        }
                    `;
                    document.head.appendChild(style);
                }

                if (!window.hasTvFocusTracker) {
                    window.hasTvFocusTracker = true;
                    window.lastFocusedElement = null;
                    document.addEventListener('focus', function(e) {
                        if (e.target && e.target !== document.body && e.target !== document.documentElement) {
                            window.lastFocusedElement = e.target;
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
                if (window.lastFocusedElement && document.body.contains(window.lastFocusedElement)) {
                    window.lastFocusedElement.focus();
                } else {
                    var focusables = document.querySelectorAll('a, button, input, select, textarea, [tabindex="0"]');
                    if (focusables.length > 0) {
                        focusables[0].focus();
                    }
                }
            })();
        """.trimIndent()
        webView.evaluateJavascript(js, null)
    }
}
