package com.adsamcik.signalcollector.activities;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.support.test.InstrumentationRegistry;
import android.support.test.espresso.NoMatchingViewException;
import android.support.test.espresso.ViewAssertion;
import android.support.test.espresso.ViewInteraction;
import android.support.test.espresso.matcher.ViewMatchers;
import android.support.test.runner.AndroidJUnit4;
import android.support.test.uiautomator.By;
import android.support.test.uiautomator.UiDevice;
import android.support.test.uiautomator.Until;
import android.util.Log;
import android.util.MalformedJsonException;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;

import com.adsamcik.signalcollector.R;
import com.adsamcik.signalcollector.data.UploadStats;
import com.adsamcik.signalcollector.enums.CloudStatus;
import com.adsamcik.signalcollector.services.MessageListenerService;
import com.adsamcik.signalcollector.utility.DataStore;
import com.adsamcik.signalcollector.network.Network;
import com.adsamcik.signalcollector.utility.Preferences;
import com.google.gson.Gson;

import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.StringDescription;
import org.hamcrest.TypeSafeMatcher;
import org.junit.Assert;
import org.junit.Before;
import org.junit.runner.RunWith;

import java.util.HashMap;
import java.util.Map;

import static android.support.test.espresso.Espresso.onView;
import static android.support.test.espresso.assertion.ViewAssertions.doesNotExist;
import static android.support.test.espresso.matcher.ViewMatchers.isDisplayed;
import static android.support.test.espresso.matcher.ViewMatchers.withId;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.core.IsNull.notNullValue;
import static org.junit.Assert.assertThat;

@RunWith(AndroidJUnit4.class)
public class AppTest {
	private static final String TAG = "SignalsSaveLoadTest";
	private static final String PACKAGE = "com.adsamcik.signalcollector";
	private static final int LAUNCH_TIMEOUT = 5000;
	private UiDevice mDevice;
	private Context context;

	@Before
	public void before() {
		// Initialize UiDevice instance
		mDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());

		final String launcherPackage = getLauncherPackageName();
		assertThat(launcherPackage, notNullValue());

		// Start from the home screen
		if (!mDevice.getCurrentPackageName().equals(launcherPackage)) {
			mDevice.pressHome();
			mDevice.wait(Until.hasObject(By.pkg(launcherPackage).depth(0)), LAUNCH_TIMEOUT);
		}

