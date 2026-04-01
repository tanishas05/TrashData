package com.example.trashdata

import android.content.Context
import android.os.Environment
import androidx.work.Worker
import androidx.work.WorkerParameters
import java.io.File

class FileScanWorker(context: Context, params: WorkerParameters) :
    Worker(context, params) {

    override fun doWork(): Result {

        val root = Environment.getExternalStorageDirectory()
        scanDirectory(root)

        return Result.success()
    }

    private fun scanDirectory(dir: File) {

        val now = System.currentTimeMillis()
        val fiveMinutes = 5 * 60 * 1000
        val tenMinutes = 10 * 60 * 1000

        val files = dir.listFiles() ?: return

        for (file in files) {

            val diff = now - file.lastModified()

            if (diff > tenMinutes) {
                NotificationHelper.showNotification(
                    applicationContext,
                    "File older than 10 minutes",
                    file.absolutePath
                )
            }

            if (file.isDirectory) {
                scanDirectory(file)
            }
        }
    }
}