package dev.dihclient.modules.movement;

import dev.dihclient.module.Category;
import dev.dihclient.module.Module;
import dev.dihclient.module.ModuleManager;
import dev.dihclient.setting.DoubleSetting;
import dev.dihclient.util.MoveUtil;
import net.minecraft.class_243;

public class Spider extends Module {
   public final DoubleSetting speed = this.dbl("Speed", "Climbing speed.", 0.25, 0.05, 1.0, 0.01).legacy("spider.speed");

   public Spider() {
      super("Spider", Category.MOVEMENT, "Climbs upward while you are pushing into a wall.");
   }

   @Override
   public void onTick() {
      if (!ModuleManager.on(Flight.class) && mc.field_1724.field_5976 && MoveUtil.isMoving()) {
         class_243 var1 = mc.field_1724.method_18798();
         mc.field_1724.method_18800(var1.field_1352, this.speed.get(), var1.field_1350);
         mc.field_1724.field_6017 = 0.0;
      }
   }
}
