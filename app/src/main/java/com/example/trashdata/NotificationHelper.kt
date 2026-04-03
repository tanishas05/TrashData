package com.example.trashdata

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import android.content.Intent
import android.app.PendingIntent
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

object NotificationHelper {

    private const val CHANNEL_ID = "trashdata_channel"
    private const val NOTIFICATION_ID = 1

    fun showSummaryNotification(context: Context, count: Int, size: Long) {

        val manager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Create channel (Android 8+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

            val channel = NotificationChannel(
                CHANNEL_ID,
                "TrashData Alerts",
                NotificationManager.IMPORTANCE_HIGH
            )

            manager.createNotificationChannel(channel)
        }

        val summaryText = "Found $count old files\nTotal size: ${formatSize(size)}"

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("Scan Complete")
            .setContentText("Trash detected")
            .setStyle(NotificationCompat.BigTextStyle().bigText(summaryText))
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent) // 🔥 OPEN FILE
            .setAutoCancel(true) // dismiss on click
            .build()

        // ✅ SAME ID → replaces old notification
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

    // 🔥 DETECT FILE TYPE
    private fun getMimeType(path: String): String {

        return when {
            path.endsWith(".pdf") -> "application/pdf"
            path.endsWith(".jpg") || path.endsWith(".jpeg") -> "image/jpeg"
            path.endsWith(".png") -> "image/png"
            path.endsWith(".mp4") -> "video/mp4"
            path.endsWith(".mp3") -> "audio/mpeg"
            path.endsWith(".txt") -> "text/plain"
            path.endsWith(".doc") || path.endsWith(".docx") -> "application/msword"
            path.endsWith(".zip") -> "application/zip"
            else -> "*/*"
        }
    }
}