package dev.dihclient.autobuild;

import java.nio.file.Path;
import java.util.List;

public final class Schematic {
   public final Path file;
   public final String format;
   public final List<Schematic.Entry> blocks;
   public final int sizeX;
   public final int sizeY;
   public final int sizeZ;
   public boolean mapGrid;
   public boolean mapNoobline;

   Schematic(Path var1, String var2, List<Schematic.Entry> var3, int var4, int var5, int var6) {
      this.file = var1;
      this.format = var2;
      this.blocks = var3;
      this.sizeX = var4;
      this.sizeY = var5;
      this.sizeZ = var6;
   }

   public String name() {
      String var1 = this.file.getFileName().toString();
      int var2 = var1.lastIndexOf(46);
      return var2 > 0 ? var1.substring(0, var2) : var1;
   }

   public static final class Entry {
      public final int x;
      public final int y;
      public final int z;
      public final String id;
      public final String properties;

      Entry(int var1, int var2, int var3, String var4, String var5) {
         this.x = var1;
         this.y = var2;
         this.z = var3;
         this.id = var4;
         this.properties = var5 == null ? "" : var5;
      }

      public String property(String var1) {
         for (String var5 : this.properties.split(",")) {
            int var6 = var5.indexOf(61);
            if (var6 > 0 && var5.substring(0, var6).trim().equals(var1)) {
               return var5.substring(var6 + 1).trim();
            }
         }

         return null;
      }
   }
}
