package dev.dihclient.modules.world;

import dev.dihclient.module.Category;
import dev.dihclient.module.Module;
import dev.dihclient.module.ModuleManager;
import dev.dihclient.modules.automation.StashSorter;
import dev.dihclient.modules.automation.TaskQueue;
import dev.dihclient.render.Render3D;
import dev.dihclient.setting.BoolSetting;
import dev.dihclient.setting.IntSetting;
import dev.dihclient.setting.StringSetting;
import dev.dihclient.util.Notifications;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.class_1297;
import net.minecraft.class_1588;
import net.minecraft.class_1920;
import net.minecraft.class_2246;
import net.minecraft.class_2338;
import net.minecraft.class_2350;
import net.minecraft.class_238;
import net.minecraft.class_243;
import net.minecraft.class_2680;
import net.minecraft.class_761;

public class SafeRoute extends Module {
   public final BoolSetting avoidMobs = this.bool("Avoid Mobs", "Paths keep their distance to hostile mobs; Goto replans when one gets onto the path.", true);
   public final IntSetting mobRadius = this.integer("Mob Distance", "How far paths stay away from hostile mobs.", 6, 2, 16).visibleWhen(this.avoidMobs::get);
   public final BoolSetting avoidDark = this.bool("Avoid Dark", "Prefers lit blocks – dark caves are where mobs spawn.", true);
   public final BoolSetting avoidEdges = this.bool("Avoid Edges", "Keeps away from edges with a drop deeper than Safe Fall.", true);
   public final BoolSetting lavaMargin = this.bool("Lava Margin", "Keeps 2 blocks distance to lava instead of 1.", true);
   public final BoolSetting avoidWater = this.bool("Avoid Water", "Swims only when there is no reasonable way around.", false);
   public final IntSetting safeFall = this.integer("Safe Fall", "Highest drop any bot walks off (deeper only into water).", 3, 1, 8);
   public final BoolSetting guardBots = this.bool(
      "Guard Bots", "AutoMine, AutoFarm, Tunnel, Terraform and AutoBuild stop before lava, deep drops and mobs in front of them.", true
   );
   public final IntSetting stopRadius = this.integer("Mob Stop Distance", "Bots don't walk towards a hostile mob closer than this.", 4, 2, 10)
      .visibleWhen(this.guardBots::get);
   public final BoolSetting mobAlert = this.bool("Mob Alert", "Warns you when a hostile mob comes close while a bot is running.", true);
   public final BoolSetting learn = this.bool(
      "Learn Dangers", "Remembers where you got badly hurt or died (lava, big falls, mobs) and paths avoid those spots from then on. Saved per server.", true
   );
   public final IntSetting learnDamage = this.integer("Learn At Damage", "Damage in one hit (hearts x2) that counts as dangerous.", 6, 2, 20)
      .visibleWhen(this.learn::get);
   public final BoolSetting showDangers = this.bool("Show Dangers", "Draws the remembered danger spots.", false).visibleWhen(this.learn::get);
   public final BoolSetting retreat = this.bool(
      "Retreat Home", "When health or food gets low while a bot is running, all bots stop and Goto walks you Home.", false
   );
   public final StringSetting home = this.text("Home", "Waypoint name or coordinates to retreat to.", "home", 64).visibleWhen(this.retreat::get);
   public final IntSetting retreatHealth = this.integer("Retreat Health", "Retreats at or below this health (hearts x2).", 8, 1, 19)
      .visibleWhen(this.retreat::get);
   public final IntSetting retreatFood = this.integer("Retreat Food", "Retreats at or below this food level (0 = ignore food).", 4, 0, 19)
      .visibleWhen(this.retreat::get);
   private boolean retreating;
   private float lastHealth = -1.0F;
   private List<DangerMemory.Spot> shown = List.of();
   private int shownRefresh;
   private int alertCooldown;
   private static String lastWarning;
   private static long lastWarningAt;

   public SafeRoute() {
      super(
         "SafeRoute",
         Category.AUTOMATION,
         "Safest instead of shortest: Goto & all bots avoid mobs, darkness, lava, deep drops, water and spots where you got hurt before."
      );
      this.action(
         "Forget Dangers",
         "Deletes the learned danger spots of this server.",
         () -> Notifications.info("SafeRoute", DangerMemory.forgetHere() + " danger spots forgotten")
      );
   }

