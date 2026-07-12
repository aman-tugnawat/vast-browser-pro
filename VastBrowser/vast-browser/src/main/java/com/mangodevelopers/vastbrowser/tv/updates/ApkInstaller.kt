/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package com.mangodevelopers.vastbrowser.tv.updates

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.util.Log
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Downloads a release APK and launches the system package installer.
 *
 * APKs are stored under cacheDir/apk_updates and exposed to the installer
 * through the app's FileProvider (see res/xml/file_paths.xml).
 */
object ApkInstaller {

    private const val TAG = "ApkInstaller"
    private const val APK_DIR = "apk_updates"
    private const val MAX_REDIRECTS = 5

    /**
     * Download [url] into the app cache, reporting progress as 0–100 (or -1
     * while the total size is unknown). Returns the downloaded file, or null
     * on failure.
     */
    suspend fun download(
        context: Context,
        url: String,
        fileName: String,
        onProgress: (Int) -> Unit = {}
    ): File? = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, APK_DIR)
        dir.mkdirs()
        // Clear leftovers from previous update attempts
        dir.listFiles()?.forEach { it.delete() }
        val target = File(dir, fileName)

        try {
            // GitHub asset downloads redirect to a storage host; HttpURLConnection
            // won't follow redirects across hosts in all cases, so follow manually.
            var currentUrl = url
            var conn: HttpURLConnection? = null
            for (redirect in 0..MAX_REDIRECTS) {
                conn = (URL(currentUrl).openConnection() as HttpURLConnection).apply {
                    instanceFollowRedirects = false
                    connectTimeout = 15000
                    readTimeout = 30000
                    setRequestProperty("User-Agent", "VastBrowser-UpdateChecker")
                    setRequestProperty("Accept", "application/octet-stream")
                }
                when (conn.responseCode) {
                    in 300..399 -> {
                        val location = conn.getHeaderField("Location")
                        conn.disconnect()
                        if (location == null || redirect == MAX_REDIRECTS) {
                            Log.w(TAG, "Too many redirects downloading $url")
                            return@withContext null
                        }
                        currentUrl = location
                    }
                    200 -> break
                    else -> {
                        Log.w(TAG, "Download failed: HTTP ${conn.responseCode}")
                        conn.disconnect()
                        return@withContext null
                    }
                }
            }
            val connection = conn ?: return@withContext null

            val totalBytes = connection.contentLengthLong
            var readBytes = 0L
            var lastReported = -2

            connection.inputStream.use { input ->
                target.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (isActive) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        readBytes += read
                        val percent = if (totalBytes > 0) {
                            ((readBytes * 100) / totalBytes).toInt()
                        } else -1
                        if (percent != lastReported) {
                            lastReported = percent
                            withContext(Dispatchers.Main) { onProgress(percent) }
                        }
                    }
                }
            }
            connection.disconnect()

            if (!isActive) {
                target.delete()
                return@withContext null
            }
            if (totalBytes > 0 && readBytes != totalBytes) {
                Log.w(TAG, "Incomplete download: $readBytes of $totalBytes bytes")
                target.delete()
                return@withContext null
            }

            Log.d(TAG, "Downloaded ${target.name} ($readBytes bytes)")
            target
        } catch (e: Exception) {
            Log.e(TAG, "Failed to download update APK", e)
            target.delete()
            null
        }
    }

    /**
     * True when the app is allowed to install packages. When false, send the
     * user to [unknownSourcesSettingsIntent] first.
     */
    fun canInstall(context: Context): Boolean =
        context.packageManager.canRequestPackageInstalls()

    /**
     * Settings screen where the user grants this app the "install unknown
     * apps" permission.
     */
    fun unknownSourcesSettingsIntent(context: Context): Intent =
        Intent(
            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            Uri.parse("package:${context.packageName}")
        )

    /**
     * Hand the downloaded APK to the system package installer.
     */
    fun install(context: Context, apk: File) {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apk
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}
