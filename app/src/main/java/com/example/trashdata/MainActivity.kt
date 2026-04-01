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

        // Scrollable layout to display file names
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
                // Permission already granted
                showAllFiles(layout)
            }
        } else {
            // Android 10 and below
            showAllFiles(layout)
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

    private fun showAllFiles(layout: LinearLayout) {
        val storageDir = Environment.getExternalStorageDirectory()
        listFilesRecursive(storageDir, layout, "")
    }

    private fun listFilesRecursive(dir: File, layout: LinearLayout, indent: String) {
        val files = dir.listFiles()
        if (files != null) {
            for (file in files) {
                val textView = TextView(this)
                textView.text = "$indent${file.name}"
                layout.addView(textView)
                if (file.isDirectory) {
                    listFilesRecursive(file, layout, "$indent    ")
                }
            }
        }
    }
}