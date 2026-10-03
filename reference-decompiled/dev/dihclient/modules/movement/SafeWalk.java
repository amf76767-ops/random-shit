package dev.dihclient.modules.movement;

import dev.dihclient.module.Category;
import dev.dihclient.module.Module;
import dev.dihclient.modules.world.Goto;
import dev.dihclient.setting.DoubleSetting;
import dev.dihclient.util.KeyUtil;
import dev.dihclient.util.MoveUtil;

public class SafeWalk extends Module {
   public final DoubleSetting lookAhead = this.dbl("Look Ahead", "How far ahead ledges are detected.", 0.7, 0.1, 2.0, 0.01).legacy("safeWalk.lookAhead");
   private boolean forced;

   public SafeWalk() {
      super("SafeWalk", Category.MOVEMENT, "Sneaks automatically at ledges while you are moving on the ground.");
   }

   @Override
   public void onTick() {
      boolean var1 = mc.field_1755 == null && MoveUtil.edgeAhead(this.lookAhead.get()) && !Goto.plannedDrop();
      if (var1) {
         mc.field_1690.field_1832.method_23481(true);
         this.forced = true;
      } else if (this.forced) {
         mc.field_1690.field_1832.method_23481(KeyUtil.isPhysicallyDown(mc.field_1690.field_1832) || Goto.holdingSneak());
         this.forced = false;
      }
   }

   @Override
   protected void onDisable() {
      if (this.forced && mc.field_1690 != null) {
         mc.field_1690.field_1832.method_23481(KeyUtil.isPhysicallyDown(mc.field_1690.field_1832) || Goto.holdingSneak());
      }

      this.forced = false;
   }
}
