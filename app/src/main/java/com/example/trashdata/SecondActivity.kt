package com.example.trashdata

import android.app.Activity
import android.os.Bundle
import android.os.Environment
import androidx.appcompat.app.AlertDialog
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

class SecondActivity : Activity() {

    private lateinit var drawerLayout: DrawerLayout
    private lateinit var listView: ListView
    private lateinit var filterSpinner: Spinner
    private lateinit var deleteSelectedBtn: Button
    private lateinit var searchBar: EditText
    private lateinit var fileCount: TextView
    private lateinit var progressText: TextView
    private lateinit var pieChart: PieChart
    private lateinit var sortToggle: Button
    private lateinit var progressBar: ProgressBar
    private val allFiles = mutableListOf<File>()
    private val displayFiles = mutableListOf<File>()

    private val selectedFiles = mutableSetOf<File>()
    private var scannedFiles = 0
    private var totalFiles = 0
    private var sortBySize = true
    private var initialFilter: String = "All Files"

    private var resumedFromSettings = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        initialFilter = intent.getStringExtra("filter") ?: "All Files"

        drawerLayout = DrawerLayout(this)

        // ================= HEADER =================
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.WHITE)                    // UI: white instead of blue
            setPadding(40, 100, 40, 40)
            gravity = Gravity.CENTER
            elevation = 8f                                     // UI: subtle shadow
        }

        val hamburger = TextView(this).apply {
            text = "☰"
            textSize = 28f
            setTextColor(Color.parseColor("#1A1A2E"))          // UI: dark icon
            setTypeface(null, Typeface.BOLD)
            setOnClickListener { drawerLayout.openDrawer(GravityCompat.START) }
        }
        header.addView(hamburger, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { gravity = Gravity.START })

        val title = TextView(this).apply {
            text = "Files Cleaner"
            setTextColor(Color.parseColor("#1A1A2E"))          // UI: dark text
            textSize = 22f
            setTypeface(null, Typeface.BOLD)
        }

        fileCount = TextView(this).apply {
            setTextColor(Color.parseColor("#6B7280"))          // UI: muted grey
        }
        progressText = TextView(this).apply {
            setTextColor(Color.parseColor("#4A90E2"))          // UI: accent blue
        }
        progressBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply { max = 100 }

        header.addView(title)
        header.addView(fileCount)
        header.addView(progressText)
        header.addView(progressBar)

        // ================= CONTENT =================
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20, 20, 20, 20)
            setBackgroundColor(Color.parseColor("#F5F6FA"))    // UI: light grey page bg
        }

        searchBar = EditText(this).apply {
            hint = "Search files..."
            setHintTextColor(Color.parseColor("#9CA3AF"))      // UI: lighter hint
            setTextColor(Color.parseColor("#1A1A2E"))          // UI: dark input text
            background = GradientDrawable().apply {
                cornerRadius = 60f
                setColor(Color.WHITE)                          // UI: white search bg
                setStroke(2, Color.parseColor("#E5E7EB"))      // UI: light border
            }
            setPadding(30, 20, 30, 20)
        }

        filterSpinner = Spinner(this).apply {
            val filters = arrayOf("All Files", "Duplicate Files", "Old Files", "Large Files")
            adapter = ArrayAdapter(this@SecondActivity, android.R.layout.simple_spinner_dropdown_item, filters)
            onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                    applyFilter(parent.getItemAtPosition(position).toString())
                }
                override fun onNothingSelected(parent: AdapterView<*>) {}
            }
        }
        val spinnerAdapter = filterSpinner.adapter as ArrayAdapter<String>
        val spinnerPosition = spinnerAdapter.getPosition(initialFilter)
        if (spinnerPosition >= 0) filterSpinner.setSelection(spinnerPosition)

        sortToggle = Button(this).apply {
            text = "Sort: Size"
            setTextColor(Color.parseColor("#1A1A2E"))          // UI: dark text
            setBackgroundColor(Color.WHITE)                    // UI: white button
        }

        deleteSelectedBtn = Button(this).apply {
            text = "Delete Selected"
            setBackgroundColor(Color.parseColor("#FF4757"))    // UI: cleaner red
            setTextColor(Color.WHITE)
        }

        pieChart = PieChart(this).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 600)
        }

        listView = ListView(this)

        container.addView(pieChart)
        container.addView(searchBar)
        container.addView(filterSpinner)
        container.addView(sortToggle)
        container.addView(deleteSelectedBtn)
        container.addView(listView)

        // ================= MAIN CONTENT =================
        val mainContent = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#F5F6FA"))    // UI: light grey bg
            addView(header)
            addView(container, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        }

        // ================= DRAWER MENU =================
        val drawerMenu = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.WHITE)                    // UI: white drawer
            layoutParams = DrawerLayout.LayoutParams(600, DrawerLayout.LayoutParams.MATCH_PARENT).apply {
                gravity = GravityCompat.START
            }
            elevation = 16f
        }

        // UI: blue drawer header strip
        val drawerHeader = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#4A90E2"))
            setPadding(40, 120, 40, 40)
        }
        drawerHeader.addView(TextView(this).apply {
            text = "TrashData"
            textSize = 20f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
        })
        drawerHeader.addView(TextView(this).apply {
            text = "File Cleaner"
            textSize = 12f
            setTextColor(Color.parseColor("#BBDEFB"))
        })
        drawerMenu.addView(drawerHeader)

        val menuCleaner = TextView(this).apply {
            text = "🧹  Cleaner"
            textSize = 15f
            setTextColor(Color.parseColor("#1A1A2E"))          // UI: dark text
            setPadding(40, 40, 40, 40)
            setOnClickListener {
                startActivity(Intent(this@SecondActivity, MainActivity::class.java))
                drawerLayout.closeDrawer(GravityCompat.START)
            }
        }
        val menuFiles = TextView(this).apply {
            text = "📁  Files"
            textSize = 15f
            setTextColor(Color.parseColor("#1A1A2E"))
            setPadding(40, 40, 40, 40)
            setOnClickListener { drawerLayout.closeDrawer(GravityCompat.START) }
        }
        val menuSettings = TextView(this).apply {
            text = "⚙️  Settings"
            textSize = 15f
            setTextColor(Color.parseColor("#1A1A2E"))
            setPadding(40, 40, 40, 40)
            setOnClickListener {
                Toast.makeText(this@SecondActivity, "Settings clicked", Toast.LENGTH_SHORT).show()
                drawerLayout.closeDrawer(GravityCompat.START)
            }
        }

        drawerMenu.addView(menuCleaner)
        drawerMenu.addView(menuFiles)
        drawerMenu.addView(menuSettings)

        drawerLayout.addView(mainContent)
        drawerLayout.addView(drawerMenu)
        setContentView(drawerLayout)

        loadData()
        showStorageChart()

        if (FileRepository.junkFiles.isEmpty()) {
            Toast.makeText(this, "Run scan first", Toast.LENGTH_SHORT).show()
        }


        sortToggle.setOnClickListener {
            sortBySize = !sortBySize
            sortToggle.text = if (sortBySize) "Sort: Size" else "Sort: Date"
            applyFilter(filterSpinner.selectedItem.toString())
        }

        searchBar.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                val q = s.toString().lowercase()
                showFiles(displayFiles.filter { it.name.lowercase().contains(q) })
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        deleteSelectedBtn.setOnClickListener {
            for (file in selectedFiles) if (file.exists()) file.delete()
            Toast.makeText(this, "Deleted ${selectedFiles.size} files", Toast.LENGTH_SHORT).show()
            selectedFiles.clear()
            applyFilter(filterSpinner.selectedItem.toString())
        }
    }

    private fun requestAllFilesPermission() {
        Toast.makeText(this, "Please grant full storage access", Toast.LENGTH_SHORT).show()
        val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
        startActivity(intent)
        resumedFromSettings = true
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == 100 &&
            grantResults.isNotEmpty() &&
            grantResults[0] == PackageManager.PERMISSION_GRANTED
        ) {
            loadData()
        }
    }

    override fun onResume() {
        super.onResume()

        if (resumedFromSettings) {
            resumedFromSettings = false

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
                Environment.isExternalStorageManager()
            ) {
                loadData()

            } else {
                Toast.makeText(
                    this,
                    "Permission not granted. Scan cannot proceed.",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun loadData() {
        allFiles.clear()
        allFiles.addAll(FileRepository.junkFiles)

        FileRepository.buildDuplicateMap()

        applyFilter(initialFilter)
    }
    private fun showStorageChart() {
        var images = 0f; var videos = 0f; var audio = 0f
        var documents = 0f; var apk = 0f; var archives = 0f; var others = 0f

        for (f in allFiles) {
            val n = f.name.lowercase()
            when {
                n.endsWith(".jpg") || n.endsWith(".jpeg") || n.endsWith(".png") ->
                    images += f.length().toFloat()
                n.endsWith(".mp4") || n.endsWith(".mkv") || n.endsWith(".avi") ->
                    videos += f.length().toFloat()
                n.endsWith(".mp3") || n.endsWith(".wav") ->
                    audio += f.length().toFloat()
                n.endsWith(".pdf") || n.endsWith(".doc") || n.endsWith(".docx") || n.endsWith(".txt") ->
                    documents += f.length().toFloat()
                n.endsWith(".apk") ->
                    apk += f.length().toFloat()
                n.endsWith(".zip") || n.endsWith(".rar") ->
                    archives += f.length().toFloat()
                else ->
                    others += f.length().toFloat()
            }
        }

        val entries = arrayListOf<PieEntry>()
        if (documents > 0) entries.add(PieEntry(documents, "Documents"))
        if (apk > 0) entries.add(PieEntry(apk, "APK"))
        if (archives > 0) entries.add(PieEntry(archives, "Archives"))
        if (videos > 0) entries.add(PieEntry(videos, "Videos"))
        if (images > 0) entries.add(PieEntry(images, "Images"))
        if (audio > 0) entries.add(PieEntry(audio, "Audio"))
        if (others > 0) entries.add(PieEntry(others, "Others"))

        val dataSet = PieDataSet(entries, "")
        // UI: cleaner chart colours
        dataSet.colors = listOf(
            Color.parseColor("#4A90E2"),
            Color.parseColor("#F5A623"),
            Color.parseColor("#7ED321"),
            Color.parseColor("#9B59B6"),
            Color.parseColor("#E74C3C"),
            Color.parseColor("#1ABC9C"),
            Color.parseColor("#BDC3C7")
        )

        val data = PieData(dataSet)
        data.setValueFormatter(PercentFormatter(pieChart))

        pieChart.data = data
        pieChart.setUsePercentValues(true)
        pieChart.description.isEnabled = false
        pieChart.legend.isEnabled = true
        pieChart.centerText = getTotalStorage()
        pieChart.animateY(1200)

        pieChart.setOnChartValueSelectedListener(object : OnChartValueSelectedListener {
            override fun onValueSelected(e: Entry?, h: Highlight?) {
                val label = (e as PieEntry).label
                val filtered = allFiles.filter {
                    val n = it.name.lowercase()
                    when (label) {
                        "Documents" -> n.endsWith(".pdf") || n.endsWith(".doc") || n.endsWith(".txt")
                        "APK" -> n.endsWith(".apk")
                        "Archives" -> n.endsWith(".zip") || n.endsWith(".rar")
                        "Videos" -> n.endsWith(".mp4") || n.endsWith(".mkv")
                        "Images" -> n.endsWith(".jpg") || n.endsWith(".png")
                        "Audio" -> n.endsWith(".mp3")
                        else -> true
                    }
                }
                displayFiles.clear()
                displayFiles.addAll(filtered)
                showFiles(displayFiles)
            }
            override fun onNothingSelected() {}
        })

        pieChart.invalidate()
    }

    private fun getTotalStorage(): String {
        var total = 0L
        for (f in allFiles) total += f.length()
        return formatSize(total)
    }

    private fun applyFilter(type: String) {

        val filtered = FileFilters.filterFiles(
            allFiles,
            type,
            FileRepository.duplicateMap,
            FileRepository.fileHashMap,
            sortBySize
        )

        displayFiles.clear()
        displayFiles.addAll(filtered)

        fileCount.text = "${displayFiles.size} files"
        showFiles(displayFiles)
    }

    private fun showFiles(files: List<File>) {
        val adapter = object : BaseAdapter() {
            override fun getCount(): Int = files.size
            override fun getItem(position: Int): Any = files[position]
            override fun getItemId(position: Int): Long = position.toLong()

            override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
                val file = files[position]

                val row = LinearLayout(this@SecondActivity)
                row.orientation = LinearLayout.HORIZONTAL
                row.setPadding(20, 20, 20, 20)
                row.background = GradientDrawable().apply {
                    cornerRadius = 25f
                    setColor(Color.WHITE)                      // UI: white card rows
                    setStroke(2, Color.parseColor("#E5E7EB"))  // UI: light border
                }
                row.elevation = 2f                             // UI: lighter elevation

                val checkBox = CheckBox(this@SecondActivity)
                checkBox.setOnCheckedChangeListener(null)
                checkBox.isChecked = selectedFiles.contains(file)
                checkBox.setOnCheckedChangeListener { _, isChecked ->
                    if (isChecked) selectedFiles.add(file)
                    else selectedFiles.remove(file)
                }

                val name = TextView(this@SecondActivity)
                name.text = "${file.name}\n${formatSize(file.length())}"
                name.setTextColor(Color.parseColor("#1A1A2E")) // UI: dark file name
                name.layoutParams = LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f
                )

                val deleteBtn = Button(this@SecondActivity)
                deleteBtn.text = "Delete"
                deleteBtn.setTextColor(Color.WHITE)
                deleteBtn.setBackgroundColor(Color.parseColor("#FF4757")) // UI: cleaner red

                deleteBtn.setOnClickListener {
                    AlertDialog.Builder(this@SecondActivity)
                        .setTitle("Delete File")
                        .setMessage("Are you sure you want to delete ${file.name}?")
                        .setPositiveButton("Yes") { _, _ ->
                            file.delete()
                            allFiles.remove(file)
                            selectedFiles.remove(file)
                            applyFilter(filterSpinner.selectedItem.toString())
                            Toast.makeText(this@SecondActivity, "File deleted", Toast.LENGTH_SHORT).show()
                        }
                        .setNegativeButton("No", null)
                        .show()
                }

                row.addView(checkBox)
                row.addView(name)
                row.addView(deleteBtn)

                return row
            }
        }
        listView.adapter = adapter
    }

    private fun formatSize(size: Long): String {
        val kb = size / 1024
        val mb = kb / 1024
        val gb = mb / 1024
        return when {
            gb > 0 -> "$gb GB"
            mb > 0 -> "$mb MB"
            else -> "$kb KB"
        }
    }
}