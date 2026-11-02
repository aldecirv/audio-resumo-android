package com.audioresumo.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.core.app.NotificationCompat
import com.audioresumo.app.R
import com.audioresumo.app.data.AudioSummary
import com.audioresumo.app.data.Store
import com.audioresumo.app.ui.PlayerActivity
import java.util.Locale

class SpeechService : Service(), TextToSpeech.OnInitListener {

    private lateinit var tts: TextToSpeech
    private var ttsReady = false
    private var summary: AudioSummary? = null
    private var paragraphs: List<SpeechParagraph> = emptyList()
    private var index = 0
    private var playing = false
    private var speed = 1f
    private var sleepMinutes: Int? = null
    private val handler = Handler(Looper.getMainLooper())
    private var sleepRunnable: Runnable? = null
    private var addedSeconds = 0

    override fun onCreate() {
        super.onCreate()
        createChannel()
        tts = TextToSpeech(this, this)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts.language = Locale("pt", "BR")
            tts.setOnUtteranceProgressListener(listener)
            ttsReady = true
            if (playing) speak(index)
        } else {
            SpeechController.update { it.copy(error = "Síntese de voz indisponível neste aparelho") }
        }
    }

    private val listener = object : UtteranceProgressListener() {
        override fun onStart(utteranceId: String?) {
            playing = true
            pushState()
            updateNotification()
        }

        override fun onDone(utteranceId: String?) {
            val finished = index
            val seconds = estimateSeconds(paragraphs.getOrNull(finished)?.text.orEmpty())
            addedSeconds += seconds
            if (addedSeconds >= 60) {
                Store.addListenedSeconds(addedSeconds)
                addedSeconds = 0
            }
            if (index + 1 < paragraphs.size) {
                index += 1
                speak(index)
            } else {
                complete()
            }
        }

        @Deprecated("Deprecated in Java")
        override fun onError(utteranceId: String?) {
            playing = false
            SpeechController.update { it.copy(isPlaying = false, error = "Não foi possível narrar este trecho") }
            updateNotification()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val id = intent.getStringExtra(EXTRA_SUMMARY_ID) ?: return START_NOT_STICKY
                val start = intent.getIntExtra(EXTRA_PARAGRAPH, 0)
                val auto = intent.getBooleanExtra(EXTRA_AUTOPLAY, true)
                startSummary(id, start, auto)
            }
            ACTION_TOGGLE -> if (summary == null) stopSelf() else if (playing) pause() else resume()
            ACTION_PAUSE -> pause()
            ACTION_RESUME -> resume()
            ACTION_SEEK -> {
                index = intent.getIntExtra(EXTRA_PARAGRAPH, index).coerceIn(0, (paragraphs.size - 1).coerceAtLeast(0))
                if (playing) speak(index) else {
                    updateProgressAt(index)
                    pushState()
                }
            }
            ACTION_SKIP -> {
                val delta = intent.getIntExtra(EXTRA_DELTA, 1)
                index = (index + delta).coerceIn(0, (paragraphs.size - 1).coerceAtLeast(0))
                if (playing) speak(index) else {
                    updateProgressAt(index)
                    pushState()
                }
            }
            ACTION_SPEED -> {
                speed = intent.getFloatExtra(EXTRA_SPEED, 1f).coerceIn(0.5f, 2f)
                Store.updateSettings { it.speechRate = speed }
                tts.setSpeechRate(speed)
                if (playing) speak(index) else pushState()
            }
            ACTION_SLEEP -> {
                val minutes = intent.getIntExtra(EXTRA_MINUTES, 0)
                setSleep(minutes)
            }
            ACTION_STOP -> stopPlayback()
        }
        return START_NOT_STICKY
    }

    private fun startSummary(id: String, start: Int, autoPlay: Boolean) {
        val loaded = Store.byId(id) ?: run { stopSelf(); return }
        if (summary?.id != id) addedSeconds = 0
        summary = loaded
        paragraphs = SpeechContent.build(loaded)
        index = start.coerceIn(0, (paragraphs.size - 1).coerceAtLeast(0))
        speed = Store.settings.speechRate
        tts.setSpeechRate(speed)
        startForegroundCompat()
        SpeechController.set(
            SpeechState(
                summaryId = loaded.id,
                title = loaded.title,
                author = loaded.author,
                coverColors = loaded.coverColors,
                isPlaying = false,
                paragraphIndex = index,
                totalParagraphs = paragraphs.size,
                currentTime = paragraphs.getOrNull(index)?.startSeconds ?: 0,
                duration = loaded.estimatedMinutes * 60,
                speed = speed,
                sleepMinutes = sleepMinutes
            )
        )
        if (autoPlay) {
            playing = true
            speak(index)
        } else {
            updateNotification()
        }
    }

    private fun speak(position: Int) {
        if (!ttsReady || paragraphs.isEmpty()) return
        index = position.coerceIn(0, paragraphs.lastIndex)
        val paragraph = paragraphs[index]
        val params = android.os.Bundle()
        tts.setSpeechRate(speed)
        val result = tts.speak(paragraph.text, TextToSpeech.QUEUE_FLUSH, params, "p$index")
        if (result == TextToSpeech.ERROR) {
            SpeechController.update { it.copy(error = "Falha ao iniciar a narração") }
            return
        }
        playing = true
        updateProgressAt(index)
        pushState()
        updateNotification()
    }

    private fun pause() {
        playing = false
        tts.stop()
        SpeechController.update { it.copy(isPlaying = false) }
        updateNotification()
    }

    private fun resume() {
        if (paragraphs.isEmpty()) return
        playing = true
        speak(index)
    }

    private fun setSleep(minutes: Int) {
        sleepRunnable?.let { handler.removeCallbacks(it) }
        sleepMinutes = minutes.takeIf { it > 0 }
        sleepRunnable = sleepMinutes?.let {
            Runnable {
                pause()
                sleepMinutes = null
                SpeechController.update { s -> s.copy(sleepMinutes = null) }
                updateNotification()
            }.also { runnable -> handler.postDelayed(runnable, minutes * 60_000L) }
        }
        SpeechController.update { it.copy(sleepMinutes = sleepMinutes) }
        updateNotification()
    }

    private fun complete() {
        playing = false
        val current = summary ?: return
        Store.updateProgress(current.id, 100, current.estimatedMinutes * 60)
        SpeechController.update {
            it.copy(isPlaying = false, paragraphIndex = (paragraphs.size - 1).coerceAtLeast(0), currentTime = it.duration)
        }
        updateNotification()
        stopForeground(STOP_FOREGROUND_DETACH)
        stopSelf()
    }

    private fun stopPlayback() {
        playing = false
        tts.stop()
        summary = null
        paragraphs = emptyList()
        index = 0
        sleepRunnable?.let { handler.removeCallbacks(it) }
        sleepMinutes = null
        SpeechController.reset()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun updateProgressAt(position: Int) {
        val current = summary ?: return
        val start = paragraphs.getOrNull(position)?.startSeconds ?: 0
        val duration = current.estimatedMinutes * 60
        val percent = if (duration > 0) ((start.toFloat() / duration) * 100).toInt().coerceIn(0, 100) else 0
        if (percent > current.progressPercent) {
            Store.updateProgress(current.id, percent, start)
        }
        SpeechController.update {
            it.copy(
                paragraphIndex = position,
                currentTime = start,
                totalParagraphs = paragraphs.size,
                isPlaying = playing
            )
        }
    }

    private fun pushState() {
        val current = summary ?: return
        SpeechController.update {
            it.copy(
                summaryId = current.id,
                title = current.title,
                author = current.author,
                coverColors = current.coverColors,
                isPlaying = playing,
                paragraphIndex = index,
                totalParagraphs = paragraphs.size,
                currentTime = paragraphs.getOrNull(index)?.startSeconds ?: 0,
                duration = current.estimatedMinutes * 60,
                speed = speed,
                sleepMinutes = sleepMinutes
            )
        }
    }

    private fun startForegroundCompat() {
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    getString(R.string.notification_channel_playback),
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = getString(R.string.notification_channel_desc)
                    setShowBadge(false)
                }
            )
        }
    }

    private fun updateNotification() {
        if (!::tts.isInitialized) return
        val manager = getSystemService(NotificationManager::class.java)
        runCatching { manager.notify(NOTIFICATION_ID, buildNotification()) }
    }

    private fun buildNotification(): Notification {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, PlayerActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val toggle = PendingIntent.getService(
            this, 1, Intent(this, SpeechService::class.java).setAction(ACTION_TOGGLE),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stop = PendingIntent.getService(
            this, 2, Intent(this, SpeechService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val current = summary
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(current?.title ?: "Áudio de Resumo")
            .setContentText(current?.author ?: "Resumo narrado")
            .setContentIntent(open)
            .setOngoing(playing)
            .setOnlyAlertOnce(true)
            .addAction(
                if (playing) R.drawable.ic_pause else R.drawable.ic_play_arrow,
                if (playing) "Pausar" else "Continuar",
                toggle
            )
            .addAction(R.drawable.ic_close, "Parar", stop)
            .setStyle(NotificationCompat.BigTextStyle().bigText(current?.executiveSummary.orEmpty()))
            .build()
    }

    private fun estimateSeconds(text: String): Int {
        val words = text.split(" ").size
        return (words / (2.4f * speed)).toInt().coerceAtLeast(2)
    }

    override fun onDestroy() {
        sleepRunnable?.let { handler.removeCallbacks(it) }
        if (::tts.isInitialized) {
            tts.stop()
            tts.shutdown()
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val CHANNEL_ID = "playback"
        private const val NOTIFICATION_ID = 4102
        const val ACTION_START = "com.audioresumo.action.START"
        const val ACTION_TOGGLE = "com.audioresumo.action.TOGGLE"
        const val ACTION_PAUSE = "com.audioresumo.action.PAUSE"
        const val ACTION_RESUME = "com.audioresumo.action.RESUME"
        const val ACTION_SEEK = "com.audioresumo.action.SEEK"
        const val ACTION_SKIP = "com.audioresumo.action.SKIP"
        const val ACTION_SPEED = "com.audioresumo.action.SPEED"
        const val ACTION_SLEEP = "com.audioresumo.action.SLEEP"
        const val ACTION_STOP = "com.audioresumo.action.STOP"
        const val EXTRA_SUMMARY_ID = "summary_id"
        const val EXTRA_PARAGRAPH = "paragraph"
        const val EXTRA_AUTOPLAY = "autoplay"
        const val EXTRA_DELTA = "delta"
        const val EXTRA_SPEED = "speed"
        const val EXTRA_MINUTES = "minutes"

        fun start(context: Context, summaryId: String, paragraph: Int = 0, autoPlay: Boolean = true) {
            val intent = Intent(context, SpeechService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_SUMMARY_ID, summaryId)
                putExtra(EXTRA_PARAGRAPH, paragraph)
                putExtra(EXTRA_AUTOPLAY, autoPlay)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent)
            else context.startService(intent)
        }

        fun toggle(context: Context) {
            context.startService(Intent(context, SpeechService::class.java).setAction(ACTION_TOGGLE))
        }

        fun send(context: Context, action: String, configure: Intent.() -> Unit = {}) {
            val intent = Intent(context, SpeechService::class.java).setAction(action).apply(configure)
            context.startService(intent)
        }
    }
}
