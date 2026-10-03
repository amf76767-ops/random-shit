package dev.dihclient.path;

import dev.dihclient.modules.world.SafeRoute;
import dev.dihclient.util.ItemUtil;
import dev.dihclient.util.RegistryUtil;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
import net.minecraft.class_2248;
import net.minecraft.class_2338;
import net.minecraft.class_2350;
import net.minecraft.class_2680;
import net.minecraft.class_310;

public final class Pathfinder {
   private static final class_310 mc = class_310.method_1551();
   private static final class_2350[] HORIZONTAL = new class_2350[]{
      class_2350.field_11043, class_2350.field_11035, class_2350.field_11039, class_2350.field_11034
   };
   private static final class_2350[] ALL = new class_2350[]{
      class_2350.field_11033, class_2350.field_11036, class_2350.field_11043, class_2350.field_11035, class_2350.field_11039, class_2350.field_11034
   };
   private static final double SQRT2 = Math.sqrt(2.0);
   private static boolean collisionFallback;
   private final class_2338 goal;
   private final boolean goalHasY;
   private final Pathfinder.Options opt;
   private final Map<Long, Integer> flags = new HashMap<>();
   private final Map<Long, Double> best = new HashMap<>();
   private final Map<Long, Boolean> lavaNear = new HashMap<>();
   private SafeRoute.Search safety;
   private final Map<Long, Double> safeCost = new HashMap<>();
   private final PriorityQueue<Pathfinder.Node> open = new PriorityQueue<>((var0, var1x) -> Double.compare(var0.f, var1x.f));
   private static final int PASSABLE = 1;
   private static final int FLOOR = 2;
   private static final int WATER = 4;
   private static final int DANGER = 8;
   private static final int LAVA = 16;
   private static final int UNLOADED = 32;
   private static final int REPLACEABLE = 64;
   public static final int DOOR = 128;
   private static final IdentityHashMap<class_2680, Integer> STATE_FLAGS = new IdentityHashMap<>();
   private static final IdentityHashMap<class_2680, Integer> NAME_BITS = new IdentityHashMap<>();
   private static final int NAME_NO_DIG = 1;
   private static final int NAME_FALLING = 2;
   private static boolean cachedFallback;

   private Pathfinder(class_2338 var1, boolean var2, Pathfinder.Options var3) {
      this.goal = var1;
      this.goalHasY = var2;
      this.opt = var3;
   }

   public static Pathfinder.Result find(class_2338 var0, class_2338 var1, boolean var2, Pathfinder.Options var3, int var4, long var5) {
      Pathfinder var7 = new Pathfinder(var1, var2, var3);
      var7.safety = SafeRoute.search(var0);
      return var7.run(var0, var4, var5);
   }

   public boolean reached(class_2338 var1) {
      return var1.method_10263() == this.goal.method_10263()
         && var1.method_10260() == this.goal.method_10260()
         && (!this.goalHasY || Math.abs(var1.method_10264() - this.goal.method_10264()) <= 1);
   }

   public static boolean reached(class_2338 var0, class_2338 var1, boolean var2) {
      return var0.method_10263() == var1.method_10263()
         && var0.method_10260() == var1.method_10260()
         && (!var2 || Math.abs(var0.method_10264() - var1.method_10264()) <= 1);
   }

   private double heuristic(class_2338 var1) {
      double var2 = Math.abs(var1.method_10263() - this.goal.method_10263());
      double var4 = Math.abs(var1.method_10260() - this.goal.method_10260());
      double var6 = Math.max(var2, var4) + (SQRT2 - 1.0) * Math.min(var2, var4);
      return this.goalHasY ? var6 + Math.abs(var1.method_10264() - this.goal.method_10264()) * 1.2 : var6;
   }

   private Pathfinder.Result run(class_2338 var1, int var2, long var3) {
      long var5 = System.nanoTime() + var3 * 1000000L;
      Pathfinder.Node var7 = new Pathfinder.Node(var1, 0.0, this.heuristic(var1), null, Pathfinder.Move.WALK, List.of(), null, 0);
      this.open.add(var7);
      this.best.put(var1.method_10063(), 0.0);
      Pathfinder.Node var8 = var7;
      double var9 = this.heuristic(var1);
      int var11 = 0;

      while (!this.open.isEmpty()) {
         Pathfinder.Node var12 = this.open.poll();
         Double var13 = this.best.get(var12.pos.method_10063());
         if (var13 == null || !(var12.g > var13 + 1.0E-9)) {
            if (this.reached(var12.pos)) {
               return new Pathfinder.Result(this.build(var12), true, var11);
            }

            double var14 = this.heuristic(var12.pos);
            if (var14 < var9) {
               var9 = var14;
               var8 = var12;
            }

            var11++;
            if (var11 >= var2 || (var11 & 0xFF) == 0 && System.nanoTime() > var5) {
               break;
            }

            this.expand(var12);
         }
      }

      return new Pathfinder.Result(var8 == var7 ? List.of() : this.build(var8), false, var11);
   }

