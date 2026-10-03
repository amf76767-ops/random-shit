package dev.dihclient.modules.player;

import dev.dihclient.module.Category;
import dev.dihclient.module.Module;
import dev.dihclient.setting.BoolSetting;
import dev.dihclient.setting.IntSetting;
import dev.dihclient.util.InvUtil;
import dev.dihclient.util.KeyUtil;
import net.minecraft.class_1799;
import net.minecraft.class_1802;
import net.minecraft.class_9334;

public class AutoEat extends Module {
   public final IntSetting hunger = this.integer("Hunger", "Eats when the food level is at or below this value.", 14, 1, 19).legacy("autoEat.hunger");
   public final BoolSetting restoreSlot = this.bool("Restore Slot", "Returns to the previous slot after eating.", true).legacy("autoEat.restoreSlot");
   private boolean eating;
   private int previous = -1;

   public AutoEat() {
      super("AutoEat", Category.PLAYER, "Selects food from your hotbar and eats when hunger drops below the threshold.");
   }

   private static boolean isGoodFood(class_1799 var0) {
      return var0.method_58694(class_9334.field_50075) == null
         ? false
         : !var0.method_31574(class_1802.field_8511)
            && !var0.method_31574(class_1802.field_8680)
            && !var0.method_31574(class_1802.field_8635)
            && !var0.method_31574(class_1802.field_8323)
            && !var0.method_31574(class_1802.field_8233)
            && !var0.method_31574(class_1802.field_8766);
   }

   @Override
   public void onTick() {
      int var1 = mc.field_1724.method_7344().method_7586();
      if (this.eating) {
         boolean var3 = isGoodFood(mc.field_1724.method_6047());
         if (var1 < 20 && var3 && mc.field_1755 == null) {
            mc.field_1690.field_1904.method_23481(true);
         } else {
            this.stop();
         }
      } else if (mc.field_1755 == null && var1 <= this.hunger.get() && !mc.field_1724.method_6115()) {
         int var2 = InvUtil.findHotbar(AutoEat::isGoodFood);
         if (var2 >= 0) {
            this.previous = InvUtil.selectedSlot();
            InvUtil.select(var2);
            mc.field_1690.field_1904.method_23481(true);
            this.eating = true;
         }
      }
   }

   private void stop() {
      this.eating = false;
      mc.field_1690.field_1904.method_23481(KeyUtil.isPhysicallyDown(mc.field_1690.field_1904));
      if (this.restoreSlot.get() && this.previous >= 0) {
         InvUtil.select(this.previous);
      }

      this.previous = -1;
   }

   @Override
   protected void onDisable() {
      if (this.eating && mc.field_1724 != null) {
         this.stop();
      }

      this.eating = false;
   }

   public boolean isEating() {
      return this.eating;
   }
}
