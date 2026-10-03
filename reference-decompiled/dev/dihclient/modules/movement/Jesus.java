package dev.dihclient.modules.movement;

import dev.dihclient.module.Category;
import dev.dihclient.module.Module;
import dev.dihclient.setting.BoolSetting;
import dev.dihclient.setting.EnumSetting;
import net.minecraft.class_2338;
import net.minecraft.class_243;
import net.minecraft.class_3486;
import net.minecraft.class_3610;

public class Jesus extends Module {
   public final EnumSetting<Jesus.Mode> mode = this.mode("Mode", "Solid: stand on the surface. Bob: float up and bob.", Jesus.Mode.SOLID);
   public final BoolSetting lava = this.bool("Lava", "Also works on lava.", true);
   public final BoolSetting sneakSink = this.bool("Sneak To Sink", "Hold sneak to go under.", true);

   public Jesus() {
      super("Jesus", Category.MOVEMENT, "Walk on water and lava. Sneak to dive.");
   }

   private boolean isFluid(class_3610 var1) {
      return var1.method_15769() ? false : var1.method_15767(class_3486.field_15517) || this.lava.get() && var1.method_15767(class_3486.field_15518);
   }

   @Override
   public void onTick() {
      if (!mc.field_1724.method_31549().field_7479 && !mc.field_1724.method_6128()) {
         if (!this.sneakSink.get() || !mc.field_1690.field_1832.method_1434()) {
            boolean var1 = mc.field_1724.method_5799() || this.lava.get() && mc.field_1724.method_5771();
            class_243 var2 = mc.field_1724.method_18798();
            if (var1) {
               mc.field_1724.method_18800(var2.field_1352, this.mode.get() == Jesus.Mode.BOB ? 0.11 : 0.2, var2.field_1350);
            } else if (this.mode.get() == Jesus.Mode.SOLID) {
               double var3 = mc.field_1724.method_23318();
               class_2338 var5 = class_2338.method_49637(mc.field_1724.method_23317(), var3 - 0.05, mc.field_1724.method_23321());
               class_3610 var6 = mc.field_1687.method_8316(var5);
               if (this.isFluid(var6) && mc.field_1687.method_8316(var5.method_10084()).method_15769()) {
                  double var7 = var5.method_10264() + var6.method_15763(mc.field_1687, var5);
                  if (var2.field_1351 <= 0.0 && var3 - var7 < 0.15) {
                     mc.field_1724.method_5814(mc.field_1724.method_23317(), var7, mc.field_1724.method_23321());
                     mc.field_1724.method_18800(var2.field_1352, mc.field_1690.field_1903.method_1434() ? 0.42 : 0.0, var2.field_1350);
                     mc.field_1724.method_24830(true);
                     mc.field_1724.field_6017 = 0.0;
                  }
               }
            }
         }
      }
   }

   @Override
   public String getInfo() {
      return this.mode.displayValue();
   }

   public static enum Mode {
      SOLID,
      BOB;
   }
}
