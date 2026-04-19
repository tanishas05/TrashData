package com.example.trashdata

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

object RecycleBin {

    private const val PREFS_NAME   = "recycle_bin_prefs"
    private const val KEY_ENTRIES  = "entries"
    private const val RETENTION_MS = 5L * 24 * 60 * 60 * 1000L

    data class TrashEntry(
        val trashedName: String,
        val originalPath: String,
        val deletedAt: Long
    )

    private fun binDir(context: Context): File {
        val dir = File(context.filesDir, "recycle_bin")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }


    private fun loadEntries(context: Context): MutableList<TrashEntry> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json  = prefs.getString(KEY_ENTRIES, "[]") ?: "[]"
        val arr   = JSONArray(json)
        val list  = mutableListOf<TrashEntry>()
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            list.add(TrashEntry(
                trashedName  = obj.getString("trashedName"),
                originalPath = obj.getString("originalPath"),
                deletedAt    = obj.getLong("deletedAt")
            ))
        }
        return list
    }

    private fun saveEntries(context: Context, entries: List<TrashEntry>) {
        val arr = JSONArray()
        for (e in entries) {
            arr.put(JSONObject().apply {
                put("trashedName",  e.trashedName)
                put("originalPath", e.originalPath)
                put("deletedAt",    e.deletedAt)
            })
        }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_ENTRIES, arr.toString()).apply()
    }

    fun moveToTrash(context: Context, file: File): Boolean {
        return try {
            val bin        = binDir(context)
            val trashedName = "${System.currentTimeMillis()}_${file.name}"
            val dest       = File(bin, trashedName)
            val moved      = file.copyTo(dest, overwrite = true).exists() && file.delete()
            if (moved) {
                val entries = loadEntries(context).toMutableList()
                entries.add(TrashEntry(trashedName, file.absolutePath, System.currentTimeMillis()))
                saveEntries(context, entries)
            }
            moved
        } catch (e: Exception) {
            false
        }
    }

    fun restore(context: Context, entry: TrashEntry): Boolean {
        return try {
            val src  = File(binDir(context), entry.trashedName)
            val dest = File(entry.originalPath)
            dest.parentFile?.mkdirs()
            val ok = src.copyTo(dest, overwrite = true).exists() && src.delete()
            if (ok) {
                val entries = loadEntries(context).toMutableList()
                entries.removeAll { it.trashedName == entry.trashedName }
                saveEntries(context, entries)
            }
            ok
        } catch (e: Exception) {
            false
        }
    }

    fun deletePermanently(context: Context, entry: TrashEntry): Boolean {
        return try {
            File(binDir(context), entry.trashedName).delete()
            val entries = loadEntries(context).toMutableList()
            entries.removeAll { it.trashedName == entry.trashedName }
            saveEntries(context, entries)
            true
        } catch (e: Exception) {
            false
        }
    }

    fun emptyBin(context: Context) {
        val entries = loadEntries(context)
        val bin     = binDir(context)
        for (e in entries) File(bin, e.trashedName).delete()
        saveEntries(context, emptyList())
    }

    fun purgeExpired(context: Context) {
        val now     = System.currentTimeMillis()
        val bin     = binDir(context)
        val entries = loadEntries(context).toMutableList()
        val expired = entries.filter { now - it.deletedAt > RETENTION_MS }
        for (e in expired) File(bin, e.trashedName).delete()
        entries.removeAll { now - it.deletedAt > RETENTION_MS }
        saveEntries(context, entries)
    }

    fun getEntries(context: Context): List<TrashEntry> = loadEntries(context)

    fun totalSize(context: Context): Long {
        val bin = binDir(context)
        return loadEntries(context).sumOf { File(bin, it.trashedName).length() }
    }
}