		// Launch the blueprint app
		context = InstrumentationRegistry.getContext();
		final Intent intent = context.getPackageManager()
				.getLaunchIntentForPackage(PACKAGE);
		intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK);    // Clear out any previous instances
		context.startActivity(intent);

		// Wait for the app to appear
		mDevice.wait(Until.hasObject(By.pkg(PACKAGE).depth(0)), LAUNCH_TIMEOUT);
	}

	@org.junit.Test
	public void NotificationSavingTest() throws MalformedJsonException, InterruptedException {
		final String testFileName = DataStore.RECENT_UPLOADS_FILE;

		long time = System.currentTimeMillis();
		UploadStats us = new UploadStats(time, 2500, 10, 130, 1, 130, 2, 0, 10654465, 0);
		UploadStats usOld = new UploadStats(20, 2500, 10, 130, 1, 130, 2, 0, 10654465, 0);
		final String data = "{\"cell\":130,\"collections\":130,\"newCell\":1,\"newLocations\":2,\"newNoiseLocations\":0,\"newWifi\":10,\"noiseCollections\":0,\"time\":" + time + ",\"uploadSize\":10654465,\"wifi\":2500}";
		final String dataOld = "{\"cell\":130,\"collections\":130,\"newCell\":1,\"newLocations\":2,\"newNoiseLocations\":0,\"newWifi\":10,\"noiseCollections\":0,\"time\":20,\"uploadSize\":10654465,\"wifi\":2500}";

		Preferences.get().edit().putLong(Preferences.PREF_OLDEST_RECENT_UPLOAD, 20).apply();
		Gson gson = new Gson();
		Assert.assertEquals(true, DataStore.saveJsonArrayAppend(testFileName, gson.toJson(us), true, true));
		Assert.assertEquals(true, DataStore.exists(testFileName));
		Assert.assertEquals('[' + data, DataStore.loadString(testFileName));
		Assert.assertEquals('[' + data + ']', DataStore.loadJsonArrayAppend(testFileName));
		//DataStore.removeOldRecentUploads();
		Assert.assertEquals(true, DataStore.saveJsonArrayAppend(testFileName, gson.toJson(us)));
		Assert.assertEquals('[' + data + ',' + data, DataStore.loadString(testFileName));
		Assert.assertEquals('[' + data + ',' + data + ']', DataStore.loadJsonArrayAppend(testFileName));

		Assert.assertEquals(true, DataStore.saveJsonArrayAppend(testFileName, gson.toJson(usOld)));
		Assert.assertEquals('[' + data + ',' + data + ',' + dataOld, DataStore.loadString(testFileName));
		Assert.assertEquals('[' + data + ',' + data + ',' + dataOld + ']', DataStore.loadJsonArrayAppend(testFileName));
		DataStore.removeOldRecentUploads();

		Assert.assertEquals('[' + data + ',' + data, DataStore.loadString(testFileName));
		Assert.assertEquals('[' + data + ',' + data + ']', DataStore.loadJsonArrayAppend(testFileName));

		DataStore.deleteFile(testFileName);
		DataStore.deleteFile(DataStore.RECENT_UPLOADS_FILE);

		final String WIFI = "wifi";
		final String NEW_WIFI = "newWifi";
		final String CELL = "cell";
		final String NEW_CELL = "newCell";
		final String COLLECTIONS = "collections";
		final String NEW_LOCATIONS = "newLocations";
		final String SIZE = "uploadSize";

		Map<String, String> d = new HashMap<>(10);
		d.put(WIFI, Integer.toString(us.wifi));
		d.put(NEW_WIFI, Integer.toString(us.newWifi));
		d.put(CELL, Integer.toString(us.cell));
		d.put(NEW_CELL, Integer.toString(us.newCell));
		d.put(COLLECTIONS, Integer.toString(us.collections));
		d.put(NEW_LOCATIONS, Integer.toString(us.newLocations));
		d.put(SIZE, Long.toString(us.uploadSize));

		MessageListenerService.parseAndSaveUploadReport(context, time, d);
		Assert.assertEquals('[' + data, DataStore.loadString(DataStore.RECENT_UPLOADS_FILE));

		MessageListenerService.parseAndSaveUploadReport(context, time, d);
		Assert.assertEquals('[' + data + ',' + data, DataStore.loadString(DataStore.RECENT_UPLOADS_FILE));
		DataStore.deleteFile(DataStore.RECENT_UPLOADS_FILE);
	}

	@org.junit.Test
	public void UploadFABTest() throws InterruptedException {
		Network.cloudStatus = CloudStatus.SYNC_REQUIRED;

		Thread.sleep(500);

		mDevice.findObject(By.res(PACKAGE, "action_stats")).click();
		mDevice.findObject(By.res(PACKAGE, "action_tracker")).click();

		Thread.sleep(500);

		ViewInteraction fabUpload = onView(
				allOf(withId(R.id.fabTwo),
						childAtPosition(
								childAtPosition(
										childAtPosition(
												withId(R.id.fabCoordinator),
												0),
										0),
								0),
						isDisplayed()));

		fabUpload.check(matches(isDisplayed()));

		DataStore.onUpload(25);
		Thread.sleep(500);

		ViewInteraction progressBar = onView(
				allOf(withId(R.id.progressBar),
						childAtPosition(
								childAtPosition(
										childAtPosition(
												withId(R.id.fabCoordinator),
												0),
										0),
								1),
						isDisplayed()));
		progressBar.check(matches(isDisplayed()));

		DataStore.onUpload(50);
		Thread.sleep(500);

		DataStore.onUpload(100);
		DataStore.incSizeOfData(500);
		Network.cloudStatus = CloudStatus.SYNC_REQUIRED;
		Thread.sleep(2500);
		fabUpload.check(matches(isDisplayed()));
		progressBar.check(doesNotExist());
	}

	private static Matcher<View> childAtPosition(
			final Matcher<View> parentMatcher, final int position) {

		return new TypeSafeMatcher<View>() {
			@Override
			public void describeTo(Description description) {
				description.appendText("Child at position " + position + " in parent ");
				parentMatcher.describeTo(description);
			}

			@Override
			public boolean matchesSafely(View view) {
				ViewParent parent = view.getParent();
				return parent instanceof ViewGroup && parentMatcher.matches(parent)
						&& view.equals(((ViewGroup) parent).getChildAt(position));
			}
		};
	}

	/**
	 * Uses package manager to find the package name of the device launcher. Usually this package
	 * is "com.android.launcher" but can be different at times. This is a generic solution which
	 * works on all platforms.`
	 */
	private String getLauncherPackageName() {
		// Create launcher Intent
		final Intent intent = new Intent(Intent.ACTION_MAIN);
		intent.addCategory(Intent.CATEGORY_HOME);

		// Use PackageManager to get the launcher package name
		PackageManager pm = InstrumentationRegistry.getContext().getPackageManager();
		ResolveInfo resolveInfo = pm.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY);
		return resolveInfo.activityInfo.packageName;
	}

	/**
	 * Returns a generic {@link ViewAssertion} that asserts that a view exists in the view hierarchy
	 * and is matched by the given view matcher.
	 */
	public static ViewAssertion matches(final Matcher<? super View> viewMatcher) {
		return (View view, NoMatchingViewException noViewException) -> {
			StringDescription description = new StringDescription();
			description.appendText("'");
			viewMatcher.describeTo(description);
			if (noViewException != null) {
				description.appendText(String.format(
						"' check could not be performed because view '%s' was not found.\n",
						noViewException.getViewMatcherDescription()));
				Log.e(TAG, description.toString());
				throw noViewException;
			} else {
				description.appendText("' doesn't match the selected view.");
				ViewMatchers.assertThat(description.toString(), view, viewMatcher);
			}
		};
	}
}
