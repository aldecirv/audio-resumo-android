package com.audioresumo.app.ui.fragments

import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.audioresumo.app.R
import com.audioresumo.app.ui.MainActivity
import com.audioresumo.app.data.AudioSummary
import com.audioresumo.app.data.Store
import com.audioresumo.app.databinding.FragmentCreateBinding
import com.audioresumo.app.service.SpeechService
import com.audioresumo.app.ui.PlayerActivity
import com.audioresumo.app.ui.SummaryDetailActivity
import com.audioresumo.app.util.GeminiClient
import com.audioresumo.app.util.ImportFlow
import com.audioresumo.app.util.LocalSummarizer
import com.google.android.material.chip.Chip
import kotlinx.coroutines.launch

class CreateFragment : Fragment() {

    private var _binding: FragmentCreateBinding? = null
    private val binding get() = _binding!!
    private var format = "essential"
    private var minutes = 5
    private var pendingDocument: ImportFlow.Document? = null
    private var result: AudioSummary? = null
    private var busy = false

    private val openDocument = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) importFile(uri)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentCreateBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        buildFormatChips()
        binding.importFile.setOnClickListener {
            openDocument.launch(arrayOf("application/pdf", "application/epub+zip", "text/plain"))
        }
        binding.exampleButton.setOnClickListener { fillExample() }
        binding.generateButton.setOnClickListener { generate() }
        binding.openProfile.setOnClickListener { (activity as? MainActivity)?.openTab(R.id.navProfile) }
        binding.keyHint.visibility = if (Store.settings.apiKey.isBlank()) View.VISIBLE else View.GONE
        binding.listenResult.setOnClickListener {
            val summary = result ?: return@setOnClickListener
            SpeechService.start(requireContext(), summary.id, 0, true)
            startActivity(PlayerActivity.intent(requireContext(), summary.id))
        }
        binding.openResult.setOnClickListener {
            result?.let { startActivity(SummaryDetailActivity.intent(requireContext(), it.id)) }
        }
    }

    private fun buildFormatChips() {
        val formats = listOf(
            "essential" to "5 min",
            "complete" to "10 min",
            "deep" to "15 min",
            "deep" to "20 min"
        )
        binding.formatGroup.removeAllViews()
        formats.forEachIndexed { index, (value, label) ->
            val chip = Chip(requireContext()).apply {
                text = label
                tag = listOf(5, 10, 15, 20)[index]
                isCheckable = true
                isChecked = value == format
                textSize = 11f
                chipMinHeight = 36f * resources.displayMetrics.density
                chipBackgroundColor = ContextCompat.getColorStateList(requireContext(), R.color.card)
                setTextColor(ContextCompat.getColorStateList(requireContext(), R.color.text_secondary))
                chipStrokeWidth = 1f * resources.displayMetrics.density
                chipStrokeColor = ContextCompat.getColorStateList(requireContext(), R.color.border)
                isCheckedIconVisible = false
                setOnClickListener {
                    format = value
                    minutes = (tag as Int)
                    for (i in 0 until binding.formatGroup.childCount) {
                        val c = binding.formatGroup.getChildAt(i) as Chip
                        c.isChecked = c === this
                    }
                    binding.statusText.text = "Duração $label · sem bloqueios"
                }
            }
            binding.formatGroup.addView(chip)
        }
    }

    private fun fillExample() {
        binding.contentInput.setText(
            """
            Aprendizado acelerado: como estudar menos e aprender mais

            A ciência da aprendizagem mostra que reler e grifar são estratégias fracas. O cérebro fixa melhor quando é forçado a recuperar a informação: fazer perguntas, explicar em voz alta e testar a memória. Esse esforço é chamado de prática de recuperação.

            Outro princípio é o espaçamento. Revisar o mesmo conteúdo em intervalos crescentes — um dia, três dias, uma semana, duas semanas — combate o esquecimento e cria memória de longo prazo. Sessões curtas e frequentes superam maratonas de estudo.

            A intercalação também ajuda: alternar tipos de problema ou temas relacionados força o cérebro a escolher a estratégia certa, em vez de repetir mecanicamente. Já o feedback imediato corrige erros antes que se consolidem.

            Na prática, estude em blocos de 25 minutos, faça uma pergunta ao final de cada bloco e tente respondê-la sem consultar. Depois, revise o material no dia seguinte e na semana seguinte. Durma bem: o sono é parte do processo de consolidação.

            Aprender não é acumular informação, é construir caminhos de acesso a ela. Quanto mais você recupera e aplica, mais forte fica a memória.
            """.trimIndent()
        )
        pendingDocument = null
    }

    private fun importFile(uri: Uri) {
        val dialog = android.app.AlertDialog.Builder(requireContext())
            .setTitle("Lendo arquivo...")
            .setMessage("Extraindo o texto para o resumo.")
            .setCancelable(false)
            .create()
        dialog.show()
        viewLifecycleOwner.lifecycleScope.launch {
            val read = ImportFlow.read(requireContext(), uri)
            dialog.dismiss()
            read.onSuccess { doc ->
                pendingDocument = doc
                binding.contentInput.setText(doc.text)
                val extra = if (doc.text.isBlank()) "PDF sem texto extraível; a IA tentará ler o arquivo." else "Texto extraído: ${doc.text.length} caracteres."
                binding.statusText.text = "${doc.kind} · $extra"
                Toast.makeText(requireContext(), "${doc.kind} carregado: ${doc.title}", Toast.LENGTH_SHORT).show()
            }.onFailure {
                Toast.makeText(requireContext(), it.message ?: "Não foi possível ler o arquivo", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun generate() {
        if (busy) return
        val text = binding.contentInput.text?.toString()?.trim().orEmpty()
        val document = pendingDocument
        if (text.length < 80 && (document?.bytes == null)) {
            Toast.makeText(requireContext(), "Cole um texto ou importe um EPUB/PDF com pelo menos 80 caracteres.", Toast.LENGTH_LONG).show()
            return
        }
        busy = true
        binding.progress.visibility = View.VISIBLE
        binding.generateButton.isEnabled = false
        binding.statusText.text = "Gerando resumo..."
        viewLifecycleOwner.lifecycleScope.launch {
            val useAi = Store.settings.apiKey.isNotBlank()
            var summary: AudioSummary? = null
            var usedLocal = false
            if (useAi) {
                val ai = GeminiClient.generate(
                    text = text,
                    fileBytes = document?.bytes,
                    mimeType = if (document?.bytes != null) document.mime else null,
                    format = format,
                    sourceType = document?.kind?.lowercase() ?: "text",
                    titleHint = document?.title.orEmpty(),
                    minutesOverride = minutes
                )
                if (ai.isSuccess) summary = ai.getOrNull()
            }
            if (summary == null && (text.length >= 120 || document != null)) {
                val local = LocalSummarizer.summarize(
                    text = text.ifBlank { document?.text.orEmpty() },
                    title = document?.title.orEmpty(),
                    author = document?.author.orEmpty(),
                    format = format,
                    sourceType = document?.kind?.lowercase() ?: "text",
                    minutesOverride = minutes
                )
                summary = local.getOrNull()
                usedLocal = true
            }
            busy = false
            binding.progress.visibility = View.GONE
            binding.generateButton.isEnabled = true
            if (summary == null) {
                binding.statusText.text = "Não foi possível gerar. Verifique o texto ou a chave Gemini."
                Toast.makeText(requireContext(), "Falha ao gerar o resumo", Toast.LENGTH_LONG).show()
                return@launch
            }
            Store.addSummary(summary)
            result = summary
            renderResult(summary)
            binding.statusText.text = if (usedLocal || !useAi) {
                "Resumo local criado e salvo na biblioteca."
            } else {
                "Resumo gerado com Gemini e salvo na biblioteca."
            }
        }
    }

    private fun renderResult(summary: AudioSummary) {
        binding.resultContainer.visibility = View.VISIBLE
        binding.resultCover.bind(summary)
        binding.resultTitle.text = summary.title
        binding.resultMeta.text = "${summary.formatLabel} · ${summary.durationLabel} · ${if (summary.sourceType == "text") "texto" else summary.sourceType}"
        binding.resultSummary.text = summary.executiveSummary
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
