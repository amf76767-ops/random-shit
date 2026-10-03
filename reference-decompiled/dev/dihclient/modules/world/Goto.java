package dev.dihclient.modules.world;

import dev.dihclient.autobuild.Worker;
import dev.dihclient.gui.WaypointsScreen;
import dev.dihclient.hud.HudManager;
import dev.dihclient.module.Category;
import dev.dihclient.module.Module;
import dev.dihclient.module.ModuleManager;
import dev.dihclient.path.Pathfinder;
import dev.dihclient.render.Render3D;
import dev.dihclient.setting.BoolSetting;
import dev.dihclient.setting.DoubleSetting;
import dev.dihclient.setting.IdListSetting;
import dev.dihclient.setting.IntSetting;
import dev.dihclient.setting.StringSetting;
import dev.dihclient.util.HumanAim;
import dev.dihclient.util.InvUtil;
import dev.dihclient.util.KeyUtil;
import dev.dihclient.util.Notifications;
import dev.dihclient.util.RegistryUtil;
import dev.dihclient.waypoint.WaypointManager;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import net.minecraft.class_1268;
import net.minecraft.class_1269;
import net.minecraft.class_1747;
import net.minecraft.class_2248;
import net.minecraft.class_2338;
import net.minecraft.class_2350;
import net.minecraft.class_238;
import net.minecraft.class_243;
import net.minecraft.class_3965;

public class Goto extends Module {
   public final StringSetting target = this.text(
      "Target", "Where to go: \"x y z\" or \"x z\" (~ = your own coordinate). Turning the module on walks there.", "0 0", 64
   );
   public final BoolSetting breakBlocks = this.bool("Break Blocks", "Digs through walls when that is shorter (never next to lava, never chests / doors).", true);
   public final BoolSetting placeBlocks = this.bool("Place Blocks", "Bridges over gaps with blocks from the list below.", true);
   public final IdListSetting bridgeBlocks = this.ids(
         "Bridge Blocks",
         "Blocks used for bridging.",
         IdListSetting.Kind.BLOCK,
         new String[]{
            "minecraft:cobblestone",
            "minecraft:cobbled_deepslate",
            "minecraft:netherrack",
            "minecraft:dirt",
            "minecraft:stone",
            "minecraft:deepslate",
            "minecraft:andesite",
            "minecraft:diorite",
            "minecraft:granite",
            "minecraft:tuff",
            "minecraft:blackstone",
            "minecraft:end_stone"
         }
      )
      .visibleWhen(this.placeBlocks::get);
   public final IdListSetting protect = this.ids(
         "Never Break",
         "Blocks that are never dug through.",
         IdListSetting.Kind.BLOCK,
         new String[]{"minecraft:obsidian", "minecraft:crying_obsidian", "minecraft:glass"}
      )
      .visibleWhen(this.breakBlocks::get);
   public final IntSetting maxFall = this.integer("Max Fall", "Highest drop it walks off (deeper only into water).", 3, 1, 8);
   public final BoolSetting sprint = this.bool("Sprint", "Sprints on straight stretches.", true);
   public final DoubleSetting reach = this.dbl("Reach", "Mining / placing distance.", 4.5, 2.0, 6.0, 0.1);
   public final BoolSetting humanRotations = this.bool(
      "Human Rotations", "Turns your real camera to every block before digging / placing. Off = silent server-side rotations.", true
   );
   public final IntSetting rotateSpeed = this.integer("Rotate Speed", "Maximum head turn per tick in degrees.", 40, 8, 90)
      .visibleWhen(this.humanRotations::get);
   public final IntSetting randomness = this.integer("Randomness", "0 = robot-exact. Higher = more human turning and small pauses.", 20, 0, 100)
      .visibleWhen(this.humanRotations::get);
   public final BoolSetting hammer = this.bool(
      "3x3 Pickaxe",
      "Digs through walls with your 3x3 pickaxe (name set in the \"3x3 Pickaxe\" module) – never where the square would take the floor, Never Break blocks, doors or blocks next to lava.",
      false
   );
   public final BoolSetting render = this.bool("Render Path", "Draws the planned path.", true);
   public final StringSetting route = this.text(
      "Route", "Several stops for Start Route, separated by > or ; – waypoint names or coordinates, e.g. \"home > 100 64 -200 > farm\".", "", 256
   );
   public final BoolSetting loopRoute = this.bool("Loop Route", "Starts the route again from the first stop (patrol).", false);
   private final List<String> legs = new ArrayList<>();
   private int leg;
   private boolean routeActive;
   private final Worker worker = new Worker();
   private class_2338 goal;
   private boolean goalHasY;
   private String label = "";
   private Pathfinder.Result result;
   private int index;
   private int replanIn;
   private int failures;
   private int noProgress;
   private double lastDist = Double.MAX_VALUE;
   private int placeBanUntil;
   private int now;
   private String status = "Idle";
   private boolean presetGoal;
   private final Map<Long, Integer> digTries = new HashMap<>();
   private final Set<Long> avoid = new HashSet<>();
   private class_2338 lastDig;
   private List<class_2338> hammerBreaks = List.of();
   private int doorWait;
   private int mobReplanUntil;
   private double startDistance;
   private boolean sneakHeld;
   private int bridgeWait;
   private final Map<Long, Integer> doorTries = new HashMap<>();
   private class_2338 lastPlace;
   private int placeTicks;
   private Set<String> protectSource;
   private Set<class_2248> protectCache = Set.of();

