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

/**
 * 1분 주기로 스스로 재예약하며 활성 버스카드를 새로고침 → 알림 발송을 판정.
 * WorkManager의 최소 주기(15분) 제약을 피하기 위해 OneTimeWork + 재예약 방식 사용.
 */
@HiltWorker
class BusAlarmWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val repo: CardRepository
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        return try {
            val hasActive = repo.hasActiveBusAlarm()
            if (!hasActive) {
                Timber.d("No active bus alarms; not rescheduling.")
                return Result.success()
            }
            val toFire = repo.refreshBusAlarmsAndSelectFireable()
            toFire.forEach { NotificationHelper.notifyBusArrival(applicationContext, it) }
            // 다음 1분 뒤 재예약
            scheduleNext(applicationContext)
            Result.success()
        } catch (t: Throwable) {
            Timber.w(t, "BusAlarmWorker failed")
            // 실패해도 다음 사이클은 이어가도록 재예약
            scheduleNext(applicationContext)
            Result.success()
        }
    }

    companion object {
        const val UNIQUE_NAME = "bus_alarm_worker"

        fun scheduleNext(ctx: Context, delaySec: Long = 60L) {
            val req = OneTimeWorkRequestBuilder<BusAlarmWorker>()
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
