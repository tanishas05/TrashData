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

        var count = 0
        var totalSize = 0L

        while (queue.isNotEmpty() && !callback.isCancelled()) {
            val dir = queue.removeFirst()

            if (dir.absolutePath.contains("/Android")) continue

            val files = try {
                dir.listFiles()
            } catch (e: Exception) {
                continue
            } ?: continue

            for (file in files) {
                if (callback.isCancelled()) return

                if (file.isFile && FileFilters.isRelevant(file)) {
                    count++
                    totalSize += file.length()
                    callback.onFileFound(file)
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