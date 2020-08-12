package com.adsamcik.tracker.shared.utils.module

import android.content.Context
import android.view.View
import com.adsamcik.tracker.shared.utils.extension.dynamicStyle
import com.adsamcik.tracker.shared.utils.style.StyleManager
import com.adsamcik.tracker.shared.utils.style.StyleView
import com.afollestad.materialdialogs.MaterialDialog

typealias OnDoneListener = (Context, isCloseRequested: Boolean) -> Unit

abstract class FirstRun {
	private val styleController = StyleManager.createController()

	abstract fun onFirstRun(context: Context, onDoneListener: OnDoneListener)

	protected fun createDialog(
			context: Context,
			creator: MaterialDialog.() -> Unit
	) {
		MaterialDialog(context).apply(creator).also {
			it.cancelable(false)
			it.dynamicStyle(
					styleController,
					DIALOG_LAYER
			)
			it.show()
		}
	}

	protected fun notifyContentChanged(view: View) {
		styleController.updateOnce(
				StyleView(
						view,
						DIALOG_LAYER
				), allowRecycler = false
		)
	}

	protected fun onDestroy() {
		StyleManager.recycleController(styleController)
	}

	companion object {
		private const val DIALOG_LAYER = 2
	}
}