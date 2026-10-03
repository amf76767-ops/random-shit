package dev.dihclient.mixin;

import dev.dihclient.util.Money;
import net.minecraft.class_2561;
import net.minecraft.class_338;
import net.minecraft.class_7469;
import net.minecraft.class_7591;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({class_338.class})
public abstract class ChatCaptureMixin {
   @Inject(
      method = {"method_44811"},
      at = {@At("HEAD")}
   )
   private void dih$capture(class_2561 var1, class_7469 var2, class_7591 var3, CallbackInfo var4) {
      try {
         Money.onChat(var1.getString());
      } catch (Throwable var6) {
      }
   }
}
