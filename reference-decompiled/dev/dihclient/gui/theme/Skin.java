package dev.dihclient.gui.theme;

import dev.dihclient.module.ModuleManager;
import dev.dihclient.modules.client.ClickGui;
import dev.dihclient.util.ColorUtil;

public final class Skin {
   private static boolean active;
   private static ClickGui gui;

   private Skin() {
   }

   public static void begin() {
      active = true;
   }

   public static void end() {
      active = false;
   }

   static ClickGui gui() {
      ClickGui var0 = gui;
      if (var0 == null) {
         var0 = gui = ModuleManager.of(ClickGui.class);
      }

      return var0;
   }

   public static ClickGui.Look look() {
      ClickGui var0 = gui();
      return var0 == null ? ClickGui.Look.CLASSIC : var0.look.get();
   }

   public static boolean active() {
      return active && look() != ClickGui.Look.CLASSIC;
   }

   public static boolean neon() {
      return active() && look() == ClickGui.Look.NEON;
   }

   public static boolean frost() {
      return active() && look() == ClickGui.Look.FROST;
   }

   public static boolean pixel() {
      return active() && look() == ClickGui.Look.PIXEL;
   }

   public static int c(int var0) {
      ClickGui.Look var1 = look();
      if (var1 == ClickGui.Look.CLASSIC) {
         return var0;
      } else {
         boolean var2 = var1 == ClickGui.Look.NEON;
         boolean var3 = var1 == ClickGui.Look.FROST;
         switch (var0) {
            case -535555048:
               return c(-15328992);
            case -300937196:
            case -267382764:
               return c(-233959403);
            case -233959403:
               return var2 ? -200865268 : (var3 ? -253432069 : -12960443);
            case -16052975:
               return var2 ? -16052205 : (var3 ? -855638017 : -13750217);
            case -15592422:
               return var2 ? -16184048 : (var3 ? -1493172225 : -13421252);
            case -15328992:
               return var2 ? -15656930 : (var3 ? -1191182337 : -12105134);
            case -14868182:
               return var2 ? -15063764 : (var3 ? -2892306 : -11184033);
            case -14341579:
               return var2 ? -14931407 : (var3 ? -419430401 : -10723223);
            case -13881027:
               return var2 ? -15063764 : (var3 ? -3615512 : -14737114);
            case -10788238:
               return var2 ? -10983058 : (var3 ? -8548434 : -6644056);
            case -7564380:
               return var2 ? -7693152 : (var3 ? -11903878 : -3157286);
            case -1446670:
               return var2 ? -1904914 : (var3 ? -15260099 : -855310);
            case 921621:
               return c(-233959403) & 16777215;
            case 352321535:
               return var2 ? 402718689 : (var3 ? 872415231 : 587202559);
            case 419430399:
               return c(352321535);
            case 587202559:
               return var2 ? -2147418143 : (var3 ? -855638017 : -15921648);
            case 872415231:
               return c(587202559);
            case 1090519039:
               return var2 ? -16711711 : (var3 ? 1716219856 : -6644056);
            case 1442840575:
               return c(1090519039);
            case 1711276032:
               return var2 ? 1711276032 : (var3 ? 1084865753 : 1073741824);
            default:
               return var0;
         }
      }
   }

   public static int onCard() {
      return look() == ClickGui.Look.FROST ? -15720390 : -1;
   }

   public static int accent(double var0) {
      float var2 = (float)(Math.sin(var0 * 6.0 + System.currentTimeMillis() / 1600.0) * 0.5 + 0.5);
      switch (look()) {
         case NEON:
            return ColorUtil.blend(-16711711, -4784325, var2);
         case FROST:
            return ColorUtil.blend(-12743681, -14628428, var2);
         case PIXEL:
            return ColorUtil.blend(-11141238, -13654950, var2);
         default:
            return 0;
      }
   }

   public static int dimTop(float var0) {
      switch (look()) {
         case NEON:
            return (int)(var0 * 150.0F) << 24 | 198151;
         case FROST:
            return (int)(var0 * 70.0F) << 24 | 14477562;
         case PIXEL:
            return (int)(var0 * 120.0F) << 24 | 1053720;
         default:
            return (int)(var0 * 112.0F) << 24;
      }
   }

   public static int dimBottom(float var0) {
      switch (look()) {
         case NEON:
            return (int)(var0 * 185.0F) << 24 | 265228;
         case FROST:
            return (int)(var0 * 130.0F) << 24 | 11978992;
         case PIXEL:
            return (int)(var0 * 170.0F) << 24 | 1839626;
         default:
            return (int)(var0 * 160.0F) << 24;
      }
   }

   public static int radius(int var0) {
      switch (look()) {
         case NEON:
            return Math.min(var0, 3);
         case FROST:
            return var0 * 2;
         case PIXEL:
            return 0;
         default:
            return var0;
      }
   }
}
