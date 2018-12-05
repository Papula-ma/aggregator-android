package com.tughi.aggregator.services

import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.content.ComponentName
import android.content.Context
import android.text.format.DateUtils
import android.util.Log
import com.tughi.aggregator.App
import com.tughi.aggregator.AppDatabase
import com.tughi.aggregator.UpdateSettings
import com.tughi.aggregator.data.AdaptiveUpdateMode
import com.tughi.aggregator.data.DefaultUpdateMode
import com.tughi.aggregator.data.DisabledUpdateMode
import com.tughi.aggregator.data.Every15MinutesUpdateMode
import com.tughi.aggregator.data.Every2HoursUpdateMode
import com.tughi.aggregator.data.Every30MinutesUpdateMode
import com.tughi.aggregator.data.Every3HoursUpdateMode
import com.tughi.aggregator.data.Every45MinutesUpdateMode
import com.tughi.aggregator.data.Every4HoursUpdateMode
import com.tughi.aggregator.data.Every6HoursUpdateMode
import com.tughi.aggregator.data.Every8HoursUpdateMode
import com.tughi.aggregator.data.EveryHourUpdateMode
import com.tughi.aggregator.data.OnAppLaunchUpdateMode
import com.tughi.aggregator.data.SchedulerFeed
import com.tughi.aggregator.data.UpdateMode
import com.tughi.aggregator.utilities.JOB_SERVICE_FEEDS_UPDATER
import java.util.*
import kotlin.math.max
import kotlin.math.min

object AutoUpdateScheduler {

    const val NEXT_UPDATE_TIME__DISABLED = 0L
    const val NEXT_UPDATE_TIME__ON_APP_LAUNCH = -1L

    fun scheduleFeed(feedId: Long) {
        val database = AppDatabase.instance
        val feedDao = database.feedDao()

        feedDao.querySchedulerFeed(feedId)?.also {
            scheduleFeeds(it)
        }
    }

    fun scheduleFeedsWithDefaultUpdateMode() {
        val database = AppDatabase.instance
        val feedDao = database.feedDao()

        scheduleFeeds(*feedDao.querySchedulerFeeds(DefaultUpdateMode))
    }

    private fun scheduleFeeds(vararg feeds: SchedulerFeed) {
        val database = AppDatabase.instance
        val feedDao = database.feedDao()

        database.beginTransaction()
        try {
            feeds.forEach { feed ->
                feedDao.updateFeed(feed.id, calculateNextUpdateTime(feed.id, feed.updateMode, feed.lastUpdateTime))
            }
            database.setTransactionSuccessful()
        } finally {
            database.endTransaction()
        }

        schedule()
    }

    fun schedule() {
        val nextUpdateTime = AppDatabase.instance.feedDao().queryNextUpdateTime() ?: return
        val nextUpdateDelay = nextUpdateTime - System.currentTimeMillis()

        val context: Context = App.instance
        val jobScheduler = context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as JobScheduler

        val jobInfo = JobInfo.Builder(JOB_SERVICE_FEEDS_UPDATER, ComponentName(context, AutoUpdateService::class.java))
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setMinimumLatency(max(0, nextUpdateDelay))
                .setPersisted(true)
                .build()

        Log.d(javaClass.name, "Schedule next auto update: ${Date(nextUpdateTime)}")

        jobScheduler.schedule(jobInfo)
    }

    fun cancel() {
        val jobScheduler = App.instance.getSystemService(Context.JOB_SCHEDULER_SERVICE) as JobScheduler
        jobScheduler.cancel(JOB_SERVICE_FEEDS_UPDATER)
    }

    fun calculateNextUpdateTime(feedId: Long, updateMode: UpdateMode, lastUpdateTime: Long): Long = when (updateMode) {
        AdaptiveUpdateMode -> calculateNextAdaptiveUpdateTime(feedId, lastUpdateTime)
        DefaultUpdateMode -> calculateNextUpdateTime(feedId, UpdateSettings.defaultUpdateMode, lastUpdateTime)
        DisabledUpdateMode -> NEXT_UPDATE_TIME__DISABLED
        OnAppLaunchUpdateMode -> NEXT_UPDATE_TIME__ON_APP_LAUNCH
        Every15MinutesUpdateMode -> calculateNextRepeatingUpdateTime(lastUpdateTime, 15)
        Every30MinutesUpdateMode -> calculateNextRepeatingUpdateTime(lastUpdateTime, 30)
        Every45MinutesUpdateMode -> calculateNextRepeatingUpdateTime(lastUpdateTime, 45)
        EveryHourUpdateMode -> calculateNextRepeatingUpdateTime(lastUpdateTime, 60)
        Every2HoursUpdateMode -> calculateNextRepeatingUpdateTime(lastUpdateTime, 120)
        Every3HoursUpdateMode -> calculateNextRepeatingUpdateTime(lastUpdateTime, 180)
        Every4HoursUpdateMode -> calculateNextRepeatingUpdateTime(lastUpdateTime, 240)
        Every6HoursUpdateMode -> calculateNextRepeatingUpdateTime(lastUpdateTime, 360)
        Every8HoursUpdateMode -> calculateNextRepeatingUpdateTime(lastUpdateTime, 480)
    }

    private fun calculateNextAdaptiveUpdateTime(feedId: Long, lastUpdateTime: Long): Long {
        val entryDao = AppDatabase.instance.entryDao()

        val aggregatedEntriesSinceYesterday = entryDao.countAggregatedEntries(feedId, lastUpdateTime - DateUtils.DAY_IN_MILLIS)
        val updateRate = if (aggregatedEntriesSinceYesterday > 0) {
            max(DateUtils.DAY_IN_MILLIS / aggregatedEntriesSinceYesterday, DateUtils.HOUR_IN_MILLIS / 2) / 2
        } else {
            val aggregatedEntriesSinceLastWeek = entryDao.countAggregatedEntries(feedId, lastUpdateTime - DateUtils.WEEK_IN_MILLIS)
            if (aggregatedEntriesSinceLastWeek > 0) {
                min(DateUtils.WEEK_IN_MILLIS / aggregatedEntriesSinceLastWeek, DateUtils.DAY_IN_MILLIS / 2) / 2
            } else {
                DateUtils.DAY_IN_MILLIS / 2
            }
        }
        val alignedUpdateRate = updateRate / (DateUtils.HOUR_IN_MILLIS / 4) * (DateUtils.HOUR_IN_MILLIS / 4)
        return lastUpdateTime / (DateUtils.HOUR_IN_MILLIS / 4) * (DateUtils.HOUR_IN_MILLIS / 4) + alignedUpdateRate
    }

    private fun calculateNextRepeatingUpdateTime(lastUpdateTime: Long, minutes: Int): Long {
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = lastUpdateTime
        calendar.set(Calendar.MILLISECOND, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        val midnight = calendar.timeInMillis
        val minutesInMillis = minutes * 60000
        return midnight + ((lastUpdateTime - midnight) / minutesInMillis + 1) * minutesInMillis
    }

}
