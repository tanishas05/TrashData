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
    private lateinit var sortSpinner: Spinner
    private lateinit var ageSpinner: Spinner
    private lateinit var filterSizeText: TextView
    private lateinit var progressBar: ProgressBar
    private val allFiles      = mutableListOf<File>()
    private val displayFiles  = mutableListOf<File>()
    private val selectedFiles = mutableSetOf<File>()
    private val keywordCache = ConcurrentHashMap<String, List<String>>()
    private val aiExecutor   = Executors.newFixedThreadPool(3)
    private var sortMode    = FileFilters.SortMode.SIZE_HIGH_LOW
    private var initialFilter  = "All Files"
    private var resumedFromSettings = false
    private var ageDayFilter = -1

    // ── file type helpers ─────────────────────────────────────────────────────
    private fun isImage(n: String)    = n.endsWith(".jpg")||n.endsWith(".jpeg")||n.endsWith(".png")||
            n.endsWith(".gif")||n.endsWith(".webp")||n.endsWith(".bmp")||
            n.endsWith(".heic")||n.endsWith(".heif")||n.endsWith(".tiff")||
            n.endsWith(".raw")||n.endsWith(".svg")||n.endsWith(".ico")
    private fun isVideo(n: String)    = n.endsWith(".mp4")||n.endsWith(".mkv")||n.endsWith(".avi")||
            n.endsWith(".mov")||n.endsWith(".3gp")||n.endsWith(".3g2")||
            n.endsWith(".webm")||n.endsWith(".flv")||n.endsWith(".wmv")||
            n.endsWith(".m4v")||n.endsWith(".ts")||n.endsWith(".mpeg")||
            n.endsWith(".mpg")
    private fun isAudio(n: String)    = n.endsWith(".mp3")||n.endsWith(".wav")||n.endsWith(".aac")||
            n.endsWith(".flac")||n.endsWith(".ogg")||n.endsWith(".m4a")||
            n.endsWith(".wma")||n.endsWith(".opus")||n.endsWith(".amr")||
            n.endsWith(".mid")||n.endsWith(".midi")
    private fun isDocument(n: String) = n.endsWith(".pdf")||n.endsWith(".doc")||n.endsWith(".docx")||
            n.endsWith(".txt")||n.endsWith(".xls")||n.endsWith(".xlsx")||
            n.endsWith(".ppt")||n.endsWith(".pptx")||n.endsWith(".odt")||
            n.endsWith(".ods")||n.endsWith(".odp")||n.endsWith(".csv")||
            n.endsWith(".rtf")||n.endsWith(".epub")||n.endsWith(".pages")||
            n.endsWith(".numbers")||n.endsWith(".key")
    private fun isArchive(n: String)  = n.endsWith(".zip")||n.endsWith(".rar")||n.endsWith(".7z")||
            n.endsWith(".tar")||n.endsWith(".gz")||n.endsWith(".bz2")||
            n.endsWith(".xz")||n.endsWith(".cab")||n.endsWith(".iso")
    private fun isApk(n: String)      = n.endsWith(".apk")||n.endsWith(".apks")||n.endsWith(".xapk")||
            n.endsWith(".aab")
    private fun isCode(n: String)     = n.endsWith(".json")||n.endsWith(".xml")||n.endsWith(".html")||
            n.endsWith(".htm")||n.endsWith(".js")||n.endsWith(".css")||
            n.endsWith(".sql")||n.endsWith(".db")||n.endsWith(".sqlite")||
            n.endsWith(".log")

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
                    val type = p.getItemAtPosition(pos).toString()
                    applyFilter(type)
                    val now = System.currentTimeMillis()
                    val sizeBytes = allFiles.filter { f ->
                        when (type) {
                            "Duplicate Files" -> (FileRepository.duplicateMap[FileRepository.fileHashMap[f] ?: ""]?.size ?: 0) > 1
                            "Old Files"       -> now - f.lastModified() > 15L * 60 * 1000L
                            "Large Files"     -> f.length() >= 1 * 1024 * 1024L
                            else              -> true
                        }
                    }.sumOf { it.length() }
                    filterSizeText.text = "Space used: ${formatSize(sizeBytes)}"
                }
                override fun onNothingSelected(p: AdapterView<*>) {}
            }
        }
        filterSizeText = TextView(this).apply {
            setTextColor(Color.parseColor("#4A90E2"))
            textSize = 12f
            setPadding(8, 2, 8, 6)
        }
        val spinnerAdapter = filterSpinner.adapter as ArrayAdapter<String>
        val spinnerPos = spinnerAdapter.getPosition(initialFilter)
        if (spinnerPos >= 0) filterSpinner.setSelection(spinnerPos)

        ageSpinner = Spinner(this).apply {
            val ageOptions = arrayOf("Any Age", "1 day", "2 days", "5 days", "7 days", "10 days", "15 days")
            adapter = ArrayAdapter(this@SecondActivity,
                android.R.layout.simple_spinner_dropdown_item, ageOptions)
            onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(p: AdapterView<*>, v: View?, pos: Int, id: Long) {
                    ageDayFilter = when (pos) {
                        1 -> 1; 2 -> 2; 3 -> 5; 4 -> 7; 5 -> 10; 6 -> 15
                        else -> -1
                    }
                    applyFilter(filterSpinner.selectedItem.toString())
                }
                override fun onNothingSelected(p: AdapterView<*>) {}
            }
        }

        sortSpinner = Spinner(this).apply {
            val sortOptions = arrayOf("Size ↓ High→Low", "Size ↑ Low→High", "Date ↓ Newest", "Date ↑ Oldest")
            adapter = ArrayAdapter(this@SecondActivity,
                android.R.layout.simple_spinner_dropdown_item, sortOptions)
            onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(p: AdapterView<*>, v: View?, pos: Int, id: Long) {
                    sortMode = when (pos) {
                        0 -> FileFilters.SortMode.SIZE_HIGH_LOW
                        1 -> FileFilters.SortMode.SIZE_LOW_HIGH
                        2 -> FileFilters.SortMode.DATE_NEWEST
                        3 -> FileFilters.SortMode.DATE_OLDEST
                        else -> FileFilters.SortMode.SIZE_HIGH_LOW
                    }
                    applyFilter(filterSpinner.selectedItem.toString())
                    showStorageChart()
                }
                override fun onNothingSelected(p: AdapterView<*>) {}
            }
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
        topSection.addView(filterSizeText)
        val ageAndSortRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT)
        }
        ageAndSortRow.addView(ageSpinner, LinearLayout.LayoutParams(0,
            LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        ageAndSortRow.addView(sortSpinner, LinearLayout.LayoutParams(0,
            LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { setMargins(8, 0, 0, 0) })
        topSection.addView(ageAndSortRow)
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
        drawerMenu.addView(drawerItem("🗑  Recycle Bin") {
            startActivity(Intent(this, RecycleBinActivity::class.java))
            drawerLayout.closeDrawer(GravityCompat.START)
        })

        drawerLayout.addView(mainContent)
        drawerLayout.addView(drawerMenu)
        setContentView(drawerLayout)

        LocalBroadcastManager.getInstance(this).registerReceiver(
            scanProgressReceiver, IntentFilter(FileScanWorker.ACTION_PROGRESS))

        loadData()
        showStorageChart()

        selectAllBtn.setOnClickListener {
            if (selectedFiles.size == displayFiles.size) {
                selectedFiles.clear()
                selectAllBtn.text = "Select All"
            } else {
                selectedFiles.addAll(displayFiles)
                selectAllBtn.text = "Deselect All"
            }
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
                            if (RecycleBin.moveToTrash(this, f)) {
                                deletedCount++
                                allFiles.remove(f)
                                FileRepository.removeFile(f)
                            }
                        }
                        Toast.makeText(this, "Moved $deletedCount files to Recycle Bin", Toast.LENGTH_SHORT).show()
                        selectedFiles.clear()
                        applyFilter(filterSpinner.selectedItem.toString())
                        showStorageChart()
                        broadcastFilesChanged()
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

    private fun showStorageChart(filesToChart: List<File> = displayFiles) {
        var images = 0f; var videos = 0f; var audio = 0f
        var documents = 0f; var apk = 0f; var archives = 0f
        var code = 0f; var others = 0f

        for (f in filesToChart) {
            val n = f.name.lowercase()
            when {
                isImage(n)    -> images    += f.length()
                isVideo(n)    -> videos    += f.length()
                isAudio(n)    -> audio     += f.length()
                isDocument(n) -> documents += f.length()
                isApk(n)      -> apk       += f.length()
                isArchive(n)  -> archives  += f.length()
                isCode(n)     -> code      += f.length()
                else          -> others    += f.length()
            }
        }

        val entries = arrayListOf<PieEntry>()
        if (images    > 0) entries.add(PieEntry(images,    "Images"))
        if (videos    > 0) entries.add(PieEntry(videos,    "Videos"))
        if (audio     > 0) entries.add(PieEntry(audio,     "Audio"))
        if (documents > 0) entries.add(PieEntry(documents, "Documents"))
        if (apk       > 0) entries.add(PieEntry(apk,       "APK"))
        if (archives  > 0) entries.add(PieEntry(archives,  "Archives"))
        if (code      > 0) entries.add(PieEntry(code,      "Code/Data"))
        if (others    > 0) entries.add(PieEntry(others,    "Others"))

        val dataSet = PieDataSet(entries, "").apply {
            colors = listOf(
                Color.parseColor("#E74C3C"), // Images   - red
                Color.parseColor("#9B59B6"), // Videos   - purple
                Color.parseColor("#1ABC9C"), // Audio    - teal
                Color.parseColor("#4A90E2"), // Docs     - blue
                Color.parseColor("#F5A623"), // APK      - amber
                Color.parseColor("#7ED321"), // Archives - green
                Color.parseColor("#34495E"), // Code     - dark
                Color.parseColor("#BDC3C7")  // Others   - grey
            )
        }
        val data = PieData(dataSet).apply { setValueFormatter(PercentFormatter(pieChart)) }

        pieChart.apply {
            this.data = data
            setUsePercentValues(true)
            description.isEnabled = false
            legend.isEnabled = true
            centerText = formatSize(filesToChart.sumOf { it.length() })
            animateY(1200)
        }

        pieChart.setOnChartValueSelectedListener(object : OnChartValueSelectedListener {
            override fun onValueSelected(e: Entry?, h: Highlight?) {
                val label = (e as PieEntry).label
                val filtered = displayFiles.filter {
                    val n = it.name.lowercase()
                    when (label) {
                        "Images"    -> isImage(n)
                        "Videos"    -> isVideo(n)
                        "Audio"     -> isAudio(n)
                        "Documents" -> isDocument(n)
                        "APK"       -> isApk(n)
                        "Archives"  -> isArchive(n)
                        "Code/Data" -> isCode(n)
                        else        -> !isImage(n) && !isVideo(n) && !isAudio(n) &&
                                !isDocument(n) && !isApk(n) && !isArchive(n) && !isCode(n)
                    }
                }
                filterSizeText.text = "$label: ${formatSize(filtered.sumOf { it.length() })}"
                pieChart.centerText = "$label\n${formatSize(filtered.sumOf { it.length() })}"
                pieChart.invalidate()
                showFiles(filtered)
            }
            override fun onNothingSelected() {
                filterSizeText.text = ""
                pieChart.centerText = formatSize(displayFiles.sumOf { it.length() })
                pieChart.invalidate()
                showFiles(displayFiles)
            }
        })
        pieChart.invalidate()
    }

    private fun getTotalStorage(): String = formatSize(allFiles.sumOf { it.length() })

    private fun applyFilter(type: String) {
        val filtered = FileFilters.filterFiles(
            allFiles, type,
            FileRepository.duplicateMap,
            FileRepository.fileHashMap,
            ageDays = ageDayFilter,
            sortMode = sortMode)
        displayFiles.clear(); displayFiles.addAll(filtered)
        val ageLabel = if (ageDayFilter > 0) ", older than $ageDayFilter day(s)" else ""
        fileCount.text = "${displayFiles.size} files$ageLabel"
        selectAllBtn.text = "Select All"
        showFiles(displayFiles)
        showStorageChart(displayFiles)
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
                    val ageMs = System.currentTimeMillis() - file.lastModified()
                    val ageStr = when {
                        ageMs < 60 * 60 * 1000L         -> "${ageMs / (60 * 1000L)} min ago"
                        ageMs < 24 * 60 * 60 * 1000L    -> "${ageMs / (60 * 60 * 1000L)} hr ago"
                        else                             -> "${ageMs / (24 * 60 * 60 * 1000L)} days ago"
                    }
                    text = "${file.name}\n${formatSize(file.length())}  •  $ageStr"
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
                                    val moved = RecycleBin.moveToTrash(this@SecondActivity, file)
                                    if (moved) {
                                        allFiles.remove(file)
                                        selectedFiles.remove(file)
                                        FileRepository.removeFile(file)
                                        Toast.makeText(
                                            this@SecondActivity,
                                            "${file.name} moved to Recycle Bin",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                        applyFilter(filterSpinner.selectedItem.toString())
                                        showStorageChart()
                                        broadcastFilesChanged()
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

    /** Notify MainActivity (and any other listener) that the file list has changed. */
    private fun broadcastFilesChanged() {
        LocalBroadcastManager.getInstance(this)
            .sendBroadcast(Intent(FileScanWorker.ACTION_FILES_CHANGED))
    }
}