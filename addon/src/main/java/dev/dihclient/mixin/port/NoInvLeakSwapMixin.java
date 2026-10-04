package dev.dihclient.mixin.port;

import dev.dihclient.DIHClient;
import dev.dihclient.port.noinvleak.StreamOverlay;
import net.minecraft.class_1041;
import net.minecraft.class_10219;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Ported from Anubis Client 0.9.8 (GPL-3.0) (MixinGameRenderer, the StreamOverlay part).
 * Runs the No Inv Leak overlay once per frame, right before the window's buffers are swapped.
 */
@Mixin(class_1041.class)
public abstract class NoInvLeakSwapMixin {
    @Unique
    private static boolean dih$broken;

    @Inject(method = "method_15998", at = @At("HEAD"))
    private void dih$beforeSwap(class_10219 capture, CallbackInfo ci) {
        if (dih$broken) {
            return;
        }
        try {
            StreamOverlay.endFrame();
            StreamOverlay.drawFakes();
        } catch (Throwable t) {
            dih$broken = true;
            DIHClient.LOG.warn("[DIHClient] No Inv Leak: frame hook failed, switched off until restart", t);
        }
    }
}
