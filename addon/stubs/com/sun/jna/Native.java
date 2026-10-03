package com.sun.jna;

/** Compile-time stand-in: JNA ships with Minecraft. */
public final class Native {
    private Native() {
    }

    public static <T extends Library> T load(String name, Class<T> type) {
        return null;
    }
}
