package dev.dihclient.autobuild;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class MapArtGenerator {
   public static final int[] SHADE = new int[]{180, 220, 255};

   private MapArtGenerator() {
   }

   public static int shade(int var0, int var1) {
      int var2 = (var0 >> 16 & 0xFF) * var1 / 255;
      int var3 = (var0 >> 8 & 0xFF) * var1 / 255;
      int var4 = (var0 & 0xFF) * var1 / 255;
      return var2 << 16 | var3 << 8 | var4;
   }

   public static MapArtGenerator.Result generate(BufferedImage var0, List<MapArtGenerator.PaletteEntry> var1, MapArtGenerator.Options var2) {
      int var3 = 128 * var2.mapsX;
      int var4 = 128 * var2.mapsZ;
      BufferedImage var5 = scale(var0, var3, var4, var2.fit);
      boolean var6 = var2.mode == MapArtGenerator.Mode.STAIRCASE;
      ArrayList var7 = new ArrayList();

      for (int var8 = 0; var8 < var1.size(); var8++) {
         if (var6) {
            for (int var9 = 0; var9 < 3; var9++) {
               var7.add(new int[]{var8, var9, shade(((MapArtGenerator.PaletteEntry)var1.get(var8)).rgb(), SHADE[var9])});
            }
         } else {
            var7.add(new int[]{var8, 1, shade(((MapArtGenerator.PaletteEntry)var1.get(var8)).rgb(), SHADE[1])});
         }
      }

      int[][] var29 = new int[var3][var4];
      int[][] var30 = new int[var3][var4];
      boolean[][] var10 = new boolean[var3][var4];
      float[][][] var11 = new float[var3 + 2][var4 + 2][3];
      BufferedImage var12 = new BufferedImage(var3, var4, 2);
      HashMap var13 = new HashMap();

      for (int var14 = 0; var14 < var4; var14++) {
         for (int var15 = 0; var15 < var3; var15++) {
            int var16 = var5.getRGB(var15, var14);
            if (var16 >>> 24 < 128) {
               var10[var15][var14] = true;
            } else {
               float var17 = (var16 >> 16 & 0xFF) + var11[var15 + 1][var14 + 1][0];
               float var18 = (var16 >> 8 & 0xFF) + var11[var15 + 1][var14 + 1][1];
               float var19 = (var16 & 0xFF) + var11[var15 + 1][var14 + 1][2];
               int var20 = clamp(var17);
               int var21 = clamp(var18);
               int var22 = clamp(var19);
               int var23 = var20 << 16 | var21 << 8 | var22;
               int var24 = var2.dither ? nearest(var7, var20, var21, var22) : var13.computeIfAbsent(var23, var4x -> nearest(var7, var20, var21, var22));
               int[] var25 = (int[])var7.get(var24);
               var29[var15][var14] = var25[0];
               var30[var15][var14] = var25[1];
               var12.setRGB(var15, var14, 0xFF000000 | var25[2]);
               if (var2.dither) {
                  float var26 = var17 - (var25[2] >> 16 & 0xFF);
                  float var27 = var18 - (var25[2] >> 8 & 0xFF);
                  float var28 = var19 - (var25[2] & 0xFF);
                  spread(var11, var15 + 2, var14 + 1, var26, var27, var28, 0.4375F);
                  spread(var11, var15, var14 + 2, var26, var27, var28, 0.1875F);
                  spread(var11, var15 + 1, var14 + 2, var26, var27, var28, 0.3125F);
                  spread(var11, var15 + 2, var14 + 2, var26, var27, var28, 0.0625F);
               }
            }
         }
      }

      int var31 = var2.noobline ? 1 : 0;
      ArrayList var32 = new ArrayList();
      LinkedHashMap var33 = new LinkedHashMap();
      int var34 = 1;

      for (int var35 = 0; var35 < var3; var35++) {
         int[] var37 = new int[var4];
         int var40 = 0;
         int var43 = 0;

         for (int var47 = 0; var47 < var4; var47++) {
            if (var6 && !var10[var35][var47]) {
               var40 += var30[var35][var47] == 2 ? 1 : (var30[var35][var47] == 0 ? -1 : 0);
            }

            var37[var47] = var40;
            if (!var10[var35][var47]) {
               var43 = Math.min(var43, var40);
            }
         }

         int var48 = -var43;
         if (var2.noobline) {
            add(var32, var33, var35, var48, 0, var2.nooblineBlock);
         }

         for (int var50 = 0; var50 < var4; var50++) {
            if (!var10[var35][var50]) {
               int var52 = var37[var50] + var48;
               add(var32, var33, var35, var52, var50 + var31, ((MapArtGenerator.PaletteEntry)var1.get(var29[var35][var50])).blockId());
               var34 = Math.max(var34, var52 + 1);
               if (var6 && var52 > 0) {
                  if (var2.supports == MapArtGenerator.Supports.UNDER) {
                     add(var32, var33, var35, var52 - 1, var50 + var31, var2.supportBlock);
                  } else if (var2.supports == MapArtGenerator.Supports.FULL) {
                     for (int var53 = 0; var53 < var52; var53++) {
                        add(var32, var33, var35, var53, var50 + var31, var2.supportBlock);
                     }
                  }
               }
            }
         }

         if (var2.noobline) {
            var34 = Math.max(var34, var48 + 1);
         }
      }

      LinkedHashMap var36 = new LinkedHashMap();

      for (SchematicWriter.Block var41 : var32) {
         long var44 = (long)var41.x() << 40 | (long)var41.y() << 20 | var41.z();
         SchematicWriter.Block var51 = (SchematicWriter.Block)var36.get(var44);
         if (var51 == null || var51.state().equals(var2.supportBlock) || !var41.state().equals(var2.supportBlock)) {
            var36.put(var44, var41);
         }
      }

      ArrayList var39 = new ArrayList(var36.values());
      LinkedHashMap var42 = new LinkedHashMap();

      for (SchematicWriter.Block var49 : var39) {
         var42.merge(var49.state(), 1, Integer::sum);
      }

      int var46 = (int)var42.keySet().stream().filter(var1x -> !var1x.equals(var2.supportBlock) && !var1x.equals(var2.nooblineBlock)).count();
      return new MapArtGenerator.Result(var39, var3, var4 + var31, var34, var12, var42, var46);
   }

   private static void add(List<SchematicWriter.Block> var0, Map<String, Integer> var1, int var2, int var3, int var4, String var5) {
      var0.add(new SchematicWriter.Block(var2, var3, var4, var5));
      var1.merge(var5, 1, Integer::sum);
   }

   private static int clamp(float var0) {
      return var0 < 0.0F ? 0 : (var0 > 255.0F ? 255 : Math.round(var0));
   }

   private static void spread(float[][][] var0, int var1, int var2, float var3, float var4, float var5, float var6) {
      if (var1 < var0.length && var2 < var0[0].length) {
         var0[var1][var2][0] = var0[var1][var2][0] + var3 * var6;
         var0[var1][var2][1] = var0[var1][var2][1] + var4 * var6;
         var0[var1][var2][2] = var0[var1][var2][2] + var5 * var6;
      }
   }

   private static int nearest(List<int[]> var0, int var1, int var2, int var3) {
      int var4 = 0;
      double var5 = Double.MAX_VALUE;

      for (int var7 = 0; var7 < var0.size(); var7++) {
         int var8 = ((int[])var0.get(var7))[2];
         int var9 = var8 >> 16 & 0xFF;
         int var10 = var8 >> 8 & 0xFF;
         int var11 = var8 & 0xFF;
         double var12 = (var1 + var9) / 2.0;
         double var14 = var1 - var9;
         double var16 = var2 - var10;
         double var18 = var3 - var11;
         double var20 = (2.0 + var12 / 256.0) * var14 * var14 + 4.0 * var16 * var16 + (2.0 + (255.0 - var12) / 256.0) * var18 * var18;
         if (var20 < var5) {
            var5 = var20;
            var4 = var7;
         }
      }

      return var4;
   }

   private static BufferedImage scale(BufferedImage var0, int var1, int var2, MapArtGenerator.Fit var3) {
      BufferedImage var4 = new BufferedImage(var1, var2, 2);
      Graphics2D var5 = var4.createGraphics();
      var5.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
      var5.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
      double var6 = var0.getWidth();
      double var8 = var0.getHeight();
      switch (var3) {
         case STRETCH:
            var5.drawImage(var0, 0, 0, var1, var2, null);
            break;
         case CROP:
            double var14 = Math.max(var1 / var6, var2 / var8);
            int var15 = (int)Math.round(var6 * var14);
            int var16 = (int)Math.round(var8 * var14);
            var5.drawImage(var0, (var1 - var15) / 2, (var2 - var16) / 2, var15, var16, null);
            break;
         case FIT:
            double var10 = Math.min(var1 / var6, var2 / var8);
            int var12 = (int)Math.round(var6 * var10);
            int var13 = (int)Math.round(var8 * var10);
            var5.drawImage(var0, (var1 - var12) / 2, (var2 - var13) / 2, var12, var13, null);
      }

      var5.dispose();
      return var4;
   }

   public static enum Fit {
      STRETCH,
      CROP,
      FIT;
   }

   public static enum Mode {
      FLAT,
      STAIRCASE;
   }

   public static final class Options {
      public int mapsX = 1;
      public int mapsZ = 1;
      public MapArtGenerator.Mode mode = MapArtGenerator.Mode.FLAT;
      public MapArtGenerator.Fit fit = MapArtGenerator.Fit.CROP;
      public boolean dither = true;
      public boolean noobline = true;
      public MapArtGenerator.Supports supports = MapArtGenerator.Supports.UNDER;
      public String nooblineBlock = "minecraft:cobblestone";
      public String supportBlock = "minecraft:cobblestone";
   }

   public record PaletteEntry(String blockId, int rgb) {
   }

   public record Result(
      List<SchematicWriter.Block> blocks, int width, int length, int height, BufferedImage preview, Map<String, Integer> counts, int colorsUsed
   ) {
   }

   public static enum Supports {
      UNDER,
      FULL,
      NONE;
   }
}
