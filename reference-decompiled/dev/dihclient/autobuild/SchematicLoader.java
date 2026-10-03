package dev.dihclient.autobuild;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Stream;

public final class SchematicLoader {
   private SchematicLoader() {
   }

   public static boolean isSchematic(Path var0) {
      String var1 = var0.getFileName().toString().toLowerCase(Locale.ROOT);
      return var1.endsWith(".litematic") || var1.endsWith(".schem") || var1.endsWith(".schematic");
   }

   public static List<Path> list(Path var0) {
      ArrayList var1 = new ArrayList();

      try {
         Files.createDirectories(var0);

         try (Stream var2 = Files.list(var0)) {
            var2.filter(var0x -> Files.isRegularFile(var0x)).filter(SchematicLoader::isSchematic).forEach(var1::add);
         }
      } catch (IOException var7) {
      }

      var1.sort((var0x, var1x) -> {
         try {
            return Files.getLastModifiedTime(var1x).compareTo(Files.getLastModifiedTime(var0x));
         } catch (IOException var3) {
            return var0x.compareTo(var1x);
         }
      });
      return var1;
   }

   public static Schematic load(Path var0) throws IOException {
      Map var1 = Nbt.read(var0);
      String var2 = var0.getFileName().toString().toLowerCase(Locale.ROOT);
      List var3;
      String var4;
      if (var1.containsKey("Regions")) {
         var3 = litematic(var1);
         var4 = "Litematica";
      } else if (!var1.containsKey("Schematic") && !var1.containsKey("Palette") && !var1.containsKey("BlockData")) {
         if (!var1.containsKey("Blocks") || !(var1.get("Blocks") instanceof byte[])) {
            if (var2.endsWith(".litematic")) {
               throw new IOException("Litematic has no Regions");
            } else {
               throw new IOException("Unsupported schematic format");
            }
         }

         var3 = mcedit(var1);
         var4 = "MCEdit";
      } else {
         var3 = sponge(var1);
         var4 = "Sponge";
      }

      Schematic var5 = normalize(var0, var4, var3);
      if (var0.getFileName().toString().toLowerCase(Locale.ROOT).startsWith("mapart_")) {
         var5.mapGrid = true;
         var5.mapNoobline = true;
      }

      return var5;
   }

   private static Schematic normalize(Path var0, String var1, List<Schematic.Entry> var2) throws IOException {
      ArrayList var3 = new ArrayList();

      for (Schematic.Entry var5 : var2) {
         if (!isAir(var5.id)) {
            var3.add(var5);
         }
      }

      if (var3.isEmpty()) {
         throw new IOException("Schematic contains no placeable blocks");
      } else {
         int var13 = Integer.MAX_VALUE;
         int var14 = Integer.MAX_VALUE;
         int var6 = Integer.MAX_VALUE;
         int var7 = Integer.MIN_VALUE;
         int var8 = Integer.MIN_VALUE;
         int var9 = Integer.MIN_VALUE;

         for (Schematic.Entry var11 : var3) {
            var13 = Math.min(var13, var11.x);
            var14 = Math.min(var14, var11.y);
            var6 = Math.min(var6, var11.z);
            var7 = Math.max(var7, var11.x);
            var8 = Math.max(var8, var11.y);
            var9 = Math.max(var9, var11.z);
         }

         ArrayList var15 = new ArrayList(var3.size());

         for (Schematic.Entry var12 : var3) {
            var15.add(new Schematic.Entry(var12.x - var13, var12.y - var14, var12.z - var6, var12.id, var12.properties));
         }

         var15.sort(
            (var0x, var1x) -> var0x.y != var1x.y
               ? Integer.compare(var0x.y, var1x.y)
               : (var0x.z != var1x.z ? Integer.compare(var0x.z, var1x.z) : Integer.compare(var0x.x, var1x.x))
         );
         return new Schematic(var0, var1, var15, var7 - var13 + 1, var8 - var14 + 1, var9 - var6 + 1);
      }
   }

   static boolean isAir(String var0) {
      return var0 == null
         || var0.equals("minecraft:air")
         || var0.equals("minecraft:cave_air")
         || var0.equals("minecraft:void_air")
         || var0.equals("minecraft:structure_void");
   }

