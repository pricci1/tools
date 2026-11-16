package dev.graywake.core;

import java.util.Objects;

/** Immutable, Android-free policy. Deadlines survive service/process recreation. */
public final class State {
    public final Mode mode;
    public final long deadlineElapsedMillis;
    public final long deadlineWallMillis;
    public final int bootCount;

    private State(Mode mode, long elapsed, long wall, int bootCount) {
        this.mode = Objects.requireNonNull(mode);
        this.deadlineElapsedMillis = elapsed;
        this.deadlineWallMillis = wall;
        this.bootCount = bootCount;
    }

    public static State disabled() { return plain(Mode.DISABLED); }
    public static State enabled() { return plain(Mode.ENABLED); }
    private static State plain(Mode mode) { return new State(mode, 0, 0, -1); }

    public static State select(Mode mode, TimePoint now) {
        if (!mode.isTimedSnooze()) return plain(mode);
        return new State(mode, now.elapsedMillis + mode.durationMillis,
                now.wallMillis + mode.durationMillis, now.bootCount);
    }

    public static State restore(String name, long elapsed, long wall, int bootCount) {
        Mode mode = Mode.parse(name);
        if (!mode.isTimedSnooze()) return plain(mode);
        // Broken saved deadlines must not leave grayscale snoozed forever.
        if (elapsed <= 0 || wall <= 0) return enabled();
        return new State(mode, elapsed, wall, bootCount);
    }

    public long remainingMillis(TimePoint now) {
        if (!mode.isTimedSnooze()) return 0;
        boolean sameBoot = bootCount >= 0 && now.bootCount == bootCount;
        long remaining = sameBoot
                ? deadlineElapsedMillis - now.elapsedMillis
                : deadlineWallMillis - now.wallMillis;
        // A backward wall-clock change across reboot cannot extend beyond the preset.
        return Math.max(0, Math.min(remaining, mode.durationMillis));
    }

    public State resolve(TimePoint now) {
        if (!mode.isTimedSnooze()) return this;
        long remaining = remainingMillis(now);
        if (remaining == 0) return enabled();
        // Rebase elapsed time after reboot and keep a fresh wall-clock fallback.
        return new State(mode, now.elapsedMillis + remaining,
                now.wallMillis + remaining, now.bootCount);
    }

    public State next(TimePoint now) {
        return select(resolve(now).mode.next(), now);
    }

    @Override public boolean equals(Object other) {
        if (!(other instanceof State)) return false;
        State s = (State) other;
        return mode == s.mode && deadlineElapsedMillis == s.deadlineElapsedMillis
                && deadlineWallMillis == s.deadlineWallMillis && bootCount == s.bootCount;
    }

    @Override public int hashCode() {
        return Objects.hash(mode, deadlineElapsedMillis, deadlineWallMillis, bootCount);
    }

    @Override public String toString() { return mode.name(); }
}
