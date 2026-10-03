package dev.dihclient.mixin;

import dev.dihclient.modules.render.Nametags;
import net.minecraft.class_1297;
import net.minecraft.class_897;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin({class_897.class})
public abstract class EntityRendererMixin {
   @Inject(
      method = {"method_3921"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void dih$label(class_1297 var1, double var2, CallbackInfoReturnable<Boolean> var4) {
      if (Nametags.forceLabel(var1, var2)) {
         var4.setReturnValue(true);
      }
   }
}
