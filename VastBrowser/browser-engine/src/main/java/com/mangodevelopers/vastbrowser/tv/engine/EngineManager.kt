package com.mangodevelopers.vastbrowser.tv.engine

import android.content.Context
import android.content.SharedPreferences
import mozilla.components.browser.engine.gecko.GeckoEngine
import mozilla.components.browser.engine.system.SystemEngine
import mozilla.components.concept.engine.Engine
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.WebExtension
import org.mozilla.geckoview.WebExtensionController

object EngineManager {
    const val PREF_ENGINE_TYPE = "pref_engine_type"
    const val ENGINE_SYSTEM = "system"
    const val ENGINE_GECKO = "gecko"

    private var geckoRuntime: org.mozilla.geckoview.GeckoRuntime? = null

    fun getOrCreateGeckoRuntime(context: Context): org.mozilla.geckoview.GeckoRuntime {
        if (geckoRuntime == null) {
            val runtime = org.mozilla.geckoview.GeckoRuntime.getDefault(context.applicationContext)
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

    fun createEngine(context: Context, settings: mozilla.components.concept.engine.Settings? = null): Engine {
        val prefs = context.getSharedPreferences("vast_browser_prefs", Context.MODE_PRIVATE)
        val engineType = prefs.getString(PREF_ENGINE_TYPE, ENGINE_SYSTEM)
        
        val engineSettings = settings ?: mozilla.components.concept.engine.DefaultSettings()

        return if (engineType == ENGINE_GECKO) {
            val runtime = getOrCreateGeckoRuntime(context)
            GeckoEngine(context, engineSettings, runtime)
        } else {
            SystemEngine(context, engineSettings)
        }
    }

    fun loadGeckoExtensions(engine: Engine, context: Context) {
        if (engine is GeckoEngine) {
            val prefs = context.getSharedPreferences("vast_browser_prefs", Context.MODE_PRIVATE)
            val runtime = getOrCreateGeckoRuntime(context)

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
                                    { error -> android.util.Log.e("EngineManager", "Failed to install preloaded uBlock", error) }
                                )
                            }
                            if (!hasPrivacyBadger) {
                                runtime.webExtensionController.install("file://" + pbFile.absolutePath).accept(
                                    { /* Success */ },
                                    { error -> android.util.Log.e("EngineManager", "Failed to install preloaded Privacy Badger", error) }
                                )
                            }

                            prefs.edit().putBoolean("gecko_extensions_preloaded", true).apply()
                        } catch (e: Exception) {
                            android.util.Log.e("EngineManager", "Failed to preload extensions", e)
                        }
                    }
                },
                { error ->
                    android.util.Log.e("EngineManager", "Failed to list extensions during check", error)
                }
            )
        }
    }

    private fun copyAssetToFile(context: Context, assetPath: String, outFile: java.io.File) {
        context.assets.open(assetPath).use { inputStream ->
            outFile.outputStream().use { outputStream ->
                inputStream.copyTo(outputStream)
            }
        }
    }
}
