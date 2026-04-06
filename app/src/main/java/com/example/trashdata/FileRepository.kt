package com.example.trashdata

import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

object FileRepository {
    // FILE STORAGE
    val junkFiles = mutableListOf<File>()
    val fileHashMap = mutableMapOf<File, String>()
    val duplicateMap = mutableMapOf<String, MutableList<File>>() // hash -> list of files
    //  SCAN FLAGS
    val isScanning = AtomicBoolean(false)  // true when scanning is running
    val cancelScan = AtomicBoolean(false)  // set to true to cancel scan
    fun clear() {
        junkFiles.clear()
        fileHashMap.clear()
        duplicateMap.clear()
        isScanning.set(false)
        cancelScan.set(false)
    }
    // Rebuild duplicate map from existing junkFiles
    fun buildDuplicateMap() {
        fileHashMap.clear()
        duplicateMap.clear()
        for (f in junkFiles) {
            if (f.length() < 1024 * 1024) continue // skip files < 1MB
            val h = getFileHash(f)
            if (h.isNotEmpty()) {
                fileHashMap[f] = h
                duplicateMap.getOrPut(h) { mutableListOf() }.add(f)
            }
        }
    }
    // Generate MD5 hash for a file
    fun getFileHash(file: File): String {
        return try {
            val digest = java.security.MessageDigest.getInstance("MD5")
            val buffer = ByteArray(1024)
            file.inputStream().use { input ->
                var read: Int
                while (input.read(buffer).also { read = it } != -1) {
                    digest.update(buffer, 0, read)
                }
            }
            digest.digest().joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            ""
        }
    }
}