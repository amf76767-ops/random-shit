package com.sun.jna;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Compile-time stand-in: JNA ships with Minecraft. */
public abstract class Structure {
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.TYPE)
    public @interface FieldOrder {
        String[] value();
    }

    protected Structure() {
    }

    public int size() {
        return 0;
    }

    public void write() {
    }

    public void read() {
    }

    public Pointer getPointer() {
        return null;
    }
}
