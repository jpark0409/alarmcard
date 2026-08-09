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
            return
        }

        val cardId = intent.getStringExtra(EXTRA_CARD_ID) ?: return
        
        scope.launch {
            val entity = repository.getCardById(cardId) ?: return@launch
            if (!entity.autoEnabled) return@launch

            val now = Calendar.getInstance()
            val dayOfWeek = now.get(Calendar.DAY_OF_WEEK) // 1=Sun, 2=Mon...
            // Our bitmask: 1 << 1 (Mon) ... 1 << 7 (Sun)
            // Adjust dayOfWeek to match our mask
            val adjustedDay = if (dayOfWeek == Calendar.SUNDAY) 7 else dayOfWeek - 1
            val dayBit = 1 shl adjustedDay

            if ((entity.autoEnableDays and dayBit) != 0) {
                if (entity.type == CardEntity.TYPE_BUS) {
                    repository.setBusAlarm(cardId, true, entity.alarmMinutesBefore)
                } else if (entity.type == CardEntity.TYPE_STOCK) {
                    repository.setStockAlarm(cardId, true, entity.alarmPriceThreshold, entity.alarmRateThreshold)
                }
            }
            
            // Schedule for tomorrow
            scheduleNext(context, entity)
        }
    }

    private fun scheduleAll(context: Context) {
        scope.launch {
            repository.observeCards().collect { cards ->
                for (card in cards) {
                    if (card.autoEnabled) {
                        val entity = repository.getCardById(card.id)
                        if (entity != null) {
                            scheduleNext(context, entity)
                        }
                    }
                }
            }
        }
    }

    companion object {
        private const val EXTRA_CARD_ID = "card_id"

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

            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(context, AutoEnableReceiver::class.java).apply {
                putExtra(EXTRA_CARD_ID, entity.id)
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                entity.id.hashCode(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                calendar.timeInMillis,
                pendingIntent
            )
        }

        fun cancel(context: Context, cardId: String) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(context, AutoEnableReceiver::class.java)
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                cardId.hashCode(),
                intent,
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            )
            if (pendingIntent != null) {
                alarmManager.cancel(pendingIntent)
            }
        }
    }
}
