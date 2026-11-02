package com.audioresumo.app.ui

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.audioresumo.app.R
import com.audioresumo.app.data.AudioSummary
import com.audioresumo.app.data.Store
import com.audioresumo.app.databinding.ActivityQuizBinding
import com.audioresumo.app.util.ThemeUtils
import com.audioresumo.app.util.Ui
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView

class QuizActivity : AppCompatActivity() {

    private lateinit var binding: ActivityQuizBinding
    private lateinit var summary: AudioSummary
    private var index = 0
    private var correct = 0
    private var answered = false
    private var finished = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityQuizBinding.inflate(layoutInflater)
        setContentView(binding.root)
        ThemeUtils.darkSystemBars(this)
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.updatePadding(top = bars.top, bottom = bars.bottom)
            insets
        }
        binding.back.setOnClickListener { finish() }
        val loaded = intent.getStringExtra(EXTRA_ID)?.let(Store::byId)
        if (loaded == null || loaded.quiz.isEmpty()) {
            finish()
            return
        }
        summary = loaded
        binding.title.text = summary.title
        binding.next.setOnClickListener { next() }
        binding.retry.setOnClickListener { restart() }
        binding.close.setOnClickListener { finish() }
        render()
    }

    private fun render() {
        answered = false
        finished = false
        binding.quizScroll.visibility = View.VISIBLE
        binding.resultContainer.visibility = View.GONE
        binding.feedbackCard.visibility = View.GONE
        binding.next.visibility = View.GONE
        val question = summary.quiz.getOrNull(index) ?: return
        binding.progressLabel.text = "${index + 1} / ${summary.quiz.size}"
        binding.question.text = question.question
        binding.optionsContainer.removeAllViews()
        question.options.forEachIndexed { optionIndex, option ->
            val button = MaterialButton(this).apply {
                text = option
                textSize = 12.5f
                isAllCaps = false
                gravity = Gravity.START or Gravity.CENTER_VERTICAL
                setTextColor(ContextCompat.getColor(this@QuizActivity, R.color.text_primary))
                backgroundTintList = ContextCompat.getColorStateList(this@QuizActivity, R.color.card)
                strokeColor = ContextCompat.getColorStateList(this@QuizActivity, R.color.border)
                strokeWidth = Ui.dp(this@QuizActivity, 1f)
                cornerRadius = Ui.dp(this@QuizActivity, 14f)
                minHeight = Ui.dp(this@QuizActivity, 54f)
                setPadding(Ui.dp(this@QuizActivity, 14f), Ui.dp(this@QuizActivity, 8f), Ui.dp(this@QuizActivity, 14f), Ui.dp(this@QuizActivity, 8f))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = Ui.dp(this@QuizActivity, 8f) }
                setOnClickListener { select(optionIndex) }
            }
            binding.optionsContainer.addView(button)
        }
    }

    private fun select(selected: Int) {
        if (answered) return
        answered = true
        val question = summary.quiz.getOrNull(index) ?: return
        val isCorrect = selected == question.correctIndex
        if (isCorrect) correct++
        for (i in 0 until binding.optionsContainer.childCount) {
            val button = binding.optionsContainer.getChildAt(i) as MaterialButton
            button.isEnabled = false
            when (i) {
                question.correctIndex -> {
                    button.backgroundTintList = ContextCompat.getColorStateList(this, R.color.emerald)
                    button.setTextColor(ContextCompat.getColor(this, R.color.bg))
                    button.strokeWidth = 0
                }
                selected -> {
                    button.backgroundTintList = ContextCompat.getColorStateList(this, R.color.rose)
                    button.setTextColor(Color.WHITE)
                    button.strokeWidth = 0
                }
            }
        }
        binding.feedbackCard.visibility = View.VISIBLE
        binding.feedbackTitle.text = if (isCorrect) "Correto!" else "Quase lá"
        binding.feedbackTitle.setTextColor(
            ContextCompat.getColor(this, if (isCorrect) R.color.emerald else R.color.rose)
        )
        binding.explanation.text = question.explanation
        binding.next.visibility = View.VISIBLE
        binding.next.text = if (index + 1 < summary.quiz.size) "Próxima pergunta" else "Ver resultado"
    }

    private fun next() {
        if (index + 1 < summary.quiz.size) {
            index++
            render()
        } else {
            showResult()
        }
    }

    private fun showResult() {
        finished = true
        Store.saveQuizResult(summary.id, correct, summary.quiz.size)
        binding.quizScroll.visibility = View.GONE
        binding.resultContainer.visibility = View.VISIBLE
        binding.resultScore.text = "$correct / ${summary.quiz.size}"
        binding.resultText.text = when {
            correct == summary.quiz.size -> "Perfeito! Você dominou este resumo."
            correct >= summary.quiz.size / 2 -> "Bom resultado! Revise as explicações para fixar."
            else -> "Vale ouvir o resumo novamente e revisar os flashcards."
        }
    }

    private fun restart() {
        index = 0
        correct = 0
        render()
    }

    companion object {
        private const val EXTRA_ID = "summary_id"
        fun intent(context: android.content.Context, id: String) =
            Intent(context, QuizActivity::class.java).putExtra(EXTRA_ID, id)
    }
}
