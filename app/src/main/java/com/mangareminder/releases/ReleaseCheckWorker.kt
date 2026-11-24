package com.mangareminder.releases

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.mangareminder.MainActivity
import com.mangareminder.R
import com.mangareminder.data.Series
import com.mangareminder.data.LoadResult
import com.mangareminder.data.SeriesRepository
import com.mangareminder.widget.MangaWidgetUpdater
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONException
import java.io.IOException
import java.time.LocalDate
import java.util.concurrent.TimeUnit
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat

/** Cherche les nouveaux tomes des séries surveillées et notifie les nouveautés. */
class ReleaseCheckWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        ReleaseStore.init(applicationContext)
        // Collection illisible : on ne touche à rien (ni cache des sorties, ni copie de côté).
        val all = (SeriesRepository(applicationContext).load(setAside = false) as? LoadResult.Ok)?.series
            ?: return@withContext Result.success()
        ReleaseStore.retainOnly(all.map { it.id }.toSet())
        val watched = all.filter { it.watched }
        if (watched.isEmpty()) return@withContext Result.success()

        ReleaseStore.setChecking(true)
        val news = mutableListOf<Pair<Series, Release>>()
        val reminders = mutableListOf<Pair<Series, Release>>()
        val failures = mutableListOf<CheckFailure>()
        try {
            for (series in watched) {
                val found = try {
                    mergeReleases(SOURCES.flatMap { it.search(series.title) })
                } catch (e: IOException) {
                    failures += failureOf(e)
                    continue
                } catch (e: JSONException) {
                    failures += failureOf(e)
                    continue
                }
                val outcome = checkOutcome(series, found, ReleaseStore.get(series.id), System.currentTimeMillis())
                outcome.notify?.let { news += series to it }
                outcome.remind?.let { reminders += series to it }
                ReleaseStore.put(series.id, outcome.stored)
            }
        } finally {
            ReleaseStore.setChecking(false)
        }

        ReleaseStore.setError(checkError(failures, System.currentTimeMillis()))
        ReleaseNotifier.notify(applicationContext, news, reminders)
        MangaWidgetUpdater.update(applicationContext, all, ReleaseStore.data.value)
        if (shouldRetry(failures, watched.size, runAttemptCount)) Result.retry() else Result.success()
    }

    companion object {
        val SOURCES: List<ReleaseSource> = listOf(GoogleBooksSource)
    }
}

object ReleaseScheduler {
    private const val PERIODIC = "release-check-periodic"
    private const val NOW = "release-check-now"

    private val network = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    /** Active la vérification quotidienne s'il y a au moins une série surveillée. */
    fun sync(context: Context, anyWatched: Boolean) {
        val workManager = WorkManager.getInstance(context)
        if (anyWatched) {
            workManager.enqueueUniquePeriodicWork(
                PERIODIC,
                ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<ReleaseCheckWorker>(12, TimeUnit.HOURS)
                    .setConstraints(network)
                    .build(),
            )
        } else {
            workManager.cancelUniqueWork(PERIODIC)
        }
    }

    /** Nom de la tâche ponctuelle, observé par l'écran pour afficher « Recherche en cours… ». */
    const val NOW_WORK = NOW

    /**
     * Remplace une vérification ponctuelle en attente (nouvel essai programmé, réseau absent) :
     * un appui lance toujours une vérification. La tâche périodique n'est pas touchée.
     */
    fun checkNow(context: Context) {
        WorkManager.getInstance(context).enqueueUniqueWork(
            NOW,
            ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<ReleaseCheckWorker>().setConstraints(network).build(),
        )
    }
}

object ReleaseNotifier {
    private const val CHANNEL = "releases"

    /** Permission accordée (Android 13+) et notifications de l'app non désactivées dans les réglages. */
    fun canNotify(context: Context): Boolean =
        (
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
            ) && NotificationManagerCompat.from(context).areNotificationsEnabled()

    /** Réglages des notifications de l'app (Android 8+). */
    fun settingsIntent(context: Context): Intent =
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)

    @SuppressLint("MissingPermission") // Vérifiée par canNotify().
    fun notify(context: Context, news: List<Pair<Series, Release>>, reminders: List<Pair<Series, Release>> = emptyList()) {
        if ((news.isEmpty() && reminders.isEmpty()) || !canNotify(context)) return
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, context.getString(R.string.channel_releases), NotificationManager.IMPORTANCE_DEFAULT)
        )
        news.forEach { (series, release) ->
            val id = series.id.hashCode()
            manager.notify(
                id,
                build(context, id, series, release, context.getString(R.string.notif_new_volume, series.title), release.describe().text(context)),
            )
        }
        // Rappel du jour de sortie : identifiant distinct, il ne remplace pas une annonce encore affichée.
        reminders.forEach { (series, release) ->
            val today = LocalDate.now().toString()
            val text = if (release.date == today) context.getString(R.string.notif_out_today, release.volume)
            else context.getString(R.string.notif_out_since, release.volume, formatReleaseDate(release.date))
            val id = series.id.hashCode() xor REMINDER_ID
            manager.notify(id, build(context, id, series, release, context.getString(R.string.notif_volume_out, series.title), text))
        }
    }

    private fun build(context: Context, id: Int, series: Series, release: Release, title: String, text: String): Notification {
        // Un appui ouvre la fiche de la série : un code de requête par série, sinon Android
        // réutiliserait la même intention (et le même extra) pour toutes les notifications.
        val openSeries = PendingIntent.getActivity(
            context,
            series.id.hashCode(),
            Intent(context, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_SERIES_ID, series.id)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(openSeries)
            .addAction(0, context.getString(R.string.action_got_it), OwnedVolumeReceiver.intent(context, id, series.id, release.volume))
            .setAutoCancel(true)
            .build()
    }

    private const val REMINDER_ID = 0x5EED
}
