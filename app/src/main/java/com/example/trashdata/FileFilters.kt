package com.example.trashdata
import java.io.File
object FileFilters {
    private const val LARGE_FILE_SIZE = 1 * 1024 * 1024L
    private const val OLD_FILE_THRESHOLD = 15L * 60 * 1000L // 15 minutes
    enum class SortMode { SIZE_HIGH_LOW, SIZE_LOW_HIGH, DATE_NEWEST, DATE_OLDEST }
    fun filterFiles(
        files: List<File>,
        type: String,
        duplicateMap: Map<String, List<File>>,
        fileHashMap: Map<File, String>,
        sortBySize: Boolean = true,
        ageDays: Int = -1,
        sortMode: SortMode = SortMode.SIZE_HIGH_LOW
    ): List<File> {
        val now = System.currentTimeMillis()
        val ageThreshold = if (ageDays > 0) ageDays.toLong() * 24 * 60 * 60 * 1000L else -1L
        val filtered = mutableListOf<File>()
        for (f in files) {
            if (ageThreshold > 0 && (now - f.lastModified()) < ageThreshold) continue
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
        return when (sortMode) {
            SortMode.SIZE_HIGH_LOW -> filtered.sortedByDescending { it.length() }
            SortMode.SIZE_LOW_HIGH -> filtered.sortedBy { it.length() }
            SortMode.DATE_NEWEST   -> filtered.sortedByDescending { it.lastModified() }
            SortMode.DATE_OLDEST   -> filtered.sortedBy { it.lastModified() }
        }
    }
    fun isRelevant(file: File): Boolean {
        val n = file.name.lowercase()
        val minSize = 1L
        return file.length() > minSize && (
                // Images
                n.endsWith(".jpg") || n.endsWith(".jpeg") || n.endsWith(".png") ||
                        n.endsWith(".gif") || n.endsWith(".webp") || n.endsWith(".bmp") ||
                        n.endsWith(".heic") || n.endsWith(".heif") || n.endsWith(".tiff") ||
                        n.endsWith(".raw") || n.endsWith(".svg") || n.endsWith(".ico") ||
                        // Videos
                        n.endsWith(".mp4") || n.endsWith(".mkv") || n.endsWith(".avi") ||
                        n.endsWith(".mov") || n.endsWith(".3gp") || n.endsWith(".3g2") ||
                        n.endsWith(".webm") || n.endsWith(".flv") || n.endsWith(".wmv") ||
                        n.endsWith(".m4v") || n.endsWith(".ts") || n.endsWith(".mpeg") ||
                        n.endsWith(".mpg") ||
                        // Audio
                        n.endsWith(".mp3") || n.endsWith(".wav") || n.endsWith(".aac") ||
                        n.endsWith(".flac") || n.endsWith(".ogg") || n.endsWith(".m4a") ||
                        n.endsWith(".wma") || n.endsWith(".opus") || n.endsWith(".amr") ||
                        n.endsWith(".mid") || n.endsWith(".midi") ||
                        // Documents
                        n.endsWith(".pdf") || n.endsWith(".doc") || n.endsWith(".docx") ||
                        n.endsWith(".txt") || n.endsWith(".xls") || n.endsWith(".xlsx") ||
                        n.endsWith(".ppt") || n.endsWith(".pptx") || n.endsWith(".odt") ||
                        n.endsWith(".ods") || n.endsWith(".odp") || n.endsWith(".csv") ||
                        n.endsWith(".rtf") || n.endsWith(".epub") || n.endsWith(".pages") ||
                        n.endsWith(".numbers") || n.endsWith(".key") ||
                        // Archives
                        n.endsWith(".zip") || n.endsWith(".rar") || n.endsWith(".7z") ||
                        n.endsWith(".tar") || n.endsWith(".gz") || n.endsWith(".bz2") ||
                        n.endsWith(".xz") || n.endsWith(".cab") || n.endsWith(".iso") ||
                        // APK & Android
                        n.endsWith(".apk") || n.endsWith(".apks") || n.endsWith(".xapk") ||
                        n.endsWith(".aab") ||
                        // Code & Data
                        n.endsWith(".json") || n.endsWith(".xml") || n.endsWith(".html") ||
                        n.endsWith(".htm") || n.endsWith(".js") || n.endsWith(".css") ||
                        n.endsWith(".sql") || n.endsWith(".db") || n.endsWith(".sqlite") ||
                        n.endsWith(".log") || n.endsWith(".torrent") ||
                        n.endsWith(".ics") || n.endsWith(".vcf")
                )
    }
}