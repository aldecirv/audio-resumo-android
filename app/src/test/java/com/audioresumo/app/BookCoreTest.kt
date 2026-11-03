package com.audioresumo.app

import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.audioresumo.app.data.BookStore
import com.audioresumo.app.data.Store
import com.audioresumo.app.util.BookImporter
import com.audioresumo.app.util.BookProcessor
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BookCoreTest {

    private val context = ApplicationProvider.getApplicationContext<AudioResumoApp>()

    @Before
    fun setup() {
        Store.init(context)
        BookStore.init(context)
        BookStore.clearAll()
        Store.updateSettings { it.apiKey = "" }
    }

    @Test
    fun importsTxtAsChaptersAndPersistsText() = runBlocking {
        val file = File(context.cacheDir, "livro.txt")
        file.writeText(
            """
            Capítulo 1 — Começo
            ${"Este é o primeiro capítulo com conteúdo suficiente para ser resumido. ".repeat(8)}
            Capítulo 2 — Meio
            ${"Este é o segundo capítulo com conteúdo suficiente para ser resumido. ".repeat(8)}
            Capítulo 3 — Fim
            ${"Este é o terceiro capítulo com conteúdo suficiente para ser resumido. ".repeat(8)}
            """.trimIndent()
        )
        val result = BookImporter.importUri(context, Uri.fromFile(file))
        assertTrue(result.exceptionOrNull().toString(), result.isSuccess)
        val book = result.getOrThrow()
        assertEquals(3, book.chapterCount)
        assertEquals(1, BookStore.books.size)
        val full = BookStore.loadFull(book.id)!!
        assertTrue(full.chapters.first().text.contains("primeiro capítulo"))
        assertTrue(full.chapters.last().text.contains("terceiro capítulo"))
    }

    @Test
    fun importsEpubChapterOrder() = runBlocking {
        val epub = File(context.cacheDir, "test_book.epub")
        ZipOutputStream(epub.outputStream()).use { zip ->
            fun write(name: String, content: String) {
                zip.putNextEntry(ZipEntry(name)); zip.write(content.toByteArray()); zip.closeEntry()
            }
            write("META-INF/container.xml", """<container><rootfiles><rootfile full-path="OEBPS/content.opf"/></rootfiles></container>""")
            write("OEBPS/content.opf", """<package><metadata><title>Livro Teste</title><creator>Autor Teste</creator></metadata>
                <manifest>
                  <item id="c1" href="c1.xhtml"/><item id="c2" href="c2.xhtml"/>
                </manifest><spine><itemref idref="c1"/><itemref idref="c2"/></spine></package>""")
            write("OEBPS/c1.xhtml", "<html><body><h1>Primeiro</h1><p>${"conteúdo do primeiro capítulo ".repeat(15)}</p></body></html>")
            write("OEBPS/c2.xhtml", "<html><body><h1>Segundo</h1><p>${"conteúdo do segundo capítulo ".repeat(15)}</p></body></html>")
        }
        val result = BookImporter.importUri(context, Uri.fromFile(epub))
        assertTrue(result.exceptionOrNull().toString(), result.isSuccess)
        val book = result.getOrThrow()
        assertEquals("Livro Teste", book.title)
        assertEquals("Autor Teste", book.author)
        assertEquals(2, book.chapterCount)
        assertEquals("Primeiro", book.chapters[0].title)
        assertEquals("Segundo", book.chapters[1].title)
    }

    @Test
    fun importsTextPdf() = runBlocking {
        val pdf = File(context.cacheDir, "book.pdf")
        com.tom_roush.pdfbox.pdmodel.PDDocument().use { doc ->
            val page = com.tom_roush.pdfbox.pdmodel.PDPage()
            doc.addPage(page)
            com.tom_roush.pdfbox.pdmodel.PDPageContentStream(doc, page).use { stream ->
                stream.beginText()
                stream.setFont(com.tom_roush.pdfbox.pdmodel.font.PDType1Font.HELVETICA, 12f)
                stream.newLineAtOffset(50f, 700f)
                stream.showText("Capitulo 1 - Foco. O foco profundo exige eliminar distracoes e definir uma intencao clara.")
                stream.endText()
            }
            val page2 = com.tom_roush.pdfbox.pdmodel.PDPage()
            doc.addPage(page2)
            com.tom_roush.pdfbox.pdmodel.PDPageContentStream(doc, page2).use { stream ->
                stream.beginText()
                stream.setFont(com.tom_roush.pdfbox.pdmodel.font.PDType1Font.HELVETICA, 12f)
                stream.newLineAtOffset(50f, 700f)
                stream.showText("Segunda pagina com habitos, repeticoes e recompensas que sustentam a pratica.")
                stream.endText()
            }
            doc.save(pdf)
        }
        val result = BookImporter.importUri(context, Uri.fromFile(pdf))
        assertTrue(result.exceptionOrNull().toString(), result.isSuccess)
        val book = result.getOrThrow()
        assertEquals(1, book.chapterCount)
        assertTrue(book.chapters.first().text.contains("foco profundo", ignoreCase = true))
        assertTrue(book.chapters.first().text.contains("Segunda pagina", ignoreCase = true))
    }

    @Test
    fun importsDocxParagraphs() = runBlocking {
        val docx = File(context.cacheDir, "test_book.docx")
        ZipOutputStream(docx.outputStream()).use { zip ->
            fun write(name: String, content: String) {
                zip.putNextEntry(ZipEntry(name)); zip.write(content.toByteArray()); zip.closeEntry()
            }
            write("word/document.xml", """<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"><w:body>
                <w:p><w:r><w:t>Capítulo 1</w:t></w:r></w:p>
                <w:p><w:r><w:t>${"Texto do primeiro capítulo. ".repeat(15)}</w:t></w:r></w:p>
                <w:p><w:r><w:t>Capítulo 2</w:t></w:r></w:p>
                <w:p><w:r><w:t>${"Texto do segundo capítulo. ".repeat(15)}</w:t></w:r></w:p>
                </w:body></w:document>""")
            write("docProps/core.xml", "<cp:coreProperties xmlns:dc=\"http://purl.org/dc/elements/1.1/\"><dc:title>DOCX Teste</dc:title><dc:creator>Autor DOCX</dc:creator></cp:coreProperties>")
        }
        val result = BookImporter.importUri(context, Uri.fromFile(docx))
        assertTrue(result.exceptionOrNull().toString(), result.isSuccess)
        val book = result.getOrThrow()
        assertEquals("DOCX Teste", book.title)
        assertEquals(2, book.chapterCount)
        assertTrue(book.chapters.first().text.contains("primeiro capítulo"))
    }

    @Test
    fun processesWholeBookAndChapterLocallyAndAnswersQuestions() = runBlocking {
        val file = File(context.cacheDir, "resumo.txt")
        file.writeText(
            """
            Capítulo 1 — Foco
            ${"O foco profundo exige eliminar distrações e definir uma intenção clara. ".repeat(10)}
            Capítulo 2 — Hábitos
            ${"Hábitos são construídos por repetição, ambiente e recompensa imediata. ".repeat(10)}
            """.trimIndent()
        )
        val book = BookImporter.importUri(context, Uri.fromFile(file)).getOrThrow()
        val whole = BookProcessor.summarizeBook(context, book.id, 10).getOrThrow()
        val summary = Store.byId(whole)
        assertNotNull(summary)
        assertTrue(summary!!.isBookSummary)
        assertEquals(book.id, summary.parentBookId)
        assertEquals(10, summary.estimatedMinutes)
        assertTrue(summary.executiveSummary.isNotBlank())

        val chapterId = BookProcessor.summarizeChapter(context, book.id, 0).getOrThrow()
        val chapterSummary = Store.byId(chapterId)
        assertNotNull(chapterSummary)
        assertEquals(0, chapterSummary!!.chapterIndex)
        assertEquals(book.id, chapterSummary.parentBookId)

        val answer = BookProcessor.ask(context, book.id, "O que o foco profundo exige?").getOrThrow()
        assertTrue(answer.answer.isNotBlank())
        assertTrue(answer.answer.contains("distra", ignoreCase = true))
    }

    @Test
    fun searchRenameAndDeleteBook() = runBlocking {
        val file = File(context.cacheDir, "busca.txt")
        file.writeText("Capítulo 1\n${"conteúdo exclusivo sobre produtividade e foco ".repeat(20)}")
        val book = BookImporter.importUri(context, Uri.fromFile(file)).getOrThrow()
        assertTrue(BookStore.search("produtividade").any { it.book.id == book.id })
        BookStore.rename(book.id, "Novo título", "Novo autor")
        assertEquals("Novo título", BookStore.byId(book.id)!!.title)
        BookStore.delete(book.id)
        assertEquals(null, BookStore.byId(book.id))
    }
}
