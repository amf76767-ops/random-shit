package dev.dihclient.util;

import java.util.Set;
import net.minecraft.class_1713;
import net.minecraft.class_1799;
import net.minecraft.class_310;

public final class JunkUtil {
   private static final class_310 mc = class_310.method_1551();

   private JunkUtil() {
   }

   public static boolean full() {
      return mc.field_1724 != null && mc.field_1724.method_31548().method_7376() < 0;
   }

   public static int drop(Set<String> var0, int var1) {
      return drop(var0, var1, 0);
   }

   public static boolean ensurePickaxe(int var0) {
      return ensurePickaxe(var0, false);
   }

   public static boolean ensurePickaxe(int var0, boolean var1) {
      int var2 = -1;
      boolean var3 = false;

      for (int var4 = 0; var4 < 36; var4++) {
         class_1799 var5 = mc.field_1724.method_31548().method_5438(var4);
         if (!var5.method_7960() && ItemUtil.id(var5).endsWith("_pickaxe") && ItemUtil.durabilityLeft(var5) >= var0) {
            if (var1 && Hammer.isHammer(var5)) {
               var3 = true;
            } else {
               if (var4 < 9) {
                  return true;
               }

               if (var2 < 0) {
                  var2 = var4;
               }
            }
         }
      }

      if (var2 < 0) {
         return var3 || var1 && Hammer.available();
      } else {
         int var7 = -1;

         for (int var8 = 0; var8 < 9; var8++) {
            class_1799 var6 = mc.field_1724.method_31548().method_5438(var8);
            if (!var6.method_7960() && ItemUtil.id(var6).endsWith("_pickaxe") && (!var1 || !Hammer.isHammer(var6))) {
               var7 = var8;
               break;
            }
         }

         if (var7 < 0) {
            var7 = InvUtil.firstEmptyHotbar();
         }

         if (var7 < 0) {
            var7 = InvUtil.selectedSlot();
            if (var1 && Hammer.isHammer(mc.field_1724.method_31548().method_5438(var7))) {
               var7 = (var7 + 1) % 9;
            }
         }

         InvUtil.swapToHotbar(var2, var7);
         return true;
      }
   }

   public static int drop(Set<String> var0, int var1, int var2) {
      int var3 = 0;
      if (mc.field_1724 != null && mc.field_1761 != null && mc.field_1755 == null && !var0.isEmpty()) {
         int var4 = 0;
         int var5 = mc.field_1724.field_7498.field_7763;

         for (int var6 = 0; var6 < 36 && var4 < var1; var6++) {
            class_1799 var7 = mc.field_1724.method_31548().method_5438(var6);
            if (!var7.method_7960() && var0.contains(ItemUtil.id(var7))) {
               if (++var3 > var2) {
                  int var8 = InvUtil.toScreenSlot(var6);
                  mc.field_1761.method_2906(var5, var8, 0, class_1713.field_7790, mc.field_1724);
                  mc.field_1761.method_2906(var5, -999, 0, class_1713.field_7790, mc.field_1724);
                  var4++;
               }
            }
         }

         return var4;
      } else {
         return 0;
      }
   }

   public static int count(Set<String> var0) {
      if (mc.field_1724 == null) {
         return 0;
      } else {
         int var1 = 0;

         for (int var2 = 0; var2 < 36; var2++) {
            class_1799 var3 = mc.field_1724.method_31548().method_5438(var2);
            if (!var3.method_7960() && var0.contains(ItemUtil.id(var3))) {
               var1++;
            }
         }

         return var1;
      }
   }
}
