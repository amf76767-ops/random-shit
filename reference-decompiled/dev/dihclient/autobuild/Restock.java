package dev.dihclient.autobuild;

import dev.dihclient.util.HumanAim;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import net.minecraft.class_1268;
import net.minecraft.class_1661;
import net.minecraft.class_1703;
import net.minecraft.class_1713;
import net.minecraft.class_1735;
import net.minecraft.class_1792;
import net.minecraft.class_1799;
import net.minecraft.class_1923;
import net.minecraft.class_2281;
import net.minecraft.class_2338;
import net.minecraft.class_2350;
import net.minecraft.class_2382;
import net.minecraft.class_243;
import net.minecraft.class_2480;
import net.minecraft.class_2586;
import net.minecraft.class_2680;
import net.minecraft.class_2818;
import net.minecraft.class_310;
import net.minecraft.class_3708;
import net.minecraft.class_3965;
import net.minecraft.class_465;

public final class Restock {
   private static final class_310 mc = class_310.method_1551();
   private Restock.Stage stage = Restock.Stage.IDLE;
   private class_2338 container;
   private int ticks;
   public boolean human = true;
   public float speed = 32.0F;
   private boolean aimed;
   private int aimTicks;
   private static final Map<class_2338, Set<class_1792>> known = new HashMap<>();
   private static final Map<class_2338, Long> visited = new HashMap<>();
   private static final Map<class_2338, Long> failed = new HashMap<>();
   public int maxStacks = 6;
   public int limit = Integer.MAX_VALUE;
   private int taken;
   private Set<class_1792> wanted = Set.of();

   public boolean isActive() {
      return this.stage != Restock.Stage.IDLE;
   }

   public void reset() {
      if (this.stage == Restock.Stage.TAKE && mc.field_1755 instanceof class_465 && mc.field_1724 != null) {
         mc.field_1724.method_7346();
      }

      this.stage = Restock.Stage.IDLE;
      this.container = null;
   }

   public void forget() {
      this.reset();
      visited.clear();
   }

   public static void forgetAll() {
      known.clear();
      visited.clear();
      failed.clear();
   }

   private static boolean failedRecently(class_2338 var0) {
      Long var1 = failed.get(var0);
      return var1 != null && System.currentTimeMillis() - var1 < 60000L;
   }

   public static boolean knowsNearby(class_1792 var0, double var1) {
      if (mc.field_1724 == null) {
         return false;
      } else {
         double var3 = var1 * var1;

         for (Entry var6 : known.entrySet()) {
            if (((Set)var6.getValue()).contains(var0)
               && class_243.method_24953((class_2382)var6.getKey()).method_1025(mc.field_1724.method_73189()) <= var3
               && isContainer((class_2338)var6.getKey())
               && !failedRecently((class_2338)var6.getKey())) {
               return true;
            }
         }

         return false;
      }
   }

   public static class_2338 whereIs(class_1792 var0) {
      if (mc.field_1724 == null) {
         return null;
      } else {
         class_2338 var1 = null;
         double var2 = Double.MAX_VALUE;

         for (Entry var5 : known.entrySet()) {
            if (((Set)var5.getValue()).contains(var0)) {
               double var6 = class_243.method_24953((class_2382)var5.getKey()).method_1025(mc.field_1724.method_73189());
               if (var6 < var2) {
                  var1 = (class_2338)var5.getKey();
                  var2 = var6;
               }
            }
         }

         return var1;
      }
   }

   public static Map<class_2338, Set<class_1792>> snapshot() {
      return new HashMap<>(known);
   }

   public static int knownCount() {
      return known.size();
   }

   public class_2338 target() {
      return this.stage == Restock.Stage.WALK ? this.container : null;
   }

   public boolean begin(class_1792 var1, Set<class_1792> var2, double var3) {
      if (this.isActive()) {
         return true;
      } else {
         class_2338 var5 = null;
         double var6 = Double.MAX_VALUE;
         long var8 = System.currentTimeMillis();
         class_243 var10 = mc.field_1724.method_73189();

         for (Entry var12 : known.entrySet()) {
            if (((Set)var12.getValue()).contains(var1)) {
               double var13 = class_243.method_24953((class_2382)var12.getKey()).method_1025(var10);
               if (var13 < var6 && isContainer((class_2338)var12.getKey()) && !failedRecently((class_2338)var12.getKey())) {
                  var5 = (class_2338)var12.getKey();
                  var6 = var13;
               }
            }
         }

         if (var5 == null) {
            for (class_2338 var17 : nearbyContainers(var3)) {
               Long var18 = visited.get(var17);
               if ((var18 == null || var8 - var18 >= 120000L) && !failedRecently(var17)) {
                  double var14 = class_243.method_24953(var17).method_1025(var10);
                  if (var14 < var6) {
                     var5 = var17;
                     var6 = var14;
                  }
               }
            }
         }

         if (var5 == null) {
            return false;
         } else {
            this.container = var5;
            this.wanted = var2;
            this.taken = 0;
            this.stage = Restock.Stage.WALK;
            this.ticks = 0;
            return true;
         }
      }
   }

