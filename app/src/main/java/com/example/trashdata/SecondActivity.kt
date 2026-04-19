package com.example.trashdata

import android.app.Activity
import android.os.Bundle
import android.content.BroadcastReceiver
import android.content.Context
import android.content.IntentFilter
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import android.os.Environment
import android.app.AlertDialog
import android.widget.*
import android.graphics.Color
import androidx.drawerlayout.widget.DrawerLayout
import androidx.core.view.GravityCompat
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.graphics.drawable.GradientDrawable
import java.io.File
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.text.Editable
import android.text.TextWatcher
import com.github.mikephil.charting.charts.PieChart
import com.github.mikephil.charting.data.*
import com.github.mikephil.charting.listener.OnChartValueSelectedListener
import com.github.mikephil.charting.highlight.Highlight
import com.github.mikephil.charting.formatter.PercentFormatter
import android.provider.Settings
import java.util.concurrent.Executors
import java.util.concurrent.ConcurrentHashMap

class SecondActivity : Activity() {
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var listView: ListView
    private lateinit var filterSpinner: Spinner
    private lateinit var deleteSelectedBtn: Button
    private lateinit var selectAllBtn: Button
    private lateinit var searchBar: EditText
    private lateinit var fileCount: TextView
    private lateinit var progressText: TextView
    private lateinit var pieChart: PieChart
    private lateinit var sortToggle: Button
    private lateinit var progressBar: ProgressBar
    private val allFiles      = mutableListOf<File>()
    private val displayFiles  = mutableListOf<File>()
    private val selectedFiles = mutableSetOf<File>()
    private val keywordCache = ConcurrentHashMap<String, List<String>>()
    private val aiExecutor   = Executors.newFixedThreadPool(3)
    private var sortBySize   = true
    private var initialFilter  = "All Files"
    private var resumedFromSettings = false
    // -1 = no age filter, otherwise days
    private var ageDayFilter = -1

