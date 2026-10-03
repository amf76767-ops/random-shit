package dev.dihclient.modules.player;

import dev.dihclient.gui.TextInputScreen;
import dev.dihclient.module.Category;
import dev.dihclient.module.Module;
import dev.dihclient.util.KeyUtil;
import net.minecraft.class_304;
import net.minecraft.class_342;
import net.minecraft.class_408;
import net.minecraft.class_437;
import net.minecraft.class_471;
import net.minecraft.class_473;
import net.minecraft.class_481;
import net.minecraft.class_490;
import net.minecraft.class_7743;

public class InventoryMove extends Module {
   public InventoryMove() {
      super("InventoryMove", Category.PLAYER, "Keeps movement keys working while inventory/container screens are open.");
   }

   @Override
   public void onTick() {
      class_437 var1 = mc.field_1755;
      if (var1 != null
         && !(var1 instanceof class_408)
         && !(var1 instanceof class_7743)
         && !(var1 instanceof class_471)
         && !(var1 instanceof class_473)
         && !(var1 instanceof TextInputScreen var2 && var2.isTyping())
         && !(var1 instanceof class_481)
         && (var1 instanceof class_490 || !(var1.method_25399() instanceof class_342))) {
         class_304[] var3 = new class_304[]{
            mc.field_1690.field_1894,
            mc.field_1690.field_1881,
            mc.field_1690.field_1913,
            mc.field_1690.field_1849,
            mc.field_1690.field_1903,
            mc.field_1690.field_1867,
            mc.field_1690.field_1832
         };

         for (class_304 var7 : var3) {
            var7.method_23481(KeyUtil.isPhysicallyDown(var7));
         }
      }
   }
}
