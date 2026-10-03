package dev.dihclient.modules.movement;

import dev.dihclient.autobuild.AutoBuy;
import dev.dihclient.gui.WaypointsScreen;
import dev.dihclient.hud.HudManager;
import dev.dihclient.module.Category;
import dev.dihclient.module.Module;
import dev.dihclient.module.ModuleManager;
import dev.dihclient.modules.world.Goto;
import dev.dihclient.render.Render3D;
import dev.dihclient.setting.BoolSetting;
import dev.dihclient.setting.EnumSetting;
import dev.dihclient.setting.IntSetting;
import dev.dihclient.setting.StringSetting;
import dev.dihclient.util.InvUtil;
import dev.dihclient.util.ItemUtil;
import dev.dihclient.util.KeyUtil;
import dev.dihclient.util.Notifications;
import dev.dihclient.util.RotationUtil;
import dev.dihclient.util.WalkSafety;
import dev.dihclient.waypoint.WaypointManager;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.class_1268;
import net.minecraft.class_1304;
import net.minecraft.class_1713;
import net.minecraft.class_1792;
import net.minecraft.class_1799;
import net.minecraft.class_1802;
import net.minecraft.class_2338;
import net.minecraft.class_238;
import net.minecraft.class_243;
import net.minecraft.class_2338.class_2339;

public class ElytraBot extends Module {
   public final StringSetting target = this.text(
      "Target", "Where to fly: \"x z\", \"x y z\" (~ = your own coordinate) or the name of a waypoint. Turning the module on starts the flight.", "0 0", 64
   );
   public final EnumSetting<ElytraBot.Mode> mode = this.mode(
      "Mode",
      "Dive: climbs with firework rockets (no boost), then glides on its own with gentle dives and pull-ups – without rockets it only glides. Boost: flies with the ElytraBoost module. Firework: uses rockets all the time (can buy them on /ah).",
      ElytraBot.Mode.DIVE
   );
   public final IntSetting cruiseY = this.integer("Cruise Y", "Flight height (0 = automatic: high enough for mountains, below the Nether roof).", 0, 0, 315);
   public final BoolSetting autoEquip = this.bool("Auto Equip", "Puts an elytra from your inventory on if you are not wearing one.", true);
   public final BoolSetting takeOff = this.bool("Take Off", "Jumps and opens the wings by itself when you stand on the ground.", true);
   public final BoolSetting avoid = this.bool("Avoid Obstacles", "Looks ahead and steers around / over mountains, trees and walls.", true);
   public final BoolSetting land = this.bool(
      "Land At Target", "Descends above the target, circles if needed and lands. Off = flies over it and stops there.", true
   );
   public final IntSetting arrive = this.integer("Arrive Radius", "Counts as arrived when you land this close.", 6, 2, 64);
   public final IntSetting rocketBelow = this.integer("Rocket Below Speed", "Uses a rocket when slower than this (blocks/s).", 24, 8, 32)
      .visibleWhen(() -> this.mode.get() != ElytraBot.Mode.BOOST);
   public final IntSetting keepRockets = this.integer("Keep Rockets", "Never uses the last rockets – kept for the landing approach.", 0, 0, 64)
      .visibleWhen(() -> this.mode.get() != ElytraBot.Mode.BOOST);
   public final IntSetting diveAngle = this.integer("Dive Angle", "How steeply it dives to build speed (degrees down). 20-30 is gentle.", 25, 15, 35)
      .visibleWhen(() -> this.mode.get() == ElytraBot.Mode.DIVE);
   public final IntSetting climbAngle = this.integer(
         "Max Climb Angle", "Steepest pull-up (degrees up). It starts flat and gets steeper the more speed there is – never vertical.", 40, 15, 65
      )
      .visibleWhen(() -> this.mode.get() == ElytraBot.Mode.DIVE);
   public final IntSetting boostMaxSpeed = this.integer(
         "Boost Max Speed", "Stops boosting above this speed (blocks/s). Boost strength itself is set in the ElytraBoost module.", 55, 20, 100
      )
      .visibleWhen(() -> this.mode.get() == ElytraBot.Mode.BOOST);
   public final BoolSetting buyRockets = this.bool(
         "Buy Rockets (/ah)",
         "Buys firework rockets on the auction house (/ah) when you run low – before take-off, and it lands to restock if they run out on the way.",
         false
      )
      .visibleWhen(() -> this.mode.get() == ElytraBot.Mode.FIREWORK);
   public final IntSetting buyBelow = this.integer("Buy Below", "Buys more when you carry fewer rockets than this.", 16, 1, 64)
      .visibleWhen(() -> this.mode.get() == ElytraBot.Mode.FIREWORK && this.buyRockets.get());
   public final BoolSetting selfClimb = this.bool(
      "Self Climb",
      "Not used in Dive mode. Climbs by itself without rockets or other modules: after take-off it pulls up at a moderate angle and gains height on its own until the cruise height is reached (client-side speed – may be flagged by strict anti-cheats).",
      true
   );
   public final IntSetting selfClimbSpeed = this.integer("Climb Speed", "How fast Self Climb gains height (blocks/s up).", 8, 3, 20)
      .visibleWhen(() -> this.selfClimb.get());
   public final IntSetting selfClimbForward = this.integer("Climb Forward Speed", "Forward speed while self-climbing (blocks/s).", 22, 10, 40)
      .visibleWhen(() -> this.selfClimb.get());
   public final StringSetting route = this.text(
      "Route", "Several stops for Start Route, separated by > or ; – waypoint names or coordinates, e.g. \"spawn > 5000 -2000 > base\".", "", 256
   );
   public final BoolSetting landAtStops = this.bool(
      "Land At Stops", "Lands at every stop of the route. Off = flies through the stops and only lands at the last one.", false
   );
   public final BoolSetting loopRoute = this.bool("Loop Route", "Starts the route again from the first stop.", false);
   public final IntSetting turnSpeed = this.integer("Turn Speed", "Maximum turning per tick in degrees (lower = smoother).", 7, 2, 25);
   public final IntSetting minDurability = this.integer("Min Durability", "Lands where it is when the elytra has this little durability left.", 12, 0, 200);
   public final BoolSetting render = this.bool("Render", "Draws the target and the line to it.", true);
   private static final double CRUISE_TOLERANCE = 6.0;
   private ElytraBot.Phase phase = ElytraBot.Phase.PREPARE;
   private class_2338 goal;
   private boolean goalHasY;
   private String label = "";
   private boolean presetGoal;
   private String status = "Idle";
   private int now;
   private int lastRocket;
   private int restoreSlot = -1;
   private int restoreIn;
   private int stateTicks;
   private int equipWait;
   private double cruise;
   private boolean diving = true;
   private boolean landing;
   private boolean emergency;
   private double startDistance = 1.0;
   private int rocketsUsed;
   private double clearAhead = 999.0;
   private float avoidYaw = Float.NaN;
   private boolean avoidClimb;
   private int avoidTicks;
   private double groundY = Double.NaN;
   private double goalGround = Double.NaN;
   private class_2338 goalGroundFor;
   private double speedNow;
   private final List<String> legs = new ArrayList<>();
   private int leg;
   private boolean routeActive;
   private final AutoBuy buyer = new AutoBuy();
   private int rocketsBeforeBuy;
   private int buyFails;
   private boolean buyGiveUp;
   private boolean restocking;
   private class_2338 savedGoal;
   private boolean savedGoalHasY;
   private boolean boostOwned;
   private boolean forwardHeld;
   private double aboveGround = 999.0;
   private int diveTicks;
   private int stuckTicks;
   private int unstuckTicks;
   private float unstuckYaw;
   private int unstuckSide = 1;
   private int urgentTicks;

