package com.mangareminder.update

import com.mangareminder.UiMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data class Available(val info: UpdateInfo) : UpdateState
    data class Failed(val message: UiMessage) : UpdateState
}

/** État de la recherche de mise à jour, gardé hors de l'écran (survit à une rotation). */
object UpdateStatus {
    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    fun set(state: UpdateState) {
        _state.value = state
    }
}
