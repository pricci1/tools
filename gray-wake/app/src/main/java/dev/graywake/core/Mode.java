package dev.graywake.core;

/** Order is the UI contract. Persist names, not ordinals. */
public enum Mode {
    DISABLED(0), ENABLED(0), SNOOZE_5(5), SNOOZE_15(15), SNOOZE_30(30),
    SNOOZE_45(45), SNOOZE_60(60), SNOOZE_12H(12 * 60);

    public final long durationMillis;

    Mode(long minutes) { durationMillis = minutes * 60_000L; }
    public boolean isSnoozed() { return durationMillis > 0; }
    public boolean isMonitoring() { return this != DISABLED; }
    public boolean isGrayscale() { return this == ENABLED; }
    public Mode next() { return values()[(ordinal() + 1) % values().length]; }

    public static Mode parse(String value) {
        if (value == null) return DISABLED;
        try { return valueOf(value); }
        catch (IllegalArgumentException ignored) { return DISABLED; }
    }
}
