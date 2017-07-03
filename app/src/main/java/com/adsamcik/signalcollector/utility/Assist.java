package com.adsamcik.signalcollector.utility;

import android.animation.ObjectAnimator;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.content.res.Resources;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.RippleDrawable;
import android.location.Location;
import android.location.LocationManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Build;
import android.provider.Settings;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.res.ResourcesCompat;
import android.telephony.TelephonyManager;
import android.util.DisplayMetrics;
import android.view.Display;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.ScrollView;

import com.adsamcik.signalcollector.R;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.location.DetectedActivity;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

public class Assist {
	public static final int SECOND_IN_MILLISECONDS = 1000;
	public static final int MINUTE_IN_MILLISECONDS = 60 * SECOND_IN_MILLISECONDS;
	public static final int HOUR_IN_MILLISECONDS = 60 * MINUTE_IN_MILLISECONDS;
	public static final int DAY_IN_MILLISECONDS = 24 * HOUR_IN_MILLISECONDS;
	public static final int DAY_IN_MINUTES = DAY_IN_MILLISECONDS / MINUTE_IN_MILLISECONDS;

	public static final long KB_IN_BYTES = 1000;
	public static final long MB_IN_BYTES = KB_IN_BYTES * 1000;


	private static TelephonyManager telephonyManager;
	private static ConnectivityManager connectivityManager;

	@SuppressWarnings("BooleanMethodIsAlwaysInverted")
	public static boolean isInitialized() {
		return telephonyManager != null;
	}

	/**
	 * Initializes TelephonyManager and ConnectivityManager in Assist class
	 *
	 * @param c context
	 */
	public static void initialize(Context c) {
		telephonyManager = (TelephonyManager) c.getSystemService(Context.TELEPHONY_SERVICE);
		connectivityManager = (ConnectivityManager) c.getSystemService(Context.CONNECTIVITY_SERVICE);
	}

	/**
	 * Converts raw byte count to human readable byte count
	 *
	 * @param bytes bytes
	 * @param si    if true uses decimal (1000) representation otherwise binary (1024)
	 * @return human readable byte count
	 */
	public static String humanReadableByteCount(long bytes, boolean si) {
		int unit = si ? 1000 : 1024;
		if (bytes < unit) return bytes + " B";
		int exp = (int) (Math.log(bytes) / Math.log(unit));
		String pre = (si ? "kMGTPE" : "KMGTPE").charAt(exp - 1) + (si ? "" : "i");
		return String.format(Locale.ENGLISH, "%.1f %sB", bytes / Math.pow(unit, exp), pre);
	}

	/**
	 * Gets SW navbar height
	 *
	 * @param c context
	 * @return height, 0 if HW navbar is present
	 */
	public static int getNavBarHeight(@NonNull Context c) {
		Resources r = c.getResources();
		int resourceId = r.getIdentifier("navigation_bar_height", "dimen", "android");
		if (resourceId > 0)
			return r.getDimensionPixelSize(resourceId);
		return 0;
	}

	/**
	 * Checks if device has SW or HW navbar
	 *
	 * @param windowManager Window Manager
	 * @return true if SW navbar is present
	 */
	public static boolean hasNavBar(@NonNull WindowManager windowManager) {
		Display d = windowManager.getDefaultDisplay();

		DisplayMetrics realDisplayMetrics = new DisplayMetrics();
		d.getRealMetrics(realDisplayMetrics);

		int realHeight = realDisplayMetrics.heightPixels;
		int realWidth = realDisplayMetrics.widthPixels;

		DisplayMetrics displayMetrics = new DisplayMetrics();
		d.getMetrics(displayMetrics);

		int displayHeight = displayMetrics.heightPixels;
		int displayWidth = displayMetrics.widthPixels;

		return (realWidth - displayWidth) > 0 || (realHeight - displayHeight) > 0;
	}

	/**
	 * Slightly more optimized function for conversion from density-independent pixels to pixels
	 *
	 * @param dm display metrics
	 * @param dp Density-independent Pixels
	 * @return pixels
	 */
	public static int dpToPx(@NonNull DisplayMetrics dm, int dp) {
		return Math.round(dp * dm.density);
	}

	/**
	 * Function for conversion from dp to px
	 *
	 * @param c  context
	 * @param dp Density-independent Pixels
	 * @return pixels
	 */
	public static int dpToPx(@NonNull Context c, int dp) {
		return Math.round(dp * c.getResources().getDisplayMetrics().density);
	}

	/**
	 * Function for conversion of pixels to density-independent pixels
	 *
	 * @param c  context
	 * @param px pixels
	 * @return Density-independent pixels
	 */
	public static int pxToDp(@NonNull Context c, int px) {
		return Math.round(px / c.getResources().getDisplayMetrics().density);
	}

	/**
	 * Function for conversion of point to pixels
	 *
	 * @param c  context
	 * @param pt point
	 * @return pixels
	 */
	public static int ptToPx(@NonNull Context c, int pt) {
		return (int) (pt * c.getResources().getDisplayMetrics().density + 0.5f);
	}

