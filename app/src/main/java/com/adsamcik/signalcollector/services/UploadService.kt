package com.adsamcik.signalcollector.services

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.os.AsyncTask
import android.os.Build
import android.os.PersistableBundle
import com.adsamcik.signalcollector.R
import com.adsamcik.signalcollector.enums.CloudStatus
import com.adsamcik.signalcollector.file.Compress
import com.adsamcik.signalcollector.file.DataStore
import com.adsamcik.signalcollector.file.FileStore
import com.adsamcik.signalcollector.interfaces.INonNullValueCallback
import com.adsamcik.signalcollector.interfaces.IValueCallback
import com.adsamcik.signalcollector.jobs.scheduler
import com.adsamcik.signalcollector.network.Network
import com.adsamcik.signalcollector.signin.Signin
import com.adsamcik.signalcollector.utility.Assist
import com.adsamcik.signalcollector.utility.Constants
import com.adsamcik.signalcollector.utility.Constants.HOUR_IN_MILLISECONDS
import com.adsamcik.signalcollector.utility.Constants.MIN_COLLECTIONS_SINCE_LAST_UPLOAD
import com.adsamcik.signalcollector.utility.Failure
import com.adsamcik.signalcollector.utility.Preferences
import com.google.firebase.crash.FirebaseCrash
import com.google.firebase.perf.FirebasePerformance
import okhttp3.*
import java.io.File
import java.io.IOException
import java.lang.ref.WeakReference
import java.security.InvalidParameterException
import java.util.concurrent.locks.ReentrantLock

class UploadService : JobService() {
    private var worker: JobWorker? = null

    override fun onStartJob(jobParameters: JobParameters): Boolean {
        Preferences.getPref(this).edit().putInt(Preferences.PREF_SCHEDULED_UPLOAD, UploadScheduleSource.NONE.ordinal).apply()
        val scheduleSource = UploadScheduleSource.values()[jobParameters.extras.getInt(KEY_SOURCE)]
        if (scheduleSource == UploadScheduleSource.NONE)
            throw RuntimeException("Source cannot be null")

        if (!hasEnoughData(this, scheduleSource))
            return false

        DataStore.onUpload(this, 0)
        val context = applicationContext
        if (!Assist.isInitialized)
            Assist.initialize(context)

        isUploading = true
        val collectionsToUpload = Preferences.getPref(context).getInt(Preferences.PREF_COLLECTIONS_SINCE_LAST_UPLOAD, 0)
        worker = JobWorker(context, INonNullValueCallback { success ->
            if (success) {
                var collectionCount = Preferences.getPref(context).getInt(Preferences.PREF_COLLECTIONS_SINCE_LAST_UPLOAD, 0)
                if (collectionCount < collectionsToUpload) {
                    collectionCount = 0
                    FirebaseCrash.report(Throwable("There are less collections than thought"))
                } else
                    collectionCount -= collectionsToUpload
                DataStore.setCollections(this, collectionCount)
                DataStore.onUpload(this, 100)
            }
            isUploading = false
            jobFinished(jobParameters, !success)
        })
        worker!!.execute(jobParameters)
        return true
    }

    override fun onStopJob(jobParameters: JobParameters): Boolean {
        worker?.cancel(true)
        isUploading = false
        return true
    }

    enum class UploadScheduleSource {
        NONE,
        BACKGROUND,
        USER
    }

    private class JobWorker internal constructor(context: Context, private val callback: INonNullValueCallback<Boolean>?) : AsyncTask<JobParameters, Void, Boolean>() {
        private val context: WeakReference<Context> = WeakReference(context.applicationContext)

        private var tempZipFile: File? = null
        private var response: Response? = null
        private var call: Call? = null

        /**
         * Uploads data to server.
         *
         * @param file file to be uploaded
         */
        private fun upload(file: File?, token: String?, userID: String?): Boolean {
            if (file == null)
                throw InvalidParameterException("file is null")
            else if (token == null) {
                FirebaseCrash.report(Throwable("Token is null"))
                return false
            }

            val formBody = MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart("file", Network.generateVerificationString(userID!!, file.length()), RequestBody.create(MEDIA_TYPE_ZIP, file))
                    .build()
            try {
                call = Network.client(context.get()!!, null).newCall(Network.requestPOST(Network.URL_DATA_UPLOAD, formBody))
                response = call!!.execute()
                val code = response!!.code()
                val isSuccessful = response!!.isSuccessful
                response!!.close()
                if (isSuccessful)
                    return true

                if (code >= 500 || code == 403)
                    FirebaseCrash.report(Throwable("Upload failed " + code))
                return false
            } catch (e: IOException) {
                FirebaseCrash.report(e)
                return false
            }

        }

