package dev.dihclient.mixin;

import dev.dihclient.modules.fun.BigHead;
import net.minecraft.class_10055;
import net.minecraft.class_591;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({class_591.class})
public abstract class PlayerEntityModelMixin {
   @Inject(
      method = {"method_62110(Lnet/minecraft/class_10055;)V"},
      at = {@At("TAIL")}
   )
   private void dih$bigHead(class_10055 var1, CallbackInfo var2) {
      class_591 var3 = (class_591)this;
      BigHead.apply(var3.field_3398, var3.field_3394, var1);
   }
}
