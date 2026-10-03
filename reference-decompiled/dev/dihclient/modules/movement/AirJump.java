package dev.dihclient.modules.movement;

import dev.dihclient.module.ModuleManager;
import dev.dihclient.setting.DoubleSetting;
import net.minecraft.class_243;

public class AirJump extends JumpKeyModule {
   public final DoubleSetting height = this.dbl("Height", "Upward velocity of an air jump.", 0.42, 0.1, 1.5, 0.01).legacy("airJump.y");

   public AirJump() {
      super("AirJump", "Allows a new upward impulse whenever the jump key is pressed in mid-air.");
   }

   @Override
   public void onTick() {
      boolean var1 = this.jumpPressedNow();
      if (!ModuleManager.on(Flight.class) && var1 && !this.fromGround && !mc.field_1724.method_24828()) {
         class_243 var2 = mc.field_1724.method_18798();
         mc.field_1724.method_18800(var2.field_1352, this.height.get(), var2.field_1350);
         mc.field_1724.field_6017 = 0.0;
      }
   }
}
