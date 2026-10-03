package dev.dihclient.modules.movement;

import dev.dihclient.module.Category;
import dev.dihclient.module.Module;
import dev.dihclient.setting.DoubleSetting;
import net.minecraft.class_243;

public class ElytraBoost extends Module {
   public final DoubleSetting amount = this.dbl("Amount", "Forward acceleration per tick.", 0.035, 0.005, 0.3, 0.005).legacy("elytraBoost.amount");

   public ElytraBoost() {
      super("ElytraBoost", Category.MOVEMENT, "Adds a forward velocity boost while gliding and holding forward.");
   }

   @Override
   public void onTick() {
      if (mc.field_1724.method_6128() && mc.field_1690.field_1894.method_1434()) {
         class_243 var1 = mc.field_1724.method_5720();
         mc.field_1724.method_5762(var1.field_1352 * this.amount.get(), var1.field_1351 * this.amount.get() * 0.5, var1.field_1350 * this.amount.get());
      }
   }
}
