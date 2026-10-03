package dev.dihclient.mixin;

import dev.dihclient.modules.render.Xray;
import java.util.List;
import net.minecraft.class_10889;
import net.minecraft.class_1920;
import net.minecraft.class_2338;
import net.minecraft.class_2680;
import net.minecraft.class_3610;
import net.minecraft.class_4587;
import net.minecraft.class_4588;
import net.minecraft.class_776;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({class_776.class})
public abstract class BlockRenderManagerMixin {
   @Inject(
      method = {"method_3355"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void dih$xrayBlock(
      class_2680 var1, class_2338 var2, class_1920 var3, class_4587 var4, class_4588 var5, boolean var6, List<class_10889> var7, CallbackInfo var8
   ) {
      if (Xray.active() && !Xray.isVisible(var1)) {
         var8.cancel();
      }
   }

   @Inject(
      method = {"method_3352"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void dih$xrayFluid(class_2338 var1, class_1920 var2, class_4588 var3, class_2680 var4, class_3610 var5, CallbackInfo var6) {
      if (Xray.active() && !Xray.fluidsVisible()) {
         var6.cancel();
      }
   }
}
