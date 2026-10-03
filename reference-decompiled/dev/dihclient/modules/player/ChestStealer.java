package dev.dihclient.modules.player;

import dev.dihclient.module.Category;
import dev.dihclient.module.Module;
import dev.dihclient.setting.BoolSetting;
import dev.dihclient.setting.IntSetting;
import dev.dihclient.util.InvUtil;
import net.minecraft.class_1661;
import net.minecraft.class_1703;
import net.minecraft.class_1735;
import net.minecraft.class_1799;
import net.minecraft.class_465;
import net.minecraft.class_476;
import net.minecraft.class_488;
import net.minecraft.class_495;

public class ChestStealer extends Module {
   public final IntSetting delay = this.integer("Delay", "Ticks between two quick-moves.", 2, 0, 20).legacy("chestStealer.delayTicks");
   public final BoolSetting autoClose = this.bool("Auto Close", "Closes the container when it is empty.", false);
   private int wait;

   public ChestStealer() {
      super("ChestStealer", Category.PLAYER, "Quick-moves items from chest/shulker/hopper slots into your inventory with a configurable delay.");
   }

   private static boolean fits(class_1799 var0) {
      class_1661 var1 = mc.field_1724.method_31548();

      for (int var2 = 0; var2 < 36; var2++) {
         class_1799 var3 = var1.method_5438(var2);
         if (var3.method_7960() || var3.method_7909() == var0.method_7909() && var3.method_7947() < var3.method_7909().method_7882()) {
            return true;
         }
      }

      return false;
   }

   @Override
   public void onTick() {
      if (!(mc.field_1755 instanceof class_476) && !(mc.field_1755 instanceof class_495) && !(mc.field_1755 instanceof class_488)) {
         this.wait = 0;
      } else if (this.wait > 0) {
         this.wait--;
      } else {
         class_1703 var1 = ((class_465)mc.field_1755).method_17577();

         for (Object var3 : var1.field_7761) {
            class_1735 var4 = (class_1735)var3;
            if (!(var4.field_7871 instanceof class_1661) && !var4.method_7677().method_7960() && fits(var4.method_7677())) {
               InvUtil.quickMove(var1.field_7763, var4.field_7874);
               this.wait = this.delay.get();
               if (this.wait > 0) {
                  return;
               }
            }
         }

         if (this.autoClose.get() && this.wait == 0) {
            mc.field_1724.method_7346();
         }
      }
   }
}
