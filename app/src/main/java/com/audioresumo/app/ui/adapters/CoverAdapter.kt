package com.audioresumo.app.ui.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.audioresumo.app.R
import com.audioresumo.app.data.AudioSummary
import com.audioresumo.app.databinding.ItemCoverBinding
import com.audioresumo.app.ui.SummaryDetailActivity
import java.util.Locale

class CoverAdapter(
    private val items: List<AudioSummary>
) : RecyclerView.Adapter<CoverAdapter.Holder>() {

    inner class Holder(val binding: ItemCoverBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(summary: AudioSummary) {
            binding.cover.bind(summary)
            binding.title.text = summary.title
            binding.author.text = summary.author
            binding.meta.text = summary.durationLabel
            binding.rating.text = String.format(Locale.US, "%.1f", summary.rating)
            binding.root.setOnClickListener {
                it.context.startActivity(SummaryDetailActivity.intent(it.context, summary.id))
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder =
        Holder(ItemCoverBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: Holder, position: Int) = holder.bind(items[position])
    override fun getItemCount(): Int = items.size
}
