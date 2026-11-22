package com.mangareminder.ui

import android.content.Context
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.mangareminder.releases.Http
import com.mangareminder.releases.MAX_COVER_BYTES
import com.mangareminder.releases.coverCacheName
import com.mangareminder.releases.isSafeCoverUrl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URL

/**
 * Miniatures de couvertures : en mémoire, puis dans le cache de l'app (vidé par Android si besoin,
 * hors sauvegarde), téléchargées sinon. Une image invalide n'est ni affichée ni gardée.
 */
object CoverCache {
    private const val MAX_SIDE = 2_000
    private const val TARGET_SIDE = 300

    private val memory = LruCache<String, ImageBitmap>(80)

    fun cached(url: String): ImageBitmap? = memory.get(url)

    suspend fun load(context: Context, url: String): ImageBitmap? = withContext(Dispatchers.IO) {
        if (!isSafeCoverUrl(url)) return@withContext null
        memory.get(url)?.let { return@withContext it }
        val dir = File(context.cacheDir, "covers").apply { mkdirs() }
        val file = File(dir, coverCacheName(url))
        val bytes = file.takeIf { it.length() in 1..MAX_COVER_BYTES.toLong() }?.readBytes()
            ?: runCatching { Http.getBytes(URL(url), MAX_COVER_BYTES) }.getOrNull()?.also { downloaded ->
                runCatching {
                    val tmp = File(dir, "${file.name}.tmp")
                    tmp.writeBytes(downloaded)
                    tmp.renameTo(file)
                }
            }
            ?: return@withContext null
        val bitmap = decode(bytes)
        if (bitmap == null) {
            file.delete()
            return@withContext null
        }
        memory.put(url, bitmap)
        bitmap
    }

    /** Refuse les dimensions aberrantes et réduit l'image à une taille d'affichage. */
    private fun decode(bytes: ByteArray): ImageBitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        val (w, h) = bounds.outWidth to bounds.outHeight
        if (w !in 1..MAX_SIDE || h !in 1..MAX_SIDE) return null
        var sample = 1
        while (maxOf(w, h) / (sample * 2) >= TARGET_SIDE) sample *= 2
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)?.asImageBitmap()
    }
}

/** Couverture d'une série ou d'un tome ; un cadre neutre tant qu'elle charge ou si elle est introuvable. */
@Composable
fun CoverImage(url: String, contentDescription: String?, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val bitmap by produceState(CoverCache.cached(url), url) {
        if (value == null) value = CoverCache.load(context, url)
    }
    Box(
        modifier
            .clip(RoundedCornerShape(4.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        bitmap?.let {
            Image(it, contentDescription, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        }
    }
}
