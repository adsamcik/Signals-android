package com.adsamcik.signalcollector.Data;

import android.net.wifi.ScanResult;
import android.telephony.CellInfo;
import android.telephony.CellInfoCdma;
import android.telephony.CellInfoGsm;
import android.telephony.CellInfoLte;
import android.telephony.CellInfoWcdma;

import com.adsamcik.signalcollector.WifiData;

import java.io.Serializable;

public class Data implements Serializable {
    public CellData[] cell;
    public WifiData[] wifi;
    public double longitude, latitude, altitude;
    public float accuracy;
    public long time;
    private boolean wifiGathered;
    private boolean cellGathered;
    private String networkOperator;
    private float pressure;
    private int currentActivity;

    public Data(long time, double longitude, double latitude, double altitude, float accuracy, CellInfo[] cell, ScanResult[] wifi, float pressure, String networkOperator, int currentActivity) {
        if (cell != null) {
            CellData[] cellData = new CellData[cell.length];
            for (int i = 0; i < cell.length; i++) {
                if (cell[i] instanceof CellInfoGsm)
                    cellData[i] = new CellData((CellInfoGsm) cell[i]);
                else if (cell[i] instanceof CellInfoLte)
                    cellData[i] = new CellData((CellInfoLte) cell[i]);
                else if (cell[i] instanceof CellInfoCdma)
                    cellData[i] = new CellData((CellInfoCdma) cell[i]);
                else if (cell[i] instanceof CellInfoWcdma)
                    cellData[i] = new CellData((CellInfoWcdma) cell[i]);
            }
            this.cell = cellData;
            this.cellGathered = true;
            this.networkOperator = networkOperator;
        }

        if (wifi != null) {
            WifiData[] wifiData = new WifiData[wifi.length];
            for (int i = 0; i < wifi.length; i++) {
                wifiData[i] = new WifiData(wifi[i]);
            }
            this.wifi = wifiData;
            this.wifiGathered = true;
        }
        this.time = time;
        this.longitude = longitude;
        this.latitude = latitude;
        this.altitude = altitude;
        this.accuracy = accuracy;
        this.pressure = pressure;
        this.currentActivity = currentActivity;
    }
}
