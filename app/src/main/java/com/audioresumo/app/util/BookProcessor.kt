package com.audioresumo.app.util

import android.content.Context
import com.audioresumo.app.data.AudioSummary
import com.audioresumo.app.data.Book
import com.audioresumo.app.data.BookStore
import com.audioresumo.app.data.BookStatus
import com.audioresumo.app.data.Store
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import java.util.UUID
import kotlin.coroutines.coroutineContext

object BookProcessor {

    data class Progress(val percent: Int, val message: String)

    data class QaAnswer(
        val answer: String,
        val sources: List<String>,
        val usedAi: Boolean
    )

    private data class Block(val key: String, val chapterIndex: Int, val chapterTitle: String, val text: String)

    private const val BLOCK_CHARS = 14_000
    private const val FINAL_CONTEXT_CHARS = 48_000

    suspend fun summarizeBook(
        context: Context,
        bookId: String,
        minutes: Int,
        onProgress: (Progress) -> Unit = {},
        isActive: () -> Boolean = { true }
    ): Result<String> = withContext(Dispatchers.IO) {
        val book = BookStore.loadFull(bookId)
            ?: return@withContext Result.failure(IllegalStateException("Livro não encontrado."))
        val apiKey = Store.settings.apiKey.isNotBlank()
        book.summaryMinutes = minutes
        book.summaryStatus = "processing"
        book.summaryProgress = 1
        book.progress = 1
        book.status = BookStatus.PROCESSING
        book.message = if (apiKey) "Preparando blocos do livro..." else "Gerando resumo local..."
        BookStore.updateMetadata(book)
        onProgress(Progress(1, book.message))
        try {
            if (!isActive()) throw CancellationException("Cancelado")
            val summary = if (apiKey) {
                summarizeWithAi(context, book, minutes, onProgress, isActive)
            } else {
                summarizeLocal(book, minutes, onProgress)
            }
            if (!isActive()) throw CancellationException("Cancelado")
            val saved = summary.copy(
                id = summary.id.ifBlank { "book-${UUID.randomUUID()}" },
                parentBookId = book.id,
                chapterIndex = null,
                isBookSummary = true,
                summaryMinutes = minutes,
                sourceType = summary.sourceType
            )
            book.summaryId?.takeIf { it != saved.id }?.let { Store.removeSummary(it) }
            Store.addSummary(saved)
            book.summaryId = saved.id
            book.summaryStatus = "ready"
            book.summaryProgress = 100
            book.progress = 100
            book.status = BookStatus.READY
            book.message = if (saved.sourceType == "local") {
                "Resumo local extrativo • ${book.chapterCount} capítulos representados"
            } else {
                "Resumo do livro concluído • ${book.chapterCount} capítulos"
            }
            BookStore.updateMetadata(book)
            onProgress(Progress(100, book.message))
            Result.success(saved.id)
        } catch (cancel: CancellationException) {
            book.summaryStatus = "paused"
            book.status = BookStatus.READY
            book.message = "Processamento pausado. Toque em continuar."
            BookStore.updateMetadata(book)
            throw cancel
        } catch (error: Throwable) {
            book.summaryStatus = "error"
            book.status = BookStatus.ERROR
            book.message = error.message ?: "Falha ao gerar o resumo"
            BookStore.updateMetadata(book)
            Result.failure(error)
        }
    }

