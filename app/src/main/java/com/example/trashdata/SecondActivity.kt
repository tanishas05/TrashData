package com.example.trashdata

import android.app.Activity
import android.os.Bundle
import android.os.Environment
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
    private val duplicateMap = HashMap<String, MutableList<File>>()
    private val selectedFiles = mutableSetOf<File>()
    private var scannedFiles = 0
    private var totalFiles = 0
    private var sortBySize = true
    private var initialFilter: String = "All Files"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 🔹 Step 1: Get initial filter from Intent
        initialFilter = intent.getStringExtra("filter") ?: "All Files"


        // 🔵 ROOT DRAWER
        drawerLayout = DrawerLayout(this)

        // ================= HEADER =================
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#2F80ED"))
            setPadding(40, 100, 40, 40)
            gravity = Gravity.CENTER
        }

        // Hamburger
        val hamburger = TextView(this).apply {
            text = "☰"
            textSize = 28f
            setTextColor(Color.WHITE)
            setTypeface(null, Typeface.BOLD)
            setOnClickListener { drawerLayout.openDrawer(GravityCompat.START) }
        }
        header.addView(hamburger, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { gravity = Gravity.START })

        val title = TextView(this).apply {
            text = "Files Cleaner"
            setTextColor(Color.WHITE)
            textSize = 22f
            setTypeface(null, Typeface.BOLD)
        }

        fileCount = TextView(this).apply { setTextColor(Color.WHITE) }
        progressText = TextView(this).apply { setTextColor(Color.WHITE) }
        progressBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply { max = 100 }

        header.addView(title)
        header.addView(fileCount)
        header.addView(progressText)
        header.addView(progressBar)

        // ================= CONTENT =================
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20, 20, 20, 20)
        }

        searchBar = EditText(this).apply {
            hint = "Search files..."
            background = GradientDrawable().apply {
                cornerRadius = 50f
                setColor(Color.WHITE)
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
        // 🔹 Step 2: Set spinner to initial filter
        val spinnerAdapter = filterSpinner.adapter as ArrayAdapter<String>
        val spinnerPosition = spinnerAdapter.getPosition(initialFilter)
        if (spinnerPosition >= 0) filterSpinner.setSelection(spinnerPosition)

        sortToggle = Button(this).apply { text = "Sort: Size" }
        deleteSelectedBtn = Button(this).apply {
            text = "Delete Selected"
            setBackgroundColor(Color.RED)
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
            addView(header)
            addView(container, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))

        }

        // ================= DRAWER MENU =================
        val drawerMenu = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.LTGRAY)
            layoutParams = DrawerLayout.LayoutParams(600, DrawerLayout.LayoutParams.MATCH_PARENT).apply {
                gravity = GravityCompat.START
            }
        }
        val menuCleaner = TextView(this).apply {
            text = "Cleaner"
            setPadding(20, 40, 20, 40)
            setOnClickListener {
                startActivity(Intent(this@SecondActivity, MainActivity::class.java))
                drawerLayout.closeDrawer(GravityCompat.START)
            }
        }
        val menuFiles = TextView(this).apply {
            text = "Files"
            setPadding(20, 40, 20, 40)
            setOnClickListener { drawerLayout.closeDrawer(GravityCompat.START) }
        }
        // 🔹 ADD SETTINGS
        val menuSettings = TextView(this).apply {
            text = "Settings"
            setPadding(20, 40, 20, 40)
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

        // ================= LOGIC =================
        checkPermissionsAndScan() // your function, intact

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

    private fun checkPermissionsAndScan() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                startActivity(Intent(android.provider.Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
            } else {
                scanFiles()
            }
        } else {
            if (checkSelfPermission(android.Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(android.Manifest.permission.READ_EXTERNAL_STORAGE), 100)
            } else {
                scanFiles()
            }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 100 && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            scanFiles()
        }
    }
    // 🔥 SCAN
    private fun scanFiles(){
        Thread{
            val root = Environment.getExternalStorageDirectory()
            totalFiles = countFiles(root)
            scanRecursive(root)
            buildDuplicateMap()

            // Apply filter on UI thread after scanning
            runOnUiThread{
                progressText.text = "Scan Complete"
                progressBar.progress = 100

                // ✅ Apply the filter now that allFiles is ready
                filterSpinner.setSelection((filterSpinner.adapter as ArrayAdapter<String>).getPosition(initialFilter))
                applyFilter(initialFilter)

                showStorageChart()
            }
        }.start()
    }
    private fun scanRecursive(dir: File){
        if(dir.absolutePath.contains("/Android")) return

        val files = dir.listFiles() ?: return

        for(file in files){
            if(file.isDirectory) scanRecursive(file)
            else{
                allFiles.add(file)
                scannedFiles++

                if(scannedFiles % 100 == 0){
                    val percent = (scannedFiles*100)/totalFiles
                    runOnUiThread{
                        progressText.text = "Scanning: $percent%"
                        progressBar.progress = percent
                    }
                }
            }
        }
    }

    private fun countFiles(dir: File):Int{
        if(dir.absolutePath.contains("/Android")) return 0
        val files = dir.listFiles() ?: return 0
        var count=0
        for(f in files){
            if(f.isDirectory) count+=countFiles(f)
            else count++
        }
        return count
    }

    // 🔥 PIE CHART
    private fun showStorageChart(){

        var images=0f; var videos=0f; var audio=0f
        var documents=0f; var apk=0f; var archives=0f; var others=0f

        for(f in allFiles){
            val n=f.name.lowercase()
            when{
                n.endsWith(".jpg")||n.endsWith(".png")->images++
                n.endsWith(".mp4")||n.endsWith(".mkv")->videos++
                n.endsWith(".mp3")->audio++
                n.endsWith(".pdf")||n.endsWith(".doc")||n.endsWith(".txt")->documents++
                n.endsWith(".apk")->apk++
                n.endsWith(".zip")||n.endsWith(".rar")->archives++
                else->others++
            }
        }

        val entries = arrayListOf<PieEntry>()
        if(documents>0) entries.add(PieEntry(documents,"Documents"))
        if(apk>0) entries.add(PieEntry(apk,"APK"))
        if(archives>0) entries.add(PieEntry(archives,"Archives"))
        if(videos>0) entries.add(PieEntry(videos,"Videos"))
        if(images>0) entries.add(PieEntry(images,"Images"))
        if(audio>0) entries.add(PieEntry(audio,"Audio"))
        if(others>0) entries.add(PieEntry(others,"Others"))

        val dataSet = PieDataSet(entries,"")
        dataSet.colors = listOf(
            Color.GREEN,Color.BLUE,Color.YELLOW,
            Color.GRAY,Color.MAGENTA,Color.CYAN,Color.LTGRAY
        )

        val data = PieData(dataSet)
        data.setValueFormatter(PercentFormatter(pieChart))

        pieChart.data = data
        pieChart.setUsePercentValues(true)
        pieChart.centerText = getTotalStorage()
        pieChart.animateY(1200)

        // 🔥 CLICK FILTER
        pieChart.setOnChartValueSelectedListener(object:OnChartValueSelectedListener{
            override fun onValueSelected(e: Entry?, h: Highlight?) {
                val label = (e as PieEntry).label

                val filtered = allFiles.filter{
                    val n=it.name.lowercase()
                    when(label){
                        "Documents"->n.endsWith(".pdf")||n.endsWith(".doc")||n.endsWith(".txt")
                        "APK"->n.endsWith(".apk")
                        "Archives"->n.endsWith(".zip")||n.endsWith(".rar")
                        "Videos"->n.endsWith(".mp4")||n.endsWith(".mkv")
                        "Images"->n.endsWith(".jpg")||n.endsWith(".png")
                        "Audio"->n.endsWith(".mp3")
                        else->true
                    }
                }

                displayFiles.clear()
                displayFiles.addAll(filtered)
                showFiles(displayFiles)
            }
            override fun onNothingSelected(){}
        })

        pieChart.invalidate()
    }

    private fun getTotalStorage():String{
        var total=0L
        for(f in allFiles) total+=f.length()
        return formatSize(total)
    }

    private fun buildDuplicateMap(){
        for(f in allFiles){
            val h=getFileHash(f)
            if(h.isNotEmpty())
                duplicateMap.getOrPut(h){ mutableListOf() }.add(f)
        }
    }

    private fun getFileHash(file: File): String {
        return try {
            val d=java.security.MessageDigest.getInstance("MD5")
            val b=ByteArray(1024)
            val i=file.inputStream()
            var r:Int
            while(i.read(b).also{r=it}!=-1) d.update(b,0,r)
            d.digest().joinToString(""){"%02x".format(it)}
        } catch(e:Exception){""}
    }

    private fun applyFilter(type: String) {
        displayFiles.clear()
        val now = System.currentTimeMillis()
        val oneWeek = 7 * 24 * 60 * 60 * 1000L // example: old = 1 week
        val minSize = 5 * 1024 * 1024L // 5 MB for large files

        for (f in allFiles) {
            when (type) {
                "All Files" -> displayFiles.add(f)
                "Duplicate Files" -> {
                    val h = getFileHash(f)
                    if (duplicateMap[h]?.size ?: 0 > 1) displayFiles.add(f)
                }
                "Old Files" -> if (now - f.lastModified() > oneWeek) displayFiles.add(f)
                "Recent Files" -> if (now - f.lastModified() <= oneWeek) displayFiles.add(f)
                "Large Files" -> if (f.length() >= minSize) displayFiles.add(f)
            }
        }

        if (sortBySize) displayFiles.sortByDescending { it.length() }
        else displayFiles.sortByDescending { it.lastModified() }

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

                // ✅ CHECKBOX
                val checkBox = CheckBox(this@SecondActivity)

                checkBox.isChecked = selectedFiles.contains(file)

                checkBox.setOnCheckedChangeListener { _, isChecked ->
                    if (isChecked) selectedFiles.add(file)
                    else selectedFiles.remove(file)
                }

                // 📄 FILE NAME
                val name = TextView(this@SecondActivity)
                name.text = "${file.name}\n${formatSize(file.length())}"
                name.layoutParams = LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    1f
                )

                // 🗑 SINGLE DELETE
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
        val kb=size/1024
        val mb=kb/1024
        val gb=mb/1024
        return when{
            gb>0->"$gb GB"
            mb>0->"$mb MB"
            else->"$kb KB"
        }
    }
}