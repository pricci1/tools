package dev.graywake.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Dependency-free tests: run with tools/test-core.sh, or Gradle :app:coreTest. */
public final class CoreTests {
    private static int assertions;
    private static int cases;
    private static final long WALL = 1_800_000_000_000L;
    private static final TimePoint START = new TimePoint(50_000, WALL, 10);

    public static void main(String[] args) {
        test("full cycle and wrap", CoreTests::fullCycle);
        test("all preset durations", CoreTests::durations);
        test("each tap replaces, not extends, the timer", CoreTests::resetDeadline);
        test("expiration boundary", CoreTests::expiration);
        test("tap after expiration selects wake snooze, then fresh 5m snooze", CoreTests::tapAfterExpiry);
        test("deep sleep counts toward expiry", CoreTests::sleep);
        test("wall clock and timezone changes within one boot", CoreTests::wallChanges);
        test("reboot rebases remaining duration", CoreTests::reboot);
        test("reboot after expiration", CoreTests::expiredReboot);
        test("backward clock across reboot is bounded", CoreTests::rebootClockRollback);
        test("missing boot identity uses bounded wall time", CoreTests::unknownBoot);
        test("corrupt saved state fails safe", CoreTests::corruptState);
        test("state equality and hashing", CoreTests::equality);
        test("disabled reconciliation has no side effects", CoreTests::disabledNoWrites);
        test("wake re-applies grayscale", CoreTests::wake);
        test("wake snooze survives time and recreation, ends only on screen wake", CoreTests::untilWake);
        test("snooze restores color and then resumes", CoreTests::snooze);
        test("explicit disable restores color", CoreTests::disable);
        test("failed display write does not advance state", CoreTests::displayFailure);
        test("failed persistence rolls back display", CoreTests::persistenceFailure);
        test("rollback failure is retained", CoreTests::rollbackFailure);
        test("process recreation preserves snooze", CoreTests::processRecreation);
        test("repeated disable stays disabled", CoreTests::disableIdempotence);
        test("rapid taps preserve ordering", CoreTests::rapidTaps);
        test("randomized transition invariants", CoreTests::randomized);
        System.out.println("PASS: " + cases + " cases, " + assertions + " assertions");
    }

