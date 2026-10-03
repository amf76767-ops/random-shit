package dev.dihclient.autobuild;

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map.Entry;
import java.util.zip.GZIPOutputStream;

public final class SchematicWriter {
   private SchematicWriter() {
   }

   public static void writeSponge(Path var0, List<SchematicWriter.Block> var1, int var2) throws IOException {
      int var3 = 1;
      int var4 = 1;
      int var5 = 1;

      for (SchematicWriter.Block var7 : var1) {
         var3 = Math.max(var3, var7.x() + 1);
         var4 = Math.max(var4, var7.y() + 1);
         var5 = Math.max(var5, var7.z() + 1);
      }

      LinkedHashMap var17 = new LinkedHashMap();
      var17.put("minecraft:air", 0);
      int[] var18 = new int[var3 * var4 * var5];

      for (SchematicWriter.Block var9 : var1) {
         int var10 = var17.computeIfAbsent(var9.state(), var1x -> var17.size());
         var18[(var9.y() * var5 + var9.z()) * var3 + var9.x()] = var10;
      }

      ByteArrayOutputStream var19 = new ByteArrayOutputStream(var18.length);
      int[] var20 = var18;
      int var22 = var18.length;

      for (int var11 = 0; var11 < var22; var11++) {
         int var12;
         for (var12 = var20[var11]; (var12 & -128) != 0; var12 >>>= 7) {
            var19.write(var12 & 127 | 128);
         }

         var19.write(var12);
      }

      Files.createDirectories(var0.getParent());

      try (
         GZIPOutputStream var21 = new GZIPOutputStream(new BufferedOutputStream(Files.newOutputStream(var0)));
         DataOutputStream var23 = new DataOutputStream(var21);
      ) {
         var23.writeByte(10);
         str(var23, "Schematic");
         intTag(var23, "Version", 2);
         intTag(var23, "DataVersion", var2);
         shortTag(var23, "Width", var3);
         shortTag(var23, "Height", var4);
         shortTag(var23, "Length", var5);
         intTag(var23, "PaletteMax", var17.size());
         var23.writeByte(10);
         str(var23, "Palette");

         for (Entry var26 : var17.entrySet()) {
            intTag(var23, (String)var26.getKey(), (Integer)var26.getValue());
         }

         var23.writeByte(0);
         byte[] var25 = var19.toByteArray();
         var23.writeByte(7);
         str(var23, "BlockData");
         var23.writeInt(var25.length);
         var23.write(var25);
         var23.writeByte(11);
         str(var23, "Offset");
         var23.writeInt(3);
         var23.writeInt(0);
         var23.writeInt(0);
         var23.writeInt(0);
         var23.writeByte(0);
      }
   }

   private static void str(DataOutputStream var0, String var1) throws IOException {
      byte[] var2 = var1.getBytes(StandardCharsets.UTF_8);
      var0.writeShort(var2.length);
      var0.write(var2);
   }

   private static void intTag(DataOutputStream var0, String var1, int var2) throws IOException {
      var0.writeByte(3);
      str(var0, var1);
      var0.writeInt(var2);
   }

   private static void shortTag(DataOutputStream var0, String var1, int var2) throws IOException {
      var0.writeByte(2);
      str(var0, var1);
      var0.writeShort(var2);
   }

   public record Block(int x, int y, int z, String state) {
   }
}
