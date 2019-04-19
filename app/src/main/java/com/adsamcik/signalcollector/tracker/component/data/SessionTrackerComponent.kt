package com.adsamcik.signalcollector.tracker.component.data

import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.Observer
import com.adsamcik.signalcollector.R
import com.adsamcik.signalcollector.activity.ActivityInfo
import com.adsamcik.signalcollector.activity.GroupedActivity
import com.adsamcik.signalcollector.app.Constants
import com.adsamcik.signalcollector.database.AppDatabase
import com.adsamcik.signalcollector.database.dao.SessionDataDao
import com.adsamcik.signalcollector.misc.extension.getSystemServiceTyped
import com.adsamcik.signalcollector.preference.listener.PreferenceListener
import com.adsamcik.signalcollector.tracker.data.MutableCollectionData
import com.adsamcik.signalcollector.tracker.data.TrackerSession
import com.google.android.gms.location.LocationResult
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlin.math.max

class SessionTrackerComponent : DataTrackerComponent, SensorEventListener {
	lateinit var session: TrackerSession
		private set

	private var minUpdateDelayInSeconds = -1
	private var minDistanceInMeters = -1

	private val minDistanceInMetersObserver = Observer<Int> { minDistanceInMeters = it }
	private val minUpdateDelayInSecondsObserver = Observer<Int> { minUpdateDelayInSeconds = it }

	private var lastStepCount = -1

	private lateinit var sessionDao: SessionDataDao

	override fun onLocationUpdated(locationResult: LocationResult, previousLocation: Location?, distance: Float, activity: ActivityInfo, collectionData: MutableCollectionData) {
		val location = locationResult.lastLocation
		session.apply {
			distanceInM += distance
			collections++
			end = System.currentTimeMillis()

			if (previousLocation != null &&
					(location.time - previousLocation.time < max(Constants.SECOND_IN_MILLISECONDS * 20, minUpdateDelayInSeconds * 2 * Constants.SECOND_IN_MILLISECONDS) ||
							distance <= minDistanceInMeters * 2f)) {
				when (activity.groupedActivity) {
					GroupedActivity.ON_FOOT -> distanceOnFootInM += distance
					GroupedActivity.IN_VEHICLE -> distanceInVehicleInM += distance
					else -> {
					}
				}
			}
		}

		GlobalScope.launch {
			if (session.collections <= 1) sessionDao.delete(session)
			else sessionDao.update(session)
		}
	}

	override fun onDisable(context: Context, owner: LifecycleOwner) {
		PreferenceListener.removeObserver(context, R.string.settings_tracking_min_distance_key, minDistanceInMetersObserver)
		PreferenceListener.removeObserver(context, R.string.settings_tracking_min_time_key, minUpdateDelayInSecondsObserver)

		val sensorManager = context.getSystemServiceTyped<SensorManager>(Context.SENSOR_SERVICE)
		sensorManager.unregisterListener(this)
	}

	override fun onEnable(context: Context, owner: LifecycleOwner) {
		session = TrackerSession(System.currentTimeMillis())
		PreferenceListener.observeIntRes(context, R.string.settings_tracking_min_distance_key, minDistanceInMetersObserver, R.integer.settings_tracking_min_distance_default)
		PreferenceListener.observeIntRes(context, R.string.settings_tracking_min_time_key, minUpdateDelayInSecondsObserver, R.integer.settings_tracking_min_time_default)

		val packageManager = context.packageManager
		if (packageManager.hasSystemFeature(PackageManager.FEATURE_SENSOR_STEP_COUNTER)) {
			val sensorManager = context.getSystemServiceTyped<SensorManager>(Context.SENSOR_SERVICE)
			val stepCounter = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
			sensorManager.registerListener(this, stepCounter, SensorManager.SENSOR_DELAY_NORMAL)
		}

		sessionDao = AppDatabase.getDatabase(context).sessionDao()
	}

	override fun onSensorChanged(event: SensorEvent) {
		val sensor = event.sensor
		if (sensor.type == Sensor.TYPE_STEP_COUNTER) {
			val stepCount = event.values.first().toInt()
			if (lastStepCount >= 0) {
				//in case sensor would overflow and reset to 0 at some point
				if (lastStepCount > stepCount) {
					session.steps += stepCount
				} else {
					session.steps += stepCount - lastStepCount
				}
			}

			lastStepCount = stepCount
		}
	}

	override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}