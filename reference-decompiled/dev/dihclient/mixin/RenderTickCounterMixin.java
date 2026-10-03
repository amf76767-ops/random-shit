package dev.dihclient.mixin;

import dev.dihclient.module.ModuleManager;
import dev.dihclient.modules.world.Timer;
import net.minecraft.class_9779.class_9781;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.At.Shift;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin({class_9781.class})
public abstract class RenderTickCounterMixin {
   @Shadow
   private float field_51958;

   @Inject(
      method = {"method_60639(J)I"},
      at = {@At(
         value = "FIELD",
         target = "Lnet/minecraft/class_9779$class_9781;field_51958:F",
         opcode = 181,
         shift = Shift.AFTER
      )}
   )
   private void dih$timer(long var1, CallbackInfoReturnable<Integer> var3) {
      if (ModuleManager.on(Timer.class)) {
         this.field_51958 = this.field_51958 * ModuleManager.of(Timer.class).speed.getFloat();
      }
   }
}
