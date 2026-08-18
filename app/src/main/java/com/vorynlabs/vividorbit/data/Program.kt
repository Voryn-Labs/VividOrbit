package com.vividorbit.livetv.data

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class Program(
    val title: String,
    val startTimeUtcMillis: Long,
    val endTimeUtcMillis: Long,
    val description: String? = null
) {
    companion object {
        private val timeFormat = ThreadLocal.withInitial {
            SimpleDateFormat("h:mm a", Locale.getDefault())
        }
    }

    fun getFormattedTimeWindow(): String {
        return try {
            val sdf = timeFormat.get() ?: SimpleDateFormat("h:mm a", Locale.getDefault())
            val start = sdf.format(Date(startTimeUtcMillis))
            val end = sdf.format(Date(endTimeUtcMillis))
            "$start – $end"
        } catch (e: Exception) {
            ""
        }
    }

    fun getProgressPercent(currentTimeMillis: Long = System.currentTimeMillis()): Int {
        val total = endTimeUtcMillis - startTimeUtcMillis
        if (total <= 0) return 0
        val elapsed = currentTimeMillis - startTimeUtcMillis
        return ((elapsed.toDouble() / total.toDouble()) * 100).toInt().coerceIn(0, 100)
    }
}