   private List<Pathfinder.Step> build(Pathfinder.Node var1) {
      ArrayList var2 = new ArrayList();

      for (Pathfinder.Node var3 = var1; var3.parent != null; var3 = var3.parent) {
         var2.add(new Pathfinder.Step(var3.pos, var3.move, var3.breaks, var3.place));
      }

      Collections.reverse(var2);
      return var2;
   }

   private void push(Pathfinder.Node var1, class_2338 var2, double var3, Pathfinder.Move var5, List<class_2338> var6, class_2338 var7) {
      double var8 = var1.g + var3 + (this.nearLava(var2) ? 8.0 : 0.0) + this.safetyCost(var2, var5);
      long var10 = var2.method_10063();
      Double var12 = this.best.get(var10);
      if (var12 == null || var8 < var12 - 1.0E-9) {
         this.best.put(var10, var8);
         int var13 = var1.placesUsed + (var7 != null ? 1 : 0);
         this.open.add(new Pathfinder.Node(var2, var8, var8 + this.heuristic(var2) * 1.3, var1, var5, var6, var7, var13));
      }
   }

   private void expand(Pathfinder.Node var1) {
      class_2338 var2 = var1.pos;
      boolean var3 = this.is(var2, 4);

      for (class_2350 var7 : HORIZONTAL) {
         class_2338 var8 = var2.method_10093(var7);
         List var9 = this.occupy(var8);
         if (var9 != null) {
            double var10 = this.breakCost(var9);
            if (this.is(var8.method_10074(), 2)) {
               this.push(var1, var8, (var3 ? 2.0 : 1.0) + var10, Pathfinder.Move.WALK, var9, null);
            } else if (this.is(var8, 4)) {
               this.push(var1, var8, 2.5 + var10, Pathfinder.Move.SWIM, var9, null);
            } else {
               class_2338 var12 = this.landing(var8);
               if (var12 != null) {
                  int var13 = var8.method_10264() - var12.method_10264();
                  this.push(var1, var12, 1.0 + var13 * 0.6 + var10, Pathfinder.Move.DESCEND, var9, null);
               }

               if (this.opt.allowPlace
                  && var9.isEmpty()
                  && var1.placesUsed < this.opt.placeBlocks
                  && this.is(var8.method_10074(), 64)
                  && !this.is(var8.method_10074(), 16)) {
                  this.push(var1, var8, 4.0, Pathfinder.Move.WALK, var9, var8.method_10074());
               }
            }
         }

         if (this.is(var8, 2)) {
            Object var22 = this.occupy(var8.method_10084());
            if (var22 != null) {
               class_2338 var11 = var2.method_10086(2);
               if (!this.is(var11, 1)) {
                  if (!this.breakable(var11)) {
                     var22 = null;
                  } else {
                     var22 = new ArrayList((Collection)var22);
                     var22.add(0, var11);
                  }
               }

               if (var22 != null) {
                  this.push(var1, var8.method_10084(), 2.0 + this.breakCost((List<class_2338>)var22), Pathfinder.Move.ASCEND, (List<class_2338>)var22, null);
               }
            }
         }
      }

      if (var3 && this.is(var2.method_10084(), 4)) {
         List var14 = this.occupy(var2.method_10084());
         if (var14 != null && var14.isEmpty()) {
            this.push(var1, var2.method_10084(), 1.5, Pathfinder.Move.SWIM, var14, null);
         }
      }

      for (int var15 = 0; var15 < 4; var15++) {
         class_2350 var17 = HORIZONTAL[var15 < 2 ? 0 : 1];
         class_2350 var18 = HORIZONTAL[var15 % 2 == 0 ? 2 : 3];
         class_2338 var19 = var2.method_10093(var17);
         class_2338 var20 = var2.method_10093(var18);
         class_2338 var21 = var19.method_10093(var18);
         if (this.open(var19)
            && this.open(var20)
            && this.open(var21)
            && this.is(var21.method_10074(), 2)
            && !var3
            && !this.nearLava(var19)
            && !this.nearLava(var20)) {
            this.push(var1, var21, SQRT2, Pathfinder.Move.DIAGONAL, List.of(), null);
         }
      }

      if (this.opt.allowBreak && !var3) {
         class_2338 var16 = var2.method_10074();
         if (this.breakable(var16) && this.is(var16.method_10074(), 2) && !this.is(var16.method_10074(), 16)) {
            this.push(var1, var16, 2.0 + this.breakCost(List.of(var16)), Pathfinder.Move.DIG_DOWN, List.of(var16), null);
         }
      }
   }

