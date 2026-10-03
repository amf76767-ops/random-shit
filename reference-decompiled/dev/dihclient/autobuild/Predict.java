package dev.dihclient.autobuild;

import dev.dihclient.util.RegistryUtil;
import dev.dihclient.util.WalkSafety;
import dev.dihclient.waypoint.WaypointManager;
import java.util.ArrayDeque;
import java.util.HashMap;
import net.minecraft.class_2338;
import net.minecraft.class_2350;
import net.minecraft.class_2680;
import net.minecraft.class_310;

public final class Predict {
   private static final class_310 mc = class_310.method_1551();
   public static final int OK = 0;
   public static final int WAIT = 1;
   public static final int LAST = 2;
   public static final int SKIP = 3;
   private static final String[] FRAGILE = new String[]{
      "redstone_wire",
      "repeater",
      "comparator",
      "torch",
      "lever",
      "_button",
      "pressure_plate",
      "rail",
      "tripwire",
      "sapling",
      "flower",
      "tulip",
      "orchid",
      "allium",
      "bluet",
      "daisy",
      "dandelion",
      "poppy",
      "cornflower",
      "lily_of",
      "dead_bush",
      "fern",
      "short_grass",
      "tall_grass",
      "mushroom",
      "candle",
      "sugar_cane",
      "cocoa",
      "wheat",
      "carrots",
      "potatoes",
      "beetroots",
      "melon_stem",
      "pumpkin_stem",
      "nether_wart",
      "cobweb",
      "scaffolding"
   };
   private static final String[] FLAMMABLE = new String[]{
      "_planks",
      "_log",
      "_wood",
      "leaves",
      "_wool",
      "carpet",
      "hay_block",
      "bookshelf",
      "scaffolding",
      "_fence",
      "_stairs_wood",
      "tnt",
      "dried_kelp_block",
      "coal_block",
      "bamboo",
      "vine",
      "lectern",
      "composter",
      "beehive",
      "bee_nest"
   };
   private static final String[] MOVERS = new String[]{
      "piston", "dispenser", "dropper", "observer", "note_block", "redstone_lamp", "hopper", "_door", "trapdoor", "fence_gate", "bell"
   };

   private Predict() {
   }

   private static boolean has(String var0, String[] var1) {
      for (String var5 : var1) {
         if (var0.contains(var5)) {
            return true;
         }
      }

      return false;
   }

   private static boolean fluid(class_2338 var0) {
      return !mc.field_1687.method_8320(var0).method_26227().method_15769();
   }

   private static boolean powerSource(class_2680 var0) {
      String var1 = RegistryUtil.blockId(var0);
      String var2 = String.valueOf(var0);
      return var1.equals("minecraft:redstone_block") || var2.contains("powered=true") || var1.contains("redstone_torch") && var2.contains("lit=true");
   }

   private static boolean plannedPowerSource(class_2680 var0) {
      String var1 = RegistryUtil.blockId(var0);
      return var1.equals("minecraft:redstone_block")
         || var1.contains("redstone_torch")
         || var1.equals("minecraft:lever")
         || var1.endsWith("_button")
         || var1.endsWith("pressure_plate")
         || var1.equals("minecraft:daylight_detector");
   }

   private static Predict.Verdict fluidCell(BuildPlan var0, class_2338 var1, String var2, String var3) {
      BuildPlan.Planned var4 = var0.at(var1);
      return var4 != null && !var4.state().method_26218(mc.field_1687, var1).method_1110()
         ? new Predict.Verdict(
            1, var3 + " at " + var1.method_10263() + " " + var1.method_10264() + " " + var1.method_10260() + " (a planned block replaces it)"
         )
         : new Predict.Verdict(3, var3 + " would " + var2 + " (" + var1.method_10263() + " " + var1.method_10264() + " " + var1.method_10260() + ")");
   }

   public static Predict.Verdict check(BuildPlan var0, BuildPlan.Planned var1, boolean var2) {
      if (mc.field_1687 == null) {
         return null;
      } else {
         class_2338 var3 = var1.pos();
         class_2680 var4 = var1.state();
         String var5 = RegistryUtil.blockId(var4);
         if (BuildPlan.isFluid(var4)) {
            boolean var8 = var5.equals("minecraft:lava");
            if (!var2) {
               return new Predict.Verdict(2, (var8 ? "lava" : "water") + " goes in after all blocks");
            } else {
               String var7 = flood(var3, var8);
               return var7 == null ? null : new Predict.Verdict(3, (var8 ? "Lava" : "Water") + " would reach " + var7);
            }
         } else {
            Predict.Verdict var6 = null;
            if (has(var5, FRAGILE)) {
               var6 = fragile(var0, var3, var5);
            }

            if (var6 == null && has(var5, FLAMMABLE)) {
               var6 = flammable(var0, var3);
            }

            if (var6 == null) {
               var6 = power(var0, var3, var4, var5);
            }

            return var6;
         }
      }
   }

   private static String name(class_2338 var0) {
      String var1 = RegistryUtil.blockId(mc.field_1687.method_8320(var0));
      return var1.substring(var1.indexOf(58) + 1).replace('_', ' ') + " at " + var0.method_10263() + " " + var0.method_10264() + " " + var0.method_10260();
   }

