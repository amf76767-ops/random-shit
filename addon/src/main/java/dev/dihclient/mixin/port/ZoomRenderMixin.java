package dev.dihclient.mixin.port;

import dev.dihclient.port.zoom.Zoom;
import net.minecraft.class_4184;
import net.minecraft.class_757;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Divides the field of view by the zoom. */
@Mixin(class_757.class)
public abstract class ZoomRenderMixin {
    @Inject(method = "method_3196", at = @At("RETURN"), cancellable = true, require = 0)
    private void dih$zoomFov(class_4184 camera, float tickDelta, boolean useFovSetting, CallbackInfoReturnable<Float> cir) {
        if (useFovSetting) {
            cir.setReturnValue(Zoom.fov(cir.getReturnValueF()));
        }
    }
}
