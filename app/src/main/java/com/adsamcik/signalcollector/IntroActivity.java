package com.adsamcik.signalcollector;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.content.ContextCompat;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Toast;

import com.adsamcik.signalcollector.Play.PlayController;
import com.github.paolorotolo.appintro.AppIntro2;
import com.github.paolorotolo.appintro.AppIntroFragment;

import java.util.ArrayList;
import java.util.List;

public class IntroActivity extends AppIntro2 {
	int slideNumber = 0;

	@Override
	public void init(Bundle savedInstanceState) {
		addSlide(AppIntroFragment.newInstance("Signal Collector", "Welcome [insert your name] to the exciting world of data collecting", R.drawable.ic_signals, Color.parseColor("#3F51B5")));
		if(Build.VERSION.SDK_INT > 22 && (ContextCompat.checkSelfPermission(this, android.Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_DENIED || ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_DENIED)) {
			addSlide(AppIntroFragment.newInstance("Permissions", "The app needs location to place data you collect into a map and phone permission to get identification number to distinguish your uploads from others.", R.drawable.ic_warning_24dp, Color.parseColor("#3F51B5")));
			askForPermissions(new String[]{android.Manifest.permission.ACCESS_FINE_LOCATION, android.Manifest.permission.READ_PHONE_STATE}, 2);
		}
		addSlide(AppIntroFragment.newInstance("Automatic tracking and uploading", "As long as all background services are running, the app can manage tracking and uploading by itself. You can change this in settings.", R.drawable.ic_satellite_24dp, Color.parseColor("#3F51B5")));
		addSlide(AppIntroFragment.newInstance("What is collected", "wifi, cell, GPS position, pressure, IMEI (device identification), device manufacturer and model, time, android version", R.drawable.ic_attachment_24dp, Color.parseColor("#3F51B5")));

		if(PlayController.isPlayServiceAvailable())
			addSlide(AppIntroFragment.newInstance("Google Play Games", "If you login to google play games, you will have access to achievements and personal stats.", R.drawable.ic_games_controller, Color.parseColor("#3F51B5")));

		// Hide Skip/Done button.
		Window window = getWindow();
		window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
		window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
		window.setStatusBarColor(Color.parseColor("#3f51B5"));

		setProgressButtonEnabled(true);
		setNavBarColor("#3f51B5");
		//setNextPageSwipeLock(true);
		//setSwipeLock(true);


		// Turn vibration on and set intensity.
		// NOTE: you will probably need to ask VIBRATE permission in Manifest.
		//setVibrate(true);
		//setVibrateIntensity(30);
	}

	boolean CheckAllTrackingPermissions() {
		if(Build.VERSION.SDK_INT > 22) {
			List<String> permissions = new ArrayList<>();
			if(ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED)
				permissions.add(android.Manifest.permission.ACCESS_FINE_LOCATION);

			if(ContextCompat.checkSelfPermission(this, android.Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED)
				permissions.add(android.Manifest.permission.READ_PHONE_STATE);

			//if (ContextCompat.checkSelfPermission(instance, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED)
			//    permissions.add(Manifest.permission.RECORD_AUDIO);

			if(permissions.size() == 0)
				return true;

			requestPermissions(permissions.toArray(new String[permissions.size()]), 0);
		}
		return false;
	}

	@Override
	public void onNextPressed() {

	}

	@Override
	public void onDonePressed() {
		Setting.sharedPreferences.edit().putBoolean(Setting.HAS_BEEN_LAUNCHED, true).apply();
		startActivity(new Intent(this, MainActivity.class));
	}

	@Override
	public void onSlideChanged() {
		if(slidesNumber == ++slideNumber && PlayController.isPlayServiceAvailable()) {
			PlayController.setContext(getApplicationContext());
			PlayController.setActivity(this);
			PlayController.initializeGamesClient(findViewById(android.R.id.content));
		}
	}

	@Override
	public void onRequestPermissionsResult(int requestCode, @NonNull String permissions[], @NonNull int[] grantResults) {
		for(int grantResult : grantResults) {
			if(grantResult != PackageManager.PERMISSION_GRANTED) {
				Toast.makeText(this, "Both permissions are required.", Toast.LENGTH_SHORT).show();
				CheckAllTrackingPermissions();
			}
		}
	}

}
