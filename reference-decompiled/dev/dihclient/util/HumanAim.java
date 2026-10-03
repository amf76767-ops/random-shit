package dev.dihclient.util;

import java.util.Random;
import net.minecraft.class_243;
import net.minecraft.class_310;
import net.minecraft.class_3532;
import net.minecraft.class_746;

public final class HumanAim {
   private static final class_310 mc = class_310.method_1551();
   private static final Random RNG = new Random();
   private static final float STEP = 0.15F;

   private HumanAim() {
   }

   public static float distance(float var0, float var1) {
      class_746 var2 = mc.field_1724;
      if (var2 == null) {
         return 999.0F;
      } else {
         float var3 = class_3532.method_15393(var0 - var2.method_36454());
         float var4 = var1 - var2.method_36455();
         return (float)Math.sqrt(var3 * var3 + var4 * var4);
      }
   }

   public static boolean step(float var0, float var1, float var2, float var3) {
      return step(var0, var1, var2, var3, 1.0F);
   }

   public static boolean step(float var0, float var1, float var2, float var3, float var4) {
      class_746 var5 = mc.field_1724;
      if (var5 == null) {
         return false;
      } else {
         var1 = class_3532.method_15363(var1, -90.0F, 90.0F);
         float var6 = var5.method_36454();
         float var7 = var5.method_36455();
         float var8 = class_3532.method_15393(var0 - var6);
         float var9 = var1 - var7;
         float var10 = (float)Math.sqrt(var8 * var8 + var9 * var9);
         if (var10 <= var3) {
            var5.method_36456(var6 + snap(var8));
            var5.method_36457(class_3532.method_15363(var7 + snap(var9), -90.0F, 90.0F));
            return true;
         } else {
            float var11 = Math.min(var2, Math.max(2.5F, var10 * 0.55F));
            var11 *= 0.85F + RNG.nextFloat() * 0.3F;
            float var12 = Math.min(1.0F, var11 / var10);
            float var13 = var8 * var12;
            float var14 = var9 * var12;
            if (var10 > 10.0F) {
               var13 += (RNG.nextFloat() - 0.5F) * 0.8F * var4;
               var14 += (RNG.nextFloat() - 0.5F) * 0.5F * var4;
            } else if (var4 > 1.0F && var10 > 3.0F) {
               var13 += (RNG.nextFloat() - 0.5F) * 0.25F * (var4 - 1.0F);
               var14 += (RNG.nextFloat() - 0.5F) * 0.15F * (var4 - 1.0F);
            }

            var5.method_36456(var6 + snap(var13));
            var5.method_36457(class_3532.method_15363(var7 + snap(var14), -90.0F, 90.0F));
            return distance(var0, var1) <= var3;
         }
      }
   }

   public static boolean stepTo(class_243 var0, float var1, float var2) {
      return stepTo(var0, var1, var2, 1.0F);
   }

   public static boolean stepTo(class_243 var0, float var1, float var2, float var3) {
      float[] var4 = RotationUtil.rotationsTo(var0);
      return step(var4[0], var4[1], var1, var2, var3);
   }

   private static float snap(float var0) {
      return Math.round(var0 / 0.15F) * 0.15F;
   }
}