   public Goto() {
      super("Goto", Category.AUTOMATION, "Walks to coordinates or a waypoint by itself: pathfinding, jumping, swimming, digging through and bridging.");
      this.action("Go", "Walks to the Target coordinates.", () -> {
         this.routeActive = false;
         if (this.isEnabled()) {
            this.setEnabled(false);
         }

         this.setEnabled(true);
      });
      this.action("Start Route", "Walks the Route stop by stop (again and again with Loop Route).", this::startRoute);
      this.action("Stop", "Stops walking.", () -> this.setEnabled(false));
   }

   public static Goto.Target resolve(String var0) {
      String var1 = var0 == null ? "" : var0.trim();
      if (var1.isEmpty()) {
         return null;
      } else {
         String[] var2 = var1.split("\\s+");
         int[] var3 = var2.length >= 2 ? WaypointsScreen.parseCoords(var2, 0) : null;
         if (var3 != null) {
            return new Goto.Target(new class_2338(var3[0], var3[1], var3[2]), var3[3] == 1, var3[0] + " " + (var3[3] == 1 ? var3[1] + " " : "") + var3[2]);
         } else {
            WaypointManager.Waypoint var4 = null;
            String var5 = WaypointManager.dimKey();
            String var6 = var1.toLowerCase(Locale.ROOT);

            for (WaypointManager.Waypoint var8 : WaypointManager.get().here()) {
               boolean var9 = var8.name.equalsIgnoreCase(var1);
               boolean var10 = var8.name.toLowerCase(Locale.ROOT).contains(var6);
               if (var9 && (var4 == null || !var4.name.equalsIgnoreCase(var1) || var5.equals(var8.dim)) || var4 == null && var10) {
                  var4 = var8;
               }
            }

            return var4 == null ? null : new Goto.Target(new class_2338(var4.x, var4.y, var4.z), true, var4.name);
         }
      }
   }

   private void startRoute() {
      this.legs.clear();

      for (String var4 : this.route.get().split("[>;]")) {
         if (!var4.isBlank()) {
            if (resolve(var4) == null) {
               Notifications.warn("Goto", "Route: unknown stop \"" + var4.trim() + "\"");
               return;
            }

            this.legs.add(var4.trim());
         }
      }

      if (this.legs.isEmpty()) {
         Notifications.warn("Goto", "Route is empty – e.g. \"home > 100 64 -200 > farm\"");
      } else {
         this.leg = 0;
         this.beginLeg(true);
      }
   }

   private void beginLeg(boolean var1) {
      Goto.Target var2 = resolve(this.legs.get(this.leg));
      if (var2 == null) {
         Notifications.warn("Goto", "Route: stop \"" + this.legs.get(this.leg) + "\" is gone");
         this.routeActive = false;
         this.setEnabled(false);
      } else {
         this.goal = var2.pos();
         this.goalHasY = var2.hasY();
         this.label = "Route " + (this.leg + 1) + "/" + this.legs.size() + ": " + var2.label();
         this.presetGoal = true;
         if (var1) {
            if (this.isEnabled()) {
               this.setEnabled(false);
            }

            this.presetGoal = true;
            this.setEnabled(true);
         } else {
            this.onEnable();
         }

         this.routeActive = true;
      }
   }

