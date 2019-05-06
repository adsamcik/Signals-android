package com.adsamcik.signalcollector.common.data

import android.os.Build
import android.telephony.*
import androidx.annotation.RequiresApi
import androidx.room.ColumnInfo
import androidx.room.Ignore
import com.squareup.moshi.JsonClass

/**
 * Data class that contains all the information about Cell.
 * It works universally with every supported cell technology
 * Supported technologies are GSM, CDMA, WCDMA and LTE
 */
@Suppress("DEPRECATION")
@JsonClass(generateAdapter = true)
data class CellInfo
/**
 * CellInfo constructor
 *
 * @param operatorName [CellInfo.operatorName]
 * @param type         [CellInfo.type]
 * @param cellId           [CellInfo.cellId]
 * @param mcc          [CellInfo.mcc]
 * @param mnc          [CellInfo.mnc]
 * @param dbm          [dbm]
 * @param asu          [asu]
 * @param level        [CellInfo.level]
 */(@ColumnInfo(name = "operator_name")
    var operatorName: String,
    /**
     * Network type.
     */
    var type: CellType,
    /**
     * Cell id
     * GSM - cid
     * CDMA - baseStationId
     * WCDMA - cid
     * LTE - ci
     */
    @ColumnInfo(name = "cell_id")
    var cellId: Int,
    /**
     * Mobile country code
     * Replaced with System ID on CDMA
     */
    var mcc: String,
    /**
     * Mobile network code
     * Replaced with Network ID on CDMA
     */
    var mnc: String,
    /**
     * Strength of signal in asu
     */
    var asu: Int,
    /**
     * Strength of signal in decibels
     */
    @Ignore
    var dbm: Int = 0,
    /**
     * Signal strength as int 0...4 calculated by device
     */
    @Ignore
    var level: Int = 0) {

	constructor(operatorName: String, type: CellType, cellId: Int, mcc: String, mnc: String, asu: Int) : this(operatorName, type, cellId, mcc, mnc, asu, 0, 0)


	companion object {
		/**
		 * Finds carrier name in subscriptions
		 *
		 * @param mnc                  Mobile network code
		 * @param mcc                  Mobile country code
		 * @param subscriptionInfoList Subscribed sim cards
		 * @return carrier name or null if not found
		 */
		@RequiresApi(22)
		private fun getCarrierName(mnc: String, mcc: String, subscriptionInfoList: List<SubscriptionInfo>): String? {
			if (mcc == Integer.MAX_VALUE.toString())
				return null

			return subscriptionInfoList
					.firstOrNull { it.mcc.toString() == mcc && it.mnc.toString() == mnc }?.carrierName?.toString()
		}

		@RequiresApi(22)
		private fun getCarrierNameAndRemove(mnc: String, mcc: String, siList: MutableList<SubscriptionInfo>): String? {
			if (mcc == Integer.MAX_VALUE.toString())
				return null

			val it = siList.iterator()
			while (it.hasNext()) {
				val si = it.next()
				if (si.mcc.toString() == mcc && si.mnc.toString() == mnc) {
					val carrierName = si.carrierName.toString()
					it.remove()
					return carrierName
				}
			}

			return null
		}

		/**
		 * Creates new instance of CellInfo from GSM cell info
		 *
		 * @param cing         GSM cell info
		 * @param operatorName network operator name
		 * @return new CellInfo if successfull, null otherwise
		 */
		fun newInstance(cing: CellInfoGsm, operatorName: String?): CellInfo? {
			if (operatorName == null)
				return null
			val cig = cing.cellIdentity
			val cssg = cing.cellSignalStrength

			val mcc: String
			val mnc: String
			if (Build.VERSION.SDK_INT == 28) {
				mcc = cig.mccString
				mnc = cig.mncString
			} else {
				mcc = cig.mcc.toString()
				mnc = cig.mnc.toString()
			}

			return CellInfo(operatorName, CellType.GSM, cig.cid, mcc, mnc, cssg.asuLevel, cssg.dbm, cssg.level)
		}


		@RequiresApi(22)
		fun newInstance(cing: CellInfoGsm, subscriptionInfoList: MutableList<SubscriptionInfo>): CellInfo? {
			val cig = cing.cellIdentity
			val mcc: String
			val mnc: String
			if (Build.VERSION.SDK_INT == 28) {
				mcc = cig.mccString
				mnc = cig.mncString
			} else {
				mcc = cig.mcc.toString()
				mnc = cig.mnc.toString()
			}
			return newInstance(cing, getCarrierNameAndRemove(mnc, mcc, subscriptionInfoList))
		}

		/**
		 * Creates new instance of CellInfo from CDMA cell info
		 *
		 * @param cinc         CDMA cell info
		 * @param operatorName network operator name
		 * @return new CellInfo if successfull, null otherwise
		 */
		fun newInstance(cinc: CellInfoCdma, operatorName: String?): CellInfo? {
			if (operatorName == null)
				return null
			val cic = cinc.cellIdentity
			val cssg = cinc.cellSignalStrength

			return CellInfo(operatorName, CellType.CDMA, cic.basestationId, cic.systemId.toString(), cic.networkId.toString(), cssg.asuLevel, cssg.dbm, cssg.level)
		}

		@RequiresApi(22)
		fun newInstance(cinc: CellInfoCdma, subscriptionInfoList: List<SubscriptionInfo>): CellInfo? {
			return if (subscriptionInfoList.size == 1)
				newInstance(cinc, subscriptionInfoList[0].carrierName.toString())
			else
				null
		}

		/**
		 * Creates new instance of CellInfo from WCDMA cell info
		 *
		 * @param cinl         WCDMA cell info
		 * @param operatorName network operator name
		 * @return new CellInfo if successfull, null otherwise
		 */
		fun newInstance(cinl: CellInfoWcdma, operatorName: String?): CellInfo? {
			if (operatorName == null)
				return null
			val cil = cinl.cellIdentity
			val cssg = cinl.cellSignalStrength

			val mcc: String
			val mnc: String
			if (Build.VERSION.SDK_INT == 28) {
				mcc = cil.mccString
				mnc = cil.mncString
			} else {
				mcc = cil.mcc.toString()
				mnc = cil.mnc.toString()
			}

			return CellInfo(operatorName, CellType.WCDMA, cil.cid, mcc, mnc, cssg.asuLevel, cssg.dbm, cssg.level)
		}


		@RequiresApi(22)
		fun newInstance(cinl: CellInfoWcdma, subscriptionInfoList: MutableList<SubscriptionInfo>): CellInfo? {
			return if (subscriptionInfoList.size == 1)
				newInstance(cinl, subscriptionInfoList[0].carrierName.toString())
			else {

				val mcc: String
				val mnc: String
				val cil = cinl.cellIdentity
				if (Build.VERSION.SDK_INT == 28) {
					mcc = cil.mccString
					mnc = cil.mncString
				} else {
					mcc = cil.mcc.toString()
					mnc = cil.mnc.toString()
				}

				newInstance(cinl, getCarrierNameAndRemove(mnc, mcc, subscriptionInfoList))
			}
		}


		/**
		 * Creates new instance of CellInfo from LTE cell info
		 *
		 * @param cinl         LTE Cell Info
		 * @param operatorName network operator name
		 * @return new CellInfo if successfull, null otherwise
		 */
		fun newInstance(cinl: CellInfoLte, operatorName: String?): CellInfo? {
			if (operatorName == null)
				return null
			val cil = cinl.cellIdentity
			val cssg = cinl.cellSignalStrength

			val mcc: String
			val mnc: String
			if (Build.VERSION.SDK_INT == 28) {
				mcc = cil.mccString
				mnc = cil.mncString
			} else {
				mcc = cil.mcc.toString()
				mnc = cil.mnc.toString()
			}

			return CellInfo(operatorName, CellType.LTE, cil.ci, mcc, mnc, cssg.asuLevel, cssg.dbm, cssg.level)
		}


		@RequiresApi(22)
		fun newInstance(cinl: CellInfoLte, subscriptionInfoList: MutableList<SubscriptionInfo>): CellInfo? {
			return if (subscriptionInfoList.size == 1)
				newInstance(cinl, subscriptionInfoList[0].carrierName.toString())
			else {
				val cil = cinl.cellIdentity
				val mcc: String
				val mnc: String
				if (Build.VERSION.SDK_INT == 28) {
					mcc = cil.mccString
					mnc = cil.mncString
				} else {
					mcc = cil.mcc.toString()
					mnc = cil.mnc.toString()
				}
				newInstance(cinl, getCarrierNameAndRemove(mnc, mcc, subscriptionInfoList))
			}
		}
	}
}

enum class CellType {
	Unknown,
	GSM,
	CDMA,
	WCDMA,
	LTE,
	NR
}
