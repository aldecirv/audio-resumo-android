package com.audioresumo.app.data

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class SummarySection(
    val id: String,
    val title: String,
    val timestampSeconds: Int,
    val content: String,
    val practicalExample: String? = null,
    val keyQuote: String? = null
)

data class KeyLearning(val title: String, val description: String)

data class KeyConcept(
    val term: String,
    val simpleExplanation: String,
    val analogy: String? = null
)

data class PracticalExample(val scenario: String, val actionableAdvice: String)

data class MindMapSubBranch(val id: String, val label: String, val detail: String? = null)
data class MindMapBranch(
    val id: String,
    val label: String,
    val summary: String? = null,
    val subBranches: List<MindMapSubBranch> = emptyList()
)
data class MindMapRoot(val id: String, val label: String, val branches: List<MindMapBranch>)
data class MindMapData(val root: MindMapRoot)

data class Flashcard(
    val id: String,
    val front: String,
    val back: String,
    val hint: String? = null
)

data class QuizQuestion(
    val id: String,
    val question: String,
    val options: List<String>,
    val correctIndex: Int,
    val explanation: String
)

data class ReviewPlan(
    val day1: String,
    val day3: String,
    val day7: String,
    val day14: String
) {
    fun entries(): List<Pair<String, String>> = listOf(
        "Dia 1" to day1, "Dia 3" to day3, "Dia 7" to day7, "Dia 14" to day14
    )
}

data class AudioSummary(
    val id: String,
    val title: String,
    val subtitle: String,
    val author: String,
    val category: String,
    val format: String,
    val estimatedMinutes: Int,
    val coverColors: List<String>,
    val executiveSummary: String,
    val sections: List<SummarySection>,
    val keyLearnings: List<KeyLearning>,
    val keyConcepts: List<KeyConcept>,
    val practicalExamples: List<PracticalExample>,
    val conclusion: String,
    val mindMap: MindMapData,
    val flashcards: List<Flashcard>,
    val quiz: List<QuizQuestion>,
    val reviewPlan: ReviewPlan,
    val listensCount: Int = 0,
    val rating: Double = 4.8,
    var isFavorite: Boolean = false,
    var progressPercent: Int = 0,
    var progressSeconds: Int = 0,
    var quizCorrect: Int = 0,
    var quizTotal: Int = 0,
    val sourceType: String = "curated",
    val isUserGenerated: Boolean = false,
    val createdAt: String = Instant.now().toString(),
    val parentBookId: String? = null,
    val chapterIndex: Int? = null,
    val isBookSummary: Boolean = false,
    val summaryMinutes: Int = 0
) {
    val formatLabel: String
        get() = when (format) {
            "quick" -> "Rápido"
            "complete" -> "Completo"
            "deep" -> "Profundo"
            else -> "Essencial"
        }

    val durationLabel: String get() = "$estimatedMinutes min"
    val coverInitial: String get() = title.trim().take(1).uppercase()
    val colorStart: String get() = coverColors.getOrElse(0) { "#8B5CF6" }
    val colorMid: String get() = coverColors.getOrElse(1) { "#6366F1" }
    val colorEnd: String get() = coverColors.getOrElse(2) { "#0B132B" }

    fun sectionAt(index: Int): SummarySection? = sections.getOrNull(index)
}

data class Bookmark(
    val id: String,
    val summaryId: String,
    val paragraphIndex: Int,
    val sectionTitle: String,
    val note: String = "",
    val createdAt: String = Instant.now().toString()
)

data class HistoryEntry(
    val summaryId: String,
    val progressPercentage: Int,
    val listenedAt: String = Instant.now().toString()
)

data class UserAccount(
    var name: String = "Leitor",
    var email: String = "leitor@audioresumo.app",
    var plan: String = "free",
    var streakDays: Int = 7,
    var totalAudioMinutes: Int = 42,
    var completedQuizzes: Int = 3,
    val bookmarks: MutableList<Bookmark> = mutableListOf(),
    val history: MutableList<HistoryEntry> = mutableListOf()
) {
    val firstName: String get() = name.trim().split(" ").firstOrNull().orEmpty().ifBlank { "Leitor" }
    val initial: String get() = firstName.take(1).uppercase()
    val isPremium: Boolean get() = plan == "premium"

    fun bookmarkFor(summaryId: String, paragraphIndex: Int): Bookmark? =
        bookmarks.firstOrNull { it.summaryId == summaryId && it.paragraphIndex == paragraphIndex }
}

data class AppSettings(
    var apiKey: String = "",
    var model: String = "gemini-2.5-flash",
    var speechRate: Float = 1.0f,
    var preferredVoice: String = "",
    var dailyGoalMinutes: Int = 15,
    var onboarded: Boolean = false
)

fun today(): LocalDate = LocalDate.now()
fun isoNow(): String = Instant.now().toString()
fun prettyDate(iso: String): String = runCatching {
    val date = Instant.parse(iso).atZone(ZoneId.systemDefault()).toLocalDate()
    java.time.format.DateTimeFormatter.ofPattern("dd MMM", java.util.Locale("pt", "BR")).format(date)
}.getOrDefault("hoje")
