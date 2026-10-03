package dev.dihclient.autobuild;

import dev.dihclient.util.ItemUtil;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.class_1792;
import net.minecraft.class_1802;
import net.minecraft.class_2246;
import net.minecraft.class_2248;
import net.minecraft.class_2338;
import net.minecraft.class_238;
import net.minecraft.class_2415;
import net.minecraft.class_2470;
import net.minecraft.class_2680;
import net.minecraft.class_2741;
import net.minecraft.class_2742;
import net.minecraft.class_2756;
import net.minecraft.class_2769;
import net.minecraft.class_2771;
import net.minecraft.class_2960;
import net.minecraft.class_7923;

public final class BuildPlan {
   public static boolean includeFluids = false;
   public final Schematic schematic;
   public final class_2338 anchor;
   public final class_2470 rotation;
   public final class_2415 mirror;
   public final int sizeX;
   public final int sizeY;
   public final int sizeZ;
   public final List<BuildPlan.Planned> blocks;
   private final Map<Long, BuildPlan.Planned> byPos;

   public BuildPlan(Schematic var1, class_2338 var2, class_2470 var3, class_2415 var4) {
      this.schematic = var1;
      this.anchor = var2;
      this.rotation = var3;
      this.mirror = var4;
      boolean var5 = var3 == class_2470.field_11463 || var3 == class_2470.field_11465;
      this.sizeX = var5 ? var1.sizeZ : var1.sizeX;
      this.sizeY = var1.sizeY;
      this.sizeZ = var5 ? var1.sizeX : var1.sizeZ;
      ArrayList var6 = new ArrayList();
      HashMap var7 = new HashMap();
      HashMap var8 = new HashMap();

      for (Schematic.Entry var10 : var1.blocks) {
         String var11 = var10.id + "[" + var10.properties + "]";
         class_2680 var12 = var8.computeIfAbsent(var11, var1x -> parse(var10.id, var10.properties)).orElse(null);
         if (var12 != null
            && !var12.method_26215()
            && (var12.method_26204().method_8389() != class_1802.field_8162 || includeFluids && isFluid(var12) && var12.method_26227().method_15771())
            && !isSecondHalf(var12)) {
            var12 = var12.method_26185(var4).method_26186(var3);
            int[] var13 = this.transform(var10.x, var10.z);
            class_2338 var14 = var2.method_10069(var13[0], var10.y, var13[1]);
            BuildPlan.Planned var15 = new BuildPlan.Planned(var14, var12, var10.y);
            var6.add(var15);
            var7.put(var14.method_10063(), var15);
         }
      }

      var6.sort((var0, var1x) -> Integer.compare(var0.layer(), var1x.layer()));
      this.blocks = Collections.unmodifiableList(var6);
      this.byPos = var7;
   }

   public int[] transform(int var1, int var2) {
      int var3 = this.schematic.sizeX;
      int var4 = this.schematic.sizeZ;
      if (this.mirror == class_2415.field_11300) {
         var2 = var4 - 1 - var2;
      } else if (this.mirror == class_2415.field_11301) {
         var1 = var3 - 1 - var1;
      }
      return switch (this.rotation) {
         case field_11463 -> new int[]{var4 - 1 - var2, var1};
         case field_11464 -> new int[]{var3 - 1 - var1, var4 - 1 - var2};
         case field_11465 -> new int[]{var2, var3 - 1 - var1};
         default -> new int[]{var1, var2};
      };
   }

   public BuildPlan.Planned at(class_2338 var1) {
      return this.byPos.get(var1.method_10063());
   }

   public boolean contains(class_2338 var1) {
      return var1.method_10263() >= this.anchor.method_10263()
         && var1.method_10263() < this.anchor.method_10263() + this.sizeX
         && var1.method_10264() >= this.anchor.method_10264()
         && var1.method_10264() < this.anchor.method_10264() + this.sizeY
         && var1.method_10260() >= this.anchor.method_10260()
         && var1.method_10260() < this.anchor.method_10260() + this.sizeZ;
   }

   public class_238 bounds() {
      return new class_238(
         this.anchor.method_10263(),
         this.anchor.method_10264(),
         this.anchor.method_10260(),
         this.anchor.method_10263() + this.sizeX,
         this.anchor.method_10264() + this.sizeY,
         this.anchor.method_10260() + this.sizeZ
      );
   }