    private suspend fun summarizeWithAi(
        context: Context,
        book: Book,
        minutes: Int,
        onProgress: (Progress) -> Unit,
        isActive: () -> Boolean
    ): AudioSummary {
        val blocks = blocks(book)
        if (blocks.isEmpty()) error("O livro não tem texto processável.")
        val digests = mutableListOf<Pair<String, String>>()
        blocks.forEachIndexed { index, block ->
            if (!isActive()) throw CancellationException("Cancelado")
            val key = block.key
            var digest = book.blockDigests[key]
            if (digest.isNullOrBlank()) {
                onProgress(Progress(5 + index * 65 / blocks.size, "Lendo bloco ${index + 1} de ${blocks.size}..."))
                val result = GeminiClient.complete(
                    context,
                    "Você resume trechos de livros em português do Brasil. Preserve fatos, nomes, teses e a ordem das ideias. Não invente conteúdo.",
                    """
                        Livro: ${book.title}
                        Capítulo: ${block.chapterTitle}
                        Trecho ${index + 1} de ${blocks.size}.

                        Resuma este trecho em até 1.200 caracteres, mantendo os pontos essenciais para um resumo posterior do livro inteiro.

                        ${block.text}
                    """.trimIndent()
                )
                digest = result.getOrElse {
                    onProgress(Progress(5 + index * 65 / blocks.size, "IA indisponível; gerando resumo local..."))
                    return summarizeLocal(book, minutes, onProgress, "IA indisponível: ${it.message}")
                }
                book.blockDigests[key] = digest
                book.summaryProgress = 5 + (index + 1) * 65 / blocks.size
                book.progress = book.summaryProgress
                book.message = "Trecho ${index + 1} de ${blocks.size} resumido"
                BookStore.updateMetadata(book)
            }
            digests.add(block.chapterTitle to digest)
            onProgress(Progress(5 + (index + 1) * 65 / blocks.size, "Trecho ${index + 1} de ${blocks.size} concluído"))
        }

        var combined = digests.joinToString("\n\n") { (title, digest) -> "[$title]\n$digest" }
        var round = 0
        while (combined.length > FINAL_CONTEXT_CHARS) {
            if (!isActive()) throw CancellationException("Cancelado")
            round++
            onProgress(Progress(75 + round.coerceAtMost(10), "Consolidando resumo (etapa $round)..."))
            val groups = combined.chunked(30_000)
            val merged = mutableListOf<String>()
            groups.forEachIndexed { index, part ->
                val result = GeminiClient.complete(
                    context,
                    "Você consolida resumos parciais de um livro sem perder teses, nomes e dados. Responda em português.",
                    "Una os resumos parciais abaixo em um único texto coeso de até 1.500 caracteres. Preserve a ordem dos capítulos.\n\n$part"
                )
                merged.add(result.getOrElse { it.message?.let { message -> "Falha: $message" } ?: part.take(1500) })
                onProgress(Progress(75 + index * 5 / groups.size, "Consolidando parte ${index + 1}..."))
            }
            val next = merged.joinToString("\n\n")
            if (next.length >= combined.length) {
                // Evita laço infinito se o modelo ignorar o limite de consolidação.
                combined = next.take(FINAL_CONTEXT_CHARS)
                break
            }
            combined = next
        }

        onProgress(Progress(92, "Gerando resumo final do livro..."))
        val generated = GeminiClient.generate(
            text = combined,
            format = if (minutes >= 20) "deep" else if (minutes >= 10) "complete" else "essential",
            sourceType = "book",
            titleHint = book.title,
            minutesOverride = minutes,
            authorHint = book.author
        ).getOrElse { error ->
            onProgress(Progress(92, "IA falhou; gerando resumo local..."))
            return summarizeLocal(book, minutes, onProgress, "IA indisponível: ${error.message}")
        }
        onProgress(Progress(98, "Resumo final pronto"))
        return generated.copy(
            title = book.title,
            subtitle = "Resumo do livro · ${book.chapterCount} capítulos · ${minutes} min",
            author = book.author.ifBlank { generated.author },
            parentBookId = book.id,
            isBookSummary = true,
            summaryMinutes = minutes
        )
    }

    private suspend fun summarizeLocal(
        book: Book,
        minutes: Int,
        onProgress: (Progress) -> Unit,
        note: String? = null
    ): AudioSummary {
        val sample = buildString {
            book.chapters.forEach { chapter ->
                if (chapter.text.isNotBlank()) {
                    append("[${chapter.title}]\n")
                    append(chapter.text.take(1600))
                    if (chapter.text.length > 2000) {
                        append("\n...\n")
                        append(chapter.text.takeLast(500))
                    }
                    append("\n\n")
                }
            }
        }
        onProgress(Progress(40, "Gerando resumo local a partir de todos os capítulos..."))
        val local = LocalSummarizer.summarize(
            text = sample,
            title = book.title,
            author = book.author,
            format = if (minutes >= 20) "deep" else if (minutes >= 10) "complete" else "essential",
            sourceType = "local",
            minutesOverride = minutes
        ).getOrElse { error(it.message ?: "Falha no resumo local") }
        onProgress(Progress(85, "Resumo local pronto"))
        return local.copy(
            title = book.title,
            subtitle = buildString {
                append("Resumo local extrativo · ${book.chapterCount} capítulos")
                if (note != null) append(" · ").append(note.take(140))
            },
            author = book.author.ifBlank { "Conteúdo local" },
            parentBookId = book.id,
            chapterIndex = null,
            isBookSummary = true,
            summaryMinutes = minutes,
            sourceType = "local"
        )
    }

