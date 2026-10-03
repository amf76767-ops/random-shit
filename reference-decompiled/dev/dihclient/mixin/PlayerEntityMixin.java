package dev.dihclient.mixin;

import dev.dihclient.module.ModuleManager;
import dev.dihclient.modules.combat.Reach;
import dev.dihclient.modules.movement.NoClip;
import net.minecraft.class_1657;
import net.minecraft.class_310;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.At.Shift;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin({class_1657.class})
public abstract class PlayerEntityMixin {
   @Inject(
      method = {"method_55754"},
      at = {@At("RETURN")},
      cancellable = true
   )
   private void dih$blockReach(CallbackInfoReturnable<Double> var1) {
      if (this == class_310.method_1551().field_1724 && ModuleManager.on(Reach.class)) {
         var1.setReturnValue(Math.max((Double)var1.getReturnValue(), ModuleManager.of(Reach.class).block.get()));
      }
   }

   @Inject(
      method = {"method_55755"},
      at = {@At("RETURN")},
      cancellable = true
   )
   private void dih$entityReach(CallbackInfoReturnable<Double> var1) {
      if (this == class_310.method_1551().field_1724 && ModuleManager.on(Reach.class)) {
         var1.setReturnValue(Math.max((Double)var1.getReturnValue(), ModuleManager.of(Reach.class).entity.get()));
      }
   }

   @Inject(
      method = {"method_5773"},
      at = {@At(
         value = "FIELD",
         target = "Lnet/minecraft/class_1657;field_5960:Z",
         opcode = 181,
         shift = Shift.AFTER
      )}
   )
   private void dih$noClip(CallbackInfo var1) {
      if (this == class_310.method_1551().field_1724 && NoClip.active()) {
         ((class_1657)this).field_5960 = true;
      }
   }
}