   private static String cause() {
      if (mc.field_1724.method_5771()) {
         return "lava";
      } else if (closestHostile(5.0) != null) {
         return "mob";
      } else {
         return !(mc.field_1724.field_6017 > 3.0) && !mc.field_1724.method_24828() ? "damage" : "fall";
      }
   }

   private void retreatTick() {
      if (this.retreating) {
         if (!Goto.running()) {
            this.retreating = false;
         }
      } else if (botRunning() || ModuleManager.on(TaskQueue.class) || ModuleManager.on(StashSorter.class)) {
         boolean var1 = mc.field_1724.method_6032() <= this.retreatHealth.get().intValue();
         boolean var2 = this.retreatFood.get() > 0 && mc.field_1724.method_7344().method_7586() <= this.retreatFood.get();
         if (var1 || var2) {
            Goto.Target var3 = Goto.resolve(this.home.get());
            if (var3 == null) {
               Notifications.warn("SafeRoute", "Retreat: Home \"" + this.home.get() + "\" not found");
               this.retreat.set(false);
            } else {
               stopBots();
               this.retreating = true;
               Notifications.push("SafeRoute", (var1 ? "Low health" : "Low food") + " – retreating to " + var3.label(), Notifications.Type.WARNING);
               Goto.start(
                  var3.pos().method_10263(),
                  var3.hasY() ? var3.pos().method_10264() : Integer.MIN_VALUE,
                  var3.pos().method_10260(),
                  "Home (" + var3.label() + ")"
               );
            }
         }
      }
   }

   public static void stopBots() {
      for (Class var1 : List.of(AutoMine.class, AutoFarm.class, Tunnel.class, Terraform.class, TaskQueue.class, StashSorter.class, Goto.class)) {
         if (ModuleManager.on(var1)) {
            ModuleManager.<Module>of(var1).setEnabled(false);
         }
      }

      if (ModuleManager.on(AutoBuild.class) && ModuleManager.of(AutoBuild.class).runtime().isBuilding()) {
         ModuleManager.of(AutoBuild.class).runtime().pauseToggle();
      }
   }

   private void learnTick() {
      float var1 = mc.field_1724.method_6032();
      if (this.lastHealth >= 0.0F && var1 < this.lastHealth) {
         float var2 = this.lastHealth - var1;
         boolean var3 = var1 <= 0.0F || mc.field_1724.method_29504();
         if (var3 || var2 >= this.learnDamage.get().intValue() || var1 <= 4.0F && var2 >= 2.0F) {
            class_2338 var4 = mc.field_1724.method_24515();
            String var5 = cause();
            DangerMemory.record(var4, var5, var3 ? 3 : 1);
            note("Learned danger (" + var5 + ") at " + var4.method_10263() + " " + var4.method_10264() + " " + var4.method_10260());
            this.shownRefresh = 0;
         }
      }

      this.lastHealth = var1;
   }

   @Override
   public void onRender3D(Render3D var1) {
      if (this.learn.get() && this.showDangers.get() && mc.field_1724 != null) {
         if (--this.shownRefresh <= 0) {
            this.shown = DangerMemory.near(mc.field_1724.method_24515(), 96.0);
            this.shownRefresh = 40;
         }

         for (DangerMemory.Spot var3 : this.shown) {
            var1.box(new class_238(var3.x, var3.y, var3.z, var3.x + 1, var3.y + 1, var3.z + 1), 1090465840, -53200, true);
         }
      }
   }

   public static SafeRoute get() {
      return ModuleManager.of(SafeRoute.class);
   }

   public static boolean active() {
      return ModuleManager.on(SafeRoute.class) && mc.field_1724 != null && mc.field_1687 != null;
   }

   public static int maxFall(int var0) {
      return active() ? Math.min(var0, get().safeFall.get()) : var0;
   }

   public static String warning() {
      return lastWarning != null && System.currentTimeMillis() - lastWarningAt < 3000L ? lastWarning : null;
   }

   public static void note(String var0) {
      lastWarning = var0;
      lastWarningAt = System.currentTimeMillis();
   }

   public static boolean hostile(class_1297 var0) {
      return var0 instanceof class_1588 && var0.method_5805();
   }

