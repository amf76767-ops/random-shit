package dev.dihclient.mixin;

import dev.dihclient.DIHClient;
import net.minecraft.class_2596;
import net.minecraft.class_8673;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({class_8673.class})
public abstract class ClientCommonNetworkHandlerMixin {
   @Inject(
      method = {"method_52787"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void dih$onSend(class_2596<?> var1, CallbackInfo var2) {
      if (DIHClient.modules() != null && DIHClient.modules().packetSend(var1)) {
         var2.cancel();
      }
   }
}
