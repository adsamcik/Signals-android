package com.adsamcik.tracker.preference

import android.content.Context
import android.content.res.ColorStateList
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatImageView
import androidx.core.graphics.ColorUtils
import com.adsamcik.tracker.common.style.StyleData
import com.adsamcik.tracker.common.style.StyleableView

class ImageSwitchImageView : AppCompatImageView, StyleableView {
	private var lastColor: Int = 0

	override fun onStyleChanged(styleData: StyleData) {
		val foregroundColor = styleData.foregroundColor(false)
		if (foregroundColor == lastColor) return

		val selectedColor = ColorUtils.setAlphaComponent(foregroundColor, SELECTED_ALPHA)
		val notSelectedColor = ColorUtils.setAlphaComponent(foregroundColor, NOT_SELECTED_ALPHA)

		imageTintList = ColorStateList(
				arrayOf(
						intArrayOf(-android.R.attr.state_selected),
						intArrayOf(android.R.attr.state_selected)
				),
				intArrayOf(
						notSelectedColor,
						selectedColor
				))
	}

	constructor(context: Context?) : super(context)
	constructor(context: Context?, attrs: AttributeSet?) : super(context, attrs)
	constructor(context: Context?, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr)

	companion object {
		const val SELECTED_ALPHA = 255
		const val NOT_SELECTED_ALPHA = 128
	}
}
