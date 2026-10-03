package dev.dihclient.modules.combat;

import dev.dihclient.module.Category;
import dev.dihclient.module.Module;
import dev.dihclient.setting.DoubleSetting;
import net.minecraft.class_2828.class_2829;

public class Criticals extends Module {
   public final DoubleSetting offset = this.dbl("Offset", "Height of the short airborne movement sequence.", 0.0625, 1.0E-4, 0.5, 1.0E-4)
      .legacy("criticals.offset");

   public Criticals() {
      super("Criticals", Category.COMBAT, "Sends a short airborne movement sequence right before an attack while you are on ground.");
   }

   public void perform() {
      if (mc.field_1724 != null && mc.field_1724.field_3944 != null) {
         if (mc.field_1724.method_24828() && !mc.field_1724.method_5799() && !mc.field_1724.method_5771() && !mc.field_1724.method_6128()) {
            double var1 = mc.field_1724.method_23317();
            double var3 = mc.field_1724.method_23318();
            double var5 = mc.field_1724.method_23321();
            boolean var7 = mc.field_1724.field_5976;
            mc.field_1724.field_3944.method_52787(new class_2829(var1, var3 + this.offset.get(), var5, false, var7));
            mc.field_1724.field_3944.method_52787(new class_2829(var1, var3, var5, false, var7));
         }
      }
   }
}
