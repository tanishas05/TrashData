package com.example.trashdata

import java.io.File

object KeywordExtractor {

    // Common useless words to ignore
    private val stopWords = setOf(
        "the", "is", "and", "to", "of", "in", "for", "on", "with",
        "a", "an", "this", "that", "it", "as", "at", "by"
    )
    fun extractFromText(text: String): List<String> {
        return try {
            text.lowercase()
                .replace(Regex("[^a-z ]"), " ")
                .split(" ")
                .filter { it.length > 4 && it !in stopWords }
                .groupingBy { it }
                .eachCount()
                .entries
                .sortedByDescending { it.value }
                .take(5)
                .map { it.key }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun extract(file: File): List<String> {
        return try {

            // ✅ Read only small part (VERY IMPORTANT)
            val text = file.bufferedReader().use {
                it.readText().take(2000)
            }

            text.lowercase()
                .replace(Regex("[^a-z ]"), " ")   // remove symbols
                .split(" ")                       // split words
                .filter { it.length > 4 && it !in stopWords } // remove small/useless words
                .groupingBy { it }
                .eachCount()                     // count frequency
                .entries
                .sortedByDescending { it.value } // most frequent first
                .take(5)                         // top 5 keywords
                .map { it.key }

        } catch (e: Exception) {
            emptyList()
        }
    }
}