   private static String normalizeId(String var0) {
      if (var0 == null) {
         return null;
      } else {
         var0 = var0.trim().toLowerCase(Locale.ROOT);
         return var0.contains(":") ? var0 : "minecraft:" + var0;
      }
   }

   private static List<Schematic.Entry> litematic(Map<String, Object> var0) throws IOException {
      Map var1 = Nbt.map(var0.get("Regions"));
      if (var1 != null && !var1.isEmpty()) {
         ArrayList var2 = new ArrayList();

         for (Object var4 : var1.values()) {
            Map var5 = Nbt.map(var4);
            if (var5 != null) {
               Map var6 = Nbt.map(var5.get("Position"));
               Map var7 = Nbt.map(var5.get("Size"));
               List var8 = Nbt.list(var5.get("BlockStatePalette"));
               if (var7 != null && var8 != null && var5.get("BlockStates") instanceof long[] var10) {
                  int var11 = Nbt.i(var7, "x", 0);
                  int var12 = Nbt.i(var7, "y", 0);
                  int var13 = Nbt.i(var7, "z", 0);
                  int var14 = Nbt.i(var6, "x", 0);
                  int var15 = Nbt.i(var6, "y", 0);
                  int var16 = Nbt.i(var6, "z", 0);
                  int var17 = var14 + (var11 < 0 ? var11 + 1 : 0);
                  int var18 = var15 + (var12 < 0 ? var12 + 1 : 0);
                  int var19 = var16 + (var13 < 0 ? var13 + 1 : 0);
                  var11 = Math.abs(var11);
                  var12 = Math.abs(var12);
                  var13 = Math.abs(var13);
                  String[] var20 = new String[var8.size()];
                  String[] var21 = new String[var8.size()];

                  for (int var22 = 0; var22 < var8.size(); var22++) {
                     Map var23 = Nbt.map(var8.get(var22));
                     var20[var22] = normalizeId(Nbt.s(var23, "Name"));
                     Map var24 = Nbt.map(var23 == null ? null : var23.get("Properties"));
                     if (var24 != null && !var24.isEmpty()) {
                        StringBuilder var25 = new StringBuilder();

                        for (Entry var27 : var24.entrySet()) {
                           if (var25.length() > 0) {
                              var25.append(',');
                           }

                           var25.append((String)var27.getKey()).append('=').append(var27.getValue());
                        }

                        var21[var22] = var25.toString();
                     }
                  }

                  int var43 = Math.max(2, 32 - Integer.numberOfLeadingZeros(Math.max(1, var8.size() - 1)));
                  long var44 = (1L << var43) - 1L;
                  long var45 = (long)var11 * var12 * var13;

                  for (long var46 = 0L; var46 < var45; var46++) {
                     long var29 = var46 * var43;
                     int var31 = (int)(var29 >> 6);
                     int var32 = (int)((var46 + 1L) * var43 - 1L >> 6);
                     if (var32 >= var10.length) {
                        break;
                     }

                     int var33 = (int)(var29 & 63L);
                     long var34 = var31 == var32 ? var10[var31] >>> var33 & var44 : (var10[var31] >>> var33 | var10[var32] << 64 - var33) & var44;
                     if (var34 > 0L && var34 < var20.length) {
                        String var36 = var20[(int)var34];
                        if (!isAir(var36)) {
                           int var37 = (int)(var46 % var11);
                           int var38 = (int)(var46 / var11 % var13);
                           int var39 = (int)(var46 / ((long)var11 * var13));
                           var2.add(new Schematic.Entry(var17 + var37, var18 + var39, var19 + var38, var36, var21[(int)var34]));
                        }
                     }
                  }
               }
            }
         }

         return var2;
      } else {
         throw new IOException("Litematic has no Regions");
      }
   }

