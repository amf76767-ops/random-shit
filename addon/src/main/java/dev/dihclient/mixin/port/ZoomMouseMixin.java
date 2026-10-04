package dev.dihclient.mixin.port;

import dev.dihclient.port.zoom.Zoom;
import net.minecraft.class_310;
import net.minecraft.class_312;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The mouse wheel changes the zoom, and the mouse turns slower while zoomed. */
@Mixin(class_312.class)
public abstract class ZoomMouseMixin {
    @Inject(method = "method_1598", at = @At("HEAD"), cancellable = true, require = 0)
    private void dih$zoomWheel(long window, double horizontal, double vertical, CallbackInfo ci) {
        class_310 mc = class_310.method_1551();
        if (vertical != 0.0 && mc.field_1755 == null && mc.method_18506() == null && Zoom.scroll(vertical)) {
            ci.cancel();
        }
    }

    /**
     * The turn speed is (sensitivity * 0.6 + 0.2)^3 * 8, so the first local of method_1606 is scaled by the cube root of the
     * zoom scale: the turn then gets exactly the scale.
     */
    @ModifyVariable(method = "method_1606", at = @At("STORE"), ordinal = 0, require = 0)
    private double dih$zoomTurn(double base) {
        double scale = Zoom.turnScale();
        return scale == 1.0 ? base : base * Math.cbrt(scale);
    }
}
