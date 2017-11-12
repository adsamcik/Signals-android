package com.adsamcik.signalcollector.services;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.location.LocationProvider;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.os.PowerManager;
import android.support.annotation.Nullable;
import android.support.v4.app.NotificationCompat;
import android.support.v4.content.ContextCompat;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;

import com.adsamcik.signalcollector.NoiseTracker;
import com.adsamcik.signalcollector.R;
import com.adsamcik.signalcollector.activities.MainActivity;
import com.adsamcik.signalcollector.data.RawData;
import com.adsamcik.signalcollector.enums.ResolvedActivity;
import com.adsamcik.signalcollector.file.DataStore;
import com.adsamcik.signalcollector.interfaces.ICallback;
import com.adsamcik.signalcollector.receivers.NotificationReceiver;
import com.adsamcik.signalcollector.utility.ActivityInfo;
import com.adsamcik.signalcollector.utility.Assist;
import com.adsamcik.signalcollector.utility.Constants;
import com.adsamcik.signalcollector.utility.Preferences;
import com.adsamcik.signalcollector.utility.Shortcuts;
import com.google.firebase.crash.FirebaseCrash;
import com.google.gson.Gson;

import java.lang.ref.WeakReference;
import java.math.RoundingMode;
import java.nio.charset.Charset;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;

import static com.adsamcik.signalcollector.utility.Constants.MINUTE_IN_MILLISECONDS;
import static com.adsamcik.signalcollector.utility.Constants.NOISE_ENABLED;
import static com.adsamcik.signalcollector.utility.Constants.SECOND_IN_MILLISECONDS;

public class TrackerService extends Service {
	//Constants
	private static final String TAG = "SignalsTracker";
	private final static int LOCK_TIME_IN_MINUTES = 30;
	private final static int LOCK_TIME_IN_MILLISECONDS = LOCK_TIME_IN_MINUTES * MINUTE_IN_MILLISECONDS;
	private static final int NOTIFICATION_ID_SERVICE = 7643;

	public final static int UPDATE_TIME_SEC = 2;
	private final float MIN_DISTANCE_M = 5;

	public static ICallback onServiceStateChange;
	public static ICallback onNewDataFound;

	/**
	 * RawData from previous collection
	 */
	public static RawData rawDataEcho;
	/**
	 * Extra information about distance for tracker
	 */
	public static int distanceToWifi;

	/**
	 * Weak reference to service for AutoLock and check if service is running
	 */
	private static WeakReference<TrackerService> service;
	private static long lockedUntil;
	private static boolean backgroundActivated = false;

	private final float MAX_NOISE_TRACKING_SPEED_KM = 18;
	private final long TRACKING_ACTIVE_SINCE = System.currentTimeMillis();
	private final ArrayList<RawData> data = new ArrayList<>();

	private long wifiScanTime;
	private boolean wasWifiEnabled = false;
	private int saveAttemptsFailed = 0;
	private LocationListener locationListener;
	private ScanResult[] wifiScanData;
	private WifiReceiver wifiReceiver;
	private NotificationManager notificationManager;

	private PowerManager powerManager;
	private PowerManager.WakeLock wakeLock;
	private LocationManager locationManager;
	private TelephonyManager telephonyManager;
	private SubscriptionManager subscriptionManager;
	private WifiManager wifiManager;
	private final Gson gson = new Gson();

	private NoiseTracker noiseTracker;
	private boolean noiseActive = false;

	/**
	 * True if previous collection was mocked
	 */
	private boolean prevMocked = false;

	/**
	 * Previous location of collection
	 */
	private Location prevLocation = null;

	/**
	 * Checks if service is running
	 *
	 * @return true if service is running
	 */
	public static boolean isRunning() {
		return service != null && service.get() != null;
	}

	/**
	 * Checks if Tracker is auto locked
	 *
	 * @return true if locked
	 */
	public static boolean isAutoLocked() {
		return System.currentTimeMillis() < lockedUntil;
	}

	/**
	 * Checks if tracker was activated in background
	 *
	 * @return true if activated by the app
	 */
	public static boolean isBackgroundActivated() {
		return backgroundActivated;
	}

	/**
	 * Sets auto lock with predefined time {@link TrackerService#LOCK_TIME_IN_MINUTES}
	 */
	public static int setAutoLock() {
		lockedUntil = System.currentTimeMillis() + LOCK_TIME_IN_MILLISECONDS;

		if (isRunning() && isBackgroundActivated())
			service.get().stopSelf();
		return LOCK_TIME_IN_MINUTES;
	}

