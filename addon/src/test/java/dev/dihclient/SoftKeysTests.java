package dev.dihclient;

import dev.dihclient.glue.SoftKeys;
import java.lang.reflect.Method;

public final class SoftKeysTests {
    static int failed;
    static int passed;

    static void check(boolean ok, String what) {
        if (ok) {
            passed++;
        } else {
            failed++;
            System.out.println("FAIL: " + what);
        }
    }

    public static void main(String[] args) throws Exception {
        Object key = new Object();
        Object other = new Object();
        Method record = SoftKeys.class.getDeclaredMethod("record", Object.class, boolean.class, long.class);
        Method active = SoftKeys.class.getDeclaredMethod("active", Object.class, long.class);
        record.setAccessible(true);
        active.setAccessible(true);
        record.invoke(null, key, true, 1000L);
        check((boolean) active.invoke(null, key, 1500L), "pressed key is active");
        check(!(boolean) active.invoke(null, other, 1500L), "other key is not");
        check((boolean) active.invoke(null, key, 2000L), "still active at the limit");
        check(!(boolean) active.invoke(null, key, 2001L), "expires after one second without a new press");
        record.invoke(null, key, true, 5000L);
        record.invoke(null, key, true, 5900L);
        check((boolean) active.invoke(null, key, 6800L), "a new press renews it");
        record.invoke(null, key, false, 6000L);
        check(!(boolean) active.invoke(null, key, 6001L), "release ends it at once");
        String caller = SoftKeys.dihCaller();
        check(caller != null && caller.startsWith("SoftKeysTests."), "caller is found: " + caller);
        System.out.println(passed + " passed, " + failed + " failed");
        System.exit(failed == 0 ? 0 : 1);
    }
}
