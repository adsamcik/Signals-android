package com.adsamcik.signalcollector.services;

import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Build;
import android.os.PersistableBundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.adsamcik.signalcollector.R;
import com.adsamcik.signalcollector.enums.CloudStatus;
import com.adsamcik.signalcollector.interfaces.IValueCallback;
import com.adsamcik.signalcollector.utility.Assist;
import com.adsamcik.signalcollector.utility.Compress;
import com.adsamcik.signalcollector.utility.Failure;
import com.adsamcik.signalcollector.utility.Preferences;
import com.adsamcik.signalcollector.utility.DataStore;
import com.adsamcik.signalcollector.network.Network;
import com.adsamcik.signalcollector.network.Signin;
import com.google.firebase.crash.FirebaseCrash;

import java.io.File;
import java.io.IOException;
import java.security.InvalidParameterException;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import okhttp3.Call;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import okhttp3.Response;

public class UploadService extends JobService {
	private static final String TAG = "SignalsUploadService";
	private static final String KEY_SOURCE = "source";
	private static final MediaType MEDIA_TYPE_ZIP = MediaType.parse("application/zip");
	private static boolean isUploading = false;
	private JobWorker worker;

	public static boolean isUploading() {
		return isUploading;
	}

	public static UploadScheduleSource getUploadScheduled(@NonNull Context c) {
		return UploadScheduleSource.values()[Preferences.get(c).getInt(Preferences.PREF_SCHEDULED_UPLOAD, 0)];
	}

