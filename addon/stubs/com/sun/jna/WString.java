package com.sun.jna;

/** Compile-time stand-in: JNA ships with Minecraft. */
public final class WString implements CharSequence, Comparable<Object> {
    public WString(String s) {
    }

    @Override
    public int length() {
        return 0;
    }

    @Override
    public char charAt(int index) {
        return 0;
    }

    @Override
    public CharSequence subSequence(int start, int end) {
        return null;
    }

    @Override
    public int compareTo(Object o) {
        return 0;
    }
}
