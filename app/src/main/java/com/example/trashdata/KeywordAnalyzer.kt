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

    private const val GROQ_ENDPOINT =
        "https://api.groq.com/openai/v1/chat/completions"
    private const val MODEL = "llama-3.3-70b-versatile"

    private val TEXT_EXTENSIONS = setOf("txt", "pdf", "doc", "docx")

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
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

    fun getKeywords(file: File, context: Context): List<String> {
        if (GroqConfig.API_KEY.isNotBlank()) {
            val result = tryGroq(file, context)
            if (!result.isNullOrEmpty()) {
                Log.d(TAG, "Groq keywords for ${file.name}: $result")
                return result
            }
            Log.w(TAG, "Groq failed for ${file.name}, using offline fallback")
        }
        return offlineKeywords(file, context)
    }

    private fun tryGroq(file: File, context: Context): List<String>? {
        return try {
            val ext = file.extension.lowercase()

            val userMessage = if (ext in TEXT_EXTENSIONS) {
                val extracted = TextExtractor.extract(file, context)
                if (!extracted.isNullOrBlank()) {
                    """
File name: ${file.name}
File content (first 600 chars):
${extracted.take(600)}
                    """.trimIndent()
                } else {
                    buildMetadataString(file)
                }
            } else {
                buildMetadataString(file)
            }

            val messages = JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "system")
                    put("content",
                        "You are a file analysis assistant. " +
                                "When given file information, respond with ONLY a JSON array of 4-6 short lowercase keyword tags. " +
                                "No explanation. No markdown. No extra text. Just the JSON array. " +
                                "Example: [\"invoice\",\"finance\",\"2024\",\"important\"]"
                    )
                })
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", userMessage)
                })
            }

            val requestJson = JSONObject().apply {
                put("model", MODEL)
                put("messages", messages)
                put("temperature", 0.2)
                put("max_tokens", 100)
            }

            Log.d(TAG, "Sending to Groq for ${file.name}: $userMessage")
            val body = requestJson.toString().toRequestBody("application/json".toMediaType())
            val request = Request.Builder()
                .url(GROQ_ENDPOINT)
                .addHeader("Authorization", "Bearer ${GroqConfig.API_KEY}")
                .addHeader("Content-Type", "application/json")
                .post(body)
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: return null

            if (!response.isSuccessful) {
                Log.w(TAG, "Groq error ${response.code} for ${file.name}: $responseBody")
                return null
            }

            Log.d(TAG, "Groq raw response for ${file.name}: $responseBody")
            parseGroqResponse(responseBody)

        } catch (e: Exception) {
            Log.e(TAG, "Groq exception for ${file.name}: ${e.message}")
            null
        }
    }

    private fun buildMetadataString(file: File): String {
        val ageDays = (System.currentTimeMillis() - file.lastModified()) / (1000 * 60 * 60 * 24)
        val sizeMb  = file.length() / (1024.0 * 1024.0)
        val ext     = file.extension.lowercase()
        return "File name: ${file.name}, Type: .$ext, Size: ${"%.1f".format(sizeMb)}MB, Age: ${ageDays} days old"
    }

    private fun parseGroqResponse(body: String): List<String>? {
        return try {
            val text = JSONObject(body)
                .getJSONArray("choices")
                .getJSONObject(0)
                .getJSONObject("message")
                .getString("content")
                .trim()
                .removePrefix("```json")
                .removePrefix("```")
                .removeSuffix("```")
                .trim()

            val arr = JSONArray(text)
            (0 until arr.length()).map { arr.getString(it).lowercase().trim() }
        } catch (e: Exception) {
            Log.e(TAG, "Groq parse error: ${e.message}")
            null
        }
    }

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
        val ageMinutes = (System.currentTimeMillis() - file.lastModified()) / (1000 * 60)
        val sizeMb  = file.length() / (1024.0 * 1024.0)

        tags.add(typeTag(file))

        /*when {
             ageDays > 365 -> tags.add("very-old")
             ageDays > 90  -> tags.add("old")
             ageDays > 30  -> tags.add("aging")
             else          -> tags.add("recent")
         }*/
        if (ageMinutes > 15) {
            tags.add("old")
        } else {
            tags.add("recent")
        }
        when {
            sizeMb > 100 -> tags.add("huge")
            sizeMb > 10 -> tags.add("large")
            sizeMb > 1  -> tags.add("medium")
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