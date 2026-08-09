package com.jpark.alarmcard.notify

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.jpark.alarmcard.data.CardRepository
import com.jpark.alarmcard.data.local.CardEntity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@AndroidEntryPoint
class AlarmDismissReceiver : BroadcastReceiver() {

    @Inject
    lateinit var repo: CardRepository

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == NotificationHelper.ACTION_DISMISS_ALARM) {
            val cardId = intent.getStringExtra(NotificationHelper.EXTRA_CARD_ID) ?: return

            // 알림 종료
            val nm = ContextCompat.getSystemService(context, NotificationManager::class.java)
            nm?.cancel(cardId.hashCode())

            // 카드의 알람 비활성화
            val pendingResult = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val entity = repo.getCardById(cardId) ?: return@launch
                    if (entity.type == CardEntity.TYPE_BUS) {
                        repo.setBusAlarm(cardId, false, entity.alarmMinutesBefore)
                        Timber.d("Alarm dismissed for card: $cardId")
                    }
                } catch (e: Exception) {
                    Timber.e(e, "Failed to dismiss alarm for card: $cardId")
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }
}