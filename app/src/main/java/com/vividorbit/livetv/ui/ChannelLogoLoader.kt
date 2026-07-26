package com.vividorbit.livetv.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.LruCache

/**
 * Shared, downsampled, memory-cached channel logo loader.
 *
 * Loading a channel logo via ImageView.setImageURI() decodes the full
 * resolution image synchronously on the calling thread, which is expensive
 * enough to visibly stall the UI when done on the main thread (e.g. on every
 * channel change). This loader always decodes on whatever thread calls
 * [loadAndCache] - call it from a background dispatcher - and downsamples to
 * a small target size, then caches the result by channel id so repeat
 * lookups (e.g. re-showing the banner for the same channel) are free.
 */
object ChannelLogoLoader {

    private val maxMemoryKb = (Runtime.getRuntime().maxMemory() / 1024).toInt()
    private val cacheSizeKb = maxMemoryKb / 8
    private val cache = object : LruCache<Long, Bitmap>(cacheSizeKb) {
        override fun sizeOf(key: Long, bitmap: Bitmap): Int = bitmap.byteCount / 1024
    }

    fun getCached(channelId: Long): Bitmap? = cache.get(channelId)

    /**
     * Must be called from a background thread (e.g. Dispatchers.IO) - performs
     * content-provider I/O and bitmap decoding.
     */
    fun loadAndCache(context: Context, channelId: Long, logoUri: Uri, reqSize: Int = 100): Bitmap? {
        cache.get(channelId)?.let { return it }
        return try {
            var inSampleSize = 1
            context.contentResolver.openInputStream(logoUri)?.use { input ->
                val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeStream(input, null, options)
                val height = options.outHeight
                val width = options.outWidth
                if (height > reqSize || width > reqSize) {
                    val halfHeight = height / 2
                    val halfWidth = width / 2
                    while (halfHeight / inSampleSize >= reqSize && halfWidth / inSampleSize >= reqSize) {
                        inSampleSize *= 2
                    }
                }
            }

            context.contentResolver.openInputStream(logoUri)?.use { input ->
                val options = BitmapFactory.Options().apply {
                    this.inSampleSize = inSampleSize
                    inPreferredConfig = Bitmap.Config.RGB_565
                }
                BitmapFactory.decodeStream(input, null, options)?.also { bitmap ->
                    cache.put(channelId, bitmap)
                }
            }
        } catch (e: Exception) {
            null
        }
    }
}
