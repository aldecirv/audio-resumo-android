package com.audioresumo.app.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.audioresumo.app.R
import com.audioresumo.app.data.Store
import com.audioresumo.app.databinding.ActivityMainBinding
import com.audioresumo.app.service.SpeechController
import com.audioresumo.app.service.SpeechService
import com.audioresumo.app.service.SpeechState
import com.audioresumo.app.ui.fragments.CreateFragment
import com.audioresumo.app.ui.fragments.HomeFragment
import com.audioresumo.app.ui.fragments.LearnFragment
import com.audioresumo.app.ui.fragments.LibraryFragment
import com.audioresumo.app.ui.fragments.ProfileFragment
import com.audioresumo.app.util.ThemeUtils
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var selected = R.id.navHome
    private var currentState: SpeechState = SpeechState()

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        ThemeUtils.darkSystemBars(this)
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.updatePadding(top = bars.top, bottom = bars.bottom)
            insets
        }

        binding.navHome.setOnClickListener { select(R.id.navHome) }
        binding.navLibrary.setOnClickListener { select(R.id.navLibrary) }
        binding.navCreate.setOnClickListener { select(R.id.navCreate) }
        binding.navLearn.setOnClickListener { select(R.id.navLearn) }
        binding.navProfile.setOnClickListener { select(R.id.navProfile) }

        binding.miniPlayer.setOnClickListener {
            currentState.summaryId?.let { startActivity(PlayerActivity.intent(this, it)) }
        }
        binding.miniPlay.setOnClickListener {
            val state = currentState
            val id = state.summaryId ?: return@setOnClickListener
            if (state.isPlaying) {
                SpeechService.send(this, SpeechService.ACTION_PAUSE)
            } else {
                SpeechService.start(this, id, state.paragraphIndex, true)
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        select(R.id.navHome)

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                SpeechController.state.collect { renderMiniPlayer(it) }
            }
        }
        Store.subscribe { runOnUiThread { refreshCurrentFragment() } }
    }

    fun openTab(id: Int, category: String? = null) {
        if (id == R.id.navLibrary && category != null) {
            supportFragmentManager.beginTransaction()
                .setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out)
                .replace(R.id.content, LibraryFragment.newInstance(category))
                .commit()
            selected = id
            updateNav()
            return
        }
        select(id)
    }

    private fun select(id: Int) {
        selected = id
        val fragment: Fragment = when (id) {
            R.id.navLibrary -> LibraryFragment()
            R.id.navCreate -> CreateFragment()
            R.id.navLearn -> LearnFragment()
            R.id.navProfile -> ProfileFragment()
            else -> HomeFragment()
        }
        supportFragmentManager.beginTransaction()
            .setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out)
            .replace(R.id.content, fragment)
            .commit()
        updateNav()
    }

    private fun updateNav() {
        val active = listOf(
            binding.navHomeIcon to binding.navHomeLabel,
            binding.navLibraryIcon to binding.navLibraryLabel,
            binding.navCreateIcon to binding.navCreateLabel,
            binding.navLearnIcon to binding.navLearnLabel,
            binding.navProfileIcon to binding.navProfileLabel
        )
        val ids = listOf(R.id.navHome, R.id.navLibrary, R.id.navCreate, R.id.navLearn, R.id.navProfile)
        active.forEachIndexed { index, (icon, label) ->
            val on = ids[index] == selected
            icon.setColorFilter(ContextCompat.getColor(this, if (on) com.audioresumo.app.R.color.brand else com.audioresumo.app.R.color.text_tertiary))
            label.setTextColor(ContextCompat.getColor(this, if (on) com.audioresumo.app.R.color.brand else com.audioresumo.app.R.color.text_tertiary))
        }
        // O item Criar mantém o ícone branco dentro do círculo gradiente.
        binding.navCreateIcon.setColorFilter(ContextCompat.getColor(this, com.audioresumo.app.R.color.white))
    }

    private fun renderMiniPlayer(state: SpeechState) {
        currentState = state
        val summary = state.summaryId?.let { Store.byId(it) }
        binding.miniPlayer.isVisible = summary != null
        if (summary == null) return
        binding.miniCover.bind(summary)
        binding.miniTitle.text = summary.title
        binding.miniAuthor.text = if (state.isPlaying) "Reproduzindo · ${summary.author}" else "Pausado · ${summary.author}"
        binding.miniPlay.setImageResource(
            if (state.isPlaying) com.audioresumo.app.R.drawable.ic_pause
            else com.audioresumo.app.R.drawable.ic_play_arrow
        )
    }

    private fun refreshCurrentFragment() {
        val fragment = supportFragmentManager.findFragmentById(R.id.content)
        when (fragment) {
            is HomeFragment -> fragment.refresh()
            is LibraryFragment -> fragment.refresh()
            is LearnFragment -> fragment.refresh()
            is ProfileFragment -> fragment.refresh()
        }
    }
}
