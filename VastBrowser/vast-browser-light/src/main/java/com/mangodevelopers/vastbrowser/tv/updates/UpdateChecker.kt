/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package com.mangodevelopers.vastbrowser.tv.updates

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Checks the GitHub Releases API for newer versions of VastBrowser.
 *
 * Caches the result in SharedPreferences to avoid excessive API calls.
 * Throttles checks to at most once per 24 hours (unless manually triggered).
 */
object UpdateChecker {

    private const val TAG = "UpdateChecker"
    private const val PREFS_NAME = "vast_browser_updates"

    // GitHub API endpoint — update owner/repo to match your repository
    private const val GITHUB_OWNER = "AmanTugnawat"
    private const val GITHUB_REPO = "vast-browser"
    private const val RELEASES_API_URL = "https://api.github.com/repos/$GITHUB_OWNER/$GITHUB_REPO/releases/latest"

    private const val THROTTLE_MS = 24 * 60 * 60 * 1000L // 24 hours

    data class UpdateResult(
        val version: String,
        val downloadUrl: String,
        val releaseNotesUrl: String,
        val body: String
    )

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

                // Find the APK asset download URL
                var apkDownloadUrl = htmlUrl // fallback to release page
                val assets = json.optJSONArray("assets")
                if (assets != null) {
                    for (i in 0 until assets.length()) {
                        val asset = assets.getJSONObject(i)
                        val assetName = asset.optString("name", "")
                        if (assetName.endsWith(".apk")) {
                            apkDownloadUrl = asset.optString("browser_download_url", htmlUrl)
                            break
                        }
                    }
                }

                val result = UpdateResult(
                    version = tagName,
                    downloadUrl = apkDownloadUrl,
                    releaseNotesUrl = htmlUrl,
                    body = body
                )

                // Cache the result
                prefs.edit()
                    .putString("update_available_version", tagName)
                    .putString("update_available_url", apkDownloadUrl)
                    .putString("update_release_notes_url", htmlUrl)
                    .putLong("update_last_checked", System.currentTimeMillis())
                    .apply()

                Log.d(TAG, "Update check complete. Latest version: $tagName")
                result

            } catch (e: Exception) {
                Log.e(TAG, "Failed to check for updates", e)
                // Return cached result on error
                getCachedResult(prefs)
            }
        }
    }

    /**
     * Retrieve the cached update result from SharedPreferences.
     */
    private fun getCachedResult(prefs: android.content.SharedPreferences): UpdateResult? {
        val version = prefs.getString("update_available_version", null) ?: return null
        val downloadUrl = prefs.getString("update_available_url", "") ?: ""
        val releaseNotesUrl = prefs.getString("update_release_notes_url", "") ?: ""
        return UpdateResult(
            version = version,
            downloadUrl = downloadUrl,
            releaseNotesUrl = releaseNotesUrl,
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
            .remove("update_release_notes_url")
            .apply()
    }
}
