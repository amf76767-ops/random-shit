package dev.dihclient.util;

import dev.dihclient.modules.render.Freecam;
import net.minecraft.class_2338;
import net.minecraft.class_238;
import net.minecraft.class_243;
import net.minecraft.class_310;
import net.minecraft.class_315;

public final class MoveUtil {
   private static final class_310 mc = class_310.method_1551();

   private MoveUtil() {
   }

   public static boolean isMoving() {
      if (Freecam.active() != null) {
         return false;
      } else {
         class_315 var0 = mc.field_1690;
         return var0.field_1894.method_1434() || var0.field_1881.method_1434() || var0.field_1913.method_1434() || var0.field_1849.method_1434();
      }
   }

   public static double[] direction(double var0) {
      return direction(var0, mc.field_1724.method_36454());
   }

   public static double[] direction(double var0, float var2) {
      if (Freecam.active() != null) {
         return new double[]{0.0, 0.0};
      } else {
         class_315 var3 = mc.field_1690;
         double var4 = (var3.field_1894.method_1434() ? 1 : 0) - (var3.field_1881.method_1434() ? 1 : 0);
         double var6 = (var3.field_1913.method_1434() ? 1 : 0) - (var3.field_1849.method_1434() ? 1 : 0);
         if (var4 == 0.0 && var6 == 0.0) {
            return new double[]{0.0, 0.0};
         } else {
            double var8 = Math.sqrt(var4 * var4 + var6 * var6);
            var4 /= var8;
            var6 /= var8;
            double var10 = Math.toRadians(var2);
            double var12 = Math.sin(var10);
            double var14 = Math.cos(var10);
            double var16 = (var6 * var14 - var4 * var12) * var0;
            double var18 = (var4 * var14 + var6 * var12) * var0;
            return new double[]{var16, var18};
         }
      }
   }

   public static double horizontalSpeed() {
      class_243 var0 = mc.field_1724.method_18798();
      return Math.sqrt(var0.field_1352 * var0.field_1352 + var0.field_1350 * var0.field_1350);
   }

   public static boolean edgeAhead(double var0) {
      if (mc.field_1724 != null && mc.field_1687 != null && mc.field_1724.method_24828()) {
         double[] var2 = direction(var0);
         if (var2[0] == 0.0 && var2[1] == 0.0) {
            return false;
         } else {
            class_238 var3 = mc.field_1724.method_5829().method_989(var2[0], -0.6, var2[1]).method_35580(0.05, 0.0, 0.05);
            return mc.field_1687.method_8587(mc.field_1724, var3);
         }
      } else {
         return false;
      }
   }

   public static class_2338 below() {
      return class_2338.method_49637(mc.field_1724.method_23317(), mc.field_1724.method_23318() - 0.5, mc.field_1724.method_23321());
   }
}
