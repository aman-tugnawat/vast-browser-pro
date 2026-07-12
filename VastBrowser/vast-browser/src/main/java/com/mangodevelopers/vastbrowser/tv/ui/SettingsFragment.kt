/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package com.mangodevelopers.vastbrowser.tv.ui

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.Spinner
import android.widget.Switch
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import com.mangodevelopers.vastbrowser.tv.BuildConfig
import com.mangodevelopers.vastbrowser.tv.R
import com.mangodevelopers.vastbrowser.tv.components
import com.mangodevelopers.vastbrowser.tv.updates.UpdateChecker
import com.mangodevelopers.vastbrowser.tv.updates.UpdateFlow

class SettingsFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_settings, container, false)

        val editHomePage = view.findViewById<EditText>(R.id.edit_home_page)
        val radioNavGroup = view.findViewById<RadioGroup>(R.id.radio_nav_method)
        val radioSurfing = view.findViewById<RadioButton>(R.id.radio_nav_surfing)
        val radioCursor = view.findViewById<RadioButton>(R.id.radio_nav_cursor)
        val spinnerTimeout = view.findViewById<Spinner>(R.id.spinner_cursor_timeout)
        val editCustomTimeout = view.findViewById<EditText>(R.id.edit_custom_timeout)
        val checkboxTripleUp = view.findViewById<CheckBox>(R.id.checkbox_triple_up)
        val spinnerScrollHold = view.findViewById<Spinner>(R.id.spinner_scroll_hold_duration)
        val spinnerSpeed = view.findViewById<Spinner>(R.id.spinner_cursor_speed)
        val buttonSave = view.findViewById<Button>(R.id.button_save)

        // System Extensions UI
        val switchSystemAds = view.findViewById<Switch>(R.id.switch_system_ads)
        val switchSystemTracker = view.findViewById<Switch>(R.id.switch_system_tracker)
        val systemAdsBlockedCount = view.findViewById<TextView>(R.id.system_ads_blocked_count)
        val systemTrackerBlockedCount = view.findViewById<TextView>(R.id.system_tracker_blocked_count)
        val buttonSystemAdsDetails = view.findViewById<Button>(R.id.button_system_ads_details)
        val buttonSystemTrackerDetails = view.findViewById<Button>(R.id.button_system_tracker_details)

        // About & Updates UI
        val textCurrentVersion = view.findViewById<TextView>(R.id.text_current_version)
        val textEngineVersion = view.findViewById<TextView>(R.id.text_engine_version)
        val textUpdateStatus = view.findViewById<TextView>(R.id.text_update_status)
        val textLastChecked = view.findViewById<TextView>(R.id.text_last_checked)
        val buttonCheckUpdate = view.findViewById<Button>(R.id.button_check_update)
        val buttonDownloadUpdate = view.findViewById<Button>(R.id.button_download_update)
        val checkboxNotifyUpdate = view.findViewById<CheckBox>(R.id.checkbox_notify_update)

        val prefs = requireContext().getSharedPreferences("vast_browser_prefs", Context.MODE_PRIVATE)
        val pluginManager = requireContext().components.pluginManager

        // ===== Load existing settings =====
        val currentHomePage = prefs.getString("pref_home_page", "about:blank")
        val currentNavMethod = prefs.getString("pref_nav_method", "dpad_cursor")
        val currentTimeout = prefs.getInt("pref_cursor_timeout", 3000)
        val currentScrollHold = prefs.getInt("pref_scroll_hold_duration", 2000)
        val currentSpeed = prefs.getString("pref_cursor_speed", "fast") ?: "fast"
        val currentTripleUp = prefs.getBoolean("pref_triple_up_toolbar", true)
        val currentNotifyUpdate = prefs.getBoolean("pref_notify_update_available", true)

        editHomePage.setText(currentHomePage)
        checkboxTripleUp.isChecked = currentTripleUp
        checkboxNotifyUpdate.isChecked = currentNotifyUpdate

        if (currentNavMethod == "dpad_cursor") {
            radioCursor.isChecked = true
        } else {
            radioSurfing.isChecked = true
        }

        // Setup Cursor Timeout Spinner
        val options = arrayOf(
            getString(R.string.timeout_1s),
            getString(R.string.timeout_3s),
            getString(R.string.timeout_5s),
            getString(R.string.timeout_custom)
        )
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, options)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerTimeout.adapter = adapter

        when (currentTimeout) {
            1000 -> spinnerTimeout.setSelection(0)
            3000 -> spinnerTimeout.setSelection(1)
            5000 -> spinnerTimeout.setSelection(2)
            else -> {
                spinnerTimeout.setSelection(3)
                editCustomTimeout.visibility = View.VISIBLE
                editCustomTimeout.setText((currentTimeout / 1000).toString())
            }
        }

        spinnerTimeout.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                editCustomTimeout.visibility = if (position == 3) View.VISIBLE else View.GONE
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        // Setup Scroll Hold Duration Spinner
        val scrollHoldOptions = arrayOf("1 second", "2 seconds", "3 seconds", "5 seconds")
        val scrollHoldAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, scrollHoldOptions)
        scrollHoldAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerScrollHold.adapter = scrollHoldAdapter

        when (currentScrollHold) {
            1000 -> spinnerScrollHold.setSelection(0)
            2000 -> spinnerScrollHold.setSelection(1)
            3000 -> spinnerScrollHold.setSelection(2)
            5000 -> spinnerScrollHold.setSelection(3)
            else -> spinnerScrollHold.setSelection(1)
        }

        // Setup Cursor Speed Spinner
        val speedOptions = arrayOf("Slow", "Medium", "Fast (Default)", "Faster", "Fastest")
        val speedAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, speedOptions)
        speedAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerSpeed.adapter = speedAdapter

        when (currentSpeed) {
            "slow" -> spinnerSpeed.setSelection(0)
            "medium" -> spinnerSpeed.setSelection(1)
            "fast" -> spinnerSpeed.setSelection(2)
            "faster" -> spinnerSpeed.setSelection(3)
            "fastest" -> spinnerSpeed.setSelection(4)
            else -> spinnerSpeed.setSelection(2)
        }

        // ===== Extensions Setup =====

        // Load current extension states (System Engine)
        switchSystemAds.isChecked = pluginManager.isEnabled("ublock_origin")
        switchSystemTracker.isChecked = pluginManager.isEnabled("privacy_badger")

        // Display blocked counts
        updateBlockedCounts(pluginManager, systemAdsBlockedCount, systemTrackerBlockedCount)

        // Toggle handlers
        switchSystemAds.setOnCheckedChangeListener { _, isChecked ->
            pluginManager.setEnabled("ublock_origin", isChecked)
        }

        switchSystemTracker.setOnCheckedChangeListener { _, isChecked ->
            pluginManager.setEnabled("privacy_badger", isChecked)
        }

        // Details button handlers
        buttonSystemAdsDetails.setOnClickListener {
            showPluginDetailsDialog("ublock_origin", pluginManager, systemAdsBlockedCount, systemTrackerBlockedCount)
        }

        buttonSystemTrackerDetails.setOnClickListener {
            showPluginDetailsDialog("privacy_badger", pluginManager, systemAdsBlockedCount, systemTrackerBlockedCount)
        }

        // ===== About & Updates Setup =====

        textCurrentVersion.text = getString(R.string.about_current_version, BuildConfig.VERSION_NAME)
        // System WebView version is dynamically provided by the system, so we can display System WebView
        textEngineVersion.text = getString(R.string.about_engine_version, "System WebView")

        // Load update state from SharedPreferences
        val updatePrefs = requireContext().getSharedPreferences("vast_browser_updates", Context.MODE_PRIVATE)
        val availableVersion = updatePrefs.getString("update_available_version", null)
        val cachedUrl = updatePrefs.getString("update_available_url", null)
        val cachedAsset = updatePrefs.getString("update_asset_name", "") ?: ""
        val lastChecked = updatePrefs.getLong("update_last_checked", 0L)

        if (availableVersion != null && UpdateChecker.isNewerVersion(availableVersion, BuildConfig.VERSION_NAME)) {
            textUpdateStatus.text = getString(R.string.about_update_available, availableVersion)
            buttonDownloadUpdate.visibility = View.VISIBLE
            if (!cachedUrl.isNullOrEmpty()) {
                val cached = UpdateChecker.UpdateResult(
                    version = availableVersion,
                    downloadUrl = cachedUrl,
                    assetName = cachedAsset,
                    releaseNotesUrl = "",
                    body = ""
                )
                buttonDownloadUpdate.setOnClickListener {
                    onDownloadUpdateClicked(cached, textUpdateStatus)
                }
            }
        } else {
            textUpdateStatus.text = getString(R.string.about_up_to_date)
            buttonDownloadUpdate.visibility = View.GONE
        }

        if (lastChecked > 0) {
            textLastChecked.text = getString(R.string.about_last_checked, getTimeAgo(lastChecked))
        } else {
            textLastChecked.text = getString(R.string.about_never_checked)
        }

        buttonCheckUpdate.setOnClickListener {
            textUpdateStatus.text = getString(R.string.about_checking)
            buttonCheckUpdate.isEnabled = false

            viewLifecycleOwner.lifecycleScope.launch {
                val result = UpdateChecker.checkForUpdate(requireContext())
                if (!isAdded) return@launch

                buttonCheckUpdate.isEnabled = true
                if (result != null && UpdateChecker.isNewerVersion(result.version, BuildConfig.VERSION_NAME)) {
                    textUpdateStatus.text = getString(R.string.about_update_available, result.version)
                    buttonDownloadUpdate.visibility = View.VISIBLE
                    buttonDownloadUpdate.setOnClickListener {
                        onDownloadUpdateClicked(result, textUpdateStatus)
                    }
                } else {
                    textUpdateStatus.text = getString(R.string.about_up_to_date)
                    buttonDownloadUpdate.visibility = View.GONE
                }

                val now = System.currentTimeMillis()
                textLastChecked.text = getString(R.string.about_last_checked, getTimeAgo(now))
            }
        }

        // ===== Save Logic =====

        val performSave = {
            val newHomePage = editHomePage.text.toString().trim().takeIf { it.isNotEmpty() } ?: "about:blank"
            val newNavMethod = if (radioCursor.isChecked) "dpad_cursor" else "element_surfing"

            var newTimeout = 3000
            when (spinnerTimeout.selectedItemPosition) {
                0 -> newTimeout = 1000
                1 -> newTimeout = 3000
                2 -> newTimeout = 5000
                3 -> {
                    val customSeconds = editCustomTimeout.text.toString().toIntOrNull() ?: 3
                    val clampedSeconds = customSeconds.coerceIn(1, 60)
                    newTimeout = clampedSeconds * 1000
                }
            }

            val newScrollHold = when (spinnerScrollHold.selectedItemPosition) {
                0 -> 1000
                1 -> 2000
                2 -> 3000
                3 -> 5000
                else -> 2000
            }

            val newSpeed = when (spinnerSpeed.selectedItemPosition) {
                0 -> "slow"
                1 -> "medium"
                2 -> "fast"
                3 -> "faster"
                4 -> "fastest"
                else -> "fast"
            }

            prefs.edit()
                .putString("pref_home_page", newHomePage)
                .putString("pref_nav_method", newNavMethod)
                .putInt("pref_cursor_timeout", newTimeout)
                .putInt("pref_scroll_hold_duration", newScrollHold)
                .putString("pref_cursor_speed", newSpeed)
                .putBoolean("pref_triple_up_toolbar", checkboxTripleUp.isChecked)
                .putBoolean("pref_notify_update_available", checkboxNotifyUpdate.isChecked)
                .commit()
        }

        // ===== Save Button =====

        buttonSave.setOnClickListener {
            performSave()
            requireActivity().supportFragmentManager.popBackStack()
        }

        val backCallback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (hasChanges()) {
                    showExitConfirmationDialog()
                } else {
                    isEnabled = false
                    requireActivity().supportFragmentManager.popBackStack()
                }
            }

            private fun hasChanges(): Boolean {
                val newHomePage = editHomePage.text.toString().trim().takeIf { it.isNotEmpty() } ?: "about:blank"
                val newNavMethod = if (radioCursor.isChecked) "dpad_cursor" else "element_surfing"

                var newTimeout = 3000
                when (spinnerTimeout.selectedItemPosition) {
                    0 -> newTimeout = 1000
                    1 -> newTimeout = 3000
                    2 -> newTimeout = 5000
                    3 -> {
                        val customSeconds = editCustomTimeout.text.toString().toIntOrNull() ?: 3
                        val clampedSeconds = customSeconds.coerceIn(1, 60)
                        newTimeout = clampedSeconds * 1000
                    }
                }

                val newScrollHold = when (spinnerScrollHold.selectedItemPosition) {
                    0 -> 1000
                    1 -> 2000
                    2 -> 3000
                    3 -> 5000
                    else -> 2000
                }

                val newSpeed = when (spinnerSpeed.selectedItemPosition) {
                    0 -> "slow"
                    1 -> "medium"
                    2 -> "fast"
                    3 -> "faster"
                    4 -> "fastest"
                    else -> "fast"
                }

                return newHomePage != currentHomePage ||
                        newNavMethod != currentNavMethod ||
                        newTimeout != currentTimeout ||
                        newScrollHold != currentScrollHold ||
                        newSpeed != currentSpeed ||
                        checkboxTripleUp.isChecked != currentTripleUp ||
                        checkboxNotifyUpdate.isChecked != currentNotifyUpdate
            }

            private fun showExitConfirmationDialog() {
                val context = requireContext()
                AlertDialog.Builder(context)
                    .setMessage("Do you want to save the changes made?")
                    .setPositiveButton("Save & Exit") { _, _ ->
                        saveSettings()
                    }
                    .setNegativeButton("Exit without Saving Changes") { _, _ ->
                        requireActivity().supportFragmentManager.popBackStack()
                    }
                    .setOnCancelListener {
                        // Just dismiss dialog, do nothing
                    }
                    .create()
                    .apply {
                        setOnShowListener {
                            getButton(AlertDialog.BUTTON_POSITIVE).requestFocus()
                        }
                    }
                    .show()
            }

            private fun saveSettings() {
                performSave()
                requireActivity().supportFragmentManager.popBackStack()
            }
        }
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, backCallback)

        return view
    }

    // ===== Extension Helper Methods =====

    private fun updateBlockedCounts(
        pluginManager: com.mangodevelopers.vastbrowser.tv.plugins.PluginManager,
        ublockText: TextView,
        privacyBadgerText: TextView
    ) {
        val ublockCount = pluginManager.getBlockedCount("ublock_origin")
        val pbCount = pluginManager.getBlockedCount("privacy_badger")
        ublockText.text = getString(R.string.extensions_blocked_count, ublockCount)
        privacyBadgerText.text = getString(R.string.extensions_blocked_count, pbCount)
    }

    private fun showPluginDetailsDialog(
        pluginId: String,
        pluginManager: com.mangodevelopers.vastbrowser.tv.plugins.PluginManager,
        ublockText: TextView,
        privacyBadgerText: TextView
    ) {
        val plugin = pluginManager.plugins[pluginId] ?: return
        val info = plugin.info
        val blockedCount = plugin.getBlockedCount()

        val message = buildString {
            appendLine("${info.name}")
            appendLine()
            appendLine("Version: ${info.version}")
            appendLine()
            appendLine(info.description)
            appendLine()
            appendLine("Session blocked: $blockedCount")
            appendLine()
            appendLine("Status: ${if (pluginManager.isEnabled(pluginId)) "Enabled" else "Disabled"}")
        }

        AlertDialog.Builder(requireContext())
            .setTitle(info.name)
            .setMessage(message)
            .setPositiveButton("Close", null)
            .setNeutralButton(getString(R.string.extensions_reset_count)) { _, _ ->
                pluginManager.resetBlockedCount(pluginId)
                updateBlockedCounts(pluginManager, ublockText, privacyBadgerText)
            }
            .create()
            .apply {
                setOnShowListener {
                    getButton(AlertDialog.BUTTON_POSITIVE).requestFocus()
                }
            }
            .show()
    }

    // ===== Update Helper Methods =====

    /**
     * Download-button handler: runs the shared download + install flow, or
     * falls back to opening the release page in the browser when no APK asset
     * was found.
     */
    private fun onDownloadUpdateClicked(result: UpdateChecker.UpdateResult, statusText: TextView) {
        if (!result.isDirectApk) {
            val browserFragment = BrowserFragment.create(result.downloadUrl)
            requireActivity().supportFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, browserFragment)
                .commit()
            return
        }

        UpdateFlow.startUpdateInstall(requireActivity(), result) {
            if (isAdded) statusText.text = getString(R.string.about_download_failed)
        }
    }

    /**
     * Format a timestamp into a human-readable "X ago" string.
     */
    private fun getTimeAgo(timestamp: Long): String {
        val diff = System.currentTimeMillis() - timestamp
        val seconds = diff / 1000
        val minutes = seconds / 60
        val hours = minutes / 60
        val days = hours / 24

        return when {
            days > 0 -> "$days day${if (days > 1) "s" else ""} ago"
            hours > 0 -> "$hours hour${if (hours > 1) "s" else ""} ago"
            minutes > 0 -> "$minutes minute${if (minutes > 1) "s" else ""} ago"
            else -> "Just now"
        }
    }
}
