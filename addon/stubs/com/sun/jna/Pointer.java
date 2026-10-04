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

    public Pointer getPointer(long offset) {
        return null;
    }

    public char[] getCharArray(long offset, int length) {
        return null;
    }

    public void write(long offset, byte[] buf, int index, int length) {
    }

    public void setShort(long offset, short value) {
    }

    public void setInt(long offset, int value) {
    }

    public void setLong(long offset, long value) {
    }
}
