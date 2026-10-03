package dev.dihclient.mixin;

import dev.dihclient.glue.SoftKeys;
import net.minecraft.class_304;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Notes every key press that comes from DIHClient's own code (see {@link SoftKeys}). */
@Mixin({class_304.class})
public abstract class KeyBindingSoftMixin {
    @Inject(
        method = {"method_23481"},
        at = {@At("HEAD")}
    )
    private void dih$noteSoftPress(boolean pressed, CallbackInfo ci) {
        String caller = SoftKeys.dihCaller();
        if (caller != null) {
            SoftKeys.record(this, pressed);
            if (pressed) {
                SoftKeys.debug(caller);
            }
        }
    }
}
