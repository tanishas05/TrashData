package com.example.trashdata

import android.app.Activity
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.content.Intent
import android.net.Uri
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.io.File

class MainActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val scrollView = ScrollView(this)
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        scrollView.addView(layout)
        setContentView(scrollView)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                requestAllFilesPermission()
            } else {
                startScan(layout)
            }
        } else {
            startScan(layout)
        }
    }

    private fun startScan(layout: LinearLayout) {
        Thread {
            val storageDir = Environment.getExternalStorageDirectory()
            listFilesRecursive(storageDir, layout, "")
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

    private fun listFilesRecursive(dir: File, layout: LinearLayout, indent: String) {

        val now = System.currentTimeMillis()
        val fiveMinutes = 5 * 60 * 1000
        val tenMinutes = 10 * 60 * 1000

        val files = dir.listFiles() ?: return

        for (file in files) {

            val diff = now - file.lastModified()

            val text = when {
                diff > tenMinutes -> "$indent Older than 10 min: ${file.name}"
                diff > fiveMinutes -> "$indent Older than 5 min: ${file.name}"
                else -> "$indent${file.name}"
            }

            runOnUiThread {
                val textView = TextView(this)
                textView.text = text
                layout.addView(textView)
            }

            if (file.isDirectory) {
                listFilesRecursive(file, layout, "$indent    ")
            }
        }
    }
}