   public ElytraBot() {
      super("ElytraBot", Category.AUTOMATION, "Elytra autopilot: fly to coordinates or a waypoint by itself – rockets or rocket-free, avoids obstacles, lands.");
      this.action("Go", "Flies to the Target.", () -> {
         if (this.isEnabled()) {
            this.setEnabled(false);
         }

         this.setEnabled(true);
      });
      this.action("Start Route", "Flies the Route stop by stop.", this::startRoute);
      this.action("Stop", "Stops the autopilot.", () -> this.setEnabled(false));
   }

   public static ElytraBot instance() {
      return ModuleManager.of(ElytraBot.class);
   }

   private void startRoute() {
      this.legs.clear();

      for (String var4 : this.route.get().split("[>;]")) {
         if (!var4.isBlank()) {
            if (Goto.resolve(var4) == null) {
               Notifications.warn("ElytraBot", "Route: unknown stop \"" + var4.trim() + "\"");
               return;
            }

            this.legs.add(var4.trim());
         }
      }

      if (this.legs.isEmpty()) {
         Notifications.warn("ElytraBot", "Route is empty – e.g. \"spawn > 5000 -2000 > base\"");
      } else {
         this.leg = 0;
         if (this.isEnabled()) {
            this.setEnabled(false);
         }

         if (this.setLeg()) {
            this.presetGoal = true;
            this.setEnabled(true);
            this.routeActive = this.isEnabled();
         }
      }
   }

   private boolean setLeg() {
      Goto.Target var1 = Goto.resolve(this.legs.get(this.leg));
      if (var1 == null) {
         Notifications.warn("ElytraBot", "Route: stop \"" + this.legs.get(this.leg) + "\" is gone");
         return false;
      } else {
         this.goal = var1.pos();
         this.goalHasY = var1.hasY();
         this.label = "Route " + (this.leg + 1) + "/" + this.legs.size() + ": " + var1.label();
         return true;
      }
   }

   private boolean nextLeg() {
      if (this.routeActive && this.legs.size() >= 2 && (this.leg + 1 < this.legs.size() || this.loopRoute.get())) {
         this.leg = (this.leg + 1) % this.legs.size();
         if (this.setLeg()) {
            this.landing = false;
            this.startDistance = Math.max(1.0, this.horizontal());
            this.cruise = this.chooseCruise();
            Notifications.info("ElytraBot", "Next stop: " + this.label);
            return true;
         }

         this.routeActive = false;
      }

      return false;
   }

   private boolean lastStop() {
      return !this.routeActive || this.legs.size() < 2 || this.leg + 1 >= this.legs.size() && !this.loopRoute.get();
   }

