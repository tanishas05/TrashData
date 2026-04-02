package com.example.trashdata

import android.app.Activity
import android.os.Bundle
import android.widget.*
import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.content.Intent

class DashboardActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL

        // 🔵 TOP BLUE SECTION
        val topLayout = LinearLayout(this)
        topLayout.orientation = LinearLayout.VERTICAL
        topLayout.setBackgroundColor(Color.parseColor("#2F80ED"))
        topLayout.setPadding(20, 60, 20, 40)
        topLayout.gravity = Gravity.CENTER

        val title = TextView(this)
        title.text = "Phone Cleaner"
        title.setTextColor(Color.WHITE)
        title.textSize = 20f
        title.setTypeface(null, Typeface.BOLD)

        val junkText = TextView(this)
        junkText.text = "Junk Files"
        junkText.setTextColor(Color.WHITE)
        junkText.textSize = 18f

        val cleanBtn = Button(this)
        cleanBtn.text = "CLEAN"

        topLayout.addView(title)
        topLayout.addView(junkText)
        topLayout.addView(cleanBtn)

        // ⚪ CARD SECTION
        val card = LinearLayout(this)
        card.orientation = LinearLayout.VERTICAL
        card.setBackgroundColor(Color.WHITE)
        card.setPadding(20, 20, 20, 20)

        val grid = GridLayout(this)
        grid.columnCount = 2

        fun createItem(icon: String, name: String, action: () -> Unit): LinearLayout {

            val box = LinearLayout(this)
            box.orientation = LinearLayout.VERTICAL
            box.setPadding(30, 30, 30, 30)
            box.gravity = Gravity.CENTER

            val iconView = TextView(this)
            iconView.text = icon
            iconView.textSize = 28f

            val textView = TextView(this)
            textView.text = name
            textView.gravity = Gravity.CENTER

            box.addView(iconView)
            box.addView(textView)

            box.setOnClickListener { action() }

            return box
        }

        // 🔥 OPTIONS (like your image)
        grid.addView(createItem("🚀", "Old Files") {
            openSecond("Old Files")
        })

        grid.addView(createItem("📂", "Large Files") {
            openSecond("Large Files")
        })

        grid.addView(createItem("🧹", "Duplicate Files") {
            openSecond("Duplicate Files")
        })

        grid.addView(createItem("📦", "APK Files") {
            openSecond("APK Files")
        })

        grid.addView(createItem("📁", "All Files") {
            openSecond("All Files")
        })

        card.addView(grid)

        // 🔻 BOTTOM NAV
        val nav = LinearLayout(this)
        nav.orientation = LinearLayout.HORIZONTAL
        nav.setBackgroundColor(Color.parseColor("#EEEEEE"))

        val cleanerBtn = Button(this)
        cleanerBtn.text = "Cleaner"

        val filesBtn = Button(this)
        filesBtn.text = "Files"

        nav.addView(cleanerBtn, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        nav.addView(filesBtn, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))

        filesBtn.setOnClickListener {
            startActivity(Intent(this, SecondActivity::class.java))
        }

        // 🔗 ADD ALL
        root.addView(topLayout)
        root.addView(card, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
        ))
        root.addView(nav)

        setContentView(root)
    }

    private fun openSecond(filter: String) {
        val intent = Intent(this, SecondActivity::class.java)
        intent.putExtra("filter", filter)
        startActivity(intent)
    }
}
