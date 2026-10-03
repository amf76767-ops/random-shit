package dev.dihclient.modules.movement;

import dev.dihclient.module.Category;
import dev.dihclient.module.Module;
import dev.dihclient.module.ModuleManager;
import dev.dihclient.setting.BoolSetting;
import dev.dihclient.util.MoveUtil;

public class Sprint extends Module {
   public final BoolSetting onlyForward = this.bool("Only Forward", "Only sprints while the forward key is held.", true).legacy("sprint.onlyForward");

   public Sprint() {
      super("Sprint", Category.MOVEMENT, "Keeps sprint active while you move.");
   }

   @Override
   public void onTick() {
      boolean var1 = this.onlyForward.get() ? mc.field_1690.field_1894.method_1434() : MoveUtil.isMoving();
      if (var1 && !mc.field_1724.method_5715() && !mc.field_1724.field_5976) {
         if (mc.field_1724.method_7344().method_7586() > 6 || mc.field_1724.method_31549().field_7478) {
            if (!mc.field_1724.method_6115() || ModuleManager.on(NoSlow.class)) {
               mc.field_1724.method_5728(true);
            }
         }
      }
   }
}
