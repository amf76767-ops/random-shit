package dev.dihclient.modules.render;

import dev.dihclient.module.Category;
import dev.dihclient.module.Module;
import dev.dihclient.setting.BoolSetting;
import dev.dihclient.util.CombatUtil;
import net.minecraft.class_1657;
import net.minecraft.class_3966;

public class TargetHud extends Module {
   public final BoolSetting crosshairTarget = this.bool(
      "Crosshair Target", "Also shows the player under your crosshair when no combat module has a target.", true
   );

   public TargetHud() {
      super("Target HUD", Category.RENDER, "Shows the current combat target's head, name, health and distance. Move it in the HUD editor.");
   }

   public class_1657 current() {
      class_1657 var1 = CombatUtil.target();
      if (var1 == null && this.crosshairTarget.get() && mc.field_1765 instanceof class_3966 var2 && var2.method_17782() instanceof class_1657 var3) {
         var1 = var3;
      }

      return var1;
   }
}
