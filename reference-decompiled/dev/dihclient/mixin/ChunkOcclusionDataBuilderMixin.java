package dev.dihclient.mixin;

import dev.dihclient.modules.render.Xray;
import net.minecraft.class_2338;
import net.minecraft.class_852;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({class_852.class})
public abstract class ChunkOcclusionDataBuilderMixin {
   @Inject(
      method = {"method_3682"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void dih$xrayOcclusion(class_2338 var1, CallbackInfo var2) {
      if (Xray.active()) {
         var2.cancel();
      }
   }
}
