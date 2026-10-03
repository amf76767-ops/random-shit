package dev.dihclient.mixin;

import dev.dihclient.gui.PauseSidebar;
import net.minecraft.class_11910;
import net.minecraft.class_310;
import net.minecraft.class_312;
import net.minecraft.class_433;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({class_312.class})
public abstract class PauseMouseMixin {
   @Inject(
      method = {"method_1601"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void dih$panelClick(long var1, class_11910 var3, int var4, CallbackInfo var5) {
      class_310 var6 = class_310.method_1551();
      if (var4 == 1 && var6.field_1755 instanceof class_433) {
         class_312 var7 = (class_312)this;
         if (PauseSidebar.click(var7.method_68879(var6.method_22683()), var7.method_68883(var6.method_22683()))) {
            var5.cancel();
         }
      }
   }

   @Inject(
      method = {"method_1598"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void dih$panelScroll(long var1, double var3, double var5, CallbackInfo var7) {
      class_310 var8 = class_310.method_1551();
      if (var8.field_1755 instanceof class_433) {
         class_312 var9 = (class_312)this;
         if (PauseSidebar.scroll(var9.method_68879(var8.method_22683()), var9.method_68883(var8.method_22683()), var5)) {
            var7.cancel();
         }
      }
   }
}
