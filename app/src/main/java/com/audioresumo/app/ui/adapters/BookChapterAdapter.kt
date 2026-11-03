package com.audioresumo.app.ui.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.audioresumo.app.data.BookChapter
import com.audioresumo.app.databinding.ItemBookChapterBinding

class BookChapterAdapter(
    private val items: List<BookChapter>,
    private val onRead: (BookChapter) -> Unit,
    private val onSummarize: (BookChapter) -> Unit,
    private val onOpenSummary: (BookChapter) -> Unit
) : RecyclerView.Adapter<BookChapterAdapter.Holder>() {

    inner class Holder(val binding: ItemBookChapterBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(chapter: BookChapter) {
            binding.number.text = (chapter.index + 1).toString()
            binding.title.text = chapter.title
            val status = when (chapter.summaryStatus) {
                "ready" -> "resumo pronto"
                "processing" -> "gerando resumo..."
                "error" -> "falha no resumo"
                else -> "sem resumo"
            }
            binding.meta.text = "${chapter.wordCount} palavras · $status"
            binding.spinner.visibility = if (chapter.summaryStatus == "processing") View.VISIBLE else View.GONE
            binding.btnSummarize.visibility = if (chapter.summaryStatus == "processing") View.GONE else View.VISIBLE
            binding.btnSummarize.text = if (chapter.summaryStatus == "ready") "Resumir de novo" else "Resumir"
            binding.btnSummary.visibility = if (!chapter.summaryId.isNullOrBlank()) View.VISIBLE else View.GONE
            binding.btnRead.setOnClickListener { onRead(chapter) }
            binding.btnSummarize.setOnClickListener { onSummarize(chapter) }
            binding.btnSummary.setOnClickListener { onOpenSummary(chapter) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder =
        Holder(ItemBookChapterBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: Holder, position: Int) = holder.bind(items[position])
    override fun getItemCount(): Int = items.size
}
