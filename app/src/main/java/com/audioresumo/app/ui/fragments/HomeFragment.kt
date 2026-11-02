package com.audioresumo.app.ui.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.audioresumo.app.R
import com.audioresumo.app.ui.MainActivity
import com.audioresumo.app.data.Store
import com.audioresumo.app.databinding.FragmentHomeBinding
import com.audioresumo.app.service.SpeechContent
import com.audioresumo.app.service.SpeechService
import com.audioresumo.app.ui.PlayerActivity
import com.audioresumo.app.ui.SummaryDetailActivity
import com.audioresumo.app.ui.adapters.CoverAdapter
import com.audioresumo.app.util.Ui
import com.google.android.material.chip.Chip

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.trendingList.layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        binding.quickList.layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        binding.trendingAll.setOnClickListener { (activity as? MainActivity)?.openTab(R.id.navLibrary) }
        refresh()
    }

    fun refresh() {
        if (_binding == null) return
        val user = Store.user
        binding.greetingLabel.text = "BOAS-VINDAS, ${user.firstName.uppercase()}"
        binding.streakText.text = "${user.streakDays} dias"

        val continueSummary = Store.continueSummary()
        if (continueSummary == null) {
            binding.continueCard.visibility = View.GONE
        } else {
            binding.continueCard.visibility = View.VISIBLE
            binding.continueCover.bind(continueSummary)
            binding.continueTitle.text = continueSummary.title
            binding.continueAuthor.text = continueSummary.author
            binding.continueMeta.text = "${continueSummary.category} · ${continueSummary.durationLabel}"
            binding.continuePercent.text = if (continueSummary.progressPercent > 0) {
                "${continueSummary.progressPercent}% concluído"
            } else "Recomeçar"
            binding.continueCard.setOnClickListener {
                startActivity(SummaryDetailActivity.intent(requireContext(), continueSummary.id))
            }
            binding.continuePlay.setOnClickListener {
                val paragraphs = SpeechContent.build(continueSummary)
                val index = paragraphs.indexOfLast { it.startSeconds <= continueSummary.progressSeconds }
                    .coerceAtLeast(0)
                SpeechService.start(requireContext(), continueSummary.id, index, true)
                startActivity(PlayerActivity.intent(requireContext(), continueSummary.id))
            }
        }

        val featured = Store.featured() ?: Store.summaries.firstOrNull()
        if (featured == null) {
            binding.featuredCard.visibility = View.GONE
        } else {
            binding.featuredCard.visibility = View.VISIBLE
            binding.featuredBg.background = Ui.gradient(
                featured.coverColors,
                26f * resources.displayMetrics.density
            )
            binding.featuredCard.clipToOutline = true
            binding.featuredCategory.text = featured.category.uppercase()
            binding.featuredTitle.text = featured.title
            binding.featuredSubtitle.text = featured.subtitle
            binding.featuredMeta.text = "${featured.author} · ${featured.durationLabel} · ★ ${String.format(java.util.Locale.US, "%.1f", featured.rating)}"
            binding.featuredCard.setOnClickListener {
                startActivity(SummaryDetailActivity.intent(requireContext(), featured.id))
            }
            binding.featuredPlay.setOnClickListener {
                SpeechService.start(requireContext(), featured.id, 0, true)
                startActivity(PlayerActivity.intent(requireContext(), featured.id))
            }
        }

        binding.trendingList.adapter = CoverAdapter(Store.trending(8))
        binding.quickList.adapter = CoverAdapter(Store.quickListens(8))

        binding.categoryGroup.removeAllViews()
        Store.allCategories().take(10).forEach { category ->
            val chip = Chip(requireContext()).apply {
                text = category
                isCheckable = false
                isClickable = true
                textSize = 11f
                chipMinHeight = 34f * resources.displayMetrics.density
                chipBackgroundColor = android.content.res.ColorStateList.valueOf(
                    androidx.core.content.ContextCompat.getColor(requireContext(), R.color.card)
                )
                setTextColor(androidx.core.content.ContextCompat.getColor(requireContext(), R.color.text_secondary))
                chipStrokeWidth = 1f * resources.displayMetrics.density
                chipStrokeColor = android.content.res.ColorStateList.valueOf(
                    androidx.core.content.ContextCompat.getColor(requireContext(), R.color.border)
                )
                setOnClickListener { (activity as? MainActivity)?.openTab(R.id.navLibrary, category) }
            }
            binding.categoryGroup.addView(chip)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
