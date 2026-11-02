package com.audioresumo.app.ui

import android.content.Intent
import android.os.Bundle
import android.widget.SeekBar
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.audioresumo.app.R
import com.audioresumo.app.data.Bookmark
import com.audioresumo.app.data.Store
import com.audioresumo.app.databinding.ActivityPlayerBinding
import com.audioresumo.app.service.SpeechContent
import com.audioresumo.app.service.SpeechController
import com.audioresumo.app.service.SpeechParagraph
import com.audioresumo.app.service.SpeechService
import com.audioresumo.app.service.SpeechState
import com.audioresumo.app.ui.adapters.BookmarkAdapter
import com.audioresumo.app.ui.adapters.TranscriptAdapter
import com.audioresumo.app.util.ThemeUtils
import com.audioresumo.app.util.Ui
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch
import java.util.UUID

class PlayerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPlayerBinding
    private var summaryId: String = ""
    private var paragraphs: List<SpeechParagraph> = emptyList()
    private var transcriptAdapter: TranscriptAdapter? = null
    private var bookmarkAdapter: BookmarkAdapter? = null
    private var currentState: SpeechState = SpeechState()
    private var showingBookmarks = false
    private var userSeeking = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPlayerBinding.inflate(layoutInflater)
        setContentView(binding.root)
        ThemeUtils.darkSystemBars(this)
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.updatePadding(top = bars.top, bottom = bars.bottom)
            insets
        }

        summaryId = intent.getStringExtra(EXTRA_ID) ?: SpeechController.state.value.summaryId.orEmpty()
        val summary = Store.byId(summaryId)
        if (summary == null) {
            finish()
            return
        }
        paragraphs = SpeechContent.build(summary)
        binding.cover.bind(summary)
        binding.title.text = summary.title
        binding.author.text = summary.author
        binding.timeTotal.text = Ui.clock(summary.estimatedMinutes * 60)
        binding.seek.max = (paragraphs.size - 1).coerceAtLeast(1)
        binding.close.setOnClickListener { finish() }

        transcriptAdapter = TranscriptAdapter(paragraphs, 0) { index -> seekTo(index) }
        binding.transcriptList.adapter = transcriptAdapter

        binding.play.setOnClickListener { togglePlay() }
        binding.prev.setOnClickListener { skip(-1) }
        binding.next.setOnClickListener { skip(1) }
        binding.back15.setOnClickListener { skip(-1) }
        binding.forward15.setOnClickListener { skip(1) }
        binding.speed.setOnClickListener { chooseSpeed() }
        binding.sleep.setOnClickListener { chooseSleep() }
        binding.bookmark.setOnClickListener { toggleBookmark() }
        binding.tabTranscript.setOnClickListener { showTab(false) }
        binding.tabBookmarks.setOnClickListener { showTab(true) }

        binding.seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    userSeeking = true
                    binding.timeCurrent.text = Ui.clock(paragraphs.getOrNull(progress)?.startSeconds ?: 0)
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                userSeeking = false
                seekTo(seekBar?.progress ?: 0)
            }
        })

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                SpeechController.state.collect { render(it) }
            }
        }
        renderBookmarks()
        showTab(false)
    }

    private fun togglePlay() {
        val id = summaryId
        if (currentState.summaryId == id && currentState.isPlaying) {
            SpeechService.send(this, SpeechService.ACTION_PAUSE)
        } else {
            val start = if (currentState.summaryId == id) currentState.paragraphIndex else 0
            SpeechService.start(this, id, start, true)
        }
    }

    private fun skip(delta: Int) {
        val id = summaryId
        if (currentState.summaryId == id) {
            SpeechService.send(this, SpeechService.ACTION_SKIP) {
                putExtra(SpeechService.EXTRA_DELTA, delta)
            }
        } else {
            val target = (0 + delta).coerceAtLeast(0)
            SpeechService.start(this, id, target, true)
        }
    }

    private fun seekTo(index: Int) {
        val safe = index.coerceIn(0, (paragraphs.size - 1).coerceAtLeast(0))
        if (currentState.summaryId == summaryId) {
            SpeechService.send(this, SpeechService.ACTION_SEEK) {
                putExtra(SpeechService.EXTRA_PARAGRAPH, safe)
            }
        } else {
            SpeechService.start(this, summaryId, safe, true)
        }
        transcriptAdapter?.setCurrent(safe)
        binding.seek.progress = safe
        binding.timeCurrent.text = Ui.clock(paragraphs.getOrNull(safe)?.startSeconds ?: 0)
    }

    private fun chooseSpeed() {
        val options = arrayOf("0.75x", "1.0x", "1.25x", "1.5x", "1.75x", "2.0x")
        val values = floatArrayOf(0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f)
        val current = values.indexOfFirst { kotlin.math.abs(it - Store.settings.speechRate) < 0.01f }.coerceAtLeast(0)
        MaterialAlertDialogBuilder(this)
            .setTitle("Velocidade da narração")
            .setSingleChoiceItems(options, current) { dialog, which ->
                Store.updateSettings { it.speechRate = values[which] }
                binding.speed.text = options[which]
                if (SpeechController.state.value.summaryId != null) {
                    SpeechService.send(this, SpeechService.ACTION_SPEED) {
                        putExtra(SpeechService.EXTRA_SPEED, values[which])
                    }
                }
                dialog.dismiss()
            }
            .show()
    }

    private fun chooseSleep() {
        val options = arrayOf("Desligar soneca", "5 minutos", "10 minutos", "15 minutos", "30 minutos")
        val values = intArrayOf(0, 5, 10, 15, 30)
        MaterialAlertDialogBuilder(this)
            .setTitle("Soneca")
            .setItems(options) { _, which ->
                SpeechService.send(this, SpeechService.ACTION_SLEEP) {
                    putExtra(SpeechService.EXTRA_MINUTES, values[which])
                }
                binding.sleep.text = if (values[which] == 0) "Soneca" else "${values[which]} min"
            }
            .show()
    }

    private fun toggleBookmark() {
        val summary = Store.byId(summaryId) ?: return
        val index = currentState.paragraphIndex.coerceIn(0, (paragraphs.size - 1).coerceAtLeast(0))
        val existing = Store.user.bookmarkFor(summaryId, index)
        if (existing != null) {
            Store.removeBookmark(existing.id)
            renderBookmarks()
            updateBookmarkIcon()
            return
        }
        val input = android.widget.EditText(this).apply {
            hint = "Nota opcional"
            setPadding(Ui.dp(this@PlayerActivity, 16f), Ui.dp(this@PlayerActivity, 10f), Ui.dp(this@PlayerActivity, 16f), Ui.dp(this@PlayerActivity, 10f))
        }
        MaterialAlertDialogBuilder(this)
            .setTitle("Salvar marcador")
            .setMessage(paragraphs.getOrNull(index)?.sectionTitle ?: summary.title)
            .setView(input)
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Salvar") { _, _ ->
                Store.addBookmark(
                    Bookmark(
                        id = UUID.randomUUID().toString(),
                        summaryId = summaryId,
                        paragraphIndex = index,
                        sectionTitle = paragraphs.getOrNull(index)?.sectionTitle ?: summary.title,
                        note = input.text?.toString().orEmpty()
                    )
                )
                renderBookmarks()
                updateBookmarkIcon()
            }
            .show()
    }

    private fun render(state: SpeechState) {
        currentState = state
        val same = state.summaryId == summaryId
        binding.play.setImageResource(
            if (same && state.isPlaying) R.drawable.ic_pause else R.drawable.ic_play_arrow
        )
        binding.speed.text = String.format(java.util.Locale.US, "%.2fx", state.speed.takeIf { same } ?: Store.settings.speechRate)
            .replace("1.00x", "1.0x").replace("2.00x", "2.0x")
        binding.sleep.text = state.sleepMinutes?.let { "$it min" } ?: "Soneca"
        if (!same) return
        val index = state.paragraphIndex.coerceIn(0, (paragraphs.size - 1).coerceAtLeast(0))
        if (!userSeeking) binding.seek.progress = index
        binding.timeCurrent.text = Ui.clock(state.currentTime)
        binding.timeTotal.text = Ui.clock(state.duration)
        transcriptAdapter?.setCurrent(index)
        updateBookmarkIcon()
    }

    private fun updateBookmarkIcon() {
        val index = currentState.paragraphIndex
        val exists = Store.user.bookmarkFor(summaryId, index) != null
        binding.bookmark.setColorFilter(
            ContextCompat.getColor(this, if (exists) R.color.amber else R.color.text_secondary)
        )
    }

    private fun renderBookmarks() {
        val bookmarks = Store.user.bookmarks.filter { it.summaryId == summaryId }
        bookmarkAdapter = BookmarkAdapter(bookmarks) { bookmark ->
            Store.removeBookmark(bookmark.id)
            renderBookmarks()
            updateBookmarkIcon()
        }
        binding.bookmarkList.adapter = bookmarkAdapter
        binding.emptyBookmarks.visibility = if (bookmarks.isEmpty()) android.view.View.VISIBLE else android.view.View.GONE
    }

    private fun showTab(bookmarks: Boolean) {
        showingBookmarks = bookmarks
        binding.transcriptList.visibility = if (bookmarks) android.view.View.GONE else android.view.View.VISIBLE
        binding.bookmarkList.visibility = if (bookmarks) android.view.View.VISIBLE else android.view.View.GONE
        binding.emptyBookmarks.visibility = if (bookmarks && Store.user.bookmarks.none { it.summaryId == summaryId })
            android.view.View.VISIBLE else android.view.View.GONE
        binding.tabTranscript.setTextColor(ContextCompat.getColor(this, if (bookmarks) R.color.text_tertiary else R.color.brand))
        binding.tabBookmarks.setTextColor(ContextCompat.getColor(this, if (bookmarks) R.color.brand else R.color.text_tertiary))
        binding.tabIndicator.post {
            binding.tabIndicator.translationX = if (bookmarks) binding.tabs.width / 2f else 0f
        }
    }

    companion object {
        private const val EXTRA_ID = "summary_id"
        fun intent(context: android.content.Context, summaryId: String) =
            Intent(context, PlayerActivity::class.java).putExtra(EXTRA_ID, summaryId)
    }
}
