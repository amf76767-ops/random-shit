package dev.dihclient.mixin;

import dev.dihclient.modules.fun.CustomSky;
import net.minecraft.class_12076;
import net.minecraft.class_12131;
import net.minecraft.class_4184;
import net.minecraft.class_4587;
import net.minecraft.class_638;
import net.minecraft.class_9975;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({class_9975.class})
public abstract class SkyRenderingMixin {
   @Inject(
      method = {"method_74926"},
      at = {@At("TAIL")}
   )
   private void dih$customSky(class_638 var1, float var2, class_4184 var3, class_12076 var4, CallbackInfo var5) {
      CustomSky.modify(var4);
   }

   @Inject(
      method = {"method_62303"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void dih$hideSun(float var1, class_4587 var2, CallbackInfo var3) {
      if (CustomSky.hideSun()) {
         var3.cancel();
      }
   }

   @Inject(
      method = {"method_62304"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void dih$hideMoon(class_12131 var1, float var2, class_4587 var3, CallbackInfo var4) {
      if (CustomSky.hideMoon()) {
         var4.cancel();
      }
   }
}
