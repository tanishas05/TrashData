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

object GeminiAnalyzer {

    private const val TAG = "GeminiAnalyzer"
    private const val ENDPOINT =
        "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash-lite:generateContent"

    // Text-based extensions that support content extraction
    private val TEXT_EXTENSIONS = setOf("txt", "pdf", "doc", "docx")

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    /**
     * Main entry point. Decides whether to use text extraction or metadata-only
     * depending on the file type, then calls Gemini.
     */
    fun getKeywords(file: File, context: Context): List<String> {
        val ext = file.extension.lowercase()

        return if (ext in TEXT_EXTENSIONS) {
            getKeywordsFromText(file, context)
        } else {
            getKeywordsFromMetadata(file)
        }
    }

    // ── TEXT-BASED FILES: extract content → Gemini ───────────────────────────
    private fun getKeywordsFromText(file: File, context: Context): List<String> {
        val extractedText = TextExtractor.extract(file, context)

        // If extraction failed or returned nothing, fall back to metadata
        if (extractedText.isNullOrBlank()) {
            Log.w(TAG, "No text extracted from ${file.name}, using metadata fallback")
            return getKeywordsFromMetadata(file)
        }

        Log.d(TAG, "Extracted ${extractedText.length} chars from ${file.name}")

        val prompt = """
You are a file content analysis assistant.
Read the following text extracted from a file and return ONLY a JSON array of 4-6 short lowercase keyword tags
that describe the content, topic, or purpose of the file.
Do not explain. No markdown. Just the JSON array.

File name : ${file.name}
Extracted text:
\"\"\"
$extractedText
\"\"\"

Example output: ["invoice","finance","2023","payment","important"]
""".trimIndent()

        return callGemini(prompt, file) ?: fallbackTags(file)
    }

    // ── NON-TEXT FILES: metadata only → Gemini ───────────────────────────────
    private fun getKeywordsFromMetadata(file: File): List<String> {
        val ageMs   = System.currentTimeMillis() - file.lastModified()
        val ageDays = ageMs / (1000 * 60 * 60 * 24)
        val sizeMb  = file.length() / (1024.0 * 1024.0)
        val ext     = file.extension.lowercase().ifEmpty { "unknown" }

        val prompt = """
You are a file analysis assistant.
Given the following file metadata, return ONLY a JSON array of 3-5 short lowercase keyword tags
that describe the file's nature, potential risk, or category.
Do not explain. No markdown. Just the JSON array.

File name : ${file.name}
Extension : .$ext
Size      : ${"%.2f".format(sizeMb)} MB
Age       : $ageDays days old

Example output: ["old","large","video","unused","duplicate-risk"]
""".trimIndent()

        return callGemini(prompt, file) ?: fallbackTags(file)
    }

    // ── SHARED GEMINI CALL ────────────────────────────────────────────────────
    private fun callGemini(prompt: String, file: File): List<String>? {
        if (GeminiConfig.API_KEY == "YOUR_GEMINI_API_KEY_HERE") return null

        return try {
            Thread.sleep(500) // rate limiting — max ~2 calls/sec

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
                Log.w(TAG, "Gemini error ${response.code} for ${file.name}: $responseBody")
                return null
            }

            Log.d(TAG, "Gemini SUCCESS for ${file.name}")
            parseKeywords(responseBody)

        } catch (e: Exception) {
            Log.e(TAG, "Gemini call failed for ${file.name}: ${e.message}")
            null
        }
    }

    // ── RESPONSE PARSER ───────────────────────────────────────────────────────
    private fun parseKeywords(responseBody: String): List<String>? {
        return try {
            val root = JSONObject(responseBody)
            val text = root
                .getJSONArray("candidates")
                .getJSONObject(0)
                .getJSONObject("content")
                .getJSONArray("parts")
                .getJSONObject(0)
                .getString("text")
                .trim()

            val clean = text
                .removePrefix("```json").removePrefix("```")
                .removeSuffix("```")
                .trim()

            val arr = JSONArray(clean)
            (0 until arr.length()).map { arr.getString(it).lowercase().trim() }

        } catch (e: Exception) {
            Log.e(TAG, "Parse failed: ${e.message}")
            null
        }
    }

    // ── OFFLINE FALLBACK ──────────────────────────────────────────────────────
    fun fallbackTags(file: File): List<String> {
        val tags    = mutableListOf<String>()
        val ext     = file.extension.lowercase()
        val ageDays = (System.currentTimeMillis() - file.lastModified()) / (1000 * 60 * 60 * 24)
        val sizeMb  = file.length() / (1024.0 * 1024.0)

        when (ext) {
            "jpg", "jpeg", "png", "gif", "webp" -> tags.add("image")
            "mp4", "mkv", "avi", "mov"          -> tags.add("video")
            "mp3", "wav", "aac", "flac"         -> tags.add("audio")
            "pdf"                               -> tags.add("pdf")
            "doc", "docx"                       -> tags.add("document")
            "txt"                               -> tags.add("text")
            "apk"                               -> tags.add("apk")
            "zip", "rar", "7z"                  -> tags.add("archive")
            else                                -> tags.add("file")
        }

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

        return tags
    }
}