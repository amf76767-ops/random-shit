package dev.dihclient.autobuild;

import dev.dihclient.mixin.accessor.BlockItemInvoker;
import dev.dihclient.util.RotationUtil;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import net.minecraft.class_1268;
import net.minecraft.class_1747;
import net.minecraft.class_1750;
import net.minecraft.class_1799;
import net.minecraft.class_2237;
import net.minecraft.class_2244;
import net.minecraft.class_2248;
import net.minecraft.class_2269;
import net.minecraft.class_2272;
import net.minecraft.class_2304;
import net.minecraft.class_2309;
import net.minecraft.class_2312;
import net.minecraft.class_2323;
import net.minecraft.class_2338;
import net.minecraft.class_2349;
import net.minecraft.class_2350;
import net.minecraft.class_2401;
import net.minecraft.class_2428;
import net.minecraft.class_243;
import net.minecraft.class_2533;
import net.minecraft.class_2680;
import net.minecraft.class_2741;
import net.minecraft.class_2760;
import net.minecraft.class_2769;
import net.minecraft.class_2771;
import net.minecraft.class_310;
import net.minecraft.class_3532;
import net.minecraft.class_3709;
import net.minecraft.class_3959;
import net.minecraft.class_3962;
import net.minecraft.class_3965;
import net.minecraft.class_746;
import net.minecraft.class_2350.class_2351;
import net.minecraft.class_239.class_240;
import net.minecraft.class_3959.class_242;
import net.minecraft.class_3959.class_3960;

public final class PlacementSolver {
   private static final class_310 mc = class_310.method_1551();
   private static final Set<String> PLACEMENT_PROPS = Set.of(
      "facing", "axis", "half", "face", "rotation", "hanging", "attachment", "orientation", "vertical_direction", "type"
   );
   public static final Set<String> INTERACT_PROPS = Set.of("open", "delay", "mode", "note", "powered", "inverted");
   private static final float[] PITCHES = new float[]{0.0F, 75.0F, -75.0F};
   private static final double[] GRID = new double[]{0.5, 0.2, 0.8};
   public static boolean visibleOnly = true;
   public static boolean ignoreAxis = false;
   private static final IdentityHashMap<class_2680, class_2769<?>[]> PROPS_CACHE = new IdentityHashMap<>();
   public static boolean sneakInteractive;

   private PlacementSolver() {
   }

   public static boolean placementMatches(class_2680 var0, class_2680 var1) {
      if (var0.method_26204() != var1.method_26204()) {
         return false;
      } else {
         for (class_2769 var5 : placementProps(var1)) {
            if (!ignoreAxis || !var5.method_11899().equals("axis")) {
               if (!var0.method_28498(var5)) {
                  return false;
               }

               Comparable var6 = var0.method_11654(var5);
               Comparable var7 = var1.method_11654(var5);
               if (!var6.equals(var7) && (var7 != class_2771.field_12682 || var6 == class_2771.field_12682)) {
                  return false;
               }
            }
         }

         return true;
      }
   }

   private static class_2769<?>[] placementProps(class_2680 var0) {
      class_2769[] var1 = PROPS_CACHE.get(var0);
      if (var1 == null) {
         ArrayList var2 = new ArrayList();

         for (class_2769 var4 : var0.method_28501()) {
            if (PLACEMENT_PROPS.contains(var4.method_11899()) && (!var4.method_11899().equals("type") || var0.method_11654(var4) instanceof class_2771)) {
               var2.add(var4);
            }
         }

         var1 = var2.toArray(new class_2769[0]);
         PROPS_CACHE.put(var0, var1);
      }

      return var1;
   }

   public static boolean interactive(class_2680 var0) {
      class_2248 var1 = var0.method_26204();
      return var1 instanceof class_2237
         || var1 instanceof class_2323
         || var1 instanceof class_2533
         || var1 instanceof class_2349
         || var1 instanceof class_2269
         || var1 instanceof class_2401
         || var1 instanceof class_2312
         || var1 instanceof class_2428
         || var1 instanceof class_2244
         || var1 instanceof class_2272
         || var1 instanceof class_3709
         || var1 instanceof class_3962
         || var1 instanceof class_2304
         || var1 instanceof class_2309;
   }

