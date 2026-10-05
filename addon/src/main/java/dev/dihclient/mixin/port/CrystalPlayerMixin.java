package dev.dihclient.mixin.port;

import dev.dihclient.DIHClient;
import dev.dihclient.port.crystal.ServerRotation;
import net.minecraft.class_746;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(class_746.class)
public abstract class CrystalPlayerMixin {
    @Inject(method = "method_5773", at = @At("HEAD"))
    private void dih$crystalWalk(CallbackInfo ci) {
        try {
            ServerRotation.applyForMovement((class_746) (Object) this);
        } catch (Throwable t) {
            DIHClient.LOG.warn("[DIHClient] silent rotation failed", t);
        }
    }

    @Inject(method = "method_5773", at = @At("RETURN"))
    private void dih$crystalFinishTick(CallbackInfo ci) {
        try {
            ServerRotation.finishTick((class_746) (Object) this);
        } catch (Throwable t) {
            DIHClient.LOG.warn("[DIHClient] silent rotation failed", t);
        }
    }

    @Inject(method = "method_3136", at = @At("HEAD"))
    private void dih$crystalSwapIn(CallbackInfo ci) {
        try {
            ServerRotation.beforeSend((class_746) (Object) this);
        } catch (Throwable t) {
            DIHClient.LOG.warn("[DIHClient] silent rotation failed", t);
        }
    }

    @Inject(method = "method_3136", at = @At("RETURN"))
    private void dih$crystalSwapOut(CallbackInfo ci) {
        try {
            ServerRotation.afterSend((class_746) (Object) this);
        } catch (Throwable t) {
            DIHClient.LOG.warn("[DIHClient] silent rotation failed", t);
        }
    }
}