   public String tick(double var1) {
      this.ticks++;
      switch (this.stage) {
         case WALK:
            if (!isContainer(this.container)) {
               return this.finish("Restock: container gone", true);
            } else {
               if (class_243.method_24953(this.container).method_1022(mc.field_1724.method_33571()) <= var1 - 0.3) {
                  this.stage = Restock.Stage.OPEN;
                  this.ticks = 0;
                  this.aimed = false;
                  this.aimTicks = 0;
               } else if (this.ticks > 400) {
                  return this.finish("Restock: can't reach container", true);
               }

               return "Restock: walking to container";
            }
         case OPEN:
            if (this.ticks == 1 && this.human && ++this.aimTicks < 40) {
               boolean var12 = HumanAim.stepTo(class_243.method_24953(this.container).method_1031(0.0, 0.49, 0.0), this.speed, 2.0F);
               if (!var12 || !this.aimed) {
                  this.aimed = var12;
                  this.ticks = 0;
                  return "Restock: looking at container";
               }
            }

            if (this.ticks == 1) {
               class_243 var13 = class_243.method_24953(this.container).method_1031(0.0, 0.5, 0.0);
               mc.field_1761.method_2896(mc.field_1724, class_1268.field_5808, new class_3965(var13, class_2350.field_11036, this.container, false));
               mc.field_1724.method_6104(class_1268.field_5808);
            }

            if (mc.field_1755 instanceof class_465 && this.ticks > 3) {
               this.stage = Restock.Stage.TAKE;
               this.ticks = 0;
            }

            return this.ticks > 40 ? this.finish("Restock: container did not open", true) : "Restock: opening container";
         case TAKE:
            if (!(mc.field_1755 instanceof class_465 var3)) {
               return this.finish("Restock: closed");
            }

            class_1703 var11 = var3.method_17577();
            HashSet var5 = new HashSet();
            int var6 = 0;

            for (Object var8 : var11.field_7761) {
               class_1735 var9 = (class_1735)var8;
               if (!(var9.field_7871 instanceof class_1661)) {
                  class_1799 var10 = var9.method_7677();
                  if (!var10.method_7960()) {
                     var5.add(var10.method_7909());
                     if (this.wanted.contains(var10.method_7909())
                        && var6 < this.maxStacks
                        && this.taken < this.limit
                        && mc.field_1724.method_31548().method_7376() >= 0) {
                        mc.field_1761.method_2906(var11.field_7763, var9.field_7874, 0, class_1713.field_7794, mc.field_1724);
                        var6++;
                        this.taken++;
                     }
                  }
               }
            }

            known.put(this.container, var5);
            return var6 > 0 && this.ticks < 60
               ? "Restock: taking materials"
               : this.finish(var6 == 0 && this.ticks <= 1 ? "Restock: nothing useful here" : "Restock: done");
         default:
            return "Idle";
      }
   }

   private String finish(String var1) {
      return this.finish(var1, false);
   }

   private String finish(String var1, boolean var2) {
      if (this.container != null) {
         visited.put(this.container, System.currentTimeMillis());
         if (var2) {
            failed.put(this.container, System.currentTimeMillis());
         } else {
            failed.remove(this.container);
         }
      }

      if (mc.field_1755 instanceof class_465) {
         mc.field_1724.method_7346();
      }

      this.stage = Restock.Stage.IDLE;
      this.container = null;
      return var1;
   }

   public static void remember(class_2338 var0, Set<class_1792> var1) {
      known.put(var0.method_10062(), var1);
   }

   public static boolean isContainer(class_2338 var0) {
      if (var0 != null && mc.field_1687 != null) {
         class_2680 var1 = mc.field_1687.method_8320(var0);
         return var1.method_26204() instanceof class_2281 || var1.method_26204() instanceof class_3708 || var1.method_26204() instanceof class_2480;
      } else {
         return false;
      }
   }

   public static Set<class_2338> nearbyContainers(double var0) {
      HashSet var2 = new HashSet();
      class_1923 var3 = mc.field_1724.method_31476();
      int var4 = (int)Math.ceil(var0 / 16.0) + 1;
      double var5 = var0 * var0;

      for (int var7 = -var4; var7 <= var4; var7++) {
         for (int var8 = -var4; var8 <= var4; var8++) {
            class_2818 var9 = mc.field_1687.method_2935().method_21730(var3.field_9181 + var7, var3.field_9180 + var8);
            if (var9 != null) {
               for (class_2586 var11 : var9.method_12214().values()) {
                  class_2338 var12 = var11.method_11016();
                  if (class_243.method_24953(var12).method_1025(mc.field_1724.method_73189()) <= var5 && isContainer(var12)) {
                     var2.add(var12);
                  }
               }
            }
         }
      }

      return var2;
   }

   private static enum Stage {
      IDLE,
      WALK,
      OPEN,
      TAKE;
   }
}
