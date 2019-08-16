package com.adsamcik.tracker.tracker.ui.fragment

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.Surface
import android.view.View
import android.view.View.GONE
import android.view.View.VISIBLE
import android.view.ViewGroup
import androidx.appcompat.widget.LinearLayoutCompat
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.view.updateLayoutParams
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.LifecycleObserver
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import com.adsamcik.tracker.common.Assist
import com.adsamcik.tracker.common.Reporter
import com.adsamcik.tracker.common.Time
import com.adsamcik.tracker.common.data.ActivityInfo
import com.adsamcik.tracker.common.data.CellData
import com.adsamcik.tracker.common.data.CellInfo
import com.adsamcik.tracker.common.data.CellType
import com.adsamcik.tracker.common.data.Location
import com.adsamcik.tracker.common.data.MutableCollectionData
import com.adsamcik.tracker.common.data.NetworkOperator
import com.adsamcik.tracker.common.data.TrackerSession
import com.adsamcik.tracker.common.data.WifiData
import com.adsamcik.tracker.common.data.WifiInfo
import com.adsamcik.tracker.common.extension.dp
import com.adsamcik.tracker.common.extension.observe
import com.adsamcik.tracker.common.extension.requireParent
import com.adsamcik.tracker.common.extension.startActivity
import com.adsamcik.tracker.common.fragment.CoreUIFragment
import com.adsamcik.tracker.common.misc.SnackMaker
import com.adsamcik.tracker.common.recycler.decoration.SimpleMarginDecoration
import com.adsamcik.tracker.common.style.RecyclerStyleView
import com.adsamcik.tracker.common.style.StyleManager
import com.adsamcik.tracker.common.style.StyleView
import com.adsamcik.tracker.common.useMock
import com.adsamcik.tracker.tracker.R
import com.adsamcik.tracker.tracker.api.TrackerServiceApi
import com.adsamcik.tracker.tracker.data.collection.CollectionDataEcho
import com.adsamcik.tracker.tracker.locker.TrackerLocker
import com.adsamcik.tracker.tracker.service.TrackerService
import com.adsamcik.tracker.tracker.ui.recycler.TrackerInfoAdapter
import com.google.android.gms.location.DetectedActivity
import kotlinx.android.synthetic.main.fragment_tracker.*
import kotlinx.android.synthetic.main.fragment_tracker.view.*

class FragmentTracker : CoreUIFragment(), LifecycleObserver {
	private lateinit var adapter: TrackerInfoAdapter

	override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
		if (container == null) return null

		val view = inflater.inflate(R.layout.fragment_tracker, container, false)
		view.top_panel_root.updateLayoutParams<LinearLayoutCompat.LayoutParams> {
			height += Assist.getStatusBarHeight(container.context)
		}

		view.tracker_recycler.apply {
			val adapter = TrackerInfoAdapter()
			this@FragmentTracker.adapter = adapter
			this.adapter = adapter

			val itemAnimator = itemAnimator
			if (itemAnimator != null && itemAnimator is DefaultItemAnimator) {
				itemAnimator.supportsChangeAnimations = false
			} else {
				Reporter.report(RuntimeException("Item animator was null or invalid type"))
			}

			post { initializeTrackerRecycler() }
		}

