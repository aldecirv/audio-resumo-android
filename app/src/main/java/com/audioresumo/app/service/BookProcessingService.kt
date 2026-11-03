package com.audioresumo.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.audioresumo.app.ui.BookDetailActivity
import com.audioresumo.app.R
import com.audioresumo.app.data.BookStore
import com.audioresumo.app.data.BookStatus
import com.audioresumo.app.data.Store
import com.audioresumo.app.util.BookProcessor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/** Processa o resumo do livro em primeiro plano, com progresso persistido. */
class BookProcessingService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null
    private var bookId: String = ""
    private var minutes = 15

    override fun onCreate() {
        super.onCreate()
        Store.init(this)
        BookStore.init(this)
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_CANCEL -> {
                job?.cancel()
                stopForegroundCompat()
                stopSelf()
            }
            else -> {
                bookId = intent?.getStringExtra("book_id").orEmpty()
                minutes = intent?.getIntExtra("minutes", 15) ?: 15
                if (bookId.isBlank()) {
                    stopSelf()
                    return START_NOT_STICKY
                }
                startForegroundCompat()
                startProcessing()
            }
        }
        return START_NOT_STICKY
    }

    private fun startProcessing() {
        job?.cancel()
        job = scope.launch {
            try {
                val result = BookProcessor.summarizeBook(
                    context = this@BookProcessingService,
                    bookId = bookId,
                    minutes = minutes,
                    onProgress = { progress ->
                        updateNotification(progress.percent, progress.message)
                    },
                    isActive = { job?.isActive == true }
                )
                if (result.isSuccess) {
                    updateNotification(100, "Resumo do livro concluído")
                } else {
                    val error = result.exceptionOrNull()
                    BookStore.byId(bookId)?.let { book ->
                        book.status = BookStatus.ERROR
                        book.summaryStatus = "error"
                        book.message = error?.message ?: "Falha no processamento"
                        BookStore.updateMetadata(book)
                    }
                    updateNotification(0, error?.message ?: "Falha no processamento")
                }
            } catch (_: CancellationException) {
                BookStore.byId(bookId)?.let { book ->
                    book.status = BookStatus.READY
                    book.summaryStatus = "paused"
                    book.message = "Processamento cancelado. Toque em continuar."
                    BookStore.updateMetadata(book)
                }
            } finally {
                stopForegroundCompat()
                stopSelf()
            }
        }
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, "Processamento de livros", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Resumos de livros completos em segundo plano"
            }
        )
    }

    private fun startForegroundCompat() {
        val notification = buildNotification(0, "Preparando livro...")
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun updateNotification(progress: Int, message: String) {
        runCatching {
            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
                .notify(NOTIFICATION_ID, buildNotification(progress, message))
        }
    }

    private fun buildNotification(progress: Int, message: String): Notification {
        val open = PendingIntent.getActivity(
            this,
            0,
            BookDetailActivity.intent(this, bookId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val cancel = PendingIntent.getService(
            this,
            1,
            Intent(this, BookProcessingService::class.java).setAction(ACTION_CANCEL),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val book = BookStore.byId(bookId)
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_menu_book)
            .setContentTitle(book?.title ?: "Processando livro")
            .setContentText(message)
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(100, progress.coerceIn(0, 100), progress <= 0)
            .addAction(R.drawable.ic_close, "Cancelar", cancel)
            .build()
    }

    private fun stopForegroundCompat() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) stopForeground(STOP_FOREGROUND_REMOVE)
        else @Suppress("DEPRECATION") stopForeground(true)
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val CHANNEL = "book_processing"
        private const val NOTIFICATION_ID = 8101
        const val ACTION_CANCEL = "com.audioresumo.app.BOOK_CANCEL"

        fun start(context: Context, bookId: String, minutes: Int) {
            val intent = Intent(context, BookProcessingService::class.java).apply {
                putExtra("book_id", bookId)
                putExtra("minutes", minutes)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent)
            else context.startService(intent)
        }

        fun cancel(context: Context) {
            context.startService(
                Intent(context, BookProcessingService::class.java).setAction(ACTION_CANCEL)
            )
        }
    }
}
