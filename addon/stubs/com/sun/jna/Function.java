package com.sun.jna;

/** Compile-time stand-in: JNA ships with Minecraft. */
public class Function extends Pointer {
    private Function() {
        super(0);
    }

    public static Function getFunction(Pointer function, int callFlags) {
        return null;
    }

    public int invokeInt(Object[] args) {
        return 0;
    }
}
