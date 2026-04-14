package com.example.trashdata

import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.CopyOnWriteArrayList

object FileRepository {
    val junkFiles = CopyOnWriteArrayList<File>()
    val fileHashMap = java.util.concurrent.ConcurrentHashMap<File, String>()
    val duplicateMap = java.util.concurrent.ConcurrentHashMap<String, MutableList<File>>()
    val isScanning = AtomicBoolean(false)
    val cancelScan = AtomicBoolean(false)

    fun clear() {
        junkFiles.clear()
        fileHashMap.clear()
        duplicateMap.clear()
        isScanning.set(false)
        cancelScan.set(false)
    }

    fun removeFile(file: File) {
        junkFiles.removeAll { it.absolutePath == file.absolutePath }
        val hash = fileHashMap.remove(file)
        if (hash != null) {
            duplicateMap[hash]?.removeAll { it.absolutePath == file.absolutePath }
            if (duplicateMap[hash]?.isEmpty() == true) duplicateMap.remove(hash)
        }
    }

    fun buildDuplicateMap() {
        fileHashMap.clear()
        duplicateMap.clear()
        for (f in junkFiles) {
            if (f.length() < 100 * 1024) continue
            val h = getFileHash(f)
            if (h.isNotEmpty()) {
                fileHashMap[f] = h
                duplicateMap.getOrPut(h) { CopyOnWriteArrayList() }.add(f)
            }
        }
    }

    fun getFileHash(file: File): String {
        return try {
            val digest = java.security.MessageDigest.getInstance("MD5")
            val buffer = ByteArray(8192)
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