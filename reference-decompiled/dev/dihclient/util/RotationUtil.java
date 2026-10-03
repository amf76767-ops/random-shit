package dev.dihclient.util;

import net.minecraft.class_1297;
import net.minecraft.class_238;
import net.minecraft.class_243;
import net.minecraft.class_310;
import net.minecraft.class_3532;

public final class RotationUtil {
   private static final class_310 mc = class_310.method_1551();

   private RotationUtil() {
   }

   public static float[] rotationsTo(class_243 var0) {
      class_243 var1 = mc.field_1724.method_33571();
      double var2 = var0.field_1352 - var1.field_1352;
      double var4 = var0.field_1351 - var1.field_1351;
      double var6 = var0.field_1350 - var1.field_1350;
      double var8 = Math.sqrt(var2 * var2 + var6 * var6);
      float var10 = (float)Math.toDegrees(Math.atan2(var6, var2)) - 90.0F;
      float var11 = (float)(-Math.toDegrees(Math.atan2(var4, var8)));
      return new float[]{class_3532.method_15393(var10), class_3532.method_15363(var11, -90.0F, 90.0F)};
   }

   public static class_243 aimPoint(class_1297 var0) {
      class_243 var1 = mc.field_1724.method_33571();
      class_238 var2 = var0.method_5829();
      double var3 = class_3532.method_15350(var1.field_1352, var2.field_1323, var2.field_1320);
      double var5 = class_3532.method_15350(var1.field_1351, var2.field_1322 + var0.method_17682() * 0.25, var2.field_1325 - 0.1);
      double var7 = class_3532.method_15350(var1.field_1350, var2.field_1321, var2.field_1324);
      return new class_243(var3, var5, var7);
   }

   public static float[] rotationsTo(class_1297 var0) {
      return rotationsTo(aimPoint(var0));
   }

   public static double angleTo(class_1297 var0) {
      float[] var1 = rotationsTo(var0);
      float var2 = class_3532.method_15393(var1[0] - mc.field_1724.method_36454());
      float var3 = var1[1] - mc.field_1724.method_36455();
      return Math.sqrt(var2 * var2 + var3 * var3);
   }

   public static void smoothLook(float[] var0, double var1) {
      float var3 = mc.field_1724.method_36454();
      float var4 = mc.field_1724.method_36455();
      float var5 = class_3532.method_15393(var0[0] - var3);
      float var6 = var0[1] - var4;
      mc.field_1724.method_36456(var3 + var5 * (float)var1);
      mc.field_1724.method_36457(class_3532.method_15363(var4 + var6 * (float)var1, -90.0F, 90.0F));
   }

   public static float approachAngle(float var0, float var1, float var2) {
      float var3 = class_3532.method_15393(var1 - var0);
      return var0 + class_3532.method_15363(var3, -var2, var2);
   }
}
