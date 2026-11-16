package dev.graywake;

import android.content.Context;
import android.content.Intent;
import android.util.Log;

final class ServiceCommands {
    static final String CYCLE = "dev.graywake.CYCLE";
    static final String RESUME = "dev.graywake.RESUME";
    static final String DISABLE = "dev.graywake.DISABLE";
    private ServiceCommands() {}

    static boolean send(Context context, String action) {
        try {
            context.startForegroundService(new Intent(context, MonitorService.class).setAction(action));
            return true;
        } catch (RuntimeException failure) {
            Log.e("GrayWake", "Unable to start monitoring", failure);
            new StateStore(context).setError(context.getString(R.string.start_failed));
            return false;
        }
    }
}
