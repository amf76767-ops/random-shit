package dev.dihclient.modules.combat;

import dev.dihclient.module.Category;
import dev.dihclient.module.Module;
import dev.dihclient.setting.BoolSetting;
import dev.dihclient.setting.DoubleSetting;
import dev.dihclient.util.CombatUtil;
import dev.dihclient.util.InvUtil;
import net.minecraft.class_1657;
import net.minecraft.class_1743;
import net.minecraft.class_1799;
import net.minecraft.class_1802;
import net.minecraft.class_3489;
import net.minecraft.class_3966;
import net.minecraft.class_9285;
import net.minecraft.class_9334;
import net.minecraft.class_9362;

public class AutoWeapon extends Module {
   public final DoubleSetting range = this.dbl("Range", "Selects a weapon when a player is this close under the crosshair.", 5.0, 1.0, 8.0, 0.1)
      .legacy("autoWeapon.range");
   public final BoolSetting restore = this.bool("Restore", "Returns to your previous slot when no target is left.", true).legacy("autoWeapon.restore");
   private int previous = -1;

   public AutoWeapon() {
      super("AutoWeapon", Category.COMBAT, "Selects the best sword/axe/mace from your hotbar when you fight a player.");
   }

   public static int weaponScore(class_1799 var0, boolean var1) {
      if (var0 != null && !var0.method_7960()) {
         int var2;
         if (var0.method_7909() instanceof class_9362) {
            var2 = 90;
         } else if (var0.method_7909() instanceof class_1743) {
            var2 = var1 ? 200 : 70;
         } else if (var0.method_31573(class_3489.field_42611)) {
            var2 = 80;
         } else {
            if (!var0.method_31574(class_1802.field_8547)) {
               return 0;
            }

            var2 = 60;
         }

         class_9285 var3 = (class_9285)var0.method_58694(class_9334.field_49636);
         int var4 = var3 == null ? 0 : var3.comp_2393().size();
         if (var0.method_31574(class_1802.field_22022) || var0.method_31574(class_1802.field_22025)) {
            var4 += 8;
         } else if (var0.method_31574(class_1802.field_8802) || var0.method_31574(class_1802.field_8556)) {
            var4 += 6;
         } else if (var0.method_31574(class_1802.field_8371) || var0.method_31574(class_1802.field_8475)) {
            var4 += 4;
         } else if (var0.method_31574(class_1802.field_8528) || var0.method_31574(class_1802.field_8062)) {
            var4 += 2;
         }

         return var2 + var4 + (var0.method_7942() ? 5 : 0);
      } else {
         return 0;
      }
   }

   public static int bestSlot(boolean var0) {
      int var1 = -1;
      int var2 = 0;

      for (int var3 = 0; var3 < 9; var3++) {
         int var4 = weaponScore(mc.field_1724.method_31548().method_5438(var3), var0);
         if (var4 > var2) {
            var2 = var4;
            var1 = var3;
         }
      }

      return var1;
   }

   public void selectWeapon() {
      int var1 = bestSlot(false);
      if (var1 >= 0 && var1 != InvUtil.selectedSlot()) {
         if (this.previous < 0) {
            this.previous = InvUtil.selectedSlot();
         }

         InvUtil.select(var1);
      }
   }

   @Override
   public void onTick() {
      boolean var1 = CombatUtil.target() != null
         || mc.field_1765 instanceof class_3966 var3
            && var3.method_17782() instanceof class_1657 var2
            && CombatUtil.isValidTarget(var2)
            && mc.field_1724.method_5739(var2) <= this.range.get();
      if (var1) {
         this.selectWeapon();
      } else if (this.previous >= 0) {
         if (this.restore.get()) {
            InvUtil.select(this.previous);
         }

         this.previous = -1;
      }
   }

   @Override
   protected void onDisable() {
      this.previous = -1;
   }
}
