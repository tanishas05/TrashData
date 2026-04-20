package com.example.trashdata

import android.app.Activity
import androidx.work.OneTimeWorkRequestBuilder
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.net.Uri
import android.widget.*
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import androidx.drawerlayout.widget.DrawerLayout
import androidx.core.view.GravityCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.work.*
import java.io.File
import java.util.ArrayDeque
import java.util.concurrent.TimeUnit
import android.app.NotificationChannel
import android.app.NotificationManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import android.app.PendingIntent
import android.view.View
import android.view.MotionEvent
import android.util.TypedValue

class MainActivity : Activity() {
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var statusText: TextView
    private lateinit var counterText: TextView
    private lateinit var progressBar: ProgressBar
    lateinit var scanStatus: TextView
    private var scanning = false
    private var fileCount = 0
    private lateinit var dashboard: LinearLayout
    // Named references for each dashboard stat — avoids the brittle child-traversal bug
    private lateinit var statTotalSize: TextView
    private lateinit var statTotalFiles: TextView
    private lateinit var statDuplicates: TextView
    private lateinit var statLargeFiles: TextView
    private lateinit var statOldFiles: TextView
    private lateinit var statFreeSpace: TextView

    /** Live-update the dashboard whenever SecondActivity deletes a file. */
    private val filesChangedReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            runOnUiThread { updateDashboard() }
        }
    }
    val scanProgressReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {

            val scannedFiles = intent?.getIntExtra(FileScanWorker.EXTRA_SCANNED_FILES, 0) ?: 0
            val totalFiles = intent?.getIntExtra(FileScanWorker.EXTRA_TOTAL_FILES, 1) ?: 1

            val percent = if (totalFiles > 0) (scannedFiles * 100 / totalFiles) else 0

            runOnUiThread {
                counterText.text = "Junk files found: $scannedFiles"
                statusText.text = "Scanning... ($percent%)"

                progressBar.visibility = View.VISIBLE
                progressBar.max = 100
                progressBar.progress = percent

                if (percent >= 100) {
                    statusText.text = "Scan Complete ✅"
                    progressBar.visibility = View.GONE
                    updateDashboard()
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        drawerLayout = DrawerLayout(this)

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.WHITE)
            setPadding(40, 40, 40, 20)
            elevation = 8f
        }

        val topBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val hamburger = TextView(this).apply {
            text = "☰"
            textSize = 28f
            setTextColor(Color.parseColor("#1A1A2E"))
            setTypeface(null, Typeface.BOLD)
            setOnClickListener { drawerLayout.openDrawer(GravityCompat.START) }
        }

        val title = TextView(this).apply {
            text = "TrashData Cleaner"
            setTextColor(Color.parseColor("#1A1A2E"))
            textSize = 22f
            setTypeface(null, Typeface.BOLD)
        }

        val subtitle = TextView(this).apply {
            text = "Junk Files"
            setTextColor(Color.parseColor("#6B7280"))
        }

        val cleanBtn = TextView(this).apply {
            text = "SCAN"
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            textSize = 20f
            setTypeface(null, Typeface.BOLD)
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(
                    Color.parseColor("#4A90E2"),
                    Color.parseColor("#6C5CE7")
                )
            ).apply {
                shape = GradientDrawable.OVAL
            }
            elevation = 20f
            setOnClickListener { startScan() }
            setOnTouchListener { v, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        v.scaleX = 0.9f
                        v.scaleY = 0.9f
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        v.scaleX = 1f
                        v.scaleY = 1f
                    }
                }
                false
            }
        }

        val cancelBtn = TextView(this).apply {
            text = "CANCEL"
            gravity = Gravity.CENTER
            setTextColor(Color.parseColor("#4A90E2"))
            textSize = 14f
            setOnClickListener {
                FileScanWorker.cancelScan.set(true)
                scanning = false
                statusText.text = "Scan canceled"
                progressBar.visibility = ProgressBar.GONE
                Toast.makeText(this@MainActivity, "Scan canceled", Toast.LENGTH_SHORT).show()
            }
        }

        topBar.addView(hamburger)
        /* header.addView(hamburger, LinearLayout.LayoutParams(
             LinearLayout.LayoutParams.WRAP_CONTENT,
             LinearLayout.LayoutParams.WRAP_CONTENT
         ).apply {
             gravity = Gravity.START
         }) */
        topBar.addView(title, LinearLayout.LayoutParams(
            0,
            LinearLayout.LayoutParams.WRAP_CONTENT,
            1f
        ).apply {
            gravity = Gravity.CENTER
        })

        title.gravity = Gravity.CENTER

        header.addView(topBar)
        /* header.addView(title, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            topMargin = 10
        }) */
        header.addView(subtitle, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.CENTER_HORIZONTAL
        })

        header.addView(cleanBtn, LinearLayout.LayoutParams(320, 320).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            topMargin = 20
        })

        header.addView(cancelBtn, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.CENTER_HORIZONTAL
        })

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(30, 30, 30, 30)
            setBackgroundColor(Color.parseColor("#F5F6FA"))
        }

        statusText = TextView(this).apply {
            text = "Status: Idle"
            textSize = 16f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#1A1A2E"))
        }

        counterText = TextView(this).apply {
            text = "Junk files found: 0"
            textSize = 14f
            setTextColor(Color.parseColor("#6B7280"))
        }

        scanStatus = TextView(this).apply {
            text = ""
            textSize = 14f
            setTextColor(Color.parseColor("#6B7280"))
            gravity = Gravity.CENTER
        }

        progressBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            progress = 0
            visibility = View.GONE
        }

        container.addView(statusText)

        container.addView(counterText)

        container.addView(progressBar)

        container.addView(scanStatus)

        val grid = GridLayout(this).apply {
            columnCount = 2
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            alignmentMode = GridLayout.ALIGN_MARGINS
            useDefaultMargins = true
        }

        fun createItem(icon: String, text: String, action: () -> Unit): LinearLayout {
            return LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(40, 40, 40, 40)
                background = GradientDrawable().apply {
                    cornerRadius = 30f
                    setColor(Color.WHITE)
                    setStroke(2, Color.parseColor("#E5E7EB"))
                }
                elevation = 10f
                val typedValue = TypedValue()
                theme.resolveAttribute(android.R.attr.selectableItemBackground, typedValue, true)
                foreground = getDrawable(typedValue.resourceId)
                layoutParams = GridLayout.LayoutParams().apply {
                    width = 0
                    columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                    setMargins(30, 30, 30, 30)
                }
                addView(TextView(this@MainActivity).apply {
                    this.text = icon
                    textSize = 48f
                    gravity = Gravity.CENTER
                })
                addView(TextView(this@MainActivity).apply {
                    this.text = text
                    textSize = 14f
                    setTypeface(null, Typeface.BOLD)
                    gravity = Gravity.CENTER
                    setTextColor(Color.parseColor("#1A1A2E"))
                })
                setOnClickListener {
                    try { action() }
                    catch (e: Exception) {
                        Toast.makeText(this@MainActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        fun openFilesActivity(filter: String) {
            val intent = Intent(this, SecondActivity::class.java)
            intent.putExtra("filter", filter)
            try {
                startActivity(intent)
            } catch (e: Exception) {
                Toast.makeText(this, "Cannot open file list: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }

        grid.addView(createItem("🗂", "Old Files") { openFilesActivity("Old Files") })
        grid.addView(createItem("📦", "Large Files") { openFilesActivity("Large Files") })
        grid.addView(createItem("🧹", "Duplicate Files") { openFilesActivity("Duplicate Files") })
        grid.addView(createItem("📁", "All Files") { openFilesActivity("All Files")})

        container.addView(grid)

        val dashBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20, 20, 20, 20)
            background = GradientDrawable().apply {
                cornerRadius = 28f
                setColor(Color.WHITE)
                setStroke(2, Color.parseColor("#E5E7EB"))
            }
            elevation = 6f
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                topMargin = 24
            }
        }

        val dashTitle = TextView(this).apply {
            text = "Storage Dashboard"
            textSize = 15f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#1A1A2E"))
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(4, 0, 4, 16)
        }
        dashBox.addView(dashTitle)

        dashboard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        val gap = 10

        fun makeRow() = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT)
        }

        val row1 = makeRow()
        val row2 = makeRow()
        val row3 = makeRow()

        // FIX: capture each card's value TextView directly into named fields.
        // The old code relied on positional child-traversal in updateDashboard() which
        // miscounted rows vs cards and caused duplicate-files (and other stats) to glitch.
        fun statCardRef(icon: String, label: String, initial: String): Pair<LinearLayout, TextView> {
            val tv = TextView(this).apply {
                text = initial
                textSize = 15f
                setTypeface(null, Typeface.BOLD)
                setTextColor(Color.parseColor("#4A90E2"))
                gravity = Gravity.CENTER
            }
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(8, 16, 8, 16)
                background = GradientDrawable().apply {
                    cornerRadius = 18f
                    setColor(Color.parseColor("#F8F9FF"))
                    setStroke(1, Color.parseColor("#E5E7EB"))
                }
                elevation = 2f
                layoutParams = LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                    setMargins(0, 0, gap, gap)
                }
            }
            card.addView(TextView(this).apply {
                text = icon; textSize = 24f; gravity = Gravity.CENTER
            })
            card.addView(tv)
            card.addView(TextView(this).apply {
                text = label; textSize = 10f
                setTextColor(Color.parseColor("#6B7280")); gravity = Gravity.CENTER
            })
            return Pair(card, tv)
        }

        val (cardTotalSize,   tvTotalSize)   = statCardRef("💾", "Total Junk Size", "0 KB")
        val (cardTotalFiles,  tvTotalFiles)  = statCardRef("📂", "Total Files",     "0")
        val (cardDuplicates,  tvDuplicates)  = statCardRef("🔁", "Duplicates",      "0")
        val (cardLargeFiles,  tvLargeFiles)  = statCardRef("📦", "Large Files",     "0")
        val (cardOldFiles,    tvOldFiles)    = statCardRef("🕒", "Old Files",       "0")
        val (cardFreeSpace,   tvFreeSpace)   = statCardRef("🆓", "Free Space",      "…")

        // Store references so updateDashboard() can update them directly
        statTotalSize  = tvTotalSize
        statTotalFiles = tvTotalFiles
        statDuplicates = tvDuplicates
        statLargeFiles = tvLargeFiles
        statOldFiles   = tvOldFiles
        statFreeSpace  = tvFreeSpace

        row1.addView(cardTotalSize)
        row1.addView(cardTotalFiles)
        row2.addView(cardDuplicates)
        row2.addView(cardLargeFiles)
        row3.addView(cardOldFiles)
        row3.addView(cardFreeSpace)

        dashboard.addView(row1)
        dashboard.addView(row2)
        dashboard.addView(row3)

        dashBox.addView(dashboard)
        container.addView(dashBox)


        val scrollContainer = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
            isVerticalScrollBarEnabled = false
            addView(container)
        }

        val mainContent = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#F5F6FA"))
            layoutParams = DrawerLayout.LayoutParams(
                DrawerLayout.LayoutParams.MATCH_PARENT,
                DrawerLayout.LayoutParams.MATCH_PARENT
            )
            addView(header)
            addView(scrollContainer)
        }

        val drawerMenu = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.WHITE)
            layoutParams = DrawerLayout.LayoutParams(600, DrawerLayout.LayoutParams.MATCH_PARENT).apply {
                gravity = GravityCompat.START
            }
            elevation = 16f
        }

        val drawerHeader = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#4A90E2"))
            setPadding(40, 120, 40, 40)
        }

        drawerHeader.addView(TextView(this).apply {
            text = "TrashData"
            textSize = 20f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
        })

        drawerHeader.addView(TextView(this).apply {
            text = "File Cleaner"
            textSize = 12f
            setTextColor(Color.parseColor("#BBDEFB"))
        })

        drawerMenu.addView(drawerHeader)

        val menuCleaner = TextView(this).apply {
            text = "🧹  Scanner"
            textSize = 15f
            setTextColor(Color.parseColor("#1A1A2E"))
            setPadding(40, 40, 40, 40)
            setOnClickListener {
                Toast.makeText(this@MainActivity, "Cleaner clicked", Toast.LENGTH_SHORT).show()
                drawerLayout.closeDrawer(GravityCompat.START)
            }
        }

        val menuFiles = TextView(this).apply {
            text = "📁  All Files"
            textSize = 15f
            setTextColor(Color.parseColor("#1A1A2E"))
            setPadding(40, 40, 40, 40)
            setOnClickListener {
                startActivity(Intent(this@MainActivity, SecondActivity::class.java))
                drawerLayout.closeDrawer(GravityCompat.START)
            }
        }

        drawerMenu.addView(menuCleaner)

        drawerMenu.addView(menuFiles)

        val menuRecycleBin = TextView(this).apply {
            text = "🗑  Recycle Bin"
            textSize = 15f
            setTextColor(Color.parseColor("#1A1A2E"))
            setPadding(40, 40, 40, 40)
            setOnClickListener {
                startActivity(Intent(this@MainActivity, RecycleBinActivity::class.java))
                drawerLayout.closeDrawer(GravityCompat.START)
            }
        }
        drawerMenu.addView(menuRecycleBin)

        drawerLayout.addView(mainContent)

        drawerLayout.addView(drawerMenu)

        setContentView(drawerLayout)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !Environment.isExternalStorageManager()) {
            requestAllFilesPermission()
        } else {
            startBackgroundScan()
        }
        createNotificationChannel()

        LocalBroadcastManager.getInstance(this).registerReceiver(
            scanProgressReceiver,
            IntentFilter(FileScanWorker.ACTION_PROGRESS)
        )
        LocalBroadcastManager.getInstance(this).registerReceiver(
            filesChangedReceiver,
            IntentFilter(FileScanWorker.ACTION_FILES_CHANGED)
        )
    }
    private fun startScan() {
        scanning = true
        fileCount = 0
        FileRepository.clear()
        FileScanWorker.cancelScan.set(false)

        statusText.text = "Scanning..."
        progressBar.visibility = View.VISIBLE
        progressBar.progress = 0

        val workRequest = OneTimeWorkRequestBuilder<FileScanWorker>().build()
        WorkManager.getInstance(this).enqueue(workRequest)
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
    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(
                    arrayOf(android.Manifest.permission.POST_NOTIFICATIONS),
                    101
                )
            } else {
                startBackgroundScan()
            }
        } else {
            startBackgroundScan()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 101) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Notification permission granted", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Notification permission denied", Toast.LENGTH_SHORT).show()
            }
            startBackgroundScan()
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
        val intent = Intent(this, SecondActivity::class.java)
        intent.putExtra("filter", "All Files")
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK

        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val bigText = """
🧹 Scan Complete!

$message

Tap to view and clean 🚀
""".trimIndent()

        val builder = NotificationCompat.Builder(this, "trashdata_channel")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Scan Complete ✅")
            .setContentText("Tap to view junk files")
            .setStyle(NotificationCompat.BigTextStyle().bigText(bigText))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .addAction(android.R.drawable.ic_menu_view, "View", pendingIntent)
            .setAutoCancel(true)

        val manager = NotificationManagerCompat.from(this)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            manager.notify(1, builder.build())
        }
    }

    override fun onResume() {
        super.onResume()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                requestAllFilesPermission()
                return
            }
        }
        requestNotificationPermission()
        startBackgroundScan()
        updateDashboard()
    }

    override fun onDestroy() {
        super.onDestroy()
        LocalBroadcastManager.getInstance(this).unregisterReceiver(scanProgressReceiver)
        LocalBroadcastManager.getInstance(this).unregisterReceiver(filesChangedReceiver)
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

    private fun updateDashboard() {
        // Guard: stat TextViews must be initialized before updating
        if (!::statTotalSize.isInitialized) return

        val files = FileRepository.junkFiles
        val now   = System.currentTimeMillis()

        val totalSize  = files.sumOf { it.length() }
        // FIX: count only the *extra* copies, not all files in a duplicate group.
        // duplicateMap groups files by hash; a group of size N has (N-1) duplicates.
        val duplicates = FileRepository.duplicateMap.values
            .filter { it.size > 1 }
            .sumOf { it.size - 1 }
        val largeFiles = files.count { it.length() >= 1 * 1024 * 1024L }
        val oldFiles   = files.count { now - it.lastModified() > 15 * 60 * 1000L }
        val freeSpace  = try {
            val stat = android.os.StatFs(android.os.Environment.getExternalStorageDirectory().path)
            stat.availableBlocksLong * stat.blockSizeLong
        } catch (e: Exception) { 0L }

        // Direct assignment — no child-traversal, no index drift, no glitching
        statTotalSize.text  = formatSize(totalSize)
        statTotalFiles.text = "${files.size}"
        statDuplicates.text = "$duplicates"
        statLargeFiles.text = "$largeFiles"
        statOldFiles.text   = "$oldFiles"
        statFreeSpace.text  = if (freeSpace > 0) formatSize(freeSpace) else "N/A"
    }

    private fun formatSize(bytes: Long): String {
        val kb = bytes / 1024; val mb = kb / 1024; val gb = mb / 1024
        return when {
            gb > 0 -> "${"%.1f".format(bytes / 1_073_741_824.0)} GB"
            mb > 0 -> "${"%.1f".format(bytes / 1_048_576.0)} MB"
            else -> "$kb KB"
        }
    }
}