package com.adsamcik.signalcollector.signin;

import android.app.Activity;
import android.content.Context;
import android.content.IntentSender;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.StringRes;
import android.support.v4.app.FragmentActivity;
import android.view.View;
import android.widget.LinearLayout;

import com.adsamcik.signalcollector.R;
import com.adsamcik.signalcollector.activities.MainActivity;
import com.adsamcik.signalcollector.interfaces.IValueCallback;
import com.adsamcik.signalcollector.file.DataStore;
import com.adsamcik.signalcollector.network.Network;
import com.adsamcik.signalcollector.network.NetworkLoader;
import com.adsamcik.signalcollector.utility.Preferences;
import com.adsamcik.signalcollector.utility.SnackMaker;
import com.google.android.gms.auth.api.Auth;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.auth.api.signin.GoogleSignInResult;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.common.SignInButton;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.OptionalPendingResult;
import com.google.firebase.crash.FirebaseCrash;
import com.google.firebase.iid.FirebaseInstanceId;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.InstanceCreator;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.concurrent.TimeUnit;


public class Signin implements GoogleApiClient.OnConnectionFailedListener, GoogleApiClient.ConnectionCallbacks {
	public static final int RC_SIGN_IN = 4654;
	private static final int ERROR_REQUEST_CODE = 3543;

	private static Signin instance = null;

	private SigninStatus status = SigninStatus.NOT_SIGNED;

	private final GoogleApiClient client;
	private WeakReference<SignInButton> signInButton;
	private WeakReference<LinearLayout> signedInMenu;
	private WeakReference<FragmentActivity> activityWeakReference;

	private boolean resolvingError = false;

	private User user = null;

	private final ArrayList<IValueCallback<User>> onSignedCallbackList = new ArrayList<>(3);
	private final ArrayList<IValueCallback<User>> onDataReceivedCallbackList = new ArrayList<>(1);

	private Activity getActivity() {
		return activityWeakReference != null ? activityWeakReference.get() : null;
	}

	private void setActivity(@NonNull FragmentActivity activity) {
		activityWeakReference = new WeakReference<>(activity);
	}

	public static Signin signin(@NonNull FragmentActivity fragmentActivity, boolean silent, @Nullable IValueCallback<User> callback) {
		if (instance == null)
			instance = new Signin(fragmentActivity, callback, silent);
		else if (instance.getActivity() == null || (instance.status == SigninStatus.SIGNIN_FAILED && !silent)) {
			if (callback != null)
				instance.onSignedCallbackList.add(callback);
			instance.setActivity(fragmentActivity);
			if (!silent && ((instance.status == SigninStatus.SILENT_SIGNIN_FAILED && !instance.resolvingError) || instance.status == SigninStatus.SIGNIN_FAILED))
				fragmentActivity.startActivityForResult(Auth.GoogleSignInApi.getSignInIntent(instance.client), RC_SIGN_IN);
		} else if (callback != null && instance.user != null)
			callback.callback(instance.user);

		return instance;
	}

	private static Signin signin(@NonNull Context context, @Nullable IValueCallback<User> callback) {
		if (instance == null)
			//instance is assigned in constructor to make it sooner available
			new Signin(context, callback);
		else if (callback != null) {
			if (instance.user != null)
				callback.callback(instance.user);
			else if (instance.status == SigninStatus.SIGNIN_FAILED || instance.status == SigninStatus.SILENT_SIGNIN_FAILED)
				callback.callback(null);
			else
				instance.onSignedCallbackList.add(callback);
		}

		return instance;
	}


	private Signin(@NonNull FragmentActivity activity, @Nullable IValueCallback<User> callback, boolean silent) {
		if (callback != null)
			onSignedCallbackList.add(callback);

		instance = this;

		setActivity(activity);
		client = initializeClient(activity);
		silentSignIn(client, activity, silent);
	}

	private Signin(@NonNull Context context, @Nullable IValueCallback<User> callback) {
		if (callback != null)
			onSignedCallbackList.add(callback);

		instance = this;

		activityWeakReference = null;
		client = initializeClient(context);
		silentSignIn(client, context, true);
	}

	public static void getUserAsync(@NonNull Context context, IValueCallback<User> callback) {
		signin(context, callback);
	}

