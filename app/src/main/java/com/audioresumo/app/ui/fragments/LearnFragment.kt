package com.audioresumo.app.ui.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.audioresumo.app.data.AudioSummary
import com.audioresumo.app.data.Store
import com.audioresumo.app.databinding.FragmentLearnBinding
import com.audioresumo.app.ui.FlashcardsActivity
import com.audioresumo.app.ui.MindMapActivity
import com.audioresumo.app.ui.QuizActivity
import com.audioresumo.app.ui.ReviewPlanActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class LearnFragment : Fragment() {

    private var _binding: FragmentLearnBinding? = null
    private val binding get() = _binding!!
    private var current: AudioSummary? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentLearnBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        current = Store.continueSummary() ?: Store.visibleSummaries().firstOrNull()
        binding.switchSummary.setOnClickListener { chooseSummary() }
        binding.learnSummaryCard.setOnClickListener { current?.let { startActivity(com.audioresumo.app.ui.SummaryDetailActivity.intent(requireContext(), it.id)) } }
        binding.tileMindMap.setOnClickListener { current?.let { startActivity(MindMapActivity.intent(requireContext(), it.id)) } }
        binding.tileFlashcards.setOnClickListener { current?.let { startActivity(FlashcardsActivity.intent(requireContext(), it.id)) } }
        binding.tileQuiz.setOnClickListener { current?.let { startActivity(QuizActivity.intent(requireContext(), it.id)) } }
        binding.tileReview.setOnClickListener { current?.let { startActivity(ReviewPlanActivity.intent(requireContext(), it.id)) } }
        refresh()
    }

    fun refresh() {
        if (_binding == null) return
        if (current == null) current = Store.continueSummary() ?: Store.visibleSummaries().firstOrNull()
        val summary = current
        if (summary != null) {
            binding.learnCover.bind(summary)
            binding.learnTitle.text = summary.title
            binding.learnAuthor.text = "${summary.author} · ${summary.flashcards.size} flashcards · ${summary.quiz.size} perguntas"
        }
        binding.statSummaries.text = Store.visibleSummaries().size.toString()
        binding.statMinutes.text = "${Store.user.totalAudioMinutes}m"
        binding.statQuiz.text = Store.user.completedQuizzes.toString()
    }

    private fun chooseSummary() {
        val items = Store.visibleSummaries().map { "${it.title} · ${it.author}" }.toTypedArray()
        if (items.isEmpty()) return
        val index = Store.visibleSummaries().indexOfFirst { it.id == current?.id }.coerceAtLeast(0)
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Escolha o resumo para estudar")
            .setSingleChoiceItems(items, index) { dialog, which ->
                current = Store.visibleSummaries()[which]
                dialog.dismiss()
                refresh()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