    private static void fullCycle() {
        State s = State.disabled();
        Mode[] order = {Mode.ENABLED, Mode.SNOOZE_UNTIL_WAKE, Mode.SNOOZE_5, Mode.SNOOZE_15, Mode.SNOOZE_30,
                Mode.SNOOZE_45, Mode.SNOOZE_60, Mode.SNOOZE_12H, Mode.DISABLED, Mode.ENABLED};
        for (Mode mode : order) { s = s.next(START); equal(mode, s.mode); }
    }
    private static void durations() {
        long[] minutes = {0, 0, 0, 5, 15, 30, 45, 60, 720};
        for (Mode m : Mode.values()) {
            State s = State.select(m, START);
            equal(minutes[m.ordinal()] * 60_000L, s.remainingMillis(START));
            equal(m != Mode.DISABLED, m.isMonitoring());
            equal(m == Mode.ENABLED, m.isGrayscale());
        }
    }
    private static void resetDeadline() {
        State s = State.select(Mode.SNOOZE_5, START);
        TimePoint later = later(START, 60_000);
        s = s.next(later);
        equal(Mode.SNOOZE_15, s.mode);
        equal(900_000L, s.remainingMillis(later));
        equal(later.elapsedMillis + 900_000, s.deadlineElapsedMillis);
    }
    private static void expiration() {
        for (Mode m : Mode.values()) if (m.isTimedSnooze()) {
            State s = State.select(m, START);
            equal(m, s.resolve(later(START, m.durationMillis - 1)).mode);
            equal(Mode.ENABLED, s.resolve(later(START, m.durationMillis)).mode);
            equal(Mode.ENABLED, s.resolve(later(START, m.durationMillis + 1)).mode);
        }
    }
    private static void tapAfterExpiry() {
        for (Mode m : Mode.values()) if (m.isTimedSnooze()) {
            TimePoint t = later(START, m.durationMillis);
            State s = State.select(m, START).next(t);
            equal(Mode.SNOOZE_UNTIL_WAKE, s.mode);
            s = s.next(t);
            equal(Mode.SNOOZE_5, s.mode);
            equal(300_000L, s.remainingMillis(t));
        }
    }
    private static void sleep() {
        equal(Mode.ENABLED, State.select(Mode.SNOOZE_5, START)
                .resolve(later(START, 3_600_000)).mode);
    }
    private static void wallChanges() {
        State s = State.select(Mode.SNOOZE_15, START);
        for (long jump : new long[]{-86_400_000L, 86_400_000L, 0, 3_600_000L}) {
            TimePoint t = new TimePoint(START.elapsedMillis + 120_000, WALL + jump, 10);
            equal(780_000L, s.remainingMillis(t));
            State r = s.resolve(t);
            equal(780_000L, r.remainingMillis(t));
            equal(t.wallMillis + 780_000, r.deadlineWallMillis);
        }
    }
    private static void reboot() {
        State s = State.select(Mode.SNOOZE_15, START);
        TimePoint t = new TimePoint(5_000, WALL + 180_000, 11);
        State r = s.resolve(t);
        equal(Mode.SNOOZE_15, r.mode);
        equal(720_000L, r.remainingMillis(t));
        equal(725_000L, r.deadlineElapsedMillis);
        equal(11, r.bootCount);
        equal(660_000L, r.remainingMillis(later(t, 60_000)));
    }
    private static void expiredReboot() {
        equal(Mode.ENABLED, State.select(Mode.SNOOZE_5, START)
                .resolve(new TimePoint(2_000, WALL + 600_000, 11)).mode);
    }
    private static void rebootClockRollback() {
        State s = State.select(Mode.SNOOZE_5, START);
        TimePoint t = new TimePoint(2_000, WALL - 86_400_000L, 11);
        equal(300_000L, s.resolve(t).remainingMillis(t));
    }
    private static void unknownBoot() {
        State s = State.select(Mode.SNOOZE_5, new TimePoint(0, WALL, -1));
        equal(240_000L, s.remainingMillis(new TimePoint(0, WALL + 60_000, -1)));
        equal(Mode.ENABLED, s.resolve(new TimePoint(0, WALL + 300_000, -1)).mode);
    }
    private static void corruptState() {
        equal(Mode.DISABLED, State.restore("nope", 0, 0, -1).mode);
        equal(Mode.DISABLED, Mode.parse(null));
        equal(Mode.ENABLED, State.restore("SNOOZE_5", 0, WALL, 10).mode);
        equal(Mode.ENABLED, State.restore("SNOOZE_5", 50, -1, 10).mode);
        equal(0L, State.restore("ENABLED", 123, 456, 10).deadlineElapsedMillis);
        boolean threw = false;
        try { new TimePoint(-1, WALL, 1); } catch (IllegalArgumentException e) { threw = true; }
        check(threw);
    }
    private static void equality() {
        State s = State.select(Mode.SNOOZE_30, START);
        State copy = State.restore(s.mode.name(), s.deadlineElapsedMillis,
                s.deadlineWallMillis, s.bootCount);
        equal(s, copy); equal(s.hashCode(), copy.hashCode());
        check(!s.equals(State.disabled())); check(!s.equals(null));
        equal(s, s.resolve(START));
    }
    private static void disabledNoWrites() {
        Fixture f = new Fixture();
        f.engine.reconcile(START);
        f.engine.onScreenWake(START);
        equal(0, f.writes.size()); equal(0, f.saves);
    }
    private static void wake() {
        Fixture f = new Fixture(); f.engine.advance(START);
        f.engine.onScreenWake(later(START, 10)); f.engine.onScreenWake(later(START, 20));
        equal(List.of(true, true, true), f.writes); equal(1, f.saves);
    }
    private static void untilWake() {
        Fixture f = new Fixture();
        equal(Mode.ENABLED, f.engine.advance(START).mode);
        State snoozed = f.engine.advance(START);
        equal(Mode.SNOOZE_UNTIL_WAKE, snoozed.mode);
        equal(false, f.writes.get(f.writes.size() - 1));
        check(snoozed.mode.isSnoozed());
        check(snoozed.mode.isMonitoring());
        check(!snoozed.mode.isTimedSnooze());
        equal(0L, snoozed.deadlineElapsedMillis);
        equal(0L, snoozed.deadlineWallMillis);
        TimePoint muchLater = later(START, 7 * 86_400_000L);
        equal(snoozed, f.engine.reconcile(muchLater));
        equal(false, f.writes.get(f.writes.size() - 1));
        f.state = State.restore(snoozed.mode.name(), snoozed.deadlineElapsedMillis,
                snoozed.deadlineWallMillis, snoozed.bootCount);
        f.engine = new Engine(f, f);
        equal(snoozed, f.engine.reconcile(muchLater));
        equal(Mode.ENABLED, f.engine.onScreenWake(muchLater).mode);
        equal(true, f.writes.get(f.writes.size() - 1));
        equal(Mode.SNOOZE_UNTIL_WAKE, f.engine.advance(muchLater).mode);
        State timed = f.engine.advance(later(muchLater, 2_000));
        equal(Mode.SNOOZE_5, timed.mode);
        equal(300_000L, timed.remainingMillis(later(muchLater, 2_000)));
    }
    private static void snooze() {
        Fixture f = new Fixture();
        f.engine.advance(START); f.engine.advance(START); f.engine.advance(START);
        equal(false, f.writes.get(2));
        equal(Mode.SNOOZE_5, f.engine.onScreenWake(later(START, 299_999)).mode);
        equal(false, f.writes.get(3));
        equal(Mode.ENABLED, f.engine.onScreenWake(later(START, 300_000)).mode);
        equal(true, f.writes.get(4));
    }
    private static void disable() {
        Fixture f = new Fixture(); f.engine.advance(START); f.engine.disable();
        equal(Mode.DISABLED, f.state.mode); equal(List.of(true, false), f.writes);
        f.engine.reconcile(later(START, 60_000)); equal(2, f.writes.size());
    }
    private static void displayFailure() {
        Fixture f = new Fixture(); f.failDisplay = true;
        expectFailure(() -> f.engine.advance(START));
        equal(Mode.DISABLED, f.state.mode); equal(0, f.saves);
    }
    private static void persistenceFailure() {
        Fixture f = new Fixture(); f.failSave = true;
        expectFailure(() -> f.engine.advance(START));
        equal(Mode.DISABLED, f.state.mode); equal(List.of(true, false), f.writes);
    }
    private static void rollbackFailure() {
        Fixture f = new Fixture(); f.failSave = true; f.failRollback = true;
        RuntimeException e = expectFailure(() -> f.engine.advance(START));
        equal(1, e.getSuppressed().length);
    }
    private static void processRecreation() {
        Fixture f = new Fixture();
        f.engine.advance(START); f.engine.advance(START); f.engine.advance(START);
        f.engine = new Engine(f, f);
        State s = f.engine.reconcile(later(START, 180_000));
        equal(Mode.SNOOZE_5, s.mode); equal(120_000L, s.remainingMillis(later(START, 180_000)));
    }
    private static void disableIdempotence() {
        Fixture f = new Fixture(); f.engine.disable(); f.engine.disable();
        equal(0, f.saves); equal(List.of(false, false), f.writes);
    }
    private static void rapidTaps() {
        Fixture f = new Fixture();
        for (int i = 0; i < 90; i++) {
            f.engine.advance(later(START, i));
            equal(Mode.values()[(i + 1) % 9], f.state.mode);
        }
        equal(90, f.writes.size());
    }
    private static void randomized() {
        Random random = new Random(0x47524159);
        State state = State.disabled();
        TimePoint now = START;
        for (int i = 0; i < 5_000; i++) {
            now = later(now, random.nextInt(300_001));
            if (random.nextBoolean()) state = state.next(now);
            else state = state.resolve(now);
            long remaining = state.remainingMillis(now);
            check(remaining >= 0 && remaining <= state.mode.durationMillis);
            equal(state, state.resolve(now));
            if (state.mode.isTimedSnooze()) check(remaining > 0);
            else equal(0L, remaining);
        }
    }

