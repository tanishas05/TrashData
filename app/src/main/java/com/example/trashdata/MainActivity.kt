package com.example.trashdata

import android.app.Activity
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.content.Intent
import android.net.Uri
import android.widget.*
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import java.io.File
import androidx.work.*
import java.util.concurrent.TimeUnit

class MainActivity : Activity() {

    private lateinit var layout: LinearLayout
    private lateinit var counterText: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var circleText: TextView
    private lateinit var sizeText: TextView

    private var scanning = false
    private var fileCount = 0
    private var oldFileCount = 0
    private var oldFileSize = 0L

    private var largeFileCount = 0
    private var apkCount = 0
    private var tempCount = 0
    private var emptyFolderCount = 0

    private val oldFiles = mutableListOf<File>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val scrollView = ScrollView(this)

        layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#F1F3F4"))
        }

        val title = TextView(this).apply {
            text = "TrashData"
            textSize = 28f
            setPadding(40,70,40,10)
            setTextColor(Color.parseColor("#202124"))
        }

        val subtitle = TextView(this).apply {
            text = "Storage Cleaner"
            textSize = 16f
            setPadding(40,0,40,30)
            setTextColor(Color.GRAY)
        }

        val dashboard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(40,50,40,50)
            background = makeCard()
        }

        val dashParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        dashParams.setMargins(40,0,40,40)
        dashboard.layoutParams = dashParams

        circleText = TextView(this).apply {
            text = "0"
            textSize = 42f
            gravity = Gravity.CENTER
            setTextColor(Color.parseColor("#1A73E8"))
        }

        val label = TextView(this).apply {
            text = "Old Files"
            textSize = 18f
            gravity = Gravity.CENTER
        }

        sizeText = TextView(this).apply {
            text = "Total Size: 0 MB"
            textSize = 16f
            gravity = Gravity.CENTER
        }

        dashboard.addView(circleText)
        dashboard.addView(label)
        dashboard.addView(sizeText)

        counterText = TextView(this).apply {
            text = "Files scanned: 0"
            setPadding(40,0,40,20)
        }

        progressBar = ProgressBar(this)
        progressBar.visibility = ProgressBar.GONE

        val startButton = Button(this).apply {
            text = "Scan Storage"
            setBackgroundColor(Color.parseColor("#1A73E8"))
            setTextColor(Color.WHITE)
        }

        val stopButton = Button(this).apply {
            text = "Stop Scan"
        }

        val deleteAllButton = Button(this).apply {
            text = "Delete All Old Files"
        }

        layout.addView(title)
        layout.addView(subtitle)
        layout.addView(dashboard)
        layout.addView(counterText)
        layout.addView(startButton)
        layout.addView(stopButton)
        layout.addView(deleteAllButton)
        layout.addView(progressBar)

        startButton.setOnClickListener { startScan() }

        stopButton.setOnClickListener {
            scanning = false
            progressBar.visibility = ProgressBar.GONE
        }

        deleteAllButton.setOnClickListener {

            for(file in oldFiles){
                if(file.exists()){
                    file.delete()
                }
            }

            Toast.makeText(this,"Old files deleted",Toast.LENGTH_SHORT).show()
        }

        scrollView.addView(layout)

        // ROOT LAYOUT
        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL

        // BOTTOM NAV BAR
        val navBar = LinearLayout(this)
        navBar.orientation = LinearLayout.HORIZONTAL
        navBar.setBackgroundColor(Color.WHITE)
        navBar.setPadding(20,20,20,20)

        val cleanerBtn = Button(this)
        cleanerBtn.text = "Cleaner"

        val storageBtn = Button(this)
        storageBtn.text = "Storage"

        navBar.addView(
            cleanerBtn,
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT,1f)
        )

        navBar.addView(
            storageBtn,
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT,1f)
        )

        root.addView(
            scrollView,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        )

        root.addView(navBar)

        setContentView(root)

        cleanerBtn.setOnClickListener {

            Toast.makeText(this,"Already on Cleaner",Toast.LENGTH_SHORT).show()

        }

        storageBtn.setOnClickListener {

            val intent = Intent(this, SecondActivity::class.java)
            startActivity(intent)

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
    }

    private fun makeCard(): GradientDrawable {
        val shape = GradientDrawable()
        shape.cornerRadius = 40f
        shape.setColor(Color.WHITE)
        return shape
    }

    private fun startScan() {

        scanning = true
        fileCount = 0
        oldFileCount = 0
        oldFileSize = 0

        largeFileCount = 0
        apkCount = 0
        tempCount = 0
        emptyFolderCount = 0

        oldFiles.clear()

        progressBar.visibility = ProgressBar.VISIBLE

        Thread {

            val storageDir = Environment.getExternalStorageDirectory()
            listFilesRecursive(storageDir)

            runOnUiThread {

                progressBar.visibility = ProgressBar.GONE

                circleText.text = "$oldFileCount"

                sizeText.text =
                    """
Old Files: $oldFileCount
Large Files: $largeFileCount
APK Files: $apkCount
Temp Files: $tempCount
Empty Folders: $emptyFolderCount
""".trimIndent()

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

    private fun listFilesRecursive(dir: File) {

        if (!scanning) return

        val path = dir.absolutePath

        if (path.contains("/Android")) return
        if (path.contains("/system")) return
        if (path.contains("/data")) return
        if (path.contains("/proc")) return
        if (path.contains("/dev")) return

        val now = System.currentTimeMillis()
        val tenMinutes = 10 * 60 * 1000

        val files = dir.listFiles() ?: return

        for (file in files) {

            if (!scanning) return

            fileCount++

            val diff = now - file.lastModified()

            if (file.isDirectory) {

                val children = file.listFiles()
                if (children == null || children.isEmpty()) {
                    emptyFolderCount++
                }

                listFilesRecursive(file)

            } else {

                if (file.name.startsWith(".")) continue

                if (diff > tenMinutes) {

                    oldFileCount++
                    oldFileSize += file.length()
                    oldFiles.add(file)

                    runOnUiThread {
                        addFileRow(file)
                    }
                }

                if (file.length() > 50 * 1024 * 1024) {
                    largeFileCount++
                }

                if (file.name.endsWith(".apk")) {
                    apkCount++
                }

                if (file.name.endsWith(".tmp") ||
                    file.name.endsWith(".log") ||
                    file.name.endsWith(".cache")) {

                    tempCount++
                }
            }

            runOnUiThread {
                counterText.text = "Files scanned: $fileCount"
            }
        }
    }

    private fun addFileRow(file: File) {

        val card = LinearLayout(this)
        card.orientation = LinearLayout.HORIZONTAL
        card.setPadding(30,25,30,25)
        card.background = makeCard()

        val params = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )

        params.setMargins(40,10,40,10)
        card.layoutParams = params

        val name = TextView(this)
        name.text = file.name
        name.textSize = 16f

        name.layoutParams = LinearLayout.LayoutParams(
            0,
            LinearLayout.LayoutParams.WRAP_CONTENT,
            1f
        )

        val deleteBtn = Button(this)
        deleteBtn.text = "Delete"

        deleteBtn.setOnClickListener {

            if (file.exists()) {

                file.delete()
                layout.removeView(card)

                Toast.makeText(this,"File deleted",Toast.LENGTH_SHORT).show()
            }

        }

        card.addView(name)
        card.addView(deleteBtn)

        layout.addView(card)
    }
}