package dev.dihclient.util;

import java.util.function.Predicate;
import net.minecraft.class_1661;
import net.minecraft.class_1713;
import net.minecraft.class_1792;
import net.minecraft.class_1799;
import net.minecraft.class_310;

public final class InvUtil {
   private static final class_310 mc = class_310.method_1551();
   public static final int OFFHAND_SCREEN_SLOT = 45;

   private InvUtil() {
   }

   public static int findHotbar(Predicate<class_1799> var0) {
      if (mc.field_1724 == null) {
         return -1;
      } else {
         class_1661 var1 = mc.field_1724.method_31548();

         for (int var2 = 0; var2 < 9; var2++) {
            class_1799 var3 = var1.method_5438(var2);
            if (!var3.method_7960() && var0.test(var3)) {
               return var2;
            }
         }

         return -1;
      }
   }

   public static int findHotbar(class_1792 var0) {
      return findHotbar((Predicate<class_1799>)(var1 -> var1.method_31574(var0)));
   }

   public static int findInventory(Predicate<class_1799> var0) {
      if (mc.field_1724 == null) {
         return -1;
      } else {
         class_1661 var1 = mc.field_1724.method_31548();

         for (int var2 = 0; var2 < 36; var2++) {
            class_1799 var3 = var1.method_5438(var2);
            if (!var3.method_7960() && var0.test(var3)) {
               return var2;
            }
         }

         return -1;
      }
   }

   public static int count(Predicate<class_1799> var0) {
      if (mc.field_1724 == null) {
         return 0;
      } else {
         int var1 = 0;
         class_1661 var2 = mc.field_1724.method_31548();

         for (int var3 = 0; var3 < 36; var3++) {
            class_1799 var4 = var2.method_5438(var3);
            if (!var4.method_7960() && var0.test(var4)) {
               var1 += var4.method_7947();
            }
         }

         class_1799 var5 = mc.field_1724.method_6079();
         if (!var5.method_7960() && var0.test(var5)) {
            var1 += var5.method_7947();
         }

         return var1;
      }
   }

   public static int selectedSlot() {
      return mc.field_1724 == null ? 0 : mc.field_1724.method_31548().method_67532();
   }

   public static void select(int var0) {
      if (mc.field_1724 != null && var0 >= 0 && var0 <= 8) {
         mc.field_1724.method_31548().method_61496(var0);
      }
   }

   public static int firstEmptyHotbar() {
      return findHotbarEmpty();
   }

   private static int findHotbarEmpty() {
      if (mc.field_1724 == null) {
         return -1;
      } else {
         for (int var0 = 0; var0 < 9; var0++) {
            if (mc.field_1724.method_31548().method_5438(var0).method_7960()) {
               return var0;
            }
         }

         return -1;
      }
   }

   public static int toScreenSlot(int var0) {
      return var0 >= 0 && var0 < 9 ? 36 + var0 : var0;
   }

   public static void swapToHotbar(int var0, int var1) {
      if (mc.field_1724 != null && mc.field_1761 != null) {
         mc.field_1761.method_2906(mc.field_1724.field_7498.field_7763, toScreenSlot(var0), var1, class_1713.field_7791, mc.field_1724);
      }
   }

   public static void swapToOffhand(int var0) {
      if (mc.field_1724 != null && mc.field_1761 != null) {
         mc.field_1761.method_2906(mc.field_1724.field_7498.field_7763, toScreenSlot(var0), 40, class_1713.field_7791, mc.field_1724);
      }
   }

   public static void quickMove(int var0, int var1) {
      if (mc.field_1724 != null && mc.field_1761 != null) {
         mc.field_1761.method_2906(var0, var1, 0, class_1713.field_7794, mc.field_1724);
      }
   }
}
