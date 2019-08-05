package com.adsamcik.signalcollector.tracker.data.collection

import android.os.Build
import android.telephony.*
import androidx.annotation.RequiresApi

internal class RegisteredOperator(val mcc: String, val mnc: String, val name: String) {
	fun sameNetwork(info: CellInfoLte): Boolean {
		val identity = info.cellIdentity
		return if (Build.VERSION.SDK_INT >= 28) {
			identity.mncString == mnc && identity.mccString == mcc
		} else {
			@Suppress("deprecation")
			identity.mnc.toString() == mnc && identity.mcc.toString() == mcc
		}
	}

	fun sameNetwork(info: CellInfoCdma): Boolean {
		//todo add cdma network matching (can be done if only 1 cell is registered)
		return false
	}

	fun sameNetwork(info: CellInfoGsm): Boolean {
		val identity = info.cellIdentity
		return if (Build.VERSION.SDK_INT >= 28) {
			identity.mncString == mnc && identity.mccString == mcc
		} else {
			@Suppress("deprecation")
			identity.mnc.toString() == mnc && identity.mcc.toString() == mcc
		}
	}

	fun sameNetwork(info: CellInfoWcdma): Boolean {
		val identity = info.cellIdentity
		return if (Build.VERSION.SDK_INT >= 28) {
			identity.mncString == mnc && identity.mccString == mcc
		} else {
			@Suppress("deprecation")
			identity.mnc.toString() == mnc && identity.mcc.toString() == mcc
		}
	}

	@RequiresApi(29)
	fun sameNetwork(info: CellInfoNr): Boolean {
		val identity = info.cellIdentity as CellIdentityNr
		return identity.mncString == mnc && identity.mccString == mcc
	}
}