   public static List<class_243> hostiles(class_243 var0, double var1) {
      ArrayList var3 = new ArrayList();
      if (mc.field_1687 == null) {
         return var3;
      } else {
         double var4 = var1 * var1;

         for (class_1297 var7 : mc.field_1687.method_18112()) {
            if (hostile(var7)) {
               class_243 var8 = var7.method_73189();
               if (var8.method_1025(var0) <= var4) {
                  var3.add(var8);
               }
            }
         }

         return var3;
      }
   }

   private static class_1297 closestHostile(double var0) {
      class_1297 var2 = null;
      double var3 = var0 * var0;

      for (class_1297 var6 : mc.field_1687.method_18112()) {
         if (hostile(var6)) {
            double var7 = var6.method_73189().method_1025(mc.field_1724.method_73189());
            if (var7 < var3) {
               var3 = var7;
               var2 = var6;
            }
         }
      }

      return var2;
   }

   public static boolean mobToward(class_243 var0) {
      if (active() && get().guardBots.get()) {
         class_243 var1 = mc.field_1724.method_73189();
         double var2 = var0.field_1352 - var1.field_1352;
         double var4 = var0.field_1350 - var1.field_1350;
         double var6 = Math.sqrt(var2 * var2 + var4 * var4);
         if (var6 < 0.2) {
            return false;
         } else {
            for (class_243 var9 : hostiles(var1, get().stopRadius.get().intValue())) {
               double var10 = var9.field_1352 - var1.field_1352;
               double var12 = var9.field_1350 - var1.field_1350;
               double var14 = Math.sqrt(var10 * var10 + var12 * var12);
               if (var14 > 0.3 && (var2 * var10 + var4 * var12) / (var6 * var14) > 0.6) {
                  return true;
               }
            }

            return false;
         }
      } else {
         return false;
      }
   }

   public static boolean mobNearPath(List<class_2338> var0) {
      if (active() && get().avoidMobs.get() && !var0.isEmpty()) {
         List var1 = hostiles(mc.field_1724.method_73189(), 32.0);
         if (var1.isEmpty()) {
            return false;
         } else {
            double var2 = get().mobRadius.get().intValue() * 0.6;
            var2 *= var2;

            for (class_2338 var5 : var0) {
               class_243 var6 = class_243.method_24955(var5);

               for (class_243 var8 : var1) {
                  if (var8.method_1025(var6) < var2) {
                     return true;
                  }
               }
            }

            return false;
         }
      } else {
         return false;
      }
   }

   private static boolean lava(class_2338 var0) {
      return mc.field_1687.method_8320(var0).method_27852(class_2246.field_10164);
   }

   private static boolean solid(class_2338 var0) {
      class_2680 var1 = mc.field_1687.method_8320(var0);
      return !var1.method_26215() && (var1.method_26227().method_15769() || !var1.method_45474()) && !var1.method_26218(mc.field_1687, var0).method_1110();
   }

   private static boolean water(class_2338 var0) {
      class_2680 var1 = mc.field_1687.method_8320(var0);
      return !var1.method_26227().method_15769() && !lava(var0);
   }

   public static String blocked(class_243 var0, boolean var1) {
      if (active() && get().guardBots.get()) {
         class_243 var2 = mc.field_1724.method_73189();
         double var3 = var0.field_1352 - var2.field_1352;
         double var5 = var0.field_1350 - var2.field_1350;
         if (var3 * var3 + var5 * var5 < 0.04) {
            return null;
         } else {
            class_2350 var7 = Math.abs(var3) > Math.abs(var5)
               ? (var3 > 0.0 ? class_2350.field_11034 : class_2350.field_11039)
               : (var5 > 0.0 ? class_2350.field_11035 : class_2350.field_11043);
            class_2338 var8 = mc.field_1724.method_24515().method_10093(var7);
            if (!lava(var8) && !lava(var8.method_10084()) && !lava(var8.method_10074())) {
               if (var1 && !solid(var8) && !solid(var8.method_10074())) {
                  int var9 = get().safeFall.get();
                  int var10 = 0;

                  for (class_2338 var11 = var8.method_10074(); !solid(var11) && var10 <= var9 + 1; var11 = var11.method_10074()) {
                     if (lava(var11)) {
                        return "lava below";
                     }

                     if (water(var11)) {
                        break;
                     }

                     var10++;
                  }

                  if (var10 > var9) {
                     return "drop deeper than " + var9;
                  }
               }

               return mobToward(var0) ? "hostile mob ahead" : null;
            } else {
               return "lava ahead";
            }
         }
      } else {
         return null;
      }
   }

