package com.adsamcik.signalcollector.activities;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.StringRes;
import android.support.design.widget.BottomNavigationView;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.content.ContextCompat;
import android.util.Log;

import com.adsamcik.signalcollector.enums.CloudStatus;
import com.adsamcik.signalcollector.fragments.FragmentActivities;
import com.adsamcik.signalcollector.services.UploadService;
import com.adsamcik.signalcollector.utility.Assist;
import com.adsamcik.signalcollector.R;
import com.adsamcik.signalcollector.file.DataStore;
import com.adsamcik.signalcollector.network.Network;
import com.adsamcik.signalcollector.utility.Constants;
import com.adsamcik.signalcollector.utility.Preferences;
import com.adsamcik.signalcollector.utility.SnackMaker;
import com.adsamcik.signalcollector.utility.Failure;
import com.adsamcik.signalcollector.fragments.FragmentTracker;
import com.adsamcik.signalcollector.fragments.FragmentMap;
import com.adsamcik.signalcollector.fragments.FragmentSettings;
import com.adsamcik.signalcollector.fragments.FragmentStats;
import com.adsamcik.signalcollector.interfaces.ITabFragment;
import com.adsamcik.signalcollector.signin.Signin;
import com.adsamcik.signalcollector.services.ActivityService;
import com.google.android.gms.auth.api.Auth;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInResult;
import com.google.firebase.crash.FirebaseCrash;

public class MainActivity extends FragmentActivity {
	public static final String TAG = "SignalsMainActivity";

	private FloatingActionButton fabOne;
	private FloatingActionButton fabTwo;

	private ITabFragment currentFragment = null;

	private final String BUNDLE_FRAGMENT = "fragment";

	@Override
	protected void onCreate(@Nullable Bundle savedInstanceState) {
		setTheme(Preferences.getTheme(this));
		super.onCreate(savedInstanceState);

		setContentView(R.layout.activity_main);
		SnackMaker snackMaker = new SnackMaker(this);
		Assist.initialize(this);

		if (Network.cloudStatus == null) {
			UploadService.UploadScheduleSource scheduleSource = UploadService.getUploadScheduled(this);
			switch (scheduleSource) {
				case NONE:
					Network.cloudStatus = DataStore.sizeOfData(this) >= Constants.MIN_USER_UPLOAD_FILE_SIZE ? CloudStatus.SYNC_AVAILABLE : CloudStatus.NO_SYNC_REQUIRED;
					break;
				case BACKGROUND:
				case USER:
					Network.cloudStatus = CloudStatus.SYNC_SCHEDULED;
					break;
			}
		}

		Signin.signin(this, true, null);

		if (Assist.isPlayServiceAvailable(this))
			ActivityService.requestAutoTracking(this);
		else
			snackMaker.showSnackbar(R.string.error_play_services_not_available);

		ColorStateList primary = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.text_primary));
		ColorStateList secondary = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.color_accent));

		fabOne = findViewById(R.id.fabOne);
		fabOne.setBackgroundTintList(secondary);
		fabOne.setImageTintList(primary);

		fabTwo = findViewById(R.id.fabTwo);
		fabTwo.setBackgroundTintList(primary);
		fabTwo.setImageTintList(secondary);

		BottomNavigationView bottomNavigationView = findViewById(R.id.bottom_navigation);

		bottomNavigationView.setOnNavigationItemSelectedListener(
				item -> changeFragment(item.getItemId()));

		int currentFragment = savedInstanceState != null && savedInstanceState.containsKey(BUNDLE_FRAGMENT) ? savedInstanceState.getInt(BUNDLE_FRAGMENT) : R.id.action_tracker;
		bottomNavigationView.setSelectedItemId(currentFragment);
	}

	private boolean changeFragment(int index) {
		switch (index) {
			case R.id.action_tracker:
				handleBottomNav(FragmentTracker.class, R.string.menu_tracker);
				break;
			case R.id.action_map:
				handleBottomNav(FragmentMap.class, R.string.menu_map);
				break;
			case R.id.action_stats:
				handleBottomNav(FragmentStats.class, R.string.menu_stats);
				break;
			case R.id.action_settings:
				handleBottomNav(FragmentSettings.class, R.string.menu_settings);
				break;
			case R.id.action_activities:
				handleBottomNav(FragmentActivities.class, R.string.menu_activities);
				break;
			default:
				Log.e(TAG, "Unknown fragment item id " + index);
				return false;
		}
		return true;
	}

	@Override
	protected void onSaveInstanceState(Bundle outState) {
		super.onSaveInstanceState(outState);

		switch (currentFragment.getClass().getSimpleName()) {
			case "FragmentTracker":
				outState.putInt(BUNDLE_FRAGMENT, R.id.action_tracker);
				break;
			case "FragmentMap":
				outState.putInt(BUNDLE_FRAGMENT, R.id.action_map);
				break;
			case "FragmentStats":
				outState.putInt(BUNDLE_FRAGMENT, R.id.action_stats);
				break;
			case "FragmentSettings":
				outState.putInt(BUNDLE_FRAGMENT, R.id.action_settings);
				break;
			case "FragmentActivities":
				outState.putInt(BUNDLE_FRAGMENT, R.id.action_activities);
				break;
		}
	}

	<T extends ITabFragment> void handleBottomNav(Class<T> tClass, @StringRes int resId) {
		if (currentFragment != null && currentFragment.getClass() == tClass)
			currentFragment.onHomeAction();
		else {
			fabOne.hide();
			fabTwo.hide();

			FragmentManager fragmentManager = getSupportFragmentManager();
			FragmentTransaction fragmentTransaction = fragmentManager.beginTransaction();
			//fragmentTransaction.setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out);

			if (currentFragment != null) {
				currentFragment.onLeave(this);
				fragmentTransaction.remove((Fragment) currentFragment);
			}

			try {
				currentFragment = tClass.newInstance();
			} catch (InstantiationException | IllegalAccessException e) {
				FirebaseCrash.report(e);
				return;
			}

			String str = getString(resId);
			fragmentTransaction.replace(R.id.container, (Fragment) currentFragment, str);

			Failure<String> state = currentFragment.onEnter(this, fabOne, fabTwo);
			fragmentTransaction.commit();

			if (state.hasFailed())
				new SnackMaker(this).showSnackbar(state.value);
		}
	}

	@Override
	public void onRequestPermissionsResult(int requestCode, @NonNull String permissions[], @NonNull int[] grantResults) {
		if (requestCode == 0)
			return;
		boolean success = true;
		for (int grantResult : grantResults) {
			if (grantResult != PackageManager.PERMISSION_GRANTED) {
				success = false;
				break;
			}
		}
		if (currentFragment != null)
			currentFragment.onPermissionResponse(requestCode, success);
	}


	@Override
	public void onActivityResult(int requestCode, int resultCode, Intent data) {
		super.onActivityResult(requestCode, resultCode, data);

		if (requestCode == Signin.RC_SIGN_IN) {
			GoogleSignInResult result = Auth.GoogleSignInApi.getSignInResultFromIntent(data);
			if (result.isSuccess()) {
				if (currentFragment instanceof FragmentSettings) {
					FragmentSettings fragmentSettings = (FragmentSettings) currentFragment;
					Signin.getUserDataAsync(this, fragmentSettings.userSignedCallback);
				}

				GoogleSignInAccount acct = result.getSignInAccount();
				assert acct != null;
				Signin.onSignedIn(acct, this);
			} else {
				new SnackMaker(this).showSnackbar(getString(R.string.error_failed_signin));
				Signin.onSignedInFailed(this);
			}
		}
	}
}
