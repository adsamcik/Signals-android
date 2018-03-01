package com.adsamcik.signalcollector.fragments

import android.app.Activity
import android.os.Bundle
import android.support.v4.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.adsamcik.draggable.IOnDemandView
import com.adsamcik.signalcollector.R
import com.adsamcik.signalcollector.adapters.MapFilterableAdapter
import com.adsamcik.signalcollector.data.MapLayer
import com.adsamcik.signalcollector.test.useMock
import kotlinx.android.synthetic.main.fragment_map_menu.*

class FragmentMapMenu : Fragment(), IOnDemandView {

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.fragment_map_menu, container, true)

        val adapter = MapFilterableAdapter(context!!, R.layout.spinner_item, { it.name })
        list.adapter = adapter

        if (useMock) {
            adapter.addAll(arrayListOf(MapLayer("WiFi"),
                    MapLayer("CenterFon", MapLayer.MAX_LATITUDE / 2, MapLayer.MAX_LONGITUDE / 2, MapLayer.MIN_LATITUDE / 2, MapLayer.MIN_LONGITUDE / 2),
                    MapLayer("Filler01"),
                    MapLayer("Filler02"),
                    MapLayer("Filler03"),
                    MapLayer("Filler04")))
        }

        return view
    }

    override fun onEnter(activity: Activity) {

    }

    override fun onLeave(activity: Activity) {

    }

    override fun onPermissionResponse(requestCode: Int, success: Boolean) {

    }
}