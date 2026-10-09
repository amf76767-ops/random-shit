package dev.dihclient.mixin.port;

import dev.dihclient.glue.VisualPack;
import net.minecraft.class_1058;
import net.minecraft.class_4587;
import net.minecraft.class_4597;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "net.minecraft.class_4603")
public abstract class LowFireMixin {
    @Unique
    private static boolean dih$moved;

    @Inject(method = "method_23070", at = @At("HEAD"), require = 0)
    private static void dih$lower(class_4587 matrices, class_4597 consumers, class_1058 sprite, CallbackInfo ci) {
        float y = VisualPack.fireOffset();
        dih$moved = y != 0.0F;
        if (dih$moved) {
            matrices.method_22903();
            matrices.method_22904(0.0, -y, 0.0);
        }
    }

    @Inject(method = "method_23070", at = @At("RETURN"), require = 0)
    private static void dih$restore(class_4587 matrices, class_4597 consumers, class_1058 sprite, CallbackInfo ci) {
        if (dih$moved) {
            matrices.method_22909();
            dih$moved = false;
        }
    }
}
