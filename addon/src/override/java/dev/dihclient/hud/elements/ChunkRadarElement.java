package dev.dihclient.hud.elements;

import dev.dihclient.DIHClient;
import dev.dihclient.gui.theme.Theme;
import dev.dihclient.hud.HudElement;
import dev.dihclient.hud.HudStyle;
import dev.dihclient.module.Module;
import dev.dihclient.module.ModuleManager;
import dev.dihclient.modules.world.NetheriteFinder;
import dev.dihclient.scan.ChunkMarkModule;
import java.util.Map;
import java.util.Map.Entry;
import net.minecraft.class_1923;
import net.minecraft.class_243;
import net.minecraft.class_332;

public class ChunkRadarElement extends HudElement {
   public ChunkRadarElement() {
      super("chunk_radar", "Chunk Radar", "chunkradar", 4, 150, () -> HudStyle.hud().chunkRadar);
   }

   private static int alpha(int color, int a) {
      return Math.max(0, Math.min(255, a)) << 24 | color & 0xFFFFFF;
   }

   private static int lighter(int color) {
      int r = Math.min(255, (color >> 16 & 255) + 70);
      int gr = Math.min(255, (color >> 8 & 255) + 70);
      int b = Math.min(255, (color & 255) + 70);
      return 0xDD000000 | r << 16 | gr << 8 | b;
   }

   private static void tile(class_332 g, int x, int y, int cell, int color) {
      int inner = Math.max(1, cell - 1);
      g.method_25294(x, y, x + inner, y + inner, alpha(color, 215));
      g.method_25294(x, y, x + inner, y + 1, lighter(color));
   }

   @Override
   public void render(class_332 g, float partial, boolean editing) {
      int range = HudStyle.hud().chunkRadarRange.get();
      int cells = range * 2 + 1;
      int cell = Math.max(4, 96 / cells);
      int pad = 4;
      int top = 12;
      this.width = cells * cell + pad * 2;
      this.height = cells * cell + top + pad + 11;
      HudStyle.accentPanel(g, 0, 0, this.width, this.height);
      if (mc.field_1724 == null) {
         return;
      }
      class_1923 me = mc.field_1724.method_31476();
      int ox = pad;
      int oy = top;
      int accent = Theme.accentAt(0.0);

      g.method_51433(mc.field_1772, "Chunks", 5, 3, 0xCCFFFFFF, false);
      g.method_51433(mc.field_1772, "N", this.width - 10, 3, 0xFFFF6A6A, false);

      g.method_25294(ox, oy, ox + cells * cell, oy + cells * cell, 0x0CFFFFFF);
      for (int i = 0; i <= cells; i++) {
         g.method_25294(ox + i * cell, oy, ox + i * cell + 1, oy + cells * cell, 0x14FFFFFF);
         g.method_25294(ox, oy + i * cell, ox + cells * cell, oy + i * cell + 1, 0x14FFFFFF);
      }
      int mx = ox + range * cell;
      int mz = oy + range * cell;
      g.method_25294(mx, mz, mx + cell, mz + 1, alpha(accent, 220));
      g.method_25294(mx, mz + cell - 1, mx + cell, mz + cell, alpha(accent, 220));
      g.method_25294(mx, mz, mx + 1, mz + cell, alpha(accent, 220));
      g.method_25294(mx + cell - 1, mz, mx + cell, mz + cell, alpha(accent, 220));

      for (Module m : DIHClient.modules().all()) {
         if (!(m instanceof ChunkMarkModule marks) || !m.isEnabled()) {
            continue;
         }
         Map<Long, Integer> chunks = marks.chunkMarks();
         if (chunks.isEmpty()) {
            continue;
         }
         if (chunks.size() > cells * cells) {
            for (int dx = -range; dx <= range; dx++) {
               for (int dz = -range; dz <= range; dz++) {
                  Integer color = chunks.get(class_1923.method_8331(me.field_9181 + dx, me.field_9180 + dz));
                  if (color != null) {
                     tile(g, ox + (dx + range) * cell, oy + (dz + range) * cell, cell, color);
                  }
               }
            }
         } else {
            for (Entry<Long, Integer> en : chunks.entrySet()) {
               long key = en.getKey();
               int dx = class_1923.method_8325(key) - me.field_9181;
               int dz = class_1923.method_8332(key) - me.field_9180;
               if (Math.abs(dx) <= range && Math.abs(dz) <= range) {
                  tile(g, ox + (dx + range) * cell, oy + (dz + range) * cell, cell, en.getValue());
               }
            }
         }
      }

      NetheriteFinder finder = ModuleManager.of(NetheriteFinder.class);
      if (finder != null && finder.isEnabled()) {
         double per = cell / 16.0;
         double left = (me.field_9181 - range) * 16.0;
         double near = (me.field_9180 - range) * 16.0;
         for (class_243 d : finder.depositCenters()) {
            int x = ox + (int)((d.field_1352 - left) * per);
            int y = oy + (int)((d.field_1350 - near) * per);
            if (x >= ox && y >= oy && x < ox + cells * cell && y < oy + cells * cell) {
               g.method_25294(x - 1, y, x + 2, y + 1, 0xFFFFD24D);
               g.method_25294(x, y - 1, x + 1, y + 2, 0xFFFFD24D);
            }
         }
      }

      double px = (mc.field_1724.method_23317() - (me.field_9181 << 4)) / 16.0;
      double pz = (mc.field_1724.method_23321() - (me.field_9180 << 4)) / 16.0;
      int x = ox + (int)((range + px) * cell);
      int y = oy + (int)((range + pz) * cell);
      double yaw = Math.toRadians(mc.field_1724.method_36454());
      for (int t = 2; t <= 7; t++) {
         int lx = x + (int)Math.round(-Math.sin(yaw) * t);
         int ly = y + (int)Math.round(Math.cos(yaw) * t);
         g.method_25294(lx, ly, lx + 1, ly + 1, alpha(accent, 255 - t * 20));
      }
      g.method_25294(x - 2, y - 2, x + 3, y + 3, 0xC0000000);
      g.method_25294(x - 1, y - 1, x + 2, y + 2, 0xFFFFFFFF);

      String where = me.field_9181 + ", " + me.field_9180;
      g.method_51433(mc.field_1772, where, 5, this.height - 10, 0x88FFFFFF, false);
   }
}