	private void updateData(Location location) {
		if (location.isFromMockProvider()) {
			prevMocked = true;
			return;
		} else if (prevMocked && prevLocation != null) {
			prevMocked = false;
			if (location.distanceTo(prevLocation) < MIN_DISTANCE_M)
				return;
		}

		if (location.getAltitude() > 5600) {
			setAutoLock();
			//todo add notification
			if (!isBackgroundActivated())
				stopSelf();
			return;
		}

		wakeLock.acquire(10 * 60 * 1000L /*10 minutes*/);
		RawData d = new RawData(System.currentTimeMillis());

		if (wifiManager != null) {
			if (prevLocation != null) {
				if (wifiScanData != null) {
					double timeDiff = (double) (wifiScanTime - prevLocation.getTime()) / (double) (d.time - prevLocation.getTime());
					if (timeDiff >= 0) {
						float distTo = location.distanceTo(Assist.interpolateLocation(prevLocation, location, timeDiff));
						distanceToWifi = (int) distTo;
						//Log.d(TAG, "dist to wifi " + distTo);
						int UPDATE_MAX_DISTANCE_TO_WIFI = 40;
						if (distTo <= UPDATE_MAX_DISTANCE_TO_WIFI && distTo > 0)
							d.setWifi(wifiScanData, wifiScanTime);
					}
					wifiScanData = null;
				} else {
					double timeDiff = (double) (wifiScanTime - prevLocation.getTime()) / (double) (d.time - prevLocation.getTime());
					if (timeDiff >= 0) {
						float distTo = location.distanceTo(Assist.interpolateLocation(prevLocation, location, timeDiff));
						distanceToWifi += (int) distTo;
					}
				}
			}

			wifiManager.startScan();
		}

		if (telephonyManager != null && !Assist.isAirplaneModeEnabled(this)) {
			d.addCell(telephonyManager);
		}

		ActivityInfo activityInfo = ActivityService.getLastActivity();

		if (noiseTracker != null) {
			float MAX_NOISE_TRACKING_SPEED_M = (float) (MAX_NOISE_TRACKING_SPEED_KM / 3.6);
			if ((activityInfo.resolvedActivity == ResolvedActivity.ON_FOOT || (noiseActive && activityInfo.resolvedActivity == ResolvedActivity.UNKNOWN)) && location.getSpeed() < MAX_NOISE_TRACKING_SPEED_M) {
				noiseTracker.start();
				short value = noiseTracker.getSample(10);
				if (value >= 0)
					d.setNoise(value);
				noiseActive = true;
			} else {
				noiseTracker.stop();
				noiseActive = false;
			}
		}

		if (Preferences.get(this).getBoolean(Preferences.PREF_TRACKING_LOCATION_ENABLED, Preferences.DEFAULT_TRACKING_LOCATION_ENABLED))
			d.setLocation(location).setActivity(activityInfo.resolvedActivity);

		data.add(d);
		rawDataEcho = d;

		DataStore.incData(this, gson.toJson(d).getBytes(Charset.defaultCharset()).length, 1);

		prevLocation = location;
		prevLocation.setTime(d.time);

		notificationManager.notify(NOTIFICATION_ID_SERVICE, generateNotification(true, d));

		if (onNewDataFound != null) {
			onNewDataFound.callback();
		}

		if (data.size() > 5)
			saveData();

		if (backgroundActivated && powerManager.isPowerSaveMode())
			stopSelf();

		wakeLock.release();
	}


