package dev.dihclient.mixin;

import dev.dihclient.gui.PauseSidebar;
import net.minecraft.class_11905;
import net.minecraft.class_11908;
import net.minecraft.class_309;
import net.minecraft.class_310;
import net.minecraft.class_433;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({class_309.class})
public abstract class PauseKeyboardMixin {
   @Inject(
      method = {"method_1466"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void dih$panelKey(long var1, int var3, class_11908 var4, CallbackInfo var5) {
      if (var3 != 0
         && class_310.method_1551().field_1755 instanceof class_433
         && PauseSidebar.focused()
         && PauseSidebar.key(var4.comp_4795(), var4.comp_4797())) {
         var5.cancel();
      }
   }

   @Inject(
      method = {"method_1457"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void dih$panelChar(long var1, class_11905 var3, CallbackInfo var4) {
      if (class_310.method_1551().field_1755 instanceof class_433 && PauseSidebar.focused() && PauseSidebar.type(var3.method_74226())) {
         var4.cancel();
      }
   }
}
