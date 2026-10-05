package dev.dihclient.mixin.port;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import dev.dihclient.port.noinvleak.NoInvLeakModule;
import dev.dihclient.port.noinvleak.StreamOverlay;
import net.minecraft.class_1657;
import net.minecraft.class_1799;
import net.minecraft.class_2960;
import net.minecraft.class_310;
import net.minecraft.class_329;
import net.minecraft.class_332;
import net.minecraft.class_9779;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Ported from an open-source client (GPL-3.0). */
@Mixin(class_329.class)
public abstract class NoInvLeakGuiMixin {
    @Unique
    private static final class_2960 DIH$SELECTION = class_2960.method_60656("hud/hotbar_selection");

    @Shadow
    private int field_2040;
    @Shadow
    private class_1799 field_2031;

    @ModifyVariable(method = "method_1762", at = @At("HEAD"), argsOnly = true)
    private class_1799 dih$hotbar(class_1799 stack, class_332 graphics, int x, int y, class_9779 tracker, class_1657 player,
            class_1799 same, int seed) {
        return NoInvLeakModule.hotbar(stack, x, y, seed);
    }

    @Inject(method = "method_1749", at = @At("HEAD"), cancellable = true)
    private void dih$itemName(class_332 graphics, CallbackInfo ci) {
        if (NoInvLeakModule.hidesItemName()) {
            if (NoInvLeakModule.overlaying() && this.field_2040 > 0 && !this.field_2031.method_7960()) {
                int alpha = Math.min(255, (int) (this.field_2040 * 256.0F / 10.0F));
                int y = graphics.method_51443() - 59;
                if (!class_310.method_1551().field_1761.method_2908()) {
                    y += 14;
                }
                StreamOverlay.itemName(this.field_2031, alpha, y);
            }
            ci.cancel();
        }
    }

    @Redirect(method = "method_1759", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/class_332;method_52706(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/class_2960;IIII)V"))
    private void dih$selection(class_332 graphics, RenderPipeline pipeline, class_2960 sprite, int x, int y, int width, int height) {
        if (NoInvLeakModule.hidesSelectedSlot() && DIH$SELECTION.equals(sprite)) {
            if (NoInvLeakModule.overlaying()) {
                StreamOverlay.selection(x, y);
            }
            return;
        }
        graphics.method_52706(pipeline, sprite, x, y, width, height);
    }
}
