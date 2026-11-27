package com.mangareminder.update

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.content.edit
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.mangareminder.BuildConfig
import com.mangareminder.MainActivity
import com.mangareminder.R
import com.mangareminder.releases.ReleaseNotifier
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Réglage et mémoire de la vérification automatique (préférences privées, hors sauvegarde). */
object AutoUpdatePrefs {
    private const val FILE = "update_check"
    private const val ENABLED = "enabled"
    private const val LAST_NOTIFIED = "last_notified_version_code"

    private fun prefs(context: Context) = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    /** Activée par défaut : une requête par semaine vers GitHub, sans jeton. */
    fun isEnabled(context: Context): Boolean = prefs(context).getBoolean(ENABLED, true)

    fun setEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit { putBoolean(ENABLED, enabled) }
        UpdateScheduler.sync(context)
    }

    fun lastNotified(context: Context): Int = prefs(context).getInt(LAST_NOTIFIED, 0)

    fun setLastNotified(context: Context, versionCode: Int) {
        prefs(context).edit { putInt(LAST_NOTIFIED, versionCode) }
    }
}

/**
 * Cherche une nouvelle version une fois par semaine et la signale par une notification. Ne télécharge
 * rien : un appui ouvre la fenêtre de mise à jour habituelle, où l'utilisateur décide.
 */
class UpdateCheckWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        if (!AutoUpdatePrefs.isEnabled(applicationContext)) return Result.success()
        // Erreur réseau ou release incohérente : on réessaiera la semaine prochaine, sans insister.
        val info = try {
            Updater().findUpdate()
        } catch (e: IOException) {
            null
        } catch (e: UpdateException) {
            null
        } ?: return Result.success()
        val last = AutoUpdatePrefs.lastNotified(applicationContext)
        if (shouldNotifyUpdate(info.versionCode, BuildConfig.VERSION_CODE, last) &&
            UpdateNotifier.notify(applicationContext, info)
        ) {
            AutoUpdatePrefs.setLastNotified(applicationContext, info.versionCode)
        }
        return Result.success()
    }
}

object UpdateScheduler {
    private const val WORK = "update-check-weekly"

    /** Programme (ou annule) la vérification hebdomadaire selon le réglage. */
    fun sync(context: Context) {
        val workManager = WorkManager.getInstance(context)
        if (AutoUpdatePrefs.isEnabled(context)) {
            workManager.enqueueUniquePeriodicWork(
                WORK,
                ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<UpdateCheckWorker>(7, TimeUnit.DAYS)
                    .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                    .build(),
            )
        } else {
            workManager.cancelUniqueWork(WORK)
        }
    }
}

object UpdateNotifier {
    private const val CHANNEL = "app_updates"
    private const val NOTIFICATION_ID = 0x0B5E

    /** @return false si la notification n'a pas pu être affichée (notifications refusées). */
    @SuppressLint("MissingPermission") // Vérifiée par canNotify().
    fun notify(context: Context, info: UpdateInfo): Boolean {
        if (!ReleaseNotifier.canNotify(context)) return false
        val manager = context.getSystemService(NotificationManager::class.java)
        // Canal distinct des sorties : l'utilisateur peut couper l'un sans l'autre.
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, context.getString(R.string.channel_updates), NotificationManager.IMPORTANCE_LOW),
        )
        val open = PendingIntent.getActivity(
            context,
            NOTIFICATION_ID,
            Intent(context, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_CHECK_UPDATE, true)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.notif_update_title, info.versionName))
            .setContentText(context.getString(R.string.notif_update_text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        manager.notify(NOTIFICATION_ID, notification)
        return true
    }
}
