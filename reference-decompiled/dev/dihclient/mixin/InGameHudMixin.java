package dev.dihclient.mixin;

import dev.dihclient.module.ModuleManager;
import dev.dihclient.modules.client.BetterTablist;
import net.minecraft.class_329;
import net.minecraft.class_332;
import net.minecraft.class_9779;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({class_329.class})
public abstract class InGameHudMixin {
   @Inject(
      method = {"method_55804"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void dih$tab(class_332 var1, class_9779 var2, CallbackInfo var3) {
      if (ModuleManager.on(BetterTablist.class)) {
         BetterTablist.renderIfHeld(var1);
         var3.cancel();
      }
   }
}
