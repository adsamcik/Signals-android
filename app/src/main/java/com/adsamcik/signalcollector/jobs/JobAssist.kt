package com.adsamcik.signalcollector.jobs

import android.app.job.JobScheduler
import android.content.Context

/**
 * Shortcut to get typed JobScheduler service
 * @return JobScheduler system service
 */
fun scheduler(context: Context): JobScheduler = context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as JobScheduler