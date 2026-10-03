package dev.dihclient.modules.movement;

import dev.dihclient.module.Category;
import dev.dihclient.module.Module;
import dev.dihclient.module.ModuleManager;
import dev.dihclient.setting.DoubleSetting;
import net.minecraft.class_238;
import net.minecraft.class_243;

public class ReverseStep extends Module {
   public final DoubleSetting speed = this.dbl("Speed", "Downward velocity when a block is close below you.", 0.85, 0.1, 3.0, 0.01).legacy("reverseStep.speed");
   private boolean wasOnGround;

   public ReverseStep() {
      super("ReverseStep", Category.MOVEMENT, "Pulls you down quickly when you walk off a small ledge.");
   }

   @Override
   public void onTick() {
      boolean var1 = this.wasOnGround;
      this.wasOnGround = mc.field_1724.method_24828();
      if (var1
         && !ModuleManager.on(Flight.class)
         && !mc.field_1724.method_24828()
         && !mc.field_1724.method_5799()
         && !mc.field_1724.method_5771()
         && !mc.field_1724.method_6128()
         && !mc.field_1690.field_1903.method_1434()) {
         class_243 var2 = mc.field_1724.method_18798();
         if (!(var2.field_1351 > 0.0) && !(var2.field_1351 < -0.8)) {
            class_238 var3 = mc.field_1724.method_5829().method_989(0.0, -1.5, 0.0);
            if (!mc.field_1687.method_8587(mc.field_1724, var3)) {
               mc.field_1724.method_18800(var2.field_1352, -this.speed.get(), var2.field_1350);
            }
         }
      }
   }
}
