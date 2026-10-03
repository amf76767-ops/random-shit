package dev.dihclient.hud.elements;

import dev.dihclient.gui.theme.Theme;
import dev.dihclient.hud.HudElement;
import dev.dihclient.hud.HudStyle;
import dev.dihclient.render.Gfx;
import java.util.Locale;
import net.minecraft.class_332;

public class StatsElement extends HudElement {
   private double lastSpeed = Double.NaN;
   private String lastSpeedText;

   public StatsElement() {
      super("stats", "Stats", "stats", 4, 24, () -> HudStyle.hud().stats);
   }

   private String speedText(double var1) {
      if (this.lastSpeedText == null || Double.doubleToLongBits(var1) != Double.doubleToLongBits(this.lastSpeed)) {
         this.lastSpeed = var1;
         this.lastSpeedText = String.format(Locale.ROOT, "%.1f", var1);
      }

      return this.lastSpeedText;
   }

   @Override
   public void render(class_332 var1, float var2, boolean var3) {
      double var4 = mc.field_1724.method_23317() - mc.field_1724.field_6014;
      double var6 = mc.field_1724.method_23321() - mc.field_1724.field_5969;
      double var8 = Math.sqrt(var4 * var4 + var6 * var6) * 20.0;
      int var10 = WatermarkElement.ping();
      String[][] var11 = new String[][]{{"FPS", Integer.toString(mc.method_47599())}, {"PING", var10 + "ms"}, {"SPEED", this.speedText(var8)}};
      int var12 = 0;
      int var13 = 0;

      for (String[] var17 : var11) {
         int var18 = (int)(Gfx.width(var17[0]) * 0.7F) + Gfx.width(var17[1]) + 12;
         HudStyle.panel(var1, var12, 0, var18, 14);
         Gfx.text(var1, var17[0], var12 + 5.0F, 5.0F, Theme.accentAt(var13 * 0.15), 0.7F);
         int var19 = var17[0].equals("PING") ? (var10 > 150 ? -495247 : (var10 > 80 ? -278748 : -1446670)) : -1446670;
         Gfx.text(var1, var17[1], var12 + 7 + (int)(Gfx.width(var17[0]) * 0.7F), 3, var19);
         var12 += var18 + 3;
         var13++;
      }

      this.width = var12 - 3;
      this.height = 14;
   }
}
