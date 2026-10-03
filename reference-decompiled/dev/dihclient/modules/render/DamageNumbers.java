package dev.dihclient.modules.render;

import dev.dihclient.module.Category;
import dev.dihclient.module.Module;
import dev.dihclient.module.ModuleManager;
import net.minecraft.class_1657;

public class DamageNumbers extends Module {
   private class_1657 tracked;
   private float lastHealth;
   private float lastDamage;
   private int showTicks;

   public DamageNumbers() {
      super("DamageNumbers", Category.RENDER, "Shows the most recent health loss of your current target next to the Target HUD.");
   }

   @Override
   public void onTick() {
      if (this.showTicks > 0) {
         this.showTicks--;
      }

      TargetHud var1 = ModuleManager.of(TargetHud.class);
      class_1657 var2 = var1 != null ? var1.current() : null;
      if (var2 != this.tracked) {
         this.tracked = var2;
         this.lastHealth = var2 == null ? 0.0F : var2.method_6032() + var2.method_6067();
      } else if (var2 != null) {
         float var3 = var2.method_6032() + var2.method_6067();
         if (var3 < this.lastHealth - 0.01F) {
            this.lastDamage = this.lastHealth - var3;
            this.showTicks = 30;
         }

         this.lastHealth = var3;
      }
   }

   public float recentDamage() {
      return this.showTicks > 0 ? this.lastDamage : 0.0F;
   }

   public float fade() {
      return Math.min(1.0F, this.showTicks / 10.0F);
   }
}
