package com.vorynlabs.vividorbit

import android.view.KeyEvent
import com.vorynlabs.vividorbit.data.RemoteAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyMappingTest {

    @Test
    fun testDefaultKeyActionsMatch() {
        // Zap Up default keys
        assertTrue(RemoteAction.ZAP_UP.defaultKeys.contains(KeyEvent.KEYCODE_DPAD_UP))
        assertTrue(RemoteAction.ZAP_UP.defaultKeys.contains(KeyEvent.KEYCODE_CHANNEL_UP))

        // Zap Down default keys
        assertTrue(RemoteAction.ZAP_DOWN.defaultKeys.contains(KeyEvent.KEYCODE_DPAD_DOWN))
        assertTrue(RemoteAction.ZAP_DOWN.defaultKeys.contains(KeyEvent.KEYCODE_CHANNEL_DOWN))

        // Guide default keys
        assertTrue(RemoteAction.OPEN_GUIDE.defaultKeys.contains(KeyEvent.KEYCODE_GUIDE))
        assertTrue(RemoteAction.OPEN_GUIDE.defaultKeys.contains(KeyEvent.KEYCODE_MENU))

        // Info default keys
        assertTrue(RemoteAction.INFO_BANNER.defaultKeys.contains(KeyEvent.KEYCODE_DPAD_CENTER))
        assertTrue(RemoteAction.INFO_BANNER.defaultKeys.contains(KeyEvent.KEYCODE_ENTER))

        // Quick Recall default keys
        assertTrue(RemoteAction.QUICK_RECALL.defaultKeys.contains(KeyEvent.KEYCODE_LAST_CHANNEL))
        assertTrue(RemoteAction.QUICK_RECALL.defaultKeys.contains(KeyEvent.KEYCODE_MEDIA_PREVIOUS))

        // Favorite default keys
        assertTrue(RemoteAction.TOGGLE_FAVORITE.defaultKeys.contains(KeyEvent.KEYCODE_PROG_YELLOW))
        assertTrue(RemoteAction.TOGGLE_FAVORITE.defaultKeys.contains(KeyEvent.KEYCODE_PROG_BLUE))
    }

    @Test
    fun testActionLookupByKey() {
        assertEquals(RemoteAction.ZAP_UP, RemoteAction.fromKey("zap_up"))
        assertEquals(RemoteAction.OPEN_GUIDE, RemoteAction.fromKey("open_guide"))
        assertEquals(null, RemoteAction.fromKey("unknown_action"))
    }
}
