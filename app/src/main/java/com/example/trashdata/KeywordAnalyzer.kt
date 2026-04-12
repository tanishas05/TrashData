package com.example.trashdata

import android.content.Context
import android.util.Log
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

object KeywordAnalyzer {

    private const val TAG = "KeywordAnalyzer"

    // Zero-shot classification model — free, no fine-tuning needed
    // Classifies text into candidate labels (our keyword categories)
    private const val HF_ENDPOINT =
        "https://api-inference.huggingface.co/models/cross-encoder/nli-MiniLM2-L6-H768"

    // Candidate keyword labels sent to the model
    // It picks which ones best match the file content/name
    private val CANDIDATE_LABELS = listOf(
        "invoice", "resume", "report", "photo", "video", "audio",
        "document", "backup", "temporary", "duplicate", "download",
        "screenshot", "recording", "archive", "important", "personal",
        "work", "finance", "medical", "education", "entertainment",
        "old", "large", "unused", "whatsapp"
    )

    private val TEXT_EXTENSIONS = setOf("txt", "pdf", "doc", "docx")

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val STOP_WORDS = setOf(
        "the","and","for","are","but","not","you","all","can","her","was","one",
        "our","out","day","get","has","him","his","how","its","may","new","now",
        "old","see","two","who","boy","did","she","use","way","will","with","have",
        "this","that","from","they","been","were","said","each","which","their",
        "time","would","there","could","other","into","than","then","some","these",
        "when","what","your","more","also","about","after","first","well","even",
        "back","good","much","before","here","just","know","take","great","think",
        "where","through","long","down","over","such","because","come","work"
    )

    /**
     * Main entry point.
     * 1. Try Hugging Face API (if token is set)
     * 2. Fall back to offline extraction if API fails
     */
    fun getKeywords(file: File, context: Context): List<String> {
        if (HuggingFaceConfig.API_TOKEN != "YOUR_HF_TOKEN_HERE") {
            val result = tryHuggingFace(file, context)
            if (!result.isNullOrEmpty()) {
                Log.d(TAG, "HF keywords for ${file.name}: $result")
                return result
            }
            Log.w(TAG, "HF failed for ${file.name}, using offline fallback")
        }
        return offlineKeywords(file, context)
    }

    // ── HUGGING FACE API ─────────────────────────────────────────────────────
    private fun tryHuggingFace(file: File, context: Context): List<String>? {
        return try {
            // Build input text: extracted content for text files, metadata for others
            val ext = file.extension.lowercase()
            val inputText = if (ext in TEXT_EXTENSIONS) {
                val extracted = TextExtractor.extract(file, context)
                if (!extracted.isNullOrBlank()) {
                    // Use first 500 chars of content + filename
                    "${file.name}: ${extracted.take(500)}"
                } else {
                    buildMetadataString(file)
                }
            } else {
                buildMetadataString(file)
            }

            // Build request body for zero-shot classification
            val requestJson = JSONObject().apply {
                put("inputs", inputText)
                put("parameters", JSONObject().apply {
                    put("candidate_labels", JSONArray(CANDIDATE_LABELS))
                    put("multi_label", true)  // allow multiple labels to match
                })
            }

            val body = requestJson.toString().toRequestBody("application/json".toMediaType())
            val request = Request.Builder()
                .url(HF_ENDPOINT)
                .addHeader("Authorization", "Bearer ${HuggingFaceConfig.API_TOKEN}")
                .post(body)
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: return null

            if (!response.isSuccessful) {
                Log.w(TAG, "HF error ${response.code}: $responseBody")
                return null
            }

            parseHuggingFaceResponse(responseBody)

        } catch (e: Exception) {
            Log.e(TAG, "HF exception for ${file.name}: ${e.message}")
            null
        }
    }

