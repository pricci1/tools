package dev.graywake;

import android.content.Context;
import dev.graywake.core.Mode;
import dev.graywake.core.State;
import dev.graywake.core.TimePoint;

final class UiText {
    private UiText() {}

    static String mode(Context context, Mode mode) {
        switch (mode) {
            case DISABLED: return context.getString(R.string.mode_disabled);
            case ENABLED: return context.getString(R.string.mode_enabled);
            case SNOOZE_12H: return context.getString(R.string.mode_snooze_hours, 12);
            default: return context.getString(R.string.mode_snooze_minutes, mode.durationMillis / 60_000);
        }
    }

    static String description(Context context, State state, TimePoint now) {
        if (!state.mode.isSnoozed()) return mode(context, state.mode);
        long minutes = (state.remainingMillis(now) + 59_999) / 60_000;
        return context.getString(R.string.snooze_remaining, mode(context, state.mode), minutes);
    }
}
