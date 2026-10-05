package dev.dihclient.mixin.port;

import dev.dihclient.glue.ShaderModule;
import net.minecraft.class_1799;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(class_1799.class)
public abstract class GlintMixin {
    @Inject(method = "method_7958", at = @At("RETURN"), cancellable = true)
    private void dih$shaderGlint(CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValueZ() && ShaderModule.forceGlint((class_1799) (Object) this)) {
            cir.setReturnValue(true);
        }
    }
}
