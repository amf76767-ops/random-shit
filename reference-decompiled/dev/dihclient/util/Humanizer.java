package dev.dihclient.util;

import java.util.Random;
import net.minecraft.class_2350;
import net.minecraft.class_243;
import net.minecraft.class_2350.class_2351;

public final class Humanizer {
   private final Random rng = new Random();
   public double amount = 0.25;

   public double amount() {
      return Math.max(0.0, Math.min(1.0, this.amount));
   }

   public float speed(float var1) {
      double var2 = this.amount();
      return (float)(var1 * (1.0 - 0.45 * var2 + this.rng.nextDouble() * 0.6 * var2));
   }

   public float noise() {
      return (float)(1.0 + 2.5 * this.amount());
   }

   public int pause() {
      double var1 = this.amount();
      if (var1 <= 0.0) {
         return 0;
      } else {
         int var3 = this.rng.nextInt(1 + (int)Math.round(4.0 * var1));
         if (this.rng.nextDouble() < 0.07 * var1) {
            var3 += 6 + this.rng.nextInt(12);
         }

         return var3;
      }
   }

   public class_243 jitter(class_243 var1, class_2350 var2, double var3) {
      double var5 = this.amount() * var3;
      if (var5 <= 0.0) {
         return var1;
      } else {
         double var7 = clamp(this.rng.nextGaussian() * var5 * 0.5, var5);
         double var9 = clamp(this.rng.nextGaussian() * var5 * 0.5, var5);
         class_2351 var11 = var2.method_10166();
         if (var11 == class_2351.field_11048) {
            return var1.method_1031(0.0, var7, var9);
         } else {
            return var11 == class_2351.field_11052 ? var1.method_1031(var7, 0.0, var9) : var1.method_1031(var7, var9, 0.0);
         }
      }
   }

   private static double clamp(double var0, double var2) {
      return Math.max(-var2, Math.min(var2, var0));
   }
}
