package dev.dihclient.mixin;

import dev.dihclient.module.ModuleManager;
import dev.dihclient.modules.combat.Criticals;
import dev.dihclient.modules.render.Esp;
import net.minecraft.class_1297;
import net.minecraft.class_239;
import net.minecraft.class_310;
import net.minecraft.class_3966;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin({class_310.class})
public abstract class MinecraftClientMixin {
   @Shadow
   public class_239 field_1765;

   @Inject(
      method = {"method_1536"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void dih$attack(CallbackInfoReturnable<Boolean> var1) {
      if (this.field_1765 instanceof class_3966 && ModuleManager.on(Criticals.class)) {
         ModuleManager.of(Criticals.class).perform();
      }
   }

   @Inject(
      method = {"method_27022"},
      at = {@At("RETURN")},
      cancellable = true
   )
   private void dih$outline(class_1297 var1, CallbackInfoReturnable<Boolean> var2) {
      if (!(Boolean)var2.getReturnValue() && Esp.glowColor(var1) != null) {
         var2.setReturnValue(true);
      }
   }
}
