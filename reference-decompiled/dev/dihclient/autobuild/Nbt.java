package dev.dihclient.autobuild;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;

public final class Nbt {
   private Nbt() {
   }

   public static Map<String, Object> read(Path var0) throws IOException {
      Map var8;
      try (BufferedInputStream var1 = new BufferedInputStream(Files.newInputStream(var0))) {
         var1.mark(2);
         int var2 = var1.read();
         int var3 = var1.read();
         var1.reset();
         Object var4 = var2 == 31 && var3 == 139 ? new GZIPInputStream(var1) : var1;
         DataInputStream var5 = new DataInputStream(new BufferedInputStream((InputStream)var4));
         int var6 = var5.readUnsignedByte();
         if (var6 != 10) {
            throw new IOException("NBT root is not a compound");
         }

         readString(var5);
         Map var7 = (Map)payload(var5, 10, 0);
         var8 = var7;
      }

      return var8;
   }

   private static String readString(DataInputStream var0) throws IOException {
      int var1 = var0.readUnsignedShort();
      byte[] var2 = new byte[var1];
      var0.readFully(var2);
      return new String(var2, StandardCharsets.UTF_8);
   }

   private static Object payload(DataInputStream var0, int var1, int var2) throws IOException {
      if (var2 > 512) {
         throw new IOException("NBT too deep");
      } else {
         switch (var1) {
            case 1:
               return var0.readByte();
            case 2:
               return var0.readShort();
            case 3:
               return var0.readInt();
            case 4:
               return var0.readLong();
            case 5:
               return var0.readFloat();
            case 6:
               return var0.readDouble();
            case 7:
               int var10 = var0.readInt();
               if (var10 >= 0 && var10 <= 67108864) {
                  byte[] var14 = new byte[var10];
                  var0.readFully(var14);
                  return var14;
               }

               throw new IOException("bad byte array");
            case 8:
               return readString(var0);
            case 9:
               int var9 = var0.readUnsignedByte();
               int var13 = var0.readInt();
               if (var13 >= 0 && var13 <= 16777216) {
                  ArrayList var17 = new ArrayList(Math.min(var13, 4096));

                  for (int var6 = 0; var6 < var13; var6++) {
                     var17.add(payload(var0, var9, var2 + 1));
                  }

                  return var17;
               } else {
                  throw new IOException("bad list");
               }
            case 10:
               LinkedHashMap var8 = new LinkedHashMap();

               while (true) {
                  int var12 = var0.readUnsignedByte();
                  if (var12 == 0) {
                     return var8;
                  }

                  String var16 = readString(var0);
                  var8.put(var16, payload(var0, var12, var2 + 1));
               }
            case 11:
               int var7 = var0.readInt();
               if (var7 >= 0 && var7 <= 16777216) {
                  int[] var11 = new int[var7];

                  for (int var15 = 0; var15 < var7; var15++) {
                     var11[var15] = var0.readInt();
                  }

                  return var11;
               }

               throw new IOException("bad int array");
            case 12:
               int var3 = var0.readInt();
               if (var3 >= 0 && var3 <= 16777216) {
                  long[] var4 = new long[var3];

                  for (int var5 = 0; var5 < var3; var5++) {
                     var4[var5] = var0.readLong();
                  }

                  return var4;
               }

               throw new IOException("bad long array");
            default:
               throw new IOException("Unknown NBT tag " + var1);
         }
      }
   }

   public static Map<String, Object> map(Object var0) {
      return var0 instanceof Map var1 ? var1 : null;
   }

   public static List<Object> list(Object var0) {
      return var0 instanceof List var1 ? var1 : null;
   }

   public static int i(Map<String, Object> var0, String var1, int var2) {
      return (var0 == null ? null : var0.get(var1)) instanceof Number var4 ? var4.intValue() : var2;
   }

   public static String s(Map<String, Object> var0, String var1) {
      return (var0 == null ? null : var0.get(var1)) instanceof String var3 ? var3 : null;
   }
}
