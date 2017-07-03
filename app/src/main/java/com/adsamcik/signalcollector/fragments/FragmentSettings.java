package com.adsamcik.signalcollector.fragments;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.content.res.Resources;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.NotificationCompat;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;

import com.adsamcik.signalcollector.activities.ActivityRecognitionActivity;
import com.adsamcik.signalcollector.activities.DebugFileActivity;
import com.adsamcik.signalcollector.activities.FeedbackActivity;
import com.adsamcik.signalcollector.activities.FileSharingActivity;
import com.adsamcik.signalcollector.activities.NoiseTestingActivity;
import com.adsamcik.signalcollector.interfaces.IValueCallback;
import com.adsamcik.signalcollector.network.Prices;
import com.adsamcik.signalcollector.network.User;
import com.adsamcik.signalcollector.services.TrackerService;
import com.adsamcik.signalcollector.utility.Assist;
import com.adsamcik.signalcollector.utility.Failure;
import com.adsamcik.signalcollector.utility.FirebaseAssist;
import com.adsamcik.signalcollector.utility.MapLayer;
import com.adsamcik.signalcollector.network.Network;
import com.adsamcik.signalcollector.network.NetworkLoader;
import com.adsamcik.signalcollector.utility.Preferences;
import com.adsamcik.signalcollector.utility.DataStore;
import com.adsamcik.signalcollector.interfaces.ITabFragment;
import com.adsamcik.signalcollector.R;
import com.adsamcik.signalcollector.network.Signin;
import com.adsamcik.signalcollector.utility.SnackMaker;
import com.google.android.gms.common.SignInButton;
import com.google.firebase.analytics.FirebaseAnalytics;
import com.google.firebase.crash.FirebaseCrash;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.InstanceCreator;

import java.io.File;
import java.io.IOException;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.Locale;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MultipartBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

public class FragmentSettings extends Fragment implements ITabFragment {
	private final String TAG = "SignalsSettings";
	private final int REQUEST_CODE_PERMISSIONS_MICROPHONE = 401;

	private String[] trackingString, autoupString;
	private ImageView trackingNone, trackingOnFoot, trackingAlways;
	private ImageView autoupDisabled, autoupWifi, autoupAlways;
	private TextView autoupDesc, trackDesc, signInNoConnection;
	private Switch switchNoise;

	private ImageView mTrackingSelected, mAutoupSelected;

	private SignInButton signInButton;
	private LinearLayout signedInMenu;
	private Signin signin;

	private ColorStateList mSelectedState;
	private ColorStateList mDefaultState;


	private int dummyNotificationIndex = 1972;

	private final IValueCallback<User> userSignedCallback = u -> {
		final Activity activity = getActivity();
		if (activity != null) {
			NetworkLoader.request(Network.URL_USER_PRICES, Assist.DAY_IN_MINUTES, activity, Preferences.PREF_USER_PRICES, Prices.class, (s, p) -> {
				if (s.isSuccess())
					resolveUserMenuOnLogin(u, p);
				else
					new SnackMaker(activity).showSnackbar(R.string.error_connection_failed);
			});
		}
	};

	private void updateTracking(int select) {
		Context context = getContext();
		Preferences.get(context).edit().putInt(Preferences.PREF_BACKGROUND_TRACKING, select).apply();
		ImageView selected;
		switch (select) {
			case 0:
				selected = trackingNone;
				break;
			case 1:
				selected = trackingOnFoot;
				break;
			case 2:
				selected = trackingAlways;
				break;
			default:
				return;
		}
		FirebaseAssist.updateValue(context, FirebaseAssist.autoUploadString, trackingString[select]);
		trackDesc.setText(trackingString[select]);
		if (mTrackingSelected != null)
			mTrackingSelected.setImageTintList(mDefaultState);
		selected.setImageTintList(mSelectedState);
		mTrackingSelected = selected;
	}

