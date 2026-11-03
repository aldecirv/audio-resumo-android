package com.audioresumo.app.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.lifecycle.lifecycleScope
import com.audioresumo.app.data.BookStore
import com.audioresumo.app.data.Store
import com.audioresumo.app.databinding.ActivityBookQaBinding
import com.audioresumo.app.util.BookProcessor
import com.audioresumo.app.util.ThemeUtils
import kotlinx.coroutines.launch

class BookQaActivity : AppCompatActivity() {

    private lateinit var binding: ActivityBookQaBinding
    private lateinit var bookId: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ThemeUtils.darkSystemBars(this)
        binding = ActivityBookQaBinding.inflate(layoutInflater)
        setContentView(binding.root)
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.updatePadding(top = bars.top, bottom = bars.bottom)
            insets
        }
        bookId = intent.getStringExtra(EXTRA_ID) ?: run { finish(); return }
        val book = BookStore.byId(bookId) ?: run { finish(); return }
        binding.bookTitle.text = book.title
        binding.back.setOnClickListener { finish() }
        binding.btnAsk.setOnClickListener { ask() }
    }

    private fun ask() {
        val question = binding.questionInput.text?.toString()?.trim().orEmpty()
        if (question.length < 3) {
            binding.questionInput.error = "Escreva uma pergunta"
            return
        }
        binding.questionInput.error = null
        binding.progress.isVisible = true
        binding.btnAsk.isEnabled = false
        binding.errorText.isVisible = false
        binding.answerCard.isVisible = false
        lifecycleScope.launch {
            val result = BookProcessor.ask(this@BookQaActivity, bookId, question)
            binding.progress.isVisible = false
            binding.btnAsk.isEnabled = true
            result.onSuccess { answer ->
                binding.answerCard.isVisible = true
                binding.answerSource.text = if (answer.usedAi) "RESPOSTA · GEMINI" else "RESPOSTA · MODO LOCAL (EXTRATIVA)"
                binding.answer.text = answer.answer
                binding.sources.text = if (answer.sources.isEmpty()) {
                    "Nenhum capítulo citado."
                } else {
                    "Fontes: " + answer.sources.joinToString(" · ")
                }
            }.onFailure {
                binding.errorText.isVisible = true
                binding.errorText.text = it.message ?: "Falha ao consultar o livro"
                if (Store.settings.apiKey.isBlank()) {
                    binding.errorText.append("\nDica: configure a chave Gemini em Perfil para respostas interpretativas.")
                }
            }
        }
    }

    companion object {
        private const val EXTRA_ID = "book_id"
        fun intent(context: android.content.Context, bookId: String) =
            Intent(context, BookQaActivity::class.java).putExtra(EXTRA_ID, bookId)
    }
}
