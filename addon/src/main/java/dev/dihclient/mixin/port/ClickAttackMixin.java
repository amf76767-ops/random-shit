package dev.dihclient.mixin.port;

import dev.dihclient.port.tools.ClickAttack;
import dev.dihclient.port.tools.NoGhostBlocks;
import net.minecraft.class_1297;
import net.minecraft.class_1657;
import net.minecraft.class_2338;
import net.minecraft.class_636;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(class_636.class)
public abstract class ClickAttackMixin {
    @Inject(method = "method_2918", at = @At("HEAD"), cancellable = true, require = 0)
    private void dih$click(class_1657 player, class_1297 target, CallbackInfo ci) {
        if (ClickAttack.redirect(player, target)) {
            ci.cancel();
        }
    }

    @Inject(method = "method_2899", at = @At("HEAD"), cancellable = true, require = 0)
    private void dih$ghost(class_2338 pos, CallbackInfoReturnable<Boolean> cir) {
        NoGhostBlocks.broke(pos);
        if (NoGhostBlocks.holdsBreak()) {
            cir.setReturnValue(true);
        }
    }
}
