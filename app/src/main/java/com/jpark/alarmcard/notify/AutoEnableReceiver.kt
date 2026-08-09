package com.jpark.alarmcard.notify

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.jpark.alarmcard.data.CardRepository
import com.jpark.alarmcard.data.local.CardEntity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject

@AndroidEntryPoint
class AutoEnableReceiver : BroadcastReceiver() {

    @Inject
    lateinit var repository: CardRepository

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            scheduleAll(context)
        }
    }

    private fun scheduleAll(context: Context) {
        scope.launch {
            // Flow.first() 등을 사용하여 현재 시점의 카드 목록만 가져오도록 수정 가능
            repository.observeCards().collect { cards ->
                for (card in cards) {
                    if (card.autoEnabled) {
                        val entity = repository.getCardById(card.id)
                        if (entity != null) {
                            AutoEnableWorker.scheduleNext(context, entity)
                        }
                    }
                }
            }
        }
    }

    companion object {
        fun scheduleNext(context: Context, entity: CardEntity) {
            AutoEnableWorker.scheduleNext(context, entity)
        }

        fun cancel(context: Context, cardId: String) {
            AutoEnableWorker.cancel(context, cardId)
        }
    }
}
