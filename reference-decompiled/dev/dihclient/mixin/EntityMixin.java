package dev.dihclient.mixin;

import dev.dihclient.module.ModuleManager;
import dev.dihclient.modules.combat.HitBoxes;
import dev.dihclient.modules.render.Esp;
import dev.dihclient.modules.render.FreeLook;
import dev.dihclient.modules.render.Freecam;
import net.minecraft.class_1297;
import net.minecraft.class_1657;
import net.minecraft.class_310;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin({class_1297.class})
public abstract class EntityMixin {
   @Inject(
      method = {"method_5871"},
      at = {@At("RETURN")},
      cancellable = true
   )
   private void dih$hitbox(CallbackInfoReturnable<Float> var1) {
      if (this instanceof class_1657 && this != class_310.method_1551().field_1724) {
         if (ModuleManager.on(HitBoxes.class)) {
            var1.setReturnValue((Float)var1.getReturnValue() + ModuleManager.of(HitBoxes.class).expand.getFloat());
         }
      }
   }

   @Inject(
      method = {"method_5872"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void dih$look(double var1, double var3, CallbackInfo var5) {
      if (this == class_310.method_1551().field_1724) {
         if (ModuleManager.on(Freecam.class)) {
            ModuleManager.of(Freecam.class).look(var1, var3);
            var5.cancel();
         } else if (ModuleManager.on(FreeLook.class) && ModuleManager.of(FreeLook.class).look(var1, var3)) {
            var5.cancel();
         }
      }
   }

   @Inject(
      method = {"method_22861"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void dih$glowColor(CallbackInfoReturnable<Integer> var1) {
      Integer var2 = Esp.glowColor((class_1297)this);
      if (var2 != null) {
         var1.setReturnValue(var2);
      }
   }
}
