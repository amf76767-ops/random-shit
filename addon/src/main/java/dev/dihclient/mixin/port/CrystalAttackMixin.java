package dev.dihclient.mixin.port;

import dev.dihclient.port.crystal.AttackHooks;
import net.minecraft.class_1297;
import net.minecraft.class_1657;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Tells the crystal modules about an attack of the local player (original: PlayerAttackEntityEvent). */
@Mixin(class_1657.class)
public abstract class CrystalAttackMixin {
    @Inject(method = "method_7324", at = @At("HEAD"))
    private void dih$crystalOnAttack(class_1297 target, CallbackInfo ci) {
        AttackHooks.fire(this, target);
    }
}
