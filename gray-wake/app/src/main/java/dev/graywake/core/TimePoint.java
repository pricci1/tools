package dev.graywake.core;

/** A single clock sample; elapsedMillis includes time spent in deep sleep. */
public final class TimePoint {
    public final long elapsedMillis;
    public final long wallMillis;
    public final int bootCount;

    public TimePoint(long elapsedMillis, long wallMillis, int bootCount) {
        if (elapsedMillis < 0) throw new IllegalArgumentException("Negative elapsed time");
        this.elapsedMillis = elapsedMillis;
        this.wallMillis = wallMillis;
        this.bootCount = bootCount;
    }
}
