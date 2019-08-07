package com.adsamcik.signalcollector.tracker.shortcut

import android.os.Bundle
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import com.adsamcik.signalcollector.common.Reporter
import com.adsamcik.signalcollector.tracker.api.TrackerServiceApi
import com.adsamcik.signalcollector.tracker.shortcut.Shortcuts.ShortcutAction

/**
 * ShortcutActivity is activity that handles shortcut actions, so no UI is shown.
 */
@RequiresApi(25)
class ShortcutActivity : AppCompatActivity() {

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		Reporter.initialize(this)
		intent.let {
			if (it.action == Shortcuts.ACTION) {
				val value = it.getIntExtra(Shortcuts.ACTION_STRING, -1)
				if (value < 0 || value >= ShortcutAction.values().size) {
					Reporter.report(Throwable("Invalid value $value"))
				} else {
					onActionReceived(ShortcutAction.values()[value])
				}
			}
			finishAffinity()
		}
	}

	private fun onActionReceived(action: ShortcutAction) {
		when (action) {
			Shortcuts.ShortcutAction.START_COLLECTION -> {
				TrackerServiceApi.startService(this, true)
			}
			Shortcuts.ShortcutAction.STOP_COLLECTION -> {
				TrackerServiceApi.stopService(this)
			}
		}
	}
}
