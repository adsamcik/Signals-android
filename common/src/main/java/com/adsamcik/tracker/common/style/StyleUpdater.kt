package com.adsamcik.tracker.common.style

import android.R.attr.state_enabled
import android.R.attr.state_pressed
import android.annotation.SuppressLint
import android.content.res.ColorStateList
import android.graphics.BlendMode
import android.graphics.BlendModeColorFilter
import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.RippleDrawable
import android.os.Build
import android.view.View
import android.view.ViewGroup
import android.widget.CompoundButton
import android.widget.ImageView
import android.widget.SeekBar
import android.widget.TextView
import androidx.annotation.ColorInt
import androidx.annotation.MainThread
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.alpha
import androidx.core.graphics.drawable.DrawableCompat
import androidx.core.view.children
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.RecyclerView
import com.adsamcik.tracker.common.Assist
import com.adsamcik.tracker.common.extension.firstParent
import com.adsamcik.tracker.common.style.marker.StyleableForegroundDrawable
import com.adsamcik.tracker.common.style.marker.StyleableView
import com.adsamcik.tracker.common.style.utility.ColorFunctions
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout

internal class StyleUpdater {
	internal fun updateSingle(styleView: RecyclerStyleView, styleData: StyleData) {
		val backgroundColor = styleData.backgroundColorFor(styleView)
		val foregroundColor = styleData.foregroundColorFor(styleView)
		val perceivedLuminance = styleData.perceivedLuminanceFor(styleView)

		val updateData = UpdateStyleData(
				backgroundColor,
				foregroundColor,
				perceivedLuminance,
				false
		)

		styleView.view.post {
			updateSingle(styleView, updateData)
		}
	}

	internal fun updateSingle(styleView: StyleView, styleData: StyleData) {
		val backgroundColor = styleData.backgroundColorFor(styleView)
		val foregroundColor = styleData.foregroundColorFor(styleView)
		val perceivedLuminance = styleData.perceivedLuminanceFor(styleView)

		val updateData = UpdateStyleData(
				backgroundColor,
				foregroundColor,
				perceivedLuminance,
				false
		)

		styleView.view.post {
			updateSingle(
					updateData,
					styleView.view,
					styleView.layer,
					styleView.maxDepth
			)
		}
	}

