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
import com.github.mikephil.charting.charts.PieChart
import com.github.mikephil.charting.data.*

class SecondActivity : Activity() {

    private lateinit var listView: ListView
    private lateinit var filterSpinner: Spinner
    private lateinit var deleteSelectedBtn: Button
    private lateinit var searchBar: EditText
    private lateinit var fileCount: TextView
    private lateinit var progressText: TextView
    private lateinit var pieChart: PieChart
    private lateinit var sortToggle: Button

    private val allFiles = mutableListOf<File>()
    private val displayFiles = mutableListOf<File>()
    private val selectedFiles = mutableSetOf<File>()

    private val duplicateMap = HashMap<String, MutableList<File>>()

    private var totalFiles = 0
    private var scannedFiles = 0
    private var sortBySize = true

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
        fileCount.setTextColor(Color.WHITE)

        progressText = TextView(this)
        progressText.setTextColor(Color.WHITE)

        header.addView(title)
        header.addView(fileCount)
        header.addView(progressText)

        // ⚪ CONTENT
        val container = LinearLayout(this)
        container.orientation = LinearLayout.VERTICAL
        container.setPadding(20, 20, 20, 20)

        // 🔍 SEARCH
        searchBar = EditText(this)
        searchBar.hint = "Search files..."
        val bg = GradientDrawable()
        bg.cornerRadius = 50f
        bg.setColor(Color.WHITE)
        searchBar.background = bg
        searchBar.setPadding(30,20,30,20)

        // 🔽 FILTER
        filterSpinner = Spinner(this)
        val filters = arrayOf(
            "All Files",
            "Old Files",
            "Large Files",
            "Duplicate Files",
            "APK Files",
            "Temp Files"
        )
        filterSpinner.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            filters
        )

        // 🔄 SORT BUTTON
        sortToggle = Button(this)
        sortToggle.text = "Sort: Size"

        // 🔴 DELETE
        deleteSelectedBtn = Button(this)
        deleteSelectedBtn.text = "Delete Selected"
        deleteSelectedBtn.setBackgroundColor(Color.RED)
        deleteSelectedBtn.setTextColor(Color.WHITE)

        // 📊 PIE CHART
        pieChart = PieChart(this)

        // 📄 LIST
        listView = ListView(this)

        container.addView(pieChart)
        container.addView(searchBar)
        container.addView(filterSpinner)
        container.addView(sortToggle)
        container.addView(deleteSelectedBtn)
        container.addView(listView)

        // 🔻 NAV
        val navBar = LinearLayout(this)
        navBar.orientation = LinearLayout.HORIZONTAL

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

        // 🔄 SORT TOGGLE
        sortToggle.setOnClickListener {
            sortBySize = !sortBySize
            sortToggle.text = if (sortBySize) "Sort: Size" else "Sort: Date"
            applyFilter(filterSpinner.selectedItem.toString())
        }

        // FILTER
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

        // DELETE
        deleteSelectedBtn.setOnClickListener {
            for(file in selectedFiles){
                if(file.exists()) file.delete()
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

            totalFiles = allFiles.size
            buildDuplicateMap()

            runOnUiThread {
                applyFilter("All Files")
                showStorageChart()
            }
        }.start()
    }

    private fun scanRecursive(dir: File) {
        if (dir.absolutePath.contains("/Android")) return

        val files = dir.listFiles() ?: return

        for(file in files){
            if(file.isDirectory){
                scanRecursive(file)
            } else {
                allFiles.add(file)
                scannedFiles++

                runOnUiThread {
                    val percent = (scannedFiles * 100 / (totalFiles + 1))
                    progressText.text = "Scanning: $percent%"
                }
            }
        }
    }

    private fun buildDuplicateMap() {
        for (file in allFiles) {
            val hash = getFileHash(file)
            if (hash.isNotEmpty()) {
                duplicateMap.getOrPut(hash) { mutableListOf() }.add(file)
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

                "Old Files" -> if(now - file.lastModified() > tenMin) displayFiles.add(file)

                "Large Files" -> if(file.length() > 50 * 1024 * 1024) displayFiles.add(file)

                "Duplicate Files" -> {
                    val hash = getFileHash(file)
                    if (duplicateMap[hash]?.size ?: 0 > 1) {
                        displayFiles.add(file)
                    }
                }

                "APK Files" -> if(file.name.endsWith(".apk")) displayFiles.add(file)

                "Temp Files" -> if(file.name.endsWith(".tmp") || file.name.endsWith(".log") || file.name.endsWith(".cache"))
                    displayFiles.add(file)
            }
        }

        // 🔥 SORTING
        if (sortBySize) {
            displayFiles.sortByDescending { it.length() }
        } else {
            displayFiles.sortByDescending { it.lastModified() }
        }

        fileCount.text = "${displayFiles.size} files found"
        showFiles(displayFiles)
    }

    private fun showStorageChart() {

        var images = 0f
        var videos = 0f
        var audio = 0f
        var others = 0f

        for (file in allFiles) {
            val name = file.name.lowercase()
            when {
                name.endsWith(".jpg") || name.endsWith(".png") -> images++
                name.endsWith(".mp4") -> videos++
                name.endsWith(".mp3") -> audio++
                else -> others++
            }
        }

        val entries = listOf(
            PieEntry(images, "Images"),
            PieEntry(videos, "Videos"),
            PieEntry(audio, "Audio"),
            PieEntry(others, "Others")
        )

        val dataSet = PieDataSet(entries, "Storage")
        pieChart.data = PieData(dataSet)
        pieChart.invalidate()
    }

    private fun getFileHash(file: File): String {
        return try {
            val digest = java.security.MessageDigest.getInstance("MD5")
            val buffer = ByteArray(1024)
            val input = file.inputStream()

            var read: Int
            while (input.read(buffer).also { read = it } != -1) {
                digest.update(buffer, 0, read)
            }

            digest.digest().joinToString("") { "%02x".format(it) }

        } catch (e: Exception) {
            ""
        }
    }

    private fun showFiles(files: List<File>){

        val adapter = object : BaseAdapter() {

            override fun getCount(): Int = files.size
            override fun getItem(position: Int): Any = files[position]
            override fun getItemId(position: Int): Long = position.toLong()

            override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {

                val file = files[position]

                val row = LinearLayout(this@SecondActivity)
                row.orientation = LinearLayout.HORIZONTAL
                row.setPadding(20,20,20,20)

                val checkBox = CheckBox(this@SecondActivity)
                checkBox.setOnCheckedChangeListener { _, isChecked ->
                    if(isChecked) selectedFiles.add(file)
                    else selectedFiles.remove(file)
                }

                val name = TextView(this@SecondActivity)
                name.text = "${file.name}\n${formatSize(file.length())}"
                name.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT,1f)

                val deleteBtn = Button(this@SecondActivity)
                deleteBtn.text = "Delete"
                deleteBtn.setOnClickListener {
                    file.delete()
                    applyFilter(filterSpinner.selectedItem.toString())
                }

                row.addView(checkBox)
                row.addView(name)
                row.addView(deleteBtn)

                return row
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
}