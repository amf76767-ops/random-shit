package dev.dihclient.mixin.port;

import dev.dihclient.DIHClient;
import dev.dihclient.port.crystal.ServerRotation;
import net.minecraft.class_10185;
import net.minecraft.class_241;
import net.minecraft.class_743;
import net.minecraft.class_744;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** While the silent rotation turns the body, the keys are remapped so you still walk where you meant to. */
@Mixin(class_743.class)
public abstract class CrystalKeyboardInputMixin {
    @Inject(method = "method_3129", at = @At("TAIL"))
    private void dih$crystalCorrectInput(CallbackInfo ci) {
        try {
            class_744 input = (class_744) (Object) this;
            class_10185 corrected = ServerRotation.correctInput(input.field_54155);
            if (corrected != input.field_54155) {
                input.field_54155 = corrected;
                ((CrystalInputAccessorMixin) input).dih$crystalSetMoveVector(
                        new class_241(impulse(corrected.comp_3161(), corrected.comp_3162()), impulse(corrected.comp_3159(), corrected.comp_3160())).method_35581());
            }
        } catch (Throwable t) {
            DIHClient.LOG.warn("[DIHClient] silent rotation input failed", t);
        }
    }

    private static float impulse(boolean positive, boolean negative) {
        return positive == negative ? 0.0F : (positive ? 1.0F : -1.0F);
    }
}