	private fun updateUiVisibility(view: View, luminance: Int) {
		assert(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
		@SuppressLint("InlinedApi")
		view.systemUiVisibility = if (luminance > 0) {
			view.systemUiVisibility or
					View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or
					View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
		} else {
			view.systemUiVisibility and
					(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or
							View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR).inv()
		}
	}

	internal fun updateNavigationBar(styleView: SystemBarStyleView, styleData: StyleData) {
		styleView.view.post {
			when (styleView.style) {
				SystemBarStyle.LayerColor -> {
					val perceivedLuminance = styleData.perceivedLuminanceFor(styleView)
					val backgroundColor = styleData.backgroundColorFor(styleView)
					updateUiVisibility(styleView.view, perceivedLuminance)

					styleView.window.navigationBarColor = ColorFunctions.getBackgroundLayerColor(
							backgroundColor,
							perceivedLuminance,
							styleView.layer
					)
				}
				SystemBarStyle.Transparent -> {
					val perceivedLuminance = styleData.perceivedLuminanceFor(styleView)
					updateUiVisibility(styleView.view, perceivedLuminance)
					styleView.window.navigationBarColor = Color.TRANSPARENT
				}
				SystemBarStyle.Translucent, SystemBarStyle.Default -> Unit
			}
		}
	}

	internal fun updateNotificationBar(styleView: SystemBarStyleView, styleData: StyleData) {
		styleView.view.post {
			when (styleView.style) {
				SystemBarStyle.LayerColor -> {
					val perceivedLuminance = styleData.perceivedLuminanceFor(styleView)
					val backgroundColor = styleData.backgroundColorFor(styleView)
					updateUiVisibility(styleView.view, perceivedLuminance)

					styleView.window.statusBarColor = ColorFunctions.getBackgroundLayerColor(
							backgroundColor,
							perceivedLuminance,
							styleView.layer
					)
				}
				SystemBarStyle.Transparent -> {
					val perceivedLuminance = styleData.perceivedLuminanceFor(styleView)
					updateUiVisibility(styleView.view, perceivedLuminance)
					styleView.window.statusBarColor = Color.TRANSPARENT
				}
				SystemBarStyle.Translucent, SystemBarStyle.Default -> Unit
			}
		}
	}

	@MainThread
	internal fun updateSingle(
			styleData: RecyclerStyleView,
			updateStyleData: UpdateStyleData
	) {
		if (!styleData.onlyChildren) {
			updateStyleForeground(styleData.view, updateStyleData)
			updateSingle(
					updateStyleData,
					styleData.view,
					styleData.layer,
					depthLeft = 0
			)
		}

		val iterator = styleData.view.children.iterator()

		for (item in iterator) {
			updateSingle(
					updateStyleData,
					item,
					styleData.childrenLayer,
					depthLeft = Int.MAX_VALUE
			)
		}
	}

	@MainThread
	@Suppress("LongParameterList")
	internal fun updateSingle(
			updateStyleData: UpdateStyleData,
			view: View,
			layer: Int,
			depthLeft: Int,
			allowRecycler: Boolean = false
	) {
		var newLayer = layer

		val backgroundLayerColor = ColorFunctions.getBackgroundLayerColor(
				updateStyleData.baseBackgroundColor,
				updateStyleData.backgroundLuminance,
				layer
		)
		val wasBackgroundUpdated = updateBackgroundDrawable(
				view,
				backgroundLayerColor,
				updateStyleData.backgroundLuminance
		)
		if (wasBackgroundUpdated) newLayer++

		if (view is ViewGroup) {
			if (depthLeft <= 0 || (!allowRecycler && view is RecyclerView)) return

			val newDepthLeft = depthLeft - 1

			for (i in 0 until view.childCount) {
				updateSingle(
						updateStyleData,
						view.getChildAt(i),
						newLayer,
						newDepthLeft
				)
			}
		} else {
			updateStyleForeground(view, updateStyleData)
		}
	}

	@MainThread
	private fun updateStyleForeground(drawable: Drawable, updateStyleData: UpdateStyleData) {
		drawable.mutate()
		when (drawable) {
			is StyleableForegroundDrawable -> drawable.onForegroundStyleChanged(updateStyleData.baseForegroundColor)
			else -> DrawableCompat.setTint(drawable, updateStyleData.baseForegroundColor)
		}
	}

	@MainThread
	private fun updateStyleForeground(view: CompoundButton, colorStateList: ColorStateList) {
		view.buttonTintList = colorStateList
	}

	@MainThread
	private fun updateStyleForeground(view: TextView, updateStyleData: UpdateStyleData) {
		val alpha = view.textColors.defaultColor.alpha
		val colorStateList = updateStyleData.getBaseTextColorStateList(alpha)

		when (view) {
			is CompoundButton -> updateStyleForeground(view, colorStateList)
		}

		view.setTextColor(colorStateList)
		view.compoundDrawables.forEach {
			if (it != null) updateStyleForeground(it, updateStyleData)
		}

		val hintColorState = colorStateList.withAlpha(alpha - HINT_TEXT_ALPHA_OFFSET)
		if (view is TextInputEditText) {
			val parent = view.firstParent<TextInputLayout>(1)
			require(parent is TextInputLayout) {
				"TextInputEditText ($view) should always have TextInputLayout as it's parent! Found $parent instead"
			}

			parent.defaultHintTextColor = hintColorState
		} else {
			view.setHintTextColor(hintColorState)
		}
	}

	@MainThread
	private fun updateStyleForeground(view: SeekBar, updateStyleData: UpdateStyleData) {
		view.thumbTintList = ColorStateList(
				arrayOf(
						intArrayOf(-state_enabled),
						intArrayOf(state_enabled),
						intArrayOf(state_pressed)
				),
				intArrayOf(
						ColorUtils.setAlphaComponent(
								updateStyleData.baseForegroundColor,
								DISABLED_ALPHA
						),
						updateStyleData.baseForegroundColor,
						ColorUtils.setAlphaComponent(
								updateStyleData.baseForegroundColor,
								SEEKBAR_PRESSED_ALPHA
						)
				)
		)
	}

	@MainThread
	private fun updateStyleForeground(view: ImageView, updateStyleData: UpdateStyleData) {
		view.drawable?.let { drawable ->
			updateStyleForeground(drawable, updateStyleData)
			view.invalidateDrawable(drawable)
		}
	}

	@MainThread
	private fun updateStyleForeground(view: RecyclerView, updateStyleData: UpdateStyleData) {
		for (i in 0 until view.itemDecorationCount) {
			when (val decoration = view.getItemDecorationAt(i)) {
				is DividerItemDecoration -> {
					val drawable = decoration.drawable
					if (drawable != null) updateStyleForeground(drawable, updateStyleData)
				}
			}
		}
	}

	@MainThread
	private fun updateStyleForeground(view: View, updateStyleData: UpdateStyleData) {
		when (view) {
			is StyleableView -> view.onStyleChanged(StyleManager.styleData)
			is ImageView -> updateStyleForeground(view, updateStyleData)
			is TextView -> updateStyleForeground(view, updateStyleData)
			is SeekBar -> updateStyleForeground(view, updateStyleData)
		}
	}

	//todo refactor
	@MainThread
	@Suppress("ReturnCount")
	private fun updateBackgroundDrawable(
			view: View,
			@ColorInt bgColor: Int,
			luminance: Int
	): Boolean {
		val background = view.background
		when {
			view is MaterialButton -> {
				val nextLevel = ColorFunctions.getBackgroundLayerColor(bgColor, luminance, 1)
				view.rippleColor = ColorStateList.valueOf(nextLevel)
				view.setBackgroundColor(bgColor)
			}
			background?.isVisible == true -> {
				if (background.alpha == 0) return false

				background.mutate()
				when (background) {
					is ColorDrawable -> {
						view.setBackgroundColor(bgColor)
					}
					is RippleDrawable -> {
						val nextLevel = ColorFunctions.getBackgroundLayerColor(
								bgColor,
								luminance,
								1
						)
						background.setColor(Assist.getPressedState(nextLevel))
						background.setTint(bgColor)
					}
					else -> {
						background.setTint(bgColor)
						background.colorFilter = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
							BlendModeColorFilter(bgColor, BlendMode.SRC_IN)
						} else {
							@Suppress("DEPRECATION")
							(PorterDuffColorFilter(bgColor, PorterDuff.Mode.SRC_IN))
						}
					}
				}
				return true
			}
		}
		return false
	}

	data class UpdateStyleData(
			@ColorInt val baseBackgroundColor: Int,
			@ColorInt val baseForegroundColor: Int,
			val backgroundLuminance: Int,
			val isRecyclerAllowed: Boolean
	) {
		private val stateArray = arrayOf(
				intArrayOf(state_enabled),
				intArrayOf(-state_enabled)
		)

		fun getBaseTextColorStateList(alpha: Int): ColorStateList {
			return ColorStateList(
					stateArray,
					intArrayOf(
							ColorUtils.setAlphaComponent(baseForegroundColor, alpha),
							ColorUtils.setAlphaComponent(baseForegroundColor, DISABLED_ALPHA)
					)
			)
		}
	}

	companion object {
		const val SEEKBAR_PRESSED_ALPHA = 255
		const val DISABLED_ALPHA = 97
		const val HINT_TEXT_ALPHA_OFFSET = 48
	}
}