   public static boolean clickable(class_2338 var0) {
      class_2680 var1 = mc.field_1687.method_8320(var0);
      return !var1.method_45474() && !var1.method_26218(mc.field_1687, var0).method_1110() && (sneakInteractive || !interactive(var1));
   }

   public static boolean hasNeighbor(class_2338 var0) {
      for (class_2350 var4 : new class_2350[]{
         class_2350.field_11033, class_2350.field_11036, class_2350.field_11043, class_2350.field_11035, class_2350.field_11039, class_2350.field_11034
      }) {
         if (clickable(var0.method_10093(var4))) {
            return true;
         }
      }

      return false;
   }

   public static boolean visible(class_243 var0, class_2338 var1, class_2350 var2) {
      return visible(var0, var1, var2, mc.field_1724.method_33571());
   }

   public static boolean visible(class_243 var0, class_2338 var1, class_2350 var2, class_243 var3) {
      class_746 var4 = mc.field_1724;
      class_243 var6 = var0.method_1031(-var2.method_10148() * 0.05, -var2.method_10164() * 0.05, -var2.method_10165() * 0.05);
      class_3965 var7 = mc.field_1687.method_17742(new class_3959(var3, var6, class_3960.field_17559, class_242.field_1348, var4));
      return var7 != null && var7.method_17783() == class_240.field_1332 && var7.method_17777().equals(var1) && var7.method_17780() == var2;
   }

   public static PlacementSolver.Click solve(class_2338 var0, class_2680 var1, class_1799 var2, double var3) {
      return solve(var0, var1, var2, var3, false);
   }

   public static PlacementSolver.Click solve(class_2338 var0, class_2680 var1, class_1799 var2, double var3, boolean var5) {
      return mc.field_1724 == null ? null : solveFrom(var0, var1, var2, var3, var5, mc.field_1724.method_33571());
   }

   public static PlacementSolver.Click solveFrom(class_2338 var0, class_2680 var1, class_1799 var2, double var3, boolean var5, class_243 var6) {
      class_746 var7 = mc.field_1724;
      if (var7 != null && mc.field_1687 != null && var2.method_7909() instanceof class_1747 var8) {
         class_243 var39 = var6;
         float var10 = var7.method_36454();
         float var11 = var7.method_36455();
         PlacementSolver.Click var12 = null;
         double var13 = Double.MAX_VALUE;
         PlacementSolver.Click var15 = null;
         double var16 = Double.MAX_VALUE;
         PlacementSolver.Click var18 = null;
         ArrayList var19 = new ArrayList();

         PlacementSolver.Click var42;
         try {
            for (class_2350 var21 : faceOrder(var1)) {
               class_2338 var22 = var0.method_10093(var21);
               if (clickable(var22)) {
                  var19.add(new PlacementSolver.Face(var22, var21.method_10153()));
               }
            }

            for (PlacementSolver.Face var45 : var19) {
               for (class_243 var23 : hitPoints(var45.pos(), var45.side())) {
                  if (!(var23.method_1025(var39) > var3 * var3)) {
                     float[] var24 = rotFrom(var39, var23);
                     var7.method_36456(var24[0]);
                     var7.method_36457(var24[1]);
                     class_3965 var25 = new class_3965(var23, var45.side(), var45.pos(), false);
                     class_2680 var26 = simulate(var8, var2, var25, var0);
                     if (var26 != null && placementMatches(var26, var1)) {
                        boolean var27 = !visibleOnly && !var5 || visible(var23, var45.pos(), var45.side(), var39);
                        if (!var5 || var27) {
                           boolean var28 = stable(var7, var8, var2, var25, var0, var1, var24[0], var24[1]);
                           double var29 = angle(var10, var11, var24[0], var24[1]) + (var27 ? 0.0 : 1000.0) + (var28 ? 0.0 : 300.0);
                           if (var29 < var13) {
                              var13 = var29;
                              var12 = new PlacementSolver.Click(var25, var24[0], var24[1], true, var27);
                           }
                        }
                     }
                  }
               }
            }

            if (var12 != null && var12.aimed() || var5) {
               return var12;
            }

            for (float var49 : yaws(var1)) {
               for (float var53 : PITCHES) {
                  var7.method_36456(var49);
                  var7.method_36457(var53);

                  for (PlacementSolver.Face var55 : var19) {
                     for (class_243 var31 : hitPoints(var55.pos(), var55.side())) {
                        if (!(var31.method_1025(var39) > var3 * var3)) {
                           class_3965 var32 = new class_3965(var31, var55.side(), var55.pos(), false);
                           class_2680 var33 = simulate(var8, var2, var32, var0);
                           if (var33 != null) {
                              if (placementMatches(var33, var1)) {
                                 double var34 = angle(var10, var11, var49, var53);
                                 if (var34 < var16) {
                                    var16 = var34;
                                    var15 = new PlacementSolver.Click(var32, var49, var53, true, false);
                                 }
                              } else if (var18 == null && var33.method_26204() == var1.method_26204()) {
                                 var18 = new PlacementSolver.Click(var32, var49, var53, false, false);
                              }
                           }
                        }
                     }
                  }
               }
            }

            if (var12 == null) {
               return var15 != null ? var15 : var18;
            }

            var42 = var12;
         } finally {
            var7.method_36456(var10);
            var7.method_36457(var11);
         }

         return var42;
      } else {
         return null;
      }
   }

