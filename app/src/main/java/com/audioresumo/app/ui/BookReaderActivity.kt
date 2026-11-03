package com.audioresumo.app.ui

import android.content.Intent
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.view.View
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.audioresumo.app.data.Book
import com.audioresumo.app.data.BookChapter
import com.audioresumo.app.data.BookStore
import com.audioresumo.app.databinding.ActivityBookReaderBinding
import com.audioresumo.app.util.ThemeUtils
import java.util.Locale

class BookReaderActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    private lateinit var binding: ActivityBookReaderBinding
    private lateinit var bookId: String
    private var book: Book? = null
    private var chapter: BookChapter? = null
    private var chapterIndex = 0
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var segments: List<String> = emptyList()
    private var segmentIndex = 0
    private var playing = false
    private var paused = false
    private var speed = 1f
    private var pendingAutoPlay = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ThemeUtils.darkSystemBars(this)
        binding = ActivityBookReaderBinding.inflate(layoutInflater)
        setContentView(binding.root)
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.updatePadding(top = bars.top, bottom = bars.bottom)
            insets
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        bookId = intent.getStringExtra(EXTRA_BOOK_ID) ?: run { finish(); return }
        chapterIndex = intent.getIntExtra(EXTRA_CHAPTER, 0)
        book = BookStore.loadFull(bookId) ?: run { finish(); return }
        binding.back.setOnClickListener { finish() }
        binding.btnPlay.setOnClickListener { togglePlay() }
        binding.btnPrevChapter.setOnClickListener { switchChapter(-1) }
        binding.btnNextChapter.setOnClickListener { switchChapter(1) }
        binding.speed.setOnClickListener { cycleSpeed() }
        tts = TextToSpeech(this, this)
        loadChapter(chapterIndex, resumeScroll = true)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts?.setLanguage(Locale("pt", "BR"))
            ttsReady = result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED
            tts?.setSpeechRate(speed)
            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    runOnUiThread {
                        playing = true
                        binding.btnPlay.setImageResource(com.audioresumo.app.R.drawable.ic_pause)
                        binding.ttsStatus.text = "Narrando..."
                    }
                }

                override fun onDone(utteranceId: String?) {
                    runOnUiThread {
                        if (segmentIndex + 1 < segments.size) {
                            segmentIndex++
                            speakCurrent()
                        } else {
                            val current = book
                            if (current != null && chapterIndex < current.chapters.lastIndex) {
                                binding.ttsStatus.text = "Próximo capítulo..."
                                loadChapter(chapterIndex + 1, resumeScroll = false)
                                speakCurrent()
                            } else {
                                playing = false
                                paused = false
                                binding.btnPlay.setImageResource(com.audioresumo.app.R.drawable.ic_play_arrow)
                                binding.ttsStatus.text = "Livro concluído"
                                binding.ttsProgress.setProgressCompat(100, true)
                            }
                        }
                    }
                }

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    runOnUiThread {
                        playing = false
                        binding.btnPlay.setImageResource(com.audioresumo.app.R.drawable.ic_play_arrow)
                        binding.ttsStatus.text = "Não foi possível narrar este trecho"
                    }
                }
            })
            if (pendingAutoPlay) {
                pendingAutoPlay = false
                speakCurrent()
            }
        } else {
            binding.ttsStatus.text = "Síntese de voz indisponível neste aparelho"
        }
    }

    private fun loadChapter(index: Int, resumeScroll: Boolean) {
        val current = book ?: return
        if (index !in current.chapters.indices) return
        stopSpeaking()
        chapterIndex = index
        chapter = current.chapters[index]
        val text = chapter?.text.orEmpty()
        binding.chapterTitle.text = chapter?.title ?: "Capítulo ${index + 1}"
        binding.toolbarTitle.text = current.title
        binding.chapterPosition.text = "${index + 1}/${current.chapters.size}"
        binding.chapterText.text = text
        segments = splitSegments(text)
        segmentIndex = 0
        binding.ttsProgress.max = segments.size.coerceAtLeast(1)
        binding.ttsProgress.setProgressCompat(0, false)
        binding.ttsStatus.text = if (text.isBlank()) "Capítulo sem texto" else "Toque para ouvir este capítulo"
        binding.btnPrevChapter.alpha = if (index > 0) 1f else 0.3f
        binding.btnNextChapter.alpha = if (index < current.chapters.lastIndex) 1f else 0.3f
        binding.btnPlay.setImageResource(com.audioresumo.app.R.drawable.ic_play_arrow)
        if (resumeScroll && index == current.lastChapterIndex) {
            binding.textScroll.post { binding.textScroll.scrollTo(0, current.lastScroll) }
        } else {
            binding.textScroll.post { binding.textScroll.scrollTo(0, 0) }
        }
        BookStore.saveProgress(current.id, index, 0)
    }

    private fun splitSegments(text: String): List<String> {
        if (text.isBlank()) return emptyList()
        val paragraphs = text.split(Regex("\\n\\s*\\n"))
            .flatMap { paragraph ->
                if (paragraph.length <= 2800) listOf(paragraph)
                else paragraph.split(Regex("(?<=[.!?])\\s+")).chunked(12).map { it.joinToString(" ") }
            }
            .filter { it.isNotBlank() }
        return paragraphs
    }

    private fun togglePlay() {
        if (playing) {
            paused = true
            playing = false
            tts?.stop()
            binding.btnPlay.setImageResource(com.audioresumo.app.R.drawable.ic_play_arrow)
            binding.ttsStatus.text = "Pausado · toque para continuar"
            return
        }
        if (!ttsReady) {
            pendingAutoPlay = true
            binding.ttsStatus.text = "Preparando voz..."
            return
        }
        speakCurrent()
    }

    private fun speakCurrent() {
        if (segments.isEmpty()) return
        val text = segments.getOrNull(segmentIndex) ?: return
        tts?.setSpeechRate(speed)
        val result = tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "chapter-$segmentIndex")
        if (result == TextToSpeech.ERROR) {
            binding.ttsStatus.text = "Falha ao iniciar a narração"
            return
        }
        playing = true
        paused = false
        binding.btnPlay.setImageResource(com.audioresumo.app.R.drawable.ic_pause)
        binding.ttsStatus.text = "Narrando trecho ${segmentIndex + 1} de ${segments.size}"
        binding.ttsProgress.setProgressCompat(
            ((segmentIndex + 1) * 100 / segments.size.coerceAtLeast(1)),
            true
        )
        binding.chapterText.post {
            val line = binding.chapterText.layout?.getLineForOffset(
                binding.chapterText.text.indexOf(text).coerceAtLeast(0)
            ) ?: 0
            val y = binding.chapterText.layout?.getLineTop(line) ?: 0
            binding.textScroll.smoothScrollTo(0, (y - 100).coerceAtLeast(0))
        }
    }

    private fun stopSpeaking() {
        playing = false
        paused = false
        tts?.stop()
    }

    private fun switchChapter(delta: Int) {
        val current = book ?: return
        val target = chapterIndex + delta
        if (target !in current.chapters.indices) return
        BookStore.saveProgress(current.id, chapterIndex, binding.textScroll.scrollY)
        loadChapter(target, resumeScroll = false)
    }

    private fun cycleSpeed() {
        speed = when (speed) {
            1f -> 1.25f
            1.25f -> 1.5f
            1.5f -> 0.75f
            0.75f -> 2f
            else -> 1f
        }
        tts?.setSpeechRate(speed)
        binding.speed.text = String.format(Locale.US, "%.2fx", speed).replace("1.00x", "1.0x")
        if (playing) speakCurrent()
    }

    override fun onPause() {
        super.onPause()
        book?.let { BookStore.saveProgress(it.id, chapterIndex, binding.textScroll.scrollY) }
    }

    override fun onDestroy() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        super.onDestroy()
    }

    companion object {
        private const val EXTRA_BOOK_ID = "book_id"
        private const val EXTRA_CHAPTER = "chapter_index"
        fun intent(context: android.content.Context, bookId: String, chapterIndex: Int) =
            Intent(context, BookReaderActivity::class.java)
                .putExtra(EXTRA_BOOK_ID, bookId)
                .putExtra(EXTRA_CHAPTER, chapterIndex)
    }
}
