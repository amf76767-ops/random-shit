package dev.dihclient.mixin.port;

import dev.dihclient.port.noinvleak.NoInvLeakModule;
import net.minecraft.class_1661;
import net.minecraft.class_1735;
import net.minecraft.class_2960;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Ported from an open-source client (GPL-3.0) (MixinNoInvLeakSlot).
 * The grey silhouette of an empty armor/offhand slot would show through the overlay, so it is dropped for filled own slots.
 */
@Mixin(class_1735.class)
public abstract class NoInvLeakSlotMixin {
    @Inject(method = "method_7679", at = @At("RETURN"), cancellable = true)
    private void dih$silhouette(CallbackInfoReturnable<class_2960> cir) {
        class_1735 self = (class_1735) (Object) this;
        if (cir.getReturnValue() != null && NoInvLeakModule.overlaying() && self.field_7871 instanceof class_1661 && self.method_7681()) {
            cir.setReturnValue(null);
        }
    }
}
