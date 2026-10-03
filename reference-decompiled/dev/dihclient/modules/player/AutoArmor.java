package dev.dihclient.modules.player;

import dev.dihclient.module.Category;
import dev.dihclient.module.Module;
import dev.dihclient.setting.BoolSetting;
import dev.dihclient.setting.IntSetting;
import it.unimi.dsi.fastutil.objects.Object2IntMap.Entry;
import net.minecraft.class_10192;
import net.minecraft.class_1304;
import net.minecraft.class_1322;
import net.minecraft.class_1661;
import net.minecraft.class_1713;
import net.minecraft.class_1799;
import net.minecraft.class_1802;
import net.minecraft.class_1887;
import net.minecraft.class_1893;
import net.minecraft.class_465;
import net.minecraft.class_490;
import net.minecraft.class_5134;
import net.minecraft.class_5321;
import net.minecraft.class_6880;
import net.minecraft.class_9285;
import net.minecraft.class_9334;

public class AutoArmor extends Module {
   public final IntSetting delay = this.integer("Delay", "Ticks between moves.", 2, 0, 20);
   public final BoolSetting keepElytra = this.bool("Keep Elytra", "Don't replace a worn elytra with a chestplate.", true);
   public final BoolSetting skipBinding = this.bool("Skip Binding", "Never equip Curse of Binding items.", true);
   public final BoolSetting onlyInventory = this.bool("Only In Inventory", "Only while no screen or your inventory is open.", true);
   private static final class_1304[] SLOTS = new class_1304[]{class_1304.field_6169, class_1304.field_6174, class_1304.field_6172, class_1304.field_6166};
   private int wait;

   public AutoArmor() {
      super("AutoArmor", Category.PLAYER, "Automatically equips the best armour in your inventory.");
   }

   private static int armorSlot(class_1304 var0) {
      return switch (var0) {
         case field_6169 -> 5;
         case field_6174 -> 6;
         case field_6172 -> 7;
         default -> 8;
      };
   }

   private static boolean hasEnchant(class_1799 var0, class_5321<class_1887> var1) {
      for (Object var3 : var0.method_58657().method_57539()) {
         Entry var4 = (Entry)var3;
         if (((class_6880)var4.getKey()).method_40225(var1)) {
            return true;
         }
      }

      return false;
   }

   private static int enchantLevel(class_1799 var0, class_5321<class_1887> var1) {
      for (Object var3 : var0.method_58657().method_57539()) {
         Entry var4 = (Entry)var3;
         if (((class_6880)var4.getKey()).method_40225(var1)) {
            return var4.getIntValue();
         }
      }

      return 0;
   }

   private static double score(class_1799 var0, class_1304 var1) {
      if (var0.method_7960()) {
         return -1.0;
      } else {
         class_10192 var2 = (class_10192)var0.method_58694(class_9334.field_54196);
         if (var2 != null && var2.comp_3174() == var1 && !var0.method_31574(class_1802.field_8833)) {
            double[] var3 = new double[3];
            ((class_9285)var0.method_58695(class_9334.field_49636, class_9285.field_49326)).method_57482(var1, (var1x, var2x) -> {
               if (var1x.equals(class_5134.field_23724)) {
                  var3[0] += ((class_1322)var2x).comp_2449();
               } else if (var1x.equals(class_5134.field_23725)) {
                  var3[1] += ((class_1322)var2x).comp_2449();
               } else if (var1x.equals(class_5134.field_23718)) {
                  var3[2] += ((class_1322)var2x).comp_2449();
               }
            });
            if (var3[0] <= 0.0 && var3[1] <= 0.0) {
               return -1.0;
            } else {
               double var4 = var3[0] * 10.0 + var3[1] * 4.0 + var3[2] * 10.0 + enchantLevel(var0, class_1893.field_9111) * 3;
               if (var0.method_7963() && var0.method_7936() > 0) {
                  var4 += 1.0 - (double)var0.method_7919() / var0.method_7936();
               }

               return var4;
            }
         } else {
            return -1.0;
         }
      }
   }

   @Override
   public void onTick() {
      if (this.wait > 0) {
         this.wait--;
      } else if ((!this.onlyInventory.get() || mc.field_1755 == null || mc.field_1755 instanceof class_490)
         && (!(mc.field_1755 instanceof class_465) || mc.field_1755 instanceof class_490)
         && mc.field_1724.field_7498.method_34255().method_7960()) {
         class_1661 var1 = mc.field_1724.method_31548();
         int var2 = mc.field_1724.field_7498.field_7763;

         for (class_1304 var6 : SLOTS) {
            class_1799 var7 = mc.field_1724.method_6118(var6);
            if ((var6 != class_1304.field_6174 || !this.keepElytra.get() || !var7.method_31574(class_1802.field_8833))
               && !hasEnchant(var7, class_1893.field_9113)) {
               double var8 = score(var7, var6);
               int var10 = -1;
               double var11 = var8;

               for (int var13 = 0; var13 < 36; var13++) {
                  class_1799 var14 = var1.method_5438(var13);
                  double var15 = score(var14, var6);
                  if (var15 > var11 && (!this.skipBinding.get() || !hasEnchant(var14, class_1893.field_9113))) {
                     var11 = var15;
                     var10 = var13;
                  }
               }

               if (var10 >= 0) {
                  int var17 = var10 < 9 ? 36 + var10 : var10;
                  int var18 = armorSlot(var6);
                  if (var7.method_7960()) {
                     mc.field_1761.method_2906(var2, var17, 0, class_1713.field_7794, mc.field_1724);
                  } else {
                     mc.field_1761.method_2906(var2, var17, 0, class_1713.field_7790, mc.field_1724);
                     mc.field_1761.method_2906(var2, var18, 0, class_1713.field_7790, mc.field_1724);
                     mc.field_1761.method_2906(var2, var17, 0, class_1713.field_7790, mc.field_1724);
                  }

                  this.wait = this.delay.get();
                  return;
               }
            }
         }
      }
   }
}
