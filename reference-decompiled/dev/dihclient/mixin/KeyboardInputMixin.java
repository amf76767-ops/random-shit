package dev.dihclient.mixin;

import dev.dihclient.modules.render.Freecam;
import net.minecraft.class_10185;
import net.minecraft.class_241;
import net.minecraft.class_743;
import net.minecraft.class_744;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({class_743.class})
public abstract class KeyboardInputMixin extends class_744 {
   @Inject(
      method = {"method_3129"},
      at = {@At("TAIL")}
   )
   private void dih$freeze(CallbackInfo var1) {
      Freecam var2 = Freecam.active();
      if (var2 != null) {
         this.field_54155 = new class_10185(false, false, false, false, false, var2.playerSneaks(), false);
         this.field_55868 = class_241.field_1340;
      }
   }
}
