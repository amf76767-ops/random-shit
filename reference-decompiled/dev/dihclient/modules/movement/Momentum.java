package dev.dihclient.modules.movement;

import dev.dihclient.module.Category;
import dev.dihclient.module.Module;
import dev.dihclient.module.ModuleManager;
import dev.dihclient.setting.BoolSetting;
import dev.dihclient.setting.DoubleSetting;
import dev.dihclient.setting.EnumSetting;
import dev.dihclient.util.MoveUtil;

public class Momentum extends Module {
   public final EnumSetting<Momentum.Mode> mode = this.mode("Mode", "Strafe: constant speed. BHop: jumps automatically.", Momentum.Mode.STRAFE)
      .legacy("momentum.mode");
   public final DoubleSetting speed = this.dbl("Speed", "Horizontal speed.", 0.32, 0.05, 2.0, 0.01).legacy("momentum.speed");
   public final DoubleSetting airMultiplier = this.dbl("Air Multiplier", "BHop speed multiplier while airborne.", 0.92, 0.1, 2.0, 0.01)
      .legacy("momentum.airMultiplier")
      .visibleWhen(() -> this.mode.get() == Momentum.Mode.BHOP);
   public final BoolSetting autoJump = this.bool("Auto Jump", "Jumps automatically in BHop mode.", true)
      .legacy("momentum.autoJump")
      .visibleWhen(() -> this.mode.get() == Momentum.Mode.BHOP);

   public Momentum() {
      super("Momentum", Category.MOVEMENT, "Movement speed control with Strafe and BHop modes.");
   }

   @Override
   public void onTick() {
      if (!ModuleManager.on(Flight.class) && MoveUtil.isMoving() && !mc.field_1724.method_6128()) {
         boolean var1 = this.mode.get() == Momentum.Mode.BHOP;
         if (var1 && mc.field_1724.method_24828() && this.autoJump.get()) {
            mc.field_1724.method_6043();
         }

         double var2 = var1 && !mc.field_1724.method_24828() ? this.airMultiplier.get() : 1.0;
         double[] var4 = MoveUtil.direction(this.speed.get() * var2);
         mc.field_1724.method_18800(var4[0], mc.field_1724.method_18798().field_1351, var4[1]);
      }
   }

   @Override
   public String getInfo() {
      return this.mode.displayValue();
   }

   public static enum Mode {
      STRAFE,
      BHOP;
   }
}
