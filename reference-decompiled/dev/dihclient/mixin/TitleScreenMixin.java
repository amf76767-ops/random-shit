package dev.dihclient.mixin;

import dev.dihclient.gui.AccountsScreen;
import dev.dihclient.hud.HudManager;
import dev.dihclient.render.Gfx;
import net.minecraft.class_11909;
import net.minecraft.class_310;
import net.minecraft.class_332;
import net.minecraft.class_437;
import net.minecraft.class_442;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin({class_437.class})
public abstract class TitleScreenMixin {
   private static final int BTN_X = 6;
   private static final int BTN_Y = 6;
   private static final int BTN_W = 96;
   private static final int BTN_H = 20;

   private boolean dih$onTitle() {
      return this instanceof class_442;
   }

   @Inject(
      method = {"method_25394"},
      at = {@At("TAIL")}
   )
   private void dih$drawAccounts(class_332 var1, int var2, int var3, float var4, CallbackInfo var5) {
      if (this.dih$onTitle()) {
         int var6 = HudManager.accent();
         boolean var7 = Gfx.inside(var2, var3, 6, 6, 96, 20);
         Gfx.round(var1, 6, 6, 96, 20, var7 ? var6 : -14079703);
         Gfx.textCentered(var1, "Accounts", 54, 12, -1);
      }
   }

   @Inject(
      method = {"method_25402"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void dih$clickAccounts(class_11909 var1, boolean var2, CallbackInfoReturnable<Boolean> var3) {
      if (this.dih$onTitle() && Gfx.inside((int)var1.comp_4798(), (int)var1.comp_4799(), 6, 6, 96, 20)) {
         class_310 var4 = class_310.method_1551();
         var4.method_1507(new AccountsScreen((class_437)this));
         var3.setReturnValue(true);
      }
   }
}
