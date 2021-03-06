package com.adsamcik.tracker.shared.utils.style.update.implementation

import com.adsamcik.tracker.shared.base.R
import com.adsamcik.tracker.shared.base.Time
import com.adsamcik.tracker.shared.utils.style.update.abstraction.LightStyleUpdate
import com.adsamcik.tracker.shared.utils.style.update.data.DefaultColorData
import com.adsamcik.tracker.shared.utils.style.update.data.DefaultColors

internal class LightDayNightSwitchUpdate : LightStyleUpdate() {
	override val minTimeBetweenUpdatesInMs: Long
		get() = Time.SECOND_IN_MILLISECONDS * 2L

	override val requiredLuminanceChange: Float
		get() = 0.05f

	override val nameRes: Int = R.string.settings_color_update_light_switch_title

	override val defaultColors: DefaultColors
		get() = DefaultColors(
				listOf(
						DefaultColorData(
								defaultColor = -2031888,
								nameRes = R.string.settings_color_day_title
						),
						DefaultColorData(
								defaultColor = -16315596,
								nameRes = R.string.settings_color_night_title
						)
				)
		)

	private var lastColorIndex: Int = -1

	private fun evalLuminance(luminance: Float) = if (luminance < LUMINANCE_THRESHOLD) 1 else 0

	override fun filter(luminance: Float): Boolean = evalLuminance(luminance) != lastColorIndex

	override fun onNewLuminance(newLuminance: Float) {
		val colorIndex = evalLuminance(newLuminance)
		lastColorIndex = colorIndex
		val color = colorList[colorIndex]
		requireConfigData().callback(color)
	}

	companion object {
		private const val LUMINANCE_THRESHOLD = 10f
	}
}
