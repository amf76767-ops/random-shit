package dev.dihclient.mixin.port;

import dev.dihclient.port.tools.Spinbot;
import net.minecraft.class_10042;
import net.minecraft.class_1309;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "net.minecraft.class_922")
public abstract class SpinbotMixin {
    @Inject(method = "method_62355(Lnet/minecraft/class_1309;Lnet/minecraft/class_10042;F)V", at = @At("TAIL"), require = 0)
    private void dih$spin(class_1309 entity, class_10042 state, float tickDelta, CallbackInfo ci) {
        Spinbot.apply(entity, state);
    }
}
