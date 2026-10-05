package dev.dihclient.mixin.port;

import dev.dihclient.port.tools.SpawnerProtect;
import net.minecraft.class_2626;
import net.minecraft.class_2637;
import net.minecraft.class_634;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.At.Shift;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(class_634.class)
public abstract class SpawnerProtectPacketMixin {
    @Inject(
        method = "method_11136",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/class_2600;method_11074(Lnet/minecraft/class_2596;Lnet/minecraft/class_2547;Lnet/minecraft/class_11980;)V", shift = Shift.AFTER)
    )
    private void dih$spawnerProtectBlock(class_2626 packet, CallbackInfo ci) {
        SpawnerProtect module = SpawnerProtect.active;
        if (module != null) {
            module.onBlockUpdatePacket(packet);
        }
    }

    @Inject(
        method = "method_11100",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/class_2600;method_11074(Lnet/minecraft/class_2596;Lnet/minecraft/class_2547;Lnet/minecraft/class_11980;)V", shift = Shift.AFTER)
    )
    private void dih$spawnerProtectSection(class_2637 packet, CallbackInfo ci) {
        SpawnerProtect module = SpawnerProtect.active;
        if (module != null) {
            module.onSectionBlocksUpdatePacket(packet);
        }
    }
}
