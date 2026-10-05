package dev.dihclient.mixin;

import dev.dihclient.glue.CustomModel;
import net.minecraft.class_10042;
import net.minecraft.class_1309;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "net.minecraft.class_922")
public abstract class CustomModelMixin {
    @Inject(
        method = {"method_62355(Lnet/minecraft/class_1309;Lnet/minecraft/class_10042;F)V"},
        at = {@At("TAIL")}
    )
    private void dih$customModel(class_1309 entity, class_10042 state, float tickDelta, CallbackInfo ci) {
        if (CustomModel.hides(entity)) {
            state.field_53453 = 1.0E-4F;
        }
        dev.dihclient.glue.Emotes.capture(entity, state);
    }
}
