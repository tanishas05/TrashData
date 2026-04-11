package com.example.trashdata

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
        "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent"

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    /**
     * Analyzes a file's metadata (name, size, extension, age) and returns
     * a list of short keyword tags (e.g. ["old", "large", "image", "unused"]).
     *
     * This runs on a background thread — call it from a coroutine or thread pool.
     */
    fun getKeywords(file: File): List<String> {
        return try {
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

            val requestJson = JSONObject().apply {
                put("contents", JSONArray().apply {
                    put(JSONObject().apply {
                        put("parts", JSONArray().apply {
                            put(JSONObject().apply {
                                put("text", prompt)
                            })
                        })
                    })
                })
                put("generationConfig", JSONObject().apply {
                    put("temperature", 0.2)
                    put("maxOutputTokens", 100)
                })
            }

            val body = requestJson.toString()
                .toRequestBody("application/json".toMediaType())

            val request = Request.Builder()
                .url("$ENDPOINT?key=${GeminiConfig.API_KEY}")
                .post(body)
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: return fallbackTags(file)

            if (!response.isSuccessful) {
                Log.w(TAG, "Gemini error ${response.code}: $responseBody")
                return fallbackTags(file)
            }

            parseKeywords(responseBody)

        } catch (e: Exception) {
            Log.e(TAG, "Gemini call failed for ${file.name}: ${e.message}")
            fallbackTags(file)
        }
    }

    /** Parses the Gemini response and extracts the keyword array. */
    private fun parseKeywords(responseBody: String): List<String> {
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

            // Strip any accidental markdown fences
            val clean = text
                .removePrefix("```json").removePrefix("```")
                .removeSuffix("```")
                .trim()

            val arr = JSONArray(clean)
            (0 until arr.length()).map { arr.getString(it).lowercase().trim() }

        } catch (e: Exception) {
            Log.e(TAG, "Parse failed: ${e.message}")
            emptyList()
        }
    }

    /**
     * Offline fallback — generates simple rule-based tags when the API
     * is unavailable or the key hasn't been set yet.
     */
    fun fallbackTags(file: File): List<String> {
        val tags = mutableListOf<String>()
        val ext  = file.extension.lowercase()
        val ageMs = System.currentTimeMillis() - file.lastModified()
        val ageDays = ageMs / (1000 * 60 * 60 * 24)
        val sizeMb  = file.length() / (1024.0 * 1024.0)

        // Type tag
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

        // Age tag
        when {
            ageDays > 365 -> tags.add("very-old")
            ageDays > 90  -> tags.add("old")
            ageDays > 30  -> tags.add("aging")
            else          -> tags.add("recent")
        }

        // Size tag
        when {
            sizeMb > 500  -> tags.add("huge")
            sizeMb > 100  -> tags.add("large")
            sizeMb > 10   -> tags.add("medium")
            else          -> tags.add("small")
        }

        return tags
    }
}