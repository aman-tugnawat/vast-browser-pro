/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package com.mangodevelopers.vastbrowser.tv.plugins

import android.content.Context

/**
 * Metadata describing a browser plugin/extension.
 */
data class PluginInfo(
    val id: String,
    val name: String,
    val description: String,
    val version: String,
    val iconRes: Int,
    val defaultEnabled: Boolean = true
)

/**
 * Abstract base for all content-script injection plugins.
 *
 * Each plugin is responsible for:
 * - Generating a JavaScript string that will be injected via evaluateJavascript()
 *   on every page load completion.
 * - Tracking how many items (ads, trackers, etc.) were blocked in the current session.
 */
abstract class BasePlugin(val info: PluginInfo) {

    /**
     * Generate the JavaScript content script to inject into the given [url].
     * The returned JS is executed in the WebView's main frame.
     *
     * @param context Android context for reading assets
     * @param url     The URL of the page that just finished loading
     * @return A complete JavaScript string ready for evaluateJavascript(), or empty string to skip.
     */
    abstract fun generateInjectionScript(context: Context, url: String): String

    /**
     * Number of items blocked/filtered in this session.
     */
    abstract fun getBlockedCount(): Int

    /**
     * Reset the session blocked counter to zero.
     */
    abstract fun resetBlockedCount()

    /**
     * Optional one-time initialization (e.g. loading filter lists from assets).
     * Called when the plugin is first enabled.
     */
    open fun initialize(context: Context) {}
}
