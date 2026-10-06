package dev.dihclient.mixin.port;

import dev.dihclient.port.tools.FastBreak;
import net.minecraft.class_1657;
import net.minecraft.class_1922;
import net.minecraft.class_2338;
import net.minecraft.class_310;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "net.minecraft.class_4970$class_4971")
public abstract class BreakSpeedMixin {
    @Inject(method = "method_26165", at = @At("RETURN"), cancellable = true, require = 0)
    private void dih$fastBreak(class_1657 player, class_1922 world, class_2338 pos, CallbackInfoReturnable<Float> cir) {
        float factor = FastBreak.factor();
        if (factor != 1.0F && player == class_310.method_1551().field_1724) {
            cir.setReturnValue(cir.getReturnValueF() * factor);
        }
    }
}
