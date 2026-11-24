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
import com.mangareminder.data.Series

/** Widget d'écran d'accueil : tomes à acheter et prochaines sorties des séries surveillées. */
class MangaWidget : AppWidgetProvider() {
    /** Demandé par Android (ajout du widget, une fois par jour) : relecture des fichiers, toujours redessiné. */
    override fun onUpdate(context: Context, manager: AppWidgetManager, widgetIds: IntArray) {
        MangaWidgetUpdater.updateFromFiles(context)
    }
}

object MangaWidgetUpdater {

    private val gate = WidgetRedrawGate()

    /**
     * Après une modification de la collection ou une vérification des sorties : à partir des données
     * déjà en mémoire, sans relire les fichiers, et seulement si le contenu du widget a changé.
     */
    fun update(context: Context, series: List<Series>, releases: Map<Long, SeriesReleases>) {
        val summary = widgetSummary(series, releases)
        if (gate.shouldRedraw(summary)) draw(context, summary)
    }

    /** Relecture des fichiers (appel d'Android) : lecture seule, un fichier illisible donne un widget vide. */
    fun updateFromFiles(context: Context) {
        val series = try {
            SeriesRepository(context).readRaw()?.let { SeriesRepository.fromJson(it) }.orEmpty()
        } catch (e: JSONException) {
            emptyList()
        }
        val summary = widgetSummary(series, readReleases(context))
        gate.shouldRedraw(summary)
        draw(context, summary)
    }

    private fun draw(context: Context, summary: WidgetSummary) {
        val manager = AppWidgetManager.getInstance(context)
        val ids = manager.getAppWidgetIds(ComponentName(context, MangaWidget::class.java))
        if (ids.isEmpty()) return
        manager.updateAppWidget(ids, views(context, summary))
    }

    private fun views(context: Context, summary: WidgetSummary): RemoteViews {
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