    private static TimePoint later(TimePoint t, long delay) {
        return new TimePoint(t.elapsedMillis + delay, t.wallMillis + delay, t.bootCount);
    }
    private static void test(String name, Runnable test) {
        try { test.run(); cases++; System.out.println("OK  " + name); }
        catch (AssertionError e) { throw new AssertionError(name + ": " + e.getMessage(), e); }
    }
    private static void check(boolean condition) {
        assertions++; if (!condition) throw new AssertionError("Condition failed");
    }
    private static void equal(Object expected, Object actual) {
        assertions++;
        if (!expected.equals(actual)) throw new AssertionError("expected " + expected + ", got " + actual);
    }
    private static RuntimeException expectFailure(Runnable operation) {
        assertions++;
        try { operation.run(); } catch (RuntimeException e) { return e; }
        throw new AssertionError("Expected failure");
    }
    private static final class Fixture implements Engine.Store, Engine.Display {
        State state = State.disabled();
        Engine engine = new Engine(this, this);
        final List<Boolean> writes = new ArrayList<>();
        int saves;
        boolean failDisplay, failSave, failRollback;
        @Override public State load() { return state; }
        @Override public void save(State next) {
            if (failSave) throw new IllegalStateException("disk failure");
            state = next; saves++;
        }
        @Override public void setGrayscale(boolean enabled) {
            if (failDisplay || (failRollback && !enabled)) throw new SecurityException("write denied");
            writes.add(enabled);
        }
    }
}
