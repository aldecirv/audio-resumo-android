package com.audioresumo.app.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.audioresumo.app.R
import com.audioresumo.app.data.Book
import com.audioresumo.app.data.BookStore
import com.audioresumo.app.data.Store
import com.audioresumo.app.databinding.ActivityBookDetailBinding
import com.audioresumo.app.service.BookProcessingService
import com.audioresumo.app.service.SpeechService
import com.audioresumo.app.ui.adapters.BookChapterAdapter
import com.audioresumo.app.util.BookProcessor
import com.audioresumo.app.util.ThemeUtils
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch

class BookDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityBookDetailBinding
    private lateinit var bookId: String
    private var book: Book? = null
    private var adapter: BookChapterAdapter? = null
    private var unsubscribe: (() -> Unit)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ThemeUtils.darkSystemBars(this)
        binding = ActivityBookDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.updatePadding(top = bars.top, bottom = bars.bottom)
            insets
        }
        bookId = intent.getStringExtra(EXTRA_ID) ?: run { finish(); return }
        book = BookStore.byId(bookId) ?: run { finish(); return }
        binding.back.setOnClickListener { finish() }
        binding.favorite.setOnClickListener {
            BookStore.toggleFavorite(bookId)
            render()
        }
        binding.more.setOnClickListener { showMoreMenu() }
        binding.btnSummaryBook.setOnClickListener { showDurationDialog() }
        binding.btnAsk.setOnClickListener { startActivity(BookQaActivity.intent(this, bookId)) }
        binding.btnContinueReading.setOnClickListener {
            val current = book ?: return@setOnClickListener
            val index = current.lastChapterIndex.coerceIn(0, (current.chapterCount - 1).coerceAtLeast(0))
            startActivity(BookReaderActivity.intent(this, current.id, index))
        }
        binding.cancelProcessing.setOnClickListener {
            val current = book ?: return@setOnClickListener
            if (current.summaryStatus == "processing") {
                current.summaryStatus = "paused"
                current.status = com.audioresumo.app.data.BookStatus.READY
                current.message = "Processamento cancelado. Toque em continuar."
                BookStore.updateMetadata(current)
                BookProcessingService.cancel(this)
                render()
            } else {
                startSummary(current.summaryMinutes)
            }
        }
        binding.btnOpenSummary.setOnClickListener {
            book?.summaryId?.let { startActivity(SummaryDetailActivity.intent(this, it)) }
        }
        binding.btnListenSummary.setOnClickListener {
            val id = book?.summaryId ?: return@setOnClickListener
            SpeechService.start(this, id, 0, true)
            startActivity(PlayerActivity.intent(this, id))
        }
        unsubscribe = BookStore.subscribe { runOnUiThread { render() } }
        render()
    }

    private fun render() {
        val current = BookStore.byId(bookId) ?: run { finish(); return }
        book = current
        binding.cover.bind(current.title, current.coverColors, "LIVRO")
        binding.title.text = current.title
        binding.author.text = current.author.ifBlank { "Autor não identificado" }
        binding.meta.text = "${current.format.label} · ${current.progressLabel} · ${current.wordsLabel}"
        binding.chapterCount.text = current.progressLabel
        binding.favorite.setColorFilter(
            ContextCompat.getColor(this, if (current.isFavorite) R.color.rose else R.color.text_secondary)
        )

        val processing = current.summaryStatus == "processing"
        val interrupted = current.summaryStatus == "paused" || current.summaryStatus == "error"
        binding.progressCard.isVisible = processing || interrupted
        if (processing || interrupted) {
            binding.progressLabel.text = when (current.summaryStatus) {
                "processing" -> "Processando livro completo..."
                "paused" -> "Processamento pausado"
                else -> "Falha no processamento"
            }
            binding.progress.setProgressCompat(current.summaryProgress.coerceIn(0, 100), false)
            binding.progressMessage.text = current.message.ifBlank { "Aguarde..." }
            binding.cancelProcessing.text = if (processing) "Cancelar processamento" else "Continuar processamento"
        }

        val summary = current.summaryId?.let(Store::byId)
        binding.summaryCard.isVisible = summary != null
        if (summary != null) {
            binding.summaryTitle.text = summary.title
            binding.summarySubtitle.text = buildString {
                append(summary.estimatedMinutes).append(" min · ")
                append(if (summary.sourceType == "local") "modo local extrativo" else "gerado com IA")
                if (current.summaryMinutes > 0) append(" · opção ${current.summaryMinutes} min")
            }
            binding.summaryPreview.text = summary.executiveSummary
        }

        adapter = BookChapterAdapter(
            current.chapters,
            onRead = { chapter ->
                startActivity(BookReaderActivity.intent(this, current.id, chapter.index))
            },
            onSummarize = { chapter -> summarizeChapter(current, chapter.index) },
            onOpenSummary = { chapter ->
                chapter.summaryId?.let { startActivity(SummaryDetailActivity.intent(this, it)) }
            }
        )
        binding.chapterList.layoutManager = LinearLayoutManager(this)
        binding.chapterList.adapter = adapter

        binding.btnSummaryBook.text = if (summary != null) "Gerar outro resumo" else "Resumo do livro"
        val resumeIndex = current.lastChapterIndex.coerceIn(0, (current.chapterCount - 1).coerceAtLeast(0))
        val resumeTitle = current.chapters.getOrNull(resumeIndex)?.title ?: "Começar a ler"
        binding.btnContinueReading.text = if (current.lastChapterIndex > 0) "Continuar: $resumeTitle" else "Começar a ler"
    }

    private fun showDurationDialog() {
        val current = book ?: return
        val options = arrayOf("5 minutos", "10 minutos", "15 minutos", "20 minutos")
        val values = intArrayOf(5, 10, 15, 20)
        val selected = values.indexOf(current.summaryMinutes).coerceAtLeast(2)
        MaterialAlertDialogBuilder(this)
            .setTitle("Duração do resumo do livro")
            .setSingleChoiceItems(options, selected) { dialog, which ->
                dialog.dismiss()
                if (Store.settings.apiKey.isBlank()) {
                    Toast.makeText(
                        this,
                        "Modo local: o resumo será extrativo e identificado como local.",
                        Toast.LENGTH_LONG
                    ).show()
                }
                startSummary(values[which])
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun startSummary(minutes: Int) {
        val current = book ?: return
        if (Store.settings.apiKey.isBlank()) {
            MaterialAlertDialogBuilder(this)
                .setTitle("Gerar resumo local?")
                .setMessage(
                    "Sem chave Gemini, o resumo será extrativo, gerado a partir de todos os capítulos e identificado como “modo local”. " +
                        "Para um resumo interpretativo do livro inteiro, configure a chave em Perfil."
                )
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("Gerar local") { _, _ ->
                    BookProcessingService.start(this, current.id, minutes)
                }
                .show()
        } else {
            BookProcessingService.start(this, current.id, minutes)
        }
    }

    private fun summarizeChapter(book: Book, chapterIndex: Int) {
        val chapter = book.chapters.getOrNull(chapterIndex) ?: return
        chapter.summaryStatus = "processing"
        BookStore.updateMetadata(book)
        render()
        lifecycleScope.launch {
            val result = BookProcessor.summarizeChapter(this@BookDetailActivity, book.id, chapterIndex) {
                runOnUiThread {
                    binding.progressMessage.text = it.message
                }
            }
            result.onFailure {
                Toast.makeText(this@BookDetailActivity, it.message ?: "Falha ao resumir capítulo", Toast.LENGTH_LONG).show()
            }.onSuccess {
                Toast.makeText(this@BookDetailActivity, "Resumo do capítulo salvo", Toast.LENGTH_SHORT).show()
            }
            render()
        }
    }

    private fun showMoreMenu() {
        val popup = androidx.appcompat.widget.PopupMenu(this, binding.more)
        popup.menu.add("Renomear livro")
        popup.menu.add("Excluir livro")
        popup.setOnMenuItemClickListener { item ->
            when (item.title.toString()) {
                "Renomear livro" -> renameBook()
                "Excluir livro" -> deleteBook()
            }
            true
        }
        popup.show()
    }

    private fun renameBook() {
        val current = book ?: return
        val container = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(48, 8, 48, 0)
        }
        val title = android.widget.EditText(this).apply { setText(current.title); hint = "Título" }
        val author = android.widget.EditText(this).apply { setText(current.author); hint = "Autor" }
        container.addView(title)
        container.addView(author)
        MaterialAlertDialogBuilder(this)
            .setTitle("Renomear livro")
            .setView(container)
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Salvar") { _, _ ->
                BookStore.rename(current.id, title.text?.toString().orEmpty(), author.text?.toString().orEmpty())
                render()
            }
            .show()
    }

    private fun deleteBook() {
        val current = book ?: return
        MaterialAlertDialogBuilder(this)
            .setTitle("Excluir livro?")
            .setMessage("${current.title}\n\nO texto e os resumos vinculados serão removidos deste aparelho.")
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Excluir") { _, _ ->
                BookStore.delete(current.id)
                finish()
            }
            .show()
    }

    override fun onDestroy() {
        unsubscribe?.invoke()
        unsubscribe = null
        super.onDestroy()
    }

    companion object {
        private const val EXTRA_ID = "book_id"
        fun intent(context: android.content.Context, bookId: String) =
            Intent(context, BookDetailActivity::class.java).putExtra(EXTRA_ID, bookId)
    }
}
