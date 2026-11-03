package com.audioresumo.app.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList

/** Persiste metadados em JSON e o texto de cada capítulo em arquivo separado. */
object BookStore {

    lateinit var appContext: Context
        private set

    val books = mutableListOf<Book>()
    private val listeners = CopyOnWriteArrayList<() -> Unit>()
    private var initialized = false

    private fun root(): File = File(appContext.filesDir, "books").apply { mkdirs() }
    private fun bookFile(id: String) = File(root(), "$id.json")
    private fun chaptersDir(id: String) = File(root(), id).apply { mkdirs() }
    private fun chapterFile(id: String, index: Int) = File(chaptersDir(id), "$index.txt")

    fun init(context: Context) {
        if (initialized) return
        appContext = context.applicationContext
        root().listFiles { file -> file.extension == "json" }?.forEach { file ->
            runCatching {
                val json = JSONObject(file.readText())
                bookFromJson(json)
            }.onSuccess { books.add(it) }
        }
        books.sortByDescending { it.updatedAt }
        // Processamento interrompido por fechamento do app/serviço: permite continuar.
        books.filter { it.summaryStatus == "processing" }.forEach {
            it.summaryStatus = "paused"
            it.status = BookStatus.READY
            it.message = "Processamento interrompido. Toque em continuar para retomar."
            runCatching { bookFile(it.id).writeText(bookToJson(it).toString()) }
        }
        initialized = true
    }

    fun subscribe(listener: () -> Unit): () -> Unit {
        listeners.add(listener)
        return { listeners.remove(listener) }
    }

    fun notifyChanged() {
        listeners.toList().forEach { runCatching { it() } }
    }

    fun byId(id: String): Book? = books.firstOrNull { it.id == id }

    fun add(book: Book) {
        books.removeAll { it.id == book.id }
        save(book)
        books.add(0, book.copyMetadata())
        notifyChanged()
    }

    fun update(book: Book) {
        save(book)
        val index = books.indexOfFirst { it.id == book.id }
        if (index >= 0) books[index] = book.copyMetadata() else books.add(0, book.copyMetadata())
        notifyChanged()
    }

    fun save(book: Book) {
        runCatching {
            book.updatedAt = java.time.LocalDateTime.now()
            book.updateCounts()
            chaptersDir(book.id)
            book.chapters.forEach { chapter ->
                if (chapter.text.isNotBlank()) {
                    chapterFile(book.id, chapter.index).writeText(chapter.text)
                }
            }
            bookFile(book.id).writeText(bookToJson(book).toString())
            books.sortByDescending { it.updatedAt }
        }
    }

    /** Atualiza apenas o JSON de metadados (sem reescrever capítulos). */
    fun updateMetadata(book: Book) {
        runCatching {
            book.updatedAt = java.time.LocalDateTime.now()
            bookFile(book.id).writeText(bookToJson(book).toString())
            val index = books.indexOfFirst { it.id == book.id }
            if (index >= 0) books[index] = book.copyMetadata()
            notifyChanged()
        }
    }

    fun loadFull(id: String): Book? {
        val metadata = byId(id) ?: return null
        val full = metadata.copy(
            chapters = metadata.chapters.map { chapter ->
                chapter.copy(text = runCatching { chapterFile(id, chapter.index).readText() }.getOrDefault(""))
            }.toMutableList()
        )
        return full
    }

    fun loadChapterText(id: String, index: Int): String =
        runCatching { chapterFile(id, index).readText() }.getOrDefault("")

    fun delete(id: String) {
        val book = byId(id)
        book?.chapters?.mapNotNull { it.summaryId }?.forEach { Store.removeSummary(it) }
        book?.summaryId?.let { Store.removeSummary(it) }
        books.removeAll { it.id == id }
        runCatching {
            bookFile(id).delete()
            chaptersDir(id).deleteRecursively()
        }
        notifyChanged()
    }

    fun rename(id: String, title: String, author: String) {
        val book = byId(id) ?: return
        book.title = Book.fromJsonTitle(title)
        book.author = author.trim()
        save(book)
        notifyChanged()
    }

    fun toggleFavorite(id: String) {
        val book = byId(id) ?: return
        book.isFavorite = !book.isFavorite
        save(book)
        notifyChanged()
    }

    fun saveProgress(id: String, chapterIndex: Int, scroll: Int) {
        val book = byId(id) ?: return
        book.lastChapterIndex = chapterIndex.coerceAtLeast(0)
        book.lastScroll = scroll.coerceAtLeast(0)
        save(book)
        notifyChanged()
    }

    fun saveSummary(book: Book) {
        update(book)
    }

    fun clearAll() {
        books.forEach { book ->
            book.chapters.mapNotNull { it.summaryId }.forEach { Store.removeSummary(it) }
            book.summaryId?.let { Store.removeSummary(it) }
            runCatching {
                bookFile(book.id).delete()
                chaptersDir(book.id).deleteRecursively()
            }
        }
        books.clear()
        notifyChanged()
    }