	public static void getUserDataAsync(@NonNull Context context, IValueCallback<User> callback) {
		if (instance == null)
			signin(context, null);

		if (instance.user != null && instance.user.networkInfo != null)
			callback.callback(instance.user);
		else
			instance.onDataReceivedCallbackList.add(callback);
	}

	public @Nullable
	User getUser() {
		return user;
	}

	public static @Nullable
	String getUserID(@NonNull Context context) {
		return Preferences.get(context).getString(Preferences.PREF_USER_ID, null);
	}

	public static void removeOnSignedListeners() {
		if (instance == null)
			return;
		instance.onSignedCallbackList.clear();
	}

	public static boolean isSignedIn() {
		return instance != null && instance.status == SigninStatus.SIGNED;
	}

	private GoogleApiClient initializeClient(@NonNull Context context) {
		GoogleSignInOptions gso = new GoogleSignInOptions.Builder()
				.requestIdToken(context.getString(R.string.server_client_id))
				.requestId()
				.build();
		return new GoogleApiClient.Builder(context)
				.addConnectionCallbacks(this)
				.addOnConnectionFailedListener(this)
				.addApi(Auth.GOOGLE_SIGN_IN_API, gso)
				.build();
	}

	private void silentSignIn(GoogleApiClient googleApiClient, @NonNull Context context, boolean silentOnly) {
		googleApiClient.connect();
		OptionalPendingResult<GoogleSignInResult> pendingResult = Auth.GoogleSignInApi.silentSignIn(googleApiClient);

		if (pendingResult.isDone()) {
			final GoogleSignInAccount acc = pendingResult.get().getSignInAccount();
			assert acc != null;
			onSignIn(acc, context);
		} else {
			updateStatus(SigninStatus.SIGNIN_IN_PROGRESS, context);
			pendingResult.setResultCallback((@NonNull GoogleSignInResult result) -> {
						if (result.isSuccess()) {
							final GoogleSignInAccount acc = result.getSignInAccount();
							assert acc != null;
							onSignIn(acc, context);
						} else {
							Activity activity = getActivity();
							if (silentOnly || activity == null)
								onSignInFailed(context);
							else
								activity.startActivityForResult(Auth.GoogleSignInApi.getSignInIntent(instance.client), RC_SIGN_IN);
						}
					}
					, 10, TimeUnit.SECONDS);
		}
	}

	public void setButtons(@NonNull SignInButton signInButton, @NonNull LinearLayout signedMenu, @NonNull Context context) {
		this.signInButton = new WeakReference<>(signInButton);
		this.signedInMenu = new WeakReference<>(signedMenu);
		updateStatus(status, context);
	}

	private void updateStatus(SigninStatus signinStatus, @NonNull Context context) {
		status = signinStatus;
		if (signInButton != null && signedInMenu != null) {
			SignInButton signInButton = this.signInButton.get();
			LinearLayout signedMenu = this.signedInMenu.get();
			if (signInButton != null && signedMenu != null) {
				switch (status) {
					case SIGNED:
						signInButton.setVisibility(View.GONE);
						signedMenu.setVisibility(View.VISIBLE);
						signedMenu.getChildAt(0).setVisibility(user.networkInfo == null ? View.GONE : View.VISIBLE);
						signedMenu.findViewById(R.id.sign_out_button).setOnClickListener(v -> signout(context));
						break;
					case SIGNIN_FAILED:
					case SILENT_SIGNIN_FAILED:
					case NOT_SIGNED:
						signInButton.setVisibility(View.VISIBLE);
						signedMenu.setVisibility(View.GONE);
						signInButton.setOnClickListener((v) -> {
							Activity a = getActivity();
							if (a != null) {
								a.startActivityForResult(Auth.GoogleSignInApi.getSignInIntent(client), RC_SIGN_IN);
							}
						});
						break;
					case SIGNIN_IN_PROGRESS:
						signInButton.setVisibility(View.GONE);
						signedMenu.setVisibility(View.GONE);
						break;
				}
			}
		}
	}

	public static void onSignedIn(@NonNull GoogleSignInAccount account, @NonNull Context context) {
		Signin signin = signin(context, null);
		signin.onSignIn(account, context);
		signin.showSnackbar(R.string.signed_in_message);
	}

