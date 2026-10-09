package dev.dihclient.mixin.port;

import net.minecraft.class_2596;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(targets = "net.minecraft.class_8673")
public abstract class SpinbotPacketMixin {
    @ModifyVariable(method = "method_52787", at = @At("HEAD"), argsOnly = true, require = 0)
    private class_2596<?> dih$spinPacket(class_2596<?> packet) {
        return dev.dihclient.glue.PacketRewrite.apply(packet);
    }
}