	private void saveData() {
		if (data.size() == 0) return;

		SharedPreferences sp = Preferences.get(this);
		Preferences.checkStatsDay(this);

		int wifiCount, cellCount, locations;

		wifiCount = sp.getInt(Preferences.PREF_STATS_WIFI_FOUND, 0);
		cellCount = sp.getInt(Preferences.PREF_STATS_CELL_FOUND, 0);
		locations = sp.getInt(Preferences.PREF_STATS_LOCATIONS_FOUND, 0);
		for (RawData d : data) {
			if (d.wifi != null)
				wifiCount += d.wifi.length;
			if (d.cellCount != null)
				cellCount += d.cellCount;
		}

		DataStore.SaveStatus result = DataStore.saveData(this, data.toArray(new RawData[data.size()]));
		if (result == DataStore.SaveStatus.SAVE_FAILED) {
			saveAttemptsFailed++;
			if (saveAttemptsFailed >= 5)
				stopSelf();
		} else {
			sp.edit()
					.putInt(Preferences.PREF_STATS_WIFI_FOUND, wifiCount)
					.putInt(Preferences.PREF_STATS_CELL_FOUND, cellCount)
					.putInt(Preferences.PREF_STATS_LOCATIONS_FOUND, locations + data.size())
					.putInt(Preferences.PREF_COLLECTIONS_SINCE_LAST_UPLOAD, sp.getInt(Preferences.PREF_COLLECTIONS_SINCE_LAST_UPLOAD, 0) + data.size())
					.apply();
			data.clear();
			if (result == DataStore.SaveStatus.SAVE_SUCCESS_FILE_DONE &&
					!Preferences.get(this).getBoolean(Preferences.PREF_AUTO_UPLOAD_SMART, Preferences.DEFAULT_AUTO_UPLOAD_SMART) &&
					DataStore.sizeOfData(this) >= Constants.U_MEGABYTE * Preferences.get(this).getInt(Preferences.PREF_AUTO_UPLOAD_AT_MB, Preferences.DEFAULT_AUTO_UPLOAD_AT_MB)) {
				UploadService.requestUpload(this, UploadService.UploadScheduleSource.BACKGROUND);
				FirebaseCrash.log("Requested upload from tracking");
			}
		}
	}

	private Notification generateNotification(boolean gpsAvailable, RawData d) {
		Intent intent = new Intent(this, MainActivity.class);
		NotificationCompat.Builder builder = new NotificationCompat.Builder(this, getString(R.string.channel_track_id))
				.setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
				.setSmallIcon(R.drawable.ic_signals)  // the done icon
				.setTicker(getString(R.string.notification_tracker_active_ticker))  // the done text
				.setWhen(System.currentTimeMillis())  // the time stamp
				.setContentIntent(PendingIntent.getActivity(this, 0, intent, 0)) // The intent to send when the entry is clicked
				.setColor(ContextCompat.getColor(this, R.color.color_accent));

		Intent stopIntent = new Intent(this, NotificationReceiver.class);
		stopIntent.putExtra(NotificationReceiver.ACTION_STRING, backgroundActivated ? 0 : 1);
		PendingIntent stop = PendingIntent.getBroadcast(this, 0, stopIntent, PendingIntent.FLAG_UPDATE_CURRENT);
		if (backgroundActivated)
			builder.addAction(R.drawable.ic_battery_alert_black_24dp, getString(R.string.notification_stop_til_recharge), stop);
		else
			builder.addAction(R.drawable.ic_pause, getString(R.string.notification_stop), stop);

		if (!gpsAvailable)
			builder.setContentTitle(getString(R.string.notification_looking_for_gps));
		else {
			builder.setContentTitle(getString(R.string.notification_tracking_active));
			builder.setContentText(buildNotificationText(d));
		}

		return builder.build();
	}

	private String buildNotificationText(final RawData d) {
		StringBuilder sb = new StringBuilder();
		DecimalFormat df = new DecimalFormat("#.#");
		df.setRoundingMode(RoundingMode.HALF_UP);
		if (d.wifi != null)
			sb.append(d.wifi.length).append(" wifi ");
		if (d.cellCount != null)
			sb.append(d.cellCount).append(" cell ");
		if (d.noise > 0)
			sb.append(df.format(Assist.amplitudeToDbm(d.noise))).append(" dB ");
		if (sb.length() > 0)
			sb.setLength(sb.length() - 1);
		else
			sb.append("Nothing found");
		return sb.toString();
	}


	@Override
	public void onCreate() {
		service = new WeakReference<>(this);
		Assist.initialize(this);
		SharedPreferences sp = Preferences.get(this);

		ActivityService.requestActivity(this, getClass(), UPDATE_TIME_SEC);

		//Get managers
		locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
		notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
		powerManager = (PowerManager) getSystemService(POWER_SERVICE);
		assert powerManager != null;
		wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "TrackerWakeLock");

		//Enable location update
		locationListener = new LocationListener() {
			public void onLocationChanged(Location location) {
				updateData(location);

			}

			public void onStatusChanged(String provider, int status, Bundle extras) {
				if (status == LocationProvider.TEMPORARILY_UNAVAILABLE || status == LocationProvider.OUT_OF_SERVICE)
					notificationManager.notify(NOTIFICATION_ID_SERVICE, generateNotification(false, null));
			}

			public void onProviderEnabled(String provider) {
			}

			public void onProviderDisabled(String provider) {
				if (provider.equals(LocationManager.GPS_PROVIDER))
					stopSelf();
			}
		};

