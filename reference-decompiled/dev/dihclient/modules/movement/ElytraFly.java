package dev.dihclient.modules.movement;

import dev.dihclient.module.Category;
import dev.dihclient.module.Module;
import dev.dihclient.setting.BoolSetting;
import dev.dihclient.setting.DoubleSetting;
import dev.dihclient.util.MoveUtil;

public class ElytraFly extends Module {
   public final DoubleSetting speed = this.dbl("Speed", "Horizontal speed while gliding.", 0.8, 0.1, 5.0, 0.01).legacy("elytraFly.speed");
   public final DoubleSetting vertical = this.dbl("Vertical", "Up/down speed (jump/sneak).", 0.35, 0.05, 3.0, 0.01).legacy("elytraFly.vertical");
   public final BoolSetting holdAltitude = this.bool("Hold Altitude", "Keeps your height when no vertical key is held.", false)
      .legacy("elytraFly.holdAltitude");

   public ElytraFly() {
      super("ElytraFly", Category.MOVEMENT, "Directly controls horizontal/vertical velocity while gliding with an elytra.");
   }

   @Override
   public void onTick() {
      if (mc.field_1724.method_6128()) {
         double[] var1 = MoveUtil.direction(this.speed.get());
         double var2;
         if (mc.field_1690.field_1903.method_1434()) {
            var2 = this.vertical.get();
         } else if (mc.field_1690.field_1832.method_1434()) {
            var2 = -this.vertical.get();
         } else {
            var2 = this.holdAltitude.get() ? 0.0 : mc.field_1724.method_18798().field_1351;
         }

         mc.field_1724.method_18800(var1[0], var2, var1[1]);
      }
   }
}