   private boolean nextStopFar() {
      int var1 = (this.leg + 1) % this.legs.size();
      Goto.Target var2 = Goto.resolve(this.legs.get(var1));
      return var2 != null
         && Math.hypot(var2.pos().method_10263() + 0.5 - mc.field_1724.method_23317(), var2.pos().method_10260() + 0.5 - mc.field_1724.method_23321()) > 90.0;
   }

   public static void start(int var0, int var1, int var2, String var3) {
      ElytraBot var4 = instance();
      if (var4 != null) {
         if (var4.isEnabled()) {
            var4.setEnabled(false);
         }

         var4.goal = new class_2338(var0, var1 == Integer.MIN_VALUE ? 0 : var1, var2);
         var4.goalHasY = var1 != Integer.MIN_VALUE;
         var4.label = var3;
         var4.presetGoal = true;
         var4.routeActive = false;
         var4.setEnabled(true);
      }
   }

   private boolean parseTarget() {
      String var1 = this.target.get().trim();
      String[] var2 = var1.split("\\s+");
      int[] var3 = var2.length >= 2 ? WaypointsScreen.parseCoords(var2, 0) : null;
      if (var3 != null) {
         this.goal = new class_2338(var3[0], var3[1], var3[2]);
         this.goalHasY = var3[3] == 1;
         this.label = var3[0] + " " + (this.goalHasY ? var3[1] + " " : "") + var3[2];
         return true;
      } else {
         WaypointManager.Waypoint var4 = null;
         String var5 = WaypointManager.dimKey();

         for (WaypointManager.Waypoint var7 : WaypointManager.get().here()) {
            if ((
                  var7.name.equalsIgnoreCase(var1)
                     || var4 == null && var7.name.toLowerCase(Locale.ROOT).contains(var1.toLowerCase(Locale.ROOT)) && !var1.isEmpty()
               )
               && (var4 == null || var5.equals(var7.dim))) {
               var4 = var7;
            }
         }

         if (var4 == null) {
            return false;
         } else {
            this.goal = new class_2338(var4.x, var4.y, var4.z);
            this.goalHasY = true;
            this.label = var4.name;
            return true;
         }
      }
   }

   @Override
   protected void onEnable() {
      if (!inGame()) {
         this.presetGoal = false;
         this.setEnabledSilently(false);
      } else {
         if (!this.presetGoal && !this.parseTarget()) {
            Notifications.warn("ElytraBot", "Target must be \"x y z\", \"x z\" or a waypoint name");
            this.setEnabledSilently(false);
            return;
         }

         this.presetGoal = false;
         this.phase = ElytraBot.Phase.PREPARE;
         this.stateTicks = 0;
         this.equipWait = 0;
         this.landing = false;
         this.emergency = false;
         this.diving = true;
         this.rocketsUsed = 0;
         this.avoidYaw = Float.NaN;
         this.avoidClimb = false;
         this.groundY = Double.NaN;
         this.goalGroundFor = null;
         this.buyer.reset();
         this.buyFails = 0;
         this.buyGiveUp = false;
         this.restocking = false;
         this.diveTicks = 0;
         this.stuckTicks = 0;
         this.unstuckTicks = 0;
         this.urgentTicks = 0;
         this.startDistance = Math.max(1.0, this.horizontal());
         this.cruise = this.chooseCruise();
         this.status = "Preparing";
         Notifications.info("ElytraBot", "Flying to " + this.label);
      }
   }

   @Override
   protected void onDisable() {
      this.routeActive = false;
      this.releaseJump();
      this.boost(false);
      this.buyer.reset();
      this.restocking = false;
      if (mc.field_1724 != null && this.restoreSlot >= 0) {
         InvUtil.select(this.restoreSlot);
      }

      this.restoreSlot = -1;
      this.status = "Idle";
   }

   @Override
   public void onWorldChange() {
      if (this.isEnabled()) {
         this.setEnabled(false);
      }
   }

   private void releaseJump() {
      if (mc.field_1690 != null) {
         mc.field_1690.field_1903.method_23481(KeyUtil.isPhysicallyDown(mc.field_1690.field_1903));
      }
   }

   private double chooseCruise() {
      String var1 = WaypointManager.dimKey();
      boolean var2 = var1.contains("nether");
      double var3 = var2 ? 118.0 : 315.0;
      if (this.cruiseY.get() > 0) {
         return Math.min((double)this.cruiseY.get().intValue(), var3);
      } else {
         double var5 = Math.max(mc.field_1724.method_23318() + 40.0, var2 ? 100.0 : 150.0);
         if (this.goalHasY && !var2) {
            var5 = Math.max(var5, this.goal.method_10264() + 60.0);
         }

         return Math.min(var5, var3 - 8.0);
      }
   }

   private double horizontal() {
      return this.goal != null && mc.field_1724 != null
         ? Math.hypot(this.goal.method_10263() + 0.5 - mc.field_1724.method_23317(), this.goal.method_10260() + 0.5 - mc.field_1724.method_23321())
         : 0.0;
   }

