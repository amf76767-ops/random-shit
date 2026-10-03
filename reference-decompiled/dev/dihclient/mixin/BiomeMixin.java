package dev.dihclient.mixin;

import dev.dihclient.modules.fun.FakeWeather;
import net.minecraft.class_1959;
import net.minecraft.class_2338;
import net.minecraft.class_1959.class_1963;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin({class_1959.class})
public abstract class BiomeMixin {
   @Inject(
      method = {"method_48162"},
      at = {@At("RETURN")},
      cancellable = true
   )
   private void dih$fakeWeather(class_2338 var1, int var2, CallbackInfoReturnable<class_1963> var3) {
      class_1963 var4 = FakeWeather.precipitation((class_1963)var3.getReturnValue());
      if (var4 != null) {
         var3.setReturnValue(var4);
      }
   }

   @Inject(
      method = {"method_48163"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void dih$fakeWeatherDry(CallbackInfoReturnable<Boolean> var1) {
      if (FakeWeather.forcePrecipitation()) {
         var1.setReturnValue(true);
      }
   }
}
