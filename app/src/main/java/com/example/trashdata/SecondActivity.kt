package com.example.trashdata

import android.app.Activity
import android.os.Bundle
import android.os.Environment
import android.widget.*
import android.graphics.Color
import android.view.View
import android.view.ViewGroup
import java.io.File
import android.content.Intent

class SecondActivity : Activity() {

    private lateinit var listView: ListView
    private lateinit var filterSpinner: Spinner
    private lateinit var deleteSelectedBtn: Button

    private val allFiles = mutableListOf<File>()
    private val displayFiles = mutableListOf<File>()
    private val selectedFiles = mutableSetOf<File>()

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

        val spinnerAdapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            filters
        )

        filterSpinner.adapter = spinnerAdapter

        deleteSelectedBtn = Button(this)
        deleteSelectedBtn.text = "Delete Selected"

        listView = ListView(this)

        layout.addView(title)
        layout.addView(filterSpinner)
        layout.addView(deleteSelectedBtn)
        layout.addView(listView)

        // ---------- BOTTOM NAVIGATION ----------
        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL

        val navBar = LinearLayout(this)
        navBar.orientation = LinearLayout.HORIZONTAL
        navBar.setBackgroundColor(Color.parseColor("#EEEEEE"))
        navBar.setPadding(20,20,20,20)

        val cleanerBtn = Button(this)
        cleanerBtn.text = "Cleaner"

        val filesBtn = Button(this)
        filesBtn.text = "Files"

        navBar.addView(
            cleanerBtn,
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT,1f)
        )

        navBar.addView(
            filesBtn,
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT,1f)
        )

        root.addView(
            layout,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        )

        root.addView(navBar)

        setContentView(root)
        // --------------------------------------

        scanFiles()

        filterSpinner.onItemSelectedListener =
            object : AdapterView.OnItemSelectedListener {

                override fun onItemSelected(
                    parent: AdapterView<*>,
                    view: View?,
                    position: Int,
                    id: Long
                ) {
                    applyFilter(filters[position])
                }

                override fun onNothingSelected(parent: AdapterView<*>) {}
            }

        deleteSelectedBtn.setOnClickListener {

            for(file in selectedFiles){
                if(file.exists()){
                    file.delete()
                }
            }

            Toast.makeText(this,"Selected files deleted",Toast.LENGTH_SHORT).show()

            selectedFiles.clear()
            applyFilter(filterSpinner.selectedItem.toString())
        }

        // ---------- NAVIGATION ACTIONS ----------
        cleanerBtn.setOnClickListener {
            val intent = Intent(this, MainActivity::class.java)
            startActivity(intent)
        }

        filesBtn.setOnClickListener {
            Toast.makeText(this,"Already on Files screen",Toast.LENGTH_SHORT).show()
        }
        // ---------------------------------------
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

        displayFiles.sortByDescending { it.length() }

        showFiles()
    }

    private fun showFiles(){

        val adapter = object : BaseAdapter() {

            override fun getCount(): Int = displayFiles.size

            override fun getItem(position: Int): Any = displayFiles[position]

            override fun getItemId(position: Int): Long = position.toLong()

            override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {

                val file = displayFiles[position]

                val row = LinearLayout(this@SecondActivity)
                row.orientation = LinearLayout.HORIZONTAL
                row.setPadding(20,20,20,20)

                val checkBox = CheckBox(this@SecondActivity)

                checkBox.setOnCheckedChangeListener { _, isChecked ->

                    if(isChecked){
                        selectedFiles.add(file)
                    } else {
                        selectedFiles.remove(file)
                    }
                }

                val icon = TextView(this@SecondActivity)
                icon.text = getFileIcon(file)
                icon.textSize = 20f

                val name = TextView(this@SecondActivity)
                name.text = "${file.name} (${file.length()/1024} KB)"
                name.layoutParams = LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    1f
                )

                val deleteBtn = Button(this@SecondActivity)
                deleteBtn.text = "Delete"

                deleteBtn.setOnClickListener {

                    if(file.exists()){
                        file.delete()
                        Toast.makeText(this@SecondActivity,"Deleted ${file.name}",Toast.LENGTH_SHORT).show()
                        applyFilter(filterSpinner.selectedItem.toString())
                    }

                }

                row.addView(checkBox)
                row.addView(icon)
                row.addView(name)
                row.addView(deleteBtn)

                return row
            }
        }

        listView.adapter = adapter
    }

    private fun getFileIcon(file:File):String{

        val name = file.name.lowercase()

        return when {

            name.endsWith(".jpg") || name.endsWith(".png") -> "🖼"
            name.endsWith(".mp4") || name.endsWith(".mkv") -> "🎬"
            name.endsWith(".mp3") -> "🎵"
            name.endsWith(".apk") -> "📦"
            name.endsWith(".zip") -> "🗜"
            name.endsWith(".pdf") -> "📄"
            else -> "📁"

        }
    }
}