   private static List<Schematic.Entry> sponge(Map<String, Object> var0) throws IOException {
      Map var1 = var0.containsKey("Schematic") ? Nbt.map(var0.get("Schematic")) : var0;
      int var2 = Nbt.i(var1, "Width", 0);
      int var3 = Nbt.i(var1, "Height", 0);
      int var4 = Nbt.i(var1, "Length", 0);
      Map var7 = Nbt.map(var1.get("Blocks"));
      Map var5;
      byte[] var6;
      if (var7 != null) {
         var5 = Nbt.map(var7.get("Palette"));
         var6 = var7.get("Data") instanceof byte[] var8 ? var8 : null;
      } else {
         var5 = Nbt.map(var1.get("Palette"));
         var6 = var1.get("BlockData") instanceof byte[] var19 ? var19 : null;
      }

      if (var5 != null && var6 != null && var2 > 0 && var3 > 0 && var4 > 0) {
         HashMap var20 = new HashMap();

         for (Entry var10 : var5.entrySet()) {
            if (var10.getValue() instanceof Number var11) {
               String var26 = (String)var10.getKey();
               int var13 = var26.indexOf(91);
               String var14 = normalizeId(var13 >= 0 ? var26.substring(0, var13) : var26);
               String var15 = var13 >= 0 && var26.endsWith("]") ? var26.substring(var13 + 1, var26.length() - 1) : "";
               var20.put(var11.intValue(), new String[]{var14, var15});
            }
         }

         ArrayList var23 = new ArrayList();
         int var24 = 0;

         for (int var25 = 0; var24 < var6.length; var25++) {
            int var27 = 0;
            byte var28 = 0;

            int var29;
            do {
               var29 = var6[var24++] & 255;
               var27 |= (var29 & 127) << var28;
               var28 += 7;
            } while ((var29 & 128) != 0 && var24 < var6.length);

            String[] var30 = (String[])var20.get(var27);
            if (var30 != null && !isAir(var30[0])) {
               int var16 = var25 / (var2 * var4);
               int var17 = var25 % (var2 * var4) / var2;
               int var18 = var25 % var2;
               var23.add(new Schematic.Entry(var18, var16, var17, var30[0], var30[1]));
            }
         }

         return var23;
      } else {
         throw new IOException("Invalid .schem file");
      }
   }

   private static List<Schematic.Entry> mcedit(Map<String, Object> var0) throws IOException {
      int var1 = Nbt.i(var0, "Width", 0);
      int var2 = Nbt.i(var0, "Height", 0);
      int var3 = Nbt.i(var0, "Length", 0);
      byte[] var4 = (byte[])var0.get("Blocks");
      byte[] var5 = var0.get("Data") instanceof byte[] var6 ? var6 : new byte[var4.length];
      byte[] var16 = var0.get("AddBlocks") instanceof byte[] var17 ? var17 : null;
      if (var1 > 0 && var2 > 0 && var3 > 0 && var4.length >= var1 * var2 * var3) {
         HashMap var18 = new HashMap();
         Map var19 = Nbt.map(var0.get("SchematicaMapping"));
         if (var19 != null) {
            for (Entry var10 : var19.entrySet()) {
               if (var10.getValue() instanceof Number var11) {
                  var18.put(var11.intValue(), normalizeId((String)var10.getKey()));
               }
            }
         }

         ArrayList var20 = new ArrayList();

         for (int var21 = 0; var21 < var2; var21++) {
            for (int var22 = 0; var22 < var3; var22++) {
               for (int var23 = 0; var23 < var1; var23++) {
                  int var13 = (var21 * var3 + var22) * var1 + var23;
                  int var14 = var4[var13] & 255;
                  if (var16 != null && var13 >> 1 < var16.length) {
                     int var15 = (var13 & 1) == 0 ? var16[var13 >> 1] & 15 : var16[var13 >> 1] >> 4 & 15;
                     var14 |= var15 << 8;
                  }

                  if (var14 != 0) {
                     String var24 = var18.containsKey(var14) ? (String)var18.get(var14) : LegacyIds.get(var14, var5[var13]);
                     if (var24 != null && !isAir(var24)) {
                        var20.add(new Schematic.Entry(var23, var21, var22, var24, ""));
                     }
                  }
               }
            }
         }

         return var20;
      } else {
         throw new IOException("Invalid .schematic dimensions/Blocks");
      }
   }
}
