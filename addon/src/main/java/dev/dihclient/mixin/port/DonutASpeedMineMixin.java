package dev.dihclient.mixin.port;

import dev.dihclient.DIHClient;
import dev.dihclient.port.donuta.DonutSpeedMine;
import net.minecraft.class_2338;
import net.minecraft.class_2350;
import net.minecraft.class_2680;
import net.minecraft.class_310;
import net.minecraft.class_636;
import net.minecraft.class_638;
import net.minecraft.class_746;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(class_636.class)
public abstract class DonutASpeedMineMixin {
    @Shadow
    @Final
    private class_310 field_3712;
    @Shadow
    private int field_3716;
    @Shadow
    private float field_3715;
    @Shadow
    private class_2338 field_3714;

    @Inject(method = "method_2902", at = @At("HEAD"))
    private void dih$donutASpeedMineDelay(class_2338 pos, class_2350 direction, CallbackInfoReturnable<Boolean> cir) {
        try {
            class_746 player = this.field_3712.field_1724;
            if (this.field_3716 > 0 && player != null && this.field_3712.field_1687 != null && !player.method_31549().field_7477) {
                class_2680 state = this.field_3712.field_1687.method_8320(pos);
                if (DonutSpeedMine.dropsDelay(state, state.method_26165(player, this.field_3712.field_1687, pos))) {
                    this.field_3716 = 0;
                }
            }
        } catch (Throwable t) {
            DIHClient.LOG.warn("[DIHClient] Donut Speed Mine delay hook failed", t);
        }
    }

    @Redirect(method = "method_2902", at = @At(value = "FIELD", target = "Lnet/minecraft/class_636;field_3715:F", opcode = Opcodes.PUTFIELD, ordinal = 0))
    private void dih$donutASpeedMineProgress(class_636 self, float value) {

        float old = this.field_3715;
        float result = value;
        try {
            class_638 level = this.field_3712.field_1687;
            if (level != null && this.field_3714 != null) {
                float gain = value - old;
                float scaled = DonutSpeedMine.scaleProgress(level.method_8320(this.field_3714), gain);
                if (scaled != gain) {
                    result = old + scaled;
                }
            }
        } catch (Throwable t) {
            DIHClient.LOG.warn("[DIHClient] Donut Speed Mine progress hook failed", t);
            result = value;
        }
        this.field_3715 = result;
    }
}
