package dev.graywake;

import android.Manifest;
import android.app.Activity;
import android.app.StatusBarManager;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ComponentName;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Insets;
import android.graphics.Typeface;
import android.graphics.drawable.Icon;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import dev.graywake.core.State;
import dev.graywake.core.TimePoint;

/** Setup/recovery only; daily controls live entirely in the Quick Settings tile. */
public final class MainActivity extends Activity {
    private StateStore store;
    private TextView status;
    private TextView permission;
    private TextView error;
    private Button retry;
    private Button disable;
    private Button notifications;
    private final SharedPreferences.OnSharedPreferenceChangeListener listener = (prefs, key) -> refresh();

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        store = new StateStore(this);
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(content);
        applyInsets(scroll, content);
        text(content, R.string.app_name, 32, true);
        text(content, R.string.tagline, 17, false);
        status = text(content, 0, 18, true);
        permission = text(content, 0, 15, false);
        error = text(content, 0, 15, false);
        text(content, R.string.setup_title, 22, true);
        text(content, R.string.setup_instructions, 16, false);
        TextView command = text(content, R.string.adb_command, 14, false);
        command.setTypeface(Typeface.MONOSPACE);
        command.setTextIsSelectable(true);
        button(content, R.string.copy_command, this::copyCommand);
        button(content, R.string.add_tile, this::addTile);
        retry = button(content, R.string.retry, () -> ServiceCommands.send(this, ServiceCommands.RESUME));
        disable = button(content, R.string.disable_restore, () -> ServiceCommands.send(this, ServiceCommands.DISABLE));
        notifications = button(content, R.string.notifications, this::notificationAccess);
        button(content, R.string.battery_settings, () -> openSettings(new Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + getPackageName()))));
        text(content, R.string.cycle_title, 22, true);
        text(content, R.string.cycle, 16, false);
        text(content, R.string.behavior, 16, false);
        text(content, R.string.notes_title, 22, true);
        text(content, R.string.notes, 15, false);
        setContentView(scroll);
        scroll.requestApplyInsets();
    }

    @Override public void onResume() {
        super.onResume();
        store.preferences.registerOnSharedPreferenceChangeListener(listener);
        refresh();
    }

    @Override public void onPause() {
        store.preferences.unregisterOnSharedPreferenceChangeListener(listener);
        super.onPause();
    }

    private void refresh() {
        if (status == null) return;
        TimePoint now = AndroidClock.now(this);
        State state = store.load().resolve(now);
        boolean granted = GrayscaleSettings.hasPermission(this);
        status.setText(getString(R.string.status, UiText.description(this, state, now),
                getString(MonitorService.isRunning ? R.string.running : R.string.stopped)));
        permission.setText(granted ? R.string.permission_granted : R.string.permission_not_granted);
        String message = store.error();
        error.setVisibility(message.isEmpty() ? View.GONE : View.VISIBLE);
        error.setText(message);
        retry.setEnabled(granted);
        disable.setEnabled(granted);
        notifications.setText(Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                ? R.string.notifications : R.string.notification_settings);
    }

    private void copyCommand() {
        getSystemService(ClipboardManager.class).setPrimaryClip(
                ClipData.newPlainText(getString(R.string.app_name), getString(R.string.adb_command)));
        Toast.makeText(this, R.string.command_copied, Toast.LENGTH_SHORT).show();
    }

    private void addTile() {
        if (Build.VERSION.SDK_INT < 33) {
            Toast.makeText(this, R.string.manual_tile, Toast.LENGTH_LONG).show();
            return;
        }
        try {
            getSystemService(StatusBarManager.class).requestAddTileService(
                    new ComponentName(this, GrayTileService.class), getString(R.string.tile_name),
                    Icon.createWithResource(this, R.drawable.ic_grayscale), getMainExecutor(), result -> {
                        boolean added = result == StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ADDED
                                || result == StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ALREADY_ADDED;
                        Toast.makeText(this, added ? R.string.tile_added : R.string.tile_add_failed,
                                Toast.LENGTH_LONG).show();
                    });
        } catch (RuntimeException unavailable) {
            Toast.makeText(this, R.string.manual_tile, Toast.LENGTH_LONG).show();
        }
    }

    private void notificationAccess() {
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1);
        } else {
            openSettings(new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName()));
        }
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        refresh();
    }

    private void openSettings(Intent intent) {
        try { startActivity(intent); }
        catch (RuntimeException unavailable) {
            Toast.makeText(this, R.string.settings_unavailable, Toast.LENGTH_LONG).show();
        }
    }

    private TextView text(LinearLayout parent, int resource, int size, boolean bold) {
        TextView view = new TextView(this);
        if (resource != 0) view.setText(resource);
        view.setTextSize(size);
        if (bold) view.setTypeface(null, Typeface.BOLD);
        view.setPadding(0, dp(bold ? 20 : 8), 0, dp(8));
        parent.addView(view, new LinearLayout.LayoutParams(-1, -2));
        return view;
    }

    private Button button(LinearLayout parent, int resource, Runnable action) {
        Button button = new Button(this);
        button.setText(resource);
        button.setAllCaps(false);
        button.setOnClickListener(v -> action.run());
        parent.addView(button, new LinearLayout.LayoutParams(-1, -2));
        return button;
    }

    @SuppressWarnings("deprecation")
    private void applyInsets(ScrollView scroll, LinearLayout content) {
        int padding = dp(24);
        content.setPadding(padding, padding, padding, padding);
        scroll.setOnApplyWindowInsetsListener((view, insets) -> {
            int left, top, right, bottom;
            if (Build.VERSION.SDK_INT >= 30) {
                Insets bars = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                left = bars.left; top = bars.top; right = bars.right; bottom = bars.bottom;
            } else {
                left = insets.getSystemWindowInsetLeft(); top = insets.getSystemWindowInsetTop();
                right = insets.getSystemWindowInsetRight(); bottom = insets.getSystemWindowInsetBottom();
            }
            view.setPadding(left, top, right, bottom);
            return insets;
        });
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
