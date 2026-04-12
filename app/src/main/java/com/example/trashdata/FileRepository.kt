package com.example.trashdata

import java.io.File
import java.security.MessageDigest
import java.util.Collections

object FileRepository {

    // THREAD SAFE LISTS
    val junkFiles = Collections.synchronizedList(mutableListOf<File>())

    // Use PATH instead of File object
    val fileHashMap = Collections.synchronizedMap(mutableMapOf<String, String>())
    val duplicateMap = Collections.synchronizedMap(mutableMapOf<String, MutableList<File>>())

    fun clear() {
        junkFiles.clear()
        fileHashMap.clear()
        duplicateMap.clear()
    }

    // 🔥 FAST DUPLICATE DETECTION
    fun buildDuplicateMap() {
        fileHashMap.clear()
        duplicateMap.clear()

        // STEP 1: GROUP BY SIZE
        val sizeMap = mutableMapOf<Long, MutableList<File>>()

        for (f in junkFiles) {
            if (f.length() < 100 * 1024) continue
            sizeMap.getOrPut(f.length()) { mutableListOf() }.add(f)
        }

        // STEP 2: ONLY CHECK SAME SIZE FILES
        for ((_, files) in sizeMap) {
            if (files.size < 2) continue

            for (f in files) {
                val hash = getQuickHash(f) // ⚡ faster hash
                if (hash.isNotEmpty()) {
                    fileHashMap[f.absolutePath] = hash
                    duplicateMap.getOrPut(hash) { mutableListOf() }.add(f)
                }
            }
        }
    }

    // ⚡ HASH ONLY FIRST 4KB (FAST)
    private fun getQuickHash(file: File): String {
        return try {
            val digest = MessageDigest.getInstance("MD5")
            val buffer = ByteArray(4096)

            file.inputStream().use { input ->
                val read = input.read(buffer)
                if (read > 0) digest.update(buffer, 0, read)
            }

            digest.digest().joinToString("") { "%02x".format(it) }

        } catch (e: Exception) {
            ""
        }
    }
}