   private static boolean isRocket(class_1799 var0) {
      return !var0.method_7960() && ItemUtil.id(var0).equals("minecraft:firework_rocket");
   }

   private static int rockets() {
      return InvUtil.count(ElytraBot::isRocket);
   }

   private static boolean wearingElytra() {
      return mc.field_1724.method_6118(class_1304.field_6174).method_31574(class_1802.field_8833);
   }

   private boolean useRockets() {
      return this.mode.get() != ElytraBot.Mode.BOOST && rockets() > this.keepRockets.get();
   }

   private void boost(boolean var1) {
      ElytraBoost var2 = ModuleManager.of(ElytraBoost.class);
      if (var2 != null && mc.field_1690 != null) {
         if (var1) {
            if (!var2.isEnabled()) {
               var2.setEnabled(true);
               this.boostOwned = true;
            }

            mc.field_1690.field_1894.method_23481(true);
            this.forwardHeld = true;
         } else {
            if (this.boostOwned) {
               var2.setEnabled(false);
               this.boostOwned = false;
            }

            this.releaseForward();
         }
      }
   }

   private void releaseForward() {
      if (this.forwardHeld && mc.field_1690 != null) {
         mc.field_1690.field_1894.method_23481(KeyUtil.isPhysicallyDown(mc.field_1690.field_1894));
         this.forwardHeld = false;
      }
   }

   private boolean wantsRestock() {
      return this.mode.get() == ElytraBot.Mode.FIREWORK && this.buyRockets.get() && !this.buyGiveUp;
   }

   private static double scanGround(double var0, double var2, double var4) {
      int var6 = (int)Math.floor(var0);
      int var7 = (int)Math.floor(var4);
      if (!mc.field_1687.method_2935().method_12123(var6 >> 4, var7 >> 4)) {
         return Double.NaN;
      } else {
         class_2339 var8 = new class_2339();

         for (int var9 = (int)Math.floor(var2); var9 > -64; var9--) {
            if (WalkSafety.solid(var8.method_10103(var6, var9, var7))) {
               return var9 + 1.0;
            }
         }

         return Double.NaN;
      }
   }

   private static double clearDistance(float var0, float var1, double var2) {
      double var4 = Math.toRadians(var0);
      double var6 = Math.toRadians(var1);
      double var8 = -Math.sin(var4) * Math.cos(var6);
      double var10 = -Math.sin(var6);
      double var12 = Math.cos(var4) * Math.cos(var6);
      double var14 = Math.cos(var4);
      double var16 = Math.sin(var4);
      class_243 var18 = mc.field_1724.method_33571();
      class_2339 var19 = new class_2339();
      double[][] var20 = new double[][]{{0.0, 0.0, 0.0}, {0.0, -0.9, 0.0}, {0.7, -0.4, 0.0}, {-0.7, -0.4, 0.0}, {0.0, 0.5, 0.0}};

      for (double var21 = 2.0; var21 <= var2; var21++) {
         for (double[] var26 : var20) {
            var19.method_10103(
               (int)Math.floor(var18.field_1352 + var8 * var21 + var14 * var26[0]),
               (int)Math.floor(var18.field_1351 + var10 * var21 + var26[1]),
               (int)Math.floor(var18.field_1350 + var12 * var21 + var16 * var26[0])
            );
            if (mc.field_1687.method_2935().method_12123(var19.method_10263() >> 4, var19.method_10260() >> 4) && WalkSafety.solid(var19)) {
               return var21;
            }
         }
      }

      return var2;
   }

   private void useRocket() {
      int var1 = InvUtil.findHotbar(ElytraBot::isRocket);
      if (var1 < 0) {
         int var2 = InvUtil.findInventory(ElytraBot::isRocket);
         if (var2 < 9) {
            return;
         }

         int var3 = InvUtil.firstEmptyHotbar();
         var1 = var3 < 0 ? 8 : var3;
         InvUtil.swapToHotbar(var2, var1);
      }

      if (this.restoreSlot < 0) {
         this.restoreSlot = InvUtil.selectedSlot();
      }

      InvUtil.select(var1);
      mc.field_1761.method_2919(mc.field_1724, class_1268.field_5808);
      mc.field_1724.method_6104(class_1268.field_5808);
      this.lastRocket = this.now;
      this.rocketsUsed++;
      this.restoreIn = 2;
   }

   @Override
   public void onTick() {
      if (inGame() && this.goal != null) {
         this.now++;
         if (this.restoreIn > 0 && --this.restoreIn == 0 && this.restoreSlot >= 0) {
            InvUtil.select(this.restoreSlot);
            this.restoreSlot = -1;
         }

         if (!mc.field_1724.method_31549().field_7479 && mc.field_1724.method_5854() == null) {
            class_243 var1 = mc.field_1724.method_18798();
            this.speedNow = Math.sqrt(var1.field_1352 * var1.field_1352 + var1.field_1351 * var1.field_1351 + var1.field_1350 * var1.field_1350);
            if (mc.field_1724.method_6128()) {
               if (this.phase != ElytraBot.Phase.FLY) {
                  this.phase = ElytraBot.Phase.FLY;
                  this.releaseJump();
               }

               this.fly();
            } else if (this.phase == ElytraBot.Phase.FLY) {
               this.landed();
            } else {
               this.prepare();
            }
         } else {
            this.status = "Waiting (flying / riding)";
         }
      }
   }

