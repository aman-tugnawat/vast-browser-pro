/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.tv.firefox.plugins

import android.content.Context
import android.util.Log
import android.webkit.WebView

/**
 * Central registry and coordinator for all browser plugins.
 *
 * Manages plugin lifecycle (enable/disable), persistence of preferences,
 * and orchestrates JavaScript injection into the WebView on page load.
 */
class PluginManager(private val context: Context) {

    companion object {
        private const val TAG = "PluginManager"
        private const val PREFS_NAME = "vast_browser_plugins"
        private const val PREF_PREFIX_ENABLED = "plugin_enabled_"
    }

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** All available plugins, keyed by their ID. */
    val plugins: Map<String, BasePlugin> = mapOf(
        "ublock_origin" to UBlockOriginPlugin(),
        "privacy_badger" to PrivacyBadgerPlugin()
    )

    init {
        // Initialize default preferences for first run
        val editor = prefs.edit()
        var needsApply = false
        for ((_, plugin) in plugins) {
            val key = PREF_PREFIX_ENABLED + plugin.info.id
            if (!prefs.contains(key)) {
                editor.putBoolean(key, plugin.info.defaultEnabled)
                needsApply = true
            }
        }
        if (needsApply) editor.apply()

        // Initialize enabled plugins
        for ((_, plugin) in plugins) {
            if (isEnabled(plugin.info.id)) {
                plugin.initialize(context)
            }
        }
    }

    /**
     * Check if a plugin is enabled.
     */
    fun isEnabled(pluginId: String): Boolean {
        return prefs.getBoolean(PREF_PREFIX_ENABLED + pluginId, true)
    }

    /**
     * Enable or disable a plugin.
     */
    fun setEnabled(pluginId: String, enabled: Boolean) {
        prefs.edit().putBoolean(PREF_PREFIX_ENABLED + pluginId, enabled).apply()
        if (enabled) {
            plugins[pluginId]?.initialize(context)
        }
        Log.d(TAG, "Plugin $pluginId ${if (enabled) "enabled" else "disabled"}")
    }

    /**
     * Inject all enabled plugins' content scripts into the given WebView.
     * Should be called after every page load completion.
     *
     * @param webView The WebView to inject scripts into
     * @param url     The URL of the page that just loaded
     */
    fun injectPluginScripts(webView: WebView, url: String) {
        for ((id, plugin) in plugins) {
            if (!isEnabled(id)) continue

            try {
                val script = plugin.generateInjectionScript(context, url)
                if (script.isNotEmpty()) {
                    webView.evaluateJavascript(script, null)
                    Log.d(TAG, "Injected ${plugin.info.name} into $url")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to inject ${plugin.info.name}", e)
            }
        }

        // After a short delay, query blocked counts from JS
        webView.postDelayed({
            queryBlockedCounts(webView)
        }, 1500)
    }

    /**
     * Query the blocked count from each plugin's JS context and update native counters.
     */
    private fun queryBlockedCounts(webView: WebView) {
        // uBlock Origin
        if (isEnabled("ublock_origin")) {
            webView.evaluateJavascript("(function(){ return window.__vastUblockCount || 0; })()") { value ->
                try {
                    val count = value?.toIntOrNull() ?: 0
                    if (count > 0) {
                        (plugins["ublock_origin"] as? UBlockOriginPlugin)?.updateBlockedCountFromJs(count)
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to read uBlock count", e)
                }
            }
        }

        // Privacy Badger
        if (isEnabled("privacy_badger")) {
            webView.evaluateJavascript("(function(){ return window.__vastPBBlockedCount || 0; })()") { value ->
                try {
                    val count = value?.toIntOrNull() ?: 0
                    if (count > 0) {
                        (plugins["privacy_badger"] as? PrivacyBadgerPlugin)?.updateBlockedCountFromJs(count)
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to read Privacy Badger count", e)
                }
            }
        }
    }

    /**
     * Get the total blocked count across all enabled plugins.
     */
    fun getTotalBlockedCount(): Int {
        return plugins.values
            .filter { isEnabled(it.info.id) }
            .sumOf { it.getBlockedCount() }
    }

    /**
     * Get the blocked count for a specific plugin.
     */
    fun getBlockedCount(pluginId: String): Int {
        return plugins[pluginId]?.getBlockedCount() ?: 0
    }

    /**
     * Reset blocked count for a specific plugin.
     */
    fun resetBlockedCount(pluginId: String) {
        plugins[pluginId]?.resetBlockedCount()
    }

    /**
     * Get ordered list of all plugin infos.
     */
    fun getPluginInfoList(): List<PluginInfo> {
        return plugins.values.map { it.info }
    }
}
