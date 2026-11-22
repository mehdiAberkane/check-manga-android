package com.mangareminder.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.mangareminder.MainActivity
import com.mangareminder.R
import com.mangareminder.data.SeriesRepository
import com.mangareminder.releases.MAX_RELEASE_CACHE_BYTES
import com.mangareminder.releases.SeriesReleases
import com.mangareminder.releases.releaseCacheFromJson
import org.json.JSONException
import java.io.File

/** Widget d'écran d'accueil : tomes à acheter et prochaines sorties des séries surveillées. */
class MangaWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, widgetIds: IntArray) {
        MangaWidgetUpdater.update(context)
    }
}

object MangaWidgetUpdater {

    /**
     * Redessine tous les widgets à partir des fichiers de l'app. Appelé après chaque modification
     * de la collection, après chaque vérification des sorties, et par Android une fois par jour.
     * Lecture seule : un fichier illisible donne un widget vide, sans rien toucher.
     */
    fun update(context: Context) {
        val manager = AppWidgetManager.getInstance(context)
        val ids = manager.getAppWidgetIds(ComponentName(context, MangaWidget::class.java))
        if (ids.isEmpty()) return
        manager.updateAppWidget(ids, views(context))
    }

    private fun views(context: Context): RemoteViews {
        val series = try {
            SeriesRepository(context).readRaw()?.let { SeriesRepository.fromJson(it) }.orEmpty()
        } catch (e: JSONException) {
            emptyList()
        }
        val summary = widgetSummary(series, readReleases(context))
        val res = context.resources
        return RemoteViews(context.packageName, R.layout.widget).apply {
            setTextViewText(
                R.id.widget_to_buy,
                if (summary.toBuy == 0) res.getString(R.string.widget_nothing_to_buy)
                else res.getQuantityString(R.plurals.widget_to_buy, summary.toBuy, summary.toBuy),
            )
            setTextViewText(
                R.id.widget_releases,
                if (summary.nextReleases.isEmpty()) res.getString(R.string.widget_no_release)
                else summary.nextReleases.joinToString("\n") { (title, release) ->
                    res.getString(R.string.widget_release_line, title, release.describe().text(context))
                },
            )
            val open = PendingIntent.getActivity(
                context,
                0,
                // Ouvre directement la liste « À acheter ».
                Intent(context, MainActivity::class.java)
                    .putExtra(MainActivity.EXTRA_SHOPPING, true)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            setOnClickPendingIntent(R.id.widget_root, open)
        }
    }

    private fun readReleases(context: Context): Map<Long, SeriesReleases> = runCatching {
        val file = File(context.filesDir, "releases.json")
        if (!file.exists() || file.length() > MAX_RELEASE_CACHE_BYTES) emptyMap() else releaseCacheFromJson(file.readText())
    }.getOrDefault(emptyMap())
}
