package dev.dihclient.mixin.port;

import dev.dihclient.DIHClient;
import dev.dihclient.port.donuta.DonutSpeedMine;
import net.minecraft.class_1657;
import net.minecraft.class_1922;
import net.minecraft.class_2338;
import net.minecraft.class_2350;
import net.minecraft.class_2680;
import net.minecraft.class_310;
import net.minecraft.class_636;
import net.minecraft.class_746;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Donut Speed Mine: the game adds the break progress of a block once per tick in updateBlockBreakingProgress; the
 * module scales that number, and may zero the wait between two blocks before it is checked. (The Anubis original uses
 * MixinExtras' WrapOperation; DIH does not ship it, so a plain redirect does the same.)
 */
@Mixin(class_636.class)
public abstract class DonutASpeedMineMixin {
    @Shadow
    @Final
    private class_310 field_3712;
    @Shadow
    private int field_3716;

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

    @Redirect(method = "method_2902", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/class_2680;method_26165(Lnet/minecraft/class_1657;Lnet/minecraft/class_1922;Lnet/minecraft/class_2338;)F"))
    private float dih$donutASpeedMineProgress(class_2680 state, class_1657 player, class_1922 level, class_2338 pos) {
        float progress = state.method_26165(player, level, pos);
        try {
            return DonutSpeedMine.scaleProgress(state, progress);
        } catch (Throwable t) {
            DIHClient.LOG.warn("[DIHClient] Donut Speed Mine progress hook failed", t);
            return progress;
        }
    }
}
