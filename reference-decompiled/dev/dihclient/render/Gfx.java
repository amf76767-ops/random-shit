package dev.dihclient.render;

import dev.dihclient.gui.theme.Skin;
import dev.dihclient.gui.theme.Theme;
import dev.dihclient.util.ColorUtil;
import net.minecraft.class_310;
import net.minecraft.class_327;
import net.minecraft.class_332;

public final class Gfx {
   public static final int BG = -233959403;
   public static final int BG_SOFT = -15328992;
   public static final int BG_LIGHT = -14868182;
   public static final int BORDER = 587202559;
   public static final int TEXT = -1446670;
   public static final int MUTED = -7564380;
   public static final int GREEN = -11870592;
   public static final int RED = -495247;
   public static final int ORANGE = -278748;
   public static final int SHADOW = 1426063360;
   private static boolean textShadow = true;
   private static final int[][] INSETS = new int[65][];
   private static final String[] CHARS = new String[128];

   private Gfx() {
   }

   public static class_327 font() {
      return class_310.method_1551().field_1772;
   }

   public static void setTextShadow(boolean var0) {
      textShadow = var0;
   }

   private static int inset(int var0, int var1) {
      double var2 = var0 - var1 - 0.5;
      return (int)Math.round(var0 - Math.sqrt(Math.max(0.0, var0 * var0 - var2 * var2)));
   }

   private static int[] insets(int var0) {
      int[] var1 = var0 < INSETS.length ? INSETS[var0] : null;
      if (var1 == null) {
         var1 = new int[var0];

         for (int var2 = 0; var2 < var0; var2++) {
            var1[var2] = inset(var0, var2);
         }

         if (var0 < INSETS.length) {
            INSETS[var0] = var1;
         }
      }

      return var1;
   }

   private static int roundRows(class_332 var0, int var1, int var2, int var3, int var4, int var5, int var6, boolean var7) {
      int[] var8 = insets(var5);
      int var9 = 0;

      while (var9 < var5 && var8[var9] > 0) {
         var9++;
      }

      int var10 = 0;

      while (var10 < var9) {
         int var11 = var8[var10];
         int var12 = var10 + 1;

         while (var12 < var9 && var8[var12] == var11) {
            var12++;
         }

         var0.method_25294(var1 + var11, var2 + var10, var1 + var3 - var11, var2 + var12, var6);
         if (var7) {
            var0.method_25294(var1 + var11, var2 + var4 - var12, var1 + var3 - var11, var2 + var4 - var10, var6);
         }

         var10 = var12;
      }

      return var9;
   }

   public static void rect(class_332 var0, int var1, int var2, int var3, int var4, int var5, int var6) {
      if (var3 > 0 && var4 > 0 && var6 >>> 24 != 0) {
         boolean var7 = Skin.active();
         if (var7) {
            var5 = Skin.radius(var5);
         }

         var5 = Math.max(0, Math.min(var5, Math.min(var3, var4) / 2));
         if (var5 == 0) {
            var0.method_25294(var1, var2, var1 + var3, var2 + var4, var6);
            if (var7 && Skin.pixel() && var3 >= 8 && var4 >= 8 && var6 >>> 24 >= 192) {
               int var8 = Math.min(2, Math.min(var3, var4) / 6);
               int var9 = ColorUtil.blend(var6, -1, 0.3F);
               int var10 = ColorUtil.blend(var6, -16777216, 0.4F);
               var0.method_25294(var1, var2, var1 + var3, var2 + var8, var9);
               var0.method_25294(var1, var2, var1 + var8, var2 + var4, var9);
               var0.method_25294(var1, var2 + var4 - var8, var1 + var3, var2 + var4, var10);
               var0.method_25294(var1 + var3 - var8, var2 + var8, var1 + var3, var2 + var4, var10);
            }
         } else {
            int var12 = roundRows(var0, var1, var2, var3, var4, var5, var6, true);
            var0.method_25294(var1, var2 + var12, var1 + var3, var2 + var4 - var12, var6);
         }
      }
   }

   public static void rectTop(class_332 var0, int var1, int var2, int var3, int var4, int var5, int var6) {
      var5 = Math.max(0, Math.min(var5, Math.min(var3, var4) / 2));
      int var7 = roundRows(var0, var1, var2, var3, var4, var5, var6, false);
      var0.method_25294(var1, var2 + var7, var1 + var3, var2 + var4, var6);
   }

