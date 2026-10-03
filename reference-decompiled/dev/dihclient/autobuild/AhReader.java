package dev.dihclient.autobuild;

import dev.dihclient.util.ItemUtil;
import dev.dihclient.util.Money;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.class_1661;
import net.minecraft.class_1703;
import net.minecraft.class_1713;
import net.minecraft.class_1735;
import net.minecraft.class_1799;
import net.minecraft.class_310;
import net.minecraft.class_465;

public final class AhReader {
   private static final class_310 mc = class_310.method_1551();
   private static final String[] NEXT = new String[]{"nächste", "naechste", "weiter", "next", "vorwärts", "vorwaerts", "→", "»", ">>", "forward"};
   private static final String[] SORT = new String[]{"sort", "sortier", "order by"};

   private AhReader() {
   }

   private static class_1703 handler() {
      return mc.field_1755 instanceof class_465 var0 ? var0.method_17577() : null;
   }

   private static List<class_1735> slots() {
      ArrayList var0 = new ArrayList();
      class_1703 var1 = handler();
      if (var1 != null) {
         for (class_1735 var3 : var1.field_7761) {
            if (!(var3.field_7871 instanceof class_1661)) {
               var0.add(var3);
            }
         }
      }

      return var0;
   }

   public static boolean open() {
      return mc.field_1755 instanceof class_465;
   }

   public static boolean hasItems() {
      for (class_1735 var1 : slots()) {
         if (!var1.method_7677().method_7960()) {
            return true;
         }
      }

      return false;
   }

   public static List<AhReader.Row> rows() {
      ArrayList var0 = new ArrayList();

      for (class_1735 var2 : slots()) {
         class_1799 var3 = var2.method_7677();
         if (!var3.method_7960()) {
            double var4 = AutoBuy.unitPrice(var3);
            if (!Double.isNaN(var4) && var4 > 0.0) {
               int var6 = Math.max(1, var3.method_7947());
               var0.add(new AhReader.Row(ItemUtil.id(var3), var2.field_7874, var6, var4, var4 * var6));
            }
         }
      }

      return var0;
   }

   public static String signature() {
      StringBuilder var0 = new StringBuilder();

      for (class_1735 var2 : slots()) {
         class_1799 var3 = var2.method_7677();
         var0.append(var3.method_7960() ? "-" : ItemUtil.id(var3) + var3.method_7947() + "@" + AutoBuy.unitPrice(var3)).append(',');
      }

      return var0.toString();
   }

   private static int find(String[] var0, boolean var1) {
      for (class_1735 var3 : slots()) {
         class_1799 var4 = var3.method_7677();
         if (!var4.method_7960() && (!var1 || Double.isNaN(AutoBuy.unitPrice(var4)))) {
            String var5 = Money.strip(var4.method_7964().getString()).toLowerCase(Locale.ROOT);

            for (String var9 : var0) {
               if (var5.contains(var9)) {
                  return var3.field_7874;
               }
            }
         }
      }

      return -1;
   }

   public static int findByWords(String... var0) {
      for (class_1735 var2 : slots()) {
         class_1799 var3 = var2.method_7677();
         if (!var3.method_7960()) {
            String var4 = Money.strip(var3.method_7964().getString()).toLowerCase(Locale.ROOT);
            boolean var5 = var0.length > 0;

            for (String var9 : var0) {
               if (!var9.isEmpty() && !var4.contains(var9)) {
                  var5 = false;
                  break;
               }
            }

            if (var5) {
               return var2.field_7874;
            }
         }
      }

      return -1;
   }

   public static int nextButton() {
      return find(NEXT, true);
   }

   public static int sortButton() {
      return find(SORT, true);
   }

   public static void click(int var0) {
      class_1703 var1 = handler();
      if (var1 != null && var0 >= 0) {
         mc.field_1761.method_2906(var1.field_7763, var0, 0, class_1713.field_7790, mc.field_1724);
      }
   }

   public static void close() {
      if (mc.field_1755 instanceof class_465 && mc.field_1724 != null) {
         mc.field_1724.method_7346();
      }
   }

   public static void command(String var0) {
      mc.field_1724.field_3944.method_45730(var0);
   }

   public record Row(String id, int slot, int count, double unit, double total) {
   }
}
