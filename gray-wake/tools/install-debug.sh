#!/bin/sh
# The user explicitly runs this on a computer connected to their own Android device.
set -eu
ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
command -v adb >/dev/null 2>&1 || { echo 'Install Android SDK platform-tools (adb) first.' >&2; exit 1; }
adb get-state >/dev/null || { echo 'Connect and authorize one device, or set ANDROID_SERIAL.' >&2; exit 1; }
"$ROOT/gradlew" --no-daemon :app:assembleDebug
adb install -r "$ROOT/app/build/outputs/apk/debug/app-debug.apk"
adb shell pm grant dev.graywake android.permission.WRITE_SECURE_SETTINGS
adb shell am start -n dev.graywake/.MainActivity
printf '%s\n' 'Add the Grayscale tile, then tap it to enable monitoring.'
