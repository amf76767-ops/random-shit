package dev.dihclient.mixin;

import dev.dihclient.modules.fun.CustomSky;
import net.minecraft.class_4184;
import net.minecraft.class_638;
import net.minecraft.class_758;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin({class_758.class})
public abstract class FogRendererMixin {
   @Inject(
      method = {"method_62185"},
      at = {@At("RETURN")}
   )
   private void dih$fogColor(class_4184 var1, float var2, class_638 var3, int var4, float var5, CallbackInfoReturnable<Vector4f> var6) {
      Vector4f var7 = (Vector4f)var6.getReturnValue();
      if (var7 != null) {
         CustomSky.fog(var7, var1.method_19334());
      }
   }
}
