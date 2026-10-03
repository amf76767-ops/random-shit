package dev.dihclient.modules.movement;

import dev.dihclient.module.Category;
import dev.dihclient.module.Module;
import dev.dihclient.setting.DoubleSetting;
import dev.dihclient.util.MoveUtil;

public class Parkour extends Module {
   public final DoubleSetting lookAhead = this.dbl("Look Ahead", "Distance at which a ledge triggers the jump.", 0.72, 0.1, 2.0, 0.01)
      .legacy("parkour.lookAhead");

   public Parkour() {
      super("Parkour", Category.MOVEMENT, "Automatically jumps when you reach a ledge while moving.");
   }

   @Override
   public void onTick() {
      if (mc.field_1755 == null && !mc.field_1724.method_5715() && MoveUtil.isMoving()) {
         if (MoveUtil.edgeAhead(this.lookAhead.get())) {
            mc.field_1724.method_6043();
         }
      }
   }
}
