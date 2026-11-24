package com.mangareminder

import android.content.Context
import com.mangareminder.backup.AutoBackup
import com.mangareminder.data.CollectionStore
import com.mangareminder.data.SeriesRepository
import com.mangareminder.releases.ReleaseStore
import com.mangareminder.widget.MangaWidgetUpdater
import java.util.concurrent.Executors

/**
 * La collection, une seule fois par processus : l'écran et les actions hors de l'app (bouton
 * « Je l'ai » d'une notification) modifient la même liste en mémoire et passent par le même fil
 * d'écriture. Sans cela, l'écran ouvert réécrirait sa copie par-dessus la modification.
 */
object AppCollection {
    @Volatile private var store: CollectionStore? = null

    fun get(context: Context): CollectionStore =
        store ?: synchronized(this) { store ?: create(context.applicationContext).also { store = it } }

    private fun create(app: Context) = CollectionStore(
        SeriesRepository(app),
        // Un seul fil d'écriture : les enregistrements s'appliquent dans l'ordre des modifications.
        Executors.newSingleThreadExecutor(),
        afterSaved = { saved ->
            AutoBackup.scheduleAfterChange(app)
            MangaWidgetUpdater.update(app, saved, ReleaseStore.data.value)
        },
    )
}
