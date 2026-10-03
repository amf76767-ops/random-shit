package dev.dihclient.util;

import java.util.HashSet;
import java.util.Set;
import net.minecraft.class_1792;
import net.minecraft.class_1799;
import net.minecraft.class_2248;
import net.minecraft.class_2680;
import net.minecraft.class_2960;
import net.minecraft.class_7923;

public final class ItemUtil {
   private ItemUtil() {
   }

   public static String id(class_1792 var0) {
      return class_7923.field_41178.method_10221(var0).toString();
   }

   public static String id(class_1799 var0) {
      return var0 != null && !var0.method_7960() ? id(var0.method_7909()) : "minecraft:air";
   }

   public static class_1792 item(String var0) {
      class_2960 var1 = class_2960.method_12829(var0);
      return var1 == null ? null : (class_1792)class_7923.field_41178.method_63535(var1);
   }

   public static class_2248 block(String var0) {
      class_2960 var1 = class_2960.method_12829(var0);
      return var1 == null ? null : (class_2248)class_7923.field_41175.method_63535(var1);
   }

   public static Set<class_1792> items(Set<String> var0) {
      HashSet var1 = new HashSet();

      for (String var3 : var0) {
         class_2960 var4 = class_2960.method_12829(var3);
         if (var4 != null) {
            class_7923.field_41178.method_17966(var4).ifPresent(var1x -> var1.add(var1x));
         }
      }

      return var1;
   }

   public static String prop(class_2680 var0, String var1) {
      String var2 = var0.toString();
      int var3 = var2.indexOf(91);
      if (var3 < 0) {
         return null;
      } else {
         for (String var7 : var2.substring(var3 + 1, var2.length() - 1).split(",")) {
            int var8 = var7.indexOf(61);
            if (var8 > 0 && var7.substring(0, var8).equals(var1)) {
               return var7.substring(var8 + 1);
            }
         }

         return null;
      }
   }

   public static int intProp(class_2680 var0, String var1, int var2) {
      String var3 = prop(var0, var1);
      if (var3 == null) {
         return var2;
      } else {
         try {
            return Integer.parseInt(var3);
         } catch (NumberFormatException var5) {
            return var2;
         }
      }
   }

   public static int durabilityLeft(class_1799 var0) {
      return var0.method_7963() ? var0.method_7936() - var0.method_7919() : Integer.MAX_VALUE;
   }
}
