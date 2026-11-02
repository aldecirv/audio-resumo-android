package com.audioresumo.app

import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.audioresumo.app.data.Store
import com.audioresumo.app.util.ImportFlow
import com.audioresumo.app.util.LocalSummarizer
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CoreTest {

    private val context = ApplicationProvider.getApplicationContext<AudioResumoApp>()

    @Test
    fun libraryLoadsTwelveCuratedSummaries() {
        Store.init(context)
        assertTrue(Store.summaries.size >= 12)
        assertTrue(Store.summaries.any { it.title.contains("Hábitos Atômicos") })
        assertTrue(Store.byId("habitos-atomicos")!!.sections.isNotEmpty())
        assertTrue(Store.byId("habitos-atomicos")!!.quiz.isNotEmpty())
    }

    @Test
    fun localSummarizerCreatesCompleteLearningMaterial() {
        val text = """
            Aprendizado acelerado: como estudar menos e aprender mais.
            A ciência mostra que reler e grifar são estratégias fracas. O cérebro fixa melhor quando recupera a informação.
            Revisar em intervalos crescentes combate o esquecimento e cria memória de longo prazo.
            Sessões curtas e frequentes superam maratonas. A intercalação força o cérebro a escolher estratégias.
            Na prática, estude em blocos de 25 minutos e faça uma pergunta ao final. Durma bem para consolidar.
            Aprender não é acumular informação, é construir caminhos de acesso a ela.
        """.trimIndent()
        val result = LocalSummarizer.summarize(text, "Aprendizado acelerado", "Teste", "essential", "txt")
        assertTrue(result.isSuccess)
        val summary = result.getOrThrow()
        assertTrue(summary.sections.size >= 2)
        assertTrue(summary.flashcards.isNotEmpty())
        assertTrue(summary.quiz.isNotEmpty())
        assertTrue(summary.mindMap.root.branches.isNotEmpty())
        assertEquals(5, summary.estimatedMinutes)
    }

    @Test
    fun readsEpubLocallyAndExtractsTitleAndText() {
        val epub = File(context.cacheDir, "test.epub")
        ZipOutputStream(epub.outputStream()).use { zip ->
            fun write(name: String, content: String) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(content.toByteArray())
                zip.closeEntry()
            }
            write("META-INF/container.xml", """<?xml version="1.0"?>
                <container xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
                  <rootfiles><rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/></rootfiles>
                </container>""")
            write("OEBPS/content.opf", """<?xml version="1.0"?>
                <package xmlns="http://www.idpf.org/2007/opf">
                  <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                    <dc:title>Livro de Teste</dc:title><dc:creator>Autor Teste</dc:creator>
                  </metadata>
                  <manifest><item id="c1" href="chapter1.xhtml" media-type="application/xhtml+xml"/></manifest>
                  <spine><itemref idref="c1"/></spine>
                </package>""")
            write("OEBPS/chapter1.xhtml", """<html><body>
                <h1>Capítulo 1</h1>
                <p>Este é um livro de teste sobre aprendizado acelerado e revisão espaçada.</p>
                <p>O conteúdo precisa ter texto suficiente para ser resumido pelo aplicativo.</p>
                </body></html>""")
        }
        val result = runBlocking { ImportFlow.read(context, Uri.fromFile(epub)) }
        assertTrue(result.isSuccess)
        val doc = result.getOrThrow()
        assertEquals("Livro de Teste", doc.title)
        assertEquals("Autor Teste", doc.author)
        assertTrue(doc.text.contains("aprendizado acelerado"))
        assertEquals("EPUB", doc.kind)
    }

    @Test
    fun readsPdfLocallyAndExtractsText() {
        val pdf = File(context.cacheDir, "test.pdf")
        PDDocument().use { doc ->
            val page = PDPage()
            doc.addPage(page)
            PDPageContentStream(doc, page).use { stream ->
                stream.beginText()
                stream.setFont(PDType1Font.HELVETICA, 12f)
                stream.newLineAtOffset(50f, 700f)
                stream.showText("Aprendizado acelerado e revisao espacada para memorizar melhor.")
                stream.endText()
            }
            doc.save(pdf)
        }
        val result = runBlocking { ImportFlow.read(context, Uri.fromFile(pdf)) }
        assertTrue(result.isSuccess)
        val doc = result.getOrThrow()
        assertEquals("PDF", doc.kind)
        assertTrue(doc.text.contains("Aprendizado acelerado"))
    }
}
