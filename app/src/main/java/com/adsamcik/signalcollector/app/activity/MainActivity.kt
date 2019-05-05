package com.adsamcik.signalcollector.app.activity

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Point
import android.os.Build
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.adsamcik.draggable.*
import com.adsamcik.signalcollector.R
import com.adsamcik.signalcollector.activity.service.ActivityService
import com.adsamcik.signalcollector.app.Assist
import com.adsamcik.signalcollector.app.Tips
import com.adsamcik.signalcollector.app.widget.behavior.BottomBarBehavior
import com.adsamcik.signalcollector.common.Constants
import com.adsamcik.signalcollector.common.color.ColorManager
import com.adsamcik.signalcollector.common.color.ColorSupervisor
import com.adsamcik.signalcollector.common.color.ColorView
import com.adsamcik.signalcollector.common.misc.extension.dpAsPx
import com.adsamcik.signalcollector.common.misc.extension.marginBottom
import com.adsamcik.signalcollector.common.misc.extension.setMargin
import com.adsamcik.signalcollector.common.misc.extension.transaction
import com.adsamcik.signalcollector.common.misc.keyboard.NavBarPosition
import com.adsamcik.signalcollector.notification.NotificationChannels
import com.adsamcik.signalcollector.tracker.fragment.FragmentTracker
import com.google.android.gms.location.LocationServices
import kotlinx.android.synthetic.main.activity_ui.*


/**
 * MainActivity containing the core of the App
 * Users should spend most time in here.
 */
class MainActivity : AppCompatActivity() {
	private lateinit var colorManager: ColorManager
	private var themeLocationRequestCode = 4513

	private var draggableOriginalMargin = Int.MIN_VALUE

	private var mapFragment: FragmentMap? = null

	private lateinit var trackerFragment: androidx.fragment.app.Fragment


	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		setContentView(R.layout.activity_ui)

		if (Build.VERSION.SDK_INT >= 26)
			NotificationChannels.prepareChannels(this)

		if (Assist.checkPlayServices(this))
			ActivityService.requestAutoTracking(this, this::class)

		initializeColors()
		initializeButtons()
		initializeColorElements()

