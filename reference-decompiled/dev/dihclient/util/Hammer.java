package dev.dihclient.util;

import dev.dihclient.autobuild.Worker;
import dev.dihclient.module.ModuleManager;
import dev.dihclient.modules.player.HammerTool;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;
import net.minecraft.class_1799;
import net.minecraft.class_1836;
import net.minecraft.class_2246;
import net.minecraft.class_2338;
import net.minecraft.class_2350;
import net.minecraft.class_243;
import net.minecraft.class_2561;
import net.minecraft.class_2680;
import net.minecraft.class_310;
import net.minecraft.class_1792.class_9635;
import net.minecraft.class_2350.class_2351;

public final class Hammer {
   private static final class_310 mc = class_310.method_1551();
   private static final class_2350[] ALL = class_2350.values();
   private static long cacheTick = -1L;
   private static int cacheSlot = -2;
   private static String keywordSource;
   private static String[] keywordCache = new String[0];
   private static final IdentityHashMap<class_1799, Boolean> HAMMER_CACHE = new IdentityHashMap<>();
   private static long hammerCacheBucket = Long.MIN_VALUE;

   private Hammer() {
   }

   private static String[] keywords() {
      HammerTool var0 = ModuleManager.of(HammerTool.class);
      String var1 = var0 == null ? "3x3,hammer" : var0.names.get();
      if (!var1.equals(keywordSource)) {
         String[] var2 = var1.toLowerCase(Locale.ROOT).split(",");

         for (int var3 = 0; var3 < var2.length; var3++) {
            var2[var3] = var2[var3].trim();
         }

         keywordSource = var1;
         keywordCache = var2;
         HAMMER_CACHE.clear();
      }

      return keywordCache;
   }

   public static boolean isHammer(class_1799 var0) {
      if (var0 != null && !var0.method_7960()) {
         String[] var1 = keywords();
         long var2 = mc.field_1724 == null ? System.currentTimeMillis() / 1000L : mc.field_1724.field_6012 / 20;
         if (var2 != hammerCacheBucket || HAMMER_CACHE.size() > 256) {
            hammerCacheBucket = var2;
            HAMMER_CACHE.clear();
         }

         Boolean var4 = HAMMER_CACHE.get(var0);
         if (var4 == null) {
            var4 = isHammerNow(var0, var1);
            HAMMER_CACHE.put(var0, var4);
         }

         return var4;
      } else {
         return false;
      }
   }

   private static boolean isHammerNow(class_1799 var0, String[] var1) {
      StringBuilder var2 = new StringBuilder(Money.strip(var0.method_7964().getString()).toLowerCase(Locale.ROOT));

      try {
         List var3 = var0.method_7950(class_9635.field_51353, mc.field_1724, class_1836.field_41070);

         for (int var4 = 1; var4 < var3.size(); var4++) {
            var2.append('\n').append(Money.strip(((class_2561)var3.get(var4)).getString()).toLowerCase(Locale.ROOT));
         }
      } catch (Throwable var8) {
      }

      String var9 = var2.toString();

      for (String var7 : var1) {
         if (!var7.isEmpty() && var9.contains(var7)) {
            return true;
         }
      }

      return false;
   }

   public static int find() {
      if (mc.field_1724 == null) {
         return -1;
      } else {
         long var0 = mc.field_1724.field_6012;
         if (var0 == cacheTick && cacheSlot != -2) {
            return cacheSlot;
         } else {
            int var2 = -1;

            for (int var3 = 0; var3 < 36 && var2 < 0; var3++) {
               class_1799 var4 = mc.field_1724.method_31548().method_5438(var3);
               if (isHammer(var4) && (!var4.method_7963() || var4.method_7936() - var4.method_7919() >= Math.max(9, Worker.toolSaver))) {
                  var2 = var3;
               }
            }

            cacheTick = var0;
            cacheSlot = var2;
            return var2;
         }
      }
   }

   public static boolean available() {
      return find() >= 0;
   }

