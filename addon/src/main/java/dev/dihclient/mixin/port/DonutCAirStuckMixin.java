package dev.dihclient.mixin.port;

import dev.dihclient.port.donutc.AirStuck;
import net.minecraft.class_746;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Ported from an open-source client (GPL-3.0). */
@Mixin(class_746.class)
public abstract class DonutCAirStuckMixin {
    @Inject(method = "method_6007", at = @At("HEAD"), cancellable = true)
    private void dih$airStuck(CallbackInfo ci) {
        if (AirStuck.active != null) {
            ci.cancel();
        }
    }
}
