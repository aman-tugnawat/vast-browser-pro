package org.mozilla.tv.firefox.engine

import android.content.Context
import android.content.SharedPreferences
import mozilla.components.browser.engine.gecko.GeckoEngine
import mozilla.components.browser.engine.system.SystemEngine
import mozilla.components.concept.engine.Engine

object EngineManager {
    const val PREF_ENGINE_TYPE = "pref_engine_type"
    const val ENGINE_SYSTEM = "system"
    const val ENGINE_GECKO = "gecko"

    fun createEngine(context: Context, settings: mozilla.components.concept.engine.Settings? = null): Engine {
        val prefs = context.getSharedPreferences("vast_browser_prefs", Context.MODE_PRIVATE)
        val engineType = prefs.getString(PREF_ENGINE_TYPE, ENGINE_SYSTEM)
        
        val engineSettings = settings ?: mozilla.components.concept.engine.DefaultSettings()

        return if (engineType == ENGINE_GECKO) {
            GeckoEngine(context, engineSettings)
        } else {
            SystemEngine(context, engineSettings)
        }
    }

    fun loadGeckoExtensions(engine: Engine, context: Context) {
        if (engine is GeckoEngine) {
            // Extensions loading via GeckoRuntime/WebExtensionController 
            // goes here in the future
        }
    }
}
