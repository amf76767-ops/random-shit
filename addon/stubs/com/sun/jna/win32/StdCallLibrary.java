package com.sun.jna.win32;

import com.sun.jna.Callback;
import com.sun.jna.Library;

/** Compile-time stand-in: JNA ships with Minecraft. */
public interface StdCallLibrary extends Library {
    interface StdCallCallback extends Callback {
    }
}
