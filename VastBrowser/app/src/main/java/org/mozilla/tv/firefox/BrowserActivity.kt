/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.tv.firefox

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import org.mozilla.tv.firefox.databinding.ActivityBrowserBinding
import org.mozilla.tv.firefox.ui.BrowserFragment
import org.mozilla.tv.firefox.updates.UpdateChecker

/**
 * Main activity for Firefox for TV.
 * Hosts the BrowserFragment which contains the EngineView and URL bar.
 */
class BrowserActivity : AppCompatActivity() {

    private lateinit var binding: ActivityBrowserBinding

    private var dpadUpClicks = 0
    private var lastDpadUpTime = 0L
    private var dpadDownClicks = 0
    private var lastDpadDownTime = 0L
    private val QUICK_PRESS_INTERVAL = 500L // 500ms between consecutive clicks

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBrowserBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Immersive mode for TV
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            or View.SYSTEM_UI_FLAG_FULLSCREEN
        )

        if (savedInstanceState == null) {
            val initialUrl = getUrlFromIntent(intent)
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, BrowserFragment.create(initialUrl))
                .commit()
        }

        // Check for app updates in the background (throttled to once per 24h)
        lifecycleScope.launch {
            UpdateChecker.checkForUpdate(this@BrowserActivity, forceCheck = false)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        val url = getUrlFromIntent(intent) ?: return
        val fragment = supportFragmentManager.findFragmentById(R.id.fragment_container)
        if (fragment is BrowserFragment) {
            fragment.loadUrl(url)
        }
    }

    override fun onBackPressed() {
        val fragment = supportFragmentManager.findFragmentById(R.id.fragment_container)
        if (fragment is BrowserFragment && fragment.onBackPressed()) {
            return
        }
        super.onBackPressed()
    }

    override fun dispatchKeyEvent(event: android.view.KeyEvent): Boolean {
        val fragment = supportFragmentManager.findFragmentById(R.id.fragment_container) as? BrowserFragment

        // Forward ALL key events (ACTION_DOWN + ACTION_UP) for center/enter to handle click + long-press
        if (event.keyCode == android.view.KeyEvent.KEYCODE_DPAD_CENTER || event.keyCode == android.view.KeyEvent.KEYCODE_ENTER) {
            if (fragment?.handleDpadEvent(event) == true) {
                return true
            }
        }

        if (event.action == android.view.KeyEvent.ACTION_DOWN) {

            // Menu/Settings button toggles the toolbar
            if (event.keyCode == android.view.KeyEvent.KEYCODE_MENU ||
                event.keyCode == android.view.KeyEvent.KEYCODE_SETTINGS) {
                if (fragment?.isToolbarVisible() == true) {
                    fragment.hideToolbar()
                } else {
                    fragment?.showToolbar(focusUrlBar = true)
                }
                return true
            }

            // Handle 3 consecutive UP/DOWN presses for toolbar toggle (if enabled)
            val prefs = getSharedPreferences("vast_browser_prefs", MODE_PRIVATE)
            val tripleUpEnabled = prefs.getBoolean("pref_triple_up_toolbar", true)
            
            when (event.keyCode) {
                android.view.KeyEvent.KEYCODE_DPAD_UP -> {
                    if (tripleUpEnabled && fragment?.isToolbarVisible() == false && fragment?.isInScrollMode() != true) {
                        if (event.repeatCount == 0) {
                            val currentTime = System.currentTimeMillis()
                            if (currentTime - lastDpadUpTime < QUICK_PRESS_INTERVAL) {
                                dpadUpClicks++
                            } else {
                                dpadUpClicks = 1
                            }
                            lastDpadUpTime = currentTime

                            if (dpadUpClicks >= 3) {
                                dpadUpClicks = 0 // Reset
                                fragment.showToolbar(focusUrlBar = true)
                                return true
                            }
                        }
                    }
                }
                android.view.KeyEvent.KEYCODE_DPAD_DOWN -> {
                    if (fragment?.isToolbarVisible() == true) {
                        if (event.repeatCount == 0) {
                            val currentTime = System.currentTimeMillis()
                            if (currentTime - lastDpadDownTime < QUICK_PRESS_INTERVAL) {
                                dpadDownClicks++
                            } else {
                                dpadDownClicks = 1
                            }
                            lastDpadDownTime = currentTime

                            if (dpadDownClicks >= 3) {
                                dpadDownClicks = 0 // Reset
                                fragment.hideToolbar()
                                return true
                            }
                        }
                    }
                }
            }

            // Allow BrowserFragment to handle D-Pad cursor direction keys
            if (fragment?.handleDpadEvent(event) == true) {
                return true
            }

            when (event.keyCode) {
                android.view.KeyEvent.KEYCODE_BACK -> {
                    val toolbar = fragment?.view?.findViewById<View>(R.id.toolbar)
                    if (toolbar?.hasFocus() == true) {
                        // Return focus to engine view
                        fragment.view?.findViewById<View>(R.id.engineView)?.requestFocus()
                        // Hide soft keyboard
                        val imm = getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
                        fragment.view?.findViewById<View>(R.id.url_input)?.let { urlInput ->
                            imm.hideSoftInputFromWindow(urlInput.windowToken, 0)
                        }
                        return true
                    }
                }
                android.view.KeyEvent.KEYCODE_MEDIA_PLAY -> {
                    fragment?.executeMediaAction("play")
                    return true
                }
                android.view.KeyEvent.KEYCODE_MEDIA_PAUSE -> {
                    fragment?.executeMediaAction("pause")
                    return true
                }
                android.view.KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                    fragment?.executeMediaAction("toggle")
                    return true
                }
                android.view.KeyEvent.KEYCODE_MEDIA_FAST_FORWARD,
                android.view.KeyEvent.KEYCODE_MEDIA_NEXT -> {
                    fragment?.executeMediaAction("forward")
                    return true
                }
                android.view.KeyEvent.KEYCODE_MEDIA_REWIND,
                android.view.KeyEvent.KEYCODE_MEDIA_PREVIOUS -> {
                    fragment?.executeMediaAction("backward")
                    return true
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }

    override fun dispatchGenericMotionEvent(event: android.view.MotionEvent): Boolean {
        val action = event.actionMasked
        if (action == android.view.MotionEvent.ACTION_HOVER_MOVE ||
            action == android.view.MotionEvent.ACTION_HOVER_ENTER ||
            action == android.view.MotionEvent.ACTION_HOVER_EXIT) {
            val fragment = supportFragmentManager.findFragmentById(R.id.fragment_container) as? BrowserFragment
            fragment?.handleHoverEvent(event)
        }
        return super.dispatchGenericMotionEvent(event)
    }

    private fun getUrlFromIntent(intent: Intent?): String? {
        if (intent?.action == Intent.ACTION_VIEW) {
            return intent.dataString
        }
        return null
    }
}
