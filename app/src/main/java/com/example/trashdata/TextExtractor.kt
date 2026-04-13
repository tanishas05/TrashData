package com.example.trashdata

import android.content.Context
import android.util.Log
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import java.io.File
import java.util.zip.ZipFile

object TextExtractor {
    private const val TAG = "TextExtractor"
    private const val MAX_CHARS = 1500
    private var pdfBoxInitialized = false
    fun extract(file: File, context: Context): String? {
        return try {
            when (file.extension.lowercase()) {
                "txt"  -> extractTxt(file)
                "pdf"  -> extractPdf(file, context)
                "docx" -> extractDocx(file)
                "doc"  -> extractDoc(file)
                else   -> null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Extraction failed for ${file.name}: ${e.message}")
            null
        }
    }
    private fun extractTxt(file: File): String? {
        val text = file.readText(Charsets.UTF_8).trim()
        return if (text.isBlank()) null else text.take(MAX_CHARS)
    }
    private fun extractPdf(file: File, context: Context): String? {
        return try {
            if (!pdfBoxInitialized) {
                PDFBoxResourceLoader.init(context)
                pdfBoxInitialized = true
            }
            val document = PDDocument.load(file)
            val stripper = PDFTextStripper().apply {
                startPage = 1
                endPage = minOf(3, document.numberOfPages)
            }
            val text = stripper.getText(document).trim()
            document.close()
            if (text.isBlank()) null else text.take(MAX_CHARS)
        } catch (e: Exception) {
            Log.e(TAG, "PDF extraction error: ${e.message}")
            null
        }
    }
    private fun extractDocx(file: File): String? {
        return try {
            val zip = ZipFile(file)
            val entry = zip.getEntry("word/document.xml") ?: run {
                zip.close(); return null
            }
            val xml = zip.getInputStream(entry).bufferedReader(Charsets.UTF_8).readText()
            zip.close()
            val text = xml
                .replace(Regex("<[^>]+>"), " ")
                .replace(Regex("\\s+"), " ")
                .trim()
            if (text.isBlank()) null else text.take(MAX_CHARS)
        } catch (e: Exception) {
            Log.e(TAG, "DOCX extraction error: ${e.message}")
            null
        }
    }
    private fun extractDoc(file: File): String? {
        return try {
            val bytes = file.readBytes()
            val text = StringBuilder()
            var i = 0
            while (i < bytes.size) {
                val b = bytes[i].toInt() and 0xFF
                if (b in 32..126) {
                    val start = i
                    while (i < bytes.size && (bytes[i].toInt() and 0xFF) in 32..126) i++
                    val word = String(bytes, start, i - start, Charsets.US_ASCII).trim()
                    val cleanWords = word.split(" ").filter { w ->
                        w.length in 4..20 && w.any { it in "aeiouAEIOU" }
                    }
                    if (cleanWords.isNotEmpty()) text.append(cleanWords.joinToString(" ")).append(" ")
                } else {
                    i++
                }
                if (text.length >= MAX_CHARS) break
            }
            val result = text.toString().trim()
            if (result.isBlank()) null else result.take(MAX_CHARS)
        } catch (e: Exception) {
            Log.e(TAG, "DOC extraction error: ${e.message}")
            null
        }
    }
}