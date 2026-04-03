package com.example.trashdata

import android.content.Context
import android.content.Intent
import android.os.Environment
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest
import java.util.ArrayDeque
import java.util.concurrent.atomic.AtomicBoolean

class FileScanWorker(context: Context, params: WorkerParameters) :
    Worker(context, params) {

    companion object {
        val isScanning = AtomicBoolean(false)
        val cancelScan = AtomicBoolean(false)

        // Broadcast constants
        const val ACTION_PROGRESS = "com.example.trashdata.SCAN_PROGRESS"
        const val EXTRA_SCANNED_FILES = "scanned_files"

        private const val MAX_TRACKED_DUPLICATES = 5000
    }

    private var oldFileCount = 0
    private var totalSize = 0L

    override fun doWork(): Result {

        if (isScanning.get()) return Result.success()
        isScanning.set(true)
        cancelScan.set(false)

        try {
            val root = Environment.getExternalStorageDirectory()
            scanDirectoryIterative(root)
            detectDuplicates(root)  // ✅ Duplicate detection added

            NotificationHelper.showSummaryNotification(
                applicationContext,
                oldFileCount,
                totalSize
            )

        } finally {
            isScanning.set(false)
        }

        return Result.success()
    }

    // =================== ITERATIVE SCAN ===================
    private fun scanDirectoryIterative(root: File) {
        val now = System.currentTimeMillis()
        val fiveMinutes = 5 * 60 * 1000

        val queue = ArrayDeque<File>()
        queue.add(root)

        while (queue.isNotEmpty() && !cancelScan.get()) {
            val dir = queue.removeFirst()
            val files = dir.listFiles() ?: continue

            for (file in files) {
                if (cancelScan.get()) return

                if (file.isFile && now - file.lastModified() > fiveMinutes && isRelevant(file)) {
                    oldFileCount++
                    totalSize += file.length()
                }

                if (file.isDirectory) queue.add(file)
            }

            sendProgressBroadcast(oldFileCount)
        }
    }

    private fun isRelevant(file: File): Boolean {
        val path = file.absolutePath.lowercase()
        val minSize = 1 * 1024 * 1024
        return (path.endsWith(".jpg") || path.endsWith(".jpeg") ||
                path.endsWith(".png") || path.endsWith(".mp4") ||
                path.endsWith(".mp3") || path.endsWith(".pdf")) &&
                file.length() > minSize
    }

    private fun sendProgressBroadcast(scannedFiles: Int) {
        val intent = Intent(ACTION_PROGRESS)
        intent.putExtra(EXTRA_SCANNED_FILES, scannedFiles)
        LocalBroadcastManager.getInstance(applicationContext).sendBroadcast(intent)
    }

    // =================== DUPLICATE DETECTION ===================
    private fun detectDuplicates(root: File) {
        val sizeMap = mutableMapOf<Long, MutableList<File>>()
        val hashMap = mutableMapOf<String, MutableList<File>>()

        val queue = ArrayDeque<File>()
        queue.add(root)

        while (queue.isNotEmpty() && !cancelScan.get()) {
            val dir = queue.removeFirst()
            val files = dir.listFiles() ?: continue

            for (file in files) {
                if (cancelScan.get()) return
                if (file.isDirectory) {
                    queue.add(file)
                } else if (isRelevant(file)) {
                    // Group by file size first
                    sizeMap.getOrPut(file.length()) { mutableListOf() }.add(file)
                }
            }
        }

        // Compute MD5 only for size-matched groups
        for ((_, sameSizeFiles) in sizeMap) {
            if (sameSizeFiles.size < 2) continue
            for (file in sameSizeFiles) {
                if (cancelScan.get()) return
                val hash = safeMD5(file)
                if (hash != null) {
                    hashMap.getOrPut(hash) { mutableListOf() }.add(file)
                }
                if (hashMap.size > MAX_TRACKED_DUPLICATES) break
            }
        }

        // TODO: Do something with duplicates (hashMap entries with size>1)
    }

    // =================== SAFE MD5 HASH ===================
    private fun safeMD5(file: File): String? {
        return try {
            val digest = MessageDigest.getInstance("MD5")
            file.inputStream().use { fis ->
                val buffer = ByteArray(8192)
                var read: Int
                while (fis.read(buffer).also { read = it } > 0) {
                    digest.update(buffer, 0, read)
                }
            }
            digest.digest().joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            null
        }
    }

    // =================== CANCEL SCAN ===================
    fun stopScan() {
        cancelScan.set(true)
    }
}