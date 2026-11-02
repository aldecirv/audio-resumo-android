package com.audioresumo.app.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.audioresumo.app.data.Flashcard
import com.audioresumo.app.data.Store
import com.audioresumo.app.databinding.ActivityFlashcardsBinding
import com.audioresumo.app.util.ThemeUtils

class FlashcardsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityFlashcardsBinding
    private var cards: List<Flashcard> = emptyList()
    private var index = 0
    private var flipped = false
    private var correct = 0
    private var wrong = 0
    private var animating = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityFlashcardsBinding.inflate(layoutInflater)
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
        cards = summary.flashcards
        if (cards.isEmpty()) {
            finish()
            return
        }
        binding.title.text = summary.title
        binding.card.setOnClickListener { flip() }
        binding.wrong.setOnClickListener { answer(false) }
        binding.right.setOnClickListener { answer(true) }
        binding.restart.setOnClickListener {
            index = 0
            correct = 0
            wrong = 0
            binding.resultContainer.visibility = View.GONE
            binding.studyContainer.visibility = View.VISIBLE
            render()
        }
        binding.close.setOnClickListener { finish() }
        render()
    }

    private fun render() {
        val card = cards.getOrNull(index) ?: return
        flipped = false
        binding.card.rotationY = 0f
        binding.progressLabel.text = "${index + 1} / ${cards.size}"
        binding.sideLabel.text = "PERGUNTA"
        binding.cardText.text = card.front
        binding.hint.text = card.hint?.takeIf { it.isNotBlank() } ?: "Toque no cartão para virar"
        binding.counter.text = "Acertos: $correct · Erros: $wrong"
        binding.wrong.isEnabled = false
        binding.right.isEnabled = false
        binding.wrong.alpha = 0.45f
        binding.right.alpha = 0.45f
    }

    private fun flip() {
        if (animating) return
        animating = true
        binding.card.animate().rotationY(90f).setDuration(140).withEndAction {
            flipped = !flipped
            val card = cards.getOrNull(index) ?: return@withEndAction
            if (flipped) {
                binding.sideLabel.text = "RESPOSTA"
                binding.cardText.text = card.back
                binding.hint.text = "Avalie sua lembrança para continuar"
                binding.wrong.isEnabled = true
                binding.right.isEnabled = true
                binding.wrong.alpha = 1f
                binding.right.alpha = 1f
            } else {
                binding.sideLabel.text = "PERGUNTA"
                binding.cardText.text = card.front
                binding.wrong.isEnabled = false
                binding.right.isEnabled = false
                binding.wrong.alpha = 0.45f
                binding.right.alpha = 0.45f
            }
            binding.card.rotationY = -90f
            binding.card.animate().rotationY(0f).setDuration(160).withEndAction { animating = false }.start()
        }.start()
    }

    private fun answer(known: Boolean) {
        if (!flipped || animating) return
        if (known) correct++ else wrong++
        if (index + 1 < cards.size) {
            index++
            render()
        } else {
            showResult()
        }
    }

    private fun showResult() {
        binding.studyContainer.visibility = View.GONE
        binding.resultContainer.visibility = View.VISIBLE
        binding.resultTitle.text = "Sessão concluída"
        binding.resultText.text = "Você acertou $correct de ${cards.size} flashcards. Revise os erros e tente novamente."
    }

    companion object {
        private const val EXTRA_ID = "summary_id"
        fun intent(context: android.content.Context, id: String) =
            Intent(context, FlashcardsActivity::class.java).putExtra(EXTRA_ID, id)
    }
}