   public static boolean select() {
      int var0 = find();
      if (var0 < 0) {
         return false;
      } else {
         if (var0 >= 9) {
            int var1 = InvUtil.firstEmptyHotbar();
            if (var1 < 0) {
               var1 = InvUtil.selectedSlot();
            }

            InvUtil.swapToHotbar(var0, var1);
            cacheSlot = -2;
            var0 = var1;
         }

         InvUtil.select(var0);
         return true;
      }
   }

   public static class_2350 face(class_2338 var0, class_243 var1) {
      class_243 var2 = var1.method_1020(class_243.method_24953(var0));
      return class_2350.method_10142(var2.field_1352, var2.field_1351, var2.field_1350);
   }

   public static List<class_2338> square(class_2338 var0, class_2350 var1) {
      ArrayList var2 = new ArrayList(9);
      class_2351 var3 = var1.method_10166();

      for (int var4 = -1; var4 <= 1; var4++) {
         for (int var5 = -1; var5 <= 1; var5++) {
            if (var3 == class_2351.field_11052) {
               var2.add(var0.method_10069(var4, 0, var5));
            } else if (var3 == class_2351.field_11048) {
               var2.add(var0.method_10069(0, var4, var5));
            } else {
               var2.add(var0.method_10069(var4, var5, 0));
            }
         }
      }

      return var2;
   }

   private static boolean air(class_2338 var0) {
      class_2680 var1 = mc.field_1687.method_8320(var0);
      return var1.method_26215() || !var1.method_26227().method_15769() && var1.method_45474();
   }

   private static boolean nextToLava(class_2338 var0) {
      for (class_2350 var4 : ALL) {
         if (mc.field_1687.method_8320(var0.method_10093(var4)).method_27852(class_2246.field_10164)) {
            return true;
         }
      }

      return false;
   }

   public static boolean safe(class_2338 var0, class_2350 var1, Predicate<class_2338> var2) {
      for (class_2338 var4 : square(var0, var1)) {
         if (!air(var4)) {
            class_2680 var5 = mc.field_1687.method_8320(var4);
            if (!(var5.method_26214(mc.field_1687, var4) < 0.0F)) {
               if (!var4.equals(var0) && !var2.test(var4)) {
                  return false;
               }

               if (var5.method_31709() || nextToLava(var4)) {
                  return false;
               }
            }
         }
      }

      return true;
   }

   public static class_2338 bestCenter(class_2338 var0, double var1, Predicate<class_2338> var3, Predicate<class_2338> var4) {
      if (mc.field_1724 != null && mc.field_1687 != null) {
         class_243 var5 = mc.field_1724.method_33571();
         class_2338 var6 = null;
         double var7 = -1.0;

         for (int var9 = -1; var9 <= 1; var9++) {
            for (int var10 = -1; var10 <= 1; var10++) {
               for (int var11 = -1; var11 <= 1; var11++) {
                  class_2338 var12 = var0.method_10069(var9, var10, var11);
                  if (!air(var12)
                     && !(mc.field_1687.method_8320(var12).method_26214(mc.field_1687, var12) < 0.0F)
                     && !(class_243.method_24953(var12).method_1022(var5) > var1 + 0.5)
                     && (var12.equals(var0) || var3.test(var12))) {
                     class_2350 var13 = face(var12, var5);
                     List var14 = square(var12, var13);
                     if (var14.contains(var0) && safe(var12, var13, var3)) {
                        int var15 = 0;

                        for (class_2338 var17 : var14) {
                           if (!air(var17) && (var17.equals(var0) || var4.test(var17))) {
                              var15++;
                           }
                        }

                        double var18 = var15 * 10.0 + (var12.equals(var0) ? 1.0 : 0.0) - class_243.method_24953(var12).method_1022(var5) * 0.01;
                        if (var18 > var7) {
                           var7 = var18;
                           var6 = var12;
                        }
                     }
                  }
               }
            }
         }

         return var6;
      } else {
         return null;
      }
   }
}
