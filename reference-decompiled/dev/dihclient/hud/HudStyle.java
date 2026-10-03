package dev.dihclient.hud;

import dev.dihclient.module.ModuleManager;
import dev.dihclient.modules.client.Hud;
import dev.dihclient.render.Gfx;
import net.minecraft.class_332;

public final class HudStyle {
   private static Hud hud;

   private HudStyle() {
   }

   public static Hud hud() {
      Hud var0 = hud;
      if (var0 == null) {
         var0 = hud = ModuleManager.of(Hud.class);
      }

      return var0;
   }

   public static boolean glass() {
      Hud var0 = hud();
      return var0 == null || var0.glass();
   }

   public static int bgColor() {
      Hud var0 = hud();
      int var1 = var0 == null ? 170 : var0.opacity.get();
      return var1 << 24 | 855828;
   }

   public static void panel(class_332 var0, int var1, int var2, int var3, int var4) {
      if (glass()) {
         Hud var5 = hud();
         int var6 = var5 == null ? 170 : var5.opacity.get();
         if (var6 > 40) {
            Gfx.shadow(var0, var1, var2, var3, var4, 5, 2, var6 / 255.0F * 0.7F);
         }

         Gfx.rect(var0, var1, var2, var3, var4, 5, bgColor());
         Gfx.outline(var0, var1, var2, var3, var4, 5, Math.min(40, var6 / 5) << 24 | 16777215);
      }
   }

   public static void accentPanel(class_332 var0, int var1, int var2, int var3, int var4) {
      panel(var0, var1, var2, var3, var4);
      if (glass()) {
         Gfx.accentBar(var0, var1 + 4, var2, var3 - 8, 1, 0.0);
      }
   }

   public static int label() {
      return -7564380;
   }
}
