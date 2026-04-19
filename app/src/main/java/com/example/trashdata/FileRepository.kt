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

        // Phase 1: group by (name + size) — fast, no I/O
        val nameSize = java.util.concurrent.ConcurrentHashMap<String, MutableList<File>>()
        for (f in junkFiles) {
            val key = "${f.name}|${f.length()}"
            nameSize.getOrPut(key) { CopyOnWriteArrayList() }.add(f)
        }

        // Phase 2: for groups with >1 candidate, confirm with MD5 hash
        for ((_, candidates) in nameSize) {
            if (candidates.size < 2) continue
            for (f in candidates) {
                val h = getFileHash(f)
                if (h.isNotEmpty()) {
                    fileHashMap[f] = h
                    duplicateMap.getOrPut(h) { CopyOnWriteArrayList() }.add(f)
                }
            }
        }

        // Clean up groups that turned out to be unique after hashing
        val toRemove = duplicateMap.entries.filter { it.value.size < 2 }.map { it.key }
        for (k in toRemove) {
            duplicateMap[k]?.forEach { fileHashMap.remove(it) }
            duplicateMap.remove(k)
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