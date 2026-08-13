package com.jpark.alarmcard.notify

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.jpark.alarmcard.data.CardRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import timber.log.Timber
import java.util.concurrent.TimeUnit

@HiltWorker
class SubwayAlarmWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val repo: CardRepository
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        return try {
            val hasActive = repo.hasActiveSubwayAlarm()
            if (!hasActive) {
                Timber.d("No active subway alarms; not rescheduling.")
                return Result.success()
            }
            val toFire = repo.refreshSubwayAlarmsAndSelectFireable()
            toFire.forEach { NotificationHelper.notifySubwayArrival(applicationContext, it) }
            scheduleNext(applicationContext)
            Result.success()
        } catch (t: Throwable) {
            Timber.w(t, "SubwayAlarmWorker failed")
            scheduleNext(applicationContext)
            Result.success()
        }
    }

    companion object {
        const val UNIQUE_NAME = "subway_alarm_worker"

        fun scheduleNext(ctx: Context, delaySec: Long = 60L) {
            val req = OneTimeWorkRequestBuilder<SubwayAlarmWorker>()
                .setInitialDelay(delaySec, TimeUnit.SECONDS)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .build()
            WorkManager.getInstance(ctx)
                .enqueueUniqueWork(UNIQUE_NAME, ExistingWorkPolicy.REPLACE, req)
        }

        fun cancel(ctx: Context) {
            WorkManager.getInstance(ctx).cancelUniqueWork(UNIQUE_NAME)
        }
    }
}
