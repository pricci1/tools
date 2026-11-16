package dev.graywake;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import dev.graywake.core.Engine;
import dev.graywake.core.State;

/** Device-protected storage is available before the first unlock after reboot. */
final class StateStore implements Engine.Store {
    final SharedPreferences preferences;

    StateStore(Context context) {
        preferences = context.createDeviceProtectedStorageContext()
                .getSharedPreferences("gray_wake", Context.MODE_PRIVATE);
    }

    @Override public State load() {
        try {
            return State.restore(preferences.getString("mode", "DISABLED"),
                    preferences.getLong("deadline_elapsed", 0),
                    preferences.getLong("deadline_wall", 0),
                    preferences.getInt("boot_count", -1));
        } catch (ClassCastException corruptPreferences) {
            return State.disabled();
        }
    }

    @SuppressLint("ApplySharedPref") // Persist the complete deadline before reporting success.
    @Override public void save(State state) {
        State previous = load();
        if (!editor(state).commit()) {
            // commit() also updates the in-memory map; roll that back on disk failure.
            editor(previous).commit();
            throw new IllegalStateException("Unable to save the grayscale state");
        }
    }

    private SharedPreferences.Editor editor(State state) {
        return preferences.edit().putString("mode", state.mode.name())
                .putLong("deadline_elapsed", state.deadlineElapsedMillis)
                .putLong("deadline_wall", state.deadlineWallMillis)
                .putInt("boot_count", state.bootCount);
    }

    String error() { return preferences.getString("last_error", ""); }
    void setError(String message) { preferences.edit().putString("last_error", message).apply(); }

    void signalUi() {
        // Revision is just an invalidation signal, never persisted runtime truth.
        preferences.edit().putLong("ui_revision", System.nanoTime()).apply();
    }
}
