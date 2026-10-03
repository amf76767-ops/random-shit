package dev.dihclient.mixin;

import dev.dihclient.modules.render.Xray;
import net.minecraft.class_2248;
import net.minecraft.class_2350;
import net.minecraft.class_2680;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin({class_2248.class})
public abstract class BlockMixin {
   @Inject(
      method = {"method_9607"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private static void dih$xraySides(class_2680 var0, class_2680 var1, class_2350 var2, CallbackInfoReturnable<Boolean> var3) {
      if (Xray.active()) {
         var3.setReturnValue(Xray.isVisible(var0) && !Xray.isVisible(var1));
      }
   }
}
