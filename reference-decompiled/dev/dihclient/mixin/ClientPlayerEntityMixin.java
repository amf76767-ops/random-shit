package dev.dihclient.mixin;

import dev.dihclient.module.ModuleManager;
import dev.dihclient.modules.movement.NoClip;
import dev.dihclient.modules.movement.NoSlow;
import net.minecraft.class_746;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin({class_746.class})
public abstract class ClientPlayerEntityMixin {
   @Inject(
      method = {"method_75410"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void dih$noSlow(CallbackInfoReturnable<Float> var1) {
      if (ModuleManager.on(NoSlow.class)) {
         var1.setReturnValue(ModuleManager.of(NoSlow.class).multiplier.getFloat());
      }
   }

   @Inject(
      method = {"method_30673"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void dih$noClipPush(double var1, double var3, CallbackInfo var5) {
      if (NoClip.active()) {
         var5.cancel();
      }
   }
}