		trackerFragment = FragmentTracker()
		supportFragmentManager.transaction {
			replace(R.id.root, trackerFragment)
		}
	}

	override fun onStart() {
		super.onStart()
		root.post {
			Tips.showTips(this, Tips.HOME_TIPS) {}
		}
	}

	override fun onResume() {
		super.onResume()
		initializeButtonsPosition()
		initializeColors()
	}

	private fun initializeButtons() {
		val display = windowManager.defaultDisplay
		val realSize = Point()
		val size = Point()
		display.getRealSize(realSize)
		display.getSize(size)

		button_stats.dragAxis = DragAxis.X
		button_stats.setTarget(root, DragTargetAnchor.RightTop)
		button_stats.setTargetOffsetDp(Offset(56))
		button_stats.targetTranslationZ = 8.dpAsPx.toFloat()
		button_stats.extendTouchAreaBy(56.dpAsPx, 0, 0, 0)
		button_stats.onEnterStateListener = { _, state, _, _ ->
			if (state == DraggableImageButton.State.TARGET)
				hideBottomLayer()
		}
		button_stats.onLeaveStateListener = { _, state ->
			if (state == DraggableImageButton.State.TARGET)
				showBottomLayer()
		}

		val statsPayload = DraggablePayload(this, FragmentStats::class.java, root, root)
		statsPayload.width = MATCH_PARENT
		statsPayload.height = MATCH_PARENT
		statsPayload.initialTranslation = Point(-size.x, 0)
		statsPayload.backgroundColor = Color.WHITE
		statsPayload.targetTranslationZ = 7.dpAsPx.toFloat()
		statsPayload.destroyPayloadAfter = 15 * Constants.SECOND_IN_MILLISECONDS
		button_stats.addPayload(statsPayload)

		button_activity.dragAxis = DragAxis.X
		button_activity.setTarget(root, DragTargetAnchor.LeftTop)
		button_activity.setTargetOffsetDp(Offset(-56))
		button_activity.targetTranslationZ = 8.dpAsPx.toFloat()
		button_activity.extendTouchAreaBy(0, 0, 56.dpAsPx, 0)
		button_activity.onEnterStateListener = { _, state, _, _ ->
			if (state == DraggableImageButton.State.TARGET)
				hideBottomLayer()
		}
		button_activity.onLeaveStateListener = { _, state ->
			if (state == DraggableImageButton.State.TARGET)
				showBottomLayer()
		}

		val activityPayload = DraggablePayload(this, com.adsamcik.signalcollector.game.challenge.fragment.FragmentGame::class.java, root, root)
		activityPayload.width = MATCH_PARENT
		activityPayload.height = MATCH_PARENT
		activityPayload.initialTranslation = Point(size.x, 0)
		activityPayload.backgroundColor = Color.WHITE
		activityPayload.targetTranslationZ = 7.dpAsPx.toFloat()
		activityPayload.destroyPayloadAfter = 15 * Constants.SECOND_IN_MILLISECONDS
		activityPayload.onInitialized = { colorManager.watchView(ColorView(it.view!!, 1, recursive = true, rootIsBackground = true)) }

		button_activity.addPayload(activityPayload)

		button_map.extendTouchAreaBy(32.dpAsPx)
		button_map.onEnterStateListener = { _, state, _, _ ->
			if (state == DraggableImageButton.State.TARGET) {
				hideBottomLayer()
				hideMiddleLayer()
			}
		}
		button_map.onLeaveStateListener = { _, state ->
			if (state == DraggableImageButton.State.TARGET) {
				if (button_activity.state != DraggableImageButton.State.TARGET && button_stats.state != DraggableImageButton.State.TARGET)
					showBottomLayer()

				showMiddleLayer()
			}
		}

		val mapPayload = DraggablePayload(this, FragmentMap::class.java, root, root)
		mapPayload.width = MATCH_PARENT
		mapPayload.height = MATCH_PARENT
		mapPayload.initialTranslation = Point(0, realSize.y)
		mapPayload.backgroundColor = Color.WHITE
		mapPayload.setTranslationZ(16.dpAsPx.toFloat())
		mapPayload.destroyPayloadAfter = 30 * Constants.SECOND_IN_MILLISECONDS

		mapPayload.onInitialized = { mapFragment = it }
		mapPayload.onBeforeDestroyed = { mapFragment = null }

		button_map.addPayload(mapPayload)

		val params = root.layoutParams as androidx.coordinatorlayout.widget.CoordinatorLayout.LayoutParams
		params.behavior = BottomBarBehavior(button_map)
		root.requestLayout()
	}

	private fun hideBottomLayer() {
		trackerFragment.view?.visibility = View.GONE
	}

	private fun showBottomLayer() {
		trackerFragment.view?.visibility = View.VISIBLE
	}

	private fun hideMiddleLayer() {
		button_activity.visibility = View.GONE
		button_stats.visibility = View.GONE

		if (button_stats.state == DraggableImageButton.State.TARGET)
			button_stats.payloads.forEach { it.wrapper?.visibility = View.GONE }

		if (button_activity.state == DraggableImageButton.State.TARGET)
			button_activity.payloads.forEach { it.wrapper?.visibility = View.GONE }
	}

	private fun showMiddleLayer() {
		button_activity.visibility = View.VISIBLE
		button_stats.visibility = View.VISIBLE

		if (button_stats.state == DraggableImageButton.State.TARGET)
			button_stats.payloads.forEach { it.wrapper?.visibility = View.VISIBLE }

		if (button_activity.state == DraggableImageButton.State.TARGET)
			button_activity.payloads.forEach { it.wrapper?.visibility = View.VISIBLE }
	}

	private fun initializeButtonsPosition() {
		if (draggableOriginalMargin == Int.MIN_VALUE)
			draggableOriginalMargin = button_map.marginBottom

		val (position, navDim) = Assist.navbarSize(this)
		if (navDim.x > navDim.y)
			navDim.x = 0
		else
			navDim.y = 0

		button_map.setMargin(0, 0, 0, draggableOriginalMargin + navDim.y)

		when (position) {
			NavBarPosition.RIGHT -> {
				root.setPadding(0, 0, navDim.x, 0)
			}
			NavBarPosition.LEFT -> {
				root.setPadding(navDim.x, 0, 0, 0)
			}
			else -> root.setPadding(0, 0, 0, 0)
		}
	}

	private fun initializeColorElements() {
		colorManager = ColorSupervisor.createColorManager(this)

		colorManager.watchView(ColorView(root, 0, recursive = false, rootIsBackground = true, ignoreRoot = false))

		colorManager.watchView(ColorView(button_stats, 1, recursive = false, rootIsBackground = false, ignoreRoot = false, backgroundIsForeground = true))
		colorManager.watchView(ColorView(button_map, 1, recursive = false, rootIsBackground = false, ignoreRoot = false, backgroundIsForeground = true))
		colorManager.watchView(ColorView(button_activity, 1, recursive = false, rootIsBackground = false, ignoreRoot = false, backgroundIsForeground = true))

		ColorSupervisor.ensureUpdate()
	}

	override fun onSaveInstanceState(outState: Bundle) {
		super.onSaveInstanceState(outState)

		button_map.saveFragments(outState)
		button_stats.saveFragments(outState)
		button_activity.saveFragments(outState)
	}

	override fun onRestoreInstanceState(savedInstanceState: Bundle) {
		super.onRestoreInstanceState(savedInstanceState)

		button_map.restoreFragments(savedInstanceState)
		button_stats.restoreFragments(savedInstanceState)
		button_activity.restoreFragments(savedInstanceState)
	}

	override fun onDestroy() {
		super.onDestroy()
		ColorSupervisor.recycleColorManager(colorManager)
	}

	private fun initializeColors() {
		ColorSupervisor.initializeFromPreferences(this)
		initializeSunriseSunset()
	}

	private fun initializeSunriseSunset() {
		val fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

		if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
			fusedLocationClient.lastLocation.addOnCompleteListener {
				if (it.isSuccessful) {
					val loc = it.result
					if (loc != null)
						ColorSupervisor.setLocation(loc)
				}
			}
		} else if (Build.VERSION.SDK_INT >= 23)
			requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), themeLocationRequestCode)
	}

	override fun dispatchTouchEvent(event: MotionEvent): Boolean {
		return if (!Tips.isActive && root.touchDelegate.onTouchEvent(event))
			true
		else
			super.dispatchTouchEvent(event)
	}

	override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
		if (requestCode == themeLocationRequestCode) {
			if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED })
				initializeSunriseSunset()
		}
	}

	override fun onBackPressed() {
		when {
			button_map.state == DraggableImageButton.State.TARGET -> button_map.moveToState(DraggableImageButton.State.INITIAL, true)
			button_stats.state == DraggableImageButton.State.TARGET -> button_stats.moveToState(DraggableImageButton.State.INITIAL, true)
			button_activity.state == DraggableImageButton.State.TARGET -> button_activity.moveToState(DraggableImageButton.State.INITIAL, true)
			else -> super.onBackPressed()
		}
	}

	companion object {
		const val MAP_OPENED: String = "mapopened"
		const val STATS_OPENED: String = "statsopened"
		const val ACTIVITIES_OPENED: String = "activitiesopened"
	}
}
