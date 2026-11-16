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
        val news = mutableListOf<Pair<Series, Release>>()
        var failures = 0
        try {
            for (series in watched) {
                val found = try {
                    mergeReleases(SOURCES.flatMap { it.search(series.title) })
                } catch (e: IOException) {
                    failures++
                    continue
                } catch (e: JSONException) {
                    failures++
                    continue
                }
                val previous = ReleaseStore.get(series.id)
                var notifiedMax = previous?.notifiedMax ?: 0
                val latest = found.maxByOrNull { it.volume }
                if (latest != null && latest.volume > series.lastKnownVolume && latest.volume > notifiedMax) {
                    news += series to latest
                    notifiedMax = latest.volume
                }
                ReleaseStore.put(
                    series.id,
                    SeriesReleases(
                        checkedAt = System.currentTimeMillis(),
                        // Une recherche vide (quota, titre mal reconnu) n'efface pas les résultats connus.
                        releases = found.ifEmpty { previous?.releases ?: emptyList() },
                        notifiedMax = notifiedMax,
                    ),
                )
            }
        } finally {
            ReleaseStore.setChecking(false)
        }

        ReleaseStore.setError(
            if (failures > 0) "$failures série(s) non vérifiée(s) : Google Books n'a pas répondu (quota ?)." else null
        )
        ReleaseNotifier.notify(applicationContext, news)
        if (failures == watched.size) Result.retry() else Result.success()
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
