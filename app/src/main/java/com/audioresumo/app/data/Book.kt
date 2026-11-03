package com.audioresumo.app.data

import java.time.Instant
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

enum class BookStatus { READY, PROCESSING, ERROR }

enum class BookFormat(val label: String) {
    EPUB("EPUB"), PDF("PDF"), DOCX("DOCX"), TXT("TXT"), TEXT("Texto"), PDF_OCR("PDF + OCR");

    companion object {
        fun from(value: String?): BookFormat = entries.firstOrNull { it.name == value } ?: TEXT
    }
}

data class BookChapter(
    val id: String,
    val index: Int,
    var title: String,
    var charCount: Int = 0,
    var wordCount: Int = 0,
    var summaryId: String? = null,
    var summaryStatus: String = "none",
    var text: String = ""
) {
    val numberLabel: String get() = "Capítulo ${index + 1}"
}

data class Book(
    val id: String,
    var title: String,
    var author: String,
    val format: BookFormat,
    val fileName: String,
    val sizeBytes: Long,
    val importedAt: LocalDateTime = LocalDateTime.now(),
    var updatedAt: LocalDateTime = LocalDateTime.now(),
    val chapters: MutableList<BookChapter> = mutableListOf(),
    var totalWords: Int = 0,
    var totalChars: Int = 0,
    var status: BookStatus = BookStatus.READY,
    var progress: Int = 0,
    var message: String = "",
    var summaryId: String? = null,
    var summaryMinutes: Int = 15,
    var summaryStatus: String = "none",
    var summaryProgress: Int = 0,
    var isFavorite: Boolean = false,
    var lastChapterIndex: Int = 0,
    var lastScroll: Int = 0,
    var coverColors: List<String> = listOf("#8B5CF6", "#6366F1", "#0B132B"),
    val blockDigests: MutableMap<String, String> = mutableMapOf()
) {
    val chapterCount: Int get() = chapters.size
    val progressLabel: String get() = "$chapterCount ${if (chapterCount == 1) "capítulo" else "capítulos"}"
    val wordsLabel: String get() = if (totalWords >= 1000) String.format(Locale("pt", "BR"), "%.1f mil palavras", totalWords / 1000.0) else "$totalWords palavras"
    val initial: String get() = title.trim().take(1).uppercase().ifBlank { "L" }
    val colorStart: String get() = coverColors.getOrElse(0) { "#8B5CF6" }
    val colorMid: String get() = coverColors.getOrElse(1) { "#6366F1" }
    val colorEnd: String get() = coverColors.getOrElse(2) { "#0B132B" }

    fun dateLabel(): String =
        DateTimeFormatter.ofPattern("dd MMM yyyy", Locale("pt", "BR")).format(importedAt)

    fun updateCounts() {
        totalWords = chapters.sumOf { it.wordCount }
        totalChars = chapters.sumOf { it.charCount }
    }

    fun copyMetadata(): Book = copy(chapters = chapters.map { it.copy(text = "") }.toMutableList())

    companion object {
        fun colorsFor(seed: String): List<String> {
            val palettes = listOf(
                listOf("#8B5CF6", "#6366F1", "#0B132B"),
                listOf("#F59E0B", "#EA580C", "#7C2D12"),
                listOf("#10B981", "#0D9488", "#083344"),
                listOf("#EC4899", "#BE185D", "#3B0764"),
                listOf("#38BDF8", "#2563EB", "#172554")
            )
            return palettes[Math.floorMod(seed.hashCode(), palettes.size)]
        }

        fun fromJsonTitle(title: String): String = title.trim().ifBlank { "Livro sem título" }
    }
}

data class BookMatch(val book: Book, val snippet: String = "")
