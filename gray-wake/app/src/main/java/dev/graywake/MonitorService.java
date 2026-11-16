package dev.graywake;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;
import dev.graywake.core.Engine;
import dev.graywake.core.State;

/** Only this service mutates state. All taps, timer callbacks and wakes are serialized. */
public final class MonitorService extends Service {
    private static final String CHANNEL = "monitor";
    private static final int NOTIFICATION_ID = 1;
    static volatile boolean isRunning;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private StateStore store;
    private Engine engine;
    private int latestStartId;
    private boolean receiverRegistered;
    private final Runnable expiration = this::reconcile;

    private final BroadcastReceiver screenReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            if (isRunning) reconcile();
        }
    };

    @Override public void onCreate() {
        super.onCreate();
        store = new StateStore(this);
        engine = new Engine(store, new GrayscaleSettings(this));
        NotificationChannel channel = new NotificationChannel(CHANNEL,
                getString(R.string.notification_channel), NotificationManager.IMPORTANCE_LOW);
        channel.setDescription(getString(R.string.notification_channel_description));
        channel.setShowBadge(false);
        getSystemService(NotificationManager.class).createNotificationChannel(channel);

        IntentFilter filter = new IntentFilter(Intent.ACTION_SCREEN_ON);
        filter.addAction(Intent.ACTION_USER_PRESENT);
        filter.addAction(Intent.ACTION_TIME_CHANGED);
        filter.addAction(Intent.ACTION_TIMEZONE_CHANGED);
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(screenReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(screenReceiver, filter);
        }
        receiverRegistered = true;
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        latestStartId = startId;
        String action = intent == null ? ServiceCommands.RESUME : intent.getAction();
        try {
            // Promote before doing any secure-settings work (also on sticky restart).
            promote(store.load().resolve(AndroidClock.now(this)));
            isRunning = true;
            State state;
            if (ServiceCommands.CYCLE.equals(action)) state = engine.advance(AndroidClock.now(this));
            else if (ServiceCommands.DISABLE.equals(action)) state = engine.disable();
            else state = engine.reconcile(AndroidClock.now(this));
            store.setError("");
            render(state);
            return state.mode.isMonitoring() ? START_STICKY : START_NOT_STICKY;
        } catch (RuntimeException failure) {
            fail(failure, ServiceCommands.CYCLE.equals(action) || ServiceCommands.DISABLE.equals(action));
            return START_NOT_STICKY;
        }
    }

    private void reconcile() {
        try {
            State state = engine.reconcile(AndroidClock.now(this));
            store.setError("");
            render(state);
        } catch (RuntimeException failure) {
            fail(failure, false);
        }
    }

    private void render(State state) {
        handler.removeCallbacks(expiration);
        if (!state.mode.isMonitoring()) {
            isRunning = false;
            stopForeground(STOP_FOREGROUND_REMOVE);
            // Do not accidentally discard a later rapid-tap start command.
            stopSelfResult(latestStartId);
        } else {
            promote(state);
            if (state.mode.isSnoozed()) {
                long remaining = state.remainingMillis(AndroidClock.now(this));
                // Handler uses uptime. SCREEN_ON always recomputes elapsed time after sleep.
                handler.postDelayed(expiration, Math.max(1, remaining));
            }
        }
        store.signalUi();
    }

    private void promote(State state) {
        Notification notification = notification(state);
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        } else {
            startForeground(NOTIFICATION_ID, notification);
        }
    }

    private Notification notification(State state) {
        PendingIntent setup = PendingIntent.getActivity(this, 0,
                new Intent(this, MainActivity.class), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        PendingIntent disable = PendingIntent.getForegroundService(this, 1,
                new Intent(this, MonitorService.class).setAction(ServiceCommands.DISABLE),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder builder = new Notification.Builder(this, CHANNEL)
                .setSmallIcon(state.mode.isSnoozed() ? R.drawable.ic_snooze : R.drawable.ic_grayscale)
                .setContentTitle(getString(R.string.app_name))
                .setContentText(getString(state.mode.isSnoozed()
                        ? R.string.notification_snoozed : R.string.notification_enabled))
                .setContentIntent(setup)
                .setCategory(Notification.CATEGORY_SERVICE)
                .setOngoing(true).setOnlyAlertOnce(true).setShowWhen(false)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .addAction(new Notification.Action.Builder(null, getString(R.string.disable), disable).build());
        if (state.mode.isSnoozed()) {
            builder.setSubText(UiText.mode(this, state.mode))
                    .setWhen(System.currentTimeMillis() + state.remainingMillis(AndroidClock.now(this)))
                    .setShowWhen(true).setUsesChronometer(true).setChronometerCountDown(true);
        }
        return builder.build();
    }

    private void fail(RuntimeException failure, boolean userInitiated) {
        Log.e("GrayWake", "Monitoring stopped", failure);
        handler.removeCallbacks(expiration);
        isRunning = false;
        String message = GrayscaleSettings.hasPermission(this)
                ? getString(R.string.operation_failed) : getString(R.string.permission_missing);
        store.setError(message);
        store.signalUi();
        if (userInitiated) Toast.makeText(this, message, Toast.LENGTH_LONG).show();
        stopForeground(STOP_FOREGROUND_REMOVE);
        stopSelfResult(latestStartId);
    }

    @Override public void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        if (receiverRegistered) unregisterReceiver(screenReceiver);
        isRunning = false;
        if (store != null) store.signalUi();
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) { return null; }
}
