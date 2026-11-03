package com.audioresumo.app.util

import com.audioresumo.app.data.AudioSummary
import com.audioresumo.app.data.Flashcard
import com.audioresumo.app.data.KeyConcept
import com.audioresumo.app.data.KeyLearning
import com.audioresumo.app.data.MindMapBranch
import com.audioresumo.app.data.MindMapData
import com.audioresumo.app.data.MindMapRoot
import com.audioresumo.app.data.MindMapSubBranch
import com.audioresumo.app.data.PracticalExample
import com.audioresumo.app.data.QuizQuestion
import com.audioresumo.app.data.ReviewPlan
import com.audioresumo.app.data.SummarySection
import java.util.UUID

/** Resumidor extrativo offline: garante que o app funcione sem chave de API. */
object LocalSummarizer {

    private val palettes = listOf(
        listOf("#8B5CF6", "#6366F1", "#0B132B"),
        listOf("#F59E0B", "#EA580C", "#7C2D12"),
        listOf("#10B981", "#0D9488", "#083344"),
        listOf("#EC4899", "#BE185D", "#3B0764"),
        listOf("#38BDF8", "#2563EB", "#172554")
    )

    fun summarize(
        text: String,
        title: String = "",
        author: String = "",
        format: String = "essential",
        sourceType: String = "text",
        minutesOverride: Int? = null
    ): Result<AudioSummary> {
        val clean = cleanText(text)
        if (clean.length < 120) {
            return Result.failure(IllegalArgumentException("Texto muito curto para gerar um resumo (mínimo 120 caracteres)."))
        }
        val sentences = splitSentences(clean)
        if (sentences.size < 3) {
            return Result.failure(IllegalArgumentException("Não há frases suficientes para resumir este conteúdo."))
        }
        val minutes = minutesOverride ?: when (format) {
            "quick" -> 2
            "complete" -> 10
            "deep" -> 20
            else -> 5
        }
        val chunks = chunk(sentences, if (minutes <= 5) 3 else 4)
        val sectionTitles = listOf("Ideia central", "Como funciona", "Aplicação prática", "Aprofundamento").take(chunks.size)
        val sections = chunks.mapIndexed { index, part ->
            val content = part.joinToString(" ").take(1400)
            SummarySection(
                id = "sec-$index",
                title = sectionTitles.getOrElse(index) { "Parte ${index + 1}" },
                timestampSeconds = index * (minutes * 60 / chunks.size.coerceAtLeast(1)),
                content = content,
                practicalExample = if (index == chunks.lastIndex) {
                    "Escolha uma ideia deste trecho e aplique por 7 dias, anotando o que funcionou."
                } else null,
                keyQuote = part.firstOrNull()?.takeIf { it.length in 40..220 }
            )
        }
        val learnings = chunks.take(4).mapIndexed { index, part ->
            KeyLearning(
                title = sectionTitles.getOrElse(index) { "Aprendizado ${index + 1}" },
                description = part.firstOrNull()?.take(240) ?: ""
            )
        }
        val concepts = chunks.take(3).mapIndexed { index, part ->
            KeyConcept(
                term = sectionTitles.getOrElse(index) { "Conceito ${index + 1}" },
                simpleExplanation = part.firstOrNull()?.take(240) ?: "",
                analogy = null
            )
        }
        val branches = chunks.mapIndexed { index, part ->
            MindMapBranch(
                id = "branch-$index",
                label = sectionTitles.getOrElse(index) { "Parte ${index + 1}" },
                summary = part.firstOrNull()?.take(140),
                subBranches = part.drop(1).take(2).mapIndexed { subIndex, sentence ->
                    MindMapSubBranch("sub-$index-$subIndex", sentence.take(48).trimEnd('.', ','), sentence.take(160))
                }
            )
        }
        val flashcards = chunks.take(5).mapIndexed { index, part ->
            Flashcard(
                id = "fc-$index",
                front = "O que o conteúdo ensina sobre ${sectionTitles.getOrElse(index) { "este trecho" }}?",
                back = part.take(2).joinToString(" ").take(400),
                hint = "Reveja a seção ${index + 1}."
            )
        }
        val quiz = buildQuiz(chunks, sectionTitles)
        val safeTitle = title.trim().ifBlank {
            clean.lineSequence().firstOrNull()?.take(70) ?: "Meu resumo"
        }
        val palette = palettes[Math.floorMod(safeTitle.hashCode(), palettes.size)]
        val summary = AudioSummary(
            id = "local-${UUID.randomUUID()}",
            title = safeTitle,
            subtitle = "Síntese criada a partir de ${sourceType.uppercase()}",
            author = author.trim().ifBlank { "Conteúdo local" },
            category = guessCategory(clean),
            format = format,
            estimatedMinutes = minutes,
            coverColors = palette,
            executiveSummary = sentences.take(3).joinToString(" ").take(700),
            sections = sections,
            keyLearnings = learnings,
            keyConcepts = concepts,
            practicalExamples = listOf(
                PracticalExample(
                    "Aplicar o conteúdo na rotina",
                    "Reserve 10 minutos hoje para aplicar uma ideia e anote o resultado."
                )
            ),
            conclusion = sentences.takeLast(2).joinToString(" ").take(400),
            mindMap = MindMapData(MindMapRoot("root", safeTitle, branches)),
            flashcards = flashcards,
            quiz = quiz,
            reviewPlan = ReviewPlan(
                "Ouça o resumo e destaque uma ideia.",
                "Releia suas anotações e faça os flashcards.",
                "Aplique uma ideia por um dia inteiro.",
                "Refaça o quiz e revise o que errou."
            ),
            listensCount = 0,
            rating = 4.7,
            sourceType = sourceType,
            isUserGenerated = true
        )
        return Result.success(summary)
    }

