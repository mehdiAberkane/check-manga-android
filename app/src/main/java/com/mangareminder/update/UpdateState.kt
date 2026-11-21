package com.mangareminder.update

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import androidx.core.content.IntentCompat
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
    data class Failed(val message: String) : UpdateState
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
                    UpdateStatus.set(UpdateState.Failed("Android n'a pas demandé de confirmation."))
                    return
                }
                confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                runCatching { context.startActivity(confirm) }
                    .onFailure { UpdateStatus.set(UpdateState.Failed("Impossible d'afficher la confirmation d'Android.")) }
            }
            // L'app est redémarrée par Android dans sa nouvelle version.
            PackageInstaller.STATUS_SUCCESS -> UpdateStatus.set(UpdateState.Idle)
            PackageInstaller.STATUS_FAILURE_ABORTED -> UpdateStatus.set(UpdateState.Failed("Mise à jour annulée."))
            else -> {
                val detail = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)?.take(200)
                UpdateStatus.set(
                    UpdateState.Failed(
                        when (status) {
                            PackageInstaller.STATUS_FAILURE_STORAGE -> "Pas assez de place sur le téléphone."
                            PackageInstaller.STATUS_FAILURE_CONFLICT,
                            PackageInstaller.STATUS_FAILURE_INCOMPATIBLE,
                            -> "Android a refusé la mise à jour (signature ou version incompatible)."
                            else -> "Échec de l'installation" + (detail?.let { " : $it" } ?: ".")
                        },
                    ),
                )
            }
        }
    }
}
