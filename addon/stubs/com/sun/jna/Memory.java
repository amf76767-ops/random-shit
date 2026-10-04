package com.sun.jna;

/** Compile-time stand-in: JNA ships with Minecraft. */
public class Memory extends Pointer {
    public Memory(long size) {
        super(0);
    }

    public void clear() {
    }
}
