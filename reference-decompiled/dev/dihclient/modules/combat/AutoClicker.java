package dev.dihclient.modules.combat;

import dev.dihclient.mixin.accessor.MinecraftClientAccessor;
import dev.dihclient.module.Category;
import dev.dihclient.module.Module;
import dev.dihclient.setting.BoolSetting;
import dev.dihclient.setting.DoubleSetting;
import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.class_239.class_240;

public class AutoClicker extends Module {
   public final BoolSetting left = this.bool("Left Click", "Auto-attack while holding the attack button.", true);
   public final DoubleSetting leftMin = this.dbl("Left Min CPS", "Lowest clicks per second.", 9.0, 1.0, 20.0, 0.5).visibleWhen(this.left::get);
   public final DoubleSetting leftMax = this.dbl("Left Max CPS", "Highest clicks per second.", 12.0, 1.0, 20.0, 0.5).visibleWhen(this.left::get);
   public final BoolSetting cooldown = this.bool("Cooldown Check", "Only hits once the attack cooldown is (almost) full – more damage on 1.9+ combat.", false)
      .visibleWhen(this.left::get);
   public final DoubleSetting cooldownValue = this.dbl("Cooldown", "Required cooldown progress.", 0.9, 0.0, 1.0, 0.01)
      .visibleWhen(() -> this.left.get() && this.cooldown.get());
   public final BoolSetting right = this.bool("Right Click", "Auto-use while holding the use button (blocks, snowballs …).", false);
   public final DoubleSetting rightMin = this.dbl("Right Min CPS", "Lowest clicks per second.", 8.0, 1.0, 20.0, 0.5).visibleWhen(this.right::get);
   public final DoubleSetting rightMax = this.dbl("Right Max CPS", "Highest clicks per second.", 12.0, 1.0, 20.0, 0.5).visibleWhen(this.right::get);
   private double leftAcc;
   private double rightAcc;

   public AutoClicker() {
      super("AutoClicker", Category.COMBAT, "Clicks automatically with a random CPS range while you hold left/right click.");
   }

   private static double cps(DoubleSetting var0, DoubleSetting var1) {
      double var2 = Math.min(var0.get(), var1.get());
      double var4 = Math.max(var0.get(), var1.get());
      return var4 > var2 ? ThreadLocalRandom.current().nextDouble(var2, var4) : var2;
   }

   @Override
   protected void onDisable() {
      this.leftAcc = this.rightAcc = 0.0;
   }

   @Override
   public void onTick() {
      if (mc.field_1755 == null && !mc.field_1724.method_6115()) {
         MinecraftClientAccessor var1 = (MinecraftClientAccessor)mc;
         if (this.left.get() && mc.field_1690.field_1886.method_1434() && (mc.field_1765 == null || mc.field_1765.method_17783() != class_240.field_1332)) {
            this.leftAcc = this.leftAcc + cps(this.leftMin, this.leftMax) / 20.0;

            while (this.leftAcc >= 1.0) {
               this.leftAcc--;
               if (this.cooldown.get() && mc.field_1724.method_7261(0.5F) < this.cooldownValue.getFloat()) {
                  this.leftAcc = Math.min(this.leftAcc, 1.0);
                  break;
               }

               var1.dih$doAttack();
            }
         } else {
            this.leftAcc = 0.0;
         }

         if (this.right.get() && mc.field_1690.field_1904.method_1434()) {
            this.rightAcc = this.rightAcc + cps(this.rightMin, this.rightMax) / 20.0;

            while (this.rightAcc >= 1.0) {
               this.rightAcc--;
               var1.dih$setItemUseCooldown(0);
               var1.dih$doItemUse();
            }
         } else {
            this.rightAcc = 0.0;
         }
      } else {
         this.leftAcc = this.rightAcc = 0.0;
      }
   }

   @Override
   public String getInfo() {
      return (int)Math.round((this.leftMin.get() + this.leftMax.get()) / 2.0) + " CPS";
   }
}
