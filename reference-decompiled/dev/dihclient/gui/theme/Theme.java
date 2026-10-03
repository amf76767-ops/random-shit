package dev.dihclient.gui.theme;

import dev.dihclient.modules.client.ClickGui;
import dev.dihclient.util.ColorUtil;
import java.awt.Color;

public final class Theme {
   public static final int WINDOW = -233959403;
   public static final int SIDEBAR = -16052975;
   public static final int SURFACE = -15328992;
   public static final int SURFACE_2 = -14868182;
   public static final int HOVER = -14341579;
   public static final int BORDER = 587202559;
   public static final int TEXT = -1446670;
   public static final int MUTED = -7564380;
   public static final int DIM = -10788238;
   public static final int GREEN = -11870592;
   public static final int RED = -495247;
   public static final int ORANGE = -278748;
   public static final int SWITCH_OFF = -13881027;
   private static final int OLD_ACCENT = -8614657;
   private static final int OLD_ACCENT_2 = -4160260;
   public static final int DEFAULT_ACCENT = -1754827;
   public static final int DEFAULT_ACCENT_2 = -30147;

   private Theme() {
   }

   private static ClickGui gui() {
      return Skin.gui();
   }

   private static void migrate(ClickGui var0) {
      boolean var1 = (var0.accent.get() | 0xFF000000) == -8614657;
      boolean var2 = (var0.accent2.get() | 0xFF000000) == -4160260;
      if (var1) {
         var0.accent.set(-1754827);
      }

      if (var2) {
         var0.accent2.set(-30147);
      }

      if (var1 && var2 && var0.colorMode.get() == Theme.ColorMode.GRADIENT) {
         var0.colorMode.set(Theme.ColorMode.STATIC);
      }
   }

   public static int accent() {
      if (Skin.active()) {
         return Skin.accent(0.0);
      } else {
         ClickGui var0 = gui();
         if (var0 == null) {
            return -1754827;
         } else {
            migrate(var0);
            return var0.accent.get() | 0xFF000000;
         }
      }
   }

   public static int accent2() {
      ClickGui var0 = gui();
      if (var0 == null) {
         return -30147;
      } else {
         migrate(var0);
         return var0.accent2.get() | 0xFF000000;
      }
   }

   public static int accentAt(double var0) {
      if (Skin.active()) {
         return Skin.accent(var0);
      } else {
         ClickGui var2 = gui();
         Theme.ColorMode var3 = var2 == null ? Theme.ColorMode.GRADIENT : var2.colorMode.get();
         double var4 = var2 == null ? 1.0 : var2.colorSpeed.get();
         double var6 = System.currentTimeMillis() / 1000.0 * var4;
         switch (var3) {
            case STATIC:
               return accent();
            case RAINBOW:
               float var8 = (float)((var6 * 0.15 + var0) % 1.0);
               if (var8 < 0.0F) {
                  var8++;
               }

               return 0xFF000000 | Color.HSBtoRGB(var8, 0.55F, 1.0F);
            default:
               float var9 = (float)(Math.sin((var6 + var0 * 6.0) * 1.2) * 0.5 + 0.5);
               return ColorUtil.blend(accent(), accent2(), var9);
         }
      }
   }

   public static int withAlpha(int var0, float var1) {
      return ColorUtil.withAlpha(var0, (int)(Math.max(0.0F, Math.min(1.0F, var1)) * (var0 >>> 24 & 0xFF)));
   }

   public static enum ColorMode {
      STATIC,
      GRADIENT,
      RAINBOW;
   }
}
