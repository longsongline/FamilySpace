package com.example.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.MainActivity
import com.example.R

object FamilyNotificationHelper {

    const val CHANNEL_ID_FAMILY_CARE = "family_care_channel"
    const val CHANNEL_NAME = "亲情空间 · 关怀与报平安通知"
    const val CHANNEL_DESCRIPTION = "孩子安全到家自动提醒与家庭重要节日关怀通知"

    const val CHANNEL_ID_LOCATION_SERVICE = "family_location_service_channel"
    const val CHANNEL_NAME_LOCATION_SERVICE = "亲情空间 · 后台位置守护"
    const val CHANNEL_DESCRIPTION_LOCATION_SERVICE = "用于在后台静默同步位置与到家安全监测"

    const val NOTIFICATION_ID_ARRIVAL = 1001
    const val NOTIFICATION_ID_EVENT = 1002
    const val NOTIFICATION_ID_LOCATION_SERVICE = 1003

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return

            val careChannel = NotificationChannel(
                CHANNEL_ID_FAMILY_CARE,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = CHANNEL_DESCRIPTION
                enableLights(true)
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 300, 200, 300)
            }
            notificationManager.createNotificationChannel(careChannel)

            val locationChannel = NotificationChannel(
                CHANNEL_ID_LOCATION_SERVICE,
                CHANNEL_NAME_LOCATION_SERVICE,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = CHANNEL_DESCRIPTION_LOCATION_SERVICE
                setShowBadge(false)
                enableVibration(false)
                enableLights(false)
            }
            notificationManager.createNotificationChannel(locationChannel)
        }
    }

    fun buildLocationServiceNotification(
        context: Context,
        content: String = "正在静默守护到家安全与位置同步"
    ): android.app.Notification {
        createNotificationChannel(context)

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            2,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(context, CHANNEL_ID_LOCATION_SERVICE)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("亲情空间 · 后台位置守护中 🏠")
            .setContentText(content)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .build()
    }

    fun showSafeArrivalNotification(
        context: Context,
        title: String,
        message: String,
        location: String
    ) {
        createNotificationChannel(context)

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

        val builder = NotificationCompat.Builder(context, CHANNEL_ID_FAMILY_CARE)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("$message\n📍 到达地点: $location")
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setSound(soundUri)
            .setVibrate(longArrayOf(0, 350, 200, 350))
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)

        try {
            val notificationManager = NotificationManagerCompat.from(context)
            notificationManager.notify(NOTIFICATION_ID_ARRIVAL, builder.build())
        } catch (_: SecurityException) {
            // Permission might not be granted yet on Android 13+
        }
    }

    fun showBirthdayReminderNotification(
        context: Context,
        title: String,
        message: String
    ) {
        createNotificationChannel(context)

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            1,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID_FAMILY_CARE)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)

        try {
            val notificationManager = NotificationManagerCompat.from(context)
            notificationManager.notify(NOTIFICATION_ID_EVENT, builder.build())
        } catch (_: SecurityException) {}
    }
}
