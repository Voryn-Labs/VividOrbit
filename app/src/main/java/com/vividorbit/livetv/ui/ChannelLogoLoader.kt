package com.vividorbit.livetv.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.LruCache
import java.util.concurrent.ConcurrentHashMap

object ChannelLogoLoader {

    private const val NEGATIVE_CACHE_TTL_MS = 5 * 60 * 1000L // 5 minutes
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

    fun loadAndCache(context: Context, channelId: Long, logoUri: Uri?, reqSize: Int = 200): Bitmap? {
        if (logoUri == null) return null
        cache.get(channelId)?.let { return it }

        val now = System.currentTimeMillis()
        val missTime = missesWithTimestamp[channelId]
        if (missTime != null && (now - missTime) < NEGATIVE_CACHE_TTL_MS) {
            return null
        }

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

            val decodedBitmap = context.contentResolver.openInputStream(logoUri)?.use { input ->
                val options = BitmapFactory.Options().apply {
                    this.inSampleSize = inSampleSize
                    inPreferredConfig = Bitmap.Config.RGB_565
                }
                BitmapFactory.decodeStream(input, null, options)?.also { bitmap ->
                    cache.put(channelId, bitmap)
                    missesWithTimestamp.remove(channelId)
                }
            }

            if (decodedBitmap == null) {
                missesWithTimestamp[channelId] = now
            }
            decodedBitmap
        } catch (e: Exception) {
            missesWithTimestamp[channelId] = now
            null
        }
    }
}