		return view
	}

	private fun RecyclerView.initializeTrackerRecycler() {
		val computedWidth = measuredWidth - paddingStart - paddingEnd
		val oneSideHorizontalMargin = RECYCLER_HORIZONTAL_MARGIN.dp
		val totalHorizontalMargin = oneSideHorizontalMargin * 2
		val maxWidth = MAX_RECYCLER_COLUMN_WIDTH.dp + totalHorizontalMargin
		val minWidth = MIN_RECYCLER_COLUMN_WIDTH.dp + totalHorizontalMargin
		val minColumnCount = kotlin.math.max(computedWidth / maxWidth, 1)
		val columnPlusOneWidth = computedWidth / (minColumnCount + 1)
		val columnCount = if (columnPlusOneWidth < minWidth) minColumnCount else minColumnCount + 1
		layoutManager = StaggeredGridLayoutManager(columnCount, LinearLayoutManager.VERTICAL)
		addItemDecoration(SimpleMarginDecoration(horizontalMargin = oneSideHorizontalMargin))
	}

	override fun onStart() {
		super.onStart()

		button_settings.setOnClickListener {
			val context = it.context
			context.startActivity("com.adsamcik.tracker.preference.activity.SettingsActivity")
		}

		button_tracking.setOnClickListener {
			val activity = requireActivity()
			if (TrackerService.sessionInfo.value?.isInitiatedByUser == false) {
				TrackerLocker.lockTimeLock(activity, Time.MINUTE_IN_MILLISECONDS * LOCK_WHEN_CANCELLED)
				SnackMaker(rootCoordinatorLayout).addMessage(
						activity.resources.getQuantityString(R.plurals.notification_auto_tracking_lock,
								LOCK_WHEN_CANCELLED, LOCK_WHEN_CANCELLED))
			} else {
				toggleCollecting(activity, !TrackerService.isServiceRunning.value)
			}
		}

		button_tracking_lock.setOnClickListener {
			val context = requireContext()
			TrackerLocker.unlockTimeLock(context)
			TrackerLocker.unlockRechargeLock(context)
		}

		TrackerLocker.isLocked.observeGetCurrent(this) {
			button_tracking_lock.visibility = if (it) VISIBLE else GONE
		}

		initializeColorElements()

		TrackerService.isServiceRunning.observeGetCurrent(this) {
			updateTrackerButton(it)
		}

		TrackerService.lastCollectionData.observe(this) {
			if (it != null && it.session.start > 0) {
				updateData(it)
			}
		}

	}

	override fun onStop() {
		StyleManager.recycleController(styleController)
		super.onStop()
	}

	override fun onResume() {
		super.onResume()
		val context = requireContext()

		val orientation = Assist.orientation(context)
		if (orientation == Surface.ROTATION_90 || orientation == Surface.ROTATION_270) {
			tracker_recycler.setPadding(RECYCLER_HORIZONTAL_PADDING.dp, 0, RECYCLER_HORIZONTAL_PADDING.dp, 0)
		}

		if (useMock) mock()
	}

	private val rootCoordinatorLayout: CoordinatorLayout
		get() {
			val fragmentRoot = requireActivity().findViewById<View>(R.id.fragment_tracker_root)
			return fragmentRoot.requireParent()
		}

	private fun startTracking(activity: FragmentActivity) {
		if (!Assist.isGNSSEnabled(activity)) {
			SnackMaker(rootCoordinatorLayout).addMessage(R.string.error_gnss_not_enabled,
					priority = SnackMaker.SnackbarPriority.IMPORTANT,
					actionRes = R.string.enable,
					onActionClick = View.OnClickListener {
						val locationOptionsIntent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
						startActivity(locationOptionsIntent)
					})
		} else if (!Assist.canTrack(activity)) {
			SnackMaker(rootCoordinatorLayout).addMessage(R.string.error_nothing_to_track)
		} else {
			TrackerServiceApi.startService(activity, isUserInitiated = true)
			updateTrackerButton(true)
		}
	}

	private fun stopTracking(activity: FragmentActivity) {
		TrackerServiceApi.stopService(activity)
	}

	/**
	 * Enables or disables collecting service
	 *
	 * @param enable ensures intended action
	 */
	private fun toggleCollecting(activity: FragmentActivity, enable: Boolean) {
		if (TrackerServiceApi.isActive == enable) return

		val missingPermissions = Assist.checkTrackingPermissions(activity)

		if (missingPermissions.isEmpty()) {
			if (!TrackerServiceApi.isActive) {
				startTracking(activity)
			} else {
				stopTracking(activity)
			}
		} else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
			activity.requestPermissions(missingPermissions, 0)
		}
	}

	//todo improve this
	@Suppress("MagicNumber")
	private fun mock() {
		val collectionData = MutableCollectionData(Time.nowMillis)
		val location = Location(collectionData.time, 15.0, 15.0, 123.0, 6f, 3f, 10f, 15f)
		collectionData.location = location
		collectionData.activity = ActivityInfo(DetectedActivity.RUNNING, 75)
		collectionData.wifi = WifiData(location, Time.nowMillis, listOf(WifiInfo(), WifiInfo(), WifiInfo()))
		collectionData.cell = CellData(
				listOf(CellInfo(NetworkOperator("123", "321", "MOCK"), 123456, CellType.LTE, 90, -30, 0)), 8)

		val session = TrackerSession(0, Time.nowMillis - 5 * Time.MINUTE_IN_MILLISECONDS, Time.nowMillis, true, 56,
				5410f, 15f, 5000f, 154)

		updateData(CollectionDataEcho(collectionData, session))
	}

	private fun initializeColorElements() {
		styleController.apply {
			watchView(StyleView(top_panel_root, layer = 1))
			watchRecyclerView(RecyclerStyleView(tracker_recycler, layer = 0, childrenLayer = 1))
		}
	}

	private fun updateTrackerButton(state: Boolean) {
		if (state) {
			button_tracking.setImageResource(R.drawable.ic_pause_circle_filled_black_24dp)
			button_tracking.contentDescription = getString(R.string.description_tracking_stop)
		} else {
			button_tracking.setImageResource(R.drawable.ic_play_circle_filled_black_24dp)
			button_tracking.contentDescription = getString(R.string.description_tracking_start)
		}
	}

	private fun updateData(dataEcho: CollectionDataEcho) {
		adapter.update(dataEcho.collectionData, dataEcho.session)
	}

	companion object {
		private const val LOCK_WHEN_CANCELLED = 60
		private const val MIN_RECYCLER_COLUMN_WIDTH = 125
		private const val MAX_RECYCLER_COLUMN_WIDTH = 220
		private const val RECYCLER_HORIZONTAL_MARGIN = 8

		private const val RECYCLER_HORIZONTAL_PADDING = 72
	}
}

