package dev.dihclient.mixin;

import dev.dihclient.gui.PauseSidebar;
import net.minecraft.class_332;
import net.minecraft.class_433;
import net.minecraft.class_437;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({class_437.class})
public abstract class PauseMenuMixin {
   @Inject(
      method = {"method_25394"},
      at = {@At("TAIL")}
   )
   private void dih$pausePanel(class_332 var1, int var2, int var3, float var4, CallbackInfo var5) {
      if (this instanceof class_433) {
         class_437 var6 = (class_437)this;
         PauseSidebar.render(var1, var2, var3, var6.field_22789, var6.field_22790);
      }
   }
}
