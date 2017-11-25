package com.adsamcik.signalcollector.utility

import android.app.Activity
import android.content.Context
import android.support.design.widget.FloatingActionButton
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ListView
import com.adsamcik.signalcollector.R
import com.adsamcik.signalcollector.adapters.FilterableAdapter
import com.adsamcik.signalcollector.interfaces.IFilterRule
import kotlinx.coroutines.experimental.android.UI
import kotlinx.coroutines.experimental.launch

class FabMenu<in T>(parent: ViewGroup, private val fab: FloatingActionButton?, activity: Activity, filterRule: IFilterRule<T>?, toString: (T) -> String) {
    private val TAG = "SignalsFabMenu"

    private val wrapper: ViewGroup = LayoutInflater.from(activity).inflate(R.layout.fab_menu, parent, false) as ViewGroup
    private val listView: ListView

    private val adapter: FilterableAdapter<T>

    private var callback: ((String) -> Unit)? = null

    private val closeClickListener = View.OnClickListener { _ -> hide() }

    private var isVisible = false
    private var boundsCalculated = false

    val itemCount: Int
        get() = adapter.count

    init {
        listView = wrapper.getChildAt(0) as ListView
        wrapper.visibility = View.INVISIBLE
        listView.visibility = View.INVISIBLE

        adapter = FilterableAdapter(activity, R.layout.spinner_item, null, filterRule, toString)

        listView.adapter = adapter
        listView.setOnItemClickListener { _, _, i, _ -> callback(adapter.getItem(i)) }

        activity.runOnUiThread { parent.addView(wrapper) }
    }

    private fun callback(value: String) {
        callback?.invoke(value)
        hide()
    }

    fun setCallback(callback: ((String) -> Unit)?): FabMenu<*> {
        this.callback = callback
        return this
    }

    fun addItems(itemList: Collection<T>) {
        for (item in itemList)
            addItem(item)
    }

    fun addItems(itemList: Array<out T>) {
        for (item in itemList)
            addItem(item)
    }

    fun addItem(item: T){
        adapter.add(item)
        boundsCalculated = false
    }

    private fun recalculateBounds(context: Context) {
        if (boundsCalculated)
            return

        val dp16px = Assist.dpToPx(context, 16)

        val maxHeight = wrapper.height / 2
        var height: Int
        height = if (adapter.count == 0)
            0
        else {
            val item = adapter.getView(0, null, null)
            item.measure(
                    View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                    View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
            val measureHeight = item.measuredHeight
            measureHeight * adapter.count
        }
        val minHeight = fab!!.height + dp16px
        if (height > maxHeight)
            height = maxHeight
        else if (height < minHeight)
            height = minHeight

        val fabPos = IntArray(2)
        val fabParentPos = IntArray(2)
        val wrapperPos = IntArray(2)
        fab.getLocationOnScreen(fabPos)
        wrapper.getLocationOnScreen(wrapperPos)
        val parent = fab.parent.parent.parent as View
        parent.getLocationOnScreen(fabParentPos)


        val displayMetrics = context.resources.displayMetrics
        listView.x = (displayMetrics.widthPixels - listView.width - dp16px).toFloat()

        fabPos[1] += fab.height / 2
        val halfHeight = height / 2
        var offset = halfHeight
        val botY = fabPos[1] + halfHeight
        val maxY = wrapperPos[1] + wrapper.height - dp16px - Assist.dpToPx(context, 56)
        if (botY > maxY)
            offset += botY - maxY

        val y = fabPos[1] - offset
        listView.y = y.toFloat()

        val layoutParams = FrameLayout.LayoutParams(listView.width, height)
        listView.layoutParams = layoutParams
        boundsCalculated = true
        //Log.d(TAG, "offset " + offset + " y " + y + " max y " + maxY + " bot y " + botY + " height " + menu.getHeight() + " target height " + height + " max height " + maxHeight);
    }

    fun clear(activity: Activity?): FabMenu<*> {
        if (activity != null)
            adapter.clear()
        return this
    }

    private fun destroy(): FabMenu<*> {
        launch(UI) {
            wrapper.removeAllViews()
            (wrapper.parent as ViewGroup).removeView(wrapper)
        }

        return this
    }

    private fun calculateRevealCenter(): IntArray {
        val fabPos = IntArray(2)
        fab!!.getLocationOnScreen(fabPos)
        val menuPos = IntArray(2)
        listView.getLocationOnScreen(menuPos)

        val result = IntArray(2)
        result[0] = fabPos[0] - menuPos[0] + fab.width / 2
        result[1] = fabPos[1] - menuPos[1] + fab.height / 2
        return result
    }

    private fun hide() {
        if (!isVisible)
            return
        isVisible = false
        wrapper.setOnClickListener(null)
        val pos = calculateRevealCenter()
        Animate.revealHide(listView, pos[0], pos[1], 0, { wrapper.visibility = View.INVISIBLE })
    }

    fun hideAndDestroy() {
        if (!isVisible)
            destroy()
        else {
            isVisible = false
            wrapper.setOnClickListener(null)
            val pos = calculateRevealCenter()
            Animate.revealHide(listView, pos[0], pos[1], 0,  { destroy() })
        }
    }

    @Throws(NullPointerException::class)
    fun show(activity: Activity) {
        if (fab == null)
            throw NullPointerException("Fab is null")
        if (isVisible)
            return

        adapter.filter.filter(" ", {
            isVisible = true
            boundsCalculated = false

            recalculateBounds(activity)
            wrapper.visibility = View.VISIBLE
            listView.visibility = View.INVISIBLE
            val fabPos = IntArray(2)
            fab.getLocationOnScreen(fabPos)

            val pos = calculateRevealCenter()
            Animate.revealShow(listView, pos[0], pos[1], 0)
            wrapper.setOnClickListener(closeClickListener)
        })
    }
}