   public static PlacementSolver.Click solveIncrement(class_2338 var0, class_2680 var1, class_2680 var2, class_1799 var3, double var4) {
      return solveIncrement(var0, var1, var2, var3, var4, false);
   }

   public static PlacementSolver.Click solveIncrement(class_2338 var0, class_2680 var1, class_2680 var2, class_1799 var3, double var4, boolean var6) {
      class_746 var7 = mc.field_1724;
      if (var7 != null && var3.method_7909() instanceof class_1747 var8) {
         class_243 var23 = var7.method_33571();
         float var10 = var7.method_36454();
         float var11 = var7.method_36455();
         List var12 = List.of(class_2350.field_11036, class_2350.field_11033);
         if (var1.method_28498(class_2741.field_12485) && var1.method_11654(class_2741.field_12485) == class_2771.field_12679) {
            var12 = List.of(class_2350.field_11033, class_2350.field_11036);
         }

         try {
            for (class_2350 var14 : var12) {
               class_243 var15 = class_243.method_24953(var0).method_1031(var14.method_10148() * 0.5, var14.method_10164() * 0.5, var14.method_10165() * 0.5);
               if (var1.method_28498(class_2741.field_12485)) {
                  var15 = new class_243(var15.field_1352, var0.method_10264() + 0.5, var15.field_1350);
               }

               if (!(var15.method_1025(var23) > var4 * var4) && (!var6 || visible(var15, var0, var14))) {
                  float[] var16 = RotationUtil.rotationsTo(var15);
                  var7.method_36456(var16[0]);
                  var7.method_36457(var16[1]);
                  class_3965 var17 = new class_3965(var15, var14, var0, false);
                  class_2680 var18 = simulate(var8, var3, var17, var0);
                  if (var18 != null && var18.method_26204() == var2.method_26204()) {
                     return new PlacementSolver.Click(var17, var16[0], var16[1], true, true);
                  }
               }
            }

            return null;
         } finally {
            var7.method_36456(var10);
            var7.method_36457(var11);
         }
      } else {
         return null;
      }
   }

   private static boolean stable(class_746 var0, class_1747 var1, class_1799 var2, class_3965 var3, class_2338 var4, class_2680 var5, float var6, float var7) {
      for (float var11 : new float[]{1.2F, -1.2F}) {
         var0.method_36456(var6 + var11);
         var0.method_36457(class_3532.method_15363(var7 + var11, -90.0F, 90.0F));
         class_2680 var12 = simulate(var1, var2, var3, var4);
         if (var12 == null || !placementMatches(var12, var5)) {
            return false;
         }
      }

      return true;
   }