   private void landed() {
      this.releaseJump();
      this.boost(false);
      if (this.restocking) {
         if (mc.field_1724.method_24828()) {
            this.goal = this.savedGoal;
            this.goalHasY = this.savedGoalHasY;
            this.restocking = false;
            this.landing = false;
            this.emergency = false;
            this.phase = ElytraBot.Phase.PREPARE;
            this.stateTicks = 0;
            this.status = "Landed to restock rockets";
         } else {
            this.status = "Waiting for ground (restock)";
         }
      } else {
         double var1 = this.horizontal();
         if ((var1 <= this.arrive.get().intValue() + 4.0 || !this.land.get() && var1 < 30.0) && !this.emergency && this.nextLeg()) {
            this.phase = ElytraBot.Phase.PREPARE;
            this.stateTicks = 0;
            this.status = "Stop reached – taking off to " + this.label;
         } else if (var1 <= this.arrive.get().intValue() + 4.0 || !this.land.get() && var1 < 30.0 || this.emergency) {
            Notifications.push("ElytraBot", this.emergency ? "Landed (low durability)" : "Arrived at " + this.label, Notifications.Type.SUCCESS);
            this.setEnabled(false);
         } else if (this.takeOff.get() && mc.field_1724.method_24828()) {
            this.phase = ElytraBot.Phase.PREPARE;
            this.stateTicks = 0;
            this.landing = false;
            this.status = "Landed early – taking off again";
         } else {
            this.status = "Waiting for ground";
         }
      }
   }

   private void prepare() {
      if (this.buyer.isActive()) {
         this.status = this.buyer.tick();
         if (!this.buyer.isActive()) {
            if (rockets() > this.rocketsBeforeBuy) {
               this.buyFails = 0;
            } else if (++this.buyFails >= 3) {
               this.buyGiveUp = true;
               Notifications.warn("ElytraBot", "Could not buy rockets on /ah – giving up");
            }
         }
      } else if (mc.field_1755 == null) {
         if (this.wantsRestock() && mc.field_1724.method_24828() && rockets() < this.buyBelow.get()) {
            class_1792 var1 = ItemUtil.item("minecraft:firework_rocket");
            if (var1 != null) {
               this.rocketsBeforeBuy = rockets();
               this.buyer.begin(var1);
               if (this.buyer.isActive()) {
                  this.status = "Buying rockets on /ah";
                  return;
               }

               if (rockets() == 0) {
                  this.status = "Waiting to buy rockets";
                  return;
               }
            }
         }

         if (!wearingElytra()) {
            if (this.autoEquip.get() && this.equipWait <= 0) {
               int var4 = InvUtil.findInventory(var1x -> var1x.method_31574(class_1802.field_8833) && ItemUtil.durabilityLeft(var1x) > this.minDurability.get());
               if (var4 < 0) {
                  Notifications.warn("ElytraBot", "No usable elytra found");
                  this.setEnabled(false);
               } else {
                  int var2 = mc.field_1724.field_7498.field_7763;
                  int var3 = InvUtil.toScreenSlot(var4);
                  mc.field_1761.method_2906(var2, var3, 0, class_1713.field_7790, mc.field_1724);
                  mc.field_1761.method_2906(var2, 6, 0, class_1713.field_7790, mc.field_1724);
                  mc.field_1761.method_2906(var2, var3, 0, class_1713.field_7790, mc.field_1724);
                  this.equipWait = 10;
                  this.status = "Putting the elytra on";
               }
            } else {
               this.equipWait = Math.max(0, this.equipWait - 1);
               this.status = "No elytra worn";
               if (!this.autoEquip.get() && this.now % 100 == 1) {
                  Notifications.warn("ElytraBot", "Wear an elytra first");
               }
            }
         } else if (ItemUtil.durabilityLeft(mc.field_1724.method_6118(class_1304.field_6174)) <= this.minDurability.get()) {
            Notifications.warn("ElytraBot", "Elytra is almost broken");
            this.setEnabled(false);
         } else if (this.mode.get() == ElytraBot.Mode.FIREWORK && rockets() == 0 && !this.wantsRestock() && !this.selfClimb.get()) {
            Notifications.warn("ElytraBot", "No firework rockets (use Mode Dive / Boost to fly without, or turn on Buy Rockets)");
            this.setEnabled(false);
         } else if (!this.takeOff.get()) {
            this.status = "Jump and open the wings yourself";
         } else {
            this.stateTicks++;
            class_243 var5 = mc.field_1724.method_18798();
            if (mc.field_1724.method_24828()) {
               if (this.phase == ElytraBot.Phase.DEPLOY || this.phase == ElytraBot.Phase.RELEASE) {
                  this.phase = ElytraBot.Phase.PREPARE;
               }

               if (this.roofAbove()) {
                  this.status = "Something above – can't take off here";
                  this.releaseJump();
                  if (this.stateTicks > 60) {
                     Notifications.warn("ElytraBot", "Not enough room above you to take off");
                     this.setEnabled(false);
                  }

                  return;
               }

               this.phase = ElytraBot.Phase.JUMP;
               mc.field_1690.field_1903.method_23481(true);
               this.status = "Taking off";
            } else if (this.phase == ElytraBot.Phase.JUMP || this.phase == ElytraBot.Phase.RELEASE) {
               this.phase = ElytraBot.Phase.RELEASE;
               if (var5.field_1351 > 0.0) {
                  mc.field_1690.field_1903.method_23481(false);
               } else {
                  this.phase = ElytraBot.Phase.DEPLOY;
                  mc.field_1690.field_1903.method_23481(true);
                  this.status = "Opening the wings";
               }
            } else if (this.phase == ElytraBot.Phase.DEPLOY) {
               mc.field_1690.field_1903.method_23481(this.stateTicks % 2 == 0);
               if (this.stateTicks > 200) {
                  Notifications.warn("ElytraBot", "Could not open the wings");
                  this.setEnabled(false);
               }
            } else {
               this.phase = ElytraBot.Phase.DEPLOY;
            }
         }
      }
   }

