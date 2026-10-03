package dev.dihclient.mixin;

import dev.dihclient.module.ModuleManager;
import dev.dihclient.modules.render.Freecam;
import net.minecraft.class_310;
import net.minecraft.class_312;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({class_312.class})
public abstract class MouseMixin {
   @Inject(
      method = {"method_1598"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void dih$scroll(long var1, double var3, double var5, CallbackInfo var7) {
      if (class_310.method_1551().field_1755 == null && ModuleManager.on(Freecam.class)) {
         if (ModuleManager.of(Freecam.class).scroll(var5)) {
            var7.cancel();
         }
      }
   }
}
