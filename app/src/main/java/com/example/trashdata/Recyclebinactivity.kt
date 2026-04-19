package com.example.trashdata

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout

class RecycleBinActivity : Activity() {

    private lateinit var drawerLayout: DrawerLayout
    private lateinit var listView: ListView
    private lateinit var emptyView: TextView
    private lateinit var binSizeText: TextView
    private lateinit var emptyBinBtn: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        RecycleBin.purgeExpired(this)

        drawerLayout = DrawerLayout(this)

        // ── Header ───────────────────────────────────────────────────────────
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
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val title = TextView(this).apply {
            text = "🗑  Recycle Bin"
            setTextColor(Color.parseColor("#1A1A2E"))
            textSize = 20f
            setTypeface(null, Typeface.BOLD)
        }
        binSizeText = TextView(this).apply {
            setTextColor(Color.parseColor("#6B7280"))
            textSize = 13f
        }
        titleGroup.addView(title)
        titleGroup.addView(binSizeText)
        headerRow.addView(hamburger)
        headerRow.addView(titleGroup)
        header.addView(headerRow)

        val noteText = TextView(this).apply {
            text = "Files are kept for 5 days, then deleted permanently."
            textSize = 11f
            setTextColor(Color.parseColor("#9CA3AF"))
            setPadding(0, 6, 0, 0)
        }
        header.addView(noteText)

