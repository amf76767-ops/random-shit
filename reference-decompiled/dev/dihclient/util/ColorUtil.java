package dev.dihclient.util;

public final class ColorUtil {
   private ColorUtil() {
   }

   public static int argb(int var0, int var1, int var2, int var3) {
      return (var0 & 0xFF) << 24 | (var1 & 0xFF) << 16 | (var2 & 0xFF) << 8 | var3 & 0xFF;
   }

   public static int withAlpha(int var0, int var1) {
      return var0 & 16777215 | (var1 & 0xFF) << 24;
   }

   public static int blend(int var0, int var1, float var2) {
      var2 = Math.max(0.0F, Math.min(1.0F, var2));
      int var3 = var0 >>> 24;
      int var4 = var0 >> 16 & 0xFF;
      int var5 = var0 >> 8 & 0xFF;
      int var6 = var0 & 0xFF;
      int var7 = var1 >>> 24;
      int var8 = var1 >> 16 & 0xFF;
      int var9 = var1 >> 8 & 0xFF;
      int var10 = var1 & 0xFF;
      return argb(
         (int)(var3 + (var7 - var3) * var2), (int)(var4 + (var8 - var4) * var2), (int)(var5 + (var9 - var5) * var2), (int)(var6 + (var10 - var6) * var2)
      );
   }

   public static int health(float var0) {
      var0 = Math.max(0.0F, Math.min(1.0F, var0));
      return var0 > 0.5F ? blend(-10166, -11740828, (var0 - 0.5F) * 2.0F) : blend(-46261, -10166, var0 * 2.0F);
   }

   public static int darker(int var0, float var1) {
      int var2 = (int)((var0 >> 16 & 0xFF) * var1);
      int var3 = (int)((var0 >> 8 & 0xFF) * var1);
      int var4 = (int)((var0 & 0xFF) * var1);
      return argb(var0 >>> 24, var2, var3, var4);
   }

   public static float[] rgbFloats(int var0) {
      return new float[]{(var0 >> 16 & 0xFF) / 255.0F, (var0 >> 8 & 0xFF) / 255.0F, (var0 & 0xFF) / 255.0F, (var0 >>> 24) / 255.0F};
   }
}