	public static void onSignedInFailed(@NonNull Context context) {
		Signin signin = signin(context, null);
		signin.onSignInFailed(context);
		signin.showSnackbar(R.string.error_failed_signin);
	}

	private void onSignIn(@NonNull GoogleSignInAccount account, @NonNull Context context) {
		this.user = new User(account.getId(), account.getIdToken());

		assert user.token != null;
		assert user.id != null;

		Preferences.get(context).edit().putString(Preferences.PREF_USER_ID, user.id).apply();

		//todo uncomment this when server is ready
		//SharedPreferences sp = Preferences.get(context);
		//if (!sp.getBoolean(Preferences.PREF_SENT_TOKEN_TO_SERVER, false)) {
		String token = FirebaseInstanceId.getInstance().getToken();
		if (token != null)
			Network.register(context, user.token, token);
		else
			FirebaseCrash.report(new Throwable("Token is null"));
		//}

		updateStatus(SigninStatus.SIGNED, context);

		NetworkLoader.requestStringSigned(Network.URL_USER_INFO, 10, context, Preferences.PREF_USER_DATA, (state, value) -> {
			if (state.isDataAvailable()) {
				InstanceCreator<User> creator = type -> user;
				Gson gson = new GsonBuilder().registerTypeAdapter(User.class, creator).create();
				user = gson.fromJson(value, User.class);
				callOnDataCallbacks();
			}

			if (!state.isSuccess()) {
				//todo add job schedule to download data at later date
				showSnackbar(R.string.error_connection_failed);
			}
		});

		callOnSigninCallbacks();
	}

	private void onSignInFailed(@NonNull final Context context) {
		updateStatus(SigninStatus.SIGNIN_FAILED, context);
		callOnSigninCallbacks();
		callOnDataCallbacks();
	}

	private synchronized void callOnSigninCallbacks() {
		for (IValueCallback<User> c : onSignedCallbackList)
			c.callback(user);
		onSignedCallbackList.clear();
	}

	private synchronized void callOnDataCallbacks() {
		for (IValueCallback<User> c : onDataReceivedCallbackList)
			c.callback(user);
		onDataReceivedCallbackList.clear();
	}

	private void onSignedOut(@NonNull final Context context) {
		updateStatus(SigninStatus.NOT_SIGNED, context);
		showSnackbar(R.string.signed_out_message);
	}

	private void showSnackbar(@StringRes int messageResId) {
		Activity a = getActivity();
		if (a != null && a instanceof MainActivity)
			new SnackMaker(a).showSnackbar(a.getString(messageResId));
	}

	private void signout(@NonNull final Context context) {
		if (status == SigninStatus.SIGNED) {
			if (client.isConnected())
				Auth.GoogleSignInApi.signOut(client).setResultCallback(status -> onSignedOut(context));
			client.disconnect();
			user = null;
			updateStatus(SigninStatus.NOT_SIGNED, context);
			Network.clearCookieJar();
			Preferences.get(context).edit().remove(Preferences.PREF_USER_ID).remove(Preferences.PREF_USER_DATA).remove(Preferences.PREF_USER_STATS).remove(Preferences.PREF_REGISTERED_USER).apply();
			DataStore.delete(context, Preferences.PREF_USER_DATA);
			DataStore.delete(context, Preferences.PREF_USER_STATS);
			callOnDataCallbacks();
			callOnSigninCallbacks();
		}
	}

	@Override
	public void onConnectionFailed(@NonNull ConnectionResult result) {
		if (resolvingError)
			return;
		Activity activity = getActivity();

		if (activity != null) {
			if (result.hasResolution()) {
				try {
					resolvingError = true;
					result.startResolutionForResult(getActivity(), RC_SIGN_IN);
				} catch (IntentSender.SendIntentException e) {
					client.connect();
				}
			} else {
				GoogleApiAvailability.getInstance().getErrorDialog(activity, result.getErrorCode(), ERROR_REQUEST_CODE).show();
				resolvingError = true;
			}
		}
	}

	@Override
	public void onConnected(@Nullable Bundle bundle) {
		resolvingError = false;
	}

	@Override
	public void onConnectionSuspended(int i) {

	}

	public enum SigninStatus {
		NOT_SIGNED,
		SIGNIN_IN_PROGRESS,
		SIGNED,
		SILENT_SIGNIN_FAILED,
		SIGNIN_FAILED
	}
}
