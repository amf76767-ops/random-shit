package dev.dihclient.modules.movement;

import dev.dihclient.module.ModuleManager;
import dev.dihclient.setting.DoubleSetting;
import net.minecraft.class_243;

public class HighJump extends JumpKeyModule {
   public final DoubleSetting height = this.dbl("Height", "Vertical velocity of a ground jump (vanilla 0.42).", 0.75, 0.42, 3.0, 0.01).legacy("highJump.y");

   public HighJump() {
      super("HighJump", "Replaces the ground jump velocity with a configurable value.");
   }

   @Override
   public void onTick() {
      boolean var1 = this.jumpPressedNow();
      if (!ModuleManager.on(Flight.class) && var1 && this.fromGround && mc.field_1724.method_18798().field_1351 > 0.0) {
         class_243 var2 = mc.field_1724.method_18798();
         mc.field_1724.method_18800(var2.field_1352, this.height.get(), var2.field_1350);
      }
   }
}