   public static SafeRoute.Search search(class_2338 var0) {
      if (!active()) {
         return null;
      } else {
         SafeRoute var1 = get();
         List var2 = var1.avoidMobs.get() ? hostiles(class_243.method_24955(var0), 96.0) : List.of();
         SafeRoute.Search var3 = new SafeRoute.Search(
            var2,
            var1.mobRadius.get().intValue(),
            var1.avoidDark.get(),
            var1.avoidEdges.get(),
            var1.lavaMargin.get(),
            var1.avoidWater.get(),
            var1.safeFall.get()
         );
         if (var1.learn.get()) {
            var3.dangers = DangerMemory.near(var0, 256.0);
         }

         return var3;
      }
   }

   public static boolean botRunning() {
      return ModuleManager.on(Goto.class)
         || ModuleManager.on(AutoMine.class)
         || ModuleManager.on(AutoFarm.class)
         || ModuleManager.on(Tunnel.class)
         || ModuleManager.on(Terraform.class)
         || ModuleManager.on(AutoBuild.class) && ModuleManager.of(AutoBuild.class).runtime().isBuilding();
   }

   @Override
   public void onTick() {
      if (inGame()) {
         if (this.learn.get()) {
            this.learnTick();
         } else {
            this.lastHealth = -1.0F;
         }

         if (this.retreat.get()) {
            this.retreatTick();
         }

         if (this.alertCooldown > 0) {
            this.alertCooldown--;
         } else if (this.mobAlert.get() && botRunning()) {
            class_1297 var1 = closestHostile(this.mobRadius.get().intValue());
            if (var1 != null) {
               int var2 = (int)Math.sqrt(var1.method_73189().method_1025(mc.field_1724.method_73189()));
               Notifications.warn("SafeRoute", var1.method_5477().getString() + " " + var2 + "m away");
               this.alertCooldown = 200;
            }
         }
      }
   }

   @Override
   protected void onEnable() {
      this.lastHealth = -1.0F;
      this.retreating = false;
   }

   @Override
   public void onWorldChange() {
      this.lastHealth = -1.0F;
      this.retreating = false;
   }

   @Override
   public String getInfo() {
      return this.safeFall.get() + "↓";
   }

   public static final class Search {
      final List<class_243> mobs;
      final double mobRadius;
      List<DangerMemory.Spot> dangers = List.of();
      public final boolean dark;
      public final boolean edges;
      public final boolean lavaMargin;
      public final boolean water;
      public final int safeFall;

      Search(List<class_243> var1, double var2, boolean var4, boolean var5, boolean var6, boolean var7, int var8) {
         this.mobs = var1;
         this.mobRadius = var2;
         this.dark = var4;
         this.edges = var5;
         this.lavaMargin = var6;
         this.water = var7;
         this.safeFall = var8;
      }

      public double mobCost(class_2338 var1) {
         if (this.mobs.isEmpty() && this.dangers.isEmpty()) {
            return 0.0;
         } else {
            double var2 = 0.0;
            double var4 = var1.method_10263() + 0.5;
            double var6 = var1.method_10264();
            double var8 = var1.method_10260() + 0.5;

            for (class_243 var11 : this.mobs) {
               double var12 = var11.field_1352 - var4;
               double var14 = (var11.field_1351 - var6) * 1.5;
               double var16 = var11.field_1350 - var8;
               double var18 = Math.sqrt(var12 * var12 + var14 * var14 + var16 * var16);
               if (var18 < this.mobRadius) {
                  var2 += (this.mobRadius - var18) * 2.0;
               }
            }

            for (DangerMemory.Spot var21 : this.dangers) {
               double var22 = var21.x + 0.5 - var4;
               double var23 = var21.y - var6;
               double var24 = var21.z + 0.5 - var8;
               double var25 = Math.sqrt(var22 * var22 + var23 * var23 + var24 * var24);
               if (var25 < 4.0) {
                  var2 += (4.0 - var25) * 2.5 * var21.weight;
               }
            }

            return var2;
         }
      }

      public boolean isDark(class_2338 var1) {
         try {
            int var2 = class_761.method_23794((class_1920)SafeRoute.mc.field_1687, var1);
            return (var2 >> 4 & 15) == 0 && (var2 >> 20 & 15) < 8;
         } catch (Throwable var3) {
            return false;
         }
      }
   }
}