    suspend fun summarizeChapter(
        context: Context,
        bookId: String,
        chapterIndex: Int,
        onProgress: (Progress) -> Unit = {}
    ): Result<String> = withContext(Dispatchers.IO) {
        val book = BookStore.loadFull(bookId) ?: return@withContext Result.failure(IllegalStateException("Livro não encontrado."))
        val chapter = book.chapters.getOrNull(chapterIndex)
            ?: return@withContext Result.failure(IllegalArgumentException("Capítulo não encontrado."))
        if (chapter.text.length < 80) return@withContext Result.failure(IllegalArgumentException("Capítulo sem texto suficiente."))
        chapter.summaryStatus = "processing"
        BookStore.updateMetadata(book)
        onProgress(Progress(5, "Preparando capítulo..."))
        var generated: AudioSummary? = null
        val aiAttempted = Store.settings.apiKey.isNotBlank()
        if (aiAttempted) {
            var text = chapter.text
            var failed = false
            if (text.length > 55_000) {
                val parts = splitBlocks(text)
                val digests = mutableListOf<String>()
                parts.forEachIndexed { index, part ->
                    onProgress(Progress(10 + index * 50 / parts.size, "Resumindo parte ${index + 1}/${parts.size}..."))
                    val r = GeminiClient.complete(
                        context,
                        "Resuma o trecho de um capítulo em português, preservando fatos e ideias.",
                        "Trecho ${index + 1}/${parts.size} do capítulo \"${chapter.title}\":\n\n$part"
                    )
                    if (r.isFailure) {
                        failed = true
                        return@forEachIndexed
                    }
                    digests.add(r.getOrThrow())
                }
                if (!failed) text = digests.joinToString("\n\n").take(55_000)
            }
            if (!failed) {
                generated = GeminiClient.generate(
                    text = text,
                    format = "essential",
                    sourceType = "chapter",
                    titleHint = "${book.title} — ${chapter.title}",
                    minutesOverride = 5,
                    authorHint = book.author
                ).getOrNull()?.copy(
                    title = "${book.title} · ${chapter.title}",
                    subtitle = "Resumo do capítulo ${chapter.index + 1} · ${book.title}",
                    parentBookId = book.id,
                    chapterIndex = chapterIndex,
                    isBookSummary = false,
                    summaryMinutes = 5,
                    author = book.author
                )
            }
        }
        if (generated == null) {
            val local = localChapter(book, chapter, chapterIndex, onProgress, if (aiAttempted) "IA indisponível" else null)
            if (local.isFailure) {
                chapter.summaryStatus = "error"
                BookStore.updateMetadata(book)
                return@withContext Result.failure(local.exceptionOrNull() ?: IllegalStateException("Falha ao resumir capítulo."))
            }
            generated = local.getOrThrow()
        }
        chapter.summaryId?.takeIf { it != generated.id }?.let { Store.removeSummary(it) }
        Store.addSummary(generated)
        chapter.summaryId = generated.id
        chapter.summaryStatus = "ready"
        BookStore.updateMetadata(book)
        onProgress(Progress(100, "Resumo do capítulo salvo"))
        Result.success(generated.id)
    }

    private fun localChapter(
        book: Book,
        chapter: com.audioresumo.app.data.BookChapter,
        chapterIndex: Int,
        onProgress: (Progress) -> Unit,
        note: String?
    ): Result<AudioSummary> {
        val local = LocalSummarizer.summarize(
            text = chapter.text,
            title = "${book.title} · ${chapter.title}",
            author = book.author,
            format = "essential",
            sourceType = "chapter",
            minutesOverride = 5
        )
        if (local.isFailure) return local
        onProgress(Progress(90, "Resumo local do capítulo pronto"))
        return Result.success(
            local.getOrThrow().copy(
                title = "${book.title} · ${chapter.title}",
                subtitle = buildString {
                    append("Resumo do capítulo ${chapter.index + 1}")
                    if (note != null) append(" · local (IA indisponível)")
                },
                author = book.author,
                parentBookId = book.id,
                chapterIndex = chapterIndex,
                isBookSummary = false,
                summaryMinutes = 5,
                sourceType = "local"
            )
        )
    }

