package dev.dihclient.modules.basefinding;

import dev.dihclient.module.Category;
import dev.dihclient.module.Module;
import dev.dihclient.setting.BoolSetting;
import dev.dihclient.util.InvUtil;
import net.minecraft.class_1799;
import net.minecraft.class_2680;
import net.minecraft.class_3965;

public class AutoTool extends Module {
   public final BoolSetting restoreSlot = this.bool("Restore Slot", "Returns to the previous slot when you stop mining.", true).legacy("autoTool.restoreSlot");
   public final BoolSetting protectTools = this.bool("Protect Tools", "Never selects tools with less than 10 durability left.", true);
   private int previous = -1;

   public AutoTool() {
      super("AutoTool", Category.BASEFINDING, "Selects the fastest hotbar tool for the block you are mining.");
   }

   @Override
   protected void onDisable() {
      this.previous = -1;
   }

   @Override
   public void onWorldChange() {
      this.previous = -1;
   }

   @Override
   public void onTick() {
      if (mc.field_1690.field_1886.method_1434() && mc.field_1765 instanceof class_3965 var1 && mc.field_1755 == null) {
         class_2680 var8 = mc.field_1687.method_8320(var1.method_17777());
         if (var8.method_26215()) {
            return;
         }

         int var3 = -1;
         float var4 = mc.field_1724.method_6047().method_7924(var8);

         for (int var5 = 0; var5 < 9; var5++) {
            class_1799 var6 = mc.field_1724.method_31548().method_5438(var5);
            if (!this.protectTools.get() || !var6.method_7963() || var6.method_7936() - var6.method_7919() >= 10) {
               float var7 = var6.method_7924(var8);
               if (var7 > var4 + 0.01F) {
                  var4 = var7;
                  var3 = var5;
               }
            }
         }

         if (var3 >= 0 && var3 != InvUtil.selectedSlot()) {
            if (this.previous < 0) {
               this.previous = InvUtil.selectedSlot();
            }

            InvUtil.select(var3);
         }
      } else if (this.previous >= 0) {
         if (this.restoreSlot.get()) {
            InvUtil.select(this.previous);
         }

         this.previous = -1;
      }
   }
}
