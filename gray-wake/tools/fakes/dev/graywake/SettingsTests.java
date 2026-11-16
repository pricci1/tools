package dev.graywake;

import android.content.Context;
import java.util.List;
import java.util.Objects;

/** Tests production adapter logic against fake provider responses, NOT Android itself. */
public final class SettingsTests {
    private static int cases;
    private static int assertions;
    private static final String MODE = GrayscaleSettings.MODE_KEY;
    private static final String ENABLED = GrayscaleSettings.ENABLED_KEY;

    public static void main(String[] args) {
        test("enable selects monochromacy before enabling correction", () -> {
            Context c = context("12", "0");
            new GrayscaleSettings(c).setGrayscale(true);
            equal(List.of(MODE + "=0", ENABLED + "=1"), c.resolver.writes);
        });
        test("disable turns correction off without rewriting the mode", () -> {
            Context c = context("0", "1");
            new GrayscaleSettings(c).setGrayscale(false);
            equal(List.of(ENABLED + "=0"), c.resolver.writes);
            equal("0", c.resolver.values.get(MODE));
        });
        test("already-correct settings need no provider writes", () -> {
            Context c = context("0", "1");
            new GrayscaleSettings(c).setGrayscale(true);
            equal(0, c.resolver.calls);
        });
        test("wake repairs an external mode change", () -> {
            Context c = context("12", "1");
            new GrayscaleSettings(c).setGrayscale(true);
            equal(List.of(MODE + "=0"), c.resolver.writes);
        });
        test("permission denial writes nothing", () -> {
            Context c = context("12", "0"); c.granted = false;
            fails(() -> new GrayscaleSettings(c).setGrayscale(true));
            equal(0, c.resolver.calls);
        });
        test("first provider write failure preserves settings", () -> {
            Context c = context("12", "0"); c.resolver.failureCall = 1;
            fails(() -> new GrayscaleSettings(c).setGrayscale(true));
            equal("12", c.resolver.values.get(MODE)); equal("0", c.resolver.values.get(ENABLED));
        });
        test("second write failure rolls back the first write", () -> {
            Context c = context("12", "0"); c.resolver.failureCall = 2;
            fails(() -> new GrayscaleSettings(c).setGrayscale(true));
            equal("12", c.resolver.values.get(MODE)); equal("0", c.resolver.values.get(ENABLED));
        });
        test("permission race also rolls back the first write", () -> {
            Context c = context("13", "0"); c.resolver.failureCall = 2; c.resolver.throwInstead = true;
            fails(() -> new GrayscaleSettings(c).setGrayscale(true));
            equal("13", c.resolver.values.get(MODE));
        });
        test("rollback preserves an originally absent setting", () -> {
            Context c = new Context(); c.resolver.failureCall = 2;
            fails(() -> new GrayscaleSettings(c).setGrayscale(true));
            equal(null, c.resolver.values.get(MODE)); equal(null, c.resolver.values.get(ENABLED));
        });
        test("failed disable is not reported as success", () -> {
            Context c = context("0", "1"); c.resolver.failureCall = 1;
            fails(() -> new GrayscaleSettings(c).setGrayscale(false));
            equal("1", c.resolver.values.get(ENABLED));
        });
        System.out.println("PASS: " + cases + " adapter cases, " + assertions + " assertions (fake provider)");
    }
    private static Context context(String mode, String enabled) {
        Context c = new Context(); c.resolver.values.put(MODE, mode); c.resolver.values.put(ENABLED, enabled);
        return c;
    }
    private static void test(String name, Runnable test) {
        test.run(); cases++; System.out.println("OK  " + name);
    }
    private static void equal(Object expected, Object actual) {
        assertions++;
        if (!Objects.equals(expected, actual)) throw new AssertionError("Expected " + expected + ", got " + actual);
    }
    private static void fails(Runnable call) {
        assertions++;
        try { call.run(); } catch (RuntimeException expected) { return; }
        throw new AssertionError("Expected adapter to reject the write");
    }
}
