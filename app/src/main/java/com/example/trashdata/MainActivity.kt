package com.example.trashdata

import android.app.Activity
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

// =================== SCAN FLAGS ===================
object FileScanWorkerFlags {
    val isScanning = java.util.concurrent.atomic.AtomicBoolean(false)
    val cancelScan = java.util.concurrent.atomic.AtomicBoolean(false)
}

object FileRepository {
    val junkFiles = mutableListOf<File>()
}
class MainActivity : Activity() {

    private lateinit var drawerLayout: DrawerLayout
    private lateinit var statusText: TextView
    private lateinit var counterText: TextView
    private lateinit var progressBar: ProgressBar

    private var scanning = false
    private var fileCount = 0

    // BroadcastReceiver for background scan progress
    private val scanProgressReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val scannedFiles = intent?.getIntExtra(FileScanWorker.EXTRA_SCANNED_FILES, 0) ?: 0
            runOnUiThread {
                counterText.text = "Junk files found: $scannedFiles"
                statusText.text = "Scanning (background)..."
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        drawerLayout = DrawerLayout(this)

        // ================= HEADER =================
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#2F80ED"))
            setPadding(40, 100, 40, 100)
            gravity = Gravity.CENTER
        }

        val hamburger = TextView(this).apply {
            text = "☰"
            textSize = 28f
            setTextColor(Color.WHITE)
            setTypeface(null, Typeface.BOLD)
            setOnClickListener { drawerLayout.openDrawer(GravityCompat.START) }
        }
        header.addView(hamburger, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { gravity = Gravity.START })

        val title = TextView(this).apply {
            text = "TrashData Cleaner"
            setTextColor(Color.WHITE)
            textSize = 22f
            setTypeface(null, Typeface.BOLD)
        }

        val subtitle = TextView(this).apply {
            text = "Junk Files"
            setTextColor(Color.WHITE)
        }

