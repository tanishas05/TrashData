package com.example.trashdata

import android.content.Context
import android.os.Environment
import androidx.work.Worker
import androidx.work.WorkerParameters
import java.io.File

class FileScanWorker(context: Context, params: WorkerParameters) :
    Worker(context, params) {

    private var oldFileCount = 0
    private var totalSize = 0L

    override fun doWork(): Result {

        val root = Environment.getExternalStorageDirectory()
        scanDirectory(root)

        // 🔔 Send ONE summary notification
        NotificationHelper.showSummaryNotification(
            applicationContext,
            oldFileCount,
            totalSize
        )

        return Result.success()
    }

    private fun scanDirectory(dir: File) {

        val now = System.currentTimeMillis()
        val fiveMinutes = 5 * 60 * 1000

        val files = dir.listFiles() ?: return

        for (file in files) {

            val diff = now - file.lastModified()

            // ✅ Check for old files (older than 5 minutes)
            if (diff > fiveMinutes) {
                oldFileCount++
                totalSize += file.length()
            }

            // 🔁 Recursively scan subfolders
            if (file.isDirectory) {
                scanDirectory(file)
            }
        }
    }
}