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

    fun showNotification(context: Context, title: String, filePath: String) {

        val manager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // 🔵 Create channel for Android 8+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "TrashData Alerts",
                NotificationManager.IMPORTANCE_HIGH
            )
            manager.createNotificationChannel(channel)
        }

        // 🔥 OPEN FILE INTENT
        val file = File(filePath)

        val uri: Uri = FileProvider.getUriForFile(
            context,
            context.packageName + ".provider",
            file
        )

        val intent = Intent(Intent.ACTION_VIEW)
        intent.setDataAndType(uri, getMimeType(filePath))
        intent.flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK

        val pendingIntent = PendingIntent.getActivity(
            context,
            System.currentTimeMillis().toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // 🔔 BUILD NOTIFICATION
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(file.name)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent) // 🔥 OPEN FILE
            .setAutoCancel(true) // dismiss on click
            .build()

        manager.notify(System.currentTimeMillis().toInt(), notification)
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