   private boolean roofAbove() {
      class_2338 var1 = mc.field_1724.method_24515();

      for (int var2 = 2; var2 <= 4; var2++) {
         if (WalkSafety.solid(var1.method_10086(var2))) {
            return true;
         }
      }

      return false;
   }

   private void fly() {
      double var1 = mc.field_1724.method_23317();
      double var3 = mc.field_1724.method_23318();
      double var5 = mc.field_1724.method_23321();
      double var7 = this.goal.method_10263() + 0.5 - var1;
      double var9 = this.goal.method_10260() + 0.5 - var5;
      double var11 = Math.hypot(var7, var9);
      if (this.routeActive && !this.landAtStops.get() && !this.lastStop() && !this.restocking && !this.emergency && var11 < 60.0 && this.nextStopFar()) {
         this.nextLeg();
      } else {
         float var13 = (float)Math.toDegrees(Math.atan2(var9, var7)) - 90.0F;
         float var14 = mc.field_1724.method_36454();
         float var15 = mc.field_1724.method_36455();
         double var16 = this.speedNow * 20.0;
         if (this.now % 5 == 0) {
            double var18 = scanGround(var1, var3, var5);
            this.groundY = var18;
         }

         double var46 = Double.isNaN(this.groundY) ? var3 - 40.0 : this.groundY;
         double var20 = var3 - var46;
         this.aboveGround = var20;
         class_1799 var22 = mc.field_1724.method_6118(class_1304.field_6174);
         if (!this.emergency && ItemUtil.durabilityLeft(var22) <= this.minDurability.get() && this.minDurability.get() > 0) {
            this.emergency = true;
            this.landing = true;
            this.goal = new class_2338((int)var1, this.goal.method_10264(), (int)var5);
            this.goalHasY = false;
            Notifications.warn("ElytraBot", "Elytra almost broken – landing here");
         }

         if (this.wantsRestock() && !this.restocking && !this.emergency && rockets() == 0 && var11 > 80.0 && !this.landing && this.now - this.lastRocket > 40) {
            this.restocking = true;
            this.landing = true;
            this.savedGoal = this.goal;
            this.savedGoalHasY = this.goalHasY;
            this.goal = new class_2338((int)var1, this.goal.method_10264(), (int)var5);
            this.goalHasY = false;
            var11 = 0.0;
            Notifications.info("ElytraBot", "Out of rockets – landing to buy more");
         }

         double var23 = this.goalHasY ? this.goal.method_10264() : Double.NaN;
         if (this.now % 5 == 0 || this.goalGroundFor == null || !this.goalGroundFor.equals(this.goal)) {
            this.goalGround = scanGround(this.goal.method_10263() + 0.5, 319.0, this.goal.method_10260() + 0.5);
            this.goalGroundFor = this.goal;
         }

         double var25 = this.goalGround;
         double var27 = !Double.isNaN(var25) ? var25 : (!Double.isNaN(var23) ? var23 : var46);
         double var29 = Math.max(70.0, Math.min(700.0, (var3 - var27) * 9.0 + 40.0));
         this.landing = this.landing
            || this.land.get()
               && var11 <= var29
               && (this.landAtStops.get() || this.lastStop() || this.restocking || this.emergency || var11 < 60.0 && !this.nextStopFar());
         boolean var31 = this.landing && var11 < 30.0 && var3 - var27 > 14.0;
         float var32 = var13;
         if (var31) {
            var32 = var13 + (this.now / 200 % 2 == 0 ? 75.0F : -75.0F);
         }

         double var33 = Math.min(72.0, Math.max(26.0, var16 * 1.4));
         if (this.avoid.get() && this.now % 2 == 0) {
            this.avoidClimb = false;
            float var35 = (float)Math.max(-60.0, Math.min(60.0, (double)var15));
            this.clearAhead = clearDistance(var14, var35, var33);
            if (this.clearAhead < var33 * 0.9) {
               float var36 = Float.NaN;

               for (float var40 : new float[]{25.0F, -25.0F, 50.0F, -50.0F, 80.0F, -80.0F, 120.0F, -120.0F}) {
                  if (clearDistance(var32 + var40, var35, var33) >= var33 * 0.95) {
                     var36 = var32 + var40;
                     break;
                  }
               }

               if (Float.isNaN(var36)) {
                  for (float var55 : new float[]{25.0F, -25.0F, 60.0F, -60.0F, 110.0F, -110.0F}) {
                     if (clearDistance(var14 + var55, var35 - 25.0F, var33) >= var33 * 0.95) {
                        var36 = var14 + var55;
                        break;
                     }
                  }
               }

               if (!Float.isNaN(var36)) {
                  this.avoidYaw = var36;
                  this.avoidTicks = 40;
                  if (this.clearAhead < 22.0) {
                     this.urgentTicks = 14;
                  }
               } else {
                  this.avoidClimb = true;
                  this.avoidTicks = 20;
                  this.urgentTicks = 14;
               }
            }
         }

         if (var16 < 5.0 && !this.landing && this.aboveGround > 3.0) {
            this.stuckTicks++;
         } else {
            this.stuckTicks = Math.max(0, this.stuckTicks - 2);
         }

         if (this.stuckTicks > 30 && this.unstuckTicks <= 0) {
            this.unstuckSide = -this.unstuckSide;
            this.unstuckYaw = var14 + 130.0F * this.unstuckSide;
            this.unstuckTicks = 45;
            this.stuckTicks = 0;
            this.avoidTicks = 0;
            this.avoidClimb = false;
            this.avoidYaw = Float.NaN;
            Notifications.warn("ElytraBot", "Stuck – turning away and diving to get free");
         }

         boolean var47 = this.unstuckTicks > 0;
         if (var47) {
            this.unstuckTicks--;
            var32 = this.unstuckYaw;
         }

         if (this.avoidTicks > 0) {
            this.avoidTicks--;
            if (!Float.isNaN(this.avoidYaw) && !this.avoidClimb) {
               var32 = this.avoidYaw;
            }

            if (this.avoidTicks == 0) {
               this.avoidYaw = Float.NaN;
            }
         }

         boolean var48 = this.mode.get() == ElytraBot.Mode.DIVE && this.useRockets() && !this.landing && var3 < this.cruise - 6.0;
         boolean var50 = this.mode.get() == ElytraBot.Mode.FIREWORK && this.useRockets() || this.mode.get() == ElytraBot.Mode.BOOST && !this.landing || var48;
         float var52;
         String var54;
         if (!this.avoidClimb && (this.avoidTicks <= 0 || !this.avoidClimb)) {
            if (this.landing) {
               double var56 = Math.max(1.0, var11);
               double var42 = Math.toDegrees(Math.atan2(Math.max(0.0, var3 - (var27 + 2.0)), var56 * 0.9));
               var52 = (float)Math.max(4.0, Math.min(38.0, var42));
               var54 = "Landing approach";
               if (var31) {
                  var52 = 24.0F;
                  var54 = "Circling above the target";
               }

               double var44 = this.groundY;
               if (!Double.isNaN(var44) && var3 - var44 < 8.0 && var11 < 60.0) {
                  var52 = (float)Math.max(-14.0, Math.min(10.0, -(var16 - 14.0) * 0.6 + (var3 - var44 - 2.0)));
                  var54 = "Flaring";
               }
            } else if (var3 < this.cruise - 6.0) {
               var54 = "Climbing to " + (int)this.cruise;
               if (var50) {
                  var52 = this.mode.get() == ElytraBot.Mode.BOOST ? -38.0F : -52.0F;
               } else {
                  var52 = this.diveCycle();
               }
            } else if (var50) {
               var52 = (float)Math.max(-22.0, Math.min(22.0, (var3 - this.cruise) * 0.5));
               var54 = "Cruising";
            } else {
               var52 = var3 > this.cruise + 14.0 ? 6.0F : this.diveCycle();
               var54 = "Cruising (dive)";
            }
         } else {
            var52 = var16 < 14.0 ? 12.0F : -50.0F;
            var54 = "Avoiding an obstacle (climbing)";
         }

         boolean var57 = this.selfClimb.get()
            && !this.landing
            && var3 < this.cruise - 6.0
            && this.mode.get() != ElytraBot.Mode.DIVE
            && (this.mode.get() != ElytraBot.Mode.FIREWORK || !this.useRockets());
         if (var47) {
            var57 = false;
            var52 = this.aboveGround > 8.0 ? 32.0F : 8.0F;
            var54 = "Stuck – getting free";
         }

         if (var57) {
            var52 = this.avoidClimb ? -45.0F : -24.0F;
            var54 = (this.avoidClimb ? "Avoiding an obstacle (climbing)" : "Climbing to " + (int)this.cruise) + " (self)";
         }

         boolean var41 = this.mode.get() == ElytraBot.Mode.FIREWORK;
         if ((var41 || var48)
            && this.useRockets()
            && this.now - this.lastRocket >= 12
            && var16 < this.rocketBelow.get().intValue()
            && !this.landing
            && rockets() > this.keepRockets.get()) {
            this.useRocket();
         } else if (var41 && this.landing && !this.restocking && this.now - this.lastRocket >= 12 && var16 < 8.0 && var3 - var46 > 25.0 && rockets() > 0) {
            this.useRocket();
         }

         if (this.mode.get() != ElytraBot.Mode.BOOST || this.landing) {
            this.boost(false);
         } else if (var16 < this.boostMaxSpeed.get().intValue()) {
            this.boost(true);
         } else {
            this.releaseForward();
         }

         int var58 = this.turnSpeed.get();
         if (var47 || this.urgentTicks > 0) {
            var58 = Math.max(var58, 25);
            if (this.urgentTicks > 0) {
               this.urgentTicks--;
            }
         }

         mc.field_1724.method_36456(RotationUtil.approachAngle(var14, var32, var58));
         float var43 = Math.max(3.0F, var58 * 0.9F);
         if (this.mode.get() == ElytraBot.Mode.DIVE && !this.landing && !this.avoidClimb && !var47) {
            var43 = var52 < var15 ? 1.6F : 2.6F;
         }

         if (var47) {
            var43 = 8.0F;
         }

         mc.field_1724.method_36457(RotationUtil.approachAngle(var15, var52, var43));
         if (var57) {
            this.selfClimbTick();
         }

         this.status = var54 + " · " + (int)var11 + "m · " + (int)var3 + "Y";
      }
   }

