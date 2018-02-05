package com.adsamcik.signals.useractivity

import android.content.Context
import com.adsamcik.signals.base.Constants.DAY_IN_MILLISECONDS
import com.adsamcik.signals.base.Preferences
import com.adsamcik.signals.base.storage.FileStore
import kotlinx.coroutines.experimental.android.UI
import kotlinx.coroutines.experimental.launch
import java.io.File
import java.text.DateFormat

class ActivityRecognitionDebug {
    companion object {
        const val FILE = "activityRecognitionDebug.tsv"

        private var updateCallback: ((Array<String>) -> Unit)? = null

        private var stopCallback: (() -> Unit)? = null

        /**
         * Adds line to the activity debug if tracking is enabled
         * @param context Context
         * @param activity Name of the activity
         * @param action Action that this activity resulted in
         */
        fun addLineIfDebug(context: Context, activity: String, action: String?) {
            val preferences = Preferences.getPref(context)
            if (preferences.getBoolean(Preferences.PREF_DEV_ACTIVITY_TRACKING_ENABLED, false)) {
                if ((System.currentTimeMillis() - preferences.getLong(Preferences.PREF_DEV_ACTIVITY_TRACKING_STARTED, 0)) / DAY_IN_MILLISECONDS > 0) {
                    preferences.edit().putBoolean(Preferences.PREF_DEV_ACTIVITY_TRACKING_ENABLED, false).apply()
                    if (stopCallback != null) {
                        launch(UI) {
                            stopCallback?.invoke()
                        }
                    }
                }
                addLine(context, activity, action)
            }
        }

        /**
         * Adds line to the activity debug
         * @param context Context
         * @param activity Name of the activity
         * @param action Action that this activity resulted in
         */
        private fun addLine(context: Context, activity: String, action: String?) {
            val time = DateFormat.getDateTimeInstance().format(System.currentTimeMillis())
            val line = time + '\t' + activity + '\t' + if (action != null) action + '\n' else '\n'
            FileStore.saveString(File(context.filesDir, FILE), line, true)
            if (updateCallback != null) {
                launch(UI) {
                    updateCallback?.invoke(if (action == null) arrayOf(time, activity) else arrayOf(time, activity, action))
                }
            }
        }
    }
}