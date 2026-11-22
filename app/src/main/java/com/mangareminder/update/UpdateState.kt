package com.mangareminder.update

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import androidx.core.content.IntentCompat
import com.mangareminder.R
import com.mangareminder.UiMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data class Available(val info: UpdateInfo) : UpdateState
    data class NeedsPermission(val info: UpdateInfo) : UpdateState
    data class Downloading(val info: UpdateInfo, val progress: Float) : UpdateState
    data object Installing : UpdateState
    data class Failed(val message: UiMessage) : UpdateState
}

/** État partagé entre l'écran et [UpdateInstallReceiver], qui reçoit le résultat d'Android. */
object UpdateStatus {
    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    fun set(state: UpdateState) {
        _state.value = state
    }
}

/**
 * Résultat de la session d'installation. Non exporté : seul PackageInstaller, via le PendingIntent
 * créé par [Updater.install], peut l'appeler.
 */
class UpdateInstallReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                // Écran système « Mettre à jour cette application ? ».
                val confirm = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_INTENT, Intent::class.java)
                if (confirm == null) {
                    UpdateStatus.set(UpdateState.Failed(UiMessage(R.string.update_err_no_confirmation)))
                    return
                }
                confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                runCatching { context.startActivity(confirm) }
                    .onFailure { UpdateStatus.set(UpdateState.Failed(UiMessage(R.string.update_err_confirmation_failed))) }
            }
            // L'app est redémarrée par Android dans sa nouvelle version.
            PackageInstaller.STATUS_SUCCESS -> UpdateStatus.set(UpdateState.Idle)
            PackageInstaller.STATUS_FAILURE_ABORTED -> UpdateStatus.set(UpdateState.Failed(UiMessage(R.string.update_err_cancelled)))
            else -> {
                val detail = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)?.take(200)
                UpdateStatus.set(
                    UpdateState.Failed(
                        when (status) {
                            PackageInstaller.STATUS_FAILURE_STORAGE -> UiMessage(R.string.update_err_storage)
                            PackageInstaller.STATUS_FAILURE_CONFLICT,
                            PackageInstaller.STATUS_FAILURE_INCOMPATIBLE,
                            -> UiMessage(R.string.update_err_incompatible)
                            else -> detail?.let { UiMessage(R.string.update_err_install_failed_detail, it) }
                                ?: UiMessage(R.string.update_err_install_failed)
                        },
                    ),
                )
            }
        }
    }
}
