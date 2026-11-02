package com.audioresumo.app.service

import com.audioresumo.app.data.AudioSummary

data class SpeechParagraph(
    val text: String,
    val sectionIndex: Int,
    val sectionTitle: String,
    val startSeconds: Int
)

data class SpeechState(
    val summaryId: String? = null,
    val title: String = "",
    val author: String = "",
    val coverColors: List<String> = emptyList(),
    val isPlaying: Boolean = false,
    val paragraphIndex: Int = 0,
    val totalParagraphs: Int = 0,
    val currentTime: Int = 0,
    val duration: Int = 0,
    val speed: Float = 1f,
    val sleepMinutes: Int? = null,
    val error: String? = null
) {
    val progressPercent: Int
        get() = if (duration <= 0) 0 else ((currentTime.toFloat() / duration) * 100).toInt().coerceIn(0, 100)
}

object SpeechContent {
    fun build(summary: AudioSummary): List<SpeechParagraph> {
        val raw = mutableListOf<Pair<String, Pair<Int, String>>>()
        raw.add(
            summary.executiveSummary to (-1 to "Visão geral")
        )
        summary.sections.forEachIndexed { index, section ->
            val text = buildString {
                append(section.title).append(". ")
                append(section.content)
                section.practicalExample?.let { append(" Exemplo prático: ").append(it) }
                section.keyQuote?.let { append(" Citação: ").append(it) }
            }
            raw.add(text to (index to section.title))
        }
        if (summary.keyLearnings.isNotEmpty()) {
            raw.add(
                "Principais aprendizados. " + summary.keyLearnings.joinToString(" ") { "${it.title}: ${it.description}" }
                    to (-2 to "Principais aprendizados")
            )
        }
        if (summary.conclusion.isNotBlank()) {
            raw.add("Conclusão. ${summary.conclusion}" to (-3 to "Conclusão"))
        }

        val totalWords = raw.sumOf { it.first.split(" ").size }.coerceAtLeast(1)
        var cumulative = 0
        return raw.map { (text, meta) ->
            val start = cumulative
            cumulative += (summary.estimatedMinutes * 60f * (text.split(" ").size.toFloat() / totalWords)).toInt()
            SpeechParagraph(text, meta.first, meta.second, start)
        }
    }

    fun currentSummary(state: SpeechState): AudioSummary? =
        state.summaryId?.let { com.audioresumo.app.data.Store.byId(it) }
}
