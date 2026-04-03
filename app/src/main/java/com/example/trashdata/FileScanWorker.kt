package com.example.trashdata

import android.content.Context
import android.os.Environment
import androidx.work.Worker
import androidx.work.WorkerParameters
import java.io.File

class FileScanWorker(context: Context, params: WorkerParameters) :
    Worker(context, params) {

    // 🔥 Keep (but not used in logic now)
    private val appStartTime = System.currentTimeMillis()

    override fun doWork(): Result {

        val root = Environment.getExternalStorageDirectory()

        val prefs = applicationContext.getSharedPreferences("scan_prefs", Context.MODE_PRIVATE)
        val lastScanTime = prefs.getLong("last_scan_time", 0L)

        // 🔥 Run scan
        val foundFiles = mutableListOf<String>()
        scanDirectory(root, lastScanTime, foundFiles)

        // 🔥 Send SINGLE summary notification (no spam)
        if (foundFiles.isNotEmpty()) {
            NotificationHelper.showNotification(
                applicationContext,
                "TrashData Alert",
                "${foundFiles.size} old files found"
            )
        }

        // 🔥 Save current scan time
        prefs.edit().putLong("last_scan_time", System.currentTimeMillis()).apply()

        return Result.success()
    }

    // 🔥 UPDATED FUNCTION
    private fun scanDirectory(dir: File, lastScanTime: Long, foundFiles: MutableList<String>) {

        if (dir.absolutePath.contains("/Android")) return

        val now = System.currentTimeMillis()
        val fifteenMinutes = 15 * 60 * 1000

        val files = dir.listFiles() ?: return

        for (file in files) {

            if (!file.exists()) continue

            val lastModified = file.lastModified()
            val diff = now - lastModified

            // 🔥 CONDITION 1: file became old (crossed 15 min)
            val justBecameOld = lastModified in lastScanTime..now && diff > fifteenMinutes

            // 🔥 CONDITION 2: not already notified
            val notNotified = !isAlreadyNotified(file.absolutePath)

            if (justBecameOld && notNotified) {

                foundFiles.add(file.name)

                markAsNotified(file.absolutePath)
            }

            if (file.isDirectory) {
                scanDirectory(file, lastScanTime, foundFiles)
            }
        }
    }

    // 🔥 Prevent duplicate notifications
    private fun isAlreadyNotified(path: String): Boolean {
        val prefs = applicationContext.getSharedPreferences("notified_files", Context.MODE_PRIVATE)
        return prefs.getBoolean(path, false)
    }

    private fun markAsNotified(path: String) {
        val prefs = applicationContext.getSharedPreferences("notified_files", Context.MODE_PRIVATE)
        prefs.edit().putBoolean(path, true).apply()
    }
}