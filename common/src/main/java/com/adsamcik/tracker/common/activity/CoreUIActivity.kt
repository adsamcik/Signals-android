package com.adsamcik.tracker.common.activity

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.annotation.CallSuper
import com.adsamcik.tracker.common.language.LocaleContextWrapper
import com.adsamcik.tracker.common.style.StyleController
import com.adsamcik.tracker.common.style.StyleManager

abstract class CoreUIActivity : CoreActivity() {
	protected val styleController: StyleController = StyleManager.createController()

	private val permissionRequestList = mutableListOf<Pair<Int, PermissionRequest>>()
	private var lastPermissionRequestId = 1000

	@CallSuper
	override fun onDestroy() {
		StyleManager.recycleController(styleController)
		super.onDestroy()
	}

	@CallSuper
	override fun onPause() {
		styleController.isSuspended = true
		super.onPause()
	}

	@CallSuper
	override fun onResume() {
		styleController.isSuspended = false
		initializeColors()
		super.onResume()
	}

	@CallSuper
	override fun attachBaseContext(newBase: Context) {
		super.attachBaseContext(LocaleContextWrapper.wrap(newBase))
	}

	private fun initializeColors() {
		StyleManager.initializeFromPreferences(this)
	}
}
