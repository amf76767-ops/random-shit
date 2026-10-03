package dev.dihclient.hud.elements;

import dev.dihclient.gui.theme.Theme;
import dev.dihclient.hud.HudElement;
import dev.dihclient.hud.HudStyle;
import dev.dihclient.render.Gfx;
import java.util.Locale;
import net.minecraft.class_1937;
import net.minecraft.class_332;

public class CoordsElement extends HudElement {
   private static final String[] DIRS = new String[]{"South +Z", "SW", "West -X", "NW", "North -Z", "NE", "East +X", "SE"};
   private static final String[] AXES = new String[]{"X", "Y", "Z"};
   private final String[] texts = new String[3];
   private final double[] values = new double[3];

   public CoordsElement() {
      super("coords", "Coordinates", "coords", 4, -1, () -> HudStyle.hud().coordinates);
   }

   @Override
   public void render(class_332 var1, float var2, boolean var3) {
      double var4 = mc.field_1724.method_23317();
      double var6 = mc.field_1724.method_23318();
      double var8 = mc.field_1724.method_23321();
      String[] var10 = AXES;
      String[] var11 = this.texts;
      this.fmtAxis(0, var4);
      this.fmtAxis(1, var6);
      this.fmtAxis(2, var8);
      boolean var12 = mc.field_1687.method_27983() == class_1937.field_25180;
      boolean var13 = mc.field_1687.method_27983() == class_1937.field_25181;
      String var14 = var13
         ? null
         : (
            var12
               ? "Overworld " + (int)Math.floor(var4 * 8.0) + " " + (int)Math.floor(var8 * 8.0)
               : "Nether " + (int)Math.floor(var4 / 8.0) + " " + (int)Math.floor(var8 / 8.0)
         );
      String var15 = DIRS[Math.floorMod(Math.round(mc.field_1724.method_36454() / 45.0F), 8)];
      int var16 = 6;
      byte var17 = 4;
      int var18 = 0;

      for (int var19 = 0; var19 < 3; var19++) {
         var18 += Gfx.width(var10[var19]) + 3 + Gfx.width(var11[var19]) + 7;
      }

      int var22 = (var14 == null ? 0 : Gfx.width(var14) + 10) + Gfx.width(var15);
      this.width = Math.max(var18, var22) + 10;
      this.height = 26;
      HudStyle.panel(var1, 0, 0, this.width, this.height);

      for (int var20 = 0; var20 < 3; var20++) {
         Gfx.text(var1, var10[var20], var16, var17, Theme.accentAt(var20 * 0.12));
         var16 += Gfx.width(var10[var20]) + 3;
         Gfx.text(var1, var11[var20], var16, var17, -1446670);
         var16 += Gfx.width(var11[var20]) + 7;
      }

      int var23 = var17 + 11;
      if (var14 != null) {
         Gfx.text(var1, var14, 6, var23, var12 ? -6630145 : -25990);
      }

      Gfx.text(var1, var15, this.width - 6 - Gfx.width(var15), var23, -7564380);
   }

   private void fmtAxis(int var1, double var2) {
      if (this.texts[var1] == null || Double.doubleToLongBits(this.values[var1]) != Double.doubleToLongBits(var2)) {
         this.values[var1] = var2;
         this.texts[var1] = fmt(var2);
      }
   }

   private static String fmt(double var0) {
      return String.format(Locale.ROOT, "%.1f", var0);
   }
}
