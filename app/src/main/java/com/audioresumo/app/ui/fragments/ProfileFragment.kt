package com.audioresumo.app.ui.fragments

import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.audioresumo.app.R
import com.audioresumo.app.data.Store
import com.audioresumo.app.databinding.FragmentProfileBinding
import com.audioresumo.app.service.SpeechController
import com.audioresumo.app.service.SpeechService
import com.audioresumo.app.util.Ui
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.util.Locale

class ProfileFragment : Fragment() {

    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.editProfile.setOnClickListener { editProfile() }
        binding.premiumCard.setOnClickListener { showPremium() }
        binding.rowApiKey.setOnClickListener { editApiKey() }
        binding.rowModel.setOnClickListener { chooseModel() }
        binding.rowSpeed.setOnClickListener { chooseSpeed() }
        binding.rowAdmin.setOnClickListener { showAdmin() }
        binding.rowClear.setOnClickListener { confirmClear() }
        refresh()
    }

    fun refresh() {
        if (_binding == null) return
        val user = Store.user
        binding.avatar.text = user.initial
        binding.userName.text = user.name
        binding.userEmail.text = user.email
        binding.planPill.text = if (user.isPremium) "Premium ativo" else "Plano gratuito"
        binding.planPill.setTextColor(
            ContextCompat.getColor(requireContext(), if (user.isPremium) R.color.amber else R.color.text_secondary)
        )
        binding.premiumCard.visibility = if (user.isPremium) View.GONE else View.VISIBLE
        binding.statStreak.text = user.streakDays.toString()
        binding.statMinutes.text = "${user.totalAudioMinutes}m"
        binding.statQuiz.text = user.completedQuizzes.toString()
        binding.apiKeyValue.text = if (Store.settings.apiKey.isBlank()) "Não configurada · modo local" else "Configurada · ${Store.settings.apiKey.take(6)}…"
        binding.modelValue.text = Store.settings.model
        binding.speedValue.text = String.format(Locale.US, "%.2fx", Store.settings.speechRate).replace("1.00x", "1.0x")
    }

    private fun editProfile() {
        val container = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(Ui.dp(requireContext(), 20f), Ui.dp(requireContext(), 8f), Ui.dp(requireContext(), 20f), 0)
        }
        val name = EditText(requireContext()).apply {
            hint = "Seu nome"
            setText(Store.user.name)
            inputType = InputType.TYPE_CLASS_TEXT
        }
        val email = EditText(requireContext()).apply {
            hint = "E-mail"
            setText(Store.user.email)
            inputType = InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
        }
        container.addView(name)
        container.addView(email)
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Editar perfil")
            .setView(container)
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Salvar") { _, _ ->
                Store.updateUser(name.text.toString(), email.text.toString())
                refresh()
            }
            .show()
    }

    private fun showPremium() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Áudio de Resumo Premium")
            .setMessage(
                "Desbloqueie resumos ilimitados, PDFs de até 50 MB, vozes neurais e mapas mentais avançados.\n\n" +
                    "Nesta versão nativa, a assinatura é demonstrativa e não faz cobrança real."
            )
            .setPositiveButton("Ativar Premium (demo)") { _, _ ->
                Store.upgradeToPremium()
                Toast.makeText(requireContext(), "Premium ativado neste aparelho", Toast.LENGTH_SHORT).show()
                refresh()
            }
            .setNegativeButton("Agora não", null)
            .show()
    }

    private fun editApiKey() {
        val input = EditText(requireContext()).apply {
            hint = "AIza..."
            setText(Store.settings.apiKey)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            setPadding(Ui.dp(requireContext(), 20f), Ui.dp(requireContext(), 10f), Ui.dp(requireContext(), 20f), Ui.dp(requireContext(), 10f))
        }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Chave Gemini")
            .setMessage("Opcional. Com a chave, os resumos usam IA generativa. Sem ela, o app funciona no modo local offline.")
            .setView(input)
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Salvar") { _, _ ->
                Store.updateSettings { it.apiKey = input.text?.toString()?.trim().orEmpty() }
                Toast.makeText(requireContext(), "Chave salva", Toast.LENGTH_SHORT).show()
                refresh()
            }
            .show()
    }

    private fun chooseModel() {
        val models = arrayOf("gemini-2.5-flash", "gemini-2.5-pro", "gemini-2.0-flash")
        val current = models.indexOf(Store.settings.model).coerceAtLeast(0)
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Modelo de IA")
            .setSingleChoiceItems(models, current) { dialog, which ->
                Store.updateSettings { it.model = models[which] }
                dialog.dismiss()
                refresh()
            }
            .show()
    }

    private fun chooseSpeed() {
        val labels = arrayOf("0.75x", "1.0x", "1.25x", "1.5x", "1.75x", "2.0x")
        val values = floatArrayOf(0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f)
        val current = values.indexOfFirst { kotlin.math.abs(it - Store.settings.speechRate) < 0.01f }.coerceAtLeast(0)
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Velocidade da narração")
            .setSingleChoiceItems(labels, current) { dialog, which ->
                Store.updateSettings { it.speechRate = values[which] }
                if (SpeechController.state.value.summaryId != null) {
                    SpeechService.send(requireContext(), SpeechService.ACTION_SPEED) {
                        putExtra(SpeechService.EXTRA_SPEED, values[which])
                    }
                }
                dialog.dismiss()
                refresh()
            }
            .show()
    }

    private fun showAdmin() {
        val text = """
            Métricas de demonstração (produção web)

            • Resumos gerados: 42
            • Áudio gerado: 18.400 s (5,1 h)
            • Tokens usados: 624.500
            • Custo estimado: US$ 0,84
            • Usuários ativos: 128
            • Plano gratuito: 94 · Premium: 34

            Limites: 3 resumos/mês (free), 50 (premium); PDF 5 MB (free), 50 MB (premium).
            Nesta versão nativa os limites são locais e ilimitados no modo offline.
        """.trimIndent()
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Painel administrativo")
            .setMessage(text)
            .setPositiveButton("Fechar", null)
            .show()
    }

    private fun confirmClear() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Limpar histórico e cache?")
            .setMessage("Os resumos da biblioteca permanecem; o progresso, quizzes e histórico de escuta serão apagados.")
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Limpar") { _, _ ->
                Store.clearHistoryAndCache()
                Toast.makeText(requireContext(), "Histórico limpo", Toast.LENGTH_SHORT).show()
                refresh()
            }
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