        // ── Main container ────────────────────────────────────────────────────
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20, 16, 20, 20)
            setBackgroundColor(Color.parseColor("#F5F6FA"))
        }

        emptyBinBtn = Button(this).apply {
            text = "Empty Bin"
            setBackgroundColor(Color.parseColor("#FF4757"))
            setTextColor(Color.WHITE)
            setOnClickListener {
                AlertDialog.Builder(this@RecycleBinActivity)
                    .setTitle("Empty Recycle Bin")
                    .setMessage("Permanently delete all files in the bin?")
                    .setPositiveButton("Delete All") { _, _ ->
                        RecycleBin.emptyBin(this@RecycleBinActivity)
                        Toast.makeText(this@RecycleBinActivity, "Bin emptied", Toast.LENGTH_SHORT).show()
                        refreshList()
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
        }

        emptyView = TextView(this).apply {
            text = "Recycle bin is empty"
            gravity = Gravity.CENTER
            textSize = 16f
            setTextColor(Color.parseColor("#9CA3AF"))
            setPadding(0, 80, 0, 0)
            visibility = View.GONE
        }

        listView = ListView(this).apply { itemsCanFocus = true }

        container.addView(emptyBinBtn, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        container.addView(emptyView, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        container.addView(listView, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))

        // ── Drawer ────────────────────────────────────────────────────────────
        val drawerMenu = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.WHITE)
            layoutParams = DrawerLayout.LayoutParams(600, DrawerLayout.LayoutParams.MATCH_PARENT).apply {
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
            startActivity(Intent(this, SecondActivity::class.java))
            drawerLayout.closeDrawer(GravityCompat.START)
        })
        drawerMenu.addView(drawerItem("🗑  Recycle Bin") {
            drawerLayout.closeDrawer(GravityCompat.START)
        })

        // ── Assemble ──────────────────────────────────────────────────────────
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

        drawerLayout.addView(mainContent)
        drawerLayout.addView(drawerMenu)
        setContentView(drawerLayout)

        refreshList()
    }

    override fun onResume() {
        super.onResume()
        RecycleBin.purgeExpired(this)
        refreshList()
    }

    private fun refreshList() {
        val entries = RecycleBin.getEntries(this)
        val totalSize = RecycleBin.totalSize(this)
        binSizeText.text = "${entries.size} files  •  ${formatSize(totalSize)}"

        if (entries.isEmpty()) {
            emptyView.visibility = View.VISIBLE
            emptyBinBtn.isEnabled = false
            emptyBinBtn.alpha = 0.4f
        } else {
            emptyView.visibility = View.GONE
            emptyBinBtn.isEnabled = true
            emptyBinBtn.alpha = 1f
        }

        val adapter = object : BaseAdapter() {
            override fun getCount() = entries.size
            override fun getItem(pos: Int): Any = entries[pos]
            override fun getItemId(pos: Int) = pos.toLong()

            override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
                val entry = entries[position]
                val binFile = java.io.File(
                    getFileStreamPath("recycle_bin").parentFile,
                    "recycle_bin/${entry.trashedName}"
                )
                // Compute days remaining
                val elapsed = System.currentTimeMillis() - entry.deletedAt
                val daysLeft = 5 - (elapsed / (24 * 60 * 60 * 1000L))
                val daysAgo  = elapsed / (24 * 60 * 60 * 1000L)

                val card = LinearLayout(this@RecycleBinActivity).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(20, 20, 20, 20)
                    background = GradientDrawable().apply {
                        cornerRadius = 25f
                        setColor(Color.WHITE)
                        setStroke(2, Color.parseColor("#E5E7EB"))
                    }
                    elevation = 2f
                    layoutParams = AbsListView.LayoutParams(
                        AbsListView.LayoutParams.MATCH_PARENT,
                        AbsListView.LayoutParams.WRAP_CONTENT
                    )
                }

                val topRow = LinearLayout(this@RecycleBinActivity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                }

                val nameView = TextView(this@RecycleBinActivity).apply {
                    val originalName = entry.originalPath.substringAfterLast("/")
                    text = "$originalName\n${formatSize(binFile.length())}  •  deleted ${daysAgo}d ago  •  expires in ${daysLeft}d"
                    setTextColor(Color.parseColor("#1A1A2E"))
                    textSize = 13f
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                }

                val restoreBtn = Button(this@RecycleBinActivity).apply {
                    text = "Restore"
                    textSize = 11f
                    setTextColor(Color.WHITE)
                    setBackgroundColor(Color.parseColor("#4A90E2"))
                    setPadding(16, 8, 16, 8)
                    setOnClickListener {
                        val ok = RecycleBin.restore(this@RecycleBinActivity, entry)
                        Toast.makeText(
                            this@RecycleBinActivity,
                            if (ok) "Restored to ${entry.originalPath}" else "Restore failed",
                            Toast.LENGTH_SHORT
                        ).show()
                        if (ok) refreshList()
                    }
                }

                val deleteBtn = Button(this@RecycleBinActivity).apply {
                    text = "Delete"
                    textSize = 11f
                    setTextColor(Color.WHITE)
                    setBackgroundColor(Color.parseColor("#FF4757"))
                    setPadding(16, 8, 16, 8)
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply { setMargins(8, 0, 0, 0) }
                    setOnClickListener {
                        AlertDialog.Builder(this@RecycleBinActivity)
                            .setTitle("Delete Permanently")
                            .setMessage("This cannot be undone.")
                            .setPositiveButton("Delete") { _, _ ->
                                RecycleBin.deletePermanently(this@RecycleBinActivity, entry)
                                refreshList()
                            }
                            .setNegativeButton("Cancel", null)
                            .show()
                    }
                }

                topRow.addView(nameView)
                topRow.addView(restoreBtn)
                topRow.addView(deleteBtn)
                card.addView(topRow)

                // Expiry progress bar
                val progress = ((elapsed.toFloat() / (5 * 24 * 60 * 60 * 1000L)) * 100).toInt().coerceIn(0, 100)
                val expiryBar = ProgressBar(this@RecycleBinActivity, null,
                    android.R.attr.progressBarStyleHorizontal).apply {
                    max = 100
                    this.progress = progress
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        12
                    ).apply { setMargins(0, 10, 0, 0) }
                    progressDrawable.setColorFilter(
                        Color.parseColor(if (progress > 75) "#FF4757" else "#4A90E2"),
                        android.graphics.PorterDuff.Mode.SRC_IN
                    )
                }
                card.addView(expiryBar)

                return card
            }
        }

        listView.adapter = adapter
        listView.divider = null
        listView.dividerHeight = 16
    }

    private fun formatSize(size: Long): String {
        val kb = size / 1024; val mb = kb / 1024; val gb = mb / 1024
        return when { gb > 0 -> "$gb GB"; mb > 0 -> "$mb MB"; else -> "$kb KB" }
    }
}