   private double safetyCost(class_2338 var1, Pathfinder.Move var2) {
      if (this.safety == null) {
         return 0.0;
      } else {
         long var3 = var1.method_10063();
         Double var5 = this.safeCost.get(var3);
         if (var5 == null) {
            double var6 = this.safety.mobCost(var1);
            if (this.safety.dark && !this.is(var1, 4) && this.safety.isDark(var1)) {
               var6 += 2.5;
            }

            if (this.safety.edges && this.edge(var1)) {
               var6 += 3.0;
            }

            if (this.safety.lavaMargin && this.lavaWithin2(var1)) {
               var6 += 6.0;
            }

            var5 = var6;
            this.safeCost.put(var3, var5);
         }

         return var5 + (this.safety.water && var2 == Pathfinder.Move.SWIM ? 3.0 : 0.0);
      }
   }

   private boolean edge(class_2338 var1) {
      for (class_2350 var5 : HORIZONTAL) {
         class_2338 var6 = var1.method_10093(var5);
         if (this.is(var6, 1) && !this.is(var6, 4) && !this.is(var6.method_10074(), 2)) {
            int var7 = 0;

            class_2338 var8;
            for (var8 = var6.method_10074();
               var7 <= this.safety.safeFall && this.is(var8, 1) && !this.is(var8, 4) && !this.is(var8, 32);
               var8 = var8.method_10074()
            ) {
               var7++;
            }

            if (var7 > this.safety.safeFall || this.is(var8, 16)) {
               return true;
            }
         }
      }

      return false;
   }

   private boolean lavaWithin2(class_2338 var1) {
      for (int var2 = -2; var2 <= 2; var2++) {
         for (int var3 = -2; var3 <= 2; var3++) {
            if (Math.abs(var2) == 2 || Math.abs(var3) == 2) {
               for (int var4 = -1; var4 <= 1; var4++) {
                  if (this.is(var1.method_10069(var2, var4, var3), 16)) {
                     return true;
                  }
               }
            }
         }
      }

      return false;
   }

   private boolean nearLava(class_2338 var1) {
      long var2 = var1.method_10063();
      Boolean var4 = this.lavaNear.get(var2);
      if (var4 == null) {
         var4 = false;

         for (class_2350 var8 : HORIZONTAL) {
            class_2338 var9 = var1.method_10093(var8);
            if (this.is(var9, 16) || this.is(var9.method_10074(), 16) || this.is(var9, 8) && !this.is(var9, 2)) {
               var4 = true;
               break;
            }
         }

         this.lavaNear.put(var2, var4);
      }

      return var4;
   }

   private boolean open(class_2338 var1) {
      return this.is(var1, 1) && this.is(var1.method_10084(), 1) && !this.is(var1, 136) && !this.is(var1.method_10084(), 136);
   }

   private List<class_2338> occupy(class_2338 var1) {
      class_2338 var2 = var1.method_10084();
      if (!this.is(var1, 32) && !this.is(var1, 8) && !this.is(var2, 8)) {
         ArrayList var3 = null;

         for (class_2338 var7 : new class_2338[]{var2, var1}) {
            if (!this.is(var7, 1)) {
               if (!this.breakable(var7)) {
                  return null;
               }

               if (var3 == null) {
                  var3 = new ArrayList(2);
               }

               var3.add(var7);
            }
         }

         return (List<class_2338>)(var3 == null ? List.of() : var3);
      } else {
         return null;
      }
   }