	/**
	 * Generates position between two passed positions based on time
	 *
	 * @param locationOne first location
	 * @param locationTwo second location
	 * @param time        Value between 0 and 1. 0 is locationOne, 1 is locationTwo
	 * @return interpolated location
	 */
	public static Location interpolateLocation(@NonNull Location locationOne, @NonNull Location locationTwo, double time) {
		Location l = new Location("interpolation");
		l.setLatitude(locationOne.getLatitude() + (locationTwo.getLatitude() - locationOne.getLatitude()) * time);
		l.setLongitude(locationOne.getLongitude() + (locationTwo.getLongitude() - locationOne.getLongitude()) * time);
		l.setAltitude(locationOne.getAltitude() + (locationTwo.getAltitude() - locationOne.getAltitude()) * time);
		return l;
	}

	/**
	 * Checks if required permission are available
	 * ACCESS_FINE_LOCATION - GPS
	 * READ_PHONE_STATE - IMEI
	 *
	 * @param context context
	 * @return permissions that app does not have, null if api is lower than 23 or all permission are acquired
	 */
	public static String[] checkTrackingPermissions(@NonNull Context context) {
		if (Build.VERSION.SDK_INT > 22) {
			List<String> permissions = new ArrayList<>();
			if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED)
				permissions.add(android.Manifest.permission.ACCESS_FINE_LOCATION);

			if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED)
				permissions.add(android.Manifest.permission.READ_PHONE_STATE);

