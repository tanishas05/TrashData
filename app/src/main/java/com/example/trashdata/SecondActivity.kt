package com.example.trashdata

import android.app.Activity
import android.os.Bundle
import android.os.Environment
import android.widget.*
import android.graphics.Color
import android.view.View
import android.view.ViewGroup
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import java.io.File
import android.content.Intent
import android.text.Editable
import android.text.TextWatcher

class SecondActivity : Activity() {

    private lateinit var listView: ListView
    private lateinit var filterSpinner: Spinner
    private lateinit var deleteSelectedBtn: Button
    private lateinit var searchBar: EditText
    private lateinit var fileCount: TextView

    private val allFiles = mutableListOf<File>()
    private val displayFiles = mutableListOf<File>()
    private val selectedFiles = mutableSetOf<File>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 🔵 ROOT
        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setBackgroundColor(Color.parseColor("#F5F7FA"))

        // 🔵 HEADER
        val header = LinearLayout(this)
        header.orientation = LinearLayout.VERTICAL
        header.setBackgroundColor(Color.parseColor("#2F80ED"))
        header.setPadding(30, 80, 30, 40)

        val title = TextView(this)
        title.text = "Files Cleaner"
        title.setTextColor(Color.WHITE)
        title.textSize = 22f

        fileCount = TextView(this)
        fileCount.text = "Scanning..."
        fileCount.setTextColor(Color.WHITE)

        header.addView(title)
        header.addView(fileCount)

        // ⚪ CONTENT
        val container = LinearLayout(this)
        container.orientation = LinearLayout.VERTICAL
        container.setPadding(20, 20, 20, 20)

        // 🔍 SEARCH BAR
        searchBar = EditText(this)
        searchBar.hint = "Search files..."

        val searchBg = GradientDrawable()
        searchBg.cornerRadius = 50f
        searchBg.setColor(Color.WHITE)
        searchBar.background = searchBg
        searchBar.setPadding(30,20,30,20)

        // 🔽 FILTER
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

        // 🔴 DELETE BUTTON
        deleteSelectedBtn = Button(this)
        deleteSelectedBtn.text = "Delete Selected"
        deleteSelectedBtn.setBackgroundColor(Color.parseColor("#FF5252"))
        deleteSelectedBtn.setTextColor(Color.WHITE)

        // 📄 LIST
        listView = ListView(this)

        container.addView(searchBar)
        container.addView(filterSpinner)
        container.addView(deleteSelectedBtn)
        container.addView(listView)

        // 🔻 NAV BAR
        val navBar = LinearLayout(this)
        navBar.orientation = LinearLayout.HORIZONTAL
        navBar.setBackgroundColor(Color.WHITE)

        val cleanerBtn = Button(this)
        cleanerBtn.text = "Cleaner"

        val filesBtn = Button(this)
        filesBtn.text = "Files"

        navBar.addView(cleanerBtn, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT,1f))
        navBar.addView(filesBtn, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT,1f))

        root.addView(header)
        root.addView(container, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
        ))
        root.addView(navBar)

        setContentView(root)

        // 🔥 START SCAN
        scanFiles()

        // 🎯 FILTER FROM DASHBOARD
        val filter = intent.getStringExtra("filter")
        if (filter != null) {
            applyFilter(filter)
        }

        // FILTER CHANGE
        filterSpinner.onItemSelectedListener =
            object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                    applyFilter(filters[position])
                }
                override fun onNothingSelected(parent: AdapterView<*>) {}
            }

        // SEARCH
        searchBar.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                val query = s.toString().lowercase()
                val filtered = displayFiles.filter {
                    it.name.lowercase().contains(query)
                }
                showFiles(filtered)
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        // DELETE SELECTED
        deleteSelectedBtn.setOnClickListener {
            for(file in selectedFiles){
                if(file.exists()){
                    file.delete()
                }
            }
            Toast.makeText(this,"Deleted selected files",Toast.LENGTH_SHORT).show()
            selectedFiles.clear()
            applyFilter(filterSpinner.selectedItem.toString())
        }

        cleanerBtn.setOnClickListener {
            startActivity(Intent(this, MainActivity::class.java))
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

        fileCount.text = "${displayFiles.size} files found"

        showFiles(displayFiles)
    }

    private fun showFiles(files: List<File>){

        val adapter = object : BaseAdapter() {

            override fun getCount(): Int = files.size
            override fun getItem(position: Int): Any = files[position]
            override fun getItemId(position: Int): Long = position.toLong()

            override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {

                val file = files[position]

                val card = LinearLayout(this@SecondActivity)
                card.orientation = LinearLayout.HORIZONTAL
                card.setPadding(20,20,20,20)

                val bg = GradientDrawable()
                bg.cornerRadius = 25f
                bg.setColor(Color.WHITE)
                card.background = bg

                val checkBox = CheckBox(this@SecondActivity)

                checkBox.setOnCheckedChangeListener { _, isChecked ->
                    if(isChecked) selectedFiles.add(file)
                    else selectedFiles.remove(file)
                }

                val icon = TextView(this@SecondActivity)
                icon.text = getFileIcon(file)
                icon.textSize = 22f

                val name = TextView(this@SecondActivity)
                name.text = "${file.name}\n${formatSize(file.length())}"
                name.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT,1f)

                val deleteBtn = Button(this@SecondActivity)
                deleteBtn.text = "Delete"

                deleteBtn.setOnClickListener {
                    if(file.exists()){
                        file.delete()
                        applyFilter(filterSpinner.selectedItem.toString())
                    }
                }

                card.addView(checkBox)
                card.addView(icon)
                card.addView(name)
                card.addView(deleteBtn)

                return card
            }
        }

        listView.adapter = adapter
    }

    private fun formatSize(size:Long):String{
        val kb = size / 1024
        val mb = kb / 1024
        val gb = mb / 1024

        return when{
            gb > 0 -> "$gb GB"
            mb > 0 -> "$mb MB"
            else -> "$kb KB"
        }
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