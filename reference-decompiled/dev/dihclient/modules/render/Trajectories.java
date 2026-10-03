package dev.dihclient.modules.render;

import dev.dihclient.module.Category;
import dev.dihclient.module.Module;
import dev.dihclient.render.Render3D;
import dev.dihclient.setting.ColorSetting;
import dev.dihclient.setting.IntSetting;
import net.minecraft.class_1753;
import net.minecraft.class_1764;
import net.minecraft.class_1799;
import net.minecraft.class_1802;
import net.minecraft.class_238;
import net.minecraft.class_243;
import net.minecraft.class_3532;
import net.minecraft.class_3959;
import net.minecraft.class_3965;
import net.minecraft.class_239.class_240;
import net.minecraft.class_3959.class_242;
import net.minecraft.class_3959.class_3960;

public class Trajectories extends Module {
   public final IntSetting steps = this.integer("Steps", "Simulation steps (ticks).", 48, 10, 200).legacy("trajectories.steps");
   public final ColorSetting color = this.color("Color", "Line colour.", -1);

   public Trajectories() {
      super("Trajectories", Category.RENDER, "Previews the flight path of bows, crossbows, tridents, pearls, snowballs, eggs, potions and wind charges.");
   }

   private double[] physics(class_1799 var1) {
      if (var1.method_7909() instanceof class_1753) {
         float var2 = mc.field_1724.method_6115() ? class_1753.method_7722(mc.field_1724.method_6048()) : 1.0F;
         return var2 < 0.1F ? null : new double[]{var2 * 3.0, 0.05, 0.99};
      } else if (var1.method_7909() instanceof class_1764) {
         return class_1764.method_7781(var1) ? new double[]{3.15, 0.05, 0.99} : null;
      } else if (var1.method_31574(class_1802.field_8547)) {
         return new double[]{2.5, 0.05, 0.99};
      } else if (var1.method_31574(class_1802.field_8634) || var1.method_31574(class_1802.field_8543) || var1.method_31574(class_1802.field_8803)) {
         return new double[]{1.5, 0.03, 0.99};
      } else if (var1.method_31574(class_1802.field_8436) || var1.method_31574(class_1802.field_8150)) {
         return new double[]{0.5, 0.05, 0.99};
      } else if (var1.method_31574(class_1802.field_8287)) {
         return new double[]{0.7, 0.07, 0.99};
      } else {
         return var1.method_31574(class_1802.field_49098) ? new double[]{1.5, 0.0, 1.0} : null;
      }
   }

   @Override
   public void onRender3D(Render3D var1) {
      class_1799 var2 = mc.field_1724.method_6047();
      double[] var3 = this.physics(var2);
      if (var3 == null) {
         var2 = mc.field_1724.method_6079();
         var3 = this.physics(var2);
      }

      if (var3 != null) {
         float var4 = var1.tickDelta();
         float var5 = mc.field_1724.method_5705(var4);
         float var6 = mc.field_1724.method_5695(var4);
         class_243 var7 = new class_243(
            class_3532.method_16436(var4, mc.field_1724.field_6038, mc.field_1724.method_23317()),
            class_3532.method_16436(var4, mc.field_1724.field_5971, mc.field_1724.method_23318()) + mc.field_1724.method_5751() - 0.1,
            class_3532.method_16436(var4, mc.field_1724.field_5989, mc.field_1724.method_23321())
         );
         class_243 var8 = class_243.method_1030(var6, var5).method_1029().method_1021(var3[0]);
         class_243 var9 = mc.field_1724.method_18798();
         var8 = var8.method_1031(var9.field_1352, mc.field_1724.method_24828() ? 0.0 : var9.field_1351, var9.field_1350);
         int var10 = this.color.get();

         for (int var11 = 0; var11 < this.steps.get(); var11++) {
            class_243 var12 = var7.method_1019(var8);
            class_3965 var13 = mc.field_1687.method_17742(new class_3959(var7, var12, class_3960.field_17558, class_242.field_1348, mc.field_1724));
            if (var13.method_17783() != class_240.field_1333) {
               class_243 var14 = var13.method_17784();
               if ((var11 & 1) == 0) {
                  var1.line(var7, var14, var10, false);
               }

               if (var13 instanceof class_3965 var15) {
                  var1.box(new class_238(var15.method_17777()), var10, 40, false);
               }

               return;
            }

            if ((var11 & 1) == 0) {
               var1.line(var7, var12, var10, false);
            }

            var7 = var12;
            var8 = var8.method_1021(var3[2]).method_1023(0.0, var3[1], 0.0);
            if (var12.field_1351 < mc.field_1687.method_31607() - 16) {
               return;
            }
         }
      }
   }
}
