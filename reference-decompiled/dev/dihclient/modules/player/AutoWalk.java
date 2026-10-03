package dev.dihclient.modules.player;

import dev.dihclient.module.Category;
import dev.dihclient.module.Module;
import dev.dihclient.util.KeyUtil;

public class AutoWalk extends Module {
   public AutoWalk() {
      super("AutoWalk", Category.PLAYER, "Holds your forward movement key until the module is disabled.");
   }

   @Override
   public void onTick() {
      if (mc.field_1755 == null) {
         mc.field_1690.field_1894.method_23481(true);
      }
   }

   @Override
   protected void onDisable() {
      if (mc.field_1690 != null) {
         mc.field_1690.field_1894.method_23481(KeyUtil.isPhysicallyDown(mc.field_1690.field_1894));
      }
   }
}
