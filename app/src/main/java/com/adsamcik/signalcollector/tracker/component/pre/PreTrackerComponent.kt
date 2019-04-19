package com.adsamcik.signalcollector.tracker.component.pre

import android.location.Location
import com.adsamcik.signalcollector.activity.ActivityInfo
import com.adsamcik.signalcollector.tracker.component.TrackerComponent
import com.google.android.gms.location.LocationResult

interface PreTrackerComponent : TrackerComponent {
	fun onNewLocation(locationResult: LocationResult, previousLocation: Location?, distance: Float, activity: ActivityInfo): Boolean
}