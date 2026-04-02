package com.example.trashdata

import android.app.Activity
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.content.Intent
import android.net.Uri
import android.widget.*
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import java.io.File
import androidx.work.*
import java.util.concurrent.TimeUnit
import android.app.NotificationChannel
import android.app.NotificationManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat


class MainActivity : Activity() {

    private lateinit var statusText: TextView
    private lateinit var counterText: TextView
    private lateinit var progressBar: ProgressBar

    private var scanning = false
    private var fileCount = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 🔵 ROOT
        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setBackgroundColor(Color.WHITE)

        // 🔵 HEADER
        val header = LinearLayout(this)
        header.orientation = LinearLayout.VERTICAL
        header.setBackgroundColor(Color.parseColor("#2F80ED"))
        header.setPadding(40, 100, 40, 100)
        header.gravity = Gravity.CENTER

        val title = TextView(this)
        title.text = "TrashData Cleaner"
        title.setTextColor(Color.WHITE)
        title.textSize = 22f
        title.setTypeface(null, Typeface.BOLD)

        val subtitle = TextView(this)
        subtitle.text = "Junk Files"
        subtitle.setTextColor(Color.WHITE)

        // ⭕ CLEAN BUTTON
        val cleanBtn = TextView(this)
        cleanBtn.text = "CLEAN"
        cleanBtn.gravity = Gravity.CENTER
        cleanBtn.setTextColor(Color.WHITE)
        cleanBtn.textSize = 18f

        val shape = GradientDrawable()
        shape.shape = GradientDrawable.OVAL
        shape.setColor(Color.parseColor("#4DA3FF"))
        cleanBtn.background = shape

        val size = 300
        val params = LinearLayout.LayoutParams(size, size)
        params.setMargins(0, 40, 0, 0)
        cleanBtn.layoutParams = params

        header.addView(title)
        header.addView(subtitle)
        header.addView(cleanBtn)

        // ⚪ CONTENT CARD
        val container = LinearLayout(this)
        container.orientation = LinearLayout.VERTICAL
        container.setPadding(30, 30, 30, 30)

        statusText = TextView(this)
        statusText.text = "Status: Idle"

        counterText = TextView(this)
        counterText.text = "Files scanned: 0"

        progressBar = ProgressBar(this)
        progressBar.visibility = ProgressBar.GONE

        // 🔲 GRID
        val grid = GridLayout(this)
        grid.columnCount = 2

        val gridParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        grid.layoutParams = gridParams

        grid.alignmentMode = GridLayout.ALIGN_MARGINS
        grid.useDefaultMargins = true

        fun createItem(icon: String, text: String, action: () -> Unit): LinearLayout {

            val box = LinearLayout(this)
            box.orientation = LinearLayout.VERTICAL
            box.gravity = Gravity.CENTER
            box.setPadding(40, 40, 40, 40)

            val params = GridLayout.LayoutParams()
            params.width = 0
            params.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
            box.layoutParams = params

            val iconView = TextView(this)
            iconView.text = icon
            iconView.textSize = 36f
            iconView.gravity = Gravity.CENTER

            val label = TextView(this)
            label.text = text
            label.gravity = Gravity.CENTER

            box.addView(iconView)
            box.addView(label)

            box.setOnClickListener { action() }

            return box
        }

        grid.addView(createItem("🗂", "Old Files") {
            startActivity(Intent(this, SecondActivity::class.java).putExtra("filter","Old Files"))
        })

        grid.addView(createItem("📦", "Large Files") {
            startActivity(Intent(this, SecondActivity::class.java).putExtra("filter","Large Files"))
        })

        grid.addView(createItem("🧹", "Duplicate Files") {
            startActivity(Intent(this, SecondActivity::class.java))
        })

        grid.addView(createItem("📁", "Files") {
            startActivity(Intent(this, SecondActivity::class.java))
        })

        // ➕ ADD ALL
        container.addView(statusText)
        container.addView(counterText)
        container.addView(progressBar)
        container.addView(grid)

        // 🔻 BOTTOM NAV
        val nav = LinearLayout(this)
        nav.orientation = LinearLayout.HORIZONTAL
        nav.setBackgroundColor(Color.parseColor("#EEEEEE"))

        val cleanerBtn = Button(this)
        cleanerBtn.text = "Cleaner"

        val filesBtn = Button(this)
        filesBtn.text = "Files"

        nav.addView(cleanerBtn, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        nav.addView(filesBtn, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))

        filesBtn.setOnClickListener {
            startActivity(Intent(this, SecondActivity::class.java))
        }

        // 🔗 ROOT ADD
        root.addView(header)
        root.addView(container, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
        ))
        root.addView(nav)

        setContentView(root)

        // 🔥 CLEAN BUTTON ACTION
        cleanBtn.setOnClickListener {
            startScan()
        }

        // 🔧 PERMISSIONS
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
        // ---- ADD THIS RIGHT HERE ----
        val workRequest = PeriodicWorkRequestBuilder<FileScanWorker>(15, java.util.concurrent.TimeUnit.MINUTES)
            .build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "trashdata_5min_scan",
            ExistingPeriodicWorkPolicy.KEEP,
            workRequest
        )
// -----------------------------
    }

    private fun startScan() {
        scanning = true
        fileCount = 0

        statusText.text = "Scanning..."
        progressBar.visibility = ProgressBar.VISIBLE

        Thread {
            val storageDir = Environment.getExternalStorageDirectory()
            listFilesRecursive(storageDir)

            runOnUiThread {
                statusText.text = "Scan Complete"
                progressBar.visibility = ProgressBar.GONE
                showNotification("Scan complete. $fileCount files scanned.")
            }
        }.start()
    }

    private fun listFilesRecursive(dir: File) {
        if (!scanning) return
        if (dir.absolutePath.contains("/Android/")) return

        val files = dir.listFiles() ?: return

        for (file in files) {
            if (!scanning) return

            fileCount++

            runOnUiThread {
                counterText.text = "Files scanned: $fileCount"
            }

            if (file.isDirectory) {
                listFilesRecursive(file)
            }
        }
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
            checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            manager.notify(1, builder.build())
        }
    }
}