    suspend fun ask(context: Context, bookId: String, question: String): Result<QaAnswer> = withContext(Dispatchers.IO) {
        val book = BookStore.loadFull(bookId) ?: return@withContext Result.failure(IllegalStateException("Livro não encontrado."))
        val q = question.trim()
        if (q.length < 3) return@withContext Result.failure(IllegalArgumentException("Escreva uma pergunta."))
        val snippets = snippets(book)
        if (snippets.isEmpty()) return@withContext Result.failure(IllegalStateException("O livro não tem texto pesquisável."))
        val terms = keywords(q)
        val ranked = snippets.map { snippet ->
            val lower = normalizeForSearch(snippet.text)
            snippet to terms.sumOf { term -> Regex("\\b" + Regex.escape(term) + "\\b").findAll(lower).count() }
        }.filter { it.second > 0 }.sortedByDescending { it.second }.take(8)
        val selected = if (ranked.isEmpty()) snippets.take(5) else ranked.map { it.first }
        val sources = selected.map { it.chapterTitle }.distinct()
        if (Store.settings.apiKey.isNotBlank()) {
            val contextText = selected.joinToString("\n\n") { "[${it.chapterTitle}]\n${it.text}" }.take(40_000)
            val result = GeminiClient.complete(
                context,
                "Você responde perguntas usando SOMENTE o contexto fornecido de um livro. Se a resposta não estiver no contexto, diga que não encontrou no livro. Responda em português, de forma direta, e cite os capítulos entre colchetes.",
                "Pergunta: $q\n\nContexto do livro \"${book.title}\":\n$contextText"
            )
            return@withContext result.map { QaAnswer(it.trim(), sources, true) }
        }
        val localSentences = selected.flatMap { snippet ->
            snippet.text.split(Regex("(?<=[.!?])\\s+")).filter { sentence ->
                val normalized = normalizeForSearch(sentence)
                terms.any { normalized.contains(it) }
            }.map { snippet.chapterTitle to it.trim() }
        }.distinctBy { it.second }.take(6)
        if (localSentences.isEmpty()) {
            return@withContext Result.success(
                QaAnswer(
                    "Não encontrei essa informação no texto deste livro. Tente reformular a pergunta ou configurar a chave Gemini para uma resposta interpretativa.",
                    emptyList(),
                    false
                )
            )
        }
        val answer = localSentences.joinToString("\n\n") { (title, sentence) -> "[$title] $sentence" }
        Result.success(
            QaAnswer(
                answer,
                localSentences.map { it.first }.distinct(),
                false
            )
        )
    }

    private data class Snippet(val chapterTitle: String, val text: String)

    private fun snippets(book: Book): List<Snippet> {
        val result = mutableListOf<Snippet>()
        book.chapters.forEach { chapter ->
            val text = chapter.text.ifBlank { "" }
            if (text.isBlank()) return@forEach
            text.chunked(1800).forEach { part ->
                val clean = part.trim()
                if (clean.length > 80) result.add(Snippet(chapter.title, clean))
            }
        }
        return result
    }

    private fun blocks(book: Book): List<Block> {
        val result = mutableListOf<Block>()
        book.chapters.forEach { chapter ->
            if (chapter.text.isBlank()) return@forEach
            val parts = splitBlocks(chapter.text)
            parts.forEachIndexed { index, part ->
                result.add(Block("ch${chapter.index}-b$index", chapter.index, chapter.title, part))
            }
        }
        return result
    }

    private fun splitBlocks(text: String): List<String> {
        val result = mutableListOf<String>()
        var start = 0
        while (start < text.length) {
            var end = (start + BLOCK_CHARS).coerceAtMost(text.length)
            if (end < text.length) {
                val breakAt = text.lastIndexOf(' ', end).let { if (it > start + 5000) it else end }
                end = breakAt
            }
            result.add(text.substring(start, end).trim())
            start = end
        }
        return result.filter { it.length > 40 }
    }

    private fun normalizeForSearch(text: String): String =
        java.text.Normalizer.normalize(text.lowercase(), java.text.Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")

    private fun keywords(text: String): List<String> {
        val stop = setOf("a", "o", "e", "de", "da", "do", "que", "um", "uma", "para", "com", "por", "no", "na", "em", "os", "as", "se", "como", "qual", "quais", "onde", "quando", "porque", "livro", "autor")
        return java.text.Normalizer.normalize(text.lowercase(), java.text.Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
            .split(Regex("[^a-z0-9]+"))
            .filter { it.length > 2 && it !in stop }
            .distinct()
    }
}
