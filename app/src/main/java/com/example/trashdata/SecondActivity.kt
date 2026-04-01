package com.example.trashdata

import android.app.Activity
import android.os.Bundle
import android.os.Environment
import android.widget.*
import android.graphics.Color
import java.io.File

class SecondActivity : Activity() {

    private lateinit var listView: ListView
    private lateinit var filterSpinner: Spinner

    private val allFiles = mutableListOf<File>()
    private val displayFiles = mutableListOf<File>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val layout = LinearLayout(this)
        layout.orientation = LinearLayout.VERTICAL
        layout.setPadding(30,70,30,30)
        layout.setBackgroundColor(Color.WHITE)

        val title = TextView(this)
        title.text = "Detected Files"
        title.textSize = 24f

        filterSpinner = Spinner(this)

        val filters = arrayOf(
            "All Files",
            "Old Files",
            "Large Files",
            "APK Files",
            "Temp Files"
        )

        val adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            filters
        )

        filterSpinner.adapter = adapter

        listView = ListView(this)

        layout.addView(title)
        layout.addView(filterSpinner)
        layout.addView(listView)

        setContentView(layout)

        scanFiles()

        filterSpinner.onItemSelectedListener =
            object : AdapterView.OnItemSelectedListener {

                override fun onItemSelected(
                    parent: AdapterView<*>,
                    view: android.view.View?,
                    position: Int,
                    id: Long
                ) {
                    applyFilter(filters[position])
                }

                override fun onNothingSelected(parent: AdapterView<*>) {}
            }
    }

    private fun scanFiles() {

        Thread {

            val root = Environment.getExternalStorageDirectory()
            scanRecursive(root)

            runOnUiThread {
                applyFilter("All Files")
            }

        }.start()
    }

    private fun scanRecursive(dir: File) {

        val path = dir.absolutePath

        if (path.contains("/Android")) return
        if (path.contains("/system")) return

        val files = dir.listFiles() ?: return

        for(file in files){

            if(file.isDirectory){
                scanRecursive(file)
            } else {
                allFiles.add(file)
            }

        }
    }

    private fun applyFilter(type:String){

        displayFiles.clear()

        val now = System.currentTimeMillis()
        val tenMin = 10 * 60 * 1000

        for(file in allFiles){

            when(type){

                "All Files" -> displayFiles.add(file)

                "Old Files" -> {
                    if(now - file.lastModified() > tenMin)
                        displayFiles.add(file)
                }

                "Large Files" -> {
                    if(file.length() > 50 * 1024 * 1024)
                        displayFiles.add(file)
                }

                "APK Files" -> {
                    if(file.name.endsWith(".apk"))
                        displayFiles.add(file)
                }

                "Temp Files" -> {
                    if(file.name.endsWith(".tmp")
                        || file.name.endsWith(".log")
                        || file.name.endsWith(".cache"))
                        displayFiles.add(file)
                }
            }
        }

        showFiles()
    }

    private fun showFiles(){

        val names = mutableListOf<String>()

        for(file in displayFiles){
            names.add("${file.name} (${file.length()/1024} KB)")
        }

        val adapter = ArrayAdapter(
            this,
            android.R.layout.simple_list_item_1,
            names
        )

        listView.adapter = adapter

        listView.setOnItemClickListener { _,_,position,_ ->

            val file = displayFiles[position]

            if(file.exists()){
                file.delete()
                Toast.makeText(this,"Deleted ${file.name}",Toast.LENGTH_SHORT).show()
                displayFiles.removeAt(position)
                showFiles()
            }
        }
    }
}