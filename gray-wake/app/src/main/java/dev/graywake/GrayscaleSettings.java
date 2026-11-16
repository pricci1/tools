package dev.graywake;

import android.Manifest;
import android.content.ContentResolver;
import android.content.Context;
import android.content.pm.PackageManager;
import android.provider.Settings;
import dev.graywake.core.Engine;

/** Android's color-correction keys are hidden constants, so use their AOSP string names. */
final class GrayscaleSettings implements Engine.Display {
    static final String MODE_KEY = "accessibility_display_daltonizer";
    static final String ENABLED_KEY = "accessibility_display_daltonizer_enabled";
    private final Context context;

    GrayscaleSettings(Context context) { this.context = context.getApplicationContext(); }

    static boolean hasPermission(Context context) {
        return context.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS)
                == PackageManager.PERMISSION_GRANTED;
    }

    @Override public void setGrayscale(boolean enabled) {
        if (!hasPermission(context)) {
            throw new SecurityException("Grant WRITE_SECURE_SETTINGS using the command in setup");
        }
        ContentResolver resolver = context.getContentResolver();
        String previousMode = Settings.Secure.getString(resolver, MODE_KEY);
        String previousEnabled = Settings.Secure.getString(resolver, ENABLED_KEY);
        boolean changedMode = false;
        boolean changedEnabled = false;
        try {
            if (enabled && !"0".equals(previousMode)) {
                put(resolver, MODE_KEY, "0"); // AOSP DALTONIZER_SIMULATE_MONOCHROMACY.
                changedMode = true;
            }
            String wanted = enabled ? "1" : "0";
            if (!wanted.equals(previousEnabled)) {
                put(resolver, ENABLED_KEY, wanted);
                changedEnabled = true;
            }
        } catch (RuntimeException failure) {
            // Color correction is two settings, not an atomic platform transaction.
            // Restore any successful partial writes rather than silently corrupting it.
            try {
                if (changedMode) put(resolver, MODE_KEY, previousMode);
                if (changedEnabled) put(resolver, ENABLED_KEY, previousEnabled);
            } catch (RuntimeException rollback) {
                failure.addSuppressed(rollback);
            }
            throw failure;
        }
    }

    private static void put(ContentResolver resolver, String key, String value) {
        if (!Settings.Secure.putString(resolver, key, value)) {
            throw new IllegalStateException("Android rejected setting " + key);
        }
    }
}
