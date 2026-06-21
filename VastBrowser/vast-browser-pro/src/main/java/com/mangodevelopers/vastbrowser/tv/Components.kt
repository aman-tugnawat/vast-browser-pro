/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package com.mangodevelopers.vastbrowser.tv

import android.content.Context
import mozilla.components.browser.engine.gecko.GeckoEngine
import mozilla.components.browser.state.engine.EngineMiddleware
import mozilla.components.browser.state.store.BrowserStore
import mozilla.components.concept.engine.DefaultSettings
import mozilla.components.concept.engine.Engine
import mozilla.components.feature.session.SessionUseCases
import com.mangodevelopers.vastbrowser.tv.engine.GeckoEngineProvider

/**
 * Provides access to all components needed by the application.
 * This variant uses GeckoEngine (Firefox) directly.
 */
class Components(context: Context) {

    val engineSettings by lazy {
        DefaultSettings(
            javascriptEnabled = true,
            domStorageEnabled = true,
            mediaPlaybackRequiresUserGesture = false,
            allowContentAccess = true,
            allowFileAccess = true,
            // Force desktop layout by default since we are on a TV, removing 'Mobile' from User-Agent
            userAgentString = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
        )
    }

    val geckoProvider by lazy { GeckoEngineProvider(context) }

    val engine: Engine by lazy {
        val runtime = geckoProvider.getOrCreateRuntime()
        val eng = GeckoEngine(context, engineSettings, runtime)
        geckoProvider.loadPreloadedExtensions(runtime, context)
        eng
    }

    val store by lazy {
        BrowserStore(middleware = EngineMiddleware.create(engine))
    }

    val sessionUseCases by lazy {
        SessionUseCases(store)
    }
}
