package com.vorynlabs.vividorbit.data

import android.content.Context
import android.content.SharedPreferences
import android.view.KeyEvent
import com.vorynlabs.vividorbit.R

enum class RemoteAction(val key: String, val defaultKeys: List<Int>, val title: String) {
    ZAP_UP("zap_up", listOf(KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_CHANNEL_UP, KeyEvent.KEYCODE_PAGE_UP), "Channel Up (Zap)"),
    ZAP_DOWN("zap_down", listOf(KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_CHANNEL_DOWN, KeyEvent.KEYCODE_PAGE_DOWN), "Channel Down (Zap)"),
    OPEN_GUIDE("open_guide", listOf(KeyEvent.KEYCODE_GUIDE, KeyEvent.KEYCODE_MENU), "Open Guide"),
    INFO_BANNER("info_banner", listOf(KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_INFO), "Info Banner"),
    QUICK_RECALL("quick_recall", listOf(KeyEvent.KEYCODE_LAST_CHANNEL, KeyEvent.KEYCODE_MEDIA_PREVIOUS), "Quick Recall"),
    TOGGLE_FAVORITE("toggle_favorite", listOf(KeyEvent.KEYCODE_PROG_YELLOW, KeyEvent.KEYCODE_PROG_BLUE), "Toggle Favorite (★)");

    companion object {
        fun fromKey(key: String): RemoteAction? = values().find { it.key == key }
    }
}

class KeyMappingRepository(context: Context) {
    companion object {
        private const val PREFS_NAME = "vividorbit_keymap_prefs"
    }

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getCustomKey(action: RemoteAction): Int? {
        val code = prefs.getInt("key_${action.key}", -1)
        return if (code != -1) code else null
    }

    /**
     * Returns the action that would conflict if [keyCode] were assigned to [action],
     * or null if the key is free. A key conflicts when it is already used as a custom
     * key by another action or as a default key by any other action (defaults stay
     * active until overridden, so no physical button may activate more than one action).
     */
    fun findConflict(action: RemoteAction, keyCode: Int): RemoteAction? {
        return RemoteAction.values().firstOrNull { other ->
            other != action && (getCustomKey(other) == keyCode || other.defaultKeys.contains(keyCode))
        }
    }

    fun setCustomKey(action: RemoteAction, keyCode: Int): Boolean {
        if (findConflict(action, keyCode) != null) return false
        prefs.edit().putInt("key_${action.key}", keyCode).apply()
        return true
    }

    fun resetDefaults() {
        prefs.edit().clear().apply()
    }

    fun matches(action: RemoteAction, keyCode: Int): Boolean {
        val custom = getCustomKey(action)
        return if (custom != null) keyCode == custom else action.defaultKeys.contains(keyCode)
    }

    fun getActionDisplayName(action: RemoteAction): String {
        val custom = getCustomKey(action)
        return if (custom != null) {
            "${action.title}: ${getKeyDisplayName(custom)}"
        } else {
            val defs = action.defaultKeys.joinToString(" / ") { getKeyDisplayName(it) }
            "${action.title}: $defs"
        }
    }

    fun getKeyDisplayName(keyCode: Int): String {
        return when (keyCode) {
            KeyEvent.KEYCODE_DPAD_UP -> "▲ Up"
            KeyEvent.KEYCODE_DPAD_DOWN -> "▼ Down"
            KeyEvent.KEYCODE_DPAD_LEFT -> "◀ Left"
            KeyEvent.KEYCODE_DPAD_RIGHT -> "▶ Right"
            KeyEvent.KEYCODE_DPAD_CENTER -> "OK / Center"
            KeyEvent.KEYCODE_ENTER -> "Enter"
            KeyEvent.KEYCODE_CHANNEL_UP -> "CH +"
            KeyEvent.KEYCODE_CHANNEL_DOWN -> "CH -"
            KeyEvent.KEYCODE_PAGE_UP -> "Page Up"
            KeyEvent.KEYCODE_PAGE_DOWN -> "Page Down"
            KeyEvent.KEYCODE_GUIDE -> "GUIDE"
            KeyEvent.KEYCODE_MENU -> "MENU"
            KeyEvent.KEYCODE_LAST_CHANNEL -> "LAST CH"
            KeyEvent.KEYCODE_MEDIA_PREVIOUS -> "PREV"
            KeyEvent.KEYCODE_PROG_RED -> "Red Key"
            KeyEvent.KEYCODE_PROG_GREEN -> "Green Key"
            KeyEvent.KEYCODE_PROG_YELLOW -> "Yellow Key"
            KeyEvent.KEYCODE_PROG_BLUE -> "Blue Key"
            KeyEvent.KEYCODE_INFO -> "INFO"
            else -> KeyEvent.keyCodeToString(keyCode).removePrefix("KEYCODE_")
        }
    }
}
