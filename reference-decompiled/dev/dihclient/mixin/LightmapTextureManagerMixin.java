package dev.dihclient.mixin;

import dev.dihclient.module.ModuleManager;
import dev.dihclient.modules.render.Fullbright;
import net.minecraft.class_1309;
import net.minecraft.class_765;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin({class_765.class})
public abstract class LightmapTextureManagerMixin {
   @Inject(
      method = {"method_42596"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void dih$darkness(class_1309 var1, float var2, float var3, CallbackInfoReturnable<Float> var4) {
      if (ModuleManager.on(Fullbright.class) && ModuleManager.of(Fullbright.class).suppressDarkness.get()) {
         var4.setReturnValue(0.0F);
      }
   }
}
