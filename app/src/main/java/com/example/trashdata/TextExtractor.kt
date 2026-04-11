package com.example.trashdata

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.util.Log
import java.io.File
import java.util.zip.ZipFile

object TextExtractor {

    private const val TAG = "TextExtractor"
    private const val MAX_CHARS = 1500  // cap sent to Gemini to save tokens

    /**
     * Returns extracted text from the file, or null if extraction failed / not supported.
     */
    fun extract(file: File, context: Context): String? {
        return try {
            when (file.extension.lowercase()) {
                "txt"        -> extractTxt(file)
                "pdf"        -> extractPdf(file, context)
                "doc"        -> extractDoc(file)
                "docx"       -> extractDocx(file)
                else         -> null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Extraction failed for ${file.name}: ${e.message}")
            null
        }
    }

    // ── TXT ──────────────────────────────────────────────────────────────────
    private fun extractTxt(file: File): String? {
        val text = file.readText(Charsets.UTF_8).trim()
        if (text.isBlank()) return null
        return text.take(MAX_CHARS)
    }

    // ── PDF ──────────────────────────────────────────────────────────────────
    // Uses Android's built-in PdfRenderer to render pages to bitmap,
    // then reads text via a simple heuristic (first 3 pages).
    // For proper text extraction we use the file's raw bytes to find text streams.
    private fun extractPdf(file: File, context: Context): String? {
        return try {
            val text = StringBuilder()
            val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            val renderer = PdfRenderer(pfd)
            val pagesToRead = minOf(renderer.pageCount, 3)

            // PdfRenderer only renders visually — for text we parse raw PDF bytes
            renderer.close()
            pfd.close()

            // Raw PDF text stream extraction (works for most text-based PDFs)
            val raw = file.readBytes().toString(Charsets.ISO_8859_1)
            val streamRegex = Regex("stream\\r?\\n(.*?)\\r?\\nendstream", RegexOption.DOT_MATCHES_ALL)
            val matches = streamRegex.findAll(raw)
            for (match in matches) {
                val content = match.groupValues[1]
                // Extract readable ASCII text from PDF content streams
                val readable = Regex("[A-Za-z][A-Za-z\\s,.!?'\"]{4,}").findAll(content)
                    .map { it.value.trim() }
                    .filter { it.length > 4 }
                    .joinToString(" ")
                if (readable.isNotBlank()) text.append(readable).append(" ")
                if (text.length >= MAX_CHARS) break
            }

            val result = text.toString().trim()
            if (result.isBlank()) null else result.take(MAX_CHARS)
        } catch (e: Exception) {
            Log.e(TAG, "PDF extraction error: ${e.message}")
            null
        }
    }

    // ── DOC (legacy binary format) ────────────────────────────────────────────
    // Reads raw bytes and extracts printable ASCII runs (good enough for keywords)
    private fun extractDoc(file: File): String? {
        val bytes = file.readBytes()
        val text = StringBuilder()
        var i = 0
        while (i < bytes.size) {
            val b = bytes[i].toInt() and 0xFF
            if (b in 32..126) {
                val start = i
                while (i < bytes.size && (bytes[i].toInt() and 0xFF) in 32..126) i++
                val word = String(bytes, start, i - start, Charsets.US_ASCII).trim()
                if (word.length >= 4) text.append(word).append(" ")
            } else {
                i++
            }
            if (text.length >= MAX_CHARS) break
        }
        val result = text.toString().trim()
        return if (result.isBlank()) null else result.take(MAX_CHARS)
    }

    // ── DOCX (Open XML format — it's a ZIP with word/document.xml inside) ────
    private fun extractDocx(file: File): String? {
        return try {
            val zip = ZipFile(file)
            val entry = zip.getEntry("word/document.xml") ?: run {
                zip.close()
                return null
            }
            val xml = zip.getInputStream(entry).bufferedReader(Charsets.UTF_8).readText()
            zip.close()

            // Strip XML tags, keep text content
            val text = xml
                .replace(Regex("<[^>]+>"), " ")   // remove tags
                .replace(Regex("\\s+"), " ")       // collapse whitespace
                .trim()

            if (text.isBlank()) null else text.take(MAX_CHARS)
        } catch (e: Exception) {
            Log.e(TAG, "DOCX extraction error: ${e.message}")
            null
        }
    }
}