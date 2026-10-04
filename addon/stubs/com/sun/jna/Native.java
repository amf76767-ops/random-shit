package com.sun.jna;

/** Compile-time stand-in: JNA ships with Minecraft. */
public final class Native {
    public static final int POINTER_SIZE = 8;

    private Native() {
    }

    public static <T extends Library> T load(String name, Class<T> type) {
        return null;
    }
}
