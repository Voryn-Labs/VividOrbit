package com.vividorbit.livetv.data

import android.net.Uri

data class Channel(
    val id: Long,
    val displayNumber: String,
    val displayName: String,
    val inputId: String,
    val logoUri: Uri
)
