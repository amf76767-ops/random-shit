package dev.dihclient.util;

import dev.dihclient.DIHClient;
import dev.dihclient.module.ModuleManager;
import dev.dihclient.modules.combat.Criticals;
import dev.dihclient.modules.combat.HitBoxes;
import dev.dihclient.modules.combat.ShieldBreaker;
import net.minecraft.class_1268;
import net.minecraft.class_1657;
import net.minecraft.class_1743;
import net.minecraft.class_1799;
import net.minecraft.class_1802;
import net.minecraft.class_310;
import net.minecraft.class_3489;
import net.minecraft.class_742;
import net.minecraft.class_9285;
import net.minecraft.class_9334;
import net.minecraft.class_9362;

public final class CombatUtil {
   private static final class_310 mc = class_310.method_1551();
   private static class_1657 target;
   private static int lastAttackTick = -100;

   private CombatUtil() {
   }

   public static class_1657 target() {
      if (target != null && (target.method_31481() || !target.method_5805() || mc.field_1724 == null || mc.field_1724.method_5739(target) > 16.0F)) {
         target = null;
      }

      return target;
   }

   public static void setTarget(class_1657 var0) {
      target = var0;
   }

   public static boolean isValidTarget(class_1657 var0) {
      return var0 != null && var0 != mc.field_1724 && var0.method_5805() && !var0.method_7325() ? !DIHClient.social().isFriend(var0) : false;
   }

   public static class_1657 findTarget(double var0) {
      if (mc.field_1687 != null && mc.field_1724 != null) {
         double var2 = ModuleManager.on(HitBoxes.class) ? ModuleManager.of(HitBoxes.class).expand.get() : 0.0;
         double var4 = (var0 + var2) * (var0 + var2);
         class_742 var6 = null;
         double var7 = Double.MAX_VALUE;

         for (class_742 var10 : mc.field_1687.method_18456()) {
            if (isValidTarget(var10)) {
               double var11 = mc.field_1724.method_5858(var10);
               if (!(var11 > var4)) {
                  double var13 = var11 - (DIHClient.social().isEnemy(var10) ? 10000 : 0);
                  if (var13 < var7) {
                     var7 = var13;
                     var6 = var10;
                  }
               }
            }
         }

         return var6;
      } else {
         return null;
      }
   }

   public static boolean attack(class_1657 var0, float var1) {
      if (mc.field_1724 != null && mc.field_1761 != null && isValidTarget(var0)) {
         if (mc.field_1724.method_7261(0.5F) < var1) {
            return false;
         } else if (DIHClient.ticks() - lastAttackTick < 1) {
            return false;
         } else {
            if (ModuleManager.on(ShieldBreaker.class) && var0.method_6039()) {
               ModuleManager.of(ShieldBreaker.class).selectAxe();
            }

            if (ModuleManager.on(Criticals.class)) {
               ModuleManager.of(Criticals.class).perform();
            }

            mc.field_1761.method_2918(mc.field_1724, var0);
            mc.field_1724.method_6104(class_1268.field_5808);
            target = var0;
            lastAttackTick = DIHClient.ticks();
            return true;
         }
      } else {
         return false;
      }
   }

   public static int weaponScore(class_1799 var0, boolean var1) {
      if (var0 != null && !var0.method_7960()) {
         int var2;
         if (var0.method_7909() instanceof class_9362) {
            var2 = 90;
         } else if (var0.method_7909() instanceof class_1743) {
            var2 = var1 ? 200 : 70;
         } else if (var0.method_31573(class_3489.field_42611)) {
            var2 = 80;
         } else {
            if (!var0.method_31574(class_1802.field_8547)) {
               return 0;
            }

            var2 = 60;
         }

         class_9285 var3 = (class_9285)var0.method_58694(class_9334.field_49636);
         int var4 = var3 == null ? 0 : var3.comp_2393().size();
         if (var0.method_31574(class_1802.field_22022) || var0.method_31574(class_1802.field_22025)) {
            var4 += 8;
         } else if (var0.method_31574(class_1802.field_8802) || var0.method_31574(class_1802.field_8556)) {
            var4 += 6;
         } else if (var0.method_31574(class_1802.field_8371) || var0.method_31574(class_1802.field_8475)) {
            var4 += 4;
         } else if (var0.method_31574(class_1802.field_8528) || var0.method_31574(class_1802.field_8062)) {
            var4 += 2;
         }

         return var2 + var4 + (var0.method_7942() ? 5 : 0);
      } else {
         return 0;
      }
   }

   public static int bestSlot(boolean var0) {
      int var1 = -1;
      int var2 = 0;

      for (int var3 = 0; var3 < 9; var3++) {
         int var4 = weaponScore(mc.field_1724.method_31548().method_5438(var3), var0);
         if (var4 > var2) {
            var2 = var4;
            var1 = var3;
         }
      }

      return var1;
   }
}