   private static float[] rotFrom(class_243 var0, class_243 var1) {
      double var2 = var1.field_1352 - var0.field_1352;
      double var4 = var1.field_1351 - var0.field_1351;
      double var6 = var1.field_1350 - var0.field_1350;
      double var8 = Math.sqrt(var2 * var2 + var6 * var6);
      float var10 = (float)Math.toDegrees(Math.atan2(var6, var2)) - 90.0F;
      float var11 = (float)(-Math.toDegrees(Math.atan2(var4, var8)));
      return new float[]{class_3532.method_15393(var10), class_3532.method_15363(var11, -90.0F, 90.0F)};
   }

   private static double angle(float var0, float var1, float var2, float var3) {
      float var4 = class_3532.method_15393(var2 - var0);
      float var5 = var3 - var1;
      return Math.sqrt(var4 * var4 + var5 * var5);
   }

   private static class_2680 simulate(class_1747 var0, class_1799 var1, class_3965 var2, class_2338 var3) {
      try {
         class_1750 var4 = new class_1750(mc.field_1724, class_1268.field_5808, var1, var2);
         if (!var4.method_7716()) {
            return null;
         } else {
            class_1750 var5 = var0.method_16356(var4);
            if (var5 != null && var5.method_8037().equals(var3)) {
               BlockItemInvoker var6 = (BlockItemInvoker)var0;
               class_2680 var7 = var6.dih$getPlacementState(var5);
               return var7 != null && var6.dih$canPlace(var5, var7) ? var7 : null;
            } else {
               return null;
            }
         }
      } catch (Throwable var8) {
         return null;
      }
   }

   private static float[] yaws(class_2680 var0) {
      if (var0.method_28498(class_2741.field_12532)) {
         int var1 = (Integer)var0.method_11654(class_2741.field_12532);
         return new float[]{var1 * 22.5F - 180.0F};
      } else {
         return new float[]{0.0F, 90.0F, 180.0F, -90.0F};
      }
   }

   private static List<class_2350> faceOrder(class_2680 var0) {
      ArrayList var1 = new ArrayList<>(
         List.of(class_2350.field_11033, class_2350.field_11043, class_2350.field_11035, class_2350.field_11039, class_2350.field_11034, class_2350.field_11036)
      );
      boolean var2 = var0.method_28498(class_2741.field_12485) && var0.method_11654(class_2741.field_12485) == class_2771.field_12679
         || var0.method_28498(class_2741.field_12518) && var0.method_11654(class_2741.field_12518) == class_2760.field_12619;
      if (var2) {
         var1.remove(class_2350.field_11036);
         var1.add(0, class_2350.field_11036);
      }

      return var1;
   }

   private static List<class_243> hitPoints(class_2338 var0, class_2350 var1) {
      ArrayList var2 = new ArrayList(9);
      double var3 = var0.method_10263();
      double var5 = var0.method_10264();
      double var7 = var0.method_10260();

      for (double var12 : GRID) {
         for (double var17 : GRID) {
            if (var1.method_10166() == class_2351.field_11052) {
               var2.add(new class_243(var3 + var12, var5 + 0.5 + var1.method_10164() * 0.5, var7 + var17));
            } else if (var1.method_10166() == class_2351.field_11048) {
               var2.add(new class_243(var3 + 0.5 + var1.method_10148() * 0.5, var5 + var12, var7 + var17));
            } else {
               var2.add(new class_243(var3 + var17, var5 + var12, var7 + 0.5 + var1.method_10165() * 0.5));
            }
         }
      }

      return var2;
   }

   public record Click(class_3965 hit, float yaw, float pitch, boolean exact, boolean aimed) {
      public Click(class_3965 var1, float var2, float var3, boolean var4) {
         this(var1, var2, var3, var4, false);
      }
   }

   private record Face(class_2338 pos, class_2350 side) {
   }
}
