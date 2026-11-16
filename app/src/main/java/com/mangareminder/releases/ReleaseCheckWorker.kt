package com.mangareminder.releases

import android.Manifest
import android.annotation.SuppressLint
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
import com.mangareminder.data.SeriesRepository
import com.mangareminder.data.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONException
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Cherche les nouveaux tomes des séries surveillées et notifie les nouveautés. */
class ReleaseCheckWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        ReleaseStore.init(applicationContext)
        val all = SeriesRepository(applicationContext).load()
        ReleaseStore.retainOnly(all.map { it.id }.toSet())
        val watched = all.filter { it.watched }
        if (watched.isEmpty()) return@withContext Result.success()

        ReleaseStore.setChecking(true)
        val sources = buildSources(Settings(applicationContext).rssFeedUrl)
        val news = mutableListOf<Pair<Series, Release>>()
        val failedSources = mutableSetOf<String>()
        var failedSeries = 0
        try {
            for (series in watched) {
                var failuresForSeries = 0
                val found = mutableListOf<Release>()
                for (source in sources) {
                    try {
                        found += source.search(series.title)
                    } catch (e: IOException) {
                        failedSources += source.name
                        failuresForSeries++
                    } catch (e: JSONException) {
                        failedSources += source.name
                        failuresForSeries++
                    }
                }
                if (failuresForSeries == sources.size) {
                    failedSeries++
                    continue
                }
                val previous = ReleaseStore.get(series.id)
                // Les flux RSS ne gardent que les dernières entrées : on cumule avec ce qui est connu.
                val releases = mergeReleases(found + previous?.releases.orEmpty()).take(MAX_KEPT_RELEASES)
                var notifiedMax = previous?.notifiedMax ?: 0
                val latest = releases.maxByOrNull { it.volume }
                if (latest != null && latest.volume > series.lastKnownVolume && latest.volume > notifiedMax) {
                    news += series to latest
                    notifiedMax = latest.volume
                }
                ReleaseStore.put(series.id, SeriesReleases(System.currentTimeMillis(), releases, notifiedMax))
            }
        } finally {
            ReleaseStore.setChecking(false)
        }

        ReleaseStore.setError(
            if (failedSources.isEmpty()) null
            else "Sans réponse : ${failedSources.joinToString()}. Nouvel essai à la prochaine vérification."
        )
        ReleaseNotifier.notify(applicationContext, news)
        if (failedSeries == watched.size) Result.retry() else Result.success()
    }

    companion object {
        private const val MAX_KEPT_RELEASES = 30

        fun buildSources(rssFeedUrl: String): List<ReleaseSource> = buildList {
            add(GoogleBooksSource)
            if (rssFeedUrl.isNotBlank()) {
                add(RssSource(rssFeedUrl, name = if ("manga-news" in rssFeedUrl) "manga-news" else "Flux RSS"))
            }
        }
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

    fun checkNow(context: Context) {
        WorkManager.getInstance(context).enqueueUniqueWork(
            NOW,
            ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<ReleaseCheckWorker>().setConstraints(network).build(),
        )
    }
}

object ReleaseNotifier {
    private const val CHANNEL = "releases"

    fun canNotify(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission") // Vérifiée par canNotify().
    fun notify(context: Context, news: List<Pair<Series, Release>>) {
        if (news.isEmpty() || !canNotify(context)) return
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, "Sorties de tomes", NotificationManager.IMPORTANCE_DEFAULT)
        )
        val openApp = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        news.forEach { (series, release) ->
            val notification = NotificationCompat.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("Nouveau tome : ${series.title}")
                .setContentText(release.describe())
                .setContentIntent(openApp)
                .setAutoCancel(true)
                .build()
            manager.notify(series.id.hashCode(), notification)
        }
    }
}
