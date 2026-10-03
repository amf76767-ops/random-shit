package dev.dihclient.mixin.port;

import dev.dihclient.port.PacketBus;
import net.minecraft.class_2626;
import net.minecraft.class_2637;
import net.minecraft.class_2672;
import net.minecraft.class_2676;
import net.minecraft.class_2678;
import net.minecraft.class_634;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Tells {@link PacketBus} when the game has finished with login, chunk, block, section and light packets. */
@Mixin(class_634.class)
public abstract class PacketAppliedMixin {
    @Inject(method = "method_11120", at = @At("TAIL"))
    private void dih$login(class_2678 packet, CallbackInfo ci) {
        PacketBus.dispatchApplied(packet);
    }

    @Inject(method = "method_11128", at = @At("TAIL"))
    private void dih$chunk(class_2672 packet, CallbackInfo ci) {
        PacketBus.dispatchApplied(packet);
    }

    @Inject(method = "method_11136", at = @At("TAIL"))
    private void dih$block(class_2626 packet, CallbackInfo ci) {
        PacketBus.dispatchApplied(packet);
    }

    @Inject(method = "method_11100", at = @At("TAIL"))
    private void dih$section(class_2637 packet, CallbackInfo ci) {
        PacketBus.dispatchApplied(packet);
    }

    @Inject(method = "method_11143", at = @At("TAIL"))
    private void dih$light(class_2676 packet, CallbackInfo ci) {
        PacketBus.dispatchApplied(packet);
    }
}