        private inner class StringWrapper internal constructor() {

            @get:Synchronized
            @set:Synchronized
            var string: String? = null

            init {
                string = null
            }

        }

        override fun doInBackground(vararg params: JobParameters): Boolean? {
            val source = UploadScheduleSource.values()[params[0].extras.getInt(KEY_SOURCE)]
            val context = this.context.get()!!
            val files = DataStore.getDataFiles(context, if (source == UploadScheduleSource.USER) Constants.MIN_USER_UPLOAD_FILE_SIZE else Constants.MIN_BACKGROUND_UPLOAD_FILE_SIZE)
            if (files == null) {
                FirebaseCrash.report(Throwable("No files found. This should not happen. Upload initiated by " + source.name))
                DataStore.onUpload(context, -1)
                return false
            } else {
                val uploadTrace = FirebasePerformance.getInstance().newTrace("upload")
                uploadTrace.start()
                DataStore.getCurrentDataFile(context)!!.close()
                val zipName = "up" + System.currentTimeMillis()
                try {
                    val compress = Compress(DataStore.file(context, zipName))
                    compress += files
                    tempZipFile = compress.finish()
                } catch (e: IOException) {
                    FirebaseCrash.report(e)
                    uploadTrace.incrementCounter("fail", 1)
                    uploadTrace.stop()
                    return false
                }

                val lock = ReentrantLock()
                val callbackReceived = lock.newCondition()
                val token = StringWrapper()
                val userID = StringWrapper()

                Signin.getUserAsync(context, IValueCallback { value ->
                    lock.lock()
                    if (value != null) {
                        token.string = value.token
                        userID.string = value.id
                    }
                    callbackReceived.signal()
                    lock.unlock()
                })

                lock.lock()
                try {
                    if (token.string == null)
                        callbackReceived.await()
                } catch (e: InterruptedException) {
                    FirebaseCrash.report(e)
                    uploadTrace.incrementCounter("fail", 2)
                    uploadTrace.stop()
                    return false
                } finally {
                    lock.unlock()
                }

                if (upload(tempZipFile, token.string, userID.string)) {
                    for (file in files)
                        FileStore.delete(file)
                    if (!tempZipFile!!.delete())
                        tempZipFile!!.deleteOnExit()
                } else {
                    uploadTrace.incrementCounter("fail", 3)
                    uploadTrace.stop()
                    return false
                }
                uploadTrace.stop()
            }

            DataStore.recountData(context)
            return true
        }

        override fun onPostExecute(result: Boolean) {
            super.onPostExecute(result)
            val ctx = context.get()!!
            DataStore.cleanup(ctx)

            if (!result) {
                DataStore.onUpload(ctx, -1)
            }

            if (tempZipFile != null && !FileStore.delete(tempZipFile))
                FirebaseCrash.report(IOException("Upload zip file was not deleted"))

            callback?.callback(result)
        }

        override fun onCancelled() {
            val context = this.context.get()!!
            DataStore.cleanup(context)
            DataStore.recountData(context)
            if (tempZipFile != null)
                FileStore.delete(tempZipFile)

            if (call != null)
                call!!.cancel()

            callback?.callback(call != null && call!!.isExecuted && !call!!.isCanceled)

            super.onCancelled()
        }
    }

