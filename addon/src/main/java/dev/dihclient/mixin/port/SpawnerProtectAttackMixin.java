package dev.dihclient.mixin.port;

import dev.dihclient.port.tools.SpawnerProtect;
import net.minecraft.class_310;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** While Spawner Protect works, the attack button of the player does nothing (the module breaks blocks itself). */
@Mixin(class_310.class)
public abstract class SpawnerProtectAttackMixin {
    @Inject(method = "method_1590", at = @At("HEAD"), cancellable = true)
    private void dih$spawnerProtectContinueAttack(boolean down, CallbackInfo ci) {
        SpawnerProtect module = SpawnerProtect.active;
        if (module != null && module.blocksVanillaAttack()) {
            ci.cancel();
        }
    }

    @Inject(method = "method_1536", at = @At("HEAD"), cancellable = true)
    private void dih$spawnerProtectStartAttack(CallbackInfoReturnable<Boolean> cir) {
        SpawnerProtect module = SpawnerProtect.active;
        if (module != null && module.blocksVanillaAttack()) {
            cir.setReturnValue(false);
        }
    }
}
