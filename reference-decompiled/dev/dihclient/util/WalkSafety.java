package dev.dihclient.util;

import dev.dihclient.module.ModuleManager;
import dev.dihclient.modules.player.InvManager;
import dev.dihclient.modules.world.SafeRoute;
import net.minecraft.class_1661;
import net.minecraft.class_1799;
import net.minecraft.class_2338;
import net.minecraft.class_2350;
import net.minecraft.class_243;
import net.minecraft.class_2680;
import net.minecraft.class_310;

public final class WalkSafety {
   private static final class_310 mc = class_310.method_1551();

   private WalkSafety() {
   }

   public static boolean lava(class_2338 var0) {
      return RegistryUtil.blockId(mc.field_1687.method_8320(var0)).equals("minecraft:lava");
   }

   public static boolean solid(class_2338 var0) {
      class_2680 var1 = mc.field_1687.method_8320(var0);
      return !var1.method_26215() && (var1.method_26227().method_15769() || !var1.method_45474()) && !var1.method_26218(mc.field_1687, var0).method_1110();
   }

   private static boolean water(class_2338 var0) {
      class_2680 var1 = mc.field_1687.method_8320(var0);
      return !var1.method_26227().method_15769() && !lava(var0);
   }

   public static boolean safeToward(class_243 var0) {
      class_243 var1 = mc.field_1724.method_73189();
      double var2 = var0.field_1352 - var1.field_1352;
      double var4 = var0.field_1350 - var1.field_1350;
      if (var2 * var2 + var4 * var4 < 0.04) {
         return true;
      } else {
         class_2350 var6 = Math.abs(var2) > Math.abs(var4)
            ? (var2 > 0.0 ? class_2350.field_11034 : class_2350.field_11039)
            : (var4 > 0.0 ? class_2350.field_11035 : class_2350.field_11043);
         class_2338 var7 = mc.field_1724.method_24515().method_10093(var6);
         if (lava(var7) || lava(var7.method_10084()) || lava(var7.method_10074())) {
            return false;
         } else if (solid(var7)) {
            return !SafeRoute.mobToward(var0);
         } else {
            int var8 = 0;

            for (class_2338 var9 = var7.method_10074(); var8 < 4 && !solid(var9); var9 = var9.method_10074()) {
               if (lava(var9)) {
                  return false;
               }

               if (water(var9)) {
                  return true;
               }

               var8++;
            }

            return var8 < SafeRoute.maxFall(3) && !SafeRoute.mobToward(var0);
         }
      }
   }

   public static boolean hasRoom(class_1799 var0) {
      class_1661 var1 = mc.field_1724.method_31548();

      for (int var2 = 0; var2 < 36; var2++) {
         class_1799 var3 = var1.method_5438(var2);
         if (var3.method_7960() || var3.method_7909() == var0.method_7909() && var3.method_7947() < var3.method_7909().method_7882()) {
            return true;
         }
      }

      return false;
   }

   public static boolean isJunk(class_1799 var0) {
      InvManager var1 = ModuleManager.of(InvManager.class);
      return var1 != null && var1.isEnabled() && var1.dropJunk.get() && var1.junk.contains(ItemUtil.id(var0));
   }

   public static boolean worthCollecting(class_1799 var0) {
      return !var0.method_7960() && !isJunk(var0) && hasRoom(var0);
   }
}
