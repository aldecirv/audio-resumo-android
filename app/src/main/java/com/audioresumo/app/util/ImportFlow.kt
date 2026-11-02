package com.audioresumo.app.util

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.text.Html
import android.util.Xml
import com.audioresumo.app.data.AudioSummary
import com.audioresumo.app.data.Store
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import java.io.File
import java.util.zip.ZipFile

object ImportFlow {

    data class Document(
        val title: String,
        val author: String,
        val text: String,
        val kind: String,
        val mime: String,
        val bytes: ByteArray? = null
    )

    suspend fun read(context: Context, uri: Uri): Result<Document> = withContext(Dispatchers.IO) {
        runCatching {
            val resolver = context.contentResolver
            val name = displayName(context, uri) ?: "Documento"
            val mime = resolver.getType(uri) ?: guessMime(name)
            val lower = name.lowercase()
            when {
                mime.contains("pdf") || lower.endsWith(".pdf") -> readPdf(context, uri, name)
                mime.contains("epub") || lower.endsWith(".epub") -> readEpub(context, uri, name)
                mime.startsWith("text/") || lower.endsWith(".txt") -> {
                    val text = resolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }.orEmpty()
                    if (text.isBlank()) error("O arquivo de texto está vazio.")
                    Document(name.substringBeforeLast('.'), "", text, "TXT", "text/plain")
                }
                else -> {
                    val text = resolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }.orEmpty()
                    if (text.isBlank()) error("Formato não suportado. Use PDF, EPUB ou TXT.")
                    Document(name.substringBeforeLast('.'), "", text, "TXT", "text/plain")
                }
            }
        }
    }

    private fun readPdf(context: Context, uri: Uri, name: String): Document {
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: error("Não foi possível abrir o PDF.")
        PDDocument.load(bytes.inputStream()).use { doc ->
            val stripper = PDFTextStripper()
            val text = runCatching { stripper.getText(doc) }.getOrDefault("")
            val info = doc.documentInformation
            val title = info?.title?.takeIf { it.isNotBlank() } ?: name.substringBeforeLast('.')
            val author = info?.author.orEmpty()
            if (text.isBlank()) {
                return Document(title, author, "", "PDF", "application/pdf", bytes)
            }
            return Document(title, author, LocalSummarizer.cleanText(text), "PDF", "application/pdf", bytes)
        }
    }

    private fun readEpub(context: Context, uri: Uri, name: String): Document {
        val temp = File.createTempFile("import_", ".epub", context.cacheDir)
        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                temp.outputStream().use { output -> input.copyTo(output) }
            } ?: error("Não foi possível abrir o EPUB.")
            ZipFile(temp).use { zip ->
                val container = zip.getEntry("META-INF/container.xml")
                    ?: error("EPUB inválido: container.xml ausente.")
                val opfPath = runCatching {
                    parseContainer(zip.getInputStream(container).bufferedReader().readText())
                }.getOrNull() ?: error("EPUB inválido: OPF não encontrado.")
                val opfEntry = zip.getEntry(opfPath) ?: error("EPUB inválido: conteúdo não encontrado.")
                val opf = zip.getInputStream(opfEntry).bufferedReader().readText()
                val meta = parseOpf(opf)
                val base = opfPath.substringBeforeLast('/', "")
                val html = StringBuilder()
                meta.spine.forEach { href ->
                    val path = if (base.isBlank()) href else "$base/$href"
                    val entry = zip.getEntry(path) ?: zip.getEntry(path.replace("%20", " "))
                    if (entry != null) {
                        val raw = zip.getInputStream(entry).bufferedReader().readText()
                        val plain = Html.fromHtml(raw, Html.FROM_HTML_MODE_LEGACY).toString()
                        html.append(plain).append("\n\n")
                    }
                }
                val text = LocalSummarizer.cleanText(html.toString())
                if (text.isBlank()) error("Não encontrei texto legível neste EPUB.")
                val title = meta.title.ifBlank { name.substringBeforeLast('.') }
                return Document(title, meta.author, text.take(300_000), "EPUB", "application/epub+zip")
            }
        } finally {
            temp.delete()
        }
    }

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

    private data class Opf(val title: String, val author: String, val spine: List<String>)

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
                    val text = parser.text?.trim().orEmpty()
                    when (capture) {
                        "title" -> if (text.isNotBlank()) title = text
                        "creator" -> if (text.isNotBlank()) author = text
                    }
                }
                XmlPullParser.END_TAG -> if (parser.name == "title" || parser.name == "creator") capture = null
            }
            event = parser.next()
        }
        val spine = spineIds.mapNotNull { manifest[it] }.ifEmpty { manifest.values.toList() }
        return Opf(title, author, spine)
    }

    suspend fun summarize(
        context: Context,
        document: Document,
        useAi: Boolean
    ): Result<AudioSummary> = withContext(Dispatchers.IO) {
        if (useAi && Store.settings.apiKey.isNotBlank()) {
            val ai = GeminiClient.generate(
                text = document.text,
                fileBytes = document.bytes,
                mimeType = if (document.bytes != null) document.mime else null,
                format = "essential",
                sourceType = document.kind.lowercase(),
                titleHint = document.title
            )
            if (ai.isSuccess) return@withContext ai
            if (document.text.length < 120) {
                return@withContext Result.failure(
                    IllegalStateException(
                        "Não foi possível ler o texto deste ${document.kind} (pode ser digitalizado). " +
                            "Verifique a chave Gemini ou use um arquivo com texto."
                    )
                )
            }
        }
        LocalSummarizer.summarize(
            text = document.text,
            title = document.title,
            author = document.author,
            format = "essential",
            sourceType = document.kind.lowercase()
        )
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

    private fun guessMime(name: String): String = when {
        name.endsWith(".pdf", true) -> "application/pdf"
        name.endsWith(".epub", true) -> "application/epub+zip"
        else -> "text/plain"
    }
}
