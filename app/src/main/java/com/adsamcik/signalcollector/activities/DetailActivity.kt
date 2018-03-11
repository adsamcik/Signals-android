package com.adsamcik.signalcollector.activities

import android.os.Bundle
import android.support.v4.app.NavUtils
import android.support.v7.app.AppCompatActivity
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.adsamcik.signalcollector.R
import com.adsamcik.signalcollector.utility.Preferences
import kotlinx.android.synthetic.main.activity_content_detail.*

abstract class DetailActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_content_detail)
        back_button.setOnClickListener({ _ -> onBackPressed() })
    }

    override fun onBackPressed() {
        finish()
    }

    fun setTitle(title: String) {
        val titleView = findViewById<TextView>(R.id.content_detail_title)
        titleView.text = title
    }

    override fun setTitle(title: CharSequence) {
        super.setTitle(title)
        setTitle(title as String)
    }

    override fun setTitle(titleId: Int) {
        super.setTitle(titleId)
        setTitle(getString(titleId))
    }

    private fun createContentLayout(scrollbable: Boolean, addContentPadding: Boolean): LinearLayout {
        val linearLayout = LinearLayout(this)
        val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, if (scrollbable) LinearLayout.LayoutParams.WRAP_CONTENT else LinearLayout.LayoutParams.MATCH_PARENT)
        if (addContentPadding) {
            val padding = resources.getDimension(R.dimen.activity_horizontal_margin).toInt()
            linearLayout.setPadding(padding, padding, padding, padding)
        }
        linearLayout.layoutParams = lp
        linearLayout.id = CONTENT_ID

        linearLayout.orientation = LinearLayout.VERTICAL
        return linearLayout
    }

    protected fun createContentParent(addContentPadding: Boolean): LinearLayout {
        val root = findViewById<LinearLayout>(R.id.content_detail_root)
        val contentParent = createContentLayout(false, addContentPadding)
        root.addView(contentParent)
        return contentParent
    }

    protected fun createScrollableContentParent(addContentPadding: Boolean): LinearLayout {
        val root = findViewById<LinearLayout>(R.id.content_detail_root)
        val scrollView = ScrollView(this)
        val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT)
        scrollView.layoutParams = lp

        val contentParent = createContentLayout(false, addContentPadding)

        scrollView.addView(contentParent)

        root.addView(scrollView)
        return contentParent
    }

    companion object {
        const val CONTENT_ID = 2668368
    }
}
