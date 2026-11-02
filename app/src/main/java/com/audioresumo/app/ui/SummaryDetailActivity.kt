package com.audioresumo.app.ui

import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.audioresumo.app.R
import com.audioresumo.app.data.AudioSummary
import com.audioresumo.app.data.Store
import com.audioresumo.app.databinding.ActivitySummaryDetailBinding
import com.audioresumo.app.service.SpeechContent
import com.audioresumo.app.service.SpeechService
import com.audioresumo.app.util.ThemeUtils
import com.audioresumo.app.util.Ui
import com.google.android.material.card.MaterialCardView

class SummaryDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySummaryDetailBinding
    private lateinit var summary: AudioSummary

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySummaryDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)
        ThemeUtils.darkSystemBars(this)
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.updatePadding(top = bars.top, bottom = bars.bottom)
            insets
        }

        val id = intent.getStringExtra(EXTRA_ID)
        val loaded = id?.let { Store.byId(it) }
        if (loaded == null) {
            finish()
            return
        }
        summary = loaded
        binding.back.setOnClickListener { finish() }
        binding.title.text = summary.title
        binding.subtitle.text = summary.subtitle
        binding.cover.bind(summary)
        binding.categoryPill.text = summary.category.uppercase()
        binding.formatPill.text = summary.formatLabel.uppercase()
        binding.meta.text = "${summary.author} · ${summary.durationLabel} · ★ ${summary.rating} · ${Ui.compact(summary.listensCount)} ouvintes"
        binding.overview.text = summary.executiveSummary
        binding.conclusion.text = summary.conclusion
        binding.sectionCount.text = "${summary.sections.size} seções"

        binding.favorite.setOnClickListener {
            Store.toggleFavorite(summary.id)
            renderFavorite()
        }
        binding.share.setOnClickListener {
            val text = "Ouça o resumo de \"${summary.title}\" no Áudio de Resumo.\n\n${summary.executiveSummary}"
            startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, text)
            }, "Compartilhar resumo"))
        }
        binding.listenButton.setOnClickListener { playAt(0) }
        binding.mindMapButton.setOnClickListener { startActivity(MindMapActivity.intent(this, summary.id)) }
        binding.flashcardsButton.setOnClickListener { startActivity(FlashcardsActivity.intent(this, summary.id)) }
        binding.quizButton.setOnClickListener { startActivity(QuizActivity.intent(this, summary.id)) }

        renderFavorite()
        renderLearnings()
        renderSections()
        renderConcepts()
        renderExamples()
    }

    private fun renderFavorite() {
        binding.favorite.setColorFilter(
            ContextCompat.getColor(this, if (summary.isFavorite) R.color.rose else R.color.text_secondary)
        )
    }

    private fun playAt(paragraph: Int) {
        SpeechService.start(this, summary.id, paragraph, true)
        startActivity(PlayerActivity.intent(this, summary.id))
    }

    private fun renderLearnings() {
        binding.learningsContainer.removeAllViews()
        summary.keyLearnings.forEachIndexed { index, learning ->
            val card = MaterialCardView(this).apply {
                setCardBackgroundColor(ContextCompat.getColor(this@SummaryDetailActivity, R.color.card))
                strokeColor = ContextCompat.getColor(this@SummaryDetailActivity, R.color.border)
                strokeWidth = Ui.dp(this@SummaryDetailActivity, 1f)
                radius = Ui.dp(this@SummaryDetailActivity, 16f).toFloat()
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = Ui.dp(this@SummaryDetailActivity, 7f) }
            }
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.TOP
                setPadding(Ui.dp(this@SummaryDetailActivity, 13f), Ui.dp(this@SummaryDetailActivity, 12f), Ui.dp(this@SummaryDetailActivity, 13f), Ui.dp(this@SummaryDetailActivity, 12f))
            }
            val number = TextView(this).apply {
                text = "${index + 1}"
                setTextAppearance(R.style.TextAppearance_Audio_BodyStrong)
                setTextColor(ContextCompat.getColor(this@SummaryDetailActivity, R.color.brand))
                background = ContextCompat.getDrawable(this@SummaryDetailActivity, R.drawable.bg_circle_soft)
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(Ui.dp(this@SummaryDetailActivity, 28f), Ui.dp(this@SummaryDetailActivity, 28f))
            }
            val texts = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginStart = Ui.dp(this@SummaryDetailActivity, 10f)
                }
            }
            texts.addView(TextView(this).apply {
                text = learning.title
                setTextAppearance(R.style.TextAppearance_Audio_BodyStrong)
                typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            })
            texts.addView(TextView(this).apply {
                text = learning.description
                setTextAppearance(R.style.TextAppearance_Audio_Caption)
                setPadding(0, Ui.dp(this@SummaryDetailActivity, 2f), 0, 0)
            })
            row.addView(number)
            row.addView(texts)
            card.addView(row)
            binding.learningsContainer.addView(card)
        }
    }

    private fun renderSections() {
        binding.sectionsContainer.removeAllViews()
        val paragraphs = SpeechContent.build(summary)
        summary.sections.forEachIndexed { index, section ->
            val card = MaterialCardView(this).apply {
                setCardBackgroundColor(ContextCompat.getColor(this@SummaryDetailActivity, R.color.card))
                strokeColor = ContextCompat.getColor(this@SummaryDetailActivity, R.color.border)
                strokeWidth = Ui.dp(this@SummaryDetailActivity, 1f)
                radius = Ui.dp(this@SummaryDetailActivity, 18f).toFloat()
                isClickable = true
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = Ui.dp(this@SummaryDetailActivity, 8f) }
            }
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(Ui.dp(this@SummaryDetailActivity, 13f), Ui.dp(this@SummaryDetailActivity, 12f), Ui.dp(this@SummaryDetailActivity, 10f), Ui.dp(this@SummaryDetailActivity, 12f))
            }
            val indexView = TextView(this).apply {
                text = "${index + 1}"
                setTextAppearance(R.style.TextAppearance_Audio_BodyStrong)
                setTextColor(ContextCompat.getColor(this@SummaryDetailActivity, R.color.brand))
                gravity = Gravity.CENTER
                background = ContextCompat.getDrawable(this@SummaryDetailActivity, R.drawable.bg_circle_soft)
                layoutParams = LinearLayout.LayoutParams(Ui.dp(this@SummaryDetailActivity, 34f), Ui.dp(this@SummaryDetailActivity, 34f))
            }
            val texts = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginStart = Ui.dp(this@SummaryDetailActivity, 11f)
                }
            }
            texts.addView(TextView(this).apply {
                text = section.title
                setTextAppearance(R.style.TextAppearance_Audio_BodyStrong)
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
            })
            texts.addView(TextView(this).apply {
                text = section.content
                setTextAppearance(R.style.TextAppearance_Audio_Caption)
                maxLines = 2
                ellipsize = android.text.TextUtils.TruncateAt.END
                setPadding(0, Ui.dp(this@SummaryDetailActivity, 3f), 0, 0)
            })
            val play = ImageView(this).apply {
                setImageResource(R.drawable.ic_play_arrow)
                setColorFilter(ContextCompat.getColor(this@SummaryDetailActivity, R.color.brand))
                layoutParams = LinearLayout.LayoutParams(Ui.dp(this@SummaryDetailActivity, 32f), Ui.dp(this@SummaryDetailActivity, 32f))
                setPadding(Ui.dp(this@SummaryDetailActivity, 7f), Ui.dp(this@SummaryDetailActivity, 7f), Ui.dp(this@SummaryDetailActivity, 7f), Ui.dp(this@SummaryDetailActivity, 7f))
            }
            row.addView(indexView)
            row.addView(texts)
            row.addView(play)
            card.addView(row)
            card.setOnClickListener {
                val paragraph = paragraphs.indexOfFirst { it.sectionIndex == index }.coerceAtLeast(0)
                playAt(paragraph)
            }
            binding.sectionsContainer.addView(card)
        }
    }

    private fun renderConcepts() {
        binding.conceptsContainer.removeAllViews()
        summary.keyConcepts.forEach { concept ->
            val card = MaterialCardView(this).apply {
                setCardBackgroundColor(ContextCompat.getColor(this@SummaryDetailActivity, R.color.card))
                strokeColor = ContextCompat.getColor(this@SummaryDetailActivity, R.color.border)
                strokeWidth = Ui.dp(this@SummaryDetailActivity, 1f)
                radius = Ui.dp(this@SummaryDetailActivity, 16f).toFloat()
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = Ui.dp(this@SummaryDetailActivity, 7f) }
            }
            val col = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(Ui.dp(this@SummaryDetailActivity, 14f), Ui.dp(this@SummaryDetailActivity, 12f), Ui.dp(this@SummaryDetailActivity, 14f), Ui.dp(this@SummaryDetailActivity, 12f))
            }
            col.addView(TextView(this).apply {
                text = concept.term
                setTextAppearance(R.style.TextAppearance_Audio_BodyStrong)
                setTextColor(ContextCompat.getColor(this@SummaryDetailActivity, R.color.brand))
            })
            col.addView(TextView(this).apply {
                text = concept.simpleExplanation
                setTextAppearance(R.style.TextAppearance_Audio_Caption)
                setPadding(0, Ui.dp(this@SummaryDetailActivity, 3f), 0, 0)
            })
            concept.analogy?.let {
                col.addView(TextView(this).apply {
                    text = "Analogia: $it"
                    setTextAppearance(R.style.TextAppearance_Audio_Caption)
                    setTextColor(ContextCompat.getColor(this@SummaryDetailActivity, R.color.text_tertiary))
                    setPadding(0, Ui.dp(this@SummaryDetailActivity, 3f), 0, 0)
                })
            }
            card.addView(col)
            binding.conceptsContainer.addView(card)
        }
    }

    private fun renderExamples() {
        binding.examplesContainer.removeAllViews()
        summary.practicalExamples.forEach { example ->
            val card = MaterialCardView(this).apply {
                setCardBackgroundColor(ContextCompat.getColor(this@SummaryDetailActivity, R.color.surface))
                strokeWidth = 0
                radius = Ui.dp(this@SummaryDetailActivity, 16f).toFloat()
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = Ui.dp(this@SummaryDetailActivity, 7f) }
            }
            val col = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(Ui.dp(this@SummaryDetailActivity, 14f), Ui.dp(this@SummaryDetailActivity, 12f), Ui.dp(this@SummaryDetailActivity, 14f), Ui.dp(this@SummaryDetailActivity, 12f))
            }
            col.addView(TextView(this).apply {
                text = example.scenario
                setTextAppearance(R.style.TextAppearance_Audio_BodyStrong)
            })
            col.addView(TextView(this).apply {
                text = example.actionableAdvice
                setTextAppearance(R.style.TextAppearance_Audio_Caption)
                setPadding(0, Ui.dp(this@SummaryDetailActivity, 3f), 0, 0)
            })
            card.addView(col)
            binding.examplesContainer.addView(card)
        }
    }

    companion object {
        private const val EXTRA_ID = "summary_id"
        fun intent(context: android.content.Context, id: String) =
            Intent(context, SummaryDetailActivity::class.java).putExtra(EXTRA_ID, id)
    }
}
