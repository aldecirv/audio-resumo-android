package com.audioresumo.app.ui

import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.lifecycle.lifecycleScope
import com.audioresumo.app.databinding.ActivityBookImportBinding
import com.audioresumo.app.util.BookImporter
import com.audioresumo.app.util.ThemeUtils
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class BookImportActivity : AppCompatActivity() {

    private lateinit var binding: ActivityBookImportBinding
    private var job: Job? = null
    private var currentUri: Uri? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ThemeUtils.darkSystemBars(this)
        binding = ActivityBookImportBinding.inflate(layoutInflater)
        setContentView(binding.root)
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.updatePadding(top = bars.top, bottom = bars.bottom)
            insets
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val uri = intent.data ?: run { finish(); return }
        currentUri = uri
        binding.back.setOnClickListener { cancel() }
        binding.btnCancel.setOnClickListener {
            if (job?.isActive == true) cancel()
            else currentUri?.let { start(it) }
        }
        binding.progress.isIndeterminate = false
        start(uri)
    }

    private fun start(uri: Uri) {
        job?.cancel()
        binding.status.text = "Lendo livro..."
        binding.message.text = "Preparando importação."
        binding.progress.isVisible = true
        binding.progress.setProgressCompat(0, false)
        binding.percent.text = "0%"
        binding.btnCancel.text = "Cancelar"
        job = lifecycleScope.launch {
            val result = BookImporter.importUri(this@BookImportActivity, uri) { progress ->
                runOnUiThread {
                    binding.progress.setProgressCompat(progress.percent, true)
                    binding.percent.text = "${progress.percent}%"
                    binding.message.text = progress.message
                }
            }
            result.onSuccess { book ->
                binding.progress.setProgressCompat(100, true)
                binding.percent.text = "100%"
                binding.status.text = "Livro importado"
                startActivity(BookDetailActivity.intent(this@BookImportActivity, book.id))
                finish()
            }.onFailure { error ->
                binding.status.text = "Não foi possível importar"
                binding.message.text = error.message ?: "Arquivo inválido ou sem texto."
                binding.btnCancel.text = "Tentar novamente"
                binding.progress.isVisible = false
            }
        }
    }

    private fun cancel() {
        job?.cancel()
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        job?.cancel()
    }
}
