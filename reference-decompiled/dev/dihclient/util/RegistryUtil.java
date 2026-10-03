package dev.dihclient.util;

import java.util.HashSet;
import java.util.Set;
import net.minecraft.class_1297;
import net.minecraft.class_1299;
import net.minecraft.class_2248;
import net.minecraft.class_2680;
import net.minecraft.class_2960;
import net.minecraft.class_7923;

public final class RegistryUtil {
   private RegistryUtil() {
   }

   public static String blockId(class_2680 var0) {
      return class_7923.field_41175.method_10221(var0.method_26204()).toString();
   }

   public static String blockId(class_2248 var0) {
      return class_7923.field_41175.method_10221(var0).toString();
   }

   public static String entityId(class_1297 var0) {
      return class_7923.field_41177.method_10221(var0.method_5864()).toString();
   }

   public static Set<class_2248> blocks(Set<String> var0) {
      HashSet var1 = new HashSet();

      for (String var3 : var0) {
         class_2960 var4 = class_2960.method_12829(var3);
         if (var4 != null) {
            class_7923.field_41175.method_17966(var4).ifPresent(var1::add);
         }
      }

      return var1;
   }

   public static Set<class_1299<?>> entityTypes(Set<String> var0) {
      HashSet var1 = new HashSet();

      for (String var3 : var0) {
         class_2960 var4 = class_2960.method_12829(var3);
         if (var4 != null) {
            class_7923.field_41177.method_17966(var4).ifPresent(var1::add);
         }
      }

      return var1;
   }

   public static String pretty(String var0) {
      String var1 = var0.contains(":") ? var0.substring(var0.indexOf(58) + 1) : var0;
      StringBuilder var2 = new StringBuilder();

      for (String var6 : var1.split("_")) {
         if (!var6.isEmpty()) {
            if (var2.length() > 0) {
               var2.append(' ');
            }

            var2.append(Character.toUpperCase(var6.charAt(0))).append(var6.substring(1));
         }
      }

      return var2.toString();
   }
}