    companion object {
        private val TAG = "SignalsUploadService"
        private val KEY_SOURCE = "source"
        private val MEDIA_TYPE_ZIP = MediaType.parse("application/zip")
        private val MIN_NO_ACTIVITY_DELAY = HOUR_IN_MILLISECONDS

        private val SCHEDULE_UPLOAD_JOB_ID = 1921109
        private val UPLOAD_JOB_ID = 2110

        var isUploading = false
            private set

        fun getUploadScheduled(context: Context): UploadScheduleSource =
                UploadScheduleSource.values()[Preferences.getPref(context).getInt(Preferences.PREF_SCHEDULED_UPLOAD, 0)]

        /**
         * Requests upload
         * Call this when you want to auto-upload
         *
         * @param context Non-null context
         * @param source  Source that started the upload
         */
        fun requestUpload(context: Context, source: UploadScheduleSource): Failure<String> {
            if (source == UploadScheduleSource.NONE)
                throw InvalidParameterException("Upload source can't be NONE.")
            else if (isUploading)
                return Failure(context.getString(R.string.error_upload_in_progress))

            val sp = Preferences.getPref(context)
            if (hasEnoughData(context, source)) {
                val autoUpload = sp.getInt(Preferences.PREF_AUTO_UPLOAD, Preferences.DEFAULT_AUTO_UPLOAD)
                if (autoUpload != 0 || source == UploadScheduleSource.USER) {
                    val jb = prepareBuilder(UPLOAD_JOB_ID, context, source)
                    addNetworkTypeRequest(context, source, jb)

                    val scheduler = scheduler(context)
                    if (scheduler.schedule(jb.build()) == JobScheduler.RESULT_FAILURE)
                        return Failure(context.getString(R.string.error_during_upload_scheduling))
                    updateUploadScheduleSource(context, source)
                    Network.cloudStatus = CloudStatus.SYNC_SCHEDULED

                    scheduler.cancel(SCHEDULE_UPLOAD_JOB_ID)

                    return Failure()
                }
                return Failure(context.getString(R.string.error_during_upload_scheduling))
            }
            return Failure(context.getString(R.string.error_not_enough_data))
        }

        /**
         * Requests scheduling of upload
         *
         * @param context context
         */
        fun requestUploadSchedule(context: Context) {
            if (hasEnoughData(context, UploadScheduleSource.BACKGROUND) && Preferences.getPref(context).getInt(Preferences.PREF_COLLECTIONS_SINCE_LAST_UPLOAD, 0) >= MIN_COLLECTIONS_SINCE_LAST_UPLOAD) {
                val scheduler = scheduler(context)
                if (!hasJobWithID(scheduler, UPLOAD_JOB_ID)) {
                    val jb = prepareBuilder(SCHEDULE_UPLOAD_JOB_ID, context, UploadScheduleSource.BACKGROUND)
                    jb.setMinimumLatency(MIN_NO_ACTIVITY_DELAY.toLong())
                    addNetworkTypeRequest(context, UploadScheduleSource.BACKGROUND, jb)
                    updateUploadScheduleSource(context, UploadScheduleSource.BACKGROUND)

                    scheduler.schedule(jb.build())
                }
            }
        }

        private fun hasJobWithID(jobScheduler: JobScheduler, id: Int): Boolean {
            return if (Build.VERSION.SDK_INT >= 24)
                jobScheduler.getPendingJob(id) != null
            else {
                jobScheduler.allPendingJobs.any { it.id == id }
            }
        }

        private fun prepareBuilder(id: Int, context: Context, source: UploadScheduleSource): JobInfo.Builder {
            val jobBuilder = JobInfo.Builder(id, ComponentName(context, UploadService::class.java))
            jobBuilder.setPersisted(true)
            val pb = PersistableBundle(1)
            pb.putInt(KEY_SOURCE, source.ordinal)
            jobBuilder.setExtras(pb)
            return jobBuilder
        }

        private fun addNetworkTypeRequest(context: Context, source: UploadScheduleSource, jobBuilder: JobInfo.Builder) {
            if (source == UploadScheduleSource.USER) {
                jobBuilder.setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
            } else {
                if (Preferences.getPref(context).getInt(Preferences.PREF_AUTO_UPLOAD, Preferences.DEFAULT_AUTO_UPLOAD) == 2) {
                    //todo improve roaming handling
                    if (Build.VERSION.SDK_INT >= 24)
                        jobBuilder.setRequiredNetworkType(JobInfo.NETWORK_TYPE_NOT_ROAMING)
                    else
                        jobBuilder.setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                } else
                    jobBuilder.setRequiredNetworkType(JobInfo.NETWORK_TYPE_UNMETERED)
            }
        }

        private fun hasEnoughData(context: Context, source: UploadScheduleSource): Boolean {
            return when (source) {
                UploadService.UploadScheduleSource.BACKGROUND -> DataStore.sizeOfData(context) >= Constants.MIN_BACKGROUND_UPLOAD_FILE_SIZE
                UploadService.UploadScheduleSource.USER -> DataStore.sizeOfData(context) >= Constants.MIN_USER_UPLOAD_FILE_SIZE
                else -> false
            }
        }

        private fun updateUploadScheduleSource(context: Context, uss: UploadScheduleSource) {
            Preferences.getPref(context).edit().putInt(Preferences.PREF_SCHEDULED_UPLOAD, uss.ordinal).apply()
        }

        fun cancelUploadSchedule(context: Context) {
            scheduler(context).cancel(SCHEDULE_UPLOAD_JOB_ID)
        }
    }
}
