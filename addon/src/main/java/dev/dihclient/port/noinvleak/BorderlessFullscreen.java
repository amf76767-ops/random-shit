package dev.dihclient.port.noinvleak;

import dev.dihclient.DIHClient;
import net.minecraft.class_1041;
import net.minecraft.class_310;

/**
 * Ported from Anubis Client 0.9.8 (GPL-3.0).
 * <p>
 * Exclusive fullscreen hides every other window, including the overlay windows. While Stream Only wants borderless, the
 * game's fullscreen is a borderless window of the monitor's size instead ({@code BorderlessWindowMixin} does the switch).
 */
public final class BorderlessFullscreen {
    private BorderlessFullscreen() {
    }

    /** Implemented by the window mixin. */
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

    /** Render thread: asks the window to re-apply its mode when the wish and the state differ. */
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
