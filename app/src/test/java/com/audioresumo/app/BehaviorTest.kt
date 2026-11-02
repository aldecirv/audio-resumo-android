package com.audioresumo.app

import androidx.test.core.app.ApplicationProvider
import com.audioresumo.app.data.Store
import com.audioresumo.app.service.SpeechContent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BehaviorTest {

    private val context = ApplicationProvider.getApplicationContext<AudioResumoApp>()

    @Test
    fun favoritesProgressAndQuizPersistInStore() {
        Store.init(context)
        val summary = Store.summaries.first()
        val before = summary.isFavorite
        Store.toggleFavorite(summary.id)
        assertEquals(!before, Store.byId(summary.id)!!.isFavorite)
        Store.toggleFavorite(summary.id)

        Store.updateProgress(summary.id, 42, 126)
        assertEquals(42, Store.byId(summary.id)!!.progressPercent)
        assertEquals(summary.id, Store.user.history.first().summaryId)

        val quizzesBefore = Store.user.completedQuizzes
        Store.saveQuizResult(summary.id, 2, 3)
        assertEquals(2, Store.byId(summary.id)!!.quizCorrect)
        assertEquals(quizzesBefore + 1, Store.user.completedQuizzes)
    }

    @Test
    fun speechContentCoversSummaryAndSections() {
        Store.init(context)
        val summary = Store.byId("habitos-atomicos")!!
        val paragraphs = SpeechContent.build(summary)
        assertTrue(paragraphs.size >= summary.sections.size + 2)
        assertEquals("Visão geral", paragraphs.first().sectionTitle)
        assertTrue(paragraphs.first().startSeconds == 0)
        assertTrue(paragraphs.zipWithNext().all { (a, b) -> a.startSeconds <= b.startSeconds })
        assertTrue(paragraphs.any { it.text.contains("efeito composto", ignoreCase = true) })
    }

    @Test
    fun generatedSummaryCanBeAddedToLibrary() {
        Store.init(context)
        val before = Store.summaries.size
        val text = """
            Aprendizado acelerado e revisão espaçada.
            Reler é fraco; recuperar a informação fortalece a memória. Teste-se antes de revisar.
            Intervalos crescentes combatem o esquecimento. Estude em blocos curtos e durma bem.
            Aplique uma ideia por dia e registre o resultado para consolidar o aprendizado.
        """.trimIndent()
        val summary = com.audioresumo.app.util.LocalSummarizer
            .summarize(text, "Aprendizado", "Teste", "quick", "txt").getOrThrow()
        Store.addSummary(summary)
        assertEquals(before + 1, Store.summaries.size)
        assertTrue(Store.byId(summary.id) != null)
        assertFalse(Store.byId(summary.id)!!.quiz.isEmpty())
    }
}
