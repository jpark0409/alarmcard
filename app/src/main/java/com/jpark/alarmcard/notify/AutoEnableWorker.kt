package com.jpark.alarmcard.notify

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.jpark.alarmcard.data.CardRepository
import com.jpark.alarmcard.data.local.CardEntity
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.*
import java.util.concurrent.TimeUnit

@HiltWorker
class AutoEnableWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val repository: CardRepository
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val cardId = inputData.getString(EXTRA_CARD_ID) ?: return Result.failure()

        return try {
            handleAutoEnable(cardId)
            Result.success()
        } catch (e: Exception) {
            Result.failure()
        }
    }

    private suspend fun handleAutoEnable(cardId: String) {
        val entity = repository.getCardById(cardId) ?: return
        if (!entity.autoEnabled) return

        val now = Calendar.getInstance()
        val dayOfWeek = now.get(Calendar.DAY_OF_WEEK)
        // Calendar.SUNDAY = 1, MONDAY = 2, ..., SATURDAY = 7
        // UI에서 월=1, 화=2, ... 일=64 또는 유사한 0-indexed 비트를 사용한다고 가정할 때
        // 가장 안정적인 방식인 Calendar 상의 상수를 활용한 비트 플래그 매핑으로 수정
        // 여기서는 월(2)->0, 화(3)->1, ..., 토(7)->5, 일(1)->6 순서의 비트를 사용하도록 조정 (0-indexed 0~6)
        val bitShift = if (dayOfWeek == Calendar.SUNDAY) 6 else dayOfWeek - 2
        val dayBit = 1 shl bitShift

        if ((entity.autoEnableDays and dayBit) != 0) {
            if (entity.type == CardEntity.TYPE_BUS) {
                repository.setBusAlarm(cardId, true, entity.alarmMinutesBefore)
                BusAlarmWorker.scheduleNext(applicationContext, 5L)
            } else if (entity.type == CardEntity.TYPE_STOCK) {
                repository.setStockAlarm(cardId, true, entity.alarmPriceThreshold, entity.alarmRateThreshold)
                StockAlarmWorker.scheduleNext(applicationContext, 5L)
            }
        }

        // 다음 스케줄 예약
        scheduleNext(applicationContext, entity)
    }

    companion object {
        private const val EXTRA_CARD_ID = "card_id"
        private const val WORK_TAG_PREFIX = "auto_enable_"

        fun scheduleNext(context: Context, entity: CardEntity) {
            val time = entity.autoEnableTime ?: return
            val parts = time.split(":")
            if (parts.size != 2) return

            val hour = parts[0].toInt()
            val minute = parts[1].toInt()

            val calendar = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, hour)
                set(Calendar.MINUTE, minute)
                set(Calendar.SECOND, 0)
                if (before(Calendar.getInstance())) {
                    add(Calendar.DATE, 1)
                }
            }

            val delayMs = calendar.timeInMillis - System.currentTimeMillis()
            
            val data = workDataOf(EXTRA_CARD_ID to entity.id)
            val request = OneTimeWorkRequestBuilder<AutoEnableWorker>()
                .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
                .addTag(WORK_TAG_PREFIX + entity.id)
                .setInputData(data)
                .setConstraints(Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
                    .build())
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                WORK_TAG_PREFIX + entity.id,
                ExistingWorkPolicy.REPLACE,
                request
            )
        }

        fun cancel(context: Context, cardId: String) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_TAG_PREFIX + cardId)
        }
    }
}
