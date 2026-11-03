package com.audioresumo.app.ui.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.appcompat.widget.PopupMenu
import androidx.recyclerview.widget.RecyclerView
import com.audioresumo.app.R
import com.audioresumo.app.data.Book
import com.audioresumo.app.databinding.ItemBookBinding
import com.audioresumo.app.ui.BookDetailActivity

class BookAdapter(
    private val items: List<Book>,
    private val onChanged: () -> Unit
) : RecyclerView.Adapter<BookAdapter.Holder>() {

    inner class Holder(val binding: ItemBookBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(book: Book) {
            binding.cover.bind(book.title, book.coverColors, "LIVRO")
            binding.title.text = book.title
            binding.author.text = book.author.ifBlank { "Autor não identificado" }
            binding.meta.text = "${book.format.label} · ${book.progressLabel} · ${book.wordsLabel}"
            val progress = when {
                book.summaryStatus == "processing" -> book.summaryProgress
                book.chapterCount > 0 && book.lastChapterIndex > 0 ->
                    ((book.lastChapterIndex + 1) * 100 / book.chapterCount).coerceIn(0, 100)
                else -> 0
            }
            binding.progress.visibility = if (progress > 0) android.view.View.VISIBLE else android.view.View.INVISIBLE
            binding.progress.setProgressCompat(progress, false)
            binding.root.setOnClickListener {
                it.context.startActivity(BookDetailActivity.intent(it.context, book.id))
            }
            binding.more.setOnClickListener { anchor ->
                PopupMenu(anchor.context, anchor).apply {
                    menuInflater.inflate(R.menu.menu_book_item, menu)
                    menu.findItem(R.id.action_favorite).title =
                        if (book.isFavorite) "Remover dos favoritos" else "Favoritar"
                    setOnMenuItemClickListener { item ->
                        when (item.itemId) {
                            R.id.action_rename -> {
                                val current = com.audioresumo.app.data.BookStore.byId(book.id) ?: book
                                val container = android.widget.LinearLayout(anchor.context).apply {
                                    orientation = android.widget.LinearLayout.VERTICAL
                                    setPadding(40, 8, 40, 0)
                                }
                                val title = android.widget.EditText(anchor.context).apply {
                                    setText(current.title)
                                    hint = "Título"
                                }
                                val author = android.widget.EditText(anchor.context).apply {
                                    setText(current.author)
                                    hint = "Autor"
                                }
                                container.addView(title)
                                container.addView(author)
                                com.google.android.material.dialog.MaterialAlertDialogBuilder(anchor.context)
                                    .setTitle("Renomear livro")
                                    .setView(container)
                                    .setNegativeButton("Cancelar", null)
                                    .setPositiveButton("Salvar") { _, _ ->
                                        com.audioresumo.app.data.BookStore.rename(
                                            current.id,
                                            title.text?.toString().orEmpty(),
                                            author.text?.toString().orEmpty()
                                        )
                                        onChanged()
                                    }
                                    .show()
                            }
                            R.id.action_favorite -> {
                                com.audioresumo.app.data.BookStore.toggleFavorite(book.id)
                                onChanged()
                            }
                            R.id.action_delete -> confirmDelete(anchor, book, onChanged)
                        }
                        true
                    }
                    show()
                }
            }
        }
    }

    private fun confirmDelete(anchor: android.view.View, book: Book, onChanged: () -> Unit) {
        com.google.android.material.dialog.MaterialAlertDialogBuilder(anchor.context)
            .setTitle("Excluir livro?")
            .setMessage("${book.title}\n\nO texto e os resumos vinculados serão removidos deste aparelho.")
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Excluir") { _, _ ->
                com.audioresumo.app.data.BookStore.delete(book.id)
                onChanged()
            }
            .show()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder =
        Holder(ItemBookBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: Holder, position: Int) = holder.bind(items[position])
    override fun getItemCount(): Int = items.size
}