   public void startRouteNow() {
      this.startRoute();
   }

   public boolean routeRunning() {
      return this.routeActive && this.isEnabled();
   }

   public static Goto instance() {
      return ModuleManager.of(Goto.class);
   }

   public static boolean running() {
      Goto var0 = instance();
      return var0 != null && var0.isEnabled();
   }

   public static String statusText() {
      Goto var0 = instance();
      return var0 == null ? "" : var0.label + " – " + var0.status;
   }

   public static void start(int var0, int var1, int var2, String var3) {
      Goto var4 = instance();
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

   public static boolean plannedDrop() {
      Goto var0 = instance();
      if (var0 != null && var0.isEnabled() && var0.result != null && mc.field_1724 != null) {
         List var1 = var0.result.steps();
         if (var0.index < var1.size()) {
            Pathfinder.Step var2 = (Pathfinder.Step)var1.get(var0.index);
            return var2.move() == Pathfinder.Move.DESCEND || var2.pos().method_10264() < feet().method_10264();
         }
      }

      return false;
   }

   public static List<class_2338> bridgeTargets() {
      Goto var0 = instance();
      if (var0 != null && var0.isEnabled() && mc.field_1724 != null) {
         ArrayList var1 = new ArrayList();
         if (var0.result != null) {
            List var2 = var0.result.steps();

            for (int var3 = var0.index; var3 < Math.min(var2.size(), var0.index + 3); var3++) {
               class_2338 var4 = ((Pathfinder.Step)var2.get(var3)).place();
               if (var4 != null && !Pathfinder.isFloor(var4)) {
                  var1.add(var4);
               }
            }
         }

         return var1;
      } else {
         return null;
      }
   }

   public static boolean holdingSneak() {
      Goto var0 = instance();
      return var0 != null && var0.isEnabled() && var0.sneakHeld;
   }

   private static boolean smartBridgeOn() {
      return ModuleManager.on(SmartBridge.class);
   }

   private void holdSneak(boolean var1) {
      if (mc.field_1690 != null) {
         if (var1) {
            mc.field_1690.field_1832.method_23481(true);
            this.sneakHeld = true;
         } else if (this.sneakHeld) {
            mc.field_1690.field_1832.method_23481(KeyUtil.isPhysicallyDown(mc.field_1690.field_1832));
            this.sneakHeld = false;
         }
      }
   }

   public static void stop() {
      Goto var0 = instance();
      if (var0 != null) {
         var0.setEnabled(false);
      }
   }

   @Override
   protected void onEnable() {
      if (!inGame()) {
         this.presetGoal = false;
         this.setEnabledSilently(false);
      } else {
         this.digTries.clear();
         this.avoid.clear();
         this.lastDig = null;
         this.doorWait = 0;
         this.doorTries.clear();
         this.bridgeWait = 0;
         this.lastPlace = null;
         this.placeTicks = 0;
         if (!this.presetGoal) {
            int[] var1 = WaypointsScreen.parseCoords(this.target.get().trim().split("\\s+"), 0);
            if (var1 == null) {
               Notifications.warn("Goto", "Target must be \"x y z\" or \"x z\"");
               this.setEnabledSilently(false);
               return;
            }

            this.goal = new class_2338(var1[0], var1[1], var1[2]);
            this.goalHasY = var1[3] == 1;
            this.label = var1[0] + " " + (this.goalHasY ? var1[1] + " " : "") + var1[2];
         }

         this.presetGoal = false;
         this.result = null;
         this.index = 0;
         this.replanIn = 0;
         this.failures = 0;
         this.noProgress = 0;
         this.lastDist = Double.MAX_VALUE;
         this.placeBanUntil = 0;
         this.status = "Planning";
         this.mobReplanUntil = 0;
         this.startDistance = Math.max(1.0, (double)this.remaining());
         Notifications.info("Goto", "Walking to " + this.label);
      }
   }

   private void releaseKeys() {
      this.worker.release();
      this.holdSneak(false);
      if (mc.field_1690 != null) {
         mc.field_1690.field_1894.method_23481(KeyUtil.isPhysicallyDown(mc.field_1690.field_1894));
         mc.field_1690.field_1903.method_23481(KeyUtil.isPhysicallyDown(mc.field_1690.field_1903));
      }
   }

   @Override
   protected void onDisable() {
      this.routeActive = false;
      this.worker.reset();
      this.releaseKeys();
      if (mc.field_1724 != null) {
         mc.field_1724.method_5728(false);
      }

      this.result = null;
      this.status = "Idle";
   }

   @Override
   public void onWorldChange() {
      if (this.isEnabled()) {
         this.setEnabled(false);
      }
   }

   private Set<class_2248> protectBlocks() {
      Set var1 = this.protect.get();
      if (var1 != this.protectSource) {
         this.protectCache = RegistryUtil.blocks(var1);
         this.protectSource = var1;
      }

      return this.protectCache;
   }

   private int bridgeCount() {
      Set var1 = RegistryUtil.blocks(this.bridgeBlocks.get());
      boolean var2 = this.placeBlocks.get();
      boolean var3 = smartBridgeOn();
      return InvUtil.count(
         var3x -> var3x.method_7909() instanceof class_1747 var4
            && (var2 && var1.contains(var4.method_7711()) || var3 && SmartBridge.placeableBlock(var4.method_7711()))
      );
   }

   private static class_2338 feet() {
      class_2338 var0 = new class_2338(
         (int)Math.floor(mc.field_1724.method_23317()), (int)Math.floor(mc.field_1724.method_23318() + 0.001), (int)Math.floor(mc.field_1724.method_23321())
      );
      return Pathfinder.isPassable(var0) ? var0 : var0.method_10084();
   }

   private void plan() {
      class_2338 var1 = feet();
      Pathfinder.Options var2 = new Pathfinder.Options();
      var2.allowBreak = this.breakBlocks.get();
      boolean var3 = smartBridgeOn();
      var2.allowPlace = (this.placeBlocks.get() || var3) && this.now >= this.placeBanUntil;
      var2.placeBlocks = var2.allowPlace ? this.bridgeCount() : 0;
      var2.maxFall = SafeRoute.maxFall(this.maxFall.get());
      var2.protect = this.protectBlocks();
      var2.avoid = this.avoid;
      this.result = Pathfinder.find(var1, this.goal, this.goalHasY, var2, 40000, 45L);
      this.index = 0;
      this.noProgress = 0;
      this.lastDist = Double.MAX_VALUE;
      if (this.result.steps().isEmpty()) {
         this.failures++;
         this.status = "No path found (" + this.failures + "/5)";
         this.replanIn = 40;
         if (this.failures >= 5) {
            Notifications.warn("Goto", "No way to " + this.label + " – stopped");
            this.setEnabled(false);
         }
      } else {
         this.status = this.result.complete() ? "Walking" : "Walking (partial path, " + this.result.steps().size() + " steps)";
      }
   }

   @Override
   public void onTick() {
      if (inGame() && this.goal != null) {
         this.now++;
         if (mc.field_1755 != null) {
            this.releaseKeys();
         } else {
            this.worker.guard = false;
            this.worker.human = this.humanRotations.get();
            this.worker.speed = this.rotateSpeed.get().intValue();
            this.worker.humanizer.amount = this.randomness.get().intValue() / 100.0;
            this.worker.hammer = this.hammer.get();
            Set var1 = this.protectBlocks();
            int var2 = feet().method_10264();
            HashSet var3 = new HashSet();
            var3.add(feet().method_10074());
            if (this.result != null) {
               List var4 = this.result.steps();

               for (int var5 = this.index; var5 < Math.min(var4.size(), this.index + 8); var5++) {
                  var3.add(((Pathfinder.Step)var4.get(var5)).pos().method_10074());
               }
            }

            this.worker.hammerAllowed = var3x -> var3x.method_10264() >= var2
               && !var3.contains(var3x)
               && !var1.contains(mc.field_1687.method_8320(var3x).method_26204())
               && !noDig(RegistryUtil.blockId(mc.field_1687.method_8320(var3x)))
               && !Pathfinder.falling(RegistryUtil.blockId(mc.field_1687.method_8320(var3x.method_10084())));
            this.worker.hammerWanted = var1x -> this.hammerBreaks.contains(var1x);
            class_2338 var16 = feet();
            if (Pathfinder.reached(var16, this.goal, this.goalHasY)) {
               Notifications.push("Goto", "Arrived at " + this.label, Notifications.Type.SUCCESS);
               if (!this.routeActive || this.legs.isEmpty() || this.leg + 1 >= this.legs.size() && !this.loopRoute.get()) {
                  this.routeActive = false;
                  this.setEnabled(false);
               } else {
                  this.leg = (this.leg + 1) % this.legs.size();
                  this.beginLeg(false);
               }
            } else if (this.worker.busy() && this.worker.tickPlacing(this.reach.get())) {
               this.placeTicks++;
               this.status = "Bridging";
            } else if (this.worker.mining() != null && this.worker.mine(this.worker.mining(), this.reach.get())) {
               this.status = "Digging";
            } else {
               boolean var17 = mc.field_1724.method_24828() || mc.field_1724.method_5799() || mc.field_1724.method_18798().field_1351 > -0.3;
               if (this.result != null && this.replanIn == 0 && var17 && this.now % 40 == 0 && this.now >= this.mobReplanUntil) {
                  ArrayList var6 = new ArrayList();
                  List var7 = this.result.steps();

                  for (int var8 = this.index; var8 < Math.min(var7.size(), this.index + 14); var8++) {
                     var6.add(((Pathfinder.Step)var7.get(var8)).pos());
                  }

                  if (SafeRoute.mobNearPath(var6)) {
                     this.status = "SafeRoute: mob on the path – replanning";
                     SafeRoute.note("Goto: mob on the path – new route");
                     this.mobReplanUntil = this.now + 100;
                     this.result = null;
                     return;
                  }
               }

               if (this.result == null || this.replanIn > 0) {
                  if (this.replanIn > 0) {
                     this.replanIn--;
                     this.worker.release();
                     return;
                  }

                  if (!var17) {
                     return;
                  }

                  this.plan();
                  if (this.result == null || this.result.steps().isEmpty()) {
                     return;
                  }
               }

               List var18 = this.result.steps();

               for (int var19 = Math.min(var18.size() - 1, this.index + 6); var19 >= this.index; var19--) {
                  if (((Pathfinder.Step)var18.get(var19)).pos().equals(var16)
                     && (this.straight(var18, var19) || this.nearCentre(((Pathfinder.Step)var18.get(var19)).pos(), 0.4))) {
                     this.index = var19 + 1;
                     this.failures = 0;
                     this.lastDist = Double.MAX_VALUE;
                     this.noProgress = 0;
                     break;
                  }
               }

               if (this.index >= var18.size()) {
                  this.result = null;
               } else {
                  Pathfinder.Step var20 = (Pathfinder.Step)var18.get(this.index);
                  class_243 var21 = class_243.method_24955(var20.pos());
                  double var9 = Math.hypot(var21.field_1352 - mc.field_1724.method_23317(), var21.field_1350 - mc.field_1724.method_23321());
                  if (var9 > 3.5 && var17) {
                     this.result = null;
                  } else if (this.doorWait > 0) {
                     this.doorWait--;
                  } else if (!this.openDoor(var20.pos()) && !this.openDoor(var20.pos().method_10084())) {
                     this.hammerBreaks = var20.breaks();

                     for (class_2338 var12 : var20.breaks()) {
                        if (!Pathfinder.isPassable(var12)) {
                           this.releaseKeys();
                           mc.field_1724.method_5728(false);
                           this.status = "Digging";
                           if (!var12.equals(this.lastDig)) {
                              this.lastDig = var12.method_10062();
                              int var13 = this.digTries.merge(var12.method_10063(), 1, Integer::sum);
                              if (var13 > 3) {
                                 this.avoid.add(var12.method_10063());
                                 this.fail("Can't break a block – going around");
                                 return;
                              }
                           }

                           if (!this.worker.mine(var12, this.reach.get())) {
                              this.lastDig = null;
                              this.result = null;
                              this.replanIn = 5;
                           }

                           return;
                        }
                     }

                     this.lastDig = null;
                     if (var20.place() != null && !Pathfinder.isFloor(var20.place()) && smartBridgeOn() && this.bridgeWait < 100) {
                        this.bridgeWait++;
                        mc.field_1724.method_5728(false);
                        this.holdSneak(true);
                        if (SmartBridge.busy()) {
                           this.worker.release();
                           this.status = "Bridging (SmartBridge)";
                        } else {
                           this.worker.walkTo(var21, 0.2);
                           mc.field_1690.field_1903.method_23481(KeyUtil.isPhysicallyDown(mc.field_1690.field_1903));
                           mc.field_1690.field_1913.method_23481(KeyUtil.isPhysicallyDown(mc.field_1690.field_1913));
                           this.status = "Bridging (SmartBridge) – waiting for the block";
                        }
                     } else if (var20.place() != null && !Pathfinder.isFloor(var20.place())) {
                        this.holdSneak(false);
                        this.worker.release();
                        mc.field_1724.method_5728(false);
                        Set var23 = RegistryUtil.blocks(this.bridgeBlocks.get());
                        boolean var24 = smartBridgeOn();
                        this.status = "Bridging";
                        if (!var20.place().equals(this.lastPlace)) {
                           this.lastPlace = var20.place().method_10062();
                           this.placeTicks = 0;
                        }

                        if (++this.placeTicks > 200) {
                           this.lastPlace = null;
                           this.placeBanUntil = this.now + 400;
                           this.fail("Block won't stay – going around");
                        } else {
                           if (!this.worker
                              .place(
                                 var20.place(),
                                 var2x -> var2x instanceof class_1747 var3x
                                    && (var23.contains(var3x.method_7711()) || var24 && SmartBridge.placeableBlock(var3x.method_7711())),
                                 this.reach.get()
                              )) {
                              this.placeBanUntil = this.now + 400;
                              this.fail("Can't bridge here – going around");
                           }
                        }
                     } else {
                        this.bridgeWait = 0;
                        this.holdSneak(false);
                        double var22 = var21.method_1022(mc.field_1724.method_73189());
                        if (var22 < this.lastDist - 0.05) {
                           this.lastDist = var22;
                           this.noProgress = 0;
                        } else if (++this.noProgress > 60) {
                           this.failures++;
                           this.status = "Stuck – replanning";
                           this.result = null;
                           this.replanIn = 5;
                           if (this.failures >= 6) {
                              Notifications.warn("Goto", "Stuck on the way to " + this.label + " – stopped");
                              this.setEnabled(false);
                           }

                           return;
                        }

                        boolean var25 = this.worker.walkTo(var21, 0.2);
                        boolean var14 = mc.field_1724.method_5799();
                        if (var14 && var20.pos().method_10264() >= var16.method_10264()) {
                           mc.field_1690.field_1903.method_23481(true);
                        }

                        boolean var15 = this.sprint.get()
                           && var25
                           && !var14
                           && this.straight(var18, this.index)
                           && (var20.move() == Pathfinder.Move.WALK || var20.move() == Pathfinder.Move.DIAGONAL)
                           && var20.breaks().isEmpty()
                           && var20.place() == null;
                        mc.field_1724.method_5728(var15);
                        this.status = this.result.complete() ? "Walking" : "Walking (partial path)";
                     }
                  }
               }
            }
         }
      }
   }

   private boolean straight(List<Pathfinder.Step> var1, int var2) {
      if (var2 + 1 < var1.size() && var2 >= 1) {
         class_2338 var3 = ((Pathfinder.Step)var1.get(var2 - 1)).pos();
         class_2338 var4 = ((Pathfinder.Step)var1.get(var2)).pos();
         class_2338 var5 = ((Pathfinder.Step)var1.get(var2 + 1)).pos();
         return var4.method_10263() - var3.method_10263() == var5.method_10263() - var4.method_10263()
            && var4.method_10260() - var3.method_10260() == var5.method_10260() - var4.method_10260();
      } else {
         return var2 + 1 >= var1.size();
      }
   }

   private static boolean noDig(String var0) {
      return var0.contains("door") || var0.contains("fence_gate") || var0.endsWith("_bed");
   }

   private boolean nearCentre(class_2338 var1, double var2) {
      return Math.hypot(var1.method_10263() + 0.5 - mc.field_1724.method_23317(), var1.method_10260() + 0.5 - mc.field_1724.method_23321()) < var2;
   }

   private void fail(String var1) {
      this.failures++;
      this.status = var1;
      this.result = null;
      this.replanIn = 5;
      if (this.failures >= 8) {
         Notifications.warn("Goto", "Can't get to " + this.label + " – stopped");
         this.setEnabled(false);
      }
   }

   private boolean openDoor(class_2338 var1) {
      if ((Pathfinder.classify(var1) & 128) == 0) {
         return false;
      } else {
         this.releaseKeys();
         mc.field_1724.method_5728(false);
         this.status = "Opening door";
         class_243 var2 = class_243.method_24953(var1);
         class_2350 var3 = class_2350.method_58251(mc.field_1724.method_33571().method_1020(var2));
         if (this.humanRotations.get() && !HumanAim.stepTo(var2, this.rotateSpeed.get().intValue(), 3.0F, this.worker.humanizer.noise())) {
            return true;
         } else if (this.doorTries.merge(var1.method_10063(), 1, Integer::sum) > 4) {
            this.avoid.add(var1.method_10063());
            this.fail("Door won't open – going around");
            return true;
         } else {
            class_1269 var4 = mc.field_1761.method_2896(mc.field_1724, class_1268.field_5808, new class_3965(var2, var3, var1, false));
            if (var4.method_23665()) {
               mc.field_1724.method_6104(class_1268.field_5808);
            }

            this.doorWait = 10;
            return true;
         }
      }
   }

   @Override
   public void onRender3D(Render3D var1) {
      if (this.goal != null && this.isEnabled()) {
         int var2 = HudManager.accent();
         if (this.render.get() && this.result != null) {
            List var3 = this.result.steps();
            class_243 var4 = null;

            for (int var5 = Math.max(0, this.index - 1); var5 < var3.size(); var5++) {
               class_243 var6 = class_243.method_24955(((Pathfinder.Step)var3.get(var5)).pos()).method_1031(0.0, 0.1, 0.0);
               if (var4 != null) {
                  var1.line(var4, var6, var2, false);
               }

               var4 = var6;
            }
         }

         double var7 = this.goalHasY ? this.goal.method_10264() : mc.field_1724.method_23318();
         var1.boxOutline(
            new class_238(this.goal.method_10263(), var7, this.goal.method_10260(), this.goal.method_10263() + 1, var7 + 2.0, this.goal.method_10260() + 1),
            var2,
            true
         );
      }
   }

   public class_2338 goal() {
      return this.isEnabled() ? this.goal : null;
   }

   public boolean goalHasY() {
      return this.goalHasY;
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

   public double pathRemaining() {
      if (mc.field_1724 == null || this.goal == null) {
         return 0.0;
      } else if (this.result != null && !this.result.steps().isEmpty()) {
         List var1 = this.result.steps();
         double var2 = 0.0;
         class_243 var4 = mc.field_1724.method_73189();

         for (int var5 = this.index; var5 < var1.size(); var5++) {
            class_243 var6 = class_243.method_24955(((Pathfinder.Step)var1.get(var5)).pos());
            var2 += Math.sqrt(var6.method_1025(var4));
            var4 = var6;
         }

         if (!this.result.complete()) {
            var2 += Math.hypot(this.goal.method_10263() + 0.5 - var4.field_1352, this.goal.method_10260() + 0.5 - var4.field_1350) * 1.2;
         }

         return var2;
      } else {
         return this.remaining() * 1.2;
      }
   }

   private int remaining() {
      return mc.field_1724 != null && this.goal != null
         ? (int)Math.hypot(this.goal.method_10263() + 0.5 - mc.field_1724.method_23317(), this.goal.method_10260() + 0.5 - mc.field_1724.method_23321())
         : 0;
   }

   @Override
   public String getInfo() {
      return this.goal == null ? null : this.remaining() + "m";
   }

   @Override
   public List<String> details() {
      ArrayList var1 = new ArrayList();
      var1.add("Status: " + this.status);
      if (this.goal != null) {
         var1.add("Target: " + this.label + " · " + this.remaining() + "m left");
      }

      return var1;
   }

   public record Target(class_2338 pos, boolean hasY, String label) {
   }
}
