package com.mangareminder.ui

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mangareminder.R
import com.mangareminder.UiMessage
import com.mangareminder.update.UpdateException
import com.mangareminder.update.UpdateInfo
import com.mangareminder.update.UpdateState
import com.mangareminder.update.UpdateStatus
import com.mangareminder.update.Updater
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.StateFlow
import java.io.IOException

class UpdateViewModel(app: Application) : AndroidViewModel(app) {
    private val updater = Updater(app)
    private var job: Job? = null

    val state: StateFlow<UpdateState> = UpdateStatus.state

    init {
        // APK d'une tentative interrompue (app tuée pendant le téléchargement).
        if (state.value == UpdateState.Idle) updater.clean()
    }

    fun check() {
        if (job?.isActive == true) return
        UpdateStatus.set(UpdateState.Checking)
        job = viewModelScope.launch {
            UpdateStatus.set(
                runReporting { updater.findUpdate()?.let { UpdateState.Available(it) } ?: UpdateState.UpToDate }
                    ?: UpdateState.Idle,
            )
        }
    }

    fun install(info: UpdateInfo) {
        if (job?.isActive == true) return
        if (!updater.canInstall()) {
            UpdateStatus.set(UpdateState.NeedsPermission(info))
            return
        }
        UpdateStatus.set(UpdateState.Downloading(info, 0f))
        job = viewModelScope.launch {
            val result = runReporting {
                val apk = updater.download(info) { UpdateStatus.set(UpdateState.Downloading(info, it)) }
                UpdateStatus.set(UpdateState.Installing)
                updater.install(apk)
                null
            }
            // En cas de succès, c'est UpdateInstallReceiver qui donne la suite.
            if (result != null) UpdateStatus.set(result)
        }
    }

    /** Écran des réglages Android où autoriser l'installation ; on revient ensuite à la proposition. */
    fun permissionSettings(info: UpdateInfo): Intent {
        UpdateStatus.set(UpdateState.Available(info))
        return updater.permissionSettingsIntent()
    }

    fun dismiss() {
        job?.cancel()
        updater.clean()
        UpdateStatus.set(UpdateState.Idle)
    }

    private suspend fun runReporting(block: suspend () -> UpdateState?): UpdateState? = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: UpdateException) {
        UpdateState.Failed(e.uiMessage)
    } catch (e: IOException) {
        UpdateState.Failed(UiMessage(R.string.update_err_network))
    } catch (e: Exception) {
        UpdateState.Failed(UiMessage(R.string.update_err_unexpected, e.javaClass.simpleName))
    }
}