		int UPDATE_TIME_MILLISEC = UPDATE_TIME_SEC * SECOND_IN_MILLISECONDS;
		if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED)
			locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, UPDATE_TIME_MILLISEC, MIN_DISTANCE_M, locationListener);
		else {
			FirebaseCrash.report(new Exception("Tracker does not have sufficient permissions"));
			stopSelf();
			return;
		}

		//Wifi tracking setup
		if (sp.getBoolean(Preferences.PREF_TRACKING_WIFI_ENABLED, true)) {
			wifiManager = (WifiManager) this.getSystemService(Context.WIFI_SERVICE);
			assert wifiManager != null;
			wasWifiEnabled = !(wifiManager.isScanAlwaysAvailable() || wifiManager.isWifiEnabled());
			if (wasWifiEnabled)
				wifiManager.setWifiEnabled(true);

			wifiManager.startScan();
			registerReceiver(wifiReceiver = new WifiReceiver(), new IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION));
		}

		//Cell tracking setup
		if (sp.getBoolean(Preferences.PREF_TRACKING_CELL_ENABLED, true)) {
			telephonyManager = (TelephonyManager) getSystemService(TELEPHONY_SERVICE);
			if (Build.VERSION.SDK_INT >= 22)
				subscriptionManager = SubscriptionManager.from(this);
			else
				subscriptionManager = null;
		}

		//Shortcut setup
		if (android.os.Build.VERSION.SDK_INT >= 25) {
			Shortcuts.initializeShortcuts(this);
			Shortcuts.updateShortcut(this, Shortcuts.TRACKING_ID, getString(R.string.shortcut_stop_tracking), getString(R.string.shortcut_stop_tracking_long), R.drawable.ic_pause, Shortcuts.ShortcutType.STOP_COLLECTION);
		}

		UploadService.cancelUploadSchedule(this);
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		lockedUntil = 0;
		backgroundActivated = intent == null || intent.getBooleanExtra("backTrack", false);
		startForeground(NOTIFICATION_ID_SERVICE, generateNotification(false, null));
		if (onServiceStateChange != null)
			onServiceStateChange.callback();
		if (NOISE_ENABLED && Preferences.get(this).getBoolean(Preferences.PREF_TRACKING_NOISE_ENABLED, false))
			noiseTracker = new NoiseTracker(this).start();
		return super.onStartCommand(intent, flags, startId);
	}


	@Override
	public void onDestroy() {
		stopForeground(true);
		service = null;

		/*if (noiseTracker != null)
			noiseTracker.stop();*/

		ActivityService.removeActivityRequest(this, getClass());

		if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED)
			locationManager.removeUpdates(locationListener);

		if (wifiReceiver != null)
			unregisterReceiver(wifiReceiver);

		saveData();
		if (onServiceStateChange != null)
			onServiceStateChange.callback();
		DataStore.cleanup(this);

		if (wasWifiEnabled) {
			if (!powerManager.isInteractive())
				wifiManager.setWifiEnabled(false);
		}

		SharedPreferences sp = Preferences.get(this);
		sp.edit().putInt(Preferences.PREF_STATS_MINUTES, sp.getInt(Preferences.PREF_STATS_MINUTES, 0) + (int) ((System.currentTimeMillis() - TRACKING_ACTIVE_SINCE) / MINUTE_IN_MILLISECONDS)).apply();

		if (android.os.Build.VERSION.SDK_INT >= 25) {
			Shortcuts.initializeShortcuts(this);
			Shortcuts.updateShortcut(this, Shortcuts.TRACKING_ID, getString(R.string.shortcut_start_tracking), getString(R.string.shortcut_start_tracking_long), R.drawable.ic_play, Shortcuts.ShortcutType.START_COLLECTION);
		}

		if (sp.getBoolean(Preferences.PREF_AUTO_UPLOAD_SMART, Preferences.DEFAULT_AUTO_UPLOAD_SMART))
			UploadService.requestUploadSchedule(this);
	}

	@Nullable
	@Override
	public IBinder onBind(Intent intent) {
		return null;
	}


	private class WifiReceiver extends BroadcastReceiver {
		@Override
		public void onReceive(Context context, Intent intent) {
			wifiScanTime = System.currentTimeMillis();
			List<ScanResult> result = wifiManager.getScanResults();
			wifiScanData = result.toArray(new ScanResult[result.size()]);
		}
	}
}