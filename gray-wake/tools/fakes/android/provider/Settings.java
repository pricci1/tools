package android.provider;
import android.content.ContentResolver;
public final class Settings {
    public static final class Secure {
        public static String getString(ContentResolver resolver, String key) { return resolver.values.get(key); }
        public static boolean putString(ContentResolver resolver, String key, String value) {
            if (++resolver.calls == resolver.failureCall) {
                if (resolver.throwInstead) throw new SecurityException("Simulated provider failure");
                return false;
            }
            resolver.writes.add(key + "=" + value);
            if (value == null) resolver.values.remove(key); else resolver.values.put(key, value);
            return true;
        }
    }
}
