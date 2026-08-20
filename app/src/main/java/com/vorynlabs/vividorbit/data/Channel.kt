package com.vorynlabs.vividorbit.data

import android.net.Uri

data class Channel(
    val id: Long,
    val originalDisplayNumber: String,
    val customDisplayNumber: String?,
    val displayNumber: String,
    val displayName: String,
    val inputId: String,
    val logoUri: Uri? = null,
    val genre: String = "",
    val isHidden: Boolean = false
)
