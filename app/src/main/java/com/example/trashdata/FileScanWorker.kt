package com.example.trashdata

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.provider.MediaStore
import android.widget.Toast
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import java.io.File
import java.security.MessageDigest
import java.util.ArrayDeque
import java.util.concurrent.atomic.AtomicBoolean

class FileScanWorker(context: Context, params: WorkerParameters) :
    Worker(context, params) {
    companion object {
        val isScanning = AtomicBoolean(false)
        val cancelScan = AtomicBoolean(false)

        const val ACTION_PROGRESS = "com.example.trashdata.SCAN_PROGRESS"
        const val EXTRA_SCANNED_FILES = "scanned_files"
        const val EXTRA_TOTAL_FILES = "total_files"
    }
    private var scannedFileCount = 0
    private var totalSize = 0L
    private var totalFiles = 0

    override fun doWork(): Result {
        if (isScanning.get()) return Result.success()
        isScanning.set(true)
        cancelScan.set(false)

        try {
            val root = Environment.getExternalStorageDirectory()
            FileRepository.clear()
            // Dynamically count all relevant files first
            totalFiles = FileScanner.countTotalFiles(root)
            //  FILE SCAN
            FileScanner.scan(root, object : FileScanner.ScanCallback {
                override fun onFileFound(file: File) {
                    FileRepository.junkFiles.add(file)

                    // ✅ KEYWORD EXTRACTION (ONLY FOR TEXT FILES)
                    if (
                        (file.name.endsWith(".txt") ||
                                file.name.endsWith(".pdf") ||
                                file.name.endsWith(".docx")) &&
                        file.length() < 2 * 1024 * 1024
                    ) {
                        val text = FileTextExtractor.extractText(applicationContext, file)
                        val keywords = KeywordExtractor.extractFromText(text)
                        FileRepository.fileKeywords[file] = keywords
                    }

                    scannedFileCount++
                    totalSize += file.length()
                    sendProgressBroadcast(scannedFileCount, totalFiles)
                }
                override fun onProgress(count: Int, totalSize: Long) {
                    // progress now sent from onFileFound, nothing needed here
                }
                override fun isCancelled(): Boolean {
                    return cancelScan.get()
                }
            })

            // =================== BUILD DUPLICATES ===================
            // Only mark duplicates, do NOT delete
            FileRepository.buildDuplicateMap()

            // =================== NOTIFICATION ===================
            NotificationHelper.showSummaryNotification(
                applicationContext,
                scannedFileCount,
                totalSize
            )

            // Send completion broadcast
            val completeIntent = Intent("com.example.trashdata.SCAN_COMPLETE")
            LocalBroadcastManager.getInstance(applicationContext).sendBroadcast(completeIntent)

        } finally {
            isScanning.set(false)
        }

        return Result.success()
    }

    private fun sendProgressBroadcast(scannedFiles: Int, totalFiles: Int) {
        val intent = Intent(ACTION_PROGRESS)
        intent.putExtra(EXTRA_SCANNED_FILES, scannedFiles)
        intent.putExtra(EXTRA_TOTAL_FILES, totalFiles)
        LocalBroadcastManager.getInstance(applicationContext).sendBroadcast(intent)
    }

    // =================== SAFE MD5 HASH ===================
    private fun safeMD5(file: File): String? {
        return try {
            val digest = MessageDigest.getInstance("MD5")
            file.inputStream().use { fis ->
                val buffer = ByteArray(8192)
                var read: Int
                while (fis.read(buffer).also { read = it } > 0) digest.update(buffer, 0, read)
            }
            digest.digest().joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            null
        }
    }

    // =================== SAFE FILE DELETE ===================
    private fun safeDelete(file: File) {
        try {
            val deleted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val uri = MediaStore.Files.getContentUri("external")
                val rowsDeleted = applicationContext.contentResolver.delete(
                    uri,
                    "${MediaStore.MediaColumns.DATA}=?",
                    arrayOf(file.absolutePath)
                )
                rowsDeleted > 0
            } else {
                file.delete()
            }

            if (!deleted) sendToast("Failed to delete ${file.name}")
        } catch (e: SecurityException) {
            sendToast("Cannot delete ${file.name}")
        }
    }

    // CANCEL SCAN
    fun stopScan() {
        cancelScan.set(true)
    }
    // TOAST HELPER
    private fun sendToast(message: String) {
        Handler(applicationContext.mainLooper).post {
            Toast.makeText(applicationContext, message, Toast.LENGTH_SHORT).show()
        }
    }
}