   private void selfClimbTick() {
      class_243 var1 = mc.field_1724.method_18798();
      double var2 = Math.toRadians(mc.field_1724.method_36454());
      double var4 = -Math.sin(var2);
      double var6 = Math.cos(var2);
      double var8 = this.selfClimbForward.get().intValue() / 20.0;
      double var10 = Math.hypot(var1.field_1352, var1.field_1350);
      double var12 = Math.min(var8, Math.max(var10, 0.35) + 0.08);
      double var14 = var1.field_1352 * 0.35 + var4 * var12 * 0.65;
      double var16 = var1.field_1350 * 0.35 + var6 * var12 * 0.65;
      double var18 = this.selfClimbSpeed.get().intValue() / 20.0;
      double var20 = var1.field_1351 < var18 ? Math.min(var18, var1.field_1351 + 0.09) : var1.field_1351;
      mc.field_1724.method_18800(var14, var20, var16);
   }

   private float diveCycle() {
      double var1 = this.speedNow * 20.0;
      double var3 = 18.0;
      double var5 = 33.0;
      if (this.diving) {
         this.diveTicks++;
         if (var1 > var5 || this.diveTicks > 110 || this.aboveGround < 30.0 && var1 > 24.0) {
            this.diving = false;
         }
      } else if (var1 < var3) {
         this.diving = true;
         this.diveTicks = 0;
      }

      if (this.diving) {
         return this.diveAngle.get().intValue();
      } else {
         double var7 = Math.max(0.2, Math.min(1.0, (var1 - var3) / (var5 - var3)));
         return (float)(-this.climbAngle.get() * var7);
      }
   }

