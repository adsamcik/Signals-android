package com.adsamcik.signalcollector.data;

import android.annotation.SuppressLint;
import android.location.Location;
import android.net.wifi.ScanResult;
import android.os.Build;
import android.support.annotation.NonNull;
import android.telephony.CellInfo;
import android.telephony.CellInfoCdma;
import android.telephony.CellInfoGsm;
import android.telephony.CellInfoLte;
import android.telephony.CellInfoWcdma;
import android.telephony.TelephonyManager;

import com.google.firebase.crash.FirebaseCrash;
import com.vimeo.stag.UseStag;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

@SuppressWarnings({"FieldCanBeLocal", "unused"})
@UseStag
public class RawData implements Serializable {
	/**
	 * Time of collection in milliseconds since midnight, January 1, 1970 UTC
	 */
	public long time;

	/**
	 * Longitude
	 */
	public Double longitude;

	/**
	 * Latitude
	 */
	public Double latitude;

	/**
	 * Altitude
	 */
	public Double altitude;

	/**
	 * Accuracy in meters
	 */
	public Float accuracy;

	/**
	 * List of registered cells
	 * Null if not collected
	 */
	public CellData[] regCells = null;

	/**
	 * Total cell count
	 * default null if not collected.
	 */
	public Integer cellCount;

	/**
	 * Array of collected wifi networks
	 */
	public WifiData[] wifi = null;

	/**
	 * Time of collection of wifi data
	 */
	public Long wifiTime;

	/**
	 * Current resolved activity
	 */
	public Integer activity;

	public short noise;

	/**
	 * Stag constructor
	 */
	RawData() {}

	/**
	 * RawData constructor
	 *
	 * @param time collection time
	 */
	public RawData(long time) {
		this.time = time;
	}

	/**
	 * Sets collection location
	 *
	 * @param location location
	 * @return this
	 */
	public RawData setLocation(@NonNull Location location) {
		this.longitude = location.getLongitude();
		this.latitude = location.getLatitude();
		this.altitude = location.getAltitude();
		this.accuracy = location.getAccuracy();
		return this;
	}

	/**
	 * Sets wifi and time of wifi collection
	 *
	 * @param data data
	 * @param time time of collection
	 * @return this
	 */
	public RawData setWifi(ScanResult[] data, long time) {
		if (data != null && time > 0) {
			wifi = new WifiData[data.length];
			for (int i = 0; i < data.length; i++)
				wifi[i] = new WifiData(data[i]);
			this.wifiTime = time;
		}
		return this;
	}

	/**
	 * Sets activity
	 *
	 * @param activity activity
	 * @return this
	 */
	public RawData setActivity(int activity) {
		this.activity = activity;
		return this;
	}

	/**
	 * Sets noise value.
	 *
	 * @param noise Noise value. Must be absolute amplitude.
	 * @return this
	 */
	public RawData setNoise(short noise) {
		if (noise > 0)
			this.noise = noise;
		return this;
	}

	/**
	 * Sets current active cell from nearby cells
	 * <p>
	 * //* @param operator current network operator
	 * //* @param data     nearby cell
	 *
	 */
	public void addCell(@NonNull TelephonyManager telephonyManager) {
		//Annoying lint bug CoarseLocation permission is not required when android.permission.ACCESS_FINE_LOCATION is present
		@SuppressLint("MissingPermission") List<CellInfo> cellInfos = telephonyManager.getAllCellInfo();
		String nOp = telephonyManager.getNetworkOperator();
		if (!nOp.isEmpty()) {
			short mcc = Short.parseShort(nOp.substring(0, 3));
			short mnc = Short.parseShort(nOp.substring(3));

			if (cellInfos != null) {
				cellCount = cellInfos.size();
				ArrayList<CellData> registeredCells = new ArrayList<>(Build.VERSION.SDK_INT >= 23 ? telephonyManager.getPhoneCount() : 1);
				for (CellInfo ci : cellInfos) {
					if (ci.isRegistered()) {
						CellData cd = null;
						if (ci instanceof CellInfoLte) {
							CellInfoLte cig = (CellInfoLte) ci;
							if (cig.getCellIdentity().getMnc() == mnc && cig.getCellIdentity().getMcc() == mcc)
								cd = CellData.newInstance(cig, telephonyManager.getNetworkOperatorName());
							else
								cd = CellData.newInstance(cig, (String) null);
						} else if (ci instanceof CellInfoGsm) {
							CellInfoGsm cig = (CellInfoGsm) ci;
							if (cig.getCellIdentity().getMnc() == mnc && cig.getCellIdentity().getMcc() == mcc)
								cd = CellData.newInstance(cig, telephonyManager.getNetworkOperatorName());
							else
								cd = CellData.newInstance(cig, (String) null);
						} else if (ci instanceof CellInfoWcdma) {
							CellInfoWcdma cig = (CellInfoWcdma) ci;
							if (cig.getCellIdentity().getMnc() == mnc && cig.getCellIdentity().getMcc() == mcc)
								cd = CellData.newInstance(cig, telephonyManager.getNetworkOperatorName());
							else
								cd = CellData.newInstance(cig, (String) null);
						} else if (ci instanceof CellInfoCdma) {
							CellInfoCdma cic = (CellInfoCdma) ci;
						/*if (cic.getCellIdentity().getMnc() == mnc && cic.getCellIdentity().getMcc() == mcc)
							addCell(CellData.newInstance(cic, telephonyManager.getNetworkOperatorName()));
						else*/
							cd = CellData.newInstance(cic, (String) null);
						} else {
							FirebaseCrash.report(new Throwable("UNKNOWN CELL TYPE"));
						}

						if (cd != null)
							registeredCells.add(cd);
					}
				}

				regCells = new CellData[registeredCells.size()];
				registeredCells.toArray(regCells);
			}
		}
	}

	/**
	 * Returns cells that user is registered to
	 *
	 * @return list of cells
	 */
	public CellData[] getRegisteredCells() {
		return regCells;
	}
}
