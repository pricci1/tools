package dev.graywake;

import android.app.PendingIntent;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.drawable.Icon;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;
import dev.graywake.core.Mode;
import dev.graywake.core.State;
import dev.graywake.core.TimePoint;

/** Passive tile: SystemUI binds it while visible. Monitoring has its own lifecycle. */
public final class GrayTileService extends TileService {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private StateStore store;
    private boolean listening;
    private final Runnable ticker = this::render;
    private final SharedPreferences.OnSharedPreferenceChangeListener listener = (prefs, key) -> render();

    @Override public void onCreate() {
        super.onCreate();
        store = new StateStore(this);
    }

    @Override public void onStartListening() {
        super.onStartListening();
        if (!listening) store.preferences.registerOnSharedPreferenceChangeListener(listener);
        listening = true;
        render();
    }

    @Override public void onStopListening() {
        listening = false;
        store.preferences.unregisterOnSharedPreferenceChangeListener(listener);
        handler.removeCallbacksAndMessages(null);
        super.onStopListening();
    }

    @Override public void onDestroy() {
        if (store != null) store.preferences.unregisterOnSharedPreferenceChangeListener(listener);
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    @Override public void onClick() {
        super.onClick();
        if (!GrayscaleSettings.hasPermission(this) || !store.error().isEmpty()) {
            openSetup();
            return;
        }
        // A system/user stop is exceptional: resume the displayed selection first.
        boolean paused = store.load().mode.isMonitoring() && !MonitorService.isRunning;
        if (!ServiceCommands.send(this, paused ? ServiceCommands.RESUME : ServiceCommands.CYCLE)) {
            openSetup();
        }
    }

    private void render() {
        if (!listening) return;
        handler.removeCallbacks(ticker);
        Tile tile = getQsTile();
        if (tile == null) return;
        TimePoint now = AndroidClock.now(this);
        State state = store.load().resolve(now);
        boolean permitted = GrayscaleSettings.hasPermission(this);
        boolean failed = !store.error().isEmpty();
        boolean paused = state.mode.isMonitoring() && !MonitorService.isRunning;
        String status;
        if (!permitted) status = getString(R.string.tile_permission);
        else if (failed) status = getString(R.string.tile_error);
        else if (paused) status = getString(R.string.tile_paused);
        else status = UiText.description(this, state, now);
        boolean effective = permitted && !failed && !paused;
        tile.setState(effective && state.mode == Mode.ENABLED ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
        int icon = state.mode.isSnoozed() ? R.drawable.ic_snooze
                : (state.mode == Mode.ENABLED ? R.drawable.ic_grayscale : R.drawable.ic_grayscale_off);
        tile.setIcon(Icon.createWithResource(this, icon));
        String next = effective ? UiText.mode(this, state.mode.next())
                : (paused && permitted && !failed ? getString(R.string.retry) : getString(R.string.setup_title));
        tile.setContentDescription(getString(R.string.tile_description, status, next));
        if (Build.VERSION.SDK_INT >= 29) {
            tile.setLabel(getString(R.string.tile_name));
            tile.setSubtitle(status);
        } else {
            tile.setLabel(effective ? oldLabel(state.mode) : getString(R.string.tile_old_label, status));
        }
        if (Build.VERSION.SDK_INT >= 30) tile.setStateDescription(status);
        tile.updateTile();
        if (state.mode.isTimedSnooze()) {
            long remaining = state.remainingMillis(now);
            long untilMinuteChange = remaining % 60_000;
            if (untilMinuteChange == 0) untilMinuteChange = 60_000;
            handler.postDelayed(ticker, Math.max(1, Math.min(remaining, untilMinuteChange)));
        }
    }

    private String oldLabel(Mode mode) {
        String suffix;
        if (mode == Mode.DISABLED) suffix = getString(R.string.tile_old_off);
        else if (mode == Mode.ENABLED) suffix = getString(R.string.tile_old_on);
        else if (mode == Mode.SNOOZE_UNTIL_WAKE) suffix = getString(R.string.tile_old_until_wake);
        else if (mode == Mode.SNOOZE_12H) suffix = getString(R.string.tile_old_hours);
        else suffix = getString(R.string.tile_old_minutes, mode.durationMillis / 60_000);
        return getString(R.string.tile_old_label, suffix);
    }

    // The PendingIntent overload is only available on API 34+; older devices need Intent.
    @android.annotation.SuppressLint("StartActivityAndCollapseDeprecated")
    @SuppressWarnings("deprecation")
    private void openSetup() {
        Intent intent = new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        if (Build.VERSION.SDK_INT >= 34) {
            startActivityAndCollapse(PendingIntent.getActivity(this, 2, intent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
        } else {
            startActivityAndCollapse(intent);
        }
    }
}
