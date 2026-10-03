package dev.dihclient.modules.movement;

import dev.dihclient.module.Category;
import dev.dihclient.module.Module;
import dev.dihclient.module.ModuleManager;
import dev.dihclient.setting.DoubleSetting;
import net.minecraft.class_243;

public class Step extends Module {
   public final DoubleSetting velocity = this.dbl("Velocity", "Upward velocity applied when walking into a block.", 0.42, 0.1, 1.5, 0.01)
      .legacy("step.velocity");

   public Step() {
      super("Step", Category.MOVEMENT, "Steps up when colliding horizontally while grounded.");
   }

   @Override
   public void onTick() {
      if (!ModuleManager.on(Flight.class)) {
         if (mc.field_1724.field_5976 && mc.field_1724.method_24828() && !mc.field_1724.method_5715()) {
            class_243 var1 = mc.field_1724.method_18798();
            mc.field_1724.method_18800(var1.field_1352, this.velocity.get(), var1.field_1350);
         }
      }
   }
}
