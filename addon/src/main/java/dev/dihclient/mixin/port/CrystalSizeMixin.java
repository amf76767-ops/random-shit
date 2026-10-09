package dev.dihclient.mixin.port;

import dev.dihclient.glue.VisualPack;
import net.minecraft.class_10014;
import net.minecraft.class_11659;
import net.minecraft.class_12075;
import net.minecraft.class_4587;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "net.minecraft.class_892")
public abstract class CrystalSizeMixin {
    @Unique
    private boolean dih$scaled;

    @Inject(method = "method_3908", at = @At("HEAD"), require = 0)
    private void dih$shrink(class_10014 state, class_4587 matrices, class_11659 queue, class_12075 camera, CallbackInfo ci) {
        float s = VisualPack.crystalScale();
        this.dih$scaled = s != 1.0F;
        if (this.dih$scaled) {
            matrices.method_22903();
            matrices.method_22905(s, s, s);
        }
    }

    @Inject(method = "method_3908", at = @At("RETURN"), require = 0)
    private void dih$restore(class_10014 state, class_4587 matrices, class_11659 queue, class_12075 camera, CallbackInfo ci) {
        if (this.dih$scaled) {
            matrices.method_22909();
            this.dih$scaled = false;
        }
    }
}