   private class_2338 landing(class_2338 var1) {
      class_2338 var2 = var1;

      for (int var3 = 1; var3 <= 24; var3++) {
         var2 = var2.method_10074();
         if (this.is(var2, 32) || this.is(var2, 16) || this.is(var2, 8)) {
            return null;
         }

         if (this.is(var2, 4)) {
            return var2;
         }

         if (!this.is(var2, 1)) {
            class_2338 var4 = var2.method_10084();
            return var3 - 1 >= 1 && var3 - 1 <= this.opt.maxFall && this.is(var2, 2) ? var4 : null;
         }
      }

      return null;
   }

   private boolean breakable(class_2338 var1) {
      if (this.opt.allowBreak && !this.is(var1, 32) && !this.is(var1, 4) && !this.is(var1, 16) && !this.opt.avoid.contains(var1.method_10063())) {
         class_2680 var2 = mc.field_1687.method_8320(var1);
         if (!(var2.method_26214(mc.field_1687, var1) < 0.0F) && !this.opt.protect.contains(var2.method_26204())) {
            if (!var2.method_31709() && (nameBits(var2) & 1) == 0) {
               if (this.breakTicks(var1) > 200.0) {
                  return false;
               } else {
                  for (class_2350 var6 : ALL) {
                     if (this.is(var1.method_10093(var6), 16)) {
                        return false;
                     }
                  }

                  return (nameBits(mc.field_1687.method_8320(var1.method_10084())) & 2) == 0;
               }
            } else {
               return false;
            }
         } else {
            return false;
         }
      } else {
         return false;
      }
   }

   private double breakTicks(class_2338 var1) {
      float var2 = mc.field_1687.method_8320(var1).method_26165(mc.field_1724, mc.field_1687, var1);
      return var2 <= 0.0F ? 1.0E9 : Math.ceil(1.0F / var2);
   }

   private double breakCost(List<class_2338> var1) {
      double var2 = 0.0;

      for (class_2338 var5 : var1) {
         var2 += 2.0 + this.breakTicks(var5) / 4.0;
      }

      return var2;
   }

   private boolean is(class_2338 var1, int var2) {
      long var3 = var1.method_10063();
      Integer var5 = this.flags.get(var3);
      if (var5 == null) {
         var5 = classify(var1);
         if ((var5 & 128) != 0 && this.opt.avoid.contains(var3)) {
            var5 = 0;
         }

         this.flags.put(var3, var5);
      }

      return (var5 & var2) != 0;
   }

   private static int nameBits(class_2680 var0) {
      Integer var1 = NAME_BITS.get(var0);
      if (var1 == null) {
         String var2 = RegistryUtil.blockId(var0);
         byte var3 = 0;
         if (var2.contains("door") || var2.contains("fence_gate") || var2.endsWith("_bed")) {
            var3 |= 1;
         }

         if (falling(var2)) {
            var3 |= 2;
         }

         var1 = Integer.valueOf(var3);
         NAME_BITS.put(var0, var1);
      }

      return var1;
   }

   public static int classify(class_2338 var0) {
      if (!mc.field_1687.method_2935().method_12123(var0.method_10263() >> 4, var0.method_10260() >> 4)) {
         return 32;
      } else {
         class_2680 var1 = mc.field_1687.method_8320(var0);
         if (var1.method_26215()) {
            return 65;
         } else if (var1.method_31709()) {
            return classifyState(var1, var0);
         } else {
            if (cachedFallback != collisionFallback) {
               cachedFallback = collisionFallback;
               STATE_FLAGS.clear();
            }

            Integer var2 = STATE_FLAGS.get(var1);
            if (var2 == null) {
               var2 = classifyState(var1, var0);
               STATE_FLAGS.put(var1, var2);
            }

            return var2;
         }
      }
   }

   private static int classifyState(class_2680 var0, class_2338 var1) {
      String var2 = RegistryUtil.blockId(var0);
      if (var2.equals("minecraft:lava")) {
         return 88;
      } else {
         byte var3 = 0;
         if (danger(var2)) {
            var3 |= 8;
         }

         boolean var4 = noCollision(var0, var1, var2);
         boolean var5 = !var0.method_26227().method_15769();
         String var6 = var2.substring(var2.indexOf(58) + 1);
         boolean var7 = var6.endsWith("_door") && !var6.equals("iron_door") || var6.endsWith("_fence_gate");
         if (var7) {
            return 1 | ("true".equals(ItemUtil.prop(var0, "open")) ? 0 : 128);
         } else if (var4
            || !var6.endsWith("_fence") && !var6.endsWith("_wall") && !var6.equals("iron_door") && !var6.contains("iron_bars") && !var6.endsWith("_pane")) {
            if (var4) {
               var3 |= 1;
               if (var5) {
                  var3 |= 4;
               }
            } else if ((var3 & 8) == 0) {
               var3 |= 2;
            }

            if (var0.method_45474()) {
               var3 |= 64;
            }

            return var3;
         } else {
            return var3;
         }
      }
   }

