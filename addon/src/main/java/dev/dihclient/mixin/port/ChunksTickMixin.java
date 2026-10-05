package dev.dihclient.mixin.port;

import dev.dihclient.port.chunks.PlayerBypassLightTracker;
import net.minecraft.class_310;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(class_310.class)
public abstract class ChunksTickMixin {
    @Inject(method = "method_1574", at = @At("HEAD"))
    private void dih$bypassTick(CallbackInfo ci) {
        PlayerBypassLightTracker.onTick();
    }
}
