/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package com.mangodevelopers.vastbrowser.tv.updates

import android.content.Context
import android.os.Build
import android.util.Log
import com.mangodevelopers.vastbrowser.tv.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Checks the GitHub Releases API for newer versions of VastBrowser.
 *
 * Releases are published to MangoDevelopers/vast-browser-release with one APK
 * per flavor and ABI: VastBrowser-{abi}.apk (regular) and
 * VastBrowser-Pro-{abi}.apk (pro). The checker picks the asset matching this
 * build's flavor and the device's best supported ABI.
 *
 * Caches the result in SharedPreferences to avoid excessive API calls.
 * Throttles checks to at most once per 24 hours (unless manually triggered).
 */
object UpdateChecker {

    private const val TAG = "UpdateChecker"
    private const val PREFS_NAME = "vast_browser_updates"

    private const val GITHUB_OWNER = "MangoDevelopers"
    private const val GITHUB_REPO = "vast-browser-release"
    private const val RELEASES_API_URL = "https://api.github.com/repos/$GITHUB_OWNER/$GITHUB_REPO/releases/latest"

    private const val THROTTLE_MS = 24 * 60 * 60 * 1000L // 24 hours

    data class UpdateResult(
        val version: String,
        val downloadUrl: String,
        val assetName: String,
        val releaseNotesUrl: String,
        val body: String
    ) {
        /** True when downloadUrl points at an APK asset (vs. the release page fallback). */
        val isDirectApk: Boolean get() = assetName.endsWith(".apk")
    }

    /**
     * Check GitHub Releases API for a newer version.
     *
     * @param context Android context
     * @param forceCheck If true, bypasses the 24-hour throttle
     * @return UpdateResult if a release was found, null on error or if throttled
     */
    suspend fun checkForUpdate(context: Context, forceCheck: Boolean = true): UpdateResult? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        // Throttle check (unless forced)
        if (!forceCheck) {
            val lastChecked = prefs.getLong("update_last_checked", 0L)
            if (System.currentTimeMillis() - lastChecked < THROTTLE_MS) {
                Log.d(TAG, "Skipping update check — throttled")
                return getCachedResult(prefs)
            }
        }

        return withContext(Dispatchers.IO) {
            try {
                val url = URL(RELEASES_API_URL)
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "GET"
                conn.setRequestProperty("Accept", "application/vnd.github.v3+json")
                conn.setRequestProperty("User-Agent", "VastBrowser-UpdateChecker")
                conn.connectTimeout = 10000
                conn.readTimeout = 10000

                val responseCode = conn.responseCode
                if (responseCode != 200) {
                    Log.w(TAG, "GitHub API returned $responseCode")
                    conn.disconnect()
                    return@withContext getCachedResult(prefs)
                }

                val responseBody = conn.inputStream.bufferedReader().readText()
                conn.disconnect()

                val json = JSONObject(responseBody)
                val tagName = json.optString("tag_name", "")
                val htmlUrl = json.optString("html_url", "")
                val body = json.optString("body", "")

                val asset = findBestApkAsset(json.optJSONArray("assets"))

                val result = UpdateResult(
                    version = tagName,
                    downloadUrl = asset?.second ?: htmlUrl,
                    assetName = asset?.first ?: "",
                    releaseNotesUrl = htmlUrl,
                    body = body
                )

                // Cache the result
                prefs.edit()
                    .putString("update_available_version", result.version)
                    .putString("update_available_url", result.downloadUrl)
                    .putString("update_asset_name", result.assetName)
                    .putString("update_release_notes_url", result.releaseNotesUrl)
                    .putLong("update_last_checked", System.currentTimeMillis())
                    .apply()

                Log.d(TAG, "Update check complete. Latest: $tagName, asset: ${result.assetName}")
                result

            } catch (e: Exception) {
                Log.e(TAG, "Failed to check for updates", e)
                // Return cached result on error
                getCachedResult(prefs)
            }
        }
    }

    /**
     * Pick the release asset matching this build's flavor (regular vs. pro)
     * and the device's preferred ABI. Returns (assetName, downloadUrl).
     */
    private fun findBestApkAsset(assets: JSONArray?): Pair<String, String>? {
        if (assets == null) return null

        val isPro = BuildConfig.APPLICATION_ID.contains(".pro")
        val prefix = if (isPro) "VastBrowser-Pro-" else "VastBrowser-"

        fun matchesFlavor(name: String): Boolean =
            name.endsWith(".apk") && name.startsWith(prefix) &&
                (isPro || !name.startsWith("VastBrowser-Pro-"))

        val flavorAssets = mutableMapOf<String, String>()
        for (i in 0 until assets.length()) {
            val asset = assets.getJSONObject(i)
            val name = asset.optString("name", "")
            val url = asset.optString("browser_download_url", "")
            if (matchesFlavor(name) && url.isNotEmpty()) {
                flavorAssets[name] = url
            }
        }
        if (flavorAssets.isEmpty()) return null

        // SUPPORTED_ABIS is ordered by preference (best first)
        for (abi in Build.SUPPORTED_ABIS) {
            val wanted = "$prefix$abi.apk"
            flavorAssets[wanted]?.let { return wanted to it }
        }

        // No ABI match — fall back to any asset of the right flavor
        return flavorAssets.entries.first().toPair()
    }

    /**
     * Simple semantic version comparison. Returns true if [available] is newer
     * than [current]. Handles "v" prefixes and "-suffix" build metadata.
     */
    fun isNewerVersion(available: String, current: String): Boolean {
        try {
            val availParts = available.removePrefix("v").split("-")[0].split(".").map { it.toIntOrNull() ?: 0 }
            val currentParts = current.removePrefix("v").split("-")[0].split(".").map { it.toIntOrNull() ?: 0 }

            for (i in 0 until maxOf(availParts.size, currentParts.size)) {
                val a = availParts.getOrElse(i) { 0 }
                val c = currentParts.getOrElse(i) { 0 }
                if (a > c) return true
                if (a < c) return false
            }
        } catch (_: Exception) {}
        return false
    }

    /**
     * The cached update result, but only when it is newer than the running
     * build; null otherwise.
     */
    fun getAvailableUpdate(context: Context): UpdateResult? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val cached = getCachedResult(prefs) ?: return null
        return cached.takeIf { isNewerVersion(it.version, BuildConfig.VERSION_NAME) }
    }

    /**
     * Whether the update-available prompt was already shown for [version]
     * (once-per-version semantics).
     */
    fun wasUpdatePromptShownFor(context: Context, version: String): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString("update_prompt_shown_for", null) == version

    fun markUpdatePromptShown(context: Context, version: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString("update_prompt_shown_for", version)
            .apply()
    }

    /**
     * Retrieve the cached update result from SharedPreferences.
     */
    private fun getCachedResult(prefs: android.content.SharedPreferences): UpdateResult? {
        val version = prefs.getString("update_available_version", null) ?: return null
        return UpdateResult(
            version = version,
            downloadUrl = prefs.getString("update_available_url", "") ?: "",
            assetName = prefs.getString("update_asset_name", "") ?: "",
            releaseNotesUrl = prefs.getString("update_release_notes_url", "") ?: "",
            body = ""
        )
    }

    /**
     * Clear the cached update result.
     */
    fun clearCache(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove("update_available_version")
            .remove("update_available_url")
            .remove("update_asset_name")
            .remove("update_release_notes_url")
            .apply()
    }
}
