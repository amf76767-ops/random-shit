package dev.dihclient.mixin;

import dev.dihclient.modules.misc.DummyPlayer;
import net.minecraft.class_310;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin({class_310.class})
public abstract class DummyHitMixin {
    @Inject(
        method = {"method_1536"},
        at = {@At("HEAD")}
    )
    private void dih$dummyHit(CallbackInfoReturnable<Boolean> cir) {
        dev.dihclient.modules.render.TargetHud.onSwing();
        DummyPlayer.onSwing();
    }
}