    fun search(query: String): List<BookMatch> {
        val q = query.trim().lowercase()
        if (q.isBlank()) return books.map { BookMatch(it) }
        val matches = mutableListOf<BookMatch>()
        books.forEach { book ->
            val titleMatch = book.title.lowercase().contains(q) || book.author.lowercase().contains(q)
            var snippet = ""
            if (!titleMatch) {
                for (chapter in book.chapters) {
                    val text = loadChapterText(book.id, chapter.index)
                    val position = text.lowercase().indexOf(q)
                    if (position >= 0) {
                        val start = (position - 70).coerceAtLeast(0)
                        snippet = text.substring(start, (position + q.length + 110).coerceAtMost(text.length))
                            .replace(Regex("\\s+"), " ")
                        break
                    }
                    if (chapter.title.lowercase().contains(q)) {
                        snippet = chapter.title
                        break
                    }
                }
            }
            if (titleMatch || snippet.isNotBlank()) matches.add(BookMatch(book, snippet))
        }
        return matches
    }

    private fun bookToJson(book: Book): JSONObject = JSONObject().apply {
        put("id", book.id); put("title", book.title); put("author", book.author)
        put("format", book.format.name); put("fileName", book.fileName)
        put("sizeBytes", book.sizeBytes); put("importedAt", book.importedAt.toString())
        put("updatedAt", book.updatedAt.toString()); put("totalWords", book.totalWords)
        put("totalChars", book.totalChars); put("status", book.status.name)
        put("progress", book.progress); put("message", book.message)
        put("summaryId", book.summaryId ?: JSONObject.NULL)
        put("summaryMinutes", book.summaryMinutes); put("summaryStatus", book.summaryStatus)
        put("summaryProgress", book.summaryProgress); put("isFavorite", book.isFavorite)
        put("lastChapterIndex", book.lastChapterIndex); put("lastScroll", book.lastScroll)
        put("coverColors", JSONArray(book.coverColors))
        put("blockDigests", JSONObject().apply {
            book.blockDigests.forEach { (key, value) -> put(key, value) }
        })
        put("chapters", JSONArray().apply {
            book.chapters.forEach { chapter ->
                put(JSONObject().apply {
                    put("id", chapter.id); put("index", chapter.index); put("title", chapter.title)
                    put("charCount", chapter.charCount); put("wordCount", chapter.wordCount)
                    put("summaryId", chapter.summaryId ?: JSONObject.NULL)
                    put("summaryStatus", chapter.summaryStatus)
                })
            }
        })
    }

    private fun bookFromJson(json: JSONObject): Book {
        val chapters = mutableListOf<BookChapter>()
        json.optJSONArray("chapters")?.let { arr ->
            for (i in 0 until arr.length()) {
                val c = arr.getJSONObject(i)
                chapters.add(
                    BookChapter(
                        id = c.optString("id"),
                        index = c.optInt("index", i),
                        title = c.optString("title", "Capítulo ${i + 1}"),
                        charCount = c.optInt("charCount", 0),
                        wordCount = c.optInt("wordCount", 0),
                        summaryId = c.optString("summaryId").takeIf { it.isNotBlank() && it != "null" },
                        summaryStatus = c.optString("summaryStatus", "none")
                    )
                )
            }
        }
        val digests = mutableMapOf<String, String>()
        json.optJSONObject("blockDigests")?.let { obj ->
            obj.keys().forEach { key -> digests[key] = obj.optString(key) }
        }
        val colors = mutableListOf<String>()
        json.optJSONArray("coverColors")?.let { arr ->
            for (i in 0 until arr.length()) colors.add(arr.optString(i))
        }
        return Book(
            id = json.optString("id"),
            title = json.optString("title", "Livro"),
            author = json.optString("author", ""),
            format = BookFormat.from(json.optString("format")),
            fileName = json.optString("fileName", ""),
            sizeBytes = json.optLong("sizeBytes", 0),
            importedAt = runCatching { java.time.LocalDateTime.parse(json.optString("importedAt")) }.getOrDefault(java.time.LocalDateTime.now()),
            updatedAt = runCatching { java.time.LocalDateTime.parse(json.optString("updatedAt")) }.getOrDefault(java.time.LocalDateTime.now()),
            chapters = chapters,
            totalWords = json.optInt("totalWords", 0),
            totalChars = json.optInt("totalChars", 0),
            status = runCatching { BookStatus.valueOf(json.optString("status")) }.getOrDefault(BookStatus.READY),
            progress = json.optInt("progress", 0),
            message = json.optString("message", ""),
            summaryId = json.optString("summaryId").takeIf { it.isNotBlank() && it != "null" },
            summaryMinutes = json.optInt("summaryMinutes", 15),
            summaryStatus = json.optString("summaryStatus", "none"),
            summaryProgress = json.optInt("summaryProgress", 0),
            isFavorite = json.optBoolean("isFavorite", false),
            lastChapterIndex = json.optInt("lastChapterIndex", 0),
            lastScroll = json.optInt("lastScroll", 0),
            coverColors = colors.ifEmpty { Book.colorsFor(json.optString("title")) },
            blockDigests = digests
        )
    }
}
