package dev.dihclient.mixin.port;

import dev.dihclient.port.tools.GhostBlocks;
import net.minecraft.class_1268;
import net.minecraft.class_1269;
import net.minecraft.class_2338;
import net.minecraft.class_2350;
import net.minecraft.class_3965;
import net.minecraft.class_636;
import net.minecraft.class_746;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(class_636.class)
public abstract class GhostBlockMixin {
    @Inject(method = "method_2896", at = @At("HEAD"), cancellable = true, require = 0)
    private void dih$ghostPlace(class_746 player, class_1268 hand, class_3965 hit, CallbackInfoReturnable<class_1269> cir) {
        if (hand == class_1268.field_5808 && GhostBlocks.onUse(hit)) {
            cir.setReturnValue(class_1269.field_5812);
        }
    }

    @Inject(method = "method_2910", at = @At("HEAD"), cancellable = true, require = 0)
    private void dih$ghostRemove(class_2338 pos, class_2350 side, CallbackInfoReturnable<Boolean> cir) {
        if (GhostBlocks.onAttack(pos)) {
            cir.setReturnValue(false);
        }
    }
}
