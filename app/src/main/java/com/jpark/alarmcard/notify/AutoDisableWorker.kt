package com.jpark.alarmcard.notify

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.jpark.alarmcard.data.CardRepository
import com.jpark.alarmcard.domain.model.BusCard
import com.jpark.alarmcard.domain.model.StockCard
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import timber.log.Timber
import java.util.*
import java.util.concurrent.TimeUnit

@HiltWorker
class AutoDisableWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val repository: CardRepository
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            Timber.d("Starting daily auto-disable of all alarms")
            disableAllAlarms()
            scheduleNext(applicationContext)
            Result.success()
        } catch (e: Exception) {
            Timber.e(e, "Failed to auto-disable alarms")
            Result.retry()
        }
    }

    private suspend fun disableAllAlarms() {
        repository.observeCards().collect { cards ->
            cards.forEach { card ->
                when (card) {
                    is StockCard -> {
                        if (card.alarmEnabled) {
                            repository.setStockAlarm(card.id, false, null, null)
                        }
                    }
                    is BusCard -> {
                        if (card.alarmEnabled) {
                            repository.setBusAlarm(card.id, false, card.alarmMinutesBefore)
                        }
                    }
                    else -> {}
                }
            }
            // 일회성 수행을 위해 collect 중단
            return@collect
        }
        StockAlarmWorker.cancel(applicationContext)
        BusAlarmWorker.cancel(applicationContext)
    }

    companion object {
        private const val UNIQUE_NAME = "auto_disable_worker"

        fun scheduleNext(context: Context) {
            val calendar = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 23)
                set(Calendar.MINUTE, 59)
                set(Calendar.SECOND, 30)
                set(Calendar.MILLISECOND, 0)
                if (before(Calendar.getInstance())) {
                    add(Calendar.DATE, 1)
                }
            }

            val delayMs = calendar.timeInMillis - System.currentTimeMillis()
            val request = OneTimeWorkRequestBuilder<AutoDisableWorker>()
                .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
                .addTag(UNIQUE_NAME)
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                UNIQUE_NAME,
                ExistingWorkPolicy.REPLACE,
                request
            )
        }
    }
}
