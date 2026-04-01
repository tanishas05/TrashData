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

    private val oldFiles = mutableListOf<File>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val scrollView = ScrollView(this)

        layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40,40,40,40)
            setBackgroundColor(Color.WHITE)
            gravity = Gravity.CENTER_HORIZONTAL
        }

        // BIG DASHBOARD CIRCLE
        circleText = TextView(this).apply {

            text = "0\nOld Files"
            textSize = 30f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)

            val params = LinearLayout.LayoutParams(400,400)
            params.gravity = Gravity.CENTER_HORIZONTAL
            layoutParams = params

            background = makeCircle()
        }

        sizeText = TextView(this).apply {

            text = "Total Size: 0 MB"
            textSize = 18f
            gravity = Gravity.CENTER
            setTextColor(Color.parseColor("#1565C0"))

        }

        counterText = TextView(this).apply {
            text = "Files scanned: 0"
            textSize = 16f
        }

        progressBar = ProgressBar(this)
        progressBar.visibility = ProgressBar.GONE

        val startButton = Button(this).apply {
            text = "Scan Files"
            setBackgroundColor(Color.parseColor("#1976D2"))
            setTextColor(Color.WHITE)
        }

        val stopButton = Button(this).apply {
            text = "Stop Scan"
            setBackgroundColor(Color.parseColor("#64B5F6"))
            setTextColor(Color.WHITE)
        }

        val deleteAllButton = Button(this).apply {
            text = "Delete All Old Files"
            setBackgroundColor(Color.parseColor("#E53935"))
            setTextColor(Color.WHITE)
        }

        layout.addView(circleText)
        layout.addView(sizeText)
        layout.addView(counterText)
        layout.addView(startButton)
        layout.addView(stopButton)
        layout.addView(deleteAllButton)
        layout.addView(progressBar)

        scrollView.addView(layout)
        setContentView(scrollView)

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

    private fun makeCircle(): GradientDrawable {

        val shape = GradientDrawable()
        shape.shape = GradientDrawable.OVAL
        shape.setColor(Color.parseColor("#1976D2"))
        shape.setStroke(8, Color.parseColor("#BBDEFB"))

        return shape
    }

    private fun startScan() {

        scanning = true
        fileCount = 0
        oldFileCount = 0
        oldFileSize = 0
        oldFiles.clear()

        progressBar.visibility = ProgressBar.VISIBLE

        Thread {

            val storageDir = Environment.getExternalStorageDirectory()
            listFilesRecursive(storageDir)

            runOnUiThread {

                progressBar.visibility = ProgressBar.GONE

                circleText.text = "$oldFileCount\nOld Files"
                sizeText.text = "Total Size: ${oldFileSize / (1024*1024)} MB"

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

        // SYSTEM SAFETY
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
            }

            runOnUiThread {
                counterText.text = "Files scanned: $fileCount"
            }
        }
    }

    private fun addFileRow(file: File) {

        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.setPadding(0,10,0,10)

        val name = TextView(this)
        name.text = file.name

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
                layout.removeView(row)

                Toast.makeText(this,"File deleted",Toast.LENGTH_SHORT).show()
            }

        }

        row.addView(name)
        row.addView(deleteBtn)

        layout.addView(row)
    }
}