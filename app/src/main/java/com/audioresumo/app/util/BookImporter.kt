package com.audioresumo.app.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import android.text.Html
import android.util.Xml
import com.audioresumo.app.data.Book
import com.audioresumo.app.data.BookChapter
import com.audioresumo.app.data.BookFormat
import com.audioresumo.app.data.BookStore
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.tasks.await
import org.xmlpull.v1.XmlPullParser
import java.io.File
import java.util.UUID
import java.util.zip.ZipFile

object BookImporter {

    data class Progress(val percent: Int, val message: String)

    suspend fun importUri(
        context: Context,
        uri: Uri,
        onProgress: (Progress) -> Unit = {}
    ): Result<Book> = withContext(Dispatchers.IO) {
        runCatching {
            val name = displayName(context, uri) ?: "Livro"
            val mime = context.contentResolver.getType(uri) ?: guessMime(name)
            val lower = name.lowercase()
            val size = fileSize(context, uri)
            onProgress(Progress(2, "Abrindo arquivo..."))
            when {
                lower.endsWith(".epub") || mime.contains("epub") -> importEpub(context, uri, name, size, onProgress)
                lower.endsWith(".pdf") || mime.contains("pdf") -> importPdf(context, uri, name, size, onProgress)
                lower.endsWith(".docx") || mime.contains("wordprocessingml") -> importDocx(context, uri, name, size, onProgress)
                lower.endsWith(".txt") || mime.startsWith("text/") -> importTxt(context, uri, name, size, onProgress)
                else -> error("Formato não suportado. Use EPUB, PDF, DOCX ou TXT.")
            }.also { book ->
                onProgress(Progress(98, "Salvando livro..."))
                BookStore.add(book)
                onProgress(Progress(100, "Livro salvo"))
            }
        }
    }

    fun fromText(title: String, author: String, text: String): Result<Book> = runCatching {
        val clean = LocalSummarizer.cleanText(text)
        if (clean.length < 80) error("O texto é muito curto para criar um livro.")
        val chapters = splitIntoChapters(clean, title.ifBlank { "Livro de texto" })
        createBook(
            title = title.ifBlank { "Livro de texto" },
            author = author,
            format = BookFormat.TEXT,
            fileName = "texto-colado.txt",
            sizeBytes = clean.toByteArray().size.toLong(),
            chapters = chapters
        ).also { BookStore.add(it) }
    }

    // ------------------------------------------------------------------ EPUB

