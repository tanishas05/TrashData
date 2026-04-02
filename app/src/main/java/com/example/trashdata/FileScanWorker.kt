package com.example.trashdata

import android.content.Context
import android.os.Environment
import androidx.work.Worker
import androidx.work.WorkerParameters
import java.io.File

class FileScanWorker(context: Context, params: WorkerParameters) :
    Worker(context, params) {
    private val appStartTime = System.currentTimeMillis()

    override fun doWork(): Result {

        val root = Environment.getExternalStorageDirectory()
        scanDirectory(root)

        return Result.success()
    }

    private fun scanDirectory(dir: File) {

        val now = System.currentTimeMillis()
        val fiveMinutes = 5 * 60 * 1000

        val files = dir.listFiles() ?: return

        for (file in files) {

            val diff = now - file.lastModified()

            if (file.lastModified() > appStartTime && diff > fiveMinutes) {
                // Notify for all files older than 5 minutes
                NotificationHelper.showNotification(
                    applicationContext,
                    "Old file detected",
                    file.absolutePath
                )
            }

            if (file.isDirectory) {
                scanDirectory(file)
            }
        }
    }
}