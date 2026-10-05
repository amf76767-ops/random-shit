package dev.dihclient.mixin.port;

import dev.dihclient.port.tools.SpawnerProtect;
import net.minecraft.class_312;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(class_312.class)
public abstract class SpawnerProtectMouseMixin {
    @Inject(method = "method_1606", at = @At("HEAD"), cancellable = true)
    private void dih$spawnerProtectFreezeLook(double movementTime, CallbackInfo ci) {
        SpawnerProtect module = SpawnerProtect.active;
        if (module != null && module.blocksMouseTurn()) {
            ci.cancel();
        }
    }
}
