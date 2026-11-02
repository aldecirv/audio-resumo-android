package com.audioresumo.app.ui.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.audioresumo.app.R
import com.audioresumo.app.databinding.ItemTranscriptBinding
import com.audioresumo.app.service.SpeechParagraph

class TranscriptAdapter(
    private val paragraphs: List<SpeechParagraph>,
    private var current: Int,
    private val onSeek: (Int) -> Unit
) : RecyclerView.Adapter<TranscriptAdapter.Holder>() {

    inner class Holder(val binding: ItemTranscriptBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(position: Int) {
            val paragraph = paragraphs[position]
            val showLabel = position == 0 || paragraphs[position - 1].sectionTitle != paragraph.sectionTitle
            binding.sectionLabel.visibility = if (showLabel) android.view.View.VISIBLE else android.view.View.GONE
            binding.sectionLabel.text = paragraph.sectionTitle.uppercase()
            binding.text.text = paragraph.text
            val active = position == current
            binding.indicator.visibility = if (active) android.view.View.VISIBLE else android.view.View.INVISIBLE
            binding.content.setBackgroundColor(
                if (active) ContextCompat.getColor(binding.root.context, R.color.surface)
                else ContextCompat.getColor(binding.root.context, android.R.color.transparent)
            )
            binding.root.setOnClickListener { onSeek(position) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder =
        Holder(ItemTranscriptBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: Holder, position: Int) = holder.bind(position)
    override fun getItemCount(): Int = paragraphs.size

    fun setCurrent(index: Int) {
        val old = current
        current = index
        if (old in paragraphs.indices) notifyItemChanged(old)
        if (index in paragraphs.indices) notifyItemChanged(index)
    }
}
