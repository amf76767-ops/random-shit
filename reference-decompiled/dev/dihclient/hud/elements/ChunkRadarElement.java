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

   @Override
   public void render(class_332 var1, float var2, boolean var3) {
      int var4 = HudStyle.hud().chunkRadarRange.get();
      int var5 = var4 * 2 + 1;
      int var6 = Math.max(3, 90 / var5);
      this.width = var5 * var6 + 4;
      this.height = var5 * var6 + 4;
      HudStyle.accentPanel(var1, 0, 0, this.width, this.height);
      if (mc.field_1724 != null) {
         class_1923 var7 = mc.field_1724.method_31476();
         byte var8 = 2;
         byte var9 = 2;

         for (byte var10 = 0; var10 <= var5; var10 = (byte)(var10 + 2)) {
            var1.method_25294(var8 + var10 * var6, var9, var8 + var10 * var6 + 1, var9 + var5 * var6, 285212671);
            var1.method_25294(var8, var9 + var10 * var6, var8 + var5 * var6, var9 + var10 * var6 + 1, 285212671);
         }

         for (Module var11 : DIHClient.modules().all()) {
            if (var11 instanceof ChunkMarkModule var12 && var11.isEnabled()) {
               Map var13 = var12.chunkMarks();
               if (!var13.isEmpty()) {
                  if (var13.size() > var5 * var5) {
                     for (int var28 = -var4; var28 <= var4; var28++) {
                        for (int var30 = -var4; var30 <= var4; var30++) {
                           Integer var32 = (Integer)var13.get(class_1923.method_8331(var7.field_9181 + var28, var7.field_9180 + var30));
                           if (var32 != null) {
                              int var17 = var8 + (var28 + var4) * var6;
                              int var36 = var9 + (var30 + var4) * var6;
                              var1.method_25294(var17, var36, var17 + var6, var36 + var6, var32 & 16777215 | -1342177280);
                           }
                        }
                     }
                  } else {
                     for (Entry var15 : var13.entrySet()) {
                        long var16 = (Long)var15.getKey();
                        int var18 = class_1923.method_8325(var16) - var7.field_9181;
                        int var19 = class_1923.method_8332(var16) - var7.field_9180;
                        if (Math.abs(var18) <= var4 && Math.abs(var19) <= var4) {
                           int var20 = var8 + (var18 + var4) * var6;
                           int var21 = var9 + (var19 + var4) * var6;
                           var1.method_25294(var20, var21, var20 + var6, var21 + var6, (Integer)var15.getValue() & 16777215 | -1342177280);
                        }
                     }
                  }
               }
            }
         }

         NetheriteFinder var24 = ModuleManager.of(NetheriteFinder.class);
         if (var24 != null && var24.isEnabled()) {
            double var26 = var6 / 16.0;
            double var29 = (var7.field_9181 - var4) * 16.0;
            double var33 = (var7.field_9180 - var4) * 16.0;

            for (class_243 var39 : var24.depositCenters()) {
               int var40 = var8 + (int)((var39.field_1352 - var29) * var26);
               int var42 = var9 + (int)((var39.field_1350 - var33) * var26);
               if (var40 >= var8 && var42 >= var9 && var40 < var8 + var5 * var6 && var42 < var9 + var5 * var6) {
                  var1.method_25294(var40 - 1, var42 - 1, var40 + 2, var42 + 2, -20400);
               }
            }
         }

         double var25 = (mc.field_1724.method_23317() - (var7.field_9181 << 4)) / 16.0;
         double var27 = (mc.field_1724.method_23321() - (var7.field_9180 << 4)) / 16.0;
         int var31 = var8 + (int)((var4 + var25) * var6);
         int var34 = var9 + (int)((var4 + var27) * var6);
         int var35 = Theme.accentAt(0.0);
         double var38 = Math.toRadians(mc.field_1724.method_36454());

         for (int var41 = 1; var41 <= 5; var41++) {
            int var43 = var31 + (int)Math.round(-Math.sin(var38) * var41);
            int var22 = var34 + (int)Math.round(Math.cos(var38) * var41);
            var1.method_25294(var43, var22, var43 + 1, var22 + 1, var35);
         }

         var1.method_25294(var31 - 1, var34 - 1, var31 + 2, var34 + 2, -1);
         var1.method_51433(mc.field_1772, "N", this.width / 2 - 2, 3, -1862270977, false);
      }
   }
}
