package com.adsamcik.signalcollector.tracker.component.consumer.data

import android.content.Context
import com.adsamcik.signalcollector.common.data.MutableCollectionData
import com.adsamcik.signalcollector.tracker.component.DataTrackerComponent
import com.adsamcik.signalcollector.tracker.component.TrackerComponentRequirement
import com.adsamcik.signalcollector.tracker.data.collection.CollectionTempData

internal class LocationTrackerComponent : DataTrackerComponent {
	override val requiredData: Collection<TrackerComponentRequirement> = mutableListOf(TrackerComponentRequirement.LOCATION)

	override suspend fun onDisable(context: Context) {}
	override suspend fun onEnable(context: Context) {}

	override suspend fun onDataUpdated(tempData: CollectionTempData, collectionData: MutableCollectionData) {
		val locationResult = tempData.getLocationResult(this)
		collectionData.setLocation(locationResult.lastLocation)
	}
}