	private void updateAutoup(int select) {
		Context context = getContext();
		Preferences.get(context).edit().putInt(Preferences.PREF_AUTO_UPLOAD, select).apply();
		ImageView selected;
		switch (select) {
			case 0:
				selected = autoupDisabled;
				break;
			case 1:
				selected = autoupWifi;
				break;
			case 2:
				selected = autoupAlways;
				break;
			default:
				return;
		}
		FirebaseAssist.updateValue(context, FirebaseAssist.autoUploadString, autoupString[select]);

		autoupDesc.setText(autoupString[select]);
		if (mAutoupSelected != null)
			mAutoupSelected.setImageTintList(mDefaultState);
		selected.setImageTintList(mSelectedState);
		mAutoupSelected = selected;
	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		final View rootView = inflater.inflate(R.layout.fragment_settings, container, false);
		final Context context = getContext();
		final Resources resources = getResources();
		final SharedPreferences sharedPreferences = Preferences.get(getContext());
		final TextView versionView = rootView.findViewById(R.id.versionNum);
		try {
			versionView.setText(context.getPackageManager().getPackageInfo(context.getPackageName(), 0).versionName);
		} catch (Exception e) {
			Log.d(TAG, "Failed to set version");
		}

		ColorStateList[] csl = Assist.getSelectionStateLists(resources, context.getTheme());
		mSelectedState = csl[1];
		mDefaultState = csl[0];

		trackingString = resources.getStringArray(R.array.background_tracking_options);
		autoupString = resources.getStringArray(R.array.automatic_upload_options);

		autoupDesc = rootView.findViewById(R.id.autoupload_description);
		trackDesc = rootView.findViewById(R.id.tracking_description);

		trackingNone = rootView.findViewById(R.id.tracking_none);
		trackingNone.setOnClickListener(v -> updateTracking(0));
		trackingOnFoot = rootView.findViewById(R.id.tracking_onfoot);
		trackingOnFoot.setOnClickListener(v -> updateTracking(1));
		trackingAlways = rootView.findViewById(R.id.tracking_always);
		trackingAlways.setOnClickListener(v -> updateTracking(2));

		autoupDisabled = rootView.findViewById(R.id.autoupload_disabled);
		autoupDisabled.setOnClickListener(v -> updateAutoup(0));
		autoupWifi = rootView.findViewById(R.id.autoupload_wifi);
		autoupWifi.setOnClickListener(v -> updateAutoup(1));
		autoupAlways = rootView.findViewById(R.id.autoupload_always);
		autoupAlways.setOnClickListener(v -> updateAutoup(2));

		updateTracking(sharedPreferences.getInt(Preferences.PREF_BACKGROUND_TRACKING, 1));
		updateAutoup(sharedPreferences.getInt(Preferences.PREF_AUTO_UPLOAD, 1));

		signInButton = rootView.findViewById(R.id.sign_in_button);
		signedInMenu = rootView.findViewById(R.id.signed_in_menu);
		signInNoConnection = rootView.findViewById(R.id.sign_in_message);

		rootView.findViewById(R.id.other_clear_data).setOnClickListener(v -> {
			AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(context, R.style.AlertDialog);
			alertDialogBuilder
					.setPositiveButton(getResources().getText(R.string.alert_clear_confirm), (dialog, which) -> DataStore.clearAllData())
					.setNegativeButton(getResources().getText(R.string.alert_clear_cancel), (dialog, which) -> {
					})
					.setMessage(getResources().getText(R.string.alert_clear_text));

			alertDialogBuilder.create().show();
		});

		Button mapOverlayButton = rootView.findViewById(R.id.setting_map_overlay_button);
		mapOverlayButton.setEnabled(false);

		NetworkLoader.request(Network.URL_MAPS_AVAILABLE, Assist.DAY_IN_MINUTES, context, Preferences.PREF_AVAILABLE_MAPS, MapLayer[].class, (state, layerArray) -> {
			Activity activity = getActivity();
			if (activity != null) {
				if (layerArray != null && layerArray.length > 0) {
					SharedPreferences sp = Preferences.get(context);
					String defaultOverlay = sp.getString(Preferences.PREF_DEFAULT_MAP_OVERLAY, layerArray[0].name);
					int index = MapLayer.indexOf(layerArray, defaultOverlay);
					final int selectIndex = index == -1 ? 0 : index;
					if (index == -1)
						sp.edit().putString(Preferences.PREF_DEFAULT_MAP_OVERLAY, layerArray[0].name).apply();

					CharSequence[] items = new CharSequence[layerArray.length];
					for (int i = 0; i < layerArray.length; i++)
						items[i] = layerArray[i].name;
					activity.runOnUiThread(() -> {
						final ArrayAdapter<String> adapter = new ArrayAdapter<>(context, R.layout.spinner_item, MapLayer.toStringArray(layerArray));
						adapter.setDropDownViewResource(R.layout.spinner_item);
						mapOverlayButton.setEnabled(true);
						mapOverlayButton.setText(items[selectIndex]);
						mapOverlayButton.setOnClickListener(v -> {
							String ov = sp.getString(Preferences.PREF_DEFAULT_MAP_OVERLAY, layerArray[0].name);
							int in = MapLayer.indexOf(layerArray, ov);
							int selectIn = in == -1 ? 0 : in;

							AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(context, R.style.AlertDialog);
							alertDialogBuilder
									.setTitle(getString(R.string.settings_default_map_overlay))
									.setSingleChoiceItems(items, selectIn, (dialog, which) -> {
										Preferences.get(context).edit().putString(Preferences.PREF_DEFAULT_MAP_OVERLAY, adapter.getItem(which)).apply();
										mapOverlayButton.setText(items[which]);
										dialog.dismiss();
									})
									.setNegativeButton(R.string.cancel, (dialog, which) -> {
									});

							alertDialogBuilder.create().show();
						});
					});
				} else {
					activity.runOnUiThread(() -> mapOverlayButton.setEnabled(false));
				}
			}
		});

		setSwitchChangeListener(context, Preferences.PREF_TRACKING_WIFI_ENABLED, rootView.findViewById(R.id.switchTrackWifi), true, null);
		setSwitchChangeListener(context, Preferences.PREF_TRACKING_CELL_ENABLED, rootView.findViewById(R.id.switchTrackCell), true, null);

		switchNoise = rootView.findViewById(R.id.switchTrackNoise);
		switchNoise.setChecked(Preferences.get(context).getBoolean(Preferences.PREF_TRACKING_NOISE_ENABLED, false));
		switchNoise.setOnCheckedChangeListener((CompoundButton compoundButton, boolean b) -> {
			if (b && Build.VERSION.SDK_INT > 22 && ContextCompat.checkSelfPermission(context, android.Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED)
				getActivity().requestPermissions(new String[]{android.Manifest.permission.RECORD_AUDIO}, REQUEST_CODE_PERMISSIONS_MICROPHONE);
			else
				Preferences.get(context).edit().putBoolean(Preferences.PREF_TRACKING_NOISE_ENABLED, b).apply();
		});

		setSwitchChangeListener(context, Preferences.PREF_UPLOAD_NOTIFICATIONS_ENABLED, rootView.findViewById(R.id.switchNotificationsUpload), true, (b) -> FirebaseAssist.updateValue(context, FirebaseAssist.uploadNotificationString, Boolean.toString(b)));
		setSwitchChangeListener(context, Preferences.PREF_STOP_TILL_RECHARGE, rootView.findViewById(R.id.switchDisableTrackingTillRecharge), false, (b) -> {
			if (b) {
				Bundle bundle = new Bundle();
				bundle.putString(FirebaseAssist.PARAM_SOURCE, "settings");
				FirebaseAnalytics.getInstance(context).logEvent(FirebaseAssist.STOP_TILL_RECHARGE_EVENT, bundle);
				if (TrackerService.isRunning())
					context.stopService(new Intent(context, TrackerService.class));
			}
		});

		TextView valueAutoUploadAt = rootView.findViewById(R.id.settings_autoupload_at_value);
		SeekBar seekAutoUploadAt = rootView.findViewById(R.id.settings_autoupload_at_seekbar);

		final int MIN_UPLOAD_VALUE = 1;

		seekAutoUploadAt.incrementProgressBy(1);
		seekAutoUploadAt.setMax(10 - MIN_UPLOAD_VALUE);
		seekAutoUploadAt.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
			@Override
			public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
				valueAutoUploadAt.setText(getString(R.string.settings_autoupload_at_value, progress + MIN_UPLOAD_VALUE));
				if (fromUser)
					Preferences.get(context).edit().putInt(Preferences.PREF_AUTO_UPLOAD_AT_MB, progress + MIN_UPLOAD_VALUE).apply();
			}

			@Override
			public void onStartTrackingTouch(SeekBar seekBar) {

			}

			@Override
			public void onStopTrackingTouch(SeekBar seekBar) {

			}
		});
		seekAutoUploadAt.setProgress(Preferences.get(context).getInt(Preferences.PREF_AUTO_UPLOAD_AT_MB, Preferences.DEFAULT_AUTO_UPLOAD_AT_MB) - MIN_UPLOAD_VALUE);

		if (Assist.hasNetwork()) {
			signin = Signin.signin(getActivity(), userSignedCallback);
			signin.setButtons(signInButton, signedInMenu, context);
		} else
			signInNoConnection.setVisibility(View.VISIBLE);

		rootView.findViewById(R.id.export_share_button).setOnClickListener(v -> startActivity(new Intent(getActivity(), FileSharingActivity.class)));

		rootView.findViewById(R.id.other_feedback).setOnClickListener(v -> {
			if (Signin.isSignedIn())
				startActivity(new Intent(getActivity(), FeedbackActivity.class));
			else
				new SnackMaker(getActivity()).showSnackbar(R.string.feedback_error_not_signed_in);
		});

		//Dev stuff

		View devView = rootView.findViewById(R.id.dev_corner_layout);
		versionView.setOnLongClickListener(view -> {
			boolean setVisible = devView.getVisibility() == View.GONE;
			devView.setVisibility(setVisible ? View.VISIBLE : View.GONE);
			sharedPreferences.edit().putBoolean(Preferences.PREF_SHOW_DEV_SETTINGS, setVisible).apply();
			new SnackMaker(getActivity()).showSnackbar(getString(setVisible ? R.string.dev_join : R.string.dev_leave));
			return true;
		});

		boolean isDevEnabled = sharedPreferences.getBoolean(Preferences.PREF_SHOW_DEV_SETTINGS, false);
		devView.setVisibility(isDevEnabled ? View.VISIBLE : View.GONE);

		rootView.findViewById(R.id.dev_button_cache_clear).setOnClickListener((v) -> {
			AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(context, R.style.AlertDialog);
			alertDialogBuilder
					.setPositiveButton(getResources().getText(R.string.alert_confirm_generic_confirm), (dialog, which) -> {
						SnackMaker snackMaker = new SnackMaker(getActivity());
						File[] files = getContext().getFilesDir().listFiles();
						for (File file : files) {
							String fileName = file.getName();
							if (!fileName.startsWith(DataStore.DATA_FILE)) {
								int deleteRetry = 0;
								while (DataStore.recursiveDelete(file) && deleteRetry < 10) {
									try {
										Thread.sleep(50);
									} catch (InterruptedException e) {
										FirebaseCrash.report(e);
									}
									deleteRetry++;
								}

								if (deleteRetry >= 10)
									snackMaker.showSnackbar("Failed to delete " + fileName, Snackbar.LENGTH_SHORT);
								else
									snackMaker.showSnackbar("Deleted " + fileName, Snackbar.LENGTH_SHORT);
							}
						}
					})
					.setNegativeButton(getResources().getText(R.string.alert_confirm_generic_cancel), (dialog, which) -> {
					})
					.setMessage(getResources().getText(R.string.alert_confirm_generic));

			alertDialogBuilder.create().show();
		});

		rootView.findViewById(R.id.dev_button_browse_files).setOnClickListener(v -> {
			File[] files = getContext().getFilesDir().listFiles();
			ArrayList<String> temp = new ArrayList<>();
			for (File file : files) {
				String name = file.getName();
				if(!name.startsWith("DATA") && !name.startsWith("firebase") && !name.startsWith("com.") && !name.startsWith("event_store") && !name.startsWith("_m_t") && !name.equals("ZoomTables.data"))
				temp.add(name);
			}

			Collections.sort(temp, String::compareTo);

			AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(context, R.style.AlertDialog);
			String[] fileNames = new String[temp.size()];
			alertDialogBuilder
					.setTitle(getString(R.string.dev_browse_files))
					.setItems(temp.toArray(fileNames), (dialog, which) -> {
						Intent intent = new Intent(getActivity(), DebugFileActivity.class);
						intent.putExtra("fileName", fileNames[which]);
						startActivity(intent);
					})
					.setNegativeButton(R.string.cancel, (dialog, which) -> {
					});

			alertDialogBuilder.create().show();
		});

		rootView.findViewById(R.id.dev_button_noise_tracking).setOnClickListener(v -> startActivity(new Intent(getActivity(), NoiseTestingActivity.class)));

		rootView.findViewById(R.id.dev_button_notification_dummy).setOnClickListener(v -> {
			String helloWorld = getString(R.string.dev_notification_dummy);
			int color = getResources().getColor(R.color.colorPrimary);
			NotificationCompat.Builder notiBuilder = new NotificationCompat.Builder(context, getString(R.string.channel_upload_id))
					.setSmallIcon(R.drawable.ic_signals)
					.setTicker(helloWorld)
					.setColor(color)
					.setLights(color, 2000, 5000)
					.setContentTitle(helloWorld)
					.setContentText(helloWorld)
					.setWhen(System.currentTimeMillis());
			NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
			notificationManager.notify(dummyNotificationIndex++, notiBuilder.build());
		});

		rootView.findViewById(R.id.dev_button_activity_recognition).setOnClickListener(v -> startActivity(new Intent(getActivity(), ActivityRecognitionActivity.class)));

		return rootView;
	}

	private void resolveUserMenuOnLogin(@NonNull final User u, @NonNull final Prices prices) {
		Activity activity = getActivity();
		if (activity != null) {
			activity.runOnUiThread(() -> {
				DateFormat dateFormat = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.MEDIUM, Locale.getDefault());
				TextView wPointsTextView = ((TextView) signedInMenu.getChildAt(0));
				wPointsTextView.setText(String.format(activity.getString(R.string.user_have_wireless_points), Assist.formatNumber(u.wirelessPoints)));

				LinearLayout mapAccessLayout = (LinearLayout) signedInMenu.getChildAt(1);
				Switch mapAccessSwitch = (Switch) mapAccessLayout.getChildAt(0);
				TextView mapAccessTimeTextView = ((TextView) mapAccessLayout.getChildAt(1));

				mapAccessSwitch.setText(activity.getString(R.string.user_renew_map));
				mapAccessSwitch.setChecked(u.networkPreferences.renewMap);
				mapAccessSwitch.setOnCheckedChangeListener((CompoundButton compoundButton, boolean b) -> {
					compoundButton.setEnabled(false);
					MultipartBody body = new MultipartBody.Builder().setType(MultipartBody.FORM).addFormDataPart("value", Boolean.toString(b)).build();
					Network.client(u.token, activity).newCall(Network.requestPOST(Network.URL_USER_UPDATE_MAP_PREFERENCE, body)).enqueue(new Callback() {
						@Override
						public void onFailure(Call call, IOException e) {
							activity.runOnUiThread(() -> {
								compoundButton.setEnabled(true);
								compoundButton.setChecked(!b);
							});
						}

						@Override
						public void onResponse(Call call, Response response) throws IOException {
							if (response.isSuccessful()) {
								u.networkPreferences.renewMap = b;
								if (b) {
									ResponseBody body = response.body();
									if (body != null) {
										long temp = u.networkInfo.mapAccessUntil;
										u.networkInfo.mapAccessUntil = Long.parseLong(body.string());
										if (temp != u.networkInfo.mapAccessUntil) {
											u.wirelessPoints -= prices.PRICE_30DAY_MAP;
											activity.runOnUiThread(() -> {
												wPointsTextView.setText(activity.getString(R.string.user_have_wireless_points, Assist.formatNumber(u.wirelessPoints)));
												mapAccessTimeTextView.setText(String.format(activity.getString(R.string.user_access_date), dateFormat.format(new Date(u.networkInfo.mapAccessUntil))));
												mapAccessTimeTextView.setVisibility(View.VISIBLE);
											});
										}

									} else
										FirebaseCrash.report(new Throwable("Body is null"));
								}
								DataStore.saveString(Preferences.PREF_USER_DATA, new Gson().toJson(u));
							} else {
								activity.runOnUiThread(() -> compoundButton.setChecked(!b));
								new SnackMaker(activity).showSnackbar(R.string.user_not_enough_wp);
							}
							activity.runOnUiThread(() -> compoundButton.setEnabled(true));
							response.close();
						}
					});
				});

				if (u.networkInfo.mapAccessUntil > System.currentTimeMillis())
					mapAccessTimeTextView.setText(String.format(activity.getString(R.string.user_access_date), dateFormat.format(new Date(u.networkInfo.mapAccessUntil))));
				else
					mapAccessTimeTextView.setVisibility(View.GONE);
				((TextView) mapAccessLayout.getChildAt(2)).setText(String.format(activity.getString(R.string.user_cost_per_month), Assist.formatNumber(prices.PRICE_30DAY_MAP)));

				LinearLayout userMapAccessLayout = (LinearLayout) signedInMenu.getChildAt(2);
				Switch userMapAccessSwitch = (Switch) userMapAccessLayout.getChildAt(0);
				TextView personalMapAccessTimeTextView = ((TextView) userMapAccessLayout.getChildAt(1));

				userMapAccessSwitch.setText(activity.getString(R.string.user_renew_map));
				userMapAccessSwitch.setChecked(u.networkPreferences.renewPersonalMap);
				userMapAccessSwitch.setOnCheckedChangeListener((CompoundButton compoundButton, boolean b) -> {
					compoundButton.setEnabled(false);
					MultipartBody body = new MultipartBody.Builder().setType(MultipartBody.FORM).addFormDataPart("value", Boolean.toString(b)).build();
					Network.client(u.token, activity).newCall(Network.requestPOST(Network.URL_USER_UPDATE_PERSONAL_MAP_PREFERENCE, body)).enqueue(new Callback() {
						@Override
						public void onFailure(Call call, IOException e) {
							activity.runOnUiThread(() -> {
								compoundButton.setEnabled(true);
								compoundButton.setChecked(!b);
							});
						}

						@Override
						public void onResponse(Call call, Response response) throws IOException {
							if (response.isSuccessful()) {
								u.networkPreferences.renewPersonalMap = b;
								if (b) {
									ResponseBody body = response.body();
									if (body != null) {
										long temp = u.networkInfo.personalMapAccessUntil;
										u.networkInfo.personalMapAccessUntil = Long.parseLong(body.string());
										if (temp != u.networkInfo.personalMapAccessUntil) {
											u.wirelessPoints -= prices.PRICE_30DAY_PERSONAL_MAP;
											activity.runOnUiThread(() -> {
												wPointsTextView.setText(activity.getString(R.string.user_have_wireless_points, Assist.formatNumber(u.wirelessPoints)));
												personalMapAccessTimeTextView.setText(String.format(activity.getString(R.string.user_access_date), dateFormat.format(new Date(u.networkInfo.personalMapAccessUntil))));
												personalMapAccessTimeTextView.setVisibility(View.VISIBLE);
											});
										}

									} else
										FirebaseCrash.report(new Throwable("Body is null"));
								}
								DataStore.saveString(Preferences.PREF_USER_DATA, new Gson().toJson(u));
							} else {
								activity.runOnUiThread(() -> compoundButton.setChecked(!b));
								new SnackMaker(activity).showSnackbar(R.string.user_not_enough_wp);
							}
							activity.runOnUiThread(() -> compoundButton.setEnabled(true));
							response.close();
						}
					});
				});

				if (u.networkInfo.personalMapAccessUntil > System.currentTimeMillis())
					personalMapAccessTimeTextView.setText(String.format(activity.getString(R.string.user_access_date), dateFormat.format(new Date())));
				else
					personalMapAccessTimeTextView.setVisibility(View.GONE);
				((TextView) userMapAccessLayout.getChildAt(2)).setText(String.format(activity.getString(R.string.user_cost_per_month), Assist.formatNumber(prices.PRICE_30DAY_PERSONAL_MAP)));
			});
		}
	}

	private void setSwitchChangeListener(@NonNull final Context context, @NonNull final String name, Switch s, final boolean defaultState, @Nullable final IValueCallback<Boolean> callback) {
		s.setChecked(Preferences.get(context).getBoolean(name, defaultState));
		s.setOnCheckedChangeListener((CompoundButton compoundButton, boolean b) -> {
			Preferences.get(context).edit().putBoolean(name, b).apply();
			if (callback != null)
				callback.callback(b);
		});
	}

	@Override
	public Failure<String> onEnter(@NonNull FragmentActivity activity, @NonNull FloatingActionButton fabOne, @NonNull FloatingActionButton fabTwo) {
		return new Failure<>();
	}

	@Override
	public void onLeave(@NonNull FragmentActivity activity) {
		if (signin != null) {
			signin = null;
		}
	}

	@Override
	public void onPermissionResponse(int requestCode, boolean success) {
		switch (requestCode) {
			case REQUEST_CODE_PERMISSIONS_MICROPHONE:
				if (success)
					Preferences.get(getContext()).edit().putBoolean(Preferences.PREF_TRACKING_NOISE_ENABLED, true).apply();
				else
					switchNoise.setChecked(false);
				break;
			default:
				throw new UnsupportedOperationException("Permissions with requestPOST code " + requestCode + " has no defined behavior");
		}
	}

	@Override
	public void onHomeAction() {
		View v = getView();
		if (v != null) {
			Assist.verticalSmoothScrollTo(v.findViewById(R.id.settings_scrollbar), 0, 500);
		}
	}

}
