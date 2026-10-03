package dev.dihclient.modules.movement;

import dev.dihclient.module.Category;
import dev.dihclient.module.Module;
import dev.dihclient.setting.DoubleSetting;

public class NoSlow extends Module {
   public final DoubleSetting multiplier = this.dbl(
         "Multiplier", "Movement multiplier while using items (vanilla 0.2, 1.0 = no slowdown).", 1.0, 0.2, 1.0, 0.01
      )
      .legacy("noSlow.multiplier");

   public NoSlow() {
      super("NoSlow", Category.MOVEMENT, "Controls the movement slowdown while eating, blocking or drawing a bow.");
   }
}
