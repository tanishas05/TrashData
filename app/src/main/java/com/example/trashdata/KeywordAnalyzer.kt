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
    private const val ENDPOINT =
        "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash-lite:generateContent"

    private val TEXT_EXTENSIONS = setOf("txt", "pdf", "doc", "docx")

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

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

    /**
     * Main entry point.
     * 1. Try Gemini API (if key is set and quota available)
     * 2. Fall back to offline extraction if Gemini fails
     */
    fun getKeywords(file: File, context: Context): List<String> {
        // Try Gemini first if key is configured
        if (GeminiConfig.API_KEY != "YOUR_GEMINI_API_KEY_HERE") {
            val geminiResult = tryGemini(file, context)
            if (!geminiResult.isNullOrEmpty()) {
                Log.d(TAG, "Gemini keywords for ${file.name}: $geminiResult")
                return geminiResult
            }
            Log.w(TAG, "Gemini failed for ${file.name}, using offline fallback")
        }

        // Offline fallback
        return offlineKeywords(file, context)
    }

    // ── GEMINI (primary) ─────────────────────────────────────────────────────
    private fun tryGemini(file: File, context: Context): List<String>? {
        return try {
            Thread.sleep(300)

            val ext = file.extension.lowercase()
            val prompt = if (ext in TEXT_EXTENSIONS) {
                val text = TextExtractor.extract(file, context)
                if (!text.isNullOrBlank()) buildTextPrompt(file, text)
                else buildMetadataPrompt(file)
            } else {
                buildMetadataPrompt(file)
            }

            val requestJson = JSONObject().apply {
                put("contents", JSONArray().apply {
                    put(JSONObject().apply {
                        put("parts", JSONArray().apply {
                            put(JSONObject().apply { put("text", prompt) })
                        })
                    })
                })
                put("generationConfig", JSONObject().apply {
                    put("temperature", 0.2)
                    put("maxOutputTokens", 100)
                })
            }

            val body = requestJson.toString().toRequestBody("application/json".toMediaType())
            val request = Request.Builder()
                .url("$ENDPOINT?key=${GeminiConfig.API_KEY}")
                .post(body)
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: return null

            if (!response.isSuccessful) {
                Log.w(TAG, "Gemini ${response.code} for ${file.name}")
                return null
            }

            parseGeminiResponse(responseBody)

        } catch (e: Exception) {
            Log.e(TAG, "Gemini exception for ${file.name}: ${e.message}")
            null
        }
    }

    private fun buildTextPrompt(file: File, text: String) = """
You are a file content analysis assistant.
Return ONLY a JSON array of 4-6 short lowercase keyword tags describing the content.
No explanation. No markdown. Just the JSON array.

File: ${file.name}
Content: $text

Example: ["invoice","finance","2024","payment"]
""".trimIndent()

    private fun buildMetadataPrompt(file: File): String {
        val ageDays = (System.currentTimeMillis() - file.lastModified()) / (1000 * 60 * 60 * 24)
        val sizeMb  = file.length() / (1024.0 * 1024.0)
        return """
You are a file analysis assistant.
Return ONLY a JSON array of 3-5 short lowercase keyword tags describing this file.
No explanation. No markdown. Just the JSON array.

File: ${file.name}, Size: ${"%.1f".format(sizeMb)}MB, Age: ${ageDays}d

Example: ["old","large","video","unused"]
""".trimIndent()
    }

    private fun parseGeminiResponse(body: String): List<String>? {
        return try {
            val text = JSONObject(body)
                .getJSONArray("candidates").getJSONObject(0)
                .getJSONObject("content")
                .getJSONArray("parts").getJSONObject(0)
                .getString("text").trim()
                .removePrefix("```json").removePrefix("```")
                .removeSuffix("```").trim()
            val arr = JSONArray(text)
            (0 until arr.length()).map { arr.getString(it).lowercase().trim() }
        } catch (e: Exception) { null }
    }

    // ── OFFLINE FALLBACK (always works, no internet needed) ──────────────────
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