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
        val labelCursorTimeout = view.findViewById<TextView>(R.id.label_cursor_timeout)
        val buttonSave = view.findViewById<Button>(R.id.button_save)

        val prefs = requireContext().getSharedPreferences("vast_browser_prefs", Context.MODE_PRIVATE)

        // Load existing settings
        val currentHomePage = prefs.getString("pref_home_page", "about:blank")
        val currentNavMethod = prefs.getString("pref_nav_method", "dpad_cursor")
        val currentTimeout = prefs.getInt("pref_cursor_timeout", 3000)

        editHomePage.setText(currentHomePage)
        
        if (currentNavMethod == "dpad_cursor") {
            radioCursor.isChecked = true
            spinnerTimeout.visibility = View.VISIBLE
            labelCursorTimeout.visibility = View.VISIBLE
        } else {
            radioSurfing.isChecked = true
            spinnerTimeout.visibility = View.GONE
            labelCursorTimeout.visibility = View.GONE
            editCustomTimeout.visibility = View.GONE
        }

        radioNavGroup.setOnCheckedChangeListener { _, checkedId ->
            val isCursor = checkedId == R.id.radio_nav_cursor
            spinnerTimeout.visibility = if (isCursor) View.VISIBLE else View.GONE
            labelCursorTimeout.visibility = if (isCursor) View.VISIBLE else View.GONE
            if (!isCursor) {
                editCustomTimeout.visibility = View.GONE
            } else if (spinnerTimeout.selectedItemPosition == 3) {
                editCustomTimeout.visibility = View.VISIBLE
            }
        }

        // Setup Spinner
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
                editCustomTimeout.visibility = if (currentNavMethod == "dpad_cursor") View.VISIBLE else View.GONE
                editCustomTimeout.setText((currentTimeout / 1000).toString())
            }
        }

        spinnerTimeout.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (position == 3) {
                    editCustomTimeout.visibility = View.VISIBLE
                } else {
                    editCustomTimeout.visibility = View.GONE
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        buttonSave.setOnClickListener {
            val newHomePage = editHomePage.text.toString().trim().takeIf { it.isNotEmpty() } ?: "about:blank"
            val newNavMethod = if (radioCursor.isChecked) "dpad_cursor" else "element_surfing"
            
            var newTimeout = 3000
            if (newNavMethod == "dpad_cursor") {
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
            }

            prefs.edit()
                .putString("pref_home_page", newHomePage)
                .putString("pref_nav_method", newNavMethod)
                .putInt("pref_cursor_timeout", newTimeout)
                .apply()

            requireActivity().supportFragmentManager.popBackStack()
        }

        return view
    }
}
