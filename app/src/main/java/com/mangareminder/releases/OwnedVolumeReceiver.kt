package com.mangareminder.releases

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.mangareminder.AppCollection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Bouton « Je l'ai » d'une notification de sortie : coche le tome sans ouvrir l'app.
 * Non exporté (manifeste) : seules nos notifications l'appellent, par une intention immuable. Les
 * valeurs reçues sont tout de même vérifiées par [com.mangareminder.data.CollectionStore.markOwned].
 */
class OwnedVolumeReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val seriesId = intent.getLongExtra(EXTRA_SERIES_ID, -1)
        val volume = intent.getIntExtra(EXTRA_VOLUME, 0)
        val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, 0)
        val app = context.applicationContext
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            val store = AppCollection.get(app)
            try {
                // Même collection que l'écran (s'il est ouvert, il voit le tome coché) ; lue ici sinon.
                store.load()
                store.markOwned(seriesId, volume)
            } finally {
                // Après l'écriture : le widget et la sauvegarde automatique sont à jour.
                store.afterSave {
                    app.getSystemService(NotificationManager::class.java).cancel(notificationId)
                    pending.finish()
                }
            }
        }
    }

    companion object {
        private const val EXTRA_SERIES_ID = "series_id"
        private const val EXTRA_VOLUME = "volume"
        private const val EXTRA_NOTIFICATION_ID = "notification_id"

        /** Action de la notification [notificationId] ; un code de requête par notification. */
        fun intent(context: Context, notificationId: Int, seriesId: Long, volume: Int): PendingIntent =
            PendingIntent.getBroadcast(
                context,
                notificationId,
                Intent(context, OwnedVolumeReceiver::class.java)
                    .putExtra(EXTRA_SERIES_ID, seriesId)
                    .putExtra(EXTRA_VOLUME, volume)
                    .putExtra(EXTRA_NOTIFICATION_ID, notificationId),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
    }
}
