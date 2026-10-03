package dev.dihclient.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.dihclient.module.ModuleManager;
import dev.dihclient.modules.fun.DrunkMode;
import dev.dihclient.modules.render.FreeLook;
import dev.dihclient.modules.render.Freecam;
import net.minecraft.class_1297;
import net.minecraft.class_1937;
import net.minecraft.class_243;
import net.minecraft.class_310;
import net.minecraft.class_4184;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin({class_4184.class})
public abstract class CameraMixin {
   @Shadow
   protected abstract void method_19322(class_243 var1);

   @Shadow
   protected abstract void method_19325(float var1, float var2);

   @Inject(
      method = {"method_19321"},
      at = {@At("TAIL")}
   )
   private void dih$freecam(class_1937 var1, class_1297 var2, boolean var3, boolean var4, float var5, CallbackInfo var6) {
      Freecam var7 = Freecam.active();
      if (var7 != null) {
         this.method_19322(var7.cameraPos(var5));
         this.method_19325(var7.yaw, var7.pitch);
      }
   }

   @Inject(
      method = {"method_19333"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void dih$freecamThirdPerson(CallbackInfoReturnable<Boolean> var1) {
      if (Freecam.active() != null) {
         var1.setReturnValue(true);
      }
   }

   @WrapOperation(
      method = {"method_19321"},
      at = {@At(
         value = "INVOKE",
         target = "Lnet/minecraft/class_1297;method_5705(F)F"
      )}
   )
   private float dih$yaw(class_1297 var1, float var2, Operation<Float> var3) {
      float var4 = var1 == class_310.method_1551().field_1724 && ModuleManager.on(FreeLook.class)
         ? ModuleManager.of(FreeLook.class).yaw()
         : (Float)var3.call(new Object[]{var1, var2});
      return var4 + DrunkMode.yawOffset();
   }

   @WrapOperation(
      method = {"method_19321"},
      at = {@At(
         value = "INVOKE",
         target = "Lnet/minecraft/class_1297;method_5695(F)F"
      )}
   )
   private float dih$pitch(class_1297 var1, float var2, Operation<Float> var3) {
      float var4 = var1 == class_310.method_1551().field_1724 && ModuleManager.on(FreeLook.class)
         ? ModuleManager.of(FreeLook.class).pitch()
         : (Float)var3.call(new Object[]{var1, var2});
      return Math.max(-90.0F, Math.min(90.0F, var4 + DrunkMode.pitchOffset()));
   }
}
