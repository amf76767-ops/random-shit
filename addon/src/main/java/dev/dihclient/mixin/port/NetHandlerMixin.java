package dev.dihclient.mixin.port;

import dev.dihclient.port.PacketBus;
import io.netty.channel.ChannelHandlerContext;
import java.util.function.BooleanSupplier;
import net.minecraft.class_2535;
import net.minecraft.class_2596;
import net.minecraft.class_2598;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(class_2535.class)
public abstract class NetHandlerMixin {
    @Unique
    private final BooleanSupplier dih$active = this::method_10758;

    @Shadow
    public abstract boolean method_10758();

    @Shadow
    public abstract class_2598 method_36121();

    @Inject(method = "method_10770", at = @At("HEAD"), cancellable = true)
    private void dih$received(ChannelHandlerContext ctx, class_2596<?> packet, CallbackInfo ci) {
        if (this.method_36121() == class_2598.field_11942) {
            if (PacketBus.dispatchNetty(packet)) {
                ci.cancel();
            } else {
                PacketBus.dispatchReceived(packet, this.dih$active);
            }
        }
    }
}
