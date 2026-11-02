package com.audioresumo.app.ui.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.audioresumo.app.data.Bookmark
import com.audioresumo.app.databinding.ItemBookmarkBinding

class BookmarkAdapter(
    private val items: List<Bookmark>,
    private val onDelete: (Bookmark) -> Unit
) : RecyclerView.Adapter<BookmarkAdapter.Holder>() {

    inner class Holder(val binding: ItemBookmarkBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(bookmark: Bookmark) {
            binding.section.text = bookmark.sectionTitle
            binding.note.text = bookmark.note.ifBlank { "Trecho salvo na narração" }
            binding.delete.setOnClickListener { onDelete(bookmark) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder =
        Holder(ItemBookmarkBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: Holder, position: Int) = holder.bind(items[position])
    override fun getItemCount(): Int = items.size
}