    private val scanProgressReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val scanned = intent?.getIntExtra(FileScanWorker.EXTRA_SCANNED_FILES, 0) ?: 0
            val total   = intent?.getIntExtra(FileScanWorker.EXTRA_TOTAL_FILES, 1) ?: 1
            val percent = if (total > 0) (scanned * 100 / total) else 0
            runOnUiThread {
                progressText.text = "Scanning... $percent%"
                progressBar.visibility = View.VISIBLE
                progressBar.max = 100
                progressBar.progress = percent
                if (percent >= 100) {
                    progressText.text = "Scan Complete ✅"
                    progressBar.visibility = View.GONE
                    loadData()
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        initialFilter = intent.getStringExtra("filter") ?: "All Files"
        drawerLayout  = DrawerLayout(this)
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.WHITE)
            setPadding(40, 100, 40, 20)
            elevation = 8f
        }
        val headerRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val hamburger = TextView(this).apply {
            text = "☰"
            textSize = 28f
            setTextColor(Color.parseColor("#1A1A2E"))
            setTypeface(null, Typeface.BOLD)
            setPadding(0, 0, 24, 0)
            setOnClickListener { drawerLayout.openDrawer(GravityCompat.START) }
        }
        val titleGroup = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val title = TextView(this).apply {
            text = "Files Cleaner"
            setTextColor(Color.parseColor("#1A1A2E"))
            textSize = 20f
            setTypeface(null, Typeface.BOLD)
        }
        fileCount = TextView(this).apply {
            setTextColor(Color.parseColor("#6B7280"))
            textSize = 13f
        }
        titleGroup.addView(title)
        titleGroup.addView(fileCount)
        headerRow.addView(hamburger)
        headerRow.addView(titleGroup)
        header.addView(headerRow)
        progressText = TextView(this).apply {
            setTextColor(Color.parseColor("#4A90E2"))
            setPadding(0, 8, 0, 0)
        }
        progressBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal)
            .apply { max = 100 }
        header.addView(progressText)
        header.addView(progressBar)

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20, 20, 20, 20)
            setBackgroundColor(Color.parseColor("#F5F6FA"))
        }
        searchBar = EditText(this).apply {
            hint = "Search files..."
            setHintTextColor(Color.parseColor("#9CA3AF"))
            setTextColor(Color.parseColor("#1A1A2E"))
            background = GradientDrawable().apply {
                cornerRadius = 60f
                setColor(Color.WHITE)
                setStroke(2, Color.parseColor("#E5E7EB"))
            }
            setPadding(30, 20, 30, 20)
        }
        filterSpinner = Spinner(this).apply {
            val filters = arrayOf("All Files", "Duplicate Files", "Old Files", "Large Files")
            adapter = ArrayAdapter(this@SecondActivity,
                android.R.layout.simple_spinner_dropdown_item, filters)
            onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(p: AdapterView<*>, v: View?, pos: Int, id: Long) {
                    applyFilter(p.getItemAtPosition(pos).toString())
                }
                override fun onNothingSelected(p: AdapterView<*>) {}
            }
        }
        val spinnerAdapter = filterSpinner.adapter as ArrayAdapter<String>
        val spinnerPos = spinnerAdapter.getPosition(initialFilter)
        if (spinnerPos >= 0) filterSpinner.setSelection(spinnerPos)
        sortToggle = Button(this).apply {
            text = "Sort: Size"
            setTextColor(Color.parseColor("#1A1A2E"))
            setBackgroundColor(Color.WHITE)
        }
        deleteSelectedBtn = Button(this).apply {
            text = "Delete Selected"
            setBackgroundColor(Color.parseColor("#FF4757"))
            setTextColor(Color.WHITE)
        }
        selectAllBtn = Button(this).apply {
            text = "Select All"
            setBackgroundColor(Color.parseColor("#4A90E2"))
            setTextColor(Color.WHITE)
        }

        // Age filter row: All | 1d | 2d | 5d
        val ageFilterRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 8, 0, 4)
            tag = "ageFilterRow"
        }
        fun makeAgeChip(label: String, days: Int): TextView {
            return TextView(this).apply {
                text = label
                textSize = 12f
                gravity = Gravity.CENTER
                setPadding(24, 10, 24, 10)
                setTextColor(Color.parseColor("#4A90E2"))
                background = GradientDrawable().apply {
                    cornerRadius = 40f
                    setColor(Color.parseColor("#EBF3FF"))
                    setStroke(2, Color.parseColor("#4A90E2"))
                }
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { setMargins(0, 0, 14, 0) }
                setOnClickListener {
                    ageDayFilter = if (ageDayFilter == days) -1 else days
                    for (i in 0 until ageFilterRow.childCount) {
                        val chip = ageFilterRow.getChildAt(i) as? TextView ?: continue
                        val bg = chip.background as? GradientDrawable ?: continue
                        val isActive = chip.text == label && ageDayFilter != -1
                        bg.setColor(Color.parseColor(if (isActive) "#4A90E2" else "#EBF3FF"))
                        chip.setTextColor(Color.parseColor(if (isActive) "#FFFFFF" else "#4A90E2"))
                    }
                    applyFilter(filterSpinner.selectedItem.toString())
                }
            }
        }
        ageFilterRow.addView(TextView(this).apply {
            text = "Age: "
            textSize = 13f
            gravity = Gravity.CENTER_VERTICAL
            setTextColor(Color.parseColor("#6B7280"))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 0, 8, 0) }
        })
        ageFilterRow.addView(makeAgeChip("1 day", 1))
        ageFilterRow.addView(makeAgeChip("2 days", 2))
        ageFilterRow.addView(makeAgeChip("5 days", 5))
        pieChart = PieChart(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 600)
        }

        listView = ListView(this).apply {
            itemsCanFocus = true
        }

        val topSection = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, 0, 10)
        }
        topSection.addView(pieChart)
        topSection.addView(searchBar)
        topSection.addView(filterSpinner)
        topSection.addView(ageFilterRow)
        topSection.addView(sortToggle)
        val actionRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT)
        }
        actionRow.addView(selectAllBtn, LinearLayout.LayoutParams(0,
            LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        actionRow.addView(deleteSelectedBtn, LinearLayout.LayoutParams(0,
            LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { setMargins(8, 0, 0, 0) })
        topSection.addView(actionRow)

        val scrollView = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT)
            isVerticalScrollBarEnabled = false
        }
        scrollView.addView(topSection)

        container.addView(scrollView)
        container.addView(listView, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))

        val mainContent = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#F5F6FA"))
            layoutParams = DrawerLayout.LayoutParams(
                DrawerLayout.LayoutParams.MATCH_PARENT,
                DrawerLayout.LayoutParams.MATCH_PARENT)
            addView(header)
            addView(container, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        }

        val drawerMenu = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.WHITE)
            layoutParams = DrawerLayout.LayoutParams(
                600, DrawerLayout.LayoutParams.MATCH_PARENT).apply {
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
            text = "TrashData"; textSize = 20f
            setTypeface(null, Typeface.BOLD); setTextColor(Color.WHITE)
        })
        drawerHeader.addView(TextView(this).apply {
            text = "File Cleaner"; textSize = 12f
            setTextColor(Color.parseColor("#BBDEFB"))
        })
        drawerMenu.addView(drawerHeader)

        fun drawerItem(label: String, onClick: () -> Unit) = TextView(this).apply {
            text = label; textSize = 15f
            setTextColor(Color.parseColor("#1A1A2E"))
            setPadding(40, 40, 40, 40)
            setOnClickListener { onClick() }
        }
        drawerMenu.addView(drawerItem("🧹  Cleaner") {
            startActivity(Intent(this, MainActivity::class.java))
            drawerLayout.closeDrawer(GravityCompat.START)
        })
        drawerMenu.addView(drawerItem("📁  Files") {
            drawerLayout.closeDrawer(GravityCompat.START)
        })

        drawerLayout.addView(mainContent)
        drawerLayout.addView(drawerMenu)
        setContentView(drawerLayout)

        LocalBroadcastManager.getInstance(this).registerReceiver(
            scanProgressReceiver, IntentFilter(FileScanWorker.ACTION_PROGRESS))

        loadData()
        showStorageChart()

        if (FileRepository.junkFiles.isEmpty())
            sortToggle.setOnClickListener {
                sortBySize = !sortBySize
                sortToggle.text = if (sortBySize) "Sort: Size" else "Sort: Date"
                applyFilter(filterSpinner.selectedItem.toString())
                showStorageChart()
            }

        selectAllBtn.setOnClickListener {
            if (selectedFiles.size == displayFiles.size) {
                // all already selected → deselect all
                selectedFiles.clear()
                selectAllBtn.text = "Select All"
            } else {
                selectedFiles.addAll(displayFiles)
                selectAllBtn.text = "Deselect All"
            }
            // refresh list to update checkboxes
            showFiles(displayFiles)
        }

        searchBar.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                val q = s.toString().lowercase()
                showFiles(displayFiles.filter { it.name.lowercase().contains(q) })
            }
            override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
            override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {}
        })

        deleteSelectedBtn.setOnClickListener {
            if (selectedFiles.isEmpty()) {
                Toast.makeText(this, "No files selected", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            try {
                AlertDialog.Builder(this)
                    .setTitle("Delete Selected Files")
                    .setMessage("Are you sure you want to delete ${selectedFiles.size} files?")
                    .setPositiveButton("Delete") { _, _ ->
                        var deletedCount = 0
                        val toDelete = selectedFiles.toList()
                        for (f in toDelete) {
                            if (f.exists() && f.delete()) {
                                deletedCount++
                                allFiles.remove(f)
                            }
                        }
                        Toast.makeText(this, "Deleted $deletedCount files", Toast.LENGTH_SHORT).show()
                        selectedFiles.clear()
                        applyFilter(filterSpinner.selectedItem.toString())
                        showStorageChart()
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            } catch (e: Exception) {
                Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun requestAllFilesPermission() {
        Toast.makeText(this, "Please grant full storage access", Toast.LENGTH_SHORT).show()
        startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
        resumedFromSettings = true
    }

    override fun onRequestPermissionsResult(rc: Int, perms: Array<out String>, results: IntArray) {
        super.onRequestPermissionsResult(rc, perms, results)
        if (rc == 100 && results.isNotEmpty() && results[0] == PackageManager.PERMISSION_GRANTED)
            loadData()
    }

    override fun onDestroy() {
        super.onDestroy()
        LocalBroadcastManager.getInstance(this).unregisterReceiver(scanProgressReceiver)
        aiExecutor.shutdownNow()
    }

    override fun onResume() {
        super.onResume()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
            !Environment.isExternalStorageManager()) {
            requestAllFilesPermission(); return
        }
        if (resumedFromSettings) {
            resumedFromSettings = false
            if (Environment.isExternalStorageManager()) loadData()
            else Toast.makeText(this, "Permission not granted.", Toast.LENGTH_SHORT).show()
        }
    }
    private fun loadData() {
        allFiles.clear()
        allFiles.addAll(FileRepository.junkFiles)
        FileRepository.buildDuplicateMap()
        applyFilter(initialFilter)
        showStorageChart()
    }
    private fun showStorageChart() {
        var images = 0f; var videos = 0f; var audio = 0f
        var documents = 0f; var apk = 0f; var archives = 0f; var others = 0f

        for (f in allFiles) {
            val n = f.name.lowercase()
            when {
                n.endsWith(".jpg")||n.endsWith(".jpeg")||n.endsWith(".png") -> images    += f.length()
                n.endsWith(".mp4")||n.endsWith(".mkv")||n.endsWith(".avi")  -> videos    += f.length()
                n.endsWith(".mp3")||n.endsWith(".wav")                      -> audio     += f.length()
                n.endsWith(".pdf")||n.endsWith(".doc")||
                        n.endsWith(".docx")||n.endsWith(".txt")                     -> documents += f.length()
                n.endsWith(".apk")                                          -> apk       += f.length()
                n.endsWith(".zip")||n.endsWith(".rar")                      -> archives  += f.length()
                else                                                        -> others    += f.length()
            }
        }

        val entries = arrayListOf<PieEntry>()
        if (documents > 0) entries.add(PieEntry(documents, "Documents"))
        if (apk      > 0) entries.add(PieEntry(apk,        "APK"))
        if (archives > 0) entries.add(PieEntry(archives,   "Archives"))
        if (videos   > 0) entries.add(PieEntry(videos,     "Videos"))
        if (images   > 0) entries.add(PieEntry(images,     "Images"))
        if (audio    > 0) entries.add(PieEntry(audio,      "Audio"))
        if (others   > 0) entries.add(PieEntry(others,     "Others"))

        val dataSet = PieDataSet(entries, "").apply {
            colors = listOf(
                Color.parseColor("#4A90E2"), Color.parseColor("#F5A623"),
                Color.parseColor("#7ED321"), Color.parseColor("#9B59B6"),
                Color.parseColor("#E74C3C"), Color.parseColor("#1ABC9C"),
                Color.parseColor("#BDC3C7"))
        }
        val data = PieData(dataSet).apply { setValueFormatter(PercentFormatter(pieChart)) }

        pieChart.apply {
            this.data = data
            setUsePercentValues(true)
            description.isEnabled = false
            legend.isEnabled = true
            centerText = getTotalStorage()
            animateY(1200)
        }

        pieChart.setOnChartValueSelectedListener(object : OnChartValueSelectedListener {
            override fun onValueSelected(e: Entry?, h: Highlight?) {
                val label = (e as PieEntry).label
                val filtered = allFiles.filter {
                    val n = it.name.lowercase()
                    when (label) {
                        "Documents" -> n.endsWith(".pdf")||n.endsWith(".doc")||n.endsWith(".txt")
                        "APK"       -> n.endsWith(".apk")
                        "Archives"  -> n.endsWith(".zip")||n.endsWith(".rar")
                        "Videos"    -> n.endsWith(".mp4")||n.endsWith(".mkv")
                        "Images"    -> n.endsWith(".jpg")||n.endsWith(".png")
                        "Audio"     -> n.endsWith(".mp3")
                        else        -> true
                    }
                }
                displayFiles.clear(); displayFiles.addAll(filtered)
                showFiles(displayFiles)
            }
            override fun onNothingSelected() {}
        })
        pieChart.invalidate()
    }

    private fun getTotalStorage(): String = formatSize(allFiles.sumOf { it.length() })
    private fun applyFilter(type: String) {
        val filtered = FileFilters.filterFiles(
            allFiles, type,
            FileRepository.duplicateMap,
            FileRepository.fileHashMap,
            sortBySize,
            ageDayFilter)
        displayFiles.clear(); displayFiles.addAll(filtered)
        val ageLabel = if (ageDayFilter > 0) ", older than $ageDayFilter day(s)" else ""
        fileCount.text = "${displayFiles.size} files$ageLabel"
        selectAllBtn.text = "Select All"
        showFiles(displayFiles)
    }

    private fun showFiles(files: List<File>) {
        val adapter = object : BaseAdapter() {
            override fun getCount()             = files.size
            override fun getItem(pos: Int): Any = files[pos]
            override fun getItemId(pos: Int)    = pos.toLong()

            override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
                val file = files[position]

                val card = LinearLayout(this@SecondActivity).apply {
                    orientation = LinearLayout.VERTICAL
                    descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS
                    setPadding(20, 20, 20, 20)
                    background = GradientDrawable().apply {
                        cornerRadius = 25f
                        setColor(Color.WHITE)
                        setStroke(2, Color.parseColor("#E5E7EB"))
                    }
                    elevation = 2f
                }

                val topRow = LinearLayout(this@SecondActivity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                }

                val checkBox = CheckBox(this@SecondActivity).apply {
                    isFocusable = false
                    isFocusableInTouchMode = false
                    setOnCheckedChangeListener(null)
                    isChecked = selectedFiles.contains(file)
                    setOnCheckedChangeListener { _, checked ->
                        if (checked) selectedFiles.add(file) else selectedFiles.remove(file)
                    }
                }

                val nameView = TextView(this@SecondActivity).apply {
                    text = "${file.name}\n${formatSize(file.length())}"
                    setTextColor(Color.parseColor("#1A1A2E"))
                    layoutParams = LinearLayout.LayoutParams(
                        0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                }

                val deleteBtn = Button(this@SecondActivity).apply {
                    text = "Delete"
                    setTextColor(Color.WHITE)
                    setBackgroundColor(Color.parseColor("#FF4757"))
                    isFocusable = true
                    isFocusableInTouchMode = false
                    isClickable = true

                    setOnClickListener {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
                            !Environment.isExternalStorageManager()) {
                            Toast.makeText(
                                this@SecondActivity,
                                "Grant 'All Files Access' to delete files",
                                Toast.LENGTH_LONG
                            ).show()
                            startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
                            return@setOnClickListener
                        }
                        try {
                            AlertDialog.Builder(this@SecondActivity)
                                .setTitle("Delete File")
                                .setMessage("Are you sure you want to delete ${file.name}?")
                                .setPositiveButton("Delete") { _, _ ->
                                    val deleted = try { file.delete() } catch (e: Exception) { false }
                                    if (deleted) {
                                        allFiles.remove(file)
                                        selectedFiles.remove(file)
                                        Toast.makeText(
                                            this@SecondActivity,
                                            "${file.name} deleted",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                        applyFilter(filterSpinner.selectedItem.toString())
                                        showStorageChart()
                                    } else {
                                        Toast.makeText(
                                            this@SecondActivity,
                                            "Delete failed (no permission or system file)",
                                            Toast.LENGTH_LONG
                                        ).show()
                                    }
                                }
                                .setNegativeButton("Cancel", null)
                                .show()
                        } catch (e: Exception) {
                            Toast.makeText(
                                this@SecondActivity,
                                "Error showing dialog: ${e.message}",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                }

                topRow.addView(checkBox)
                topRow.addView(nameView)
                topRow.addView(deleteBtn)
                card.addView(topRow)
                val chipRow = LinearLayout(this@SecondActivity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    setPadding(0, 10, 0, 0)
                }
                chipRow.addView(TextView(this@SecondActivity).apply {
                    text = "AI ✨ "
                    textSize = 11f
                    setTextColor(Color.parseColor("#9CA3AF"))
                })
                val loadingText = TextView(this@SecondActivity).apply {
                    text = "analyzing..."
                    textSize = 11f
                    setTextColor(Color.parseColor("#9CA3AF"))
                }
                chipRow.addView(loadingText)
                card.addView(chipRow)

                val cached = keywordCache[file.absolutePath]
                if (cached != null) {
                    loadingText.visibility = View.GONE
                    addChips(chipRow, cached)
                } else {
                    aiExecutor.submit {
                        val tags = KeywordAnalyzer.getKeywords(file, applicationContext)
                        keywordCache[file.absolutePath] = tags
                        runOnUiThread {
                            loadingText.visibility = View.GONE
                            addChips(chipRow, tags)
                        }
                    }
                }

                return card
            }
        }
        listView.adapter = adapter
    }
    private fun addChips(row: LinearLayout, tags: List<String>) {
        val colours = listOf(
            "#4A90E2" to "#EBF3FF",
            "#7ED321" to "#F0FAE3",
            "#F5A623" to "#FEF6E6",
            "#9B59B6" to "#F5EEF8",
            "#E74C3C" to "#FDEDEC",
            "#1ABC9C" to "#E8F8F5"
        )
        tags.forEachIndexed { i, tag ->
            val (textColour, bgColour) = colours[i % colours.size]
            row.addView(TextView(this).apply {
                text = tag
                textSize = 11f
                setTextColor(Color.parseColor(textColour))
                background = GradientDrawable().apply {
                    cornerRadius = 40f
                    setColor(Color.parseColor(bgColour))
                }
                setPadding(18, 6, 18, 6)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { setMargins(0, 0, 10, 0) }
            })
        }
    }
    private fun formatSize(size: Long): String {
        val kb = size / 1024; val mb = kb / 1024; val gb = mb / 1024
        return when { gb > 0 -> "$gb GB"; mb > 0 -> "$mb MB"; else -> "$kb KB" }
    }
}