	/**
	 * Requests upload
	 * Call this when you want to auto-upload
	 *
	 * @param c      Non-null context
	 * @param source Source that started the upload
	 */
	public static Failure<String> requestUpload(@NonNull Context c, UploadScheduleSource source) {
		if (source.equals(UploadScheduleSource.NONE))
			throw new InvalidParameterException("Upload source can't be NONE.");
		else if (isUploading)
			return new Failure<>(c.getString(R.string.error_upload_in_progress));

		SharedPreferences sp = Preferences.get(c);
		int autoUpload = sp.getInt(Preferences.PREF_AUTO_UPLOAD, 1);
		if (autoUpload != 0 || source.equals(UploadScheduleSource.USER)) {
			JobScheduler scheduler = ((JobScheduler) c.getSystemService(Context.JOB_SCHEDULER_SERVICE));
			JobInfo.Builder jb = new JobInfo.Builder(Preferences.UPLOAD_JOB, new ComponentName(c, UploadService.class));
			jb.setPersisted(true);
			if (source.equals(UploadScheduleSource.USER)) {
				jb.setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY);
			} else {
				if (autoUpload == 2) {
					if (Build.VERSION.SDK_INT >= 24)
						jb.setRequiredNetworkType(JobInfo.NETWORK_TYPE_NOT_ROAMING);
					else
						jb.setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY);
				} else
					jb.setRequiredNetworkType(JobInfo.NETWORK_TYPE_UNMETERED);
			}
			PersistableBundle pb = new PersistableBundle(1);
			pb.putInt(KEY_SOURCE, source.ordinal());
			jb.setExtras(pb);
			if (scheduler.schedule(jb.build()) <= 0)
				return new Failure<>(c.getString(R.string.error_during_upload_scheduling));
			updateUploadScheduleSource(c, source);
			Network.cloudStatus = CloudStatus.SYNC_SCHEDULED;
		}
		return new Failure<>();
	}

	private static void updateUploadScheduleSource(@NonNull Context context, UploadScheduleSource uss) {
		Preferences.get(context).edit().putInt(Preferences.PREF_SCHEDULED_UPLOAD, uss.ordinal()).apply();
	}

	@Override
	public boolean onStartJob(JobParameters jobParameters) {
		Preferences.get(this).edit().putInt(Preferences.PREF_SCHEDULED_UPLOAD, UploadScheduleSource.NONE.ordinal()).apply();
		if (UploadScheduleSource.values()[jobParameters.getExtras().getInt(KEY_SOURCE)] == UploadScheduleSource.NONE)
			throw new InvalidParameterException("Upload source can't be NONE.");

		DataStore.onUpload(0);
		final Context c = getApplicationContext();
		DataStore.setContext(c);
		if (!Assist.isInitialized())
			Assist.initialize(c);

		isUploading = true;
		worker = new JobWorker(getFilesDir().getAbsolutePath(), c, success -> {
			if (success)
				DataStore.onUpload(100);
			isUploading = false;
			jobFinished(jobParameters, !success);
		});
		worker.execute(jobParameters);
		return true;
	}

	@Override
	public boolean onStopJob(JobParameters jobParameters) {
		if (worker != null)
			worker.cancel(true);
		isUploading = false;
		return true;
	}

	public enum UploadScheduleSource {
		NONE,
		BACKGROUND,
		USER
	}

	private class JobWorker extends AsyncTask<JobParameters, Void, Boolean> {
		private final String directory;
		private final Context context;

		private File tempZipFile = null;
		private Response response = null;
		private Call call = null;

		private IValueCallback<Boolean> callback;

		JobWorker(final String dir, final Context context, @Nullable IValueCallback<Boolean> callback) {
			this.directory = dir;
			this.context = context;
			this.callback = callback;
		}

		/**
		 * Uploads data to server.
		 *
		 * @param file file to be uploaded
		 */
		private boolean upload(final File file, String token, String userID) {
			if (file == null)
				throw new InvalidParameterException("file is null");
			else if (token == null) {
				FirebaseCrash.report(new Throwable("Token is null"));
				return false;
			}
			RequestBody formBody = new MultipartBody.Builder()
					.setType(MultipartBody.FORM)
					.addFormDataPart("file", Network.generateVerificationString(userID, file.length()), RequestBody.create(MEDIA_TYPE_ZIP, file))
					.build();
			try {
				call = Network.client(null, context).newCall(Network.requestPOST(Network.URL_DATA_UPLOAD, formBody));
				response = call.execute();
				int code = response.code();
				boolean isSuccessful = response.isSuccessful();
				response.close();
				if (isSuccessful) {
					if (!DataStore.retryDelete(file))
						FirebaseCrash.report(new IOException("Failed to delete file " + file.getName() + ". This should never happen."));
					return true;
				}
				FirebaseCrash.report(new Throwable("Upload failed " + code));
				return false;
			} catch (IOException e) {
				FirebaseCrash.report(e);
				return false;
			}
		}

		private class StringWrapper {
			StringWrapper() {
				string = null;
			}

			public synchronized String getString() {
				return string;
			}

			public synchronized void setString(String string) {
				this.string = string;
			}

			private String string;

		}

		@Override
		protected Boolean doInBackground(JobParameters... params) {
			UploadScheduleSource source = UploadScheduleSource.values()[params[0].getExtras().getInt(KEY_SOURCE)];
			String[] files = DataStore.getDataFileNames(source.equals(UploadScheduleSource.USER));
			if (files == null) {
				FirebaseCrash.report(new Throwable("No files found. This should not happen. Upload initiated by " + source.name()));
				DataStore.onUpload(-1);
				return false;
			} else {
				if (source.equals(UploadScheduleSource.USER))
					DataStore.closeUploadFile(files[files.length - 1]);
				String zipName = "up" + System.currentTimeMillis();
				tempZipFile = Compress.zip(directory, files, zipName);
				if (tempZipFile == null)
					return false;

				final Lock lock = new ReentrantLock();
				final Condition callbackReceived = lock.newCondition();
				final StringWrapper token = new StringWrapper();
				final StringWrapper userID = new StringWrapper();

				Signin.getUserAsync(context, value -> {
					lock.lock();
					token.setString(value.token);
					userID.setString(Signin.getUserID(context));
					callbackReceived.signal();
					lock.unlock();
				});

				lock.lock();
				try {
					if (token.getString() == null)
						callbackReceived.await();
				} catch (InterruptedException e) {
					FirebaseCrash.report(e);
					return false;
				} finally {
					lock.unlock();
				}

				if (upload(tempZipFile, token.getString(), userID.getString())) {
					for (String file : files)
						DataStore.deleteFile(file);
					tempZipFile = null;
				} else {
					return false;
				}
			}

			DataStore.recountDataSize();
			return true;
		}

		@Override
		protected void onPostExecute(Boolean aBoolean) {
			super.onPostExecute(aBoolean);
			DataStore.cleanup();

			if (!aBoolean) {
				DataStore.onUpload(-1);
			}

			if (tempZipFile != null && !DataStore.retryDelete(tempZipFile))
				FirebaseCrash.report(new IOException("Upload zip file was not deleted"));

			if (callback != null)
				callback.callback(aBoolean);
		}

		@Override
		protected void onCancelled() {
			DataStore.cleanup();
			DataStore.recountDataSize();
			if (tempZipFile != null)
				DataStore.retryDelete(tempZipFile);

			if (call != null)
				call.cancel();

			super.onCancelled();
		}
	}
}
