/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package com.mangodevelopers.vastbrowser.tv.updates

import android.widget.ProgressBar
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.mangodevelopers.vastbrowser.tv.R
import kotlinx.coroutines.launch

/**
 * The user-facing update-install flow, shared by the Settings screen and the
 * startup update-available dialog: "install unknown apps" permission check,
 * download with a progress dialog, then hand-off to the package installer.
 *
 * Callers must handle the non-APK (release-page URL) fallback themselves —
 * this flow only accepts results with a direct APK asset.
 */
object UpdateFlow {

    fun startUpdateInstall(
        activity: FragmentActivity,
        result: UpdateChecker.UpdateResult,
        onDownloadFailed: () -> Unit = {}
    ) {
        if (!ApkInstaller.canInstall(activity)) {
            AlertDialog.Builder(activity)
                .setTitle(activity.getString(R.string.about_install_permission_title))
                .setMessage(activity.getString(R.string.about_install_permission_message))
                .setPositiveButton(activity.getString(R.string.about_open_settings)) { _, _ ->
                    activity.startActivity(ApkInstaller.unknownSourcesSettingsIntent(activity))
                }
                .setNegativeButton(activity.getString(R.string.about_cancel), null)
                .create()
                .apply {
                    setOnShowListener {
                        getButton(AlertDialog.BUTTON_POSITIVE).requestFocus()
                    }
                }
                .show()
            return
        }

        val progressBar = ProgressBar(activity, null, android.R.attr.progressBarStyleHorizontal).apply {
            isIndeterminate = false
            max = 100
            val pad = (24 * activity.resources.displayMetrics.density).toInt()
            setPadding(pad, pad, pad, pad / 2)
        }
        val dialog = AlertDialog.Builder(activity)
            .setTitle(activity.getString(R.string.about_downloading, result.version))
            .setView(progressBar)
            .setCancelable(false)
            .create()
        dialog.show()

        activity.lifecycleScope.launch {
            val apk = ApkInstaller.download(
                activity.applicationContext,
                result.downloadUrl,
                result.assetName
            ) { percent ->
                if (percent < 0) {
                    progressBar.isIndeterminate = true
                } else {
                    progressBar.progress = percent
                }
            }
            if (activity.isFinishing || activity.isDestroyed) return@launch

            dialog.dismiss()
            if (apk != null) {
                ApkInstaller.install(activity, apk)
            } else {
                onDownloadFailed()
            }
        }
    }
}