			if (Preferences.get(context).getBoolean(Preferences.PREF_TRACKING_NOISE_ENABLED, false) && ContextCompat.checkSelfPermission(context, android.Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED)
				permissions.add(android.Manifest.permission.RECORD_AUDIO);

			if (permissions.size() == 0)
				return null;

			return permissions.toArray(new String[permissions.size()]);
		}
		return null;
	}

	/**
	 * Converts amplitude to dbm
	 *
	 * @param amplitude amplitude
	 * @return dbm
	 */
	public static double amplitudeToDbm(final short amplitude) {
		return 20 * Math.log10(Math.abs(amplitude));
	}

	/**
	 * 0 still/default
	 * 1 foot
	 * 2 vehicle
	 * 3 tilting
	 */
	public static int evaluateActivity(final int val) {
		switch (val) {
			case DetectedActivity.STILL:
				return 0;
			case DetectedActivity.RUNNING:
				return 1;
			case DetectedActivity.ON_FOOT:
				return 1;
			case DetectedActivity.WALKING:
				return 1;
			case DetectedActivity.ON_BICYCLE:
				return 2;
			case DetectedActivity.IN_VEHICLE:
				return 2;
			case DetectedActivity.TILTING:
				return 3;
			default:
				return 3;
		}
	}

	/**
	 * Returns activity as string
	 *
	 * @param type type of activity (ENUM)
	 * @return name of the activity
	 */
	public static String getActivityName(int type) {
		switch (type) {
			case DetectedActivity.IN_VEHICLE:
				return "In Vehicle";
			case DetectedActivity.ON_BICYCLE:
				return "On Bicycle";
			case DetectedActivity.ON_FOOT:
				return "On Foot";
			case DetectedActivity.WALKING:
				return "Walking";
			case DetectedActivity.STILL:
				return "Still";
			case DetectedActivity.TILTING:
				return "Tilting";
			case DetectedActivity.RUNNING:
				return "Running";
			case DetectedActivity.UNKNOWN:
				return "Unknown";
		}
		return "N/A";
	}

	/**
	 * Converts coordinate to string
	 *
	 * @param coordinate coordinate
	 * @return stringified coordinate
	 */
	public static String coordsToString(double coordinate) {
		int degree = (int) coordinate;
		coordinate = (coordinate - degree) * 60;
		int minute = (int) coordinate;
		coordinate = (coordinate - minute) * 60;
		int second = (int) coordinate;
		return String.format(Locale.ENGLISH, "%02d", degree) + "\u00B0 " + String.format(Locale.ENGLISH, "%02d", minute) + "' " + String.format(Locale.ENGLISH, "%02d", second) + "\"";
	}

	/**
	 * @return Today as a day in unix time
	 */
	public static long getDayInUTC() {
		Calendar c = Calendar.getInstance();
		c.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));
		c.set(Calendar.HOUR_OF_DAY, 0);
		c.set(Calendar.MINUTE, 0);
		c.set(Calendar.SECOND, 0);
		c.set(Calendar.MILLISECOND, 0);
		return c.getTimeInMillis();
	}

	/**
	 * Checks if airplane mode is turned on
	 *
	 * @param context context
	 * @return true if airplane mode is turned on
	 */
	public static boolean isAirplaneModeEnabled(@NonNull Context context) {
		return Settings.Global.getInt(context.getContentResolver(),
				Settings.Global.AIRPLANE_MODE_ON, 0) != 0;
	}

	/**
	 * Checks if device is connecting or is connected to network
	 *
	 * @return true if connected or connecting
	 */
	public static boolean hasNetwork() {
		NetworkInfo activeNetwork = connectivityManager.getActiveNetworkInfo();
		return activeNetwork != null && activeNetwork.isConnectedOrConnecting();
	}

	/**
	 * Returns whether satellite position is allowed
	 * GNSS is universal term for global navigation satellite system
	 *
	 * @param context context
	 * @return true if enabled
	 */
	public static boolean isGNSSEnabled(@NonNull Context context) {
		return ((LocationManager) context.getSystemService(Context.LOCATION_SERVICE)).isProviderEnabled(LocationManager.GPS_PROVIDER);
	}

	/**
	 * Returns how old is supplied unix time in days
	 *
	 * @param time unix time in milliseconds
	 * @return number of days as age (e.g. +50 = 50 days old)
	 */
	public static int getAgeInDays(long time) {
		return (int) ((System.currentTimeMillis() - time) / DAY_IN_MILLISECONDS);
	}

	public static String getDeviceID() {
		return Build.MANUFACTURER + Build.DEVICE;
	}

	/**
	 * Checks if the device looks like an emulator. This is used primarily to detect automated testing.
	 *
	 * @return true if emulator is detected
	 */
	@SuppressWarnings("BooleanMethodIsAlwaysInverted")
	public static boolean isEmulator() {
		return Build.FINGERPRINT.startsWith("generic")
				|| Build.FINGERPRINT.startsWith("unknown")
				|| Build.MODEL.contains("google_sdk")
				|| Build.MODEL.contains("Emulator")
				|| Build.MODEL.contains("Android SDK built for x86")
				|| Build.MANUFACTURER.contains("Genymotion")
				|| (Build.BRAND.startsWith("generic") && Build.DEVICE.startsWith("generic"))
				|| "google_sdk".equals(Build.PRODUCT);
	}

	/**
	 * Checks if play services are available
	 *
	 * @param context context
	 * @return true if available
	 */
	public static boolean isPlayServiceAvailable(@NonNull Context context) {
		GoogleApiAvailability gaa = GoogleApiAvailability.getInstance();
		return gaa != null && gaa.isGooglePlayServicesAvailable(context) == ConnectionResult.SUCCESS;
	}

	/**
	 * Generate ripple drawable
	 *
	 * @param normalColor  if 0, background is transparent
	 * @param pressedColor pressed color
	 * @return RippleDrawable
	 */
	public static RippleDrawable getPressedColorRippleDrawable(int normalColor, int pressedColor, @Nullable Drawable mask) {
		return new RippleDrawable(getPressedColorSelector(pressedColor), normalColor == 0 ? null : getColorDrawableFromColor(normalColor), mask);
	}

	private static ColorStateList getPressedColorSelector(int pressedColor) {
		return new ColorStateList(new int[][]{new int[]{}}, new int[]{pressedColor}
		);
	}

	private static ColorDrawable getColorDrawableFromColor(int color) {
		return new ColorDrawable(color);
	}

	/**
	 * Animate smooth scroll to y coordinate
	 *
	 * @param scrollView Scroll view
	 * @param y          target y coordinate
	 * @param millis     duration of animation
	 */
	public static void verticalSmoothScrollTo(final ScrollView scrollView, final int y, final int millis) {
		ObjectAnimator.ofInt(scrollView, "scrollY", scrollView.getScrollY(), y).setDuration(millis).start();
	}

	/**
	 * Formats 1000 as 1 000
	 *
	 * @param number input number
	 * @return formatted number
	 */
	public static String formatNumber(int number) {
		DecimalFormat df = new DecimalFormat("#,###,###");
		return df.format(number).replaceAll(",", " ");
	}

	/**
	 * Formats 1000 as 1 000
	 *
	 * @param number input number
	 * @return formatted number
	 */
	public static String formatNumber(long number) {
		DecimalFormat df = new DecimalFormat("#,###,###");
		return df.format(number).replaceAll(",", " ");
	}

	/**
	 * Hides software keyboard
	 *
	 * @param activity activity
	 * @param view     view that should have summoned the keyboard
	 */
	public static void hideSoftKeyboard(Activity activity, View view) {
		InputMethodManager imm = (InputMethodManager) activity.getSystemService(Context.INPUT_METHOD_SERVICE);
		imm.hideSoftInputFromWindow(view.getApplicationWindowToken(), 0);
	}

	/**
	 * Returns array of color state lists in this order: Default, Selected
	 *
	 * @param resources resources
	 * @return array of color states
	 */
	public static ColorStateList[] getSelectionStateLists(@NonNull Resources resources, @NonNull Resources.Theme theme) {
		return new ColorStateList[]{
				ResourcesCompat.getColorStateList(resources, R.color.default_value, theme),
				ResourcesCompat.getColorStateList(resources, R.color.selected_value, theme)
		};
	}
}
