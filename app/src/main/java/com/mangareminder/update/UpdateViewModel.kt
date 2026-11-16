package com.mangareminder.update

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data class Available(val info: UpdateInfo) : UpdateState
    data class Downloading(val info: UpdateInfo, val progress: Float?) : UpdateState
    data class Ready(val info: UpdateInfo, val apk: File) : UpdateState
    data class Failed(val message: String) : UpdateState
}

class UpdateViewModel(app: Application) : AndroidViewModel(app) {
    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    val currentVersion: String = AppUpdater.currentVersionName(app)

    init {
        if (AppUpdater.autoCheckDue(app)) check(manual = false)
    }

    /** Une vérification automatique reste silencieuse si rien n'est disponible ou en cas d'erreur. */
    fun check(manual: Boolean) {
        if (_state.value is UpdateState.Checking || _state.value is UpdateState.Downloading) return
        if (manual) _state.value = UpdateState.Checking
        viewModelScope.launch {
            val app = getApplication<Application>()
            _state.value = try {
                val info = withContext(Dispatchers.IO) { AppUpdater.fetchNewerVersion(app) }
                when {
                    info == null -> if (manual) UpdateState.UpToDate else UpdateState.Idle
                    else -> UpdateState.Available(info)
                }
            } catch (e: IOException) {
                if (manual) UpdateState.Failed("Impossible de joindre GitHub. Vérifie ta connexion.") else UpdateState.Idle
            }
        }
    }

    fun download(info: UpdateInfo) {
        _state.value = UpdateState.Downloading(info, null)
        viewModelScope.launch {
            val app = getApplication<Application>()
            _state.value = try {
                val apk = withContext(Dispatchers.IO) {
                    AppUpdater.download(app) { p -> _state.value = UpdateState.Downloading(info, p) }
                }
                UpdateState.Ready(info, apk)
            } catch (e: IOException) {
                UpdateState.Failed(e.message ?: "Téléchargement impossible")
            }
        }
    }

    /** Renvoie false si l'autorisation d'installer doit d'abord être donnée. */
    fun install(apk: File): Boolean = AppUpdater.install(getApplication<Application>(), apk)

    fun dismiss() {
        _state.value = UpdateState.Idle
    }
}
