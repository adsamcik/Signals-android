package com.adsamcik.signalcollector.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.graphics.Color
import androidx.annotation.RequiresApi
import androidx.annotation.StringRes

import com.adsamcik.signalcollector.R

@RequiresApi(26)
/**
 * Singleton that creates notification channels
 */
//todo add option for modules to add channels
object NotificationChannels {

	fun prepareChannels(context: Context) {
		createChannel(context, R.string.channel_track_id, R.string.channel_track_name, R.string.channel_track_description, true, NotificationManager.IMPORTANCE_LOW)
		createChannel(context, R.string.channel_other_id, R.string.channel_other_name, R.string.channel_other_description, true, NotificationManager.IMPORTANCE_LOW)
		createChannel(context, R.string.channel_challenges_id, R.string.channel_challenges_name, R.string.channel_challenges_description, true, NotificationManager.IMPORTANCE_HIGH)
		createChannel(context, R.string.channel_activity_watcher_id, R.string.channel_activity_watcher_name, R.string.channel_activity_watcher_description, false, NotificationManager.IMPORTANCE_LOW)
	}

	private fun createChannel(context: Context, @StringRes idId: Int, @StringRes nameId: Int, @StringRes descriptionId: Int, useVibration: Boolean, importance: Int) {
		val mNotificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
		val mChannel = NotificationChannel(context.getString(idId), context.getString(nameId), importance)
		// Configure the notification channel.
		mChannel.description = context.getString(descriptionId)
		mChannel.enableLights(true)
		mChannel.lightColor = Color.GREEN
		mChannel.enableVibration(useVibration)
		mChannel.vibrationPattern = longArrayOf(100, 200, 300, 400, 500, 400, 300, 200, 400)
		mNotificationManager.createNotificationChannel(mChannel)
	}
}
