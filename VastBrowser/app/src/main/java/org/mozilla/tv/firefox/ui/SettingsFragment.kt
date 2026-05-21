/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.tv.firefox.ui

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
import android.widget.TextView
import androidx.fragment.app.Fragment
import org.mozilla.tv.firefox.R

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
        val buttonSave = view.findViewById<Button>(R.id.button_save)

        val prefs = requireContext().getSharedPreferences("vast_browser_prefs", Context.MODE_PRIVATE)

        // Load existing settings
        val currentHomePage = prefs.getString("pref_home_page", "about:blank")
        val currentNavMethod = prefs.getString("pref_nav_method", "dpad_cursor")
        val currentTimeout = prefs.getInt("pref_cursor_timeout", 3000)
        val currentScrollHold = prefs.getInt("pref_scroll_hold_duration", 2000)
        val currentTripleUp = prefs.getBoolean("pref_triple_up_toolbar", true)

        editHomePage.setText(currentHomePage)
        checkboxTripleUp.isChecked = currentTripleUp

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

        buttonSave.setOnClickListener {
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

            prefs.edit()
                .putString("pref_home_page", newHomePage)
                .putString("pref_nav_method", newNavMethod)
                .putInt("pref_cursor_timeout", newTimeout)
                .putInt("pref_scroll_hold_duration", newScrollHold)
                .putBoolean("pref_triple_up_toolbar", checkboxTripleUp.isChecked)
                .apply()

            requireActivity().supportFragmentManager.popBackStack()
        }

        return view
    }
}
