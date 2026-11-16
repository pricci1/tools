package dev.graywake.core;

import java.util.Objects;

/** Single-threaded coordinator. Android invokes it only on its main thread. */
public final class Engine {
    public interface Store {
        State load();
        void save(State state);
    }
    public interface Display {
        /** Apply the setting or throw. Implementations must check platform writes. */
        void setGrayscale(boolean enabled);
    }

    private final Store store;
    private final Display display;

    public Engine(Store store, Display display) {
        this.store = Objects.requireNonNull(store);
        this.display = Objects.requireNonNull(display);
    }

    public State advance(TimePoint now) {
        State previous = store.load();
        return applyAndSave(previous, previous.next(now), true);
    }

    /** Runs at service start, unlock, time changes, and timer expiry. */
    public State reconcile(TimePoint now) {
        State previous = store.load();
        State next = previous.resolve(now);
        // Merely opening the app while disabled must not alter accessibility settings.
        return applyAndSave(previous, next, next.mode.isMonitoring());
    }

    /** Only an actual screen-on event ends the untimed wake snooze. */
    public State onScreenWake(TimePoint now) {
        State previous = store.load();
        State next = previous.mode == Mode.SNOOZE_UNTIL_WAKE ? State.enabled() : previous.resolve(now);
        return applyAndSave(previous, next, next.mode.isMonitoring());
    }

    public State disable() { return applyAndSave(store.load(), State.disabled(), true); }

    private State applyAndSave(State previous, State next, boolean apply) {
        if (apply) display.setGrayscale(next.mode.isGrayscale());
        if (!next.equals(previous)) {
            try {
                store.save(next);
            } catch (RuntimeException failure) {
                // Best effort: do not leave the display in a state we failed to persist.
                if (apply) {
                    try { display.setGrayscale(previous.mode.isGrayscale()); }
                    catch (RuntimeException rollback) { failure.addSuppressed(rollback); }
                }
                throw failure;
            }
        }
        return next;
    }
}
