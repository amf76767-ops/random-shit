package dev.dihclient.mixin.port;

import dev.dihclient.port.noinvleak.NoInvLeakModule;
import dev.dihclient.port.noinvleak.StreamOverlay;
import net.minecraft.class_1703;
import net.minecraft.class_1735;
import net.minecraft.class_1799;
import net.minecraft.class_332;
import net.minecraft.class_465;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Ported from Anubis Client 0.9.8 (GPL-3.0) (MixinNoInvLeakContainerScreen).
 * Container screens for No Inv Leak: the stack of the player's own slots, the cursor stack and the tooltip.
 */
@Mixin(class_465.class)
public abstract class NoInvLeakContainerScreenMixin {
    @Shadow
    protected class_1735 field_2787;
    @Shadow
    protected int field_2776;
    @Shadow
    protected int field_2800;

    @Shadow
    public abstract class_1703 method_17577();

    /** renderSlot: every {@code slot.getItem()} of the method (it reads the stack more than once). */
    @Redirect(method = "method_2385", at = @At(value = "INVOKE", target = "Lnet/minecraft/class_1735;method_7677()Lnet/minecraft/class_1799;"))
    private class_1799 dih$slotItem(class_1735 slot) {
        class_1799 stack = slot.method_7677();
        return NoInvLeakModule.ownSlot(slot)
                ? NoInvLeakModule.container(stack, slot, this.field_2776 + slot.field_7873, this.field_2800 + slot.field_7872, slot == this.field_2787)
                : stack;
    }

    /** renderFloatingItem(graphics, stack, x, y, text): the cursor stack. */
    @ModifyVariable(method = "method_2382", at = @At("HEAD"), argsOnly = true)
    private class_1799 dih$carried(class_1799 stack, class_332 graphics, class_1799 same, int x, int y, String text) {
        return NoInvLeakModule.carried(stack, x, y);
    }

    @Inject(method = "method_2380", at = @At("HEAD"), cancellable = true)
    private void dih$tooltip(class_332 graphics, int x, int y, CallbackInfo ci) {
        if (NoInvLeakModule.hidesTooltips() && NoInvLeakModule.ownSlot(this.field_2787)) {
            if (NoInvLeakModule.overlaying() && this.field_2787.method_7681() && this.method_17577().method_34255().method_7960()) {
                StreamOverlay.tooltip(this.field_2787.method_7677(), x, y);
            }
            ci.cancel();
        }
    }
}
