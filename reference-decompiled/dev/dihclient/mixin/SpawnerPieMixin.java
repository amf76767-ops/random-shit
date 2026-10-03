package dev.dihclient.mixin;

import dev.dihclient.modules.basefinding.SpawnerPie;
import net.minecraft.class_310;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin({class_310.class})
public abstract class SpawnerPieMixin {
   @ModifyVariable(
      method = {"method_24458(ZLnet/minecraft/class_4758;)Lnet/minecraft/class_3695;"},
      at = @At("HEAD"),
      argsOnly = true,
      ordinal = 0
   )
   private boolean dih$profilerFrame(boolean var1) {
      return var1 || SpawnerPie.wantsProfiler();
   }

   @ModifyVariable(
      method = {"method_24460(ZLnet/minecraft/class_4758;)V"},
      at = @At("HEAD"),
      argsOnly = true,
      ordinal = 0
   )
   private boolean dih$profilerResult(boolean var1) {
      return var1 || SpawnerPie.wantsProfiler();
   }
}
