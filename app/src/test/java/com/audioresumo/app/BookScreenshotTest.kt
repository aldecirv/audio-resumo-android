package com.audioresumo.app

import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.view.View
import androidx.test.core.app.ApplicationProvider
import com.audioresumo.app.data.BookStore
import com.audioresumo.app.data.Store
import com.audioresumo.app.ui.BookDetailActivity
import com.audioresumo.app.ui.BookQaActivity
import com.audioresumo.app.ui.BookReaderActivity
import com.audioresumo.app.ui.MainActivity
import com.audioresumo.app.util.BookImporter
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
class BookScreenshotTest {

    private val dir = File("build/screenshots").apply { mkdirs() }

    @Test
    fun captureBookScreens() {
        val context = ApplicationProvider.getApplicationContext<AudioResumoApp>()
        Store.init(context)
        BookStore.init(context)
        BookStore.clearAll()
        val text = buildString {
            appendLine("Capítulo 1 — Foco profundo")
            appendLine("O foco profundo exige eliminar distrações e definir uma intenção clara. ".repeat(30))
            appendLine("Capítulo 2 — Hábitos")
            appendLine("Hábitos são construídos por repetição, ambiente e recompensa imediata. ".repeat(30))
            appendLine("Capítulo 3 — Aprendizagem")
            appendLine("A recuperação ativa e a revisão espaçada fortalecem a memória de longo prazo. ".repeat(30))
        }
        val book = BookImporter.fromText("Foco e Aprendizagem", "Autor de Teste", text).getOrThrow()

        val main = Robolectric.buildActivity(MainActivity::class.java).setup().visible().get()
        Shadows.shadowOf(Looper.getMainLooper()).idle()
        main.openTab(R.id.navLibrary)
        Shadows.shadowOf(Looper.getMainLooper()).idle()
        main.findViewById<View>(R.id.tabBooks).performClick()
        Shadows.shadowOf(Looper.getMainLooper()).idle()
        capture(main.window.decorView, "20_books_library")

        val detail = Robolectric.buildActivity(
            BookDetailActivity::class.java,
            BookDetailActivity.intent(main, book.id)
        ).setup().visible().get()
        Shadows.shadowOf(Looper.getMainLooper()).idle()
        capture(detail.window.decorView, "21_book_detail")

        val qa = Robolectric.buildActivity(
            BookQaActivity::class.java,
            BookQaActivity.intent(main, book.id)
        ).setup().visible().get()
        Shadows.shadowOf(Looper.getMainLooper()).idle()
        capture(qa.window.decorView, "22_book_qa")

        val reader = Robolectric.buildActivity(
            BookReaderActivity::class.java,
            BookReaderActivity.intent(main, book.id, 0)
        ).setup().visible().get()
        Shadows.shadowOf(Looper.getMainLooper()).idle()
        capture(reader.window.decorView, "23_book_reader")
    }

    private fun capture(view: View, name: String) {
        Shadows.shadowOf(Looper.getMainLooper()).idle()
        view.measure(
            View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(2280, View.MeasureSpec.EXACTLY)
        )
        view.layout(0, 0, 1080, 2280)
        val bitmap = Bitmap.createBitmap(1080, 2280, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
