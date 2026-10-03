package dev.dihclient.modules.combat;

import dev.dihclient.module.Category;
import dev.dihclient.module.Module;
import dev.dihclient.setting.DoubleSetting;

public class HitBoxes extends Module {
   public final DoubleSetting expand = this.dbl("Expand", "Extra targeting margin around other players (blocks).", 0.3, 0.0, 1.0, 0.01)
      .legacy("hitboxes.expand");

   public HitBoxes() {
      super("HitBoxes", Category.COMBAT, "Expands the crosshair targeting margin of other players and the combat acquisition radius.");
   }

   @Override
   public String getInfo() {
      return this.expand.displayValue();
   }
}
