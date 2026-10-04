package dev.dihclient.mixin.port;

import dev.dihclient.autobuild.BuildRuntime;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Hoppers (and droppers, comparators into chests ...) often have to be placed against a chest or another hopper: the hopper points
 * into the block it is clicked on. With "Sneak Place" on Off, the placement search never clicks a clickable block (it would open
 * it), so such a hopper found no way to be placed and was skipped for ever. Off now behaves like "When Needed": it sneaks only for
 * clicks on clickable blocks, every other placement stays as before.
 */
@Mixin(BuildRuntime.class)
public abstract class BuildSneakMixin {
    @Inject(method = "tick", at = @At("HEAD"), require = 0)
    private void dih$sneakWhenNeeded(BuildRuntime.Settings settings, CallbackInfo ci) {
        if (settings != null && settings.sneakMode == 0) {
            settings.sneakMode = 1;
        }
    }
}
