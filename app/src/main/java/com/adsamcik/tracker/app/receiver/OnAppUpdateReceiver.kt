package com.adsamcik.tracker.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.adsamcik.tracker.R
import com.adsamcik.tracker.activity.api.ActivityRecognitionApi
import com.adsamcik.tracker.common.extension.appVersion
import com.adsamcik.tracker.common.preference.Preferences
import com.adsamcik.tracker.tracker.locker.TrackerLocker

/**
 * Receiver that is subscribed to update event so some actions can be performed and services are restored
 */
class OnAppUpdateReceiver : BroadcastReceiver() {
	override fun onReceive(context: Context, intent: Intent) {
		val action = intent.action
		if (action != null && action == Intent.ACTION_MY_PACKAGE_REPLACED) {

			Preferences.getPref(context).edit {
				val keyLastVersion = context.getString(R.string.key_last_app_version)
				val lastVersion = getLong(keyLastVersion)

				if (lastVersion < 311) {
					ActivityRecognitionApi.rerunRecognitionForAll(context)
				}

				val version = context.appVersion()
				this@edit.setLong(keyLastVersion, version)
			}

			TrackerLocker.initializeFromPersistence(context)
		}
	}
}