   public static void outline(class_332 var0, int var1, int var2, int var3, int var4, int var5, int var6) {
      if (var3 > 1 && var4 > 1) {
         if (Skin.active()) {
            var5 = Skin.radius(var5);
            if (Skin.neon() && var6 >>> 24 >= 160 && var3 > 24) {
               int var7 = ColorUtil.withAlpha(var6, 44);
               var0.method_25294(var1 - 1, var2 - 1, var1 + var3 + 1, var2, var7);
               var0.method_25294(var1 - 1, var2 + var4, var1 + var3 + 1, var2 + var4 + 1, var7);
               var0.method_25294(var1 - 1, var2, var1, var2 + var4, var7);
               var0.method_25294(var1 + var3, var2, var1 + var3 + 1, var2 + var4, var7);
            }
         }

         var5 = Math.max(0, Math.min(var5, Math.min(var3, var4) / 2));
         var0.method_25294(var1 + var5, var2, var1 + var3 - var5, var2 + 1, var6);
         var0.method_25294(var1 + var5, var2 + var4 - 1, var1 + var3 - var5, var2 + var4, var6);
         var0.method_25294(var1, var2 + var5, var1 + 1, var2 + var4 - var5, var6);
         var0.method_25294(var1 + var3 - 1, var2 + var5, var1 + var3, var2 + var4 - var5, var6);
         int[] var13 = insets(var5);
         int var8 = 0;

         while (var8 < var5) {
            int var9 = var13[var8];
            int var10 = Math.max(1, var9 - (var8 + 1 < var5 ? var13[var8 + 1] : 0));
            int var11 = var8 + 1;

            while (var11 < var5 && var13[var11] == var9 && Math.max(1, var9 - (var11 + 1 < var5 ? var13[var11 + 1] : 0)) == var10) {
               var11++;
            }

            var0.method_25294(var1 + var9, var2 + var8, var1 + var9 + var10, var2 + var11, var6);
            var0.method_25294(var1 + var3 - var9 - var10, var2 + var8, var1 + var3 - var9, var2 + var11, var6);
            var0.method_25294(var1 + var9, var2 + var4 - var11, var1 + var9 + var10, var2 + var4 - var8, var6);
            var0.method_25294(var1 + var3 - var9 - var10, var2 + var4 - var11, var1 + var3 - var9, var2 + var4 - var8, var6);
            var8 = var11;
         }
      }
   }

   public static void round(class_332 var0, int var1, int var2, int var3, int var4, int var5) {
      rect(var0, var1, var2, var3, var4, 2, var5);
   }

   public static void outline(class_332 var0, int var1, int var2, int var3, int var4, int var5) {
      outline(var0, var1, var2, var3, var4, 2, var5);
   }

   public static void shadow(class_332 var0, int var1, int var2, int var3, int var4, int var5, int var6, float var7) {
      if (!Skin.pixel()) {
         int var8 = Skin.neon() ? '￡' : (Skin.frost() ? 4943824 : 0);
         float var9 = Skin.frost() ? 0.45F : (Skin.neon() ? 0.7F : 1.0F);

         for (int var10 = var6; var10 > 0; var10--) {
            int var11 = (int)(var7 * var9 * 60.0F * (1.0F - (float)var10 / (var6 + 1)) / var6 * 2.0F);
            if (var11 > 0) {
               rect(var0, var1 - var10, var2 - var10 + 1, var3 + var10 * 2, var4 + var10 * 2, var5 + var10, var11 << 24 | var8);
            }
         }
      }
   }

   public static void hGradient(class_332 var0, int var1, int var2, int var3, int var4, int var5, int var6) {
      if (var3 > 0) {
         int var7 = var3 > 160 ? 2 : 1;

         for (int var8 = 0; var8 < var3; var8 += var7) {
            int var9 = ColorUtil.blend(var5, var6, (float)var8 / Math.max(1, var3 - 1));
            var0.method_25294(var1 + var8, var2, var1 + Math.min(var3, var8 + var7), var2 + var4, var9);
         }
      }
   }

   public static void vGradient(class_332 var0, int var1, int var2, int var3, int var4, int var5, int var6) {
      var0.method_25296(var1, var2, var1 + var3, var2 + var4, var5, var6);
   }

   public static void accentBar(class_332 var0, int var1, int var2, int var3, int var4, double var5) {
      int var7 = Math.max(1, var3 / 40);

      for (int var8 = 0; var8 < var3; var8 += var7) {
         var0.method_25294(var1 + var8, var2, var1 + Math.min(var3, var8 + var7), var2 + var4, Theme.accentAt(var5 + (double)var8 / Math.max(1, var3) * 0.5));
      }
   }

