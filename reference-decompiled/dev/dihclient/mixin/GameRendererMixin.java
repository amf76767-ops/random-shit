package dev.dihclient.mixin;

import dev.dihclient.module.ModuleManager;
import dev.dihclient.modules.render.Freecam;
import dev.dihclient.modules.render.NoRender;
import net.minecraft.class_4587;
import net.minecraft.class_757;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({class_757.class})
public abstract class GameRendererMixin {
   @Inject(
      method = {"method_3198"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void dih$hurtCam(class_4587 var1, float var2, CallbackInfo var3) {
      if (ModuleManager.on(NoRender.class) && ModuleManager.of(NoRender.class).hurtCam.get()) {
         var3.cancel();
      }
   }

   @Inject(
      method = {"method_3172"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void dih$hand(float var1, boolean var2, Matrix4f var3, CallbackInfo var4) {
      if (Freecam.hideHands()) {
         var4.cancel();
      }
   }

   @Inject(
      method = {"method_3190"},
      at = {@At("TAIL")}
   )
   private void dih$freecamCrosshair(float var1, CallbackInfo var2) {
      Freecam var3 = Freecam.active();
      if (var3 != null) {
         var3.updateCrosshair(var1);
      }
   }
}
