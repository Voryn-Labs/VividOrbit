#!/bin/bash
set -e

OUT_DIR="play_store_assets/screenshots"
mkdir -p "$OUT_DIR"

echo "📺 Connecting to Android TV (192.168.0.167:5555)..."
adb connect 192.168.0.167:5555 || true

echo "⚡ Launching VividOrbit on TV..."
adb shell am start -n com.vorynlabs.vividorbit/.MainActivity
sleep 2

# Helper function to trigger pixel-perfect in-app capture
snap_screen() {
    local filename="$1"
    local dest="$2"
    echo "📸 Capturing: $dest..."
    adb shell am broadcast -a com.vorynlabs.vividorbit.ACTION_SCREENSHOT --es filename "$filename" > /dev/null
    sleep 1.2
    adb pull "/sdcard/Android/data/com.vorynlabs.vividorbit/files/$filename" "$OUT_DIR/$dest"
}

# Dismiss any initial modal
adb shell input keyevent KEYCODE_ENTER || true
sleep 1

# 1. Info Banner Screen
adb shell input keyevent KEYCODE_DPAD_CENTER
sleep 1
snap_screen "ss1_info.png" "real_1_info_banner.png"

# 2. Channel Guide Screen
adb shell input keyevent KEYCODE_MENU
sleep 1.5
snap_screen "ss2_guide.png" "real_2_channel_guide.png"

# 3. Categories / Genre Filter
adb shell input keyevent KEYCODE_DPAD_LEFT
sleep 1
snap_screen "ss3_categories.png" "real_3_categories.png"

# Back to guide
adb shell input keyevent KEYCODE_BACK
sleep 0.8

# 4. Settings Screen (Lineup)
adb shell input keyevent KEYCODE_DPAD_UP
sleep 0.5
adb shell input keyevent KEYCODE_ENTER
sleep 1.5
snap_screen "ss4_settings.png" "real_4_settings_lineup.png"

# 5. Phone Setup Screen
adb shell input keyevent KEYCODE_DPAD_RIGHT
sleep 0.5
adb shell input keyevent KEYCODE_DPAD_RIGHT
sleep 0.5
adb shell input keyevent KEYCODE_ENTER
sleep 1.5
snap_screen "ss5_phone.png" "real_5_phone_setup.png"

# Close modals
adb shell input keyevent KEYCODE_BACK
sleep 0.5
adb shell input keyevent KEYCODE_BACK
sleep 0.5

echo ""
echo "✅ All 5 genuine in-app 1080p screenshots captured successfully:"
ls -lh "$OUT_DIR"/real_*.png
