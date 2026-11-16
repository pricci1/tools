package dev.graywake;

import android.content.Context;
import android.os.SystemClock;
import android.provider.Settings;
import dev.graywake.core.TimePoint;

final class AndroidClock {
    private AndroidClock() {}

    static TimePoint now(Context context) {
        int bootCount;
        try {
            bootCount = Settings.Global.getInt(context.getContentResolver(),
                    Settings.Global.BOOT_COUNT, -1);
        } catch (SecurityException ignored) {
            bootCount = -1; // OEM restriction: use the bounded wall-clock fallback.
        }
        return new TimePoint(SystemClock.elapsedRealtime(), System.currentTimeMillis(), bootCount);
    }
}
