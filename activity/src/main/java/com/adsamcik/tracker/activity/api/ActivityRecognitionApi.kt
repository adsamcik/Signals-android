package com.adsamcik.tracker.activity.api

import android.content.Context
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.adsamcik.tracker.activity.ActivityRecognitionWorker
import com.adsamcik.tracker.common.database.AppDatabase
import com.adsamcik.tracker.common.extension.forEachIf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

object ActivityRecognitionApi {
	fun rerunRecognitionForAll(context: Context) {
		GlobalScope.launch(Dispatchers.Default) {
			val sessionDao = AppDatabase.getDatabase(context).sessionDao()
			val workManager = WorkManager.getInstance(context)
			sessionDao.getAll().forEachIf({ it.id < 0 }) {
				val data = Data.Builder().putLong(ActivityRecognitionWorker.ARG_SESSION_ID, it.id).build()
				val workRequest = OneTimeWorkRequestBuilder<ActivityRecognitionWorker>()
						.addTag(ActivityRecognitionWorker.WORK_TAG)
						.setInputData(data)
						.setConstraints(Constraints.Builder()
								.setRequiresBatteryNotLow(true)
								.build()
						).build()

				workManager.enqueue(workRequest)
			}
		}
	}
}
