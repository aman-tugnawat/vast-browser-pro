/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package com.mangodevelopers.vastbrowser.tv.engine

import android.content.Context
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.WebExtension
import org.mozilla.geckoview.WebExtensionController

/**
 * Provides GeckoRuntime and manages preloaded WebExtensions.
 * Replaces the old EngineManager for the Gecko variant.
 */
class GeckoEngineProvider(private val context: Context) {

    private var geckoRuntime: GeckoRuntime? = null

    fun getOrCreateRuntime(): GeckoRuntime {
        if (geckoRuntime == null) {
            val runtime = GeckoRuntime.getDefault(context.applicationContext)
            runtime.webExtensionController.promptDelegate = object : WebExtensionController.PromptDelegate {
                override fun onInstallPromptRequest(
                    extension: WebExtension,
                    permissions: Array<out String>,
                    origins: Array<out String>,
                    privateMode: Array<out String>
                ): GeckoResult<WebExtension.PermissionPromptResponse>? {
                    return GeckoResult.fromValue(WebExtension.PermissionPromptResponse(true, true, true))
                }

                override fun onUpdatePrompt(
                    extension: WebExtension,
                    permissions: Array<out String>,
                    origins: Array<out String>,
                    privateMode: Array<out String>
                ): GeckoResult<org.mozilla.geckoview.AllowOrDeny>? {
                    return GeckoResult.fromValue(org.mozilla.geckoview.AllowOrDeny.ALLOW)
                }

                override fun onOptionalPrompt(
                    extension: WebExtension,
                    permissions: Array<out String>,
                    origins: Array<out String>,
                    privateMode: Array<out String>
                ): GeckoResult<org.mozilla.geckoview.AllowOrDeny>? {
                    return GeckoResult.fromValue(org.mozilla.geckoview.AllowOrDeny.ALLOW)
                }
            }
            geckoRuntime = runtime
        }
        return geckoRuntime!!
    }

    /**
     * Preload bundled extensions (uBlock Origin, Privacy Badger) if not already installed.
     */
    fun loadPreloadedExtensions(runtime: GeckoRuntime, context: Context) {
        val prefs = context.getSharedPreferences("vast_browser_prefs", Context.MODE_PRIVATE)

        runtime.webExtensionController.list().accept(
            { list ->
                val hasUblock = list?.any { it.id == "uBlock0@raymondhill.net" } ?: false
                val hasPrivacyBadger = list?.any { it.id == "jid1-MnnxcxisBPnSXQ@jetpack" } ?: false
                val preloaded = prefs.getBoolean("gecko_extensions_preloaded", false)

                if (!preloaded || !hasUblock || !hasPrivacyBadger) {
                    try {
                        val filesDir = context.filesDir
                        val ublockFile = java.io.File(filesDir, "ublock_origin.xpi")
                        val pbFile = java.io.File(filesDir, "privacy_badger.xpi")

                        copyAssetToFile(context, "extensions/ublock_origin.xpi", ublockFile)
                        copyAssetToFile(context, "extensions/privacy_badger.xpi", pbFile)

                        if (!hasUblock) {
                            runtime.webExtensionController.install("file://" + ublockFile.absolutePath).accept(
                                { /* Success */ },
                                { error -> android.util.Log.e("GeckoEngineProvider", "Failed to install preloaded uBlock", error) }
                            )
                        }
                        if (!hasPrivacyBadger) {
                            runtime.webExtensionController.install("file://" + pbFile.absolutePath).accept(
                                { /* Success */ },
                                { error -> android.util.Log.e("GeckoEngineProvider", "Failed to install preloaded Privacy Badger", error) }
                            )
                        }

                        prefs.edit().putBoolean("gecko_extensions_preloaded", true).apply()
                    } catch (e: Exception) {
                        android.util.Log.e("GeckoEngineProvider", "Failed to preload extensions", e)
                    }
                }
            },
            { error ->
                android.util.Log.e("GeckoEngineProvider", "Failed to list extensions during check", error)
            }
        )
    }

    private fun copyAssetToFile(context: Context, assetPath: String, outFile: java.io.File) {
        context.assets.open(assetPath).use { inputStream ->
            outFile.outputStream().use { outputStream ->
                inputStream.copyTo(outputStream)
            }
        }
    }
}
