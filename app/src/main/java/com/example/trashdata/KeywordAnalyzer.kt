package com.example.trashdata

import android.content.Context
import java.io.File

object KeywordAnalyzer {

    // Common words to ignore
    private val STOP_WORDS = setOf(
        "the","and","for","are","but","not","you","all","can","her","was","one",
        "our","out","day","get","has","him","his","how","its","may","new","now",
        "old","see","two","who","boy","did","she","use","way","will","with","have",
        "this","that","from","they","been","were","said","each","which","their",
        "time","would","there","could","other","into","than","then","some","these",
        "when","what","your","more","also","about","after","first","well","even",
        "back","good","much","before","here","just","know","take","great","think",
        "where","much","through","long","down","over","such","because","come","work"
    )

    private val TEXT_EXTENSIONS = setOf("txt", "pdf", "doc", "docx")

    /**
     * Main entry point — returns keyword chips for any file, instantly, offline.
     */
    fun getKeywords(file: File, context: Context): List<String> {
        val ext = file.extension.lowercase()
        return if (ext in TEXT_EXTENSIONS) {
            val text = TextExtractor.extract(file, context)
            if (!text.isNullOrBlank()) {
                extractKeywordsFromText(text) + typeTag(file)
            } else {
                metadataTags(file)
            }
        } else {
            metadataTags(file)
        }
    }

    // ── TEXT KEYWORD EXTRACTION (word frequency) ─────────────────────────────
    private fun extractKeywordsFromText(text: String): List<String> {
        val words = text
            .lowercase()
            .replace(Regex("[^a-z\\s]"), " ")
            .split(Regex("\\s+"))
            .filter { word ->
                word.length in 4..20
                        && word !in STOP_WORDS
                        && isRealWord(word)
            }

        val freq = mutableMapOf<String, Int>()
        for (word in words) freq[word] = (freq[word] ?: 0) + 1

        return freq.entries
            .sortedByDescending { it.value }
            .take(4)
            .map { it.key }
    }

    /**
     * Rejects gibberish:
     * - Must have at least one vowel
     * - No more than 4 consecutive consonants
     * - Vowel ratio between 15% and 80%
     */
    private fun isRealWord(word: String): Boolean {
        val vowels = setOf('a', 'e', 'i', 'o', 'u')
        if (word.none { it in vowels }) return false
        var consonantRun = 0
        for (ch in word) {
            if (ch !in vowels) consonantRun++ else consonantRun = 0
            if (consonantRun > 4) return false
        }
        val vowelRatio = word.count { it in vowels }.toDouble() / word.length
        if (vowelRatio < 0.15 || vowelRatio > 0.80) return false
        return true
    }

    // ── METADATA TAGS (for non-text files) ───────────────────────────────────
    fun metadataTags(file: File): List<String> {
        val tags    = mutableListOf<String>()
        val ext     = file.extension.lowercase()
        val ageDays = (System.currentTimeMillis() - file.lastModified()) / (1000 * 60 * 60 * 24)
        val sizeMb  = file.length() / (1024.0 * 1024.0)

        // Type
        tags.add(typeTag(file))

        // Age
        when {
            ageDays > 365 -> tags.add("very-old")
            ageDays > 90  -> tags.add("old")
            ageDays > 30  -> tags.add("aging")
            else          -> tags.add("recent")
        }

        // Size
        when {
            sizeMb > 500 -> tags.add("huge")
            sizeMb > 100 -> tags.add("large")
            sizeMb > 10  -> tags.add("medium")
            else         -> tags.add("small")
        }

        // Extra smart tags based on filename
        val name = file.nameWithoutExtension.lowercase()
        when {
            name.contains("invoice") || name.contains("bill")     -> tags.add("invoice")
            name.contains("resume") || name.contains("cv")        -> tags.add("resume")
            name.contains("report")                               -> tags.add("report")
            name.contains("backup") || name.contains("bak")       -> tags.add("backup")
            name.contains("screenshot") || name.contains("screen")-> tags.add("screenshot")
            name.contains("whatsapp") || name.contains("wa")      -> tags.add("whatsapp")
            name.contains("download")                             -> tags.add("downloaded")
            name.contains("temp") || name.contains("tmp")         -> tags.add("temporary")
            name.contains("copy") || name.contains("duplicate")   -> tags.add("duplicate")
            name.contains("img") || name.contains("photo")        -> tags.add("photo")
            name.contains("vid") || name.contains("movie")        -> tags.add("movie")
            name.contains("rec") || name.contains("record")       -> tags.add("recording")
        }

        return tags.distinct().take(5)
    }

    private fun typeTag(file: File): String {
        return when (file.extension.lowercase()) {
            "jpg", "jpeg", "png", "gif", "webp" -> "image"
            "mp4", "mkv", "avi", "mov"          -> "video"
            "mp3", "wav", "aac", "flac"         -> "audio"
            "pdf"                               -> "pdf"
            "doc", "docx"                       -> "document"
            "txt"                               -> "text"
            "apk"                               -> "apk"
            "zip", "rar", "7z"                  -> "archive"
            else                               -> "file"
        }
    }
}