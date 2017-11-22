package com.adsamcik.signalcollector.activities

import android.app.Activity
import android.app.job.JobScheduler
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import com.adsamcik.signalcollector.BuildConfig
import com.adsamcik.signalcollector.R
import com.adsamcik.signalcollector.services.ActivityWakerService
import com.adsamcik.signalcollector.jobs.UploadJobService
import com.adsamcik.signalcollector.test.useMock
import com.adsamcik.signalcollector.utility.FirebaseAssist
import com.adsamcik.signalcollector.utility.NotificationTools
import com.adsamcik.signalcollector.utility.Preferences
import com.adsamcik.signalcollector.utility.Shortcuts
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.crash.FirebaseCrash
import com.google.firebase.iid.FirebaseInstanceId
import com.google.firebase.perf.FirebasePerformance

class LaunchActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        Preferences.setTheme(this)
        super.onCreate(savedInstanceState)
        val scheduler = getSystemService(Context.JOB_SCHEDULER_SERVICE) as JobScheduler
        val sp = Preferences.getPref(this)
        if (sp.getInt(Preferences.LAST_VERSION, 0) <= 138) {
            val editor = sp.edit()
            FirebaseAssist.updateValue(this, FirebaseAssist.autoTrackingString, resources.getStringArray(R.array.background_tracking_options)[Preferences.getPref(this).getInt(Preferences.PREF_AUTO_TRACKING, Preferences.DEFAULT_AUTO_TRACKING)])
            FirebaseAssist.updateValue(this, FirebaseAssist.autoUploadString, resources.getStringArray(R.array.automatic_upload_options)[Preferences.getPref(this).getInt(Preferences.PREF_AUTO_UPLOAD, Preferences.DEFAULT_AUTO_UPLOAD)])
            FirebaseAssist.updateValue(this, FirebaseAssist.uploadNotificationString, java.lang.Boolean.toString(Preferences.getPref(this).getBoolean(Preferences.PREF_UPLOAD_NOTIFICATIONS_ENABLED, true)))

            editor.remove(Preferences.PREF_SCHEDULED_UPLOAD)

            try {
                editor.putInt(Preferences.LAST_VERSION, packageManager.getPackageInfo(packageName, 0).versionCode)
            } catch (e: PackageManager.NameNotFoundException) {
                FirebaseCrash.report(e)
            }

            editor.apply()

            scheduler.cancelAll()
        } else {
            val uss = UploadJobService.getUploadScheduled(this)
            if (uss != UploadJobService.UploadScheduleSource.NONE) {
                val jobs = scheduler.allPendingJobs

                val found = jobs.count { it.service.className == "UploadJobService" }
                if (found > 1) {
                    scheduler.cancelAll()
                    UploadJobService.requestUpload(this, uss)
                } else if (found == 0) {
                    UploadJobService.requestUpload(this, uss)
                }
            }
        }

        if (sp.getBoolean(Preferences.PREF_HAS_BEEN_LAUNCHED, false) || useMock)
            startActivity(Intent(this, MainActivity::class.java))
        else
            startActivity(Intent(this, IntroActivity::class.java))

        if (Build.VERSION.SDK_INT >= 25)
            Shortcuts.initializeShortcuts(this)

        if (Build.VERSION.SDK_INT >= 26)
            NotificationTools.prepareChannels(this)

        if (BuildConfig.DEBUG) {
            FirebaseCrash.setCrashCollectionEnabled(false)
            FirebaseAnalytics.getInstance(this).setAnalyticsCollectionEnabled(false)
            FirebasePerformance.getInstance().isPerformanceCollectionEnabled = false
            val token = FirebaseInstanceId.getInstance().token
            Log.d("Signals", token ?: "null token")
        }

        ActivityWakerService.poke(this)

        overridePendingTransition(0, 0)
        finish()
    }
}
