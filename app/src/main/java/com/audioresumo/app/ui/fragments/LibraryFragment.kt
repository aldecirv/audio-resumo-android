package com.audioresumo.app.ui.fragments

import android.app.AlertDialog
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.audioresumo.app.R
import com.audioresumo.app.data.AudioSummary
import com.audioresumo.app.data.Store
import com.audioresumo.app.databinding.FragmentLibraryBinding
import com.audioresumo.app.ui.SummaryDetailActivity
import com.audioresumo.app.ui.adapters.SummaryRowAdapter
import com.audioresumo.app.util.ImportFlow
import com.google.android.material.chip.Chip
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch

class LibraryFragment : Fragment() {

    private var _binding: FragmentLibraryBinding? = null
    private val binding get() = _binding!!
    private var categoryFilter = "Todos"
    private var favoritesOnly = false
    private var query = ""
    private var buildingChips = false
    private var pendingAiChoice = false

    private val openDocument = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) importDocument(uri)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentLibraryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        categoryFilter = arguments?.getString(ARG_CATEGORY) ?: "Todos"
        binding.list.layoutManager = LinearLayoutManager(requireContext())
        binding.addDocument.setOnClickListener { showImportOptions() }
        binding.importCard.setOnClickListener { showImportOptions() }
        binding.favoriteFilter.setOnClickListener {
            favoritesOnly = !favoritesOnly
            binding.favoriteFilter.setColorFilter(
                ContextCompat.getColor(requireContext(), if (favoritesOnly) R.color.rose else R.color.text_tertiary)
            )
            refresh()
        }
        binding.searchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                query = s?.toString().orEmpty()
                refresh()
            }
        })
        binding.categoryGroup.setOnCheckedStateChangeListener { _, checkedIds ->
            if (buildingChips) return@setOnCheckedStateChangeListener
            val id = checkedIds.firstOrNull() ?: return@setOnCheckedStateChangeListener
            val chip = binding.categoryGroup.findViewById<Chip>(id)
            categoryFilter = chip?.text?.toString() ?: "Todos"
            refresh()
        }
        buildChips()
        refresh()
    }

    private fun buildChips() {
        buildingChips = true
        binding.categoryGroup.removeAllViews()
        val categories = listOf("Todos") + Store.allCategories()
        categories.forEachIndexed { index, category ->
            val chip = Chip(requireContext()).apply {
                id = View.generateViewId()
                text = category
                isCheckable = true
                isChecked = category == categoryFilter
                textSize = 11f
                chipMinHeight = 34f * resources.displayMetrics.density
                chipBackgroundColor = ContextCompat.getColorStateList(requireContext(), R.color.card)
                setTextColor(ContextCompat.getColorStateList(requireContext(), R.color.text_secondary))
                chipStrokeWidth = 1f * resources.displayMetrics.density
                chipStrokeColor = ContextCompat.getColorStateList(requireContext(), R.color.border)
                isCheckedIconVisible = false
            }
            binding.categoryGroup.addView(chip)
        }
        buildingChips = false
    }

    fun refresh() {
        if (_binding == null) return
        val filtered = Store.summaries.filter { summary ->
            val byCategory = categoryFilter == "Todos" || summary.category == categoryFilter
            val byFavorite = !favoritesOnly || summary.isFavorite
            val q = query.trim().lowercase()
            val byQuery = q.isBlank() || summary.title.lowercase().contains(q) ||
                summary.author.lowercase().contains(q) || summary.category.lowercase().contains(q) ||
                summary.executiveSummary.lowercase().contains(q)
            byCategory && byFavorite && byQuery
        }
        binding.subtitle.text = "${Store.summaries.size} resumos para ouvir e aplicar"
        binding.countLabel.text = if (filtered.size == 1) "1 resumo" else "${filtered.size} resumos"
        binding.emptyState.visibility = if (filtered.isEmpty()) View.VISIBLE else View.GONE
        binding.list.visibility = if (filtered.isEmpty()) View.GONE else View.VISIBLE
        binding.list.adapter = SummaryRowAdapter(filtered) { refresh() }
    }

    private fun showImportOptions() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Adicionar à biblioteca")
            .setMessage("Importe um arquivo local e transforme em resumo narrado. O texto fica salvo no aparelho.")
            .setItems(arrayOf("PDF local", "EPUB local", "Texto (.txt)")) { _, which ->
                val mime = when (which) {
                    0 -> arrayOf("application/pdf")
                    1 -> arrayOf("application/epub+zip")
                    else -> arrayOf("text/plain")
                }
                openDocument.launch(mime)
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun importDocument(uri: Uri) {
        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle("Lendo arquivo...")
            .setMessage("Extraindo o texto do documento.")
            .setCancelable(false)
            .create()
        dialog.show()
        viewLifecycleOwner.lifecycleScope.launch {
            val result = ImportFlow.read(requireContext(), uri)
            dialog.dismiss()
            result.onSuccess { document ->
                dialog.dismiss()
                chooseSummaryMode(document)
            }.onFailure { error ->
                Toast.makeText(requireContext(), error.message ?: "Não foi possível ler o arquivo", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun chooseSummaryMode(document: ImportFlow.Document) {
        val hasKey = Store.settings.apiKey.isNotBlank()
        val options = if (hasKey) arrayOf("Resumo com Gemini (IA)", "Resumo local rápido") else arrayOf("Resumo local rápido")
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(document.title)
            .setMessage("${document.kind} · ${document.text.length} caracteres extraídos\n\nComo deseja gerar o resumo?")
            .setItems(options) { _, which ->
                val useAi = hasKey && which == 0
                generate(document, useAi)
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun generate(document: ImportFlow.Document, useAi: Boolean) {
        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(if (useAi) "Gerando com Gemini..." else "Criando resumo local...")
            .setMessage("Isso pode levar alguns segundos.")
            .setCancelable(false)
            .create()
        dialog.show()
        viewLifecycleOwner.lifecycleScope.launch {
            val result = ImportFlow.summarize(requireContext(), document, useAi)
            dialog.dismiss()
            result.onSuccess { summary ->
                Store.addSummary(summary)
                Toast.makeText(requireContext(), "Resumo adicionado à biblioteca", Toast.LENGTH_SHORT).show()
                startActivity(SummaryDetailActivity.intent(requireContext(), summary.id))
                refresh()
            }.onFailure {
                Toast.makeText(requireContext(), it.message ?: "Falha ao gerar resumo", Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val ARG_CATEGORY = "category"
        fun newInstance(category: String? = null): LibraryFragment = LibraryFragment().apply {
            arguments = Bundle().apply { if (category != null) putString(ARG_CATEGORY, category) }
        }
    }
}
