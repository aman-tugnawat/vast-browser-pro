/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.tv.firefox

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import org.mozilla.tv.firefox.databinding.ActivityBrowserBinding
import org.mozilla.tv.firefox.ui.BrowserFragment

/**
 * Main activity for Firefox for TV.
 * Hosts the BrowserFragment which contains the EngineView and URL bar.
 */
class BrowserActivity : AppCompatActivity() {

    private lateinit var binding: ActivityBrowserBinding

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
        if (event.action == android.view.KeyEvent.ACTION_DOWN && event.keyCode == android.view.KeyEvent.KEYCODE_DPAD_UP) {
            val fragment = supportFragmentManager.findFragmentById(R.id.fragment_container) as? BrowserFragment
            val engineView = fragment?.view?.findViewById<View>(R.id.engineView)
            
            if (engineView?.hasFocus() == true) {
                // If focus is inside the engine view, show toolbar and move it to the URL bar
                fragment.showToolbar(focusUrlBar = true)
                return true
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
