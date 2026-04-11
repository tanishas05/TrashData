package com.example.trashdata

import android.content.Context
import java.io.File

// PDF libs
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper

// DOCX libs
import org.apache.poi.xwpf.usermodel.XWPFDocument

object FileTextExtractor {

    fun extractText(context: Context, file: File): String {
        return try {
            when {
                file.name.endsWith(".txt") -> {
                    file.bufferedReader().use { it.readText() }
                }

                file.name.endsWith(".pdf") -> {
                    val document = PDDocument.load(file)
                    val stripper = PDFTextStripper()
                    val text = stripper.getText(document)
                    document.close()
                    text
                }

                file.name.endsWith(".docx") -> {
                    val doc = XWPFDocument(file.inputStream())
                    val text = doc.paragraphs.joinToString("\n") { it.text }
                    doc.close()
                    text
                }

                else -> ""
            }.take(2000) // limit text size

        } catch (e: Exception) {
            ""
        }
    }
}