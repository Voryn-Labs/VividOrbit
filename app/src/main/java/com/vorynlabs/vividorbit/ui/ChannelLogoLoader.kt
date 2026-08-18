package com.vorynlabs.vividorbit.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.tv.TvContract
import android.net.Uri
import android.util.LruCache
import android.view.View
import android.widget.ImageView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

object ChannelLogoLoader {

    private const val NEGATIVE_CACHE_TTL_MS = 5 * 60 * 1000L
    private val maxMemoryKb = (Runtime.getRuntime().maxMemory() / 1024).toInt()
    private val cacheSizeKb = maxMemoryKb / 8
    private val cache = object : LruCache<Long, Bitmap>(cacheSizeKb) {
        override fun sizeOf(key: Long, bitmap: Bitmap): Int = bitmap.byteCount / 1024
    }
    private val missesWithTimestamp = ConcurrentHashMap<Long, Long>()

    fun getCached(channelId: Long): Bitmap? = cache.get(channelId)

    fun evictAll() {
        cache.evictAll()
        missesWithTimestamp.clear()
    }

    fun bind(
        imageView: ImageView,
        scope: CoroutineScope,
        channelId: Long,
        logoUri: Uri?
    ): Job? {
        imageView.tag = channelId
        val cached = cache.get(channelId)
        if (cached != null) {
            imageView.setImageBitmap(cached)
            imageView.visibility = View.VISIBLE
            return null
        }
        imageView.setImageDrawable(null)
        imageView.visibility = View.GONE
        return scope.launch(Dispatchers.IO) {
            val bitmap = loadAndCache(imageView.context, channelId, logoUri)
            withContext(Dispatchers.Main) {
                if (bitmap != null && imageView.tag == channelId) {
                    imageView.setImageBitmap(bitmap)
                    imageView.visibility = View.VISIBLE
                }
            }
        }
    }

    fun loadAndCache(context: Context, channelId: Long, logoUri: Uri?, reqSize: Int = 200): Bitmap? {
        cache.get(channelId)?.let { return it }

        val now = System.currentTimeMillis()
        val missTime = missesWithTimestamp[channelId]
        if (missTime != null && (now - missTime) < NEGATIVE_CACHE_TTL_MS) {
            return null
        }

        val candidates = LinkedHashSet<Uri>()
        if (logoUri != null) candidates.add(logoUri)
        if (channelId != -1L) candidates.add(TvContract.buildChannelLogoUri(channelId))

        for (uri in candidates) {
            val decoded = decodeLogo(context, uri, reqSize)
            if (decoded != null) {
                cache.put(channelId, decoded)
                missesWithTimestamp.remove(channelId)
                return decoded
            }
        }

        missesWithTimestamp[channelId] = now
        return null
    }

    private fun decodeLogo(context: Context, uri: Uri, reqSize: Int): Bitmap? {
        val sample = sampleSizeFor(context, uri, reqSize)
        val options = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        try {
            context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { afd ->
                val bitmap = BitmapFactory.decodeFileDescriptor(afd.fileDescriptor, null, options)
                if (bitmap != null) return bitmap
            }
        } catch (_: Exception) {
        }
        return try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                BitmapFactory.decodeStream(input, null, options)
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun sampleSizeFor(context: Context, uri: Uri, reqSize: Int): Int {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        try {
            context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { afd ->
                BitmapFactory.decodeFileDescriptor(afd.fileDescriptor, null, bounds)
            }
        } catch (_: Exception) {
            try {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    BitmapFactory.decodeStream(input, null, bounds)
                }
            } catch (_: Exception) {
                return 1
            }
        }
        var sample = 1
        var halfH = bounds.outHeight / 2
        var halfW = bounds.outWidth / 2
        while (halfH / sample >= reqSize && halfW / sample >= reqSize) {
            sample *= 2
        }
        return sample
    }
}
