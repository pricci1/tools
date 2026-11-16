package dev.graywake;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Restores only an already-selected active mode; installation never enables the app. */
public final class BootReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (!Intent.ACTION_BOOT_COMPLETED.equals(action)
                && !Intent.ACTION_LOCKED_BOOT_COMPLETED.equals(action)
                && !Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)) return;
        StateStore store = new StateStore(context);
        if (!store.load().mode.isMonitoring()) return;
        if (!GrayscaleSettings.hasPermission(context)) {
            store.setError(context.getString(R.string.permission_missing));
            return;
        }
        ServiceCommands.send(context, ServiceCommands.RESUME);
    }
}
