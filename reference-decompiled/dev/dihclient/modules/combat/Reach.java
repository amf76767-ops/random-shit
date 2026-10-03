package dev.dihclient.modules.combat;

import dev.dihclient.module.Category;
import dev.dihclient.module.Module;
import dev.dihclient.setting.DoubleSetting;

public class Reach extends Module {
   public final DoubleSetting entity = this.dbl("Entity", "Entity interaction range.", 4.5, 3.0, 8.0, 0.1).legacy("reach.entity");
   public final DoubleSetting block = this.dbl("Block", "Block interaction range.", 5.0, 4.5, 8.0, 0.1).legacy("reach.block");

   public Reach() {
      super("Reach", Category.COMBAT, "Overrides your local block/entity interaction range.");
   }

   @Override
   public String getInfo() {
      return this.entity.displayValue();
   }
}
