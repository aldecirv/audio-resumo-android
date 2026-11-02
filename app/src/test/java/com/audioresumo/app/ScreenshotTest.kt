package com.audioresumo.app

import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.view.View
import androidx.test.core.app.ApplicationProvider
import com.audioresumo.app.data.Store
import com.audioresumo.app.ui.FlashcardsActivity
import com.audioresumo.app.ui.MainActivity
import com.audioresumo.app.ui.MindMapActivity
import com.audioresumo.app.ui.PlayerActivity
import com.audioresumo.app.ui.QuizActivity
import com.audioresumo.app.ui.SummaryDetailActivity
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
class ScreenshotTest {

    private val dir = File("build/screenshots").apply { mkdirs() }

    @Test
    fun captureAllScreens() {
        Store.init(ApplicationProvider.getApplicationContext())
        val main = Robolectric.buildActivity(MainActivity::class.java).setup().visible().get()
        idle()
        capture(main.window.decorView, "01_home")

        main.openTab(R.id.navLibrary)
        idle()
        capture(main.window.decorView, "02_library")

        main.openTab(R.id.navCreate)
        idle()
        capture(main.window.decorView, "03_create")

        main.openTab(R.id.navLearn)
        idle()
        capture(main.window.decorView, "04_learn")

        main.openTab(R.id.navProfile)
        idle()
        capture(main.window.decorView, "05_profile")

        val id = Store.summaries.first().id
        val detail = Robolectric.buildActivity(
            SummaryDetailActivity::class.java,
            SummaryDetailActivity.intent(main, id)
        ).setup().visible().get()
        idle()
        capture(detail.window.decorView, "06_detail")

        val player = Robolectric.buildActivity(
            PlayerActivity::class.java,
            PlayerActivity.intent(main, id)
        ).setup().visible().get()
        idle()
        capture(player.window.decorView, "07_player")

        val mind = Robolectric.buildActivity(
            MindMapActivity::class.java,
            MindMapActivity.intent(main, id)
        ).setup().visible().get()
        idle()
        capture(mind.window.decorView, "08_mindmap")

        val cards = Robolectric.buildActivity(
            FlashcardsActivity::class.java,
            FlashcardsActivity.intent(main, id)
        ).setup().visible().get()
        idle()
        capture(cards.window.decorView, "09_flashcards")

        val quiz = Robolectric.buildActivity(
            QuizActivity::class.java,
            QuizActivity.intent(main, id)
        ).setup().visible().get()
        idle()
        capture(quiz.window.decorView, "10_quiz")
    }

    private fun idle() {
        Shadows.shadowOf(Looper.getMainLooper()).idle()
    }

    private fun capture(view: View, name: String) {
        idle()
        val width = 1080
        val height = 2280
        view.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY)
        )
        view.layout(0, 0, width, height)
        idle()
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
