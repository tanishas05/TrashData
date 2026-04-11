package com.example.trashdata

import java.io.File

object FileFilters {

    private const val LARGE_FILE_SIZE = 1 * 1024 * 1024L // 1 MB
    // Threshold for old files (15 minutes)
    private const val OLD_FILE_THRESHOLD = 15 * 60 * 1000L

    fun filterFiles(
        files: List<File>,
        type: String,
        duplicateMap: Map<String, List<File>>,
        fileHashMap: Map<File, String>,
        sortBySize: Boolean = true
    ): List<File> {
        val now = System.currentTimeMillis()
        val filtered = mutableListOf<File>()

        for (f in files) {
            when (type) {
                "All Files" -> filtered.add(f)
                "Duplicate Files" -> {
                    val h = fileHashMap[f] ?: ""
                    if ((duplicateMap[h]?.size ?: 0) > 1) filtered.add(f)
                }
                "Old Files" -> if (now - f.lastModified() > OLD_FILE_THRESHOLD) filtered.add(f)
                "Large Files" -> if (f.length() >= LARGE_FILE_SIZE) filtered.add(f)
                "Recent Files" -> if (now - f.lastModified() <= OLD_FILE_THRESHOLD) filtered.add(f)
            }
        }

        // Sort files
        return if (sortBySize) {
            filtered.sortedByDescending { it.length() }
        } else {
            filtered.sortedByDescending { it.lastModified() }
        }
    }

    fun isRelevant(file: File): Boolean {
        val n = file.name.lowercase()
        val minSize = 100 * 1024L // 100 KB
        return file.length() > minSize && (
                n.endsWith(".jpg") || n.endsWith(".jpeg") || n.endsWith(".png") ||
                        n.endsWith(".mp4") || n.endsWith(".mkv") || n.endsWith(".avi") ||
                        n.endsWith(".mp3") || n.endsWith(".wav") ||
                        n.endsWith(".pdf") || n.endsWith(".doc") || n.endsWith(".docx") || n.endsWith(".txt") ||
                        n.endsWith(".apk") || n.endsWith(".zip") || n.endsWith(".rar")
                )
    }
}