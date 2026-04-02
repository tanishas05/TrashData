package com.example.trashdata

import android.app.Activity
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.content.Intent
import android.net.Uri
import android.widget.*
import java.io.File
import androidx.work.*
import java.util.concurrent.TimeUnit
import android.app.NotificationChannel
import android.app.NotificationManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

class MainActivity : Activity() {

    private lateinit var layout: LinearLayout
    private lateinit var statusText: TextView
    private lateinit var counterText: TextView
    private lateinit var progressBar: ProgressBar

    private var scanning = false
    private var fileCount = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val scrollView = ScrollView(this)

        layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(30,30,30,30)
        }

        statusText = TextView(this)
        statusText.text = "Status: Idle"

        counterText = TextView(this)
        counterText.text = "Files scanned: 0"

        progressBar = ProgressBar(this)
        progressBar.visibility = ProgressBar.GONE

        val startButton = Button(this)
        startButton.text = "Start Scan"

        val stopButton = Button(this)
        stopButton.text = "Stop Scan"

        val clearButton = Button(this)
        clearButton.text = "Clear Results"

        layout.addView(statusText)
        layout.addView(counterText)
        layout.addView(startButton)
        layout.addView(stopButton)
        layout.addView(clearButton)
        layout.addView(progressBar)

        scrollView.addView(layout)
        setContentView(scrollView)

        startButton.setOnClickListener {
            startScan()
        }

        stopButton.setOnClickListener {
            scanning = false
            statusText.text = "Status: Stopped"
            progressBar.visibility = ProgressBar.GONE
        }

        clearButton.setOnClickListener {
            if (layout.childCount > 6) {
                layout.removeViews(6, layout.childCount - 6)
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {

            if (!Environment.isExternalStorageManager()) {

                requestAllFilesPermission()

            } else {

                startBackgroundScan()

            }

        } else {

            startBackgroundScan()

        }
        requestNotificationPermission()
        createNotificationChannel()
    }

    private fun startScan() {

        scanning = true
        fileCount = 0

        statusText.text = "Status: Scanning..."
        progressBar.visibility = ProgressBar.VISIBLE

        Thread {

            val storageDir = Environment.getExternalStorageDirectory()
            listFilesRecursive(storageDir, "")

            runOnUiThread {
                statusText.text = "Status: Scan Complete"
                progressBar.visibility = ProgressBar.GONE

                showNotification("Scan complete. $fileCount files scanned.")
            }

        }.start()
    }

    private fun requestAllFilesPermission() {
        try {
            val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
            intent.data = Uri.parse("package:$packageName")
            startActivity(intent)
        } catch (e: Exception) {
            val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
            startActivity(intent)
        }
    }

    private fun startBackgroundScan() {

        val workRequest =
            PeriodicWorkRequestBuilder<FileScanWorker>(15, TimeUnit.MINUTES)
                .build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "trashdata_scan",
            ExistingPeriodicWorkPolicy.KEEP,
            workRequest
        )
    }

    private fun listFilesRecursive(dir: File, indent: String) {

        if (!scanning) return
        if (dir.absolutePath.contains("/Android/")) return

        val now = System.currentTimeMillis()
        val fiveMinutes = 5 * 60 * 1000
        val tenMinutes = 10 * 60 * 1000

        val files = dir.listFiles() ?: return

        for (file in files) {

            if (!scanning) return

            fileCount++

            val diff = now - file.lastModified()

            val text = when {
                diff > tenMinutes -> "$indent Older than 10 min: ${file.name}"
                diff > fiveMinutes -> "$indent Older than 5 min: ${file.name}"
                else -> "$indent${file.name}"
            }

            runOnUiThread {

                counterText.text = "Files scanned: $fileCount"

                val textView = TextView(this)
                textView.text = text
                layout.addView(textView)

            }

            if (file.isDirectory) {
                listFilesRecursive(file, "$indent    ")
            }
        }
    }
    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestPermissions(
                arrayOf(android.Manifest.permission.POST_NOTIFICATIONS),
                101
            )
        }
    }
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "trashdata_channel",
                "TrashData Alerts",
                NotificationManager.IMPORTANCE_HIGH
            )

            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }
    private fun showNotification(message: String) {

        val builder = NotificationCompat.Builder(this, "trashdata_channel")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("TrashData Alert")
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)

        val manager = NotificationManagerCompat.from(this)

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            manager.notify(1, builder.build())
        }
    }
}