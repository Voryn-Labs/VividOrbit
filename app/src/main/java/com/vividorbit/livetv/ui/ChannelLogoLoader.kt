package com.vividorbit.livetv.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.LruCache

object ChannelLogoLoader {

    private val maxMemoryKb = (Runtime.getRuntime().maxMemory() / 1024).toInt()
    private val cacheSizeKb = maxMemoryKb / 8
    private val cache = object : LruCache<Long, Bitmap>(cacheSizeKb) {
        override fun sizeOf(key: Long, bitmap: Bitmap): Int = bitmap.byteCount / 1024
    }

    fun getCached(channelId: Long): Bitmap? = cache.get(channelId)

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
