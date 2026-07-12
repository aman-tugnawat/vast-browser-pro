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
import org.mozilla.geckoview.WebExtension
import org.mozilla.geckoview.WebExtensionController
import com.mangodevelopers.vastbrowser.tv.engine.GeckoEngineProvider
import org.json.JSONObject
import java.net.URL
import java.net.HttpURLConnection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.widget.ProgressBar
import android.widget.LinearLayout

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

        // Gecko Extensions UI
        val buttonSearchGecko = view.findViewById<Button>(R.id.button_search_gecko_extensions)

        // About & Updates UI
        val textCurrentVersion = view.findViewById<TextView>(R.id.text_current_version)
        val textEngineVersion = view.findViewById<TextView>(R.id.text_engine_version)
        val textUpdateStatus = view.findViewById<TextView>(R.id.text_update_status)
        val textLastChecked = view.findViewById<TextView>(R.id.text_last_checked)
        val buttonCheckUpdate = view.findViewById<Button>(R.id.button_check_update)
        val buttonDownloadUpdate = view.findViewById<Button>(R.id.button_download_update)
        val checkboxNotifyUpdate = view.findViewById<CheckBox>(R.id.checkbox_notify_update)

        val prefs = requireContext().getSharedPreferences("vast_browser_prefs", Context.MODE_PRIVATE)

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

        // ===== Gecko Extensions Setup =====
        loadGeckoExtensionsList(view)

        buttonSearchGecko?.setOnClickListener {
            showSearchGeckoExtensionsDialog()
        }

        // ===== About & Updates Setup =====

        textCurrentVersion.text = getString(R.string.about_current_version, BuildConfig.VERSION_NAME)
        textEngineVersion.text = getString(R.string.about_engine_version, "GeckoView 150.0.2")

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

    // ===== Gecko WebExtension Management helper methods =====

    private fun loadGeckoExtensionsList(view: View) {
        val container = view.findViewById<LinearLayout>(R.id.linear_gecko_extensions_list) ?: return
        container.removeAllViews()

        val context = requireContext()
        val geckoProvider = requireContext().components.geckoProvider
        val runtime = geckoProvider.getOrCreateRuntime()
        runtime.webExtensionController.list().accept(
            { list ->
                activity?.runOnUiThread {
                    if (!isAdded) return@runOnUiThread
                    if (list == null || list.isEmpty()) {
                        val emptyText = TextView(context).apply {
                            text = "No extensions installed. Click below to add some."
                            setTextColor(androidx.core.content.ContextCompat.getColor(context, R.color.text_secondary))
                            textSize = 14f
                            setPadding(0, 16, 0, 16)
                        }
                        container.addView(emptyText)
                    } else {
                        for (ext in list) {
                            val row = layoutInflater.inflate(R.layout.item_extension, container, false)
                            val nameText = row.findViewById<TextView>(R.id.extension_name)
                            val descText = row.findViewById<TextView>(R.id.extension_desc)
                            val switchToggle = row.findViewById<Switch>(R.id.extension_switch)
                            val detailsBtn = row.findViewById<Button>(R.id.extension_button_details)

                            nameText.text = ext.metaData.name ?: ext.id
                            descText.text = ext.metaData.description ?: "No description provided."
                            switchToggle.isChecked = ext.metaData.enabled

                            switchToggle.setOnCheckedChangeListener { _, isChecked ->
                                val targetMethod = if (isChecked) {
                                    runtime.webExtensionController.enable(ext, WebExtensionController.EnableSource.USER)
                                } else {
                                    runtime.webExtensionController.disable(ext, WebExtensionController.EnableSource.USER)
                                }
                                targetMethod.accept(
                                    { /* Success */ },
                                    { error ->
                                        activity?.runOnUiThread {
                                            AlertDialog.Builder(context)
                                                .setMessage("Failed to update extension state: ${error?.message}")
                                                .setPositiveButton("OK", null)
                                                .show()
                                        }
                                    }
                                )
                            }

                            detailsBtn.setOnClickListener {
                                showGeckoExtensionDetailsDialog(ext)
                            }

                            container.addView(row)
                        }
                    }
                }
            },
            { error ->
                activity?.runOnUiThread {
                    if (!isAdded) return@runOnUiThread
                    val errorText = TextView(context).apply {
                        text = "Error loading extensions: ${error?.message}"
                        setTextColor(androidx.core.content.ContextCompat.getColor(context, R.color.text_secondary))
                        textSize = 14f
                    }
                    container.addView(errorText)
                }
            }
        )
    }

    private fun showGeckoExtensionDetailsDialog(ext: WebExtension) {
        val context = requireContext()
        val geckoProvider = requireContext().components.geckoProvider
        val message = buildString {
            appendLine(ext.metaData.name ?: ext.id)
            appendLine()
            appendLine("ID: ${ext.id}")
            appendLine("Version: ${ext.metaData.version ?: "Unknown"}")
            appendLine()
            appendLine(ext.metaData.description ?: "No description available.")
        }

        AlertDialog.Builder(context)
            .setTitle(ext.metaData.name ?: "Extension Details")
            .setMessage(message)
            .setPositiveButton("Close", null)
            .setNegativeButton("Uninstall") { _, _ ->
                val runtime = geckoProvider.getOrCreateRuntime()
                runtime.webExtensionController.uninstall(ext).accept(
                    {
                        activity?.runOnUiThread {
                            AlertDialog.Builder(context)
                                .setMessage("Extension uninstalled successfully.")
                                .setPositiveButton("OK") { _, _ ->
                                    view?.let { loadGeckoExtensionsList(it) }
                                }
                                .show()
                        }
                    },
                    { error ->
                        activity?.runOnUiThread {
                            AlertDialog.Builder(context)
                                .setMessage("Failed to uninstall: ${error?.message}")
                                .setPositiveButton("OK", null)
                                .show()
                        }
                    }
                )
            }
            .create()
            .apply {
                setOnShowListener {
                    getButton(AlertDialog.BUTTON_POSITIVE).requestFocus()
                }
            }
            .show()
    }

    private fun showSearchGeckoExtensionsDialog() {
        val context = requireContext()
        val dialogView = layoutInflater.inflate(R.layout.dialog_search_extensions, null)
        val editQuery = dialogView.findViewById<EditText>(R.id.edit_search_query)
        val btnSearch = dialogView.findViewById<Button>(R.id.button_search_submit)
        val progress = dialogView.findViewById<ProgressBar>(R.id.search_progress)
        val resultsContainer = dialogView.findViewById<LinearLayout>(R.id.search_results_container)

        val dialog = AlertDialog.Builder(context)
            .setTitle("Search & Install WebExtensions")
            .setView(dialogView)
            .setNegativeButton("Close", null)
            .create()

        btnSearch.setOnClickListener {
            val query = editQuery.text.toString().trim()
            if (query.isEmpty()) return@setOnClickListener

            progress.visibility = View.VISIBLE
            resultsContainer.removeAllViews()

            viewLifecycleOwner.lifecycleScope.launch {
                val results = searchGeckoExtensions(query)
                progress.visibility = View.GONE

                if (results.isEmpty()) {
                    val noResultsText = TextView(context).apply {
                        text = "No extensions found matching '$query'."
                        setTextColor(androidx.core.content.ContextCompat.getColor(context, R.color.text_secondary))
                        setPadding(0, 16, 0, 16)
                    }
                    resultsContainer.addView(noResultsText)
                } else {
                    val installedIds = mutableSetOf<String>()
                    val geckoProvider = requireContext().components.geckoProvider
                    val runtime = geckoProvider.getOrCreateRuntime()
                    
                    runtime.webExtensionController.list().accept(
                        { list ->
                            list?.forEach { installedIds.add(it.id) }
                            activity?.runOnUiThread {
                                populateSearchResults(results, resultsContainer, installedIds, dialog)
                            }
                        },
                        {
                            activity?.runOnUiThread {
                                populateSearchResults(results, resultsContainer, installedIds, dialog)
                            }
                        }
                    )
                }
            }
        }

        dialog.show()
    }

    private fun populateSearchResults(
        results: List<GeckoSearchItem>,
        container: LinearLayout,
        installedIds: Set<String>,
        dialog: AlertDialog
    ) {
        val context = requireContext()
        container.removeAllViews()
        for (item in results) {
            val row = layoutInflater.inflate(R.layout.item_search_result, container, false)
            val nameText = row.findViewById<TextView>(R.id.result_name)
            val summaryText = row.findViewById<TextView>(R.id.result_summary)
            val versionText = row.findViewById<TextView>(R.id.result_version)
            val installBtn = row.findViewById<Button>(R.id.button_install_extension)

            nameText.text = item.name
            summaryText.text = item.summary
            versionText.text = "Version: ${item.version}"

            if (installedIds.contains(item.guid)) {
                installBtn.text = "Installed"
                installBtn.isEnabled = false
            } else {
                installBtn.text = "Install"
                installBtn.setOnClickListener {
                    dialog.dismiss()
                    installGeckoExtension(item.downloadUrl)
                }
            }

            container.addView(row)
        }
    }

    private suspend fun searchGeckoExtensions(query: String): List<GeckoSearchItem> = withContext(Dispatchers.IO) {
        val results = mutableListOf<GeckoSearchItem>()
        try {
            val urlString = "https://addons.mozilla.org/api/v5/addons/search/?q=${java.net.URLEncoder.encode(query, "UTF-8")}&app=android"
            val url = URL(urlString)
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.setRequestProperty("User-Agent", "VastBrowser-Search")
            conn.connectTimeout = 10000
            conn.readTimeout = 10000

            if (conn.responseCode == 200) {
                val jsonText = conn.inputStream.bufferedReader().readText()
                val json = JSONObject(jsonText)
                val resultsArray = json.optJSONArray("results")
                if (resultsArray != null) {
                    for (i in 0 until resultsArray.length()) {
                        val item = resultsArray.getJSONObject(i)
                        val guid = item.optString("guid", "")
                        
                        val nameObj = item.optJSONObject("name")
                        val name = nameObj?.optString("en-US") ?: nameObj?.names()?.optString(0)?.let { nameObj.optString(it) } ?: item.optString("name", "")

                        val summaryObj = item.optJSONObject("summary")
                        val summary = summaryObj?.optString("en-US") ?: summaryObj?.names()?.optString(0)?.let { summaryObj.optString(it) } ?: item.optString("summary", "")

                        val currentVersion = item.optJSONObject("current_version")
                        val version = currentVersion?.optString("version", "1.0") ?: "1.0"
                        
                        var downloadUrl = ""
                        if (currentVersion != null) {
                            val fileObj = currentVersion.optJSONObject("file")
                            if (fileObj != null) {
                                downloadUrl = fileObj.optString("url", "")
                            } else {
                                val filesArray = currentVersion.optJSONArray("files")
                                if (filesArray != null && filesArray.length() > 0) {
                                    downloadUrl = filesArray.getJSONObject(0).optString("url", "")
                                }
                            }
                        }

                        if (guid.isNotEmpty() && downloadUrl.isNotEmpty()) {
                            results.add(GeckoSearchItem(guid, name, summary, version, downloadUrl))
                        }
                    }
                }
            }
            conn.disconnect()
        } catch (e: Exception) {
            android.util.Log.e("SettingsFragment", "Error searching addons", e)
        }
        results
    }

    private fun installGeckoExtension(downloadUrl: String) {
        val context = requireContext()
        val geckoProvider = requireContext().components.geckoProvider
        val runtime = geckoProvider.getOrCreateRuntime()
        runtime.webExtensionController.install(downloadUrl).accept(
            { extension ->
                activity?.runOnUiThread {
                    AlertDialog.Builder(context)
                        .setMessage("Extension ${extension?.metaData?.name ?: extension?.id ?: "Extension"} installed successfully!")
                        .setPositiveButton("OK") { _, _ ->
                            view?.let { loadGeckoExtensionsList(it) }
                        }
                        .show()
                }
            },
            { error ->
                activity?.runOnUiThread {
                    AlertDialog.Builder(context)
                        .setMessage("Failed to install extension: ${error?.message}")
                        .setPositiveButton("OK", null)
                        .show()
                }
            }
        )
    }

    data class GeckoSearchItem(
        val guid: String,
        val name: String,
        val summary: String,
        val version: String,
        val downloadUrl: String
    )
}