   public static void panel(class_332 var0, int var1, int var2, int var3, int var4, int var5) {
      shadow(var0, var1, var2, var3, var4, 4, 3, 0.8F);
      rect(var0, var1, var2, var3, var4, 4, -435154409);
      outline(var0, var1, var2, var3, var4, 4, 419430399);
      accentBar(var0, var1 + 3, var2, var3 - 6, 1, 0.0);
   }

   public static void toggle(class_332 var0, int var1, int var2, float var3, int var4) {
      byte var5 = 18;
      byte var6 = 10;
      int var7 = ColorUtil.blend(Skin.c(-13881027), var4, var3);
      rect(var0, var1, var2, var5, var6, 5, var7);
      int var8 = (int)(var1 + 1 + var3 * (var5 - 10));
      rect(var0, var8, var2 + 1, 8, 8, 4, -1);
   }

   public static void slider(class_332 var0, int var1, int var2, int var3, float var4, int var5, boolean var6) {
      var4 = Math.max(0.0F, Math.min(1.0F, var4));
      rect(var0, var1, var2, var3, 4, 2, Skin.c(-13881027));
      int var7 = Math.max(4, (int)(var3 * var4));
      rect(var0, var1, var2, var7, 4, 2, var5);
      int var8 = var1 + (int)(var3 * var4) - 3;
      int var9 = var6 ? 8 : 6;
      rect(var0, var8 - (var9 - 6) / 2, var2 + 2 - var9 / 2, var9, var9, var9 / 2, -1);
   }

   public static void bar(class_332 var0, int var1, int var2, int var3, int var4, float var5, int var6, int var7) {
      rect(var0, var1, var2, var3, var4, var4 / 2, var6);
      int var8 = (int)(var3 * Math.max(0.0F, Math.min(1.0F, var5)));
      if (var8 > 0) {
         rect(var0, var1, var2, Math.max(var8, var4), var4, var4 / 2, var7);
      }
   }

   public static void text(class_332 var0, String var1, int var2, int var3, int var4) {
      if (var4 >>> 24 >= 8) {
         var0.method_51433(font(), var1, var2, var3, var4, textShadow && !Skin.frost());
      }
   }

   public static void text(class_332 var0, String var1, float var2, float var3, int var4, float var5) {
      if (var4 >>> 24 >= 8) {
         var0.method_51448().pushMatrix();
         var0.method_51448().translate(var2, var3);
         var0.method_51448().scale(var5, var5);
         var0.method_51433(font(), var1, 0, 0, var4, textShadow && !Skin.frost());
         var0.method_51448().popMatrix();
      }
   }

   public static void textCentered(class_332 var0, String var1, int var2, int var3, int var4) {
      text(var0, var1, var2 - width(var1) / 2, var3, var4);
   }

   public static void accentText(class_332 var0, String var1, int var2, int var3, double var4) {
      int var6 = var2;

      for (int var7 = 0; var7 < var1.length(); var7++) {
         char var8 = var1.charAt(var7);
         String var9 = var8 < CHARS.length ? CHARS[var8] : String.valueOf(var8);
         text(var0, var9, var6, var3, Theme.accentAt(var4 + var7 * 0.04));
         var6 += width(var9);
      }
   }

   public static int width(String var0) {
      return font().method_1727(var0);
   }

   public static String trim(String var0, int var1) {
      if (var0 == null) {
         return "";
      } else if (width(var0) <= var1) {
         return var0;
      } else {
         String var2 = "…";
         int var3 = var1 - width(var2);
         int var4 = 0;
         int var5 = var0.length();

         while (var4 < var5) {
            int var6 = var4 + var5 + 1 >>> 1;
            if (width(var0.substring(0, var6)) > var3) {
               var5 = var6 - 1;
            } else {
               var4 = var6;
            }
         }

         return var0.substring(0, var4) + var2;
      }
   }

   public static boolean inside(double var0, double var2, int var4, int var5, int var6, int var7) {
      return var0 >= var4 && var0 < var4 + var6 && var2 >= var5 && var2 < var5 + var7;
   }

   static {
      for (int var0 = 0; var0 < CHARS.length; var0++) {
         CHARS[var0] = String.valueOf((char)var0);
      }
   }
}
