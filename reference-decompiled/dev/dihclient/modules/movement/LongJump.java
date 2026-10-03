package dev.dihclient.modules.movement;

import dev.dihclient.module.ModuleManager;
import dev.dihclient.setting.DoubleSetting;
import dev.dihclient.util.MoveUtil;

public class LongJump extends JumpKeyModule {
   public final DoubleSetting speed = this.dbl("Speed", "Horizontal boost of a ground jump.", 0.8, 0.1, 3.0, 0.01).legacy("longJump.speed");
   public final DoubleSetting height = this.dbl("Height", "Vertical velocity of the jump.", 0.42, 0.1, 1.5, 0.01).legacy("longJump.y");

   public LongJump() {
      super("LongJump", "Boosts a normal ground jump forward by a configurable amount.");
   }

   @Override
   public void onTick() {
      boolean var1 = this.jumpPressedNow();
      if (!ModuleManager.on(Flight.class) && var1 && this.fromGround && mc.field_1724.method_18798().field_1351 > 0.0 && MoveUtil.isMoving()) {
         double[] var2 = MoveUtil.direction(this.speed.get());
         mc.field_1724.method_18800(var2[0], this.height.get(), var2[1]);
      }
   }
}