   public BuildPlan moved(class_2338 var1) {
      return new BuildPlan(this.schematic, var1, this.rotation, this.mirror);
   }

   public BuildPlan rotated() {
      return new BuildPlan(this.schematic, this.anchor, this.rotation.method_10501(class_2470.field_11463), this.mirror);
   }

   public BuildPlan mirrored() {
      class_2415 var1 = switch (this.mirror) {
         case field_11302 -> class_2415.field_11300;
         case field_11300 -> class_2415.field_11301;
         case field_11301 -> class_2415.field_11302;
         default -> throw new MatchException(null, null);
      };
      return new BuildPlan(this.schematic, this.anchor, this.rotation, var1);
   }

   public Map<class_1792, Integer> materials() {
      LinkedHashMap var1 = new LinkedHashMap();

      for (BuildPlan.Planned var3 : this.blocks) {
         var1.merge(itemOf(var3.state()), pieces(var3.state()), Integer::sum);
      }

      return var1;
   }

   public static boolean isFluid(class_2680 var0) {
      class_2248 var1 = var0.method_26204();
      return var1 == class_2246.field_10382 || var1 == class_2246.field_10164;
   }

   public static String bucketId(class_2680 var0) {
      class_2248 var1 = var0.method_26204();
      if (var1 == class_2246.field_10382) {
         return "minecraft:water_bucket";
      } else {
         return var1 == class_2246.field_10164 ? "minecraft:lava_bucket" : null;
      }
   }

   public static class_1792 itemOf(class_2680 var0) {
      String var1 = bucketId(var0);
      return var1 != null ? ItemUtil.item(var1) : var0.method_26204().method_8389();
   }

   public static int pieces(class_2680 var0) {
      if (var0.method_28498(class_2741.field_12485) && var0.method_11654(class_2741.field_12485) == class_2771.field_12682) {
         return 2;
      } else if (var0.method_28498(class_2741.field_27220)) {
         return (Integer)var0.method_11654(class_2741.field_27220);
      } else if (var0.method_28498(class_2741.field_12543)) {
         return (Integer)var0.method_11654(class_2741.field_12543);
      } else if (var0.method_28498(class_2741.field_12509)) {
         return (Integer)var0.method_11654(class_2741.field_12509);
      } else {
         return var0.method_28498(class_2741.field_12536) && var0.method_27852(class_2246.field_10477) ? (Integer)var0.method_11654(class_2741.field_12536) : 1;
      }
   }

   public static Optional<class_2680> parse(String var0, String var1) {
      class_2960 var2 = class_2960.method_12829(var0);
      if (var2 == null) {
         return Optional.empty();
      } else {
         Optional var3 = class_7923.field_41175.method_17966(var2);
         if (var3.isEmpty()) {
            return Optional.empty();
         } else {
            class_2680 var4 = ((class_2248)var3.get()).method_9564();
            if (var1 != null && !var1.isBlank()) {
               for (String var8 : var1.split(",")) {
                  int var9 = var8.indexOf(61);
                  if (var9 > 0) {
                     class_2769 var10 = ((class_2248)var3.get()).method_9595().method_11663(var8.substring(0, var9).trim());
                     if (var10 != null) {
                        var4 = with(var4, var10, var8.substring(var9 + 1).trim());
                     }
                  }
               }
            }

            return Optional.of(var4);
         }
      }
   }

   private static <T extends Comparable<T>> class_2680 with(class_2680 var0, class_2769<T> var1, String var2) {
      Optional var3 = var1.method_11900(var2);
      return var3.isEmpty() ? var0 : (class_2680)var0.method_11657(var1, (Comparable)var3.get());
   }

   private static boolean isSecondHalf(class_2680 var0) {
      return var0.method_28498(class_2741.field_12533) && var0.method_11654(class_2741.field_12533) == class_2756.field_12609
         ? true
         : var0.method_28498(class_2741.field_12483) && var0.method_11654(class_2741.field_12483) == class_2742.field_12560;
   }

   public record Planned(class_2338 pos, class_2680 state, int layer) {
   }
}
