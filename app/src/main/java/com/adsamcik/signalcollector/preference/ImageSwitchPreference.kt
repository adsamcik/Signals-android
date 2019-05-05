package com.adsamcik.signalcollector.preference

import android.content.Context
import android.content.res.ColorStateList
import android.content.res.TypedArray
import android.graphics.Color
import android.util.AttributeSet
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.core.view.setMargins
import androidx.preference.Preference
import androidx.preference.PreferenceViewHolder
import com.adsamcik.signalcollector.R
import com.adsamcik.signalcollector.common.misc.extension.dpAsPx

/**
 * Custom implementation of Preference that allows use of ImageButtons to switch between different states
 */
class ImageSwitchPreference : Preference {
	private var mForegroundColors: ColorStateList? = null

	private var mItems = ArrayList<SwitchItem>()
	private var mTextView: TextView? = null
	private var mImageRoot: ViewGroup? = null

	private var mInitialized = false
	private var mInitialValue: Int = -1
	private val mImageSizePx = 50.dpAsPx
	private val mMarginPx = 10.dpAsPx

	private var mSelected: Int = -1

	@Suppress("unused")
	constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int, defStyleRes: Int) : super(context, attrs, defStyleAttr, defStyleRes) {
		initAttributes(context, attrs!!)
	}

	@Suppress("unused")
	constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr) {
		initAttributes(context, attrs!!)
	}

	@Suppress("unused")
	constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {
		initAttributes(context, attrs!!)
	}

	@Suppress("unused")
	constructor(context: Context) : super(context)

	private fun initAttributes(context: Context, attrs: AttributeSet) {
		val resources = context.resources
		val attributes = context.obtainStyledAttributes(attrs, R.styleable.ImageSwitchPreference)
		val titlesResource = attributes.getResourceId(R.styleable.ImageSwitchPreference_titles, 0)
		val drawablesResource = attributes.getResourceId(R.styleable.ImageSwitchPreference_drawables, 0)

		mForegroundColors = attributes.getColorStateList(R.styleable.ImageSwitchPreference_foregroundStateColors)
				?: ColorStateList(arrayOf(intArrayOf(android.R.attr.state_enabled), intArrayOf(android.R.attr.state_selected)), intArrayOf(Color.GRAY, Color.BLUE))

		attributes.recycle()

		val titles = resources.getStringArray(titlesResource)
		val drawables = resources.obtainTypedArray(drawablesResource)


		if (titles.size != drawables.length())
			throw RuntimeException("Drawables and titles are not equal in size")


		for (i in 0..titles.lastIndex) {
			addItem(titles[i], drawables.getResourceId(i, -1))
		}

		drawables.recycle()
	}

	init {
		layoutResource = R.layout.layout_image_switch
	}

	/**
	 * Add selectable value to the ImageSwitchPreference
	 *
	 * @param title Title of the value
	 * @param image Drawable resource of the value
	 */
	@Synchronized
	fun addItem(title: String, @DrawableRes image: Int) {
		val item = SwitchItem(title, image)
		mItems.add(item)

		if (mInitialized)
			initializeItem(item, mItems.lastIndex)

		if (mItems.size == mSelected + 1)
			select(mSelected)
	}

	private fun onClick(index: Int) {
		select(index)
	}

	private fun select(index: Int) {
		if (!callChangeListener(index) || !persistInt(index))
			return

		when {
			mSelected == index -> return
			index < 0 -> return
			mSelected >= 0 -> mItems[mSelected].imageView!!.isSelected = false
		}

		val newItem = mItems[index]
		newItem.imageView!!.isSelected = true
		mTextView!!.text = newItem.title

		mSelected = index
	}

	override fun onGetDefaultValue(a: TypedArray, index: Int): Any {
		return a.getString(index)!!.toInt()
	}

	override fun onSetInitialValue(defaultValue: Any?) {
		if (defaultValue != null)
			mInitialValue = defaultValue as Int
	}


	private fun initializeItem(item: SwitchItem, index: Int) {
		val view = item.imageView ?: ImageView(context).apply {
			imageTintList = mForegroundColors
			val layoutParams = LinearLayout.LayoutParams(mImageSizePx, mImageSizePx)
			layoutParams.setMargins(mMarginPx)
			this.layoutParams = layoutParams
			mImageRoot!!.addView(this)
			item.imageView = this
		}

		view.run {
			contentDescription = item.title
			setImageResource(item.drawable)
			setOnClickListener { onClick(index) }
		}
	}

	override fun onBindViewHolder(holder: PreferenceViewHolder) {
		super.onBindViewHolder(holder)
		holder.itemView.isClickable = false
		mTextView = holder.findViewById(R.id.selected_item) as TextView
		mImageRoot = holder.findViewById(R.id.option_root) as ViewGroup
		mItems.forEachIndexed { index, switchItem ->
			initializeItem(switchItem, index)
		}

		select(getPersistedInt(mInitialValue))

		mInitialized = true
	}

	data class SwitchItem(val title: String, @DrawableRes val drawable: Int, var imageView: ImageView? = null)
}