   public static String flood(class_2338 var0, boolean var1) {
      int var2 = var1 ? (WaypointManager.dimKey().contains("nether") ? 7 : 3) : 7;
      ArrayDeque var3 = new ArrayDeque();
      HashMap var4 = new HashMap();
      var3.add(new Object[]{var0, 0, true});
      var4.put(var0.method_10063(), 0);
      class_2350[] var5 = new class_2350[]{class_2350.field_11043, class_2350.field_11035, class_2350.field_11039, class_2350.field_11034};

      while (!var3.isEmpty() && var4.size() < 4000) {
         Object[] var6 = (Object[])var3.poll();
         class_2338 var7 = (class_2338)var6[0];
         int var8 = (Integer)var6[1];
         boolean var9 = (Boolean)var6[2];
         if (var1) {
            for (class_2350 var13 : class_2350.values()) {
               String var14 = RegistryUtil.blockId(mc.field_1687.method_8320(var7.method_10093(var13)));
               if (has(var14, FLAMMABLE)) {
                  return name(var7.method_10093(var13)) + " (fire)";
               }
            }
         }

         class_2338 var19 = var7.method_10074();
         String var20 = var19.method_10264() >= mc.field_1687.method_31607() ? open(var19) : null;
         if (var20 != null && !var20.isEmpty()) {
            return var20;
         }

         boolean var21 = var20 != null;
         if (var21) {
            visit(var3, var4, var19, 0);
         }

         if ((!var21 || var9) && var8 < var2) {
            for (class_2350 var16 : var5) {
               class_2338 var17 = var7.method_10093(var16);
               String var18 = open(var17);
               if (var18 != null && !var18.isEmpty()) {
                  return var18;
               }

               if (var18 != null) {
                  visit(var3, var4, var17, var8 + 1);
               }
            }
         }
      }

      return null;
   }

   private static void visit(ArrayDeque<Object[]> var0, HashMap<Long, Integer> var1, class_2338 var2, int var3) {
      Integer var4 = (Integer)var1.get(var2.method_10063());
      if (var4 == null || var4 > var3) {
         var1.put(var2.method_10063(), var3);
         var0.add(new Object[]{var2, var3, false});
      }
   }

   private static String open(class_2338 var0) {
      class_2680 var1 = mc.field_1687.method_8320(var0);
      String var2 = RegistryUtil.blockId(var1);
      if (has(var2, FRAGILE)) {
         return name(var0);
      } else if (var1.method_45474() && !var1.method_26227().method_15771()) {
         return "";
      } else {
         return var1.method_26215() ? "" : null;
      }
   }

   private static Predict.Verdict fragile(BuildPlan var0, class_2338 var1, String var2) {
      if (!var2.contains("rail") && !var2.contains("sign_") && !var2.contains("carpet")) {
         boolean var11 = false;
      } else {
         boolean var10000 = true;
      }

      if (fluid(var1)) {
         return fluidCell(var0, var1, "wash it away", WalkSafety.lava(var1) ? "Lava" : "Water");
      } else {
         class_2338 var4 = var1;

         for (int var5 = 0; var5 < 8; var5++) {
            var4 = var4.method_10084();
            if (fluid(var4)) {
               return fluidCell(var0, var4, "flow down onto it", WalkSafety.lava(var4) ? "Lava" : "Water");
            }

            if (!mc.field_1687.method_8320(var4).method_45474() || var0.at(var4) != null) {
               break;
            }
         }

         for (class_2350 var8 : new class_2350[]{class_2350.field_11043, class_2350.field_11035, class_2350.field_11039, class_2350.field_11034}) {
            class_2338 var9 = var1.method_10093(var8);
            if (fluid(var9)) {
               return fluidCell(var0, var9, "flow into it", WalkSafety.lava(var9) ? "Lava" : "Water");
            }
         }

         return null;
      }
   }

   private static Predict.Verdict flammable(BuildPlan var0, class_2338 var1) {
      for (class_2350 var5 : class_2350.values()) {
         class_2338 var6 = var1.method_10093(var5);
         if (WalkSafety.lava(var6)) {
            return fluidCell(var0, var6, "set it on fire", "Lava");
         }

         String var7 = RegistryUtil.blockId(mc.field_1687.method_8320(var6));
         if (var7.equals("minecraft:fire") || var7.equals("minecraft:soul_fire")) {
            return new Predict.Verdict(1, "Fire next to it");
         }
      }

      return null;
   }

   private static Predict.Verdict power(BuildPlan var0, class_2338 var1, class_2680 var2, String var3) {
      boolean var4 = var3.equals("minecraft:tnt");
      boolean var5 = plannedPowerSource(var2);
      if (!var4 && !var5) {
         return null;
      } else {
         for (class_2350 var9 : class_2350.values()) {
            class_2338 var10 = var1.method_10093(var9);
            class_2680 var11 = mc.field_1687.method_8320(var10);
            String var12 = RegistryUtil.blockId(var11);
            if (var4 && powerSource(var11)) {
               return new Predict.Verdict(3, "TNT would ignite (powered by " + var12.substring(var12.indexOf(58) + 1) + ")");
            }

            if (var5 && var12.equals("minecraft:tnt")) {
               return new Predict.Verdict(3, "would ignite the TNT next to it");
            }

            if (var5 && has(var12, MOVERS)) {
               return new Predict.Verdict(2, "would trigger " + var12.substring(var12.indexOf(58) + 1));
            }

            BuildPlan.Planned var13 = var0.at(var10);
            if (var5 && var13 != null && has(RegistryUtil.blockId(var13.state()), MOVERS)) {
               return new Predict.Verdict(2, "would trigger " + RegistryUtil.blockId(var13.state()).replace("minecraft:", ""));
            }
         }

         return null;
      }
   }

   public record Verdict(int kind, String why) {
   }
}
