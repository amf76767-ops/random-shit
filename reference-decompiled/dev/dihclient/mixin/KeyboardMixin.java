package dev.dihclient.mixin;

import dev.dihclient.DIHClient;
import net.minecraft.class_11908;
import net.minecraft.class_309;
import net.minecraft.class_310;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({class_309.class})
public abstract class KeyboardMixin {
   @Inject(
      method = {"method_1466"},
      at = {@At("TAIL")}
   )
   private void dih$onKey(long var1, int var3, class_11908 var4, CallbackInfo var5) {
      class_310 var6 = class_310.method_1551();
      if (var3 == 1 && var6.field_1755 == null && var6.field_1724 != null) {
         if (DIHClient.modules() != null) {
            DIHClient.modules().onKey(var4.comp_4795());
         }
      }
   }
}
