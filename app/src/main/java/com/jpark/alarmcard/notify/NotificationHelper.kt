package com.jpark.alarmcard.notify

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.jpark.alarmcard.domain.model.BusCard
import com.jpark.alarmcard.domain.model.StockCard

object NotificationHelper {
    const val CHANNEL_BUS_ALARM = "bus_alarm"
    const val CHANNEL_STOCK_ALARM = "stock_alarm"
    const val ACTION_DISMISS_ALARM = "com.jpark.alarmcard.ACTION_DISMISS_ALARM"
    const val EXTRA_CARD_ID = "card_id"

    fun ensureChannel(ctx: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (nm.getNotificationChannel(CHANNEL_BUS_ALARM) == null) {
                val ch = NotificationChannel(
                    CHANNEL_BUS_ALARM,
                    "버스 도착 알림",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "설정한 n분 이내로 버스가 도착할 때 알려줍니다."
                    enableVibration(true)
                }
                nm.createNotificationChannel(ch)
            }
            if (nm.getNotificationChannel(CHANNEL_STOCK_ALARM) == null) {
                val ch = NotificationChannel(
                    CHANNEL_STOCK_ALARM,
                    "주식 가격 알림",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "설정한 가격이나 변동률에 도달했을 때 알려줍니다."
                    enableVibration(true)
                }
                nm.createNotificationChannel(ch)
            }
        }
    }

    fun notifyBusArrival(ctx: Context, card: BusCard) {
        ensureChannel(ctx)
        val nextArrival = card.arrivals
            .filter { it.eta1Sec != null }
            .minByOrNull { it.eta1Sec!! }
        val eta = nextArrival?.eta1Sec ?: return
        val minutes = (eta + 59) / 60
        val routeNo = nextArrival.routeNo

        val launchIntent = ctx.packageManager.getLaunchIntentForPackage(ctx.packageName)?.apply {
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        val pi = if (launchIntent != null) {
            PendingIntent.getActivity(
                ctx, card.id.hashCode(), launchIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        } else null

        // 알림 끄기 버튼 클릭 시 처리할 Intent
        val dismissIntent = Intent(ctx, AlarmDismissReceiver::class.java).apply {
            action = ACTION_DISMISS_ALARM
            putExtra(EXTRA_CARD_ID, card.id)
        }
        val dismissPi = PendingIntent.getBroadcast(
            ctx,
            card.id.hashCode() + 1000,
            dismissIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(ctx, CHANNEL_BUS_ALARM)
            .setSmallIcon(android.R.drawable.ic_menu_directions)
            .setContentTitle("$routeNo 번 도착 임박")
            .setContentText("${card.stationName} · 약 ${minutes}분 후 도착 (${card.alarmMinutesBefore}분 알림)")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setVibrate(longArrayOf(0, 300, 200, 300))
            .setDefaults(NotificationCompat.DEFAULT_SOUND)
            .apply { if (pi != null) setContentIntent(pi) }
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "알람 끄기",
                dismissPi
            )
            .build()

        val nm = ContextCompat.getSystemService(ctx, NotificationManager::class.java) ?: return
        try {
            nm.notify(card.id.hashCode(), notification)
        } catch (_: SecurityException) {
            // Android 13+ 에서 POST_NOTIFICATIONS 권한이 없을 때 무시
        }
    }

    fun notifyStockAlarm(ctx: Context, card: StockCard) {
        ensureChannel(ctx)
        val name = card.name.ifBlank { card.symbol }
        val price = card.price ?: 0.0
        val rate = card.changeRate ?: 0.0

        val launchIntent = ctx.packageManager.getLaunchIntentForPackage(ctx.packageName)?.apply {
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        val pi = if (launchIntent != null) {
            PendingIntent.getActivity(
                ctx, card.id.hashCode(), launchIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        } else null

        val dismissIntent = Intent(ctx, AlarmDismissReceiver::class.java).apply {
            action = ACTION_DISMISS_ALARM
            putExtra(EXTRA_CARD_ID, card.id)
        }
        val dismissPi = PendingIntent.getBroadcast(
            ctx,
            card.id.hashCode() + 2000,
            dismissIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val content = if (card.alarmPriceThreshold != null) {
            "설정가 도달: ${String.format("%,.2f", price)} (임계치: ${String.format("%,.2f", card.alarmPriceThreshold)})"
        } else {
            "변동률 도달: ${String.format("%.2f%%", rate)} (임계치: ${String.format("%.2f%%", card.alarmRateThreshold)})"
        }

        val notification = NotificationCompat.Builder(ctx, CHANNEL_STOCK_ALARM)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle("주식 알림: $name")
            .setContentText(content)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setVibrate(longArrayOf(0, 500, 200, 500))
            .setDefaults(NotificationCompat.DEFAULT_SOUND)
            .apply { if (pi != null) setContentIntent(pi) }
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "알람 끄기",
                dismissPi
            )
            .build()

        val nm = ContextCompat.getSystemService(ctx, NotificationManager::class.java) ?: return
        try {
            nm.notify(card.id.hashCode(), notification)
        } catch (_: SecurityException) {}
    }
}
