package com.mangareminder.ui

import android.app.Application
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mangareminder.R
import com.mangareminder.UiMessage
import com.mangareminder.update.UpdateException
import com.mangareminder.update.UpdateInfo
import com.mangareminder.update.UpdateState
import com.mangareminder.update.UpdateStatus
import com.mangareminder.update.Updater
import com.mangareminder.update.isAllowedUrl
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.io.IOException

class UpdateViewModel(app: Application) : AndroidViewModel(app) {
    private val updater = Updater()
    private var job: Job? = null

    val state: StateFlow<UpdateState> = UpdateStatus.state

    init {
        // APK laissé dans le cache par l'ancienne mise à jour intégrée (versions 1.5 et avant).
        viewModelScope.launch(Dispatchers.IO) { File(app.cacheDir, "update").deleteRecursively() }
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

    /**
     * Ouvre le lien de l'APK dans le navigateur, qui le télécharge ; l'utilisateur l'ouvre ensuite
     * et l'installeur d'Android le met à jour par-dessus (même signature : la collection est gardée).
     */
    fun download(context: Context, info: UpdateInfo) {
        // Déjà vérifié à la lecture de la release ; on ne confie au navigateur qu'un lien GitHub en HTTPS.
        if (!isAllowedUrl(info.apk.url)) {
            UpdateStatus.set(UpdateState.Failed(UiMessage(R.string.update_err_url_refused)))
            return
        }
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, info.apk.url.toUri()))
            UpdateStatus.set(UpdateState.Idle)
        } catch (e: ActivityNotFoundException) {
            UpdateStatus.set(UpdateState.Failed(UiMessage(R.string.update_err_no_browser)))
        }
    }

    fun dismiss() {
        job?.cancel()
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
