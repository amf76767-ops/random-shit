package dev.dihclient.mixin.port;

import dev.dihclient.port.trident.TridentHooks;
import dev.dihclient.port.trident.TridentLogic;
import net.minecraft.class_1657;
import net.minecraft.class_1835;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Trident item (use = method_7836, release = method_7840) for Trident Util and Trident Boost: the "in water or rain" test,
 * the 10 tick minimum charge and the riptide launch push. One mixin on purpose, a call can only be redirected once.
 * require = 0: if the game changes, the module does nothing instead of stopping the game from starting.
 */
@Mixin(class_1835.class)
public abstract class TridentRiptideMixin {
    @Redirect(method = {"method_7836", "method_7840"}, at = @At(value = "INVOKE", target = "Lnet/minecraft/class_1657;method_5721()Z"), require = 0)
    private boolean dih$tridentWet(class_1657 player) {
        return player.method_5721() || TridentHooks.allowOutOfWater();
    }

    @ModifyConstant(method = "method_7840", constant = @Constant(intValue = TridentLogic.VANILLA_CHARGE), require = 0)
    private int dih$tridentCharge(int vanilla) {
        return TridentHooks.minChargeTicks(vanilla);
    }

    @Redirect(method = "method_7840", at = @At(value = "INVOKE", target = "Lnet/minecraft/class_1657;method_5762(DDD)V"), require = 0)
    private void dih$tridentPush(class_1657 player, double x, double y, double z) {
        double m = TridentHooks.riptideMultiplier();
        player.method_5762(TridentLogic.boosted(x, m), TridentLogic.boosted(y, m), TridentLogic.boosted(z, m));
    }
}
