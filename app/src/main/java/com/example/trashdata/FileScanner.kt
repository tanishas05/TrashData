package com.example.trashdata

import java.io.File
import java.util.ArrayDeque

object FileScanner {

    interface ScanCallback {
        fun onFileFound(file: File)
        fun onProgress(count: Int, totalSize: Long)
        fun isCancelled(): Boolean
    }

    fun scan(root: File, callback: ScanCallback) {
        val queue = ArrayDeque<File>()
        queue.add(root)

        val now = System.currentTimeMillis()
        val oldThreshold = 15 * 60 * 1000L   // SAME as your MainActivity logic

        var count = 0
        var totalSize = 0L

        while (queue.isNotEmpty() && !callback.isCancelled()) {
            val dir = queue.removeFirst()

            // Skip Android system folder (same as before)
            if (dir.absolutePath.contains("/Android")) continue

            val files = try {
                dir.listFiles()
            } catch (e: Exception) {
                continue
            } ?: continue

            for (file in files) {
                if (callback.isCancelled()) return

                if (file.isFile) {

                    // ✅ Apply SAME filters as your original logic
                    val isOld = now - file.lastModified() > oldThreshold
                    val isRelevant = FileFilters.isRelevant(file)

                    if (isOld && isRelevant) {
                        count++
                        totalSize += file.length()

                        callback.onFileFound(file)
                    }
                }

                if (file.isDirectory) {
                    queue.add(file)
                }
            }

            callback.onProgress(count, totalSize)
        }
    }
    fun countTotalFiles(root: File): Int {
        var total = 0
        val stack = ArrayDeque<File>()
        stack.add(root)
        while (stack.isNotEmpty()) {
            val dir = stack.removeFirst()
            val files = dir.listFiles()
            if (files != null) {
                for (f in files) {
                    if (f.isDirectory) stack.add(f)
                    else if (FileFilters.isRelevant(f)) total++
                }
            }
        }
        return total
    }
}