    private fun buildQuiz(
        chunks: List<List<String>>,
        titles: List<String>
    ): List<QuizQuestion> {
        val generic = listOf(
            "Ignorar o contexto e agir por impulso",
            "Repetir o que sempre foi feito, sem revisar",
            "Esperar o momento perfeito para começar",
            "Memorizar sem aplicar nenhuma ideia"
        )
        return chunks.take(4).mapIndexed { index, part ->
            val correct = part.firstOrNull()?.take(180) ?: "Aplicar a ideia principal do trecho."
            val options = (listOf(correct) + generic.shuffled().take(3)).shuffled()
            QuizQuestion(
                id = "q-$index",
                question = "Qual afirmação está de acordo com ${titles.getOrElse(index) { "o trecho ${index + 1}" }}?",
                options = options,
                correctIndex = options.indexOf(correct),
                explanation = "A resposta correta resume o que o texto apresenta nesta parte."
            )
        }
    }

    private fun guessCategory(text: String): String {
        val lower = text.lowercase()
        return when {
            listOf("dinheiro", "investimento", "riqueza", "finanças", "poupança").any(lower::contains) -> "Finanças pessoais"
            listOf("hábito", "produtividade", "foco", "rotina", "tempo").any(lower::contains) -> "Produtividade"
            listOf("emoção", "psicologia", "mente", "comportamento", "ansiedade").any(lower::contains) -> "Psicologia"
            listOf("empresa", "negócio", "mercado", "startup", "liderança").any(lower::contains) -> "Negócios"
            listOf("sono", "saúde", "corpo", "exercício", "alimentação").any(lower::contains) -> "Saúde e bem-estar"
            listOf("filosofia", "estoico", "virtude", "sabedoria").any(lower::contains) -> "Filosofia"
            listOf("tecnologia", "inteligência artificial", "dados", "digital").any(lower::contains) -> "Tecnologia"
            listOf("história", "guerra", "império", "século").any(lower::contains) -> "História"
            else -> "Educação"
        }
    }

    fun cleanText(text: String): String = text
        .replace(Regex("[\\u0000-\\u0008\\u000B\\u000C\\u000E-\\u001F]"), " ")
        .replace(Regex("[ \\t]+"), " ")
        .replace(Regex("\\n{3,}"), "\n\n")
        .trim()

    private fun splitSentences(text: String): List<String> {
        val normalized = text.replace(Regex("\\s+"), " ")
        return normalized.split(Regex("(?<=[.!?])\\s+"))
            .map { it.trim() }
            .filter { it.length >= 35 }
            .take(120)
    }

    private fun chunk(sentences: List<String>, parts: Int): List<List<String>> {
        if (sentences.isEmpty()) return emptyList()
        val size = (sentences.size + parts - 1) / parts
        return sentences.chunked(size.coerceAtLeast(1)).take(parts)
    }
}