   @Override
   public void onRender3D(Render3D var1) {
      if (this.render.get() && this.goal != null && this.isEnabled()) {
         int var2 = HudManager.accent();
         double var3 = this.goalHasY ? this.goal.method_10264() : mc.field_1724.method_23318();
         var1.boxOutline(
            new class_238(this.goal.method_10263(), var3, this.goal.method_10260(), this.goal.method_10263() + 1, var3 + 2.0, this.goal.method_10260() + 1),
            var2,
            true
         );
         var1.line(
            mc.field_1724.method_23317(),
            mc.field_1724.method_23318() - 0.5,
            mc.field_1724.method_23321(),
            this.goal.method_10263() + 0.5,
            var3 + 1.0,
            this.goal.method_10260() + 0.5,
            var2,
            true
         );
      }
   }

   public class_2338 goal() {
      return this.isEnabled() ? this.goal : null;
   }

   public String label() {
      return this.label;
   }

   public String status() {
      return this.status;
   }

   public double startDistance() {
      return this.startDistance;
   }

   public double distance() {
      return this.horizontal();
   }

   public double speed() {
      return this.speedNow * 20.0;
   }

   public int rocketsLeft() {
      return mc.field_1724 == null ? 0 : rockets();
   }

   public int rocketsUsed() {
      return this.rocketsUsed;
   }

   public double altitude() {
      return mc.field_1724 == null ? 0.0 : mc.field_1724.method_23318();
   }

   public boolean flying() {
      return this.phase == ElytraBot.Phase.FLY;
   }

   public int eta() {
      double var1 = this.speed();
      return var1 < 8.0 ? -1 : (int)(this.horizontal() / var1);
   }

   @Override
   public String getInfo() {
      return this.goal == null ? null : (int)this.horizontal() + "m";
   }

   @Override
   public List<String> details() {
      ArrayList var1 = new ArrayList();
      var1.add("Status: " + this.status);
      if (this.goal != null) {
         var1.add("Target: " + this.label + " · " + (int)this.horizontal() + "m · rockets used " + this.rocketsUsed);
      }

      return var1;
   }

   public static enum Mode {
      DIVE,
      BOOST,
      FIREWORK;
   }

   private static enum Phase {
      PREPARE,
      JUMP,
      RELEASE,
      DEPLOY,
      FLY;
   }
}
