package dev.dihclient.mixin;

import dev.dihclient.modules.render.FreeLook;
import net.minecraft.class_4184;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin({class_4184.class})
public abstract class CameraClipMixin {
   @ModifyArg(
      method = {"method_19321"},
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/class_4184;method_19318(F)F"
      ),
      index = 0
   )
   private float dih$distance(float var1) {
      return var1 * FreeLook.distanceFactor();
   }

   @Inject(
      method = {"method_19318(F)F"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void dih$clip(float var1, CallbackInfoReturnable<Float> var2) {
      if (FreeLook.cameraClip()) {
         var2.setReturnValue(var1);
      }
   }
}
