package dev.dihclient.mixin.port;

import dev.dihclient.glue.ShaderModule;
import dev.dihclient.modules.fun.Hats;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Hats.class)
public abstract class HatShineMixin {
    @Shadow
    private int seed;

    @Inject(method = "col", at = @At("RETURN"), cancellable = true, require = 0)
    private void dih$shine(float t, int base, CallbackInfoReturnable<Integer> cir) {
        int shined = ShaderModule.hatShine(cir.getReturnValueI(), t, (this.seed & 0xFF) / 255.0F);
        cir.setReturnValue(shined);
    }
}
