package dev.dihclient.mixin;

import dev.dihclient.modules.fun.ModelReplacer;
import net.minecraft.class_1297;
import net.minecraft.class_898;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin({class_898.class})
public abstract class EntityRenderManagerMixin {
   @ModifyVariable(
      method = {"method_72977"},
      at = @At("HEAD"),
      argsOnly = true
   )
   private class_1297 dih$replaceModel(class_1297 var1) {
      return ModelReplacer.swap(var1);
   }
}
