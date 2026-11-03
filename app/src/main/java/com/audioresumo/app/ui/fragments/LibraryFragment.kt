package com.audioresumo.app.ui.fragments

import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.audioresumo.app.R
import com.audioresumo.app.data.AudioSummary
import com.audioresumo.app.data.Book
import com.audioresumo.app.data.BookStore
import com.audioresumo.app.data.Store
import com.audioresumo.app.databinding.FragmentLibraryBinding
import com.audioresumo.app.ui.BookDetailActivity
import com.audioresumo.app.ui.BookImportActivity
import com.audioresumo.app.ui.SummaryDetailActivity
import com.audioresumo.app.ui.adapters.BookAdapter
import com.audioresumo.app.ui.adapters.SummaryRowAdapter
import com.audioresumo.app.util.BookImporter
import com.audioresumo.app.util.ImportFlow
import com.google.android.material.chip.Chip
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LibraryFragment : Fragment() {

    private var _binding: FragmentLibraryBinding? = null
    private val binding get() = _binding!!
    private var categoryFilter = "Todos"
    private var favoritesOnly = false
    private var query = ""
    private var buildingChips = false
    private var booksMode = false
    private var bookSort = "recent"
    private var searchJob: Job? = null
    private var bookResults: List<Book> = emptyList()
    private var bookUnsubscribe: (() -> Unit)? = null

    private val openDocument = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@registerForActivityResult
        if (booksMode) {
            startActivity(
                Intent(requireContext(), BookImportActivity::class.java)
                    .setData(uri)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            )
        } else {
            importDocument(uri)
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentLibraryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        categoryFilter = arguments?.getString(ARG_CATEGORY) ?: "Todos"
        binding.list.layoutManager = LinearLayoutManager(requireContext())
        binding.bookList.layoutManager = LinearLayoutManager(requireContext())
        binding.addDocument.setOnClickListener { showImportOptions() }
        binding.importCard.setOnClickListener { showImportOptions() }
        binding.tabSummaries.setOnClickListener { setBooksMode(false) }
        binding.tabBooks.setOnClickListener { setBooksMode(true) }
        binding.favoriteFilter.setOnClickListener {
            favoritesOnly = !favoritesOnly
            binding.favoriteFilter.setColorFilter(
                ContextCompat.getColor(requireContext(), if (favoritesOnly) R.color.rose else R.color.text_tertiary)
            )
            if (booksMode) loadBooks() else refresh()
        }
        binding.bookSort.setOnClickListener { chooseBookSort() }
        binding.searchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                query = s?.toString().orEmpty()
                if (booksMode) searchBooks() else refresh()
            }
        })
        binding.categoryGroup.setOnCheckedStateChangeListener { _, checkedIds ->
            if (buildingChips) return@setOnCheckedStateChangeListener
            val id = checkedIds.firstOrNull() ?: return@setOnCheckedStateChangeListener
            val chip = binding.categoryGroup.findViewById<Chip>(id)
            categoryFilter = chip?.text?.toString() ?: "Todos"
            refresh()
        }
        bookUnsubscribe = BookStore.subscribe {
            if (isAdded) view?.post { if (booksMode) loadBooks() }
        }
        buildChips()
        refresh()
    }

    private fun buildChips() {
        buildingChips = true
        binding.categoryGroup.removeAllViews()
        val categories = listOf("Todos") + Store.allCategories()
        categories.forEach { category ->
            val chip = Chip(requireContext()).apply {
                id = View.generateViewId()
                text = category
                isCheckable = true
                isChecked = category == categoryFilter
                textSize = 11f
                chipMinHeight = 34f * resources.displayMetrics.density
                chipBackgroundColor = ContextCompat.getColorStateList(requireContext(), R.color.card)
                setTextColor(ContextCompat.getColorStateList(requireContext(), R.color.text_secondary))
                chipStrokeWidth = 1f * resources.displayMetrics.density
                chipStrokeColor = ContextCompat.getColorStateList(requireContext(), R.color.border)
                isCheckedIconVisible = false
            }
            binding.categoryGroup.addView(chip)
        }
        buildingChips = false
    }

    private fun setBooksMode(enabled: Boolean) {
        booksMode = enabled
        binding.tabSummaries.setBackgroundColor(android.graphics.Color.TRANSPARENT)
        binding.tabBooks.setBackgroundColor(android.graphics.Color.TRANSPARENT)
        binding.tabSummaries.setTextColor(ContextCompat.getColor(requireContext(), if (enabled) R.color.text_secondary else R.color.brand))
        binding.tabBooks.setTextColor(ContextCompat.getColor(requireContext(), if (enabled) R.color.brand else R.color.text_secondary))
        (if (enabled) binding.tabBooks else binding.tabSummaries).setBackgroundResource(R.drawable.bg_pill)
        binding.categoryScroll.visibility = if (enabled) View.GONE else View.VISIBLE
        binding.summariesPane.visibility = if (enabled) View.GONE else View.VISIBLE
        binding.booksPane.visibility = if (enabled) View.VISIBLE else View.GONE
        binding.bookSort.visibility = if (enabled) View.VISIBLE else View.GONE
        binding.searchLayout.hint = if (enabled) "Buscar em títulos, autores e conteúdo" else "Buscar por título, autor ou assunto"
        if (enabled) {
            binding.subtitle.text = if (BookStore.books.size == 1) "1 livro na biblioteca" else "${BookStore.books.size} livros na biblioteca"
            binding.countLabel.text = if (BookStore.books.size == 1) "1 livro" else "${BookStore.books.size} livros"
            loadBooks()
        } else {
            refresh()
        }
    }

    fun refresh() {
        if (_binding == null || booksMode) return
        val filtered = Store.visibleSummaries().filter { summary ->
            val byCategory = categoryFilter == "Todos" || summary.category == categoryFilter
            val byFavorite = !favoritesOnly || summary.isFavorite
            val q = query.trim().lowercase()
            val byQuery = q.isBlank() || summary.title.lowercase().contains(q) ||
                summary.author.lowercase().contains(q) || summary.category.lowercase().contains(q) ||
                summary.executiveSummary.lowercase().contains(q)
            byCategory && byFavorite && byQuery
        }
        binding.subtitle.text = "${Store.visibleSummaries().size} resumos curados para ouvir e aplicar"
        binding.countLabel.text = if (filtered.size == 1) "1 resumo" else "${filtered.size} resumos"
        binding.emptyState.visibility = if (filtered.isEmpty()) View.VISIBLE else View.GONE
        binding.list.visibility = if (filtered.isEmpty()) View.GONE else View.VISIBLE
        binding.list.adapter = SummaryRowAdapter(filtered) { refresh() }
    }

    private fun loadBooks() {
        if (_binding == null) return
        searchJob?.cancel()
        searchJob = viewLifecycleOwner.lifecycleScope.launch {
            if (query.isNotBlank()) delay(180)
            val matches = withContext(Dispatchers.IO) {
                if (query.isBlank()) BookStore.search("") else BookStore.search(query)
            }
            var books = matches.map { it.book }
            if (favoritesOnly) books = books.filter { it.isFavorite }
            books = when (bookSort) {
                "title" -> books.sortedBy { it.title.lowercase() }
                "author" -> books.sortedBy { it.author.lowercase() }
                else -> books.sortedByDescending { it.updatedAt }
            }
            bookResults = books
            binding.subtitle.text = if (BookStore.books.size == 1) "1 livro na biblioteca" else "${BookStore.books.size} livros na biblioteca"
            binding.countLabel.text = if (books.size == 1) "1 livro" else "${books.size} livros"
            binding.bookEmpty.visibility = if (books.isEmpty()) View.VISIBLE else View.GONE
            binding.bookList.visibility = if (books.isEmpty()) View.GONE else View.VISIBLE
            binding.bookList.adapter = BookAdapter(books) { loadBooks() }
        }
    }

    private fun searchBooks() {
        if (booksMode) loadBooks()
    }

    private fun chooseBookSort() {
        val labels = arrayOf("Mais recentes", "Título (A-Z)", "Autor (A-Z)")
        val values = arrayOf("recent", "title", "author")
        val selected = values.indexOf(bookSort).coerceAtLeast(0)
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Organizar livros")
            .setSingleChoiceItems(labels, selected) { dialog, which ->
                bookSort = values[which]
                dialog.dismiss()
                loadBooks()
            }
            .show()
    }

    private fun showImportOptions() {
        val items = if (booksMode) {
            arrayOf("PDF (texto ou OCR)", "EPUB (capítulos)", "DOCX", "TXT", "Colar texto manualmente")
        } else {
            arrayOf("PDF", "EPUB", "TXT")
        }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(if (booksMode) "Adicionar livro" else "Importar documento")
            .setMessage(if (booksMode) "Importe o arquivo completo. Livros grandes são processados em capítulos, sem limite artificial de páginas." else "Importe um arquivo e gere um resumo avulso.")
            .setItems(items) { _, which ->
                if (booksMode) {
                    when (which) {
                        0 -> openDocument.launch(arrayOf("application/pdf"))
                        1 -> openDocument.launch(arrayOf("application/epub+zip"))
                        2 -> openDocument.launch(arrayOf("application/vnd.openxmlformats-officedocument.wordprocessingml.document"))
                        3 -> openDocument.launch(arrayOf("text/plain"))
                        4 -> showTextBookDialog()
                    }
                } else {
                    val mime = when (which) {
                        0 -> arrayOf("application/pdf")
                        1 -> arrayOf("application/epub+zip")
                        else -> arrayOf("text/plain")
                    }
                    openDocument.launch(mime)
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun showTextBookDialog() {
        val container = android.widget.LinearLayout(requireContext()).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(40, 8, 40, 0)
        }
        val title = android.widget.EditText(requireContext()).apply { hint = "Título do livro" }
        val author = android.widget.EditText(requireContext()).apply { hint = "Autor (opcional)" }
        val text = android.widget.EditText(requireContext()).apply {
            hint = "Cole o texto completo"
            minLines = 7
            gravity = android.view.Gravity.TOP
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE
        }
        container.addView(title)
        container.addView(author)
        container.addView(text)
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Livro a partir de texto")
            .setView(container)
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Criar livro") { _, _ ->
                viewLifecycleOwner.lifecycleScope.launch {
                    val result = withContext(Dispatchers.IO) {
                        BookImporter.fromText(
                            title.text?.toString().orEmpty(),
                            author.text?.toString().orEmpty(),
                            text.text?.toString().orEmpty()
                        )
                    }
                    result.onSuccess { startActivity(BookDetailActivity.intent(requireContext(), it.id)) }
                        .onFailure { Toast.makeText(requireContext(), it.message ?: "Texto inválido", Toast.LENGTH_LONG).show() }
                }
            }
            .show()
    }

    private fun importDocument(uri: Uri) {
        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle("Lendo arquivo...")
            .setMessage("Extraindo o texto para o resumo.")
            .setCancelable(false)
            .create()
        dialog.show()
        viewLifecycleOwner.lifecycleScope.launch {
            val result = ImportFlow.read(requireContext(), uri)
            dialog.dismiss()
            result.onSuccess { document -> chooseSummaryMode(document) }
                .onFailure { Toast.makeText(requireContext(), it.message ?: "Não foi possível ler o arquivo", Toast.LENGTH_LONG).show() }
        }
    }

    private fun chooseSummaryMode(document: ImportFlow.Document) {
        val hasKey = Store.settings.apiKey.isNotBlank()
        val options = if (hasKey) arrayOf("Resumo com Gemini (IA)", "Resumo local rápido") else arrayOf("Resumo local rápido")
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(document.title)
            .setMessage("${document.kind} · ${document.text.length} caracteres extraídos\n\nComo deseja gerar o resumo?")
            .setItems(options) { _, which ->
                generate(document, hasKey && which == 0)
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun generate(document: ImportFlow.Document, useAi: Boolean) {
        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(if (useAi) "Gerando com Gemini..." else "Criando resumo local...")
            .setMessage("Isso pode levar alguns segundos.")
            .setCancelable(false)
            .create()
        dialog.show()
        viewLifecycleOwner.lifecycleScope.launch {
            val result = ImportFlow.summarize(requireContext(), document, useAi)
            dialog.dismiss()
            result.onSuccess { summary ->
                Store.addSummary(summary)
                Toast.makeText(requireContext(), "Resumo adicionado à biblioteca", Toast.LENGTH_SHORT).show()
                startActivity(SummaryDetailActivity.intent(requireContext(), summary.id))
                refresh()
            }.onFailure {
                Toast.makeText(requireContext(), it.message ?: "Falha ao gerar resumo", Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (booksMode) loadBooks() else refresh()
    }

    override fun onDestroyView() {
        bookUnsubscribe?.invoke()
        bookUnsubscribe = null
        searchJob?.cancel()
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val ARG_CATEGORY = "category"
        fun newInstance(category: String? = null): LibraryFragment = LibraryFragment().apply {
            arguments = Bundle().apply { if (category != null) putString(ARG_CATEGORY, category) }
        }
    }
}
