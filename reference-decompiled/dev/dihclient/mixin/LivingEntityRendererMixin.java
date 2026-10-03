package dev.dihclient.mixin;

import dev.dihclient.modules.fun.Dinnerbone;
import dev.dihclient.modules.fun.TinyPlayers;
import dev.dihclient.modules.render.Nametags;
import net.minecraft.class_10042;
import net.minecraft.class_1309;
import net.minecraft.class_922;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin({class_922.class})
public abstract class LivingEntityRendererMixin {
   @Inject(
      method = {"method_4055(Lnet/minecraft/class_1309;D)Z"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void dih$label(class_1309 var1, double var2, CallbackInfoReturnable<Boolean> var4) {
      if (Nametags.forceLabel(var1, var2)) {
         var4.setReturnValue(true);
      }
   }

   @Inject(
      method = {"method_62355(Lnet/minecraft/class_1309;Lnet/minecraft/class_10042;F)V"},
      at = {@At("TAIL")}
   )
   private void dih$funState(class_1309 var1, class_10042 var2, float var3, CallbackInfo var4) {
      if (Dinnerbone.flip(var1)) {
         var2.field_53455 = true;
      }

      float var5 = TinyPlayers.scale(var1, var2.field_53328);
      if (var5 != 1.0F) {
         var2.field_53453 *= var5;
      }
   }
}