    private fun importEpub(
        context: Context,
        uri: Uri,
        fileName: String,
        size: Long,
        onProgress: (Progress) -> Unit
    ): Book {
        val temp = File.createTempFile("book_", ".epub", context.cacheDir)
        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                temp.outputStream().use { output -> input.copyTo(output) }
            } ?: error("Não foi possível abrir o EPUB.")
            ZipFile(temp).use { zip ->
                val container = zip.getEntry("META-INF/container.xml")
                    ?: error("EPUB inválido: container.xml ausente.")
                val opfPath = parseContainer(zip.getInputStream(container).bufferedReader().readText())
                    ?: error("EPUB inválido: OPF não encontrado.")
                val opfEntry = zip.getEntry(opfPath) ?: error("EPUB inválido: conteúdo não encontrado.")
                val opf = parseOpf(zip.getInputStream(opfEntry).bufferedReader().readText())
                val base = opfPath.substringBeforeLast('/', "")
                val chapters = mutableListOf<BookChapter>()
                val spine = opf.spine.ifEmpty { opf.allHrefs }
                spine.forEachIndexed { index, href ->
                    val path = (if (base.isBlank()) href else "$base/$href")
                        .substringBefore('#')
                        .substringBefore('?')
                    val entry = zip.getEntry(path) ?: zip.getEntry(path.replace("%20", " "))
                    if (entry != null && !entry.name.startsWith("__MACOSX") &&
                        (entry.name.endsWith(".xhtml", true) || entry.name.endsWith(".html", true) || entry.name.endsWith(".htm", true))
                    ) {
                        val raw = zip.getInputStream(entry).bufferedReader().readText()
                        val plain = LocalSummarizer.cleanText(
                            Html.fromHtml(raw, Html.FROM_HTML_MODE_LEGACY).toString()
                        )
                        if (plain.length > 20) {
                            val heading = extractHeading(raw)
                                ?: plain.lineSequence().firstOrNull { it.trim().length in 3..100 }?.trim()
                            var body = plain
                            if (heading != null && body.startsWith(heading)) {
                                body = body.removePrefix(heading).trim()
                            }
                            chapters.add(
                                chapter(
                                    index = chapters.size,
                                    title = heading?.replace(Regex("\\s+"), " ")?.take(120)
                                        ?: "Capítulo ${chapters.size + 1}",
                                    text = body
                                )
                            )
                        }
                    }
                    val percent = 5 + ((index + 1) * 85 / spine.size.coerceAtLeast(1))
                    onProgress(Progress(percent, "Capítulo ${index + 1} de ${spine.size}..."))
                }
                if (chapters.isEmpty()) error("Não encontrei capítulos com texto neste EPUB.")
                return createBook(
                    title = opf.title.ifBlank { fileName.substringBeforeLast('.') },
                    author = opf.author,
                    format = BookFormat.EPUB,
                    fileName = fileName,
                    sizeBytes = size,
                    chapters = chapters
                )
            }
        } finally {
            temp.delete()
        }
    }

    // ------------------------------------------------------------------- PDF

    private fun importPdf(
        context: Context,
        uri: Uri,
        fileName: String,
        size: Long,
        onProgress: (Progress) -> Unit
    ): Book {
        if (size > 250L * 1024 * 1024) error("PDF muito grande (limite técnico de 250 MB).")
        val temp = File.createTempFile("book_", ".pdf", context.cacheDir)
        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                temp.outputStream().use { output -> input.copyTo(output) }
            } ?: error("Não foi possível abrir o PDF.")

            val pages = mutableListOf<String>()
            var title = fileName.substringBeforeLast('.')
            var author = ""
            var usedOcr = false
            PDDocument.load(temp).use { doc ->
                title = doc.documentInformation?.title?.takeIf { it.isNotBlank() } ?: title
                author = doc.documentInformation?.author.orEmpty()
                val total = doc.numberOfPages
                val stripper = PDFTextStripper()
                for (page in 1..total) {
                    var text = runCatching {
                        stripper.startPage = page
                        stripper.endPage = page
                        stripper.getText(doc)
                    }.getOrDefault("")
                    if (text.trim().length < 40) {
                        val ocr = ocrPage(temp, page - 1)
                        if (ocr.isNotBlank()) {
                            usedOcr = true
                            text = "$text\n$ocr"
                        }
                    }
                    pages.add(LocalSummarizer.cleanText(text))
                    val percent = 5 + (page * 78 / total.coerceAtLeast(1))
                    onProgress(Progress(percent, "Página $page de $total${if (text.length < 40) " (OCR)" else ""}..."))
                }
            }
            if (pages.all { it.length < 40 }) error("PDF sem texto legível, mesmo com OCR.")
            val chapters = chaptersFromPages(pages)
            if (chapters.isEmpty()) error("Não encontrei conteúdo neste PDF.")
            return createBook(
                title = title,
                author = author,
                format = if (usedOcr) BookFormat.PDF_OCR else BookFormat.PDF,
                fileName = fileName,
                sizeBytes = size,
                chapters = chapters
            )
        } finally {
            temp.delete()
        }
    }

    private fun ocrPage(pdf: File, pageIndex: Int): String {
        return runCatching {
            ParcelFileDescriptor.open(pdf, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
                PdfRenderer(descriptor).use { renderer ->
                    if (pageIndex !in 0 until renderer.pageCount) return ""
                    renderer.openPage(pageIndex).use { page ->
                        val targetWidth = 1600
                        val scale = targetWidth.toFloat() / page.width
                        val bitmap = Bitmap.createBitmap(
                            targetWidth,
                            (page.height * scale).toInt().coerceAtLeast(1),
                            Bitmap.Config.ARGB_8888
                        )
                        try {
                            Canvas(bitmap).drawColor(Color.WHITE)
                            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
                            try {
                                val result = kotlinx.coroutines.runBlocking {
                                    recognizer.process(InputImage.fromBitmap(bitmap, 0)).await()
                                }
                                result.text
                            } finally {
                                recognizer.close()
                            }
                        } finally {
                            bitmap.recycle()
                        }
                    }
                }
            }
        }.getOrDefault("")
    }

    private fun chaptersFromPages(pages: List<String>): List<BookChapter> {
        val headingRegex = Regex("""^\s*(cap[ií]tulo|chapter|parte|se[cç][aã]o|livro)\s+[\wivxlc\d]+""", RegexOption.IGNORE_CASE)
        val chapters = mutableListOf<BookChapter>()
        var currentTitle: String? = null
        var currentText = StringBuilder()
        var pageStart = 1
        var currentPage = 0

        fun flush(endPage: Int) {
            val body = LocalSummarizer.cleanText(currentText.toString())
            if (body.length > 30) {
                chapters.add(
                    chapter(
                        index = chapters.size,
                        title = currentTitle?.take(120) ?: if (pageStart == endPage) "Página $endPage" else "Páginas $pageStart–$endPage",
                        text = body
                    )
                )
            }
            currentText = StringBuilder()
            currentTitle = null
        }

        pages.forEachIndexed { index, raw ->
            currentPage = index + 1
            val firstLine = raw.lineSequence().firstOrNull { it.isNotBlank() }?.trim().orEmpty()
            val isHeading = headingRegex.containsMatchIn(firstLine) && firstLine.length < 120
            if (isHeading && currentText.length > 300) {
                flush(currentPage - 1)
                pageStart = currentPage
            }
            if (isHeading && currentTitle == null) {
                currentTitle = firstLine.take(120)
            }
            currentText.append(raw).append("\n")
            if (currentTitle == null && (index + 1) % 8 == 0) {
                flush(currentPage)
                pageStart = currentPage + 1
            }
        }
        flush(currentPage)
        return chapters
    }

    // ------------------------------------------------------------------- DOCX

    private fun importDocx(
        context: Context,
        uri: Uri,
        fileName: String,
        size: Long,
        onProgress: (Progress) -> Unit
    ): Book {
        val temp = File.createTempFile("book_", ".docx", context.cacheDir)
        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                temp.outputStream().use { output -> input.copyTo(output) }
            } ?: error("Não foi possível abrir o DOCX.")
            ZipFile(temp).use { zip ->
                val entry = zip.getEntry("word/document.xml") ?: error("DOCX inválido: document.xml ausente.")
                val xml = zip.getInputStream(entry).bufferedReader().readText()
                val paragraphs = parseDocxParagraphs(xml)
                val text = LocalSummarizer.cleanText(paragraphs.joinToString("\n\n"))
                if (text.length < 80) error("Não encontrei texto legível neste DOCX.")
                val meta = parseCore(zip)
                onProgress(Progress(70, "Dividindo capítulos..."))
                val chapters = splitIntoChapters(text, meta.first.ifBlank { fileName.substringBeforeLast('.') })
                return createBook(
                    title = meta.first.ifBlank { fileName.substringBeforeLast('.') },
                    author = meta.second,
                    format = BookFormat.DOCX,
                    fileName = fileName,
                    sizeBytes = size,
                    chapters = chapters
                )
            }
        } finally {
            temp.delete()
        }
    }

    private fun parseDocxParagraphs(xml: String): List<String> {
        val parser = Xml.newPullParser()
        parser.setInput(xml.reader())
        val paragraphs = mutableListOf<String>()
        val current = StringBuilder()
        var inParagraph = false
        var inText = false
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> when (parser.name) {
                    "p" -> { inParagraph = true; current.clear() }
                    "t" -> inText = true
                }
                XmlPullParser.TEXT -> if (inText) current.append(parser.text)
                XmlPullParser.END_TAG -> when (parser.name) {
                    "t" -> inText = false
                    "p" -> {
                        if (current.isNotBlank()) paragraphs.add(current.toString().trim())
                        inParagraph = false
                    }
                }
            }
            event = parser.next()
        }
        return paragraphs
    }

    private fun parseCore(zip: ZipFile): Pair<String, String> {
        val entry = zip.getEntry("docProps/core.xml") ?: return "" to ""
        val xml = runCatching { zip.getInputStream(entry).bufferedReader().readText() }.getOrDefault("")
        val title = Regex("""<dc:title[^>]*>(.*?)</dc:title>""", RegexOption.DOT_MATCHES_ALL)
            .find(xml)?.groupValues?.get(1)?.let { Html.fromHtml(it).toString() }.orEmpty()
        val author = Regex("""<dc:creator[^>]*>(.*?)</dc:creator>""", RegexOption.DOT_MATCHES_ALL)
            .find(xml)?.groupValues?.get(1)?.let { Html.fromHtml(it).toString() }.orEmpty()
        return title to author
    }

    // ------------------------------------------------------------------- TXT

    private fun importTxt(
        context: Context,
        uri: Uri,
        fileName: String,
        size: Long,
        onProgress: (Progress) -> Unit
    ): Book {
        val text = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }.orEmpty()
        if (text.isBlank()) error("O arquivo de texto está vazio.")
        onProgress(Progress(60, "Identificando capítulos..."))
        val chapters = splitIntoChapters(LocalSummarizer.cleanText(text), fileName.substringBeforeLast('.'))
        return createBook(
            title = fileName.substringBeforeLast('.'),
            author = "",
            format = BookFormat.TXT,
            fileName = fileName,
            sizeBytes = size,
            chapters = chapters
        )
    }

    // ------------------------------------------------------------- capítulos

    fun splitIntoChapters(text: String, fallbackTitle: String): List<BookChapter> {
        val heading = Regex(
            """^\s*(cap[ií]tulo|chapter|parte|se[cç][aã]o|livro)\s+[\wivxlc\d]+.*$""",
            RegexOption.IGNORE_CASE
        )
        val lines = text.replace("\r\n", "\n").split('\n')
        val chapters = mutableListOf<BookChapter>()
        var title: String? = null
        val body = StringBuilder()

        fun flush() {
            val content = LocalSummarizer.cleanText(body.toString())
            if (content.length > 80) {
                chapters.add(chapter(chapters.size, title ?: "$fallbackTitle · Parte ${chapters.size + 1}", content))
            }
            body.clear()
            title = null
        }

        lines.forEach { line ->
            val trimmed = line.trim()
            val isHeading = heading.matches(trimmed) && trimmed.length < 130
            if (isHeading) {
                if (body.length > 300) flush()
                title = trimmed
            } else {
                body.append(line).append('\n')
            }
        }
        flush()

        if (chapters.size <= 1) {
            val words = LocalSummarizer.cleanText(text).split(Regex("\\s+"))
            if (words.size < 60) error("Texto muito curto para um livro.")
            chapters.clear()
            val blockSize = 3500
            var start = 0
            var part = 1
            while (start < words.size) {
                val end = (start + blockSize).coerceAtMost(words.size)
                chapters.add(
                    chapter(
                        chapters.size,
                        "$fallbackTitle · Parte $part",
                        words.subList(start, end).joinToString(" ")
                    )
                )
                start = end
                part++
            }
        }
        return chapters
    }

    private fun chapter(index: Int, title: String, text: String): BookChapter {
        val clean = LocalSummarizer.cleanText(text)
        return BookChapter(
            id = UUID.randomUUID().toString(),
            index = index,
            title = title.trim().ifBlank { "Capítulo ${index + 1}" },
            charCount = clean.length,
            wordCount = clean.split(Regex("\\s+")).count { it.isNotBlank() },
            text = clean
        )
    }

    private fun createBook(
        title: String,
        author: String,
        format: BookFormat,
        fileName: String,
        sizeBytes: Long,
        chapters: List<BookChapter>
    ): Book {
        val normalized = chapters.mapIndexed { index, chapter ->
            chapter.copy(index = index, id = chapter.id.ifBlank { UUID.randomUUID().toString() })
        }.toMutableList()
        val book = Book(
            id = UUID.randomUUID().toString(),
            title = Book.fromJsonTitle(title),
            author = author.trim(),
            format = format,
            fileName = fileName,
            sizeBytes = sizeBytes,
            chapters = normalized,
            coverColors = Book.colorsFor(title)
        )
        book.updateCounts()
        return book
    }

    // ------------------------------------------------------------------ EPUB utils

    private fun parseContainer(xml: String): String? {
        val parser = Xml.newPullParser()
        parser.setInput(xml.reader())
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG && parser.name == "rootfile") {
                return parser.getAttributeValue(null, "full-path")
            }
            event = parser.next()
        }
        return null
    }

    private data class Opf(val title: String, val author: String, val spine: List<String>, val allHrefs: List<String>)

    private fun parseOpf(xml: String): Opf {
        val parser = Xml.newPullParser()
        parser.setInput(xml.reader())
        var title = ""
        var author = ""
        val manifest = mutableMapOf<String, String>()
        val spineIds = mutableListOf<String>()
        var event = parser.eventType
        var capture: String? = null
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> when (parser.name) {
                    "title" -> capture = "title"
                    "creator" -> capture = "creator"
                    "item" -> {
                        val id = parser.getAttributeValue(null, "id")
                        val href = parser.getAttributeValue(null, "href")
                        if (id != null && href != null) manifest[id] = href
                    }
                    "itemref" -> parser.getAttributeValue(null, "idref")?.let(spineIds::add)
                }
                XmlPullParser.TEXT -> {
                    val value = parser.text?.trim().orEmpty()
                    when (capture) {
                        "title" -> if (value.isNotBlank()) title = value
                        "creator" -> if (value.isNotBlank()) author = value
                    }
                }
                XmlPullParser.END_TAG -> if (parser.name == "title" || parser.name == "creator") capture = null
            }
            event = parser.next()
        }
        val spine = spineIds.mapNotNull { manifest[it] }
        return Opf(title, author, spine, manifest.values.toList())
    }

    private fun extractHeading(html: String): String? {
        val options = setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
        val title = Regex("""<title[^>]*>(.*?)</title>""", options)
            .find(html)?.groupValues?.getOrNull(1)
        val heading = Regex("""<h[12][^>]*>(.*?)</h[12]>""", options)
            .find(html)?.groupValues?.getOrNull(1)
        return (heading ?: title)?.let { Html.fromHtml(it).toString().trim() }?.takeIf { it.isNotBlank() }
    }

    private fun displayName(context: Context, uri: Uri): String? {
        runCatching {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0 && cursor.moveToFirst()) return cursor.getString(index)
            }
        }
        return uri.lastPathSegment
    }

    private fun fileSize(context: Context, uri: Uri): Long {
        runCatching {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val index = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (index >= 0 && cursor.moveToFirst()) return cursor.getLong(index)
            }
        }
        return 0
    }

    private fun guessMime(name: String): String = when {
        name.endsWith(".pdf", true) -> "application/pdf"
        name.endsWith(".epub", true) -> "application/epub+zip"
        name.endsWith(".docx", true) -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
        else -> "text/plain"
    }
}
