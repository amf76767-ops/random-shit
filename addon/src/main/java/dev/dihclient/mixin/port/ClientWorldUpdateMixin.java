package dev.dihclient.mixin.port;

import dev.dihclient.port.chunks.BlockUpdates;
import net.minecraft.class_2338;
import net.minecraft.class_2680;
import net.minecraft.class_638;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(class_638.class)
public abstract class ClientWorldUpdateMixin {
    @Inject(method = "method_8413", at = @At("HEAD"))
    private void dih$blockUpdated(class_2338 pos, class_2680 oldState, class_2680 newState, int flags, CallbackInfo ci) {
        BlockUpdates.dispatch(pos, oldState, newState);
    }
}
