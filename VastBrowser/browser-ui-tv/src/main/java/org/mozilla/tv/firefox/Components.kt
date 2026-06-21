/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.tv.firefox

import android.content.Context
import mozilla.components.browser.state.engine.EngineMiddleware
import mozilla.components.browser.state.store.BrowserStore
import mozilla.components.concept.engine.DefaultSettings
import mozilla.components.concept.engine.Engine
import mozilla.components.feature.session.SessionUseCases
import org.mozilla.tv.firefox.plugins.PluginManager

/**
 * Provides access to all components needed by the application.
 * Replaces the old WebRenderComponents + ServiceLocator pattern.
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

    val engine: Engine by lazy {
        val eng = org.mozilla.tv.firefox.engine.EngineManager.createEngine(context, engineSettings)
        org.mozilla.tv.firefox.engine.EngineManager.loadGeckoExtensions(eng, context)
        eng
    }

    val store by lazy {
        // SystemEngine doesn't support the PDF viewer check, so we filter out PdfStateMiddleware
        // which crashes when invoking checkForPdfViewer.
        val middlewares = EngineMiddleware.create(engine).filterNot { 
            it.javaClass.simpleName == "PdfStateMiddleware"
        }
        
        BrowserStore(middleware = middlewares)
    }

    val sessionUseCases by lazy {
        SessionUseCases(store)
    }

    val pluginManager by lazy {
        PluginManager(context)
    }
}

