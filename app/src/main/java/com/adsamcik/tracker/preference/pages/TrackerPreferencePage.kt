package com.adsamcik.tracker.preference.pages

import android.content.pm.PackageManager
import androidx.preference.CheckBoxPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.adsamcik.tracker.R
import com.adsamcik.tracker.common.misc.SnackMaker
import com.adsamcik.tracker.preference.findPreference
import com.adsamcik.tracker.preference.findPreferenceTyped
import com.adsamcik.tracker.tracker.locker.TrackerLocker
import com.adsamcik.tracker.tracker.service.ActivityWatcherService

class TrackerPreferencePage : PreferencePage {

	private lateinit var snackMaker: SnackMaker

	private fun validateEnablePreference(locationEnabled: Boolean,
	                                     wifiEnabled: Boolean,
	                                     cellEnabled: Boolean) = locationEnabled.or(wifiEnabled).or(cellEnabled)

	override fun onExit(caller: PreferenceFragmentCompat) {}


	private fun initializeEnableTrackingPreferences(caller: PreferenceFragmentCompat) {
		val locationPreference = caller.findPreferenceTyped<CheckBoxPreference>(R.string.settings_location_enabled_key)
		val wifiPreference = caller.findPreferenceTyped<CheckBoxPreference>(R.string.settings_wifi_enabled_key)
		val cellPreference = caller.findPreferenceTyped<CheckBoxPreference>(R.string.settings_cell_enabled_key)

		val context = caller.requireContext()
		val packageManager = context.packageManager
		if (!packageManager.hasSystemFeature(PackageManager.FEATURE_WIFI)) {
			wifiPreference.isEnabled = false
		}

		if (!packageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY)) {
			cellPreference.isEnabled = false
		}

		locationPreference.setOnPreferenceChangeListener { _, newValue ->
			if (!validateEnablePreference(locationEnabled = newValue as Boolean, wifiEnabled = wifiPreference.isChecked,
							cellEnabled = cellPreference.isChecked)) {
				snackMaker.addMessage(R.string.error_nothing_to_track, priority = SnackMaker.SnackbarPriority.IMPORTANT)
				false
			} else {
				true
			}
		}

		wifiPreference.setOnPreferenceChangeListener { _, newValue ->
			if (!validateEnablePreference(locationEnabled = locationPreference.isChecked,
							wifiEnabled = newValue as Boolean, cellEnabled = cellPreference.isChecked)) {
				snackMaker.addMessage(R.string.error_nothing_to_track, priority = SnackMaker.SnackbarPriority.IMPORTANT)
				false
			} else
				true
		}

		cellPreference.setOnPreferenceChangeListener { _, newValue ->
			if (!validateEnablePreference(locationEnabled = locationPreference.isChecked,
							wifiEnabled = wifiPreference.isChecked, cellEnabled = newValue as Boolean)) {
				snackMaker.addMessage(R.string.error_nothing_to_track, priority = SnackMaker.SnackbarPriority.IMPORTANT)
				false
			} else {
				true
			}
		}
	}

	private fun initializeAutoTrackingPreferences(caller: PreferenceFragmentCompat) {
		caller.findPreference(R.string.settings_tracking_activity_key)
				.onPreferenceChangeListener = Preference.OnPreferenceChangeListener { preference, newValue ->
			ActivityWatcherService.onAutoTrackingPreferenceChange(preference.context, newValue as Int)
			return@OnPreferenceChangeListener true
		}

		caller.findPreference(R.string.settings_activity_watcher_key)
				.onPreferenceChangeListener = Preference.OnPreferenceChangeListener { preference, newValue ->
			ActivityWatcherService.onWatcherPreferenceChange(preference.context, newValue as Boolean)
			return@OnPreferenceChangeListener true
		}

		caller.findPreference(R.string.settings_activity_freq_key)
				.onPreferenceChangeListener = Preference.OnPreferenceChangeListener { preference, newValue ->
			ActivityWatcherService.onActivityIntervalPreferenceChange(preference.context, newValue as Int)
			return@OnPreferenceChangeListener true
		}

		caller.findPreference(R.string.settings_disabled_recharge_key)
				.onPreferenceChangeListener = Preference.OnPreferenceChangeListener { preference, newValue ->
			if (newValue as Boolean) {
				TrackerLocker.lockUntilRecharge(preference.context)
			} else {
				TrackerLocker.unlockRechargeLock(preference.context)
			}

			return@OnPreferenceChangeListener true
		}
	}

	override fun onEnter(caller: PreferenceFragmentCompat) {
		snackMaker = SnackMaker(caller.requireView())

		initializeAutoTrackingPreferences(caller)
		initializeEnableTrackingPreferences(caller)
	}

}

