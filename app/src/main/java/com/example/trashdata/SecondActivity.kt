package com.example.trashdata

import android.app.Activity
import android.os.Bundle
import android.os.Environment
import android.widget.*
import android.graphics.Color
import android.view.View
import android.view.ViewGroup
import android.graphics.drawable.GradientDrawable
import java.io.File
import android.content.Intent
import android.text.Editable
import android.text.TextWatcher
import com.github.mikephil.charting.charts.PieChart
import com.github.mikephil.charting.data.*
import com.github.mikephil.charting.listener.OnChartValueSelectedListener
import com.github.mikephil.charting.highlight.Highlight
import com.github.mikephil.charting.formatter.PercentFormatter

class SecondActivity : Activity() {

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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL

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

        progressBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal)
        progressBar.max = 100

        header.addView(title)
        header.addView(fileCount)
        header.addView(progressText)
        header.addView(progressBar)

        // ⚪ CONTENT
        val container = LinearLayout(this)
        container.orientation = LinearLayout.VERTICAL
        container.setPadding(20,20,20,20)

        searchBar = EditText(this)
        searchBar.hint = "Search files..."

        val bg = GradientDrawable()
        bg.cornerRadius = 50f
        bg.setColor(Color.WHITE)
        searchBar.background = bg
        searchBar.setPadding(30,20,30,20)

        filterSpinner = Spinner(this)
        val filters = arrayOf("All Files","Duplicate Files")
        filterSpinner.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            filters
        )

        sortToggle = Button(this)
        sortToggle.text = "Sort: Size"

        deleteSelectedBtn = Button(this)
        deleteSelectedBtn.text = "Delete Selected"
        deleteSelectedBtn.setBackgroundColor(Color.RED)


        pieChart = PieChart(this)
        pieChart.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 600
        )

        listView = ListView(this)

        container.addView(pieChart)
        container.addView(searchBar)
        container.addView(filterSpinner)
        container.addView(sortToggle)
        container.addView(deleteSelectedBtn)
        container.addView(listView)

        // 🔻 BOTTOM NAVIGATION
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
            LinearLayout.LayoutParams.MATCH_PARENT,0,1f
        ))
        root.addView(navBar)

        setContentView(root)

        scanFiles()

        sortToggle.setOnClickListener {
            sortBySize = !sortBySize
            sortToggle.text = if(sortBySize) "Sort: Size" else "Sort: Date"
            applyFilter(filterSpinner.selectedItem.toString())
        }

        searchBar.addTextChangedListener(object: TextWatcher{
            override fun afterTextChanged(s: Editable?) {
                val q = s.toString().lowercase()
                showFiles(displayFiles.filter { it.name.lowercase().contains(q) })
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int){}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int){}
        })

        deleteSelectedBtn.setOnClickListener {

            for (file in selectedFiles) {
                if (file.exists()) file.delete()
            }

            Toast.makeText(this, "Deleted ${selectedFiles.size} files", Toast.LENGTH_SHORT).show()

            selectedFiles.clear()

            applyFilter(filterSpinner.selectedItem.toString())
        }

        cleanerBtn.setOnClickListener {
            startActivity(Intent(this, MainActivity::class.java))
        }
    }

    // 🔥 SCAN
    private fun scanFiles(){
        Thread{
            val root = Environment.getExternalStorageDirectory()
            totalFiles = countFiles(root)
            scanRecursive(root)
            buildDuplicateMap()

            runOnUiThread{
                progressText.text = "Scan Complete"
                progressBar.progress = 100
                applyFilter("All Files")
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

    private fun applyFilter(type:String){
        displayFiles.clear()
        for(f in allFiles){
            when(type){
                "All Files"->displayFiles.add(f)
                "Duplicate Files"->{
                    val h=getFileHash(f)
                    if(duplicateMap[h]?.size?:0>1) displayFiles.add(f)
                }
            }
        }

        if(sortBySize) displayFiles.sortByDescending{it.length()}
        else displayFiles.sortByDescending{it.lastModified()}

        fileCount.text="${displayFiles.size} files"
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