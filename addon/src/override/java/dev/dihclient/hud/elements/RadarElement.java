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

/**
 * Round radar: dark disc with range rings, a slowly turning sweep, the view cone, compass letters, players with outline
 * (at the rim, dimmed, when they are farther away than the range), arrows for things far above or below you.
 */
public class RadarElement extends HudElement {
   private static final int SIZE = 92;

   public RadarElement() {
      super("radar", "Radar", "radar", 4, 60, () -> HudStyle.hud().radar);
   }

   private static int alpha(int color, int a) {
      return Math.max(0, Math.min(255, a)) << 24 | color & 0xFFFFFF;
   }

   private static void disc(class_332 g, int cx, int cy, int r, int color) {
      for (int dy = -r; dy <= r; dy++) {
         int half = (int)Math.round(Math.sqrt((double)r * r - (double)dy * dy));
         g.method_25294(cx - half, cy + dy, cx + half + 1, cy + dy + 1, color);
      }
   }

   private static void circle(class_332 g, int cx, int cy, int r, int color, int steps) {
      for (int i = 0; i < steps; i++) {
         double a = i * Math.PI * 2.0 / steps;
         int x = cx + (int)Math.round(Math.cos(a) * r);
         int y = cy + (int)Math.round(Math.sin(a) * r);
         g.method_25294(x, y, x + 1, y + 1, color);
      }
   }

   @Override
   public void render(class_332 g, float partial, boolean editing) {
      this.width = SIZE;
      this.height = SIZE;
      if (mc.field_1724 == null || mc.field_1687 == null) {
         return;
      }
      HudStyle.accentPanel(g, 0, 0, this.width, this.height);
      int cx = this.width / 2;
      int cy = this.height / 2;
      int radius = this.width / 2 - 6;
      int accent = Theme.accentAt(0.0);
      double range = HudStyle.hud().radarRange.get();
      double scale = radius / range;
      double yaw = Math.toRadians(mc.field_1724.method_36454());
      double cos = Math.cos(yaw);
      double sin = Math.sin(yaw);

      disc(g, cx, cy, radius, 0x99080C12);
      disc(g, cx, cy, radius * 2 / 3, 0x0CFFFFFF);
      circle(g, cx, cy, radius, alpha(accent, 190), 120);
      circle(g, cx, cy, radius * 2 / 3, 0x2CFFFFFF, 72);
      circle(g, cx, cy, radius / 3, 0x2CFFFFFF, 48);
      g.method_25294(cx, cy - radius, cx + 1, cy + radius + 1, 0x16FFFFFF);
      g.method_25294(cx - radius, cy, cx + radius + 1, cy + 1, 0x16FFFFFF);

      // view cone (straight up is where you look)
      for (int s = -1; s <= 1; s += 2) {
         double a = s * Math.toRadians(35.0);
         for (int t = 6; t < radius; t += 3) {
            int x = cx + (int)Math.round(Math.sin(a) * t);
            int y = cy - (int)Math.round(Math.cos(a) * t);
            g.method_25294(x, y, x + 1, y + 1, alpha(accent, 90));
         }
      }

      // sweep with a fading tail
      double sweep = System.currentTimeMillis() % 3200L / 3200.0 * Math.PI * 2.0;
      for (int k = 0; k < 14; k++) {
         double a = sweep - k * 0.07;
         int al = 70 - k * 5;
         for (int t = 3; t < radius; t += 2) {
            int x = cx + (int)Math.round(Math.cos(a) * t);
            int y = cy + (int)Math.round(Math.sin(a) * t);
            if (al > 0) {
               g.method_25294(x, y, x + 2, y + 2, alpha(accent, al));
            }
         }
      }

      // compass letters turn with your view: N sits where north is
      String[] letters = {"N", "E", "S", "W"};
      double[][] dir = {{sin, cos}, {-cos, sin}, {-sin, -cos}, {cos, -sin}};
      for (int i = 0; i < 4; i++) {
         int lx = cx + (int)Math.round(dir[i][0] * (radius - 1)) - 2;
         int ly = cy + (int)Math.round(dir[i][1] * (radius - 1)) - 4;
         g.method_51433(mc.field_1772, letters[i], lx, ly, i == 0 ? 0xFFFF6A6A : 0x99FFFFFF, false);
      }

      long now = System.currentTimeMillis();
      for (class_1297 e : mc.field_1687.method_18112()) {
         if (e == mc.field_1724 || !(e instanceof class_1309)) {
            continue;
         }
         double dx = e.method_23317() - mc.field_1724.method_23317();
         double dz = e.method_23321() - mc.field_1724.method_23321();
         double dy = e.method_23318() - mc.field_1724.method_23318();
         double rx = -(dx * cos + dz * sin);
         double rz = -(dz * cos - dx * sin);
         double dist = Math.sqrt(rx * rx + rz * rz);
         boolean player = e instanceof class_1657;
         boolean edge = dist > range;
         if (edge && !player) {
            continue;
         }
         if (edge) {
            rx = rx / dist * range;
            rz = rz / dist * range;
         }
         int x = cx + (int)Math.round(rx * scale);
         int y = cy + (int)Math.round(rz * scale);
         int color;
         int size;
         if (player) {
            class_1657 p = (class_1657)e;
            color = DIHClient.social().isFriend(p) ? 0xFF44D26A : (DIHClient.social().isEnemy(p) ? 0xFFF8404F : 0xFFFFFFFF);
            size = 2;
            if (edge) {
               color = alpha(color, 120);
            } else {
               double pulse = 0.5 + 0.5 * Math.sin(now / 260.0 + e.method_5628());
               g.method_25294(x - 3, y - 3, x + 4, y + 4, alpha(color, (int)(26 + 34 * pulse)));
            }
            g.method_25294(x - size - 1, y - size - 1, x + size + 2, y + size + 2, 0xC0000000);
         } else if (e instanceof class_1588) {
            color = 0xFFFF5A5A;
            size = 1;
         } else {
            color = 0xFFE9C46A;
            size = 1;
         }
         g.method_25294(x - size, y - size, x + size + 1, y + size + 1, color);
         if (!edge && Math.abs(dy) > 4.0) { // far above / below: a small arrow next to it
            int ax = x + size + 3;
            int dir2 = dy > 0 ? -1 : 1;
            g.method_25294(ax, y, ax + 1, y + 1, color);
            g.method_25294(ax - 1, y + dir2, ax + 2, y + dir2 + 1, color);
            g.method_25294(ax - 2, y + dir2 * 2, ax + 3, y + dir2 * 2 + 1, color);
         }
      }

      // you: a small arrow pointing up
      g.method_25294(cx, cy - 4, cx + 1, cy - 3, accent);
      g.method_25294(cx - 1, cy - 3, cx + 2, cy - 2, accent);
      g.method_25294(cx - 2, cy - 2, cx + 3, cy, accent);
      g.method_25294(cx - 3, cy, cx + 4, cy + 2, accent);
      g.method_25294(cx - 1, cy + 2, cx + 2, cy + 3, alpha(accent, 150));

      String label = (int)range + "m";
      g.method_51433(mc.field_1772, label, 5, this.height - 11, 0x88FFFFFF, false);
   }
}
