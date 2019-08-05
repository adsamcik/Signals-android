package com.adsamcik.signalcollector.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.adsamcik.signalcollector.R
import com.adsamcik.signalcollector.activity.ActivityRecognitionWorker
import com.adsamcik.signalcollector.activity.service.ActivityWatcherService
import com.adsamcik.signalcollector.common.database.AppDatabase
import com.adsamcik.signalcollector.common.extension.appVersion
import com.adsamcik.signalcollector.common.extension.forEachIf
import com.adsamcik.signalcollector.common.preference.Preferences
import com.adsamcik.signalcollector.tracker.locker.TrackerLocker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

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
					GlobalScope.launch(Dispatchers.Default) {
						val sessionDao = AppDatabase.getDatabase(context).sessionDao()
						val workManager = WorkManager.getInstance(context)
						sessionDao.getAll().forEachIf({ it.id < 0 }) {
							val data = Data.Builder().putLong(com.adsamcik.signalcollector.activity.ActivityRecognitionWorker.ARG_SESSION_ID, it.id).build()
							val workRequest = OneTimeWorkRequestBuilder<com.adsamcik.signalcollector.activity.ActivityRecognitionWorker>()
									.addTag(com.adsamcik.signalcollector.activity.ActivityRecognitionWorker.WORK_TAG)
									.setInputData(data)
									.setConstraints(Constraints.Builder()
											.setRequiresBatteryNotLow(true)
											.build()
									).build()

							workManager.enqueue(workRequest)
						}
					}
				}

				val version = context.appVersion()
				this@edit.setLong(keyLastVersion, version)
			}

			TrackerLocker.initializeFromPersistence(context)
			ActivityWatcherService.poke(context)
		}
	}
}
