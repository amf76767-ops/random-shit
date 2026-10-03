package dev.dihclient.modules.movement;

import dev.dihclient.module.Category;
import dev.dihclient.module.Module;
import dev.dihclient.setting.BoolSetting;
import dev.dihclient.setting.DoubleSetting;
import dev.dihclient.setting.EnumSetting;
import dev.dihclient.util.MoveUtil;
import net.minecraft.class_243;

public class Flight extends Module {
   public final EnumSetting<Flight.Mode> mode = this.mode("Mode", "Vanilla: free flight. Glide: slow descent.", Flight.Mode.VANILLA).legacy("flight.mode");
   public final DoubleSetting horizontal = this.dbl("Horizontal Speed", "Horizontal speed.", 0.35, 0.05, 5.0, 0.01).legacy("flight.horizontalSpeed");
   public final DoubleSetting vertical = this.dbl("Vertical Speed", "Up/down speed (jump/sneak).", 0.3, 0.05, 5.0, 0.01).legacy("flight.verticalSpeed");
   public final DoubleSetting glide = this.dbl("Glide Speed", "Descent speed in Glide mode.", 0.08, 0.0, 1.0, 0.01)
      .legacy("flight.glideSpeed")
      .visibleWhen(() -> this.mode.get() == Flight.Mode.GLIDE);
   public final BoolSetting antiKick = this.bool("Anti Kick", "Small downward pulse every second to avoid fly kicks.", true).legacy("flight.antiKick");
   private boolean captured;
   private boolean oldNoGravity;
   private int antiKickTicks;

   public Flight() {
      super("Flight", Category.MOVEMENT, "Client flight with Vanilla and Glide modes, independent speeds and optional anti-kick pulse.");
   }

   @Override
   public void onTick() {
      if (!this.captured) {
         this.oldNoGravity = mc.field_1724.method_5740();
         this.captured = true;
      }

      boolean var1 = this.mode.get() == Flight.Mode.GLIDE;
      mc.field_1724.method_5875(!var1);
      double[] var2 = MoveUtil.direction(this.horizontal.get());
      double var3;
      if (mc.field_1690.field_1903.method_1434()) {
         var3 = this.vertical.get();
      } else if (mc.field_1690.field_1832.method_1434()) {
         var3 = -this.vertical.get();
      } else if (var1) {
         var3 = Math.max(mc.field_1724.method_18798().field_1351, -Math.abs(this.glide.get()));
      } else {
         var3 = 0.0;
      }

      if (!var1 && var3 == 0.0 && this.antiKick.get() && ++this.antiKickTicks >= 20) {
         var3 = -0.04;
         this.antiKickTicks = 0;
      }

      mc.field_1724.method_18800(var2[0], var3, var2[1]);
   }

   @Override
   protected void onDisable() {
      if (mc.field_1724 != null) {
         if (this.captured) {
            mc.field_1724.method_5875(this.oldNoGravity);
         }

         class_243 var1 = mc.field_1724.method_18798();
         mc.field_1724.method_18800(var1.field_1352, 0.0, var1.field_1350);
      }

      this.captured = false;
      this.antiKickTicks = 0;
   }

   @Override
   public String getInfo() {
      return this.mode.displayValue();
   }

   public static enum Mode {
      VANILLA,
      GLIDE;
   }
}