    /**
     * Parses HF zero-shot classification response.
     * Returns top 4 labels with score > 0.3
     */
    private fun parseHuggingFaceResponse(body: String): List<String>? {
        return try {
            val json = JSONObject(body)
            val labels = json.getJSONArray("labels")
            val scores = json.getJSONArray("scores")

            val results = mutableListOf<String>()
            for (i in 0 until labels.length()) {
                val score = scores.getDouble(i)
                if (score > 0.30) {  // only include confident matches
                    results.add(labels.getString(i).lowercase())
                }
                if (results.size >= 4) break  // max 4 tags
            }

            if (results.isEmpty()) null else results

        } catch (e: Exception) {
            Log.e(TAG, "HF parse error: ${e.message}")
            null
        }
    }

    private fun buildMetadataString(file: File): String {
        val ageDays = (System.currentTimeMillis() - file.lastModified()) / (1000 * 60 * 60 * 24)
        val sizeMb  = file.length() / (1024.0 * 1024.0)
        val ext     = file.extension.lowercase()
        return "${file.name} $ext file, ${"%.1f".format(sizeMb)}MB, ${ageDays} days old"
    }

    // ── OFFLINE FALLBACK ─────────────────────────────────────────────────────
    fun offlineKeywords(file: File, context: Context): List<String> {
        val ext = file.extension.lowercase()
        return if (ext in TEXT_EXTENSIONS) {
            val text = TextExtractor.extract(file, context)
            if (!text.isNullOrBlank()) {
                (extractKeywordsFromText(text) + typeTag(file)).distinct().take(5)
            } else {
                metadataTags(file)
            }
        } else {
            metadataTags(file)
        }
    }

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

    private fun isRealWord(word: String): Boolean {
        val vowels = setOf('a', 'e', 'i', 'o', 'u')
        if (word.none { it in vowels }) return false
        var consonantRun = 0
        for (ch in word) {
            if (ch !in vowels) consonantRun++ else consonantRun = 0
            if (consonantRun > 4) return false
        }
        val vowelRatio = word.count { it in vowels }.toDouble() / word.length
        return vowelRatio in 0.15..0.80
    }

    fun metadataTags(file: File): List<String> {
        val tags    = mutableListOf<String>()
        val ageDays = (System.currentTimeMillis() - file.lastModified()) / (1000 * 60 * 60 * 24)
        val sizeMb  = file.length() / (1024.0 * 1024.0)

        tags.add(typeTag(file))

        when {
            ageDays > 365 -> tags.add("very-old")
            ageDays > 90  -> tags.add("old")
            ageDays > 30  -> tags.add("aging")
            else          -> tags.add("recent")
        }

        when {
            sizeMb > 500 -> tags.add("huge")
            sizeMb > 100 -> tags.add("large")
            sizeMb > 10  -> tags.add("medium")
            else         -> tags.add("small")
        }

        val name = file.nameWithoutExtension.lowercase()
        when {
            name.contains("invoice") || name.contains("bill")      -> tags.add("invoice")
            name.contains("resume") || name.contains("cv")         -> tags.add("resume")
            name.contains("report")                                -> tags.add("report")
            name.contains("backup") || name.contains("bak")        -> tags.add("backup")
            name.contains("screenshot") || name.contains("screen") -> tags.add("screenshot")
            name.contains("whatsapp") || name.contains("wa")       -> tags.add("whatsapp")
            name.contains("download")                              -> tags.add("downloaded")
            name.contains("temp") || name.contains("tmp")          -> tags.add("temporary")
            name.contains("copy") || name.contains("duplicate")    -> tags.add("duplicate")
            name.contains("record") || name.contains("rec")        -> tags.add("recording")
        }

        return tags.distinct().take(5)
    }

    private fun typeTag(file: File) = when (file.extension.lowercase()) {
        "jpg", "jpeg", "png", "gif", "webp" -> "image"
        "mp4", "mkv", "avi", "mov"          -> "video"
        "mp3", "wav", "aac", "flac"         -> "audio"
        "pdf"                               -> "pdf"
        "doc", "docx"                       -> "document"
        "txt"                               -> "text"
        "apk"                               -> "apk"
        "zip", "rar", "7z"                  -> "archive"
        else                                -> "file"
    }
}