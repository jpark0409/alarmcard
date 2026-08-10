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
class StockAlarmWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val repo: CardRepository
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        return try {
            // 알람이 켜져있거나 자동 활성화가 하나라도 있으면 실행
            val hasActive = repo.hasActiveStockAlarmOrAutoEnable()

            if (!hasActive) {
                Timber.d("No active stock alarms or auto-enabled stocks; not rescheduling.")
                return Result.success()
            }

            val toFire = repo.refreshStockAlarmsAndSelectFireable()
            toFire.forEach { NotificationHelper.notifyStockAlarm(applicationContext, it) }
            
            // 주식은 10분 주기로 체크 (또는 요구사항에 따라 조절)
            scheduleNext(applicationContext, 600L)
            Result.success()
        } catch (t: Throwable) {
            Timber.w(t, "StockAlarmWorker failed")
            scheduleNext(applicationContext, 600L)
            Result.success()
        }
    }

    companion object {
        const val UNIQUE_NAME = "stock_alarm_worker"

        fun scheduleNext(ctx: Context, delaySec: Long = 600L) {
            val req = OneTimeWorkRequestBuilder<StockAlarmWorker>()
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
