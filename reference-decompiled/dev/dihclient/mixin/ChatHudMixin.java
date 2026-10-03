package dev.dihclient.mixin;

import dev.dihclient.modules.fun.UwuChat;
import net.minecraft.class_2561;
import net.minecraft.class_338;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin({class_338.class})
public abstract class ChatHudMixin {
   @ModifyVariable(
      method = {"method_44811(Lnet/minecraft/class_2561;Lnet/minecraft/class_7469;Lnet/minecraft/class_7591;)V"},
      at = @At("HEAD"),
      argsOnly = true,
      ordinal = 0
   )
   private class_2561 dih$uwu(class_2561 var1) {
      return UwuChat.transformIncoming(var1);
   }
}