   public static boolean noCollision(class_2680 var0, class_2338 var1, String var2) {
      if (!collisionFallback) {
         try {
            return var0.method_26220(mc.field_1687, var1).method_1110();
         } catch (LinkageError var4) {
            collisionFallback = true;
         }
      }

      if (!var0.method_45474() && !var0.method_26218(mc.field_1687, var1).method_1110()) {
         String var3 = var2.substring(var2.indexOf(58) + 1);
         return var3.contains("torch")
            || var3.endsWith("_sign")
            || var3.endsWith("_button")
            || var3.endsWith("_pressure_plate")
            || var3.contains("rail")
            || var3.equals("redstone_wire")
            || var3.endsWith("_sapling")
            || var3.endsWith("_tulip")
            || var3.equals("poppy")
            || var3.equals("dandelion")
            || var3.equals("blue_orchid")
            || var3.equals("allium")
            || var3.equals("azure_bluet")
            || var3.equals("oxeye_daisy")
            || var3.equals("cornflower")
            || var3.equals("lily_of_the_valley")
            || var3.equals("sugar_cane")
            || var3.equals("wheat")
            || var3.equals("carrots")
            || var3.equals("potatoes")
            || var3.equals("beetroots")
            || var3.equals("nether_wart")
            || var3.contains("vine")
            || var3.equals("tripwire")
            || var3.equals("lever")
            || var3.endsWith("_banner")
            || var3.contains("kelp")
            || var3.contains("seagrass")
            || var3.endsWith("_mushroom")
            || var3.equals("water");
      } else {
         return true;
      }
   }

   private static boolean danger(String var0) {
      return switch (var0) {
         case "minecraft:fire", "minecraft:soul_fire", "minecraft:magma_block", "minecraft:cactus", "minecraft:sweet_berry_bush", "minecraft:powder_snow", "minecraft:campfire", "minecraft:soul_campfire", "minecraft:wither_rose", "minecraft:cobweb", "minecraft:pointed_dripstone" -> true;
         default -> false;
      };
   }

   public static boolean falling(String var0) {
      return var0.equals("minecraft:sand")
         || var0.equals("minecraft:red_sand")
         || var0.equals("minecraft:gravel")
         || var0.equals("minecraft:suspicious_sand")
         || var0.equals("minecraft:suspicious_gravel")
         || var0.endsWith("_concrete_powder")
         || var0.endsWith("anvil");
   }

   public static boolean isFloor(class_2338 var0) {
      return (classify(var0) & 2) != 0;
   }

   public static boolean isPassable(class_2338 var0) {
      return (classify(var0) & 1) != 0;
   }

   public static enum Move {
      WALK,
      DIAGONAL,
      ASCEND,
      DESCEND,
      SWIM,
      DIG_DOWN;
   }

   private static final class Node {
      final class_2338 pos;
      final double g;
      final double f;
      final Pathfinder.Node parent;
      final Pathfinder.Move move;
      final List<class_2338> breaks;
      final class_2338 place;
      final int placesUsed;

      Node(class_2338 var1, double var2, double var4, Pathfinder.Node var6, Pathfinder.Move var7, List<class_2338> var8, class_2338 var9, int var10) {
         this.pos = var1;
         this.g = var2;
         this.f = var4;
         this.parent = var6;
         this.move = var7;
         this.breaks = var8;
         this.place = var9;
         this.placesUsed = var10;
      }
   }

   public static final class Options {
      public boolean allowBreak;
      public boolean allowPlace;
      public int placeBlocks;
      public int maxFall = 3;
      public Set<class_2248> protect = Set.of();
      public Set<Long> avoid = Set.of();
   }

   public record Result(List<Pathfinder.Step> steps, boolean complete, int explored) {
   }

   public record Step(class_2338 pos, Pathfinder.Move move, List<class_2338> breaks, class_2338 place) {
   }
}
