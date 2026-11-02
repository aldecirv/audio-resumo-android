package com.audioresumo.app.ui

import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.audioresumo.app.R
import com.audioresumo.app.data.Store
import com.audioresumo.app.databinding.ActivityReviewPlanBinding
import com.audioresumo.app.service.SpeechService
import com.audioresumo.app.util.ThemeUtils
import com.audioresumo.app.util.Ui
import com.google.android.material.card.MaterialCardView

class ReviewPlanActivity : AppCompatActivity() {

    private lateinit var binding: ActivityReviewPlanBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityReviewPlanBinding.inflate(layoutInflater)
        setContentView(binding.root)
        ThemeUtils.darkSystemBars(this)
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.updatePadding(top = bars.top, bottom = bars.bottom)
            insets
        }
        binding.back.setOnClickListener { finish() }
        val summary = intent.getStringExtra(EXTRA_ID)?.let(Store::byId)
        if (summary == null) {
            finish()
            return
        }
        binding.title.text = summary.title
        summary.reviewPlan.entries().forEach { (dayLabel, planText) ->
            val card = MaterialCardView(this).apply {
                setCardBackgroundColor(ContextCompat.getColor(this@ReviewPlanActivity, R.color.card))
                strokeColor = ContextCompat.getColor(this@ReviewPlanActivity, R.color.border)
                strokeWidth = Ui.dp(this@ReviewPlanActivity, 1f)
                radius = Ui.dp(this@ReviewPlanActivity, 18f).toFloat()
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = Ui.dp(this@ReviewPlanActivity, 8f) }
            }
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(Ui.dp(this@ReviewPlanActivity, 14f), Ui.dp(this@ReviewPlanActivity, 13f), Ui.dp(this@ReviewPlanActivity, 14f), Ui.dp(this@ReviewPlanActivity, 13f))
            }
            row.addView(TextView(this).apply {
                text = dayLabel
                setTextAppearance(R.style.TextAppearance_Audio_Label)
                setTextColor(ContextCompat.getColor(this@ReviewPlanActivity, R.color.brand))
                background = ContextCompat.getDrawable(this@ReviewPlanActivity, R.drawable.bg_pill)
                gravity = Gravity.CENTER
                setPadding(Ui.dp(this@ReviewPlanActivity, 10f), Ui.dp(this@ReviewPlanActivity, 6f), Ui.dp(this@ReviewPlanActivity, 10f), Ui.dp(this@ReviewPlanActivity, 6f))
            })
            row.addView(TextView(this).apply {
                text = planText
                setTextAppearance(R.style.TextAppearance_Audio_Body)
                setPadding(Ui.dp(this@ReviewPlanActivity, 12f), 0, 0, 0)
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            })
            card.addView(row)
            binding.planContainer.addView(card)
        }
        binding.listen.setOnClickListener {
            SpeechService.start(this, summary.id, 0, true)
            startActivity(PlayerActivity.intent(this, summary.id))
        }
    }

    companion object {
        private const val EXTRA_ID = "summary_id"
        fun intent(context: android.content.Context, id: String) =
            Intent(context, ReviewPlanActivity::class.java).putExtra(EXTRA_ID, id)
    }
}
