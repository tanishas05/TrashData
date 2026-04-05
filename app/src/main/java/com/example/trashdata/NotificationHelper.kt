package com.example.trashdata

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import android.app.PendingIntent
import android.content.Intent

object NotificationHelper {

    private const val CHANNEL_ID = "trashdata_channel"
    private const val NOTIFICATION_ID = 1

    fun showSummaryNotification(context: Context, count: Int, size: Long) {

        val manager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "TrashData Alerts",
                NotificationManager.IMPORTANCE_HIGH
            )
            manager.createNotificationChannel(channel)
        }

        // 👉 Intent to open results screen
        val intent = Intent(context, SecondActivity::class.java)
        intent.putExtra("filter", "All Files")
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK

        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val summaryText = "Found $count old files\nTotal size: ${formatSize(size)}"

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("Scan Complete")
            .setContentText("Trash detected")
            .setStyle(NotificationCompat.BigTextStyle().bigText(summaryText))
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent) // ✅ CLICK HANDLER
            .setAutoCancel(true) // ✅ disappears when tapped
            .build()

        manager.notify(NOTIFICATION_ID, notification)
    }

    private fun formatSize(size: Long): String {
        val kb = size / 1024
        val mb = kb / 1024
        val gb = mb / 1024

        return when {
            gb > 0 -> "$gb GB"
            mb > 0 -> "$mb MB"
            kb > 0 -> "$kb KB"
            else -> "$size B"
        }
    }
}