        val cleanBtn = TextView(this).apply {
            text = "CLEAN"
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            textSize = 18f
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#4DA3FF"))
            }
            layoutParams = LinearLayout.LayoutParams(300, 300).apply { setMargins(0, 40, 0, 0) }
            setOnClickListener { startScan() }
        }

        val cancelBtn = TextView(this).apply {
            text = "CANCEL"
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            textSize = 14f
            setOnClickListener {
                FileScanWorkerFlags.cancelScan.set(true)
                scanning = false
                statusText.text = "Scan canceled"
                progressBar.visibility = ProgressBar.GONE
                Toast.makeText(this@MainActivity, "Scan canceled", Toast.LENGTH_SHORT).show()
            }
        }

        header.addView(title)
        header.addView(subtitle)
        header.addView(cleanBtn)
        header.addView(cancelBtn)

        // ================= CONTENT =================
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(30, 30, 30, 30)
        }

        statusText = TextView(this).apply { text = "Status: Idle" }
        counterText = TextView(this).apply { text = "Junk files found: 0" }
        progressBar = ProgressBar(this).apply { visibility = ProgressBar.GONE }

        container.addView(statusText)
        container.addView(counterText)
        container.addView(progressBar)

        // Grid of items
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
                layoutParams = GridLayout.LayoutParams().apply {
                    width = 0
                    columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                }

                addView(TextView(this@MainActivity).apply {
                    this.text = icon
                    textSize = 36f
                    gravity = Gravity.CENTER
                })
                addView(TextView(this@MainActivity).apply {
                    this.text = text
                    gravity = Gravity.CENTER
                })

                setOnClickListener {
                    try { action() }
                    catch (e: Exception) {
                        Toast.makeText(this@MainActivity, "Action failed: ${e.message}", Toast.LENGTH_SHORT).show()
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
        grid.addView(createItem("📁", "All Files") { startScan() })

        container.addView(grid)

        // ================= MAIN CONTENT =================
        val mainContent = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.WHITE)
            layoutParams = DrawerLayout.LayoutParams(
                DrawerLayout.LayoutParams.MATCH_PARENT,
                DrawerLayout.LayoutParams.MATCH_PARENT
            )
            addView(header)
            addView(container, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        }

        // ================= DRAWER MENU =================
        val drawerMenu = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.LTGRAY)
            layoutParams = DrawerLayout.LayoutParams(600, DrawerLayout.LayoutParams.MATCH_PARENT).apply {
                gravity = GravityCompat.START
            }
        }

        val menuCleaner = TextView(this).apply {
            text = "Cleaner"
            setPadding(20, 40, 20, 40)
            setOnClickListener {
                Toast.makeText(this@MainActivity, "Cleaner clicked", Toast.LENGTH_SHORT).show()
                drawerLayout.closeDrawer(GravityCompat.START)
            }
        }
        val menuFiles = TextView(this).apply {
            text = "Files"
            setPadding(20, 40, 20, 40)
            setOnClickListener {
                startActivity(Intent(this@MainActivity, SecondActivity::class.java))
                drawerLayout.closeDrawer(GravityCompat.START)
            }
        }
        val menuSettings = TextView(this).apply {
            text = "Settings"
            setPadding(20, 40, 20, 40)
            setOnClickListener {
                Toast.makeText(this@MainActivity, "Settings clicked", Toast.LENGTH_SHORT).show()
                drawerLayout.closeDrawer(GravityCompat.START)
            }
        }

        drawerMenu.addView(menuCleaner)
        drawerMenu.addView(menuFiles)
        drawerMenu.addView(menuSettings)

        // ================= ADD TO DRAWER =================
        drawerLayout.addView(mainContent)
        drawerLayout.addView(drawerMenu)
        setContentView(drawerLayout)

        // ================= PERMISSIONS & NOTIFICATIONS =================
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
    }

    // =================== MANUAL SCAN ===================
    private fun startScan() {
        scanning = true
        fileCount = 0
        FileRepository.junkFiles.clear()
        FileScanWorkerFlags.cancelScan.set(false)

        statusText.text = "Scanning..."
        progressBar.visibility = ProgressBar.VISIBLE

        Thread {
            val storageDir = Environment.getExternalStorageDirectory()
            scanIterative(storageDir) { scannedFiles ->
                runOnUiThread { counterText.text = "Junk files found: $scannedFiles" }
            }

            runOnUiThread {
                if (!FileScanWorkerFlags.cancelScan.get()) {
                    statusText.text = "Scan Complete"
                    Toast.makeText(this, "Scan complete: $fileCount junk files", Toast.LENGTH_SHORT).show()
                    showNotification("Scan complete. $fileCount junk files found.")
                    val intent = Intent(this, SecondActivity::class.java)
                    intent.putExtra("filter", "All Files")
                    startActivity(intent)
                }
                progressBar.visibility = ProgressBar.GONE
            }
        }.start()
    }

    private fun scanIterative(root: File, onProgress: (Int) -> Unit) {
        val queue = ArrayDeque<File>()
        queue.add(root)
        val now = System.currentTimeMillis()
        val oldThreshold = 15 * 60 * 1000L // 15 minutes

        while (queue.isNotEmpty() && !FileScanWorkerFlags.cancelScan.get()) {
            val dir = queue.removeFirst()
// ✅ ADD THIS LINE
            if (dir.absolutePath.contains("/Android")) continue
            val files = try {
                dir.listFiles()
            } catch (e: SecurityException) {
                runOnUiThread { Toast.makeText(this, "Cannot access ${dir.path}", Toast.LENGTH_SHORT).show() }
                continue
            } ?: continue

            for (file in files) {
                if (FileScanWorkerFlags.cancelScan.get()) return

                if (file.isFile && now - file.lastModified() > oldThreshold && isRelevant(file)) {
                    fileCount++
                    FileRepository.junkFiles.add(file)
                }

                if (file.isDirectory) queue.add(file)
            }
            onProgress(fileCount)
        }
    }

    private fun isRelevant(file: File): Boolean {
        val n = file.name.lowercase()
        val minSize = 1 * 1024 * 1024 // 1 MB

        return when {
            n.endsWith(".jpg") || n.endsWith(".jpeg") || n.endsWith(".png") -> true
            n.endsWith(".mp4") || n.endsWith(".mkv") || n.endsWith(".avi") -> true
            n.endsWith(".mp3") || n.endsWith(".wav") -> true
            n.endsWith(".pdf") || n.endsWith(".doc") || n.endsWith(".docx") || n.endsWith(".txt") -> true
            n.endsWith(".apk") -> true
            n.endsWith(".zip") || n.endsWith(".rar") -> true
            else -> false
        } && file.length() > minSize
    }

    // =================== PERMISSIONS ===================
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
                // Permission already granted → start scan
                startBackgroundScan()
            }
        } else {
            // Pre Android 13 → start scan
            startBackgroundScan()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == 101) { // Notification permission
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Notification permission granted", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Notification permission denied", Toast.LENGTH_SHORT).show()
            }

            // Either way, start background scan now
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
        val builder = NotificationCompat.Builder(this, "trashdata_channel")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("TrashData Alert")
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)

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

        // Check for Storage access first
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                // Ask for storage permission first
                requestAllFilesPermission()
                return
            }
        }

        // If storage permission granted, request notification permission
        requestNotificationPermission()
        startBackgroundScan()
    }
    override fun onDestroy() {
        super.onDestroy()
        LocalBroadcastManager.getInstance(this).unregisterReceiver(scanProgressReceiver)
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
}