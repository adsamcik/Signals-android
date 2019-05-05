package com.adsamcik.signalcollector.map

import android.content.Context
import com.adsamcik.signalcollector.preference.Preferences
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class MapLayer(var name: String,
                    val bounds: CoordinateBounds,
                    /**
                     * Contains information for the legend
                     */
                    var values: Array<ValueColor>? = null) {

	constructor(name: String,
	            top: Double = MAX_LATITUDE,
	            right: Double = MAX_LONGITUDE,
	            bottom: Double = MIN_LATITUDE,
	            left: Double = MIN_LONGITUDE,
	            /**
	             * Contains information for the legend
	             */
	            values: Array<ValueColor>? = null) : this(name, CoordinateBounds(top, right, bottom, left), values)

	companion object {
		const val MIN_LATITUDE: Double = -90.0
		const val MAX_LATITUDE: Double = 90.0
		const val MIN_LONGITUDE: Double = -180.0
		const val MAX_LONGITUDE: Double = 180.0
		/**
		 * Checks if MapLayer is in given array
		 */
		fun contains(layerArray: Array<MapLayer>, name: String): Boolean =
				layerArray.any { it.name == name }

		fun mockArray(): Array<MapLayer> = arrayOf(
				MapLayer("Mock", 30.0, 30.0, -30.0, -30.0),
				MapLayer("Wifi", 0.0, 30.0, -20.0, -30.0),
				MapLayer("Cell", 30.0, 30.0, -30.0, -30.0),
				MapLayer("Cell", 60.0, 30.0, -30.0, -30.0),
				MapLayer("Cell", 30.0, 30.0, -30.0, -30.0)
		)
	}

	override fun equals(other: Any?): Boolean {
		if (this === other) return true
		if (javaClass != other?.javaClass) return false

		other as MapLayer

		if (name != other.name) return false
		if (bounds != other.bounds) return false
		if (values != null) {
			if (other.values == null) return false
			if (!values!!.contentEquals(other.values!!)) return false
		} else if (other.values != null) return false

		return true
	}

	override fun hashCode(): Int {
		var result = name.hashCode()
		result = 31 * result + bounds.hashCode()
		result = 31 * result + (values?.contentHashCode() ?: 0)
		return result
	}
}

enum class LayerType {
	Location,
	Cell,
	WiFi;

	companion object {
		fun valueOfCaseInsensitive(value: String): LayerType = when (value.toLowerCase()) {
			Location.name.toLowerCase() -> Location
			Cell.name.toLowerCase() -> Cell
			WiFi.name.toLowerCase(), "wi-fi" -> WiFi
			else -> throw IllegalArgumentException("Value '$value' is not a valid layer type.")
		}

		fun fromPreference(context: Context, key: String, default: LayerType): LayerType {
			val stringName = Preferences.getPref(context).getString(key) ?: return default
			return valueOfCaseInsensitive(stringName)
		}

		fun fromPreference(context: Context, key: String, default: String): LayerType {
			val stringName = Preferences.getPref(context).getString(key, default)
			return valueOfCaseInsensitive(stringName)
		}
	}
}

@JsonClass(generateAdapter = true)
data class ValueColor(val name: String, val color: Int)
