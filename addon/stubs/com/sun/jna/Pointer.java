package com.sun.jna;

import java.nio.ByteBuffer;

/** Compile-time stand-in: JNA ships with Minecraft. */
public class Pointer {
    public static final Pointer NULL = null;

    public Pointer(long peer) {
    }

    public static Pointer createConstant(long peer) {
        return null;
    }

    public static long nativeValue(Pointer p) {
        return 0;
    }

    public ByteBuffer getByteBuffer(long offset, long length) {
        return null;
    }

    public void setMemory(long offset, long length, byte value) {
    }
}
