package com.audioresumo.app.service

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

object SpeechController {
    private val _state = MutableStateFlow(SpeechState())
    val state: StateFlow<SpeechState> = _state

    fun update(block: (SpeechState) -> SpeechState) {
        _state.value = block(_state.value)
    }

    fun set(state: SpeechState) {
        _state.value = state
    }

    fun reset() {
        _state.value = SpeechState()
    }
}
