package com.audioresumo.app.ui.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.audioresumo.app.R
import com.audioresumo.app.data.AudioSummary
import com.audioresumo.app.data.Store
import com.audioresumo.app.databinding.ItemSummaryRowBinding
import com.audioresumo.app.ui.SummaryDetailActivity
import com.audioresumo.app.util.Ui

class SummaryRowAdapter(
    private val items: List<AudioSummary>,
    private val onFavorite: (AudioSummary) -> Unit = {}
) : RecyclerView.Adapter<SummaryRowAdapter.Holder>() {

    inner class Holder(val binding: ItemSummaryRowBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(summary: AudioSummary) {
            binding.cover.bind(summary)
            binding.title.text = summary.title
            binding.author.text = summary.author
            binding.meta.text = "${summary.category} · ${summary.durationLabel} · ${Ui.compact(summary.listensCount)}"
            binding.progress.visibility = if (summary.progressPercent > 0) android.view.View.VISIBLE else android.view.View.INVISIBLE
            binding.progress.setProgressCompat(summary.progressPercent, false)
            binding.favorite.setImageResource(R.drawable.ic_favorite)
            binding.favorite.setColorFilter(
                ContextCompat.getColor(
                    binding.root.context,
                    if (summary.isFavorite) R.color.rose else R.color.text_tertiary
                )
            )
            binding.favorite.alpha = if (summary.isFavorite) 1f else 0.7f
            binding.root.setOnClickListener {
                it.context.startActivity(SummaryDetailActivity.intent(it.context, summary.id))
            }
            binding.favorite.setOnClickListener {
                Store.toggleFavorite(summary.id)
                onFavorite(summary)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder =
        Holder(ItemSummaryRowBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: Holder, position: Int) = holder.bind(items[position])
    override fun getItemCount(): Int = items.size
}
