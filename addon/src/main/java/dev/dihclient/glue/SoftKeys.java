package dev.dihclient.glue;

import dev.dihclient.DIHClient;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Remembers which movement keys DIHClient's own code has pressed in software.
 * The game's key state can be rewritten by other mods before the player input is built (Multi Key Bindings,
 * Snappy Tappy, ...), so the automation modules would press "forward" and the player would still stand. This list is
 * not touched by anybody else.
 *
 * A press counts for {@link #HOLD_MS} after the last call that set it; the walking code sets it every tick, so a module
 * that is switched off without letting go cannot leave the player running.
 */
public final class SoftKeys {
    static final long HOLD_MS = 1000;
    private static final Map<Object, Long> UNTIL = Collections.synchronizedMap(new IdentityHashMap<>());
    private static long lastLog;

    private SoftKeys() {
    }

    public static void record(Object key, boolean pressed) {
        record(key, pressed, System.currentTimeMillis());
    }

    static void record(Object key, boolean pressed, long now) {
        if (pressed) {
            UNTIL.put(key, now + HOLD_MS);
        } else {
            UNTIL.remove(key);
        }
    }

    public static boolean active(Object key) {
        return active(key, System.currentTimeMillis());
    }

    static boolean active(Object key, long now) {
        Long until = UNTIL.get(key);
        if (until == null) {
            return false;
        }
        if (now > until) {
            UNTIL.remove(key);
            return false;
        }
        return true;
    }

    /** "Class.method" of the DIHClient code that is calling, or null when the caller is the game or another mod. */
    public static String dihCaller() {
        Optional<String> hit = StackWalker.getInstance().walk(frames -> frames.limit(10)
                .filter(f -> f.getClassName().startsWith("dev.dihclient.")
                        && !f.getClassName().startsWith("dev.dihclient.mixin.")
                        && !f.getClassName().equals(SoftKeys.class.getName()))
                .map(f -> f.getClassName().substring("dev.dihclient.".length()) + "." + f.getMethodName())
                .findFirst());
        return hit.orElse(null);
    }

    /** One log line every two seconds at most: shows which module presses a key. */
    public static void debug(String caller) {
        long now = System.currentTimeMillis();
        if (now - lastLog > 2000) {
            lastLog = now;
            DIHClient.LOG.info("[DIH-Debug] a module presses a key (software): {}", caller);
        }
    }
}
