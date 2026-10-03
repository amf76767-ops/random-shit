package dev.dihclient.hud.elements;

import dev.dihclient.DIHClient;
import dev.dihclient.gui.theme.Theme;
import dev.dihclient.hud.HudElement;
import dev.dihclient.hud.HudStyle;
import dev.dihclient.render.Gfx;
import net.minecraft.class_1297;
import net.minecraft.class_1309;
import net.minecraft.class_1588;
import net.minecraft.class_1657;
import net.minecraft.class_332;

public class RadarElement extends HudElement {
   private static final int[][] RINGS = new int[64][];

   public RadarElement() {
      super("radar", "Radar", "radar", 4, 60, () -> HudStyle.hud().radar);
   }

   @Override
   public void render(class_332 var1, float var2, boolean var3) {
      this.width = 86;
      this.height = 86;
      HudStyle.accentPanel(var1, 0, 0, this.width, this.height);
      int var4 = this.width / 2;
      int var5 = this.height / 2;

      for (byte var6 = 14; var6 < this.width / 2 - 2; var6 = (byte)(var6 + 14)) {
         ringDots(var1, var4, var5, var6);
      }

      var1.method_25294(var4, 4, var4 + 1, this.height - 4, 419430399);
      var1.method_25294(4, var5, this.width - 4, var5 + 1, 419430399);
      double var30 = HudStyle.hud().radarRange.get();
      double var8 = (this.width / 2.0 - 5.0) / var30;
      double var10 = Math.toRadians(mc.field_1724.method_36454());
      double var12 = Math.cos(var10);
      double var14 = Math.sin(var10);

      for (class_1297 var17 : mc.field_1687.method_18112()) {
         if (var17 != mc.field_1724 && var17 instanceof class_1309) {
            double var18 = var17.method_23317() - mc.field_1724.method_23317();
            double var20 = var17.method_23321() - mc.field_1724.method_23321();
            double var22 = -(var18 * var12 + var20 * var14);
            double var24 = -(var20 * var12 - var18 * var14);
            if (!(Math.abs(var22) > var30) && !(Math.abs(var24) > var30)) {
               int var26 = var4 + (int)(var22 * var8);
               int var27 = var5 + (int)(var24 * var8);
               if (var17 instanceof class_1657 var28) {
                  int var29 = DIHClient.social().isFriend(var28) ? -11870592 : (DIHClient.social().isEnemy(var28) ? -495247 : -1);
                  Gfx.rect(var1, var26 - 2, var27 - 2, 4, 4, 2, var29);
               } else {
                  int var32 = var17 instanceof class_1588 ? -1325895311 : -1874141568;
                  var1.method_25294(var26, var27, var26 + 2, var27 + 2, var32);
               }
            }
         }
      }

      int var31 = Theme.accentAt(0.0);
      var1.method_25294(var4 - 1, var5 - 3, var4 + 2, var5 - 2, var31);
      var1.method_25294(var4 - 2, var5 - 2, var4 + 3, var5, var31);
      var1.method_25294(var4 - 3, var5, var4 + 4, var5 + 2, var31);
   }

   private static int[] ring(int var0) {
      int[] var1 = var0 >= 0 && var0 < RINGS.length ? RINGS[var0] : null;
      if (var1 == null) {
         var1 = new int[96];

         for (int var2 = 0; var2 < 48; var2++) {
            double var3 = var2 / 48.0 * Math.PI * 2.0;
            var1[var2 * 2] = (int)Math.round(Math.cos(var3) * var0);
            var1[var2 * 2 + 1] = (int)Math.round(Math.sin(var3) * var0);
         }

         if (var0 >= 0 && var0 < RINGS.length) {
            RINGS[var0] = var1;
         }
      }

      return var1;
   }

   private static void ringDots(class_332 var0, int var1, int var2, int var3) {
      int[] var4 = ring(var3);

      for (byte var5 = 0; var5 < 96; var5 += 2) {
         int var6 = var1 + var4[var5];
         int var7 = var2 + var4[var5 + 1];
         var0.method_25294(var6, var7, var6 + 1, var7 + 1, 587202559);
      }
   }
}
