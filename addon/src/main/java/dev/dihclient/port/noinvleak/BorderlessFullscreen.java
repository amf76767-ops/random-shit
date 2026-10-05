package dev.dihclient.port.noinvleak;

import dev.dihclient.DIHClient;
import net.minecraft.class_1041;
import net.minecraft.class_310;

/** Ported from an open-source client (GPL-3.0). */
public final class BorderlessFullscreen {
    private BorderlessFullscreen() {
    }

    public interface Switchable {
        boolean dih$isBorderless();

        void dih$reapplyMode();
    }

    public static boolean wanted() {
        try {
            return Win32.isWindows() && NoInvLeakModule.wantsBorderless();
        } catch (Throwable t) {
            return false;
        }
    }

    public static void tick() {
        try {
            class_310 mc = class_310.method_1551();
            class_1041 window = mc == null ? null : mc.method_22683();
            if (window != null && window.method_4498() && (Object) window instanceof Switchable switchable && wanted() != switchable.dih$isBorderless()) {
                switchable.dih$reapplyMode();
            }
        } catch (Throwable t) {
            DIHClient.LOG.warn("[DIHClient] No Inv Leak: borderless fullscreen switch failed: {}", t.toString());
        }
    }
}
