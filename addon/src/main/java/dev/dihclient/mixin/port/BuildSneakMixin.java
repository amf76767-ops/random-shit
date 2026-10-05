package dev.dihclient.mixin.port;

import dev.dihclient.autobuild.BuildRuntime;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BuildRuntime.class)
public abstract class BuildSneakMixin {
    @Inject(method = "tick", at = @At("HEAD"), require = 0)
    private void dih$sneakWhenNeeded(BuildRuntime.Settings settings, CallbackInfo ci) {
        if (settings != null && settings.sneakMode == 0) {
            settings.sneakMode = 1;
        }
    }
}
