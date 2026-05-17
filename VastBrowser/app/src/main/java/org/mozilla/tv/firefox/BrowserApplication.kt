/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.tv.firefox

import android.app.Application
import android.content.Context
import mozilla.components.support.base.log.Log
import mozilla.components.support.base.log.sink.AndroidLogSink

/**
 * Main Application class for Firefox for TV.
 *
 * Initializes the [Components] lazily so that the application context
 * is available when they are first accessed.
 */
class BrowserApplication : Application() {

    val components by lazy { Components(this) }

    override fun onCreate() {
        super.onCreate()

        // Set up Android Components logging
        Log.addSink(AndroidLogSink(defaultTag = "Firefox4TV"))
    }
}

/**
 * Extension property to access [Components] from any Context.
 */
val Context.components: Components
    get() = (applicationContext as BrowserApplication).components
