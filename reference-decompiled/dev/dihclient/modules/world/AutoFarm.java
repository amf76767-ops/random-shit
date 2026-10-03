package dev.dihclient.modules.world;

import dev.dihclient.autobuild.Worker;
import dev.dihclient.hud.HudManager;
import dev.dihclient.module.Category;
import dev.dihclient.module.Module;
import dev.dihclient.render.Render3D;
import dev.dihclient.setting.BoolSetting;
import dev.dihclient.setting.DoubleSetting;
import dev.dihclient.setting.IdListSetting;
import dev.dihclient.setting.IntSetting;
import dev.dihclient.util.HumanAim;
import dev.dihclient.util.InvUtil;
import dev.dihclient.util.ItemUtil;
import dev.dihclient.util.RegistryUtil;
import dev.dihclient.util.RotationUtil;
import dev.dihclient.util.WalkSafety;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.Map.Entry;
import java.util.function.Predicate;
import net.minecraft.class_1268;
import net.minecraft.class_1269;
import net.minecraft.class_1297;
import net.minecraft.class_1542;
import net.minecraft.class_1799;
import net.minecraft.class_2248;
import net.minecraft.class_2338;
import net.minecraft.class_2350;
import net.minecraft.class_238;
import net.minecraft.class_243;
import net.minecraft.class_2680;
import net.minecraft.class_3965;
import net.minecraft.class_2828.class_2831;

public class AutoFarm extends Module {
   private static final String[] DEFAULT_CROPS = new String[]{
      "minecraft:wheat",
      "minecraft:carrots",
      "minecraft:potatoes",
      "minecraft:beetroots",
      "minecraft:nether_wart",
      "minecraft:cocoa",
      "minecraft:melon",
      "minecraft:pumpkin",
      "minecraft:sugar_cane",
      "minecraft:sweet_berry_bush",
      "minecraft:cave_vines",
      "minecraft:cave_vines_plant"
   };
   private static final String[] FALLBACK_SEEDS = new String[]{"minecraft:wheat_seeds", "minecraft:carrot", "minecraft:potato", "minecraft:beetroot_seeds"};
   private static final class_2350[] HORIZONTAL = new class_2350[]{
      class_2350.field_11043, class_2350.field_11035, class_2350.field_11039, class_2350.field_11034
   };
   public final IdListSetting crops = this.ids("Crops", "Which plants are harvested.", IdListSetting.Kind.BLOCK, DEFAULT_CROPS);
   public final DoubleSetting reach = this.dbl("Reach", "Harvest / plant distance.", 4.5, 2.0, 6.0, 0.1);
   public final BoolSetting replant = this.bool("Replant", "Plants the matching seed right after harvesting.", true);
   public final BoolSetting fillFarmland = this.bool(
      "Fill Empty Farmland", "Also plants on empty farmland – uses the crop growing next to it, otherwise the first seed you carry.", true
   );
   public final BoolSetting boneMeal = this.bool("Bone Meal", "Uses bone meal on crops that are still growing.", false);
   public final BoolSetting collect = this.bool("Collect Drops", "Walks over dropped items near you to pick them up.", true);
   public final IntSetting collectRadius = this.integer("Collect Radius", "How far to walk for dropped items.", 6, 2, 16).visibleWhen(this.collect::get);
   public final BoolSetting walk = this.bool("Walk To Crops", "Walks to grown crops that are out of reach.", true);
   public final IntSetting scanRadius = this.integer("Scan Radius", "How far to look for grown crops when walking.", 16, 4, 48).visibleWhen(this.walk::get);
   public final BoolSetting noTrample = this.bool("No Trample", "Never jumps while standing on farmland, so the field doesn't get trampled.", true);
   public final IntSetting delay = this.integer("Action Delay", "Ticks to wait after every harvest / plant.", 1, 0, 20);
   public final BoolSetting humanRotations = this.bool(
      "Human Rotations", "Turns your real camera to every crop before clicking, like a player. Off = silent server-side rotations.", true
   );
   public final IntSetting rotateSpeed = this.integer("Rotate Speed", "Maximum head turn per tick in degrees.", 40, 8, 90)
      .visibleWhen(this.humanRotations::get);
   public final IntSetting randomness = this.integer(
         "Randomness", "0 = robot-exact. Higher = turn speed varies, aims at random spots and takes small pauses.", 25, 0, 100
      )
      .visibleWhen(this.humanRotations::get);
   public final BoolSetting render = this.bool("Render", "Highlights the crop being worked on.", true);
   private final Worker worker = new Worker();
   private final Map<class_2338, AutoFarm.Replant> replants = new HashMap<>();
   private final Map<class_2338, Integer> ignored = new HashMap<>();
   private final Map<class_1297, Integer> ignoredItems = new WeakHashMap<>();
   private class_1297 collecting;
   private final Map<class_1297, Integer> collectTime = new WeakHashMap<>();
   private AutoFarm.Use use;
   private class_2338 walkTarget;
   private int walkTicks;
   private int cooldown;
   private int scanTimer;
   private final List<class_2338> farTargets = new ArrayList<>();
   private class_2338 focus;
   private String status = "Idle";
   private int harvested;
   private int now;
   private static final IdentityHashMap<class_2248, String> IDS = new IdentityHashMap<>();

   public AutoFarm() {
      super("AutoFarm", Category.AUTOMATION, "Harvests grown crops around you, replants them, uses bone meal and picks up the drops.");
   }

   @Override
   protected void onEnable() {
      this.replants.clear();
      this.ignored.clear();
      this.ignoredItems.clear();
      this.collectTime.clear();
      this.farTargets.clear();
      this.use = null;
      this.walkTarget = null;
      this.cooldown = 0;
      this.scanTimer = 0;
      this.harvested = 0;
      this.status = "Idle";
   }

   @Override
   protected void onDisable() {
      this.worker.reset();
      this.use = null;
      this.focus = null;
      this.status = "Idle";
   }

   @Override
   public void onWorldChange() {
      this.replants.clear();
      this.ignored.clear();
      this.farTargets.clear();
      this.worker.reset();
      this.use = null;
   }

   private static boolean has(String var0) {
      return InvUtil.findInventory(var1 -> ItemUtil.id(var1).equals(var0)) >= 0;
   }

   private static String id(class_2680 var0) {
      return IDS.computeIfAbsent(var0.method_26204(), RegistryUtil::blockId);
   }

   private static String seedFor(String var0) {
      return switch (var0) {
         case "minecraft:wheat" -> "minecraft:wheat_seeds";
         case "minecraft:carrots" -> "minecraft:carrot";
         case "minecraft:potatoes" -> "minecraft:potato";
         case "minecraft:beetroots" -> "minecraft:beetroot_seeds";
         case "minecraft:nether_wart" -> "minecraft:nether_wart";
         case "minecraft:cocoa" -> "minecraft:cocoa_beans";
         default -> null;
      };
   }

   private static String cropForSeed(String var0) {
      return switch (var0) {
         case "minecraft:wheat_seeds" -> "minecraft:wheat";
         case "minecraft:carrot" -> "minecraft:carrots";
         case "minecraft:potato" -> "minecraft:potatoes";
         case "minecraft:beetroot_seeds" -> "minecraft:beetroots";
         default -> null;
      };
   }

   private static int maxAge(String var0) {
      return switch (var0) {
         case "minecraft:wheat", "minecraft:carrots", "minecraft:potatoes" -> 7;
         case "minecraft:beetroots", "minecraft:nether_wart", "minecraft:sweet_berry_bush" -> 3;
         case "minecraft:cocoa" -> 2;
         default -> -1;
      };
   }

   private static boolean byUse(String var0) {
      return var0.equals("minecraft:sweet_berry_bush") || var0.equals("minecraft:cave_vines") || var0.equals("minecraft:cave_vines_plant");
   }

   private boolean harvestable(class_2338 var1, class_2680 var2) {
      String var3 = id(var2);
      if (!this.crops.contains(var3)) {
         return false;
      } else {
         switch (var3) {
            case "minecraft:melon":
            case "minecraft:pumpkin":
               String var6 = var3.equals("minecraft:melon") ? "minecraft:attached_melon_stem" : "minecraft:attached_pumpkin_stem";

               for (class_2350 var10 : HORIZONTAL) {
                  if (id(mc.field_1687.method_8320(var1.method_10093(var10))).equals(var6)) {
                     return true;
                  }
               }

               return false;
            case "minecraft:sugar_cane":
               return id(mc.field_1687.method_8320(var1.method_10074())).equals(var3)
                  && !id(mc.field_1687.method_8320(var1.method_10074().method_10074())).equals(var3);
            case "minecraft:sweet_berry_bush":
               return ItemUtil.intProp(var2, "age", 0) >= 2;
            case "minecraft:cave_vines":
            case "minecraft:cave_vines_plant":
               return "true".equals(ItemUtil.prop(var2, "berries"));
            default:
               int var7 = maxAge(var3);
               return var7 > 0 && ItemUtil.intProp(var2, "age", 0) >= var7;
         }
      }
   }

   private boolean boneMealable(class_2680 var1) {
      String var2 = id(var1);
      if (this.crops.contains(var2) && !var2.equals("minecraft:nether_wart")) {
         int var3 = maxAge(var2);
         return var3 > 0 && ItemUtil.intProp(var1, "age", var3) < var3;
      } else {
         return false;
      }
   }

   private boolean releaseThen() {
      this.worker.release();
      return true;
   }

   private static class_2350 dirNamed(String var0) {
      if (var0 == null) {
         return null;
      } else {
         return switch (var0) {
            case "north" -> class_2350.field_11043;
            case "south" -> class_2350.field_11035;
            case "west" -> class_2350.field_11039;
            case "east" -> class_2350.field_11034;
            default -> null;
         };
      }
   }

   private boolean inReach(class_2338 var1) {
      return class_243.method_24953(var1).method_1022(mc.field_1724.method_33571()) <= this.reach.get();
   }

   private boolean ignoredNow(class_2338 var1) {
      Integer var2 = this.ignored.get(var1);
      return var2 != null && var2 > this.now;
   }

   private void ignore(class_2338 var1, int var2) {
      this.ignored.put(var1.method_10062(), this.now + var2);
   }

   @Override
   public void onTick() {
      if (inGame()) {
         this.now++;
         if (mc.field_1755 != null) {
            this.worker.release();
         } else {
            this.worker.human = this.humanRotations.get();
            this.worker.speed = this.rotateSpeed.get().intValue();
            this.worker.humanizer.amount = this.randomness.get().intValue() / 100.0;
            double var1 = this.reach.get();
            if (this.now % 100 == 0) {
               this.ignored.values().removeIf(var1x -> var1x <= this.now);
            }

            if (this.cooldown > 0) {
               this.cooldown--;
               this.worker.release();
            } else if (this.worker.busy() && this.worker.tickPlacing(var1)) {
               this.status = "Planting";
            } else if (this.use == null || !this.tickUse()) {
               if (this.worker.mining() != null) {
                  if (this.worker.mine(this.worker.mining(), var1)) {
                     this.status = "Harvesting";
                  } else {
                     this.harvested++;
                     this.cooldown = this.delay.get();
                  }
               } else if (!this.replant.get() || !this.tickReplant(var1)) {
                  if (!this.tickHarvest(var1)) {
                     if (!this.boneMeal.get() || !this.tickBoneMeal(var1)) {
                        if (!this.fillFarmland.get() || !this.tickFill(var1)) {
                           if (!this.collect.get() || !this.tickCollect()) {
                              if (!this.walk.get() || !this.tickWalk(var1)) {
                                 this.focus = null;
                                 this.worker.release();
                                 this.status = mc.field_1724.method_31548().method_7376() < 0 ? "Waiting for crops (inventory full)" : "Waiting for crops";
                              }
                           }
                        }
                     }
                  }
               }
            }
         }
      }
   }

   private boolean tickReplant(double var1) {
      Iterator var3 = this.replants.entrySet().iterator();

      while (var3.hasNext()) {
         Entry var4 = (Entry)var3.next();
         class_2338 var5 = (class_2338)var4.getKey();
         AutoFarm.Replant var6 = (AutoFarm.Replant)var4.getValue();
         class_2680 var7 = mc.field_1687.method_8320(var5);
         if (this.now - var6.since() > 600) {
            var3.remove();
         } else if (!var7.method_26215()) {
            if (this.now - var6.since() > 40) {
               var3.remove();
            }
         } else if (this.inReach(var5)) {
            String var8 = seedFor(var6.block());
            if (var8 == null) {
               var3.remove();
            } else if (!has(var8)) {
               this.status = "No " + RegistryUtil.pretty(var8) + " to replant";
            } else if (var6.block().equals("minecraft:cocoa")) {
               class_2350 var9 = dirNamed(ItemUtil.prop(var6.state(), "facing"));
               class_2338 var10 = var9 == null ? null : var5.method_10093(var9);
               if (var10 != null && RegistryUtil.blockId(mc.field_1687.method_8320(var10)).contains("jungle_")) {
                  if (this.inReach(var10)) {
                     this.focus = var5;
                     this.status = "Replanting cocoa";
                     this.use = new AutoFarm.Use(AutoFarm.UseKind.COCOA, var10, var9.method_10153(), var8, var5.method_10062(), this.now);
                     return this.tickUse();
                  }
               } else {
                  var3.remove();
               }
            } else if (this.releaseThen() && this.worker.place(var5, var1x -> ItemUtil.id(var1x).equals(var8), var1)) {
               this.focus = var5;
               this.status = "Replanting";
               return true;
            }
         }
      }

      return false;
   }

   private boolean tickHarvest(double var1) {
      class_2338 var3 = this.nearest(var1, true);
      if (var3 == null) {
         return false;
      } else {
         class_2680 var4 = mc.field_1687.method_8320(var3);
         String var5 = id(var4);
         this.focus = var3;
         if (byUse(var5)) {
            this.use = new AutoFarm.Use(AutoFarm.UseKind.BERRY, var3.method_10062(), null, null, var3.method_10062(), this.now);
            this.status = "Picking berries";
            return this.tickUse();
         } else {
            if (this.replant.get() && seedFor(var5) != null) {
               class_2680 var6 = var5.equals("minecraft:cocoa") ? var4 : ItemUtil.block(var5).method_9564();
               this.replants.put(var3.method_10062(), new AutoFarm.Replant(var5, var6, this.now));
            }

            this.worker.release();
            if (this.worker.mine(var3, var1)) {
               this.status = "Harvesting";
               return true;
            } else {
               this.ignore(var3, 100);
               return false;
            }
         }
      }
   }

   private boolean tickBoneMeal(double var1) {
      if (!has("minecraft:bone_meal")) {
         return false;
      } else {
         class_2338 var3 = this.nearest(var1, false);
         if (var3 == null) {
            return false;
         } else {
            this.focus = var3;
            this.use = new AutoFarm.Use(AutoFarm.UseKind.BONE_MEAL, var3.method_10062(), null, "minecraft:bone_meal", var3.method_10062(), this.now);
            this.status = "Bone meal";
            return this.tickUse();
         }
      }
   }

   private boolean tickFill(double var1) {
      int var3 = (int)Math.ceil(var1);
      class_2338 var4 = mc.field_1724.method_24515();
      class_243 var5 = mc.field_1724.method_33571();
      class_2338 var6 = null;
      double var7 = Double.MAX_VALUE;

      for (class_2338 var10 : class_2338.method_10097(var4.method_10069(-var3, -2, -var3), var4.method_10069(var3, 1, var3))) {
         if (id(mc.field_1687.method_8320(var10)).equals("minecraft:farmland")) {
            class_2338 var11 = var10.method_10084();
            if (mc.field_1687.method_8320(var11).method_26215() && !this.ignoredNow(var11) && !this.replants.containsKey(var11)) {
               double var12 = class_243.method_24953(var11).method_1025(var5);
               if (var12 <= var1 * var1 && var12 < var7) {
                  var7 = var12;
                  var6 = var11.method_10062();
               }
            }
         }
      }

      if (var6 == null) {
         return false;
      } else {
         String var14 = this.seedForEmpty(var6);
         if (var14 == null) {
            this.ignore(var6, 200);
            return false;
         } else if (this.releaseThen() && this.worker.place(var6, var1x -> ItemUtil.id(var1x).equals(var14), var1)) {
            this.focus = var6;
            this.status = "Planting";
            if (!this.worker.busy()) {
               this.cooldown = this.delay.get();
            }

            return true;
         } else {
            this.ignore(var6, 100);
            return false;
         }
      }
   }

   private String seedForEmpty(class_2338 var1) {
      for (class_2350 var5 : HORIZONTAL) {
         String var6 = seedFor(id(mc.field_1687.method_8320(var1.method_10093(var5))));
         if (var6 != null && cropForSeed(var6) != null && has(var6)) {
            return var6;
         }
      }

      for (String var10 : FALLBACK_SEEDS) {
         if (this.crops.contains(cropForSeed(var10)) && has(var10)) {
            return var10;
         }
      }

      return null;
   }

   private boolean tickCollect() {
      class_243 var1 = mc.field_1724.method_73189();
      double var2 = this.collectRadius.get() * this.collectRadius.get();
      class_1542 var4 = null;
      double var5 = Double.MAX_VALUE;

      for (class_1297 var8 : mc.field_1687.method_18112()) {
         Integer var9 = this.ignoredItems.get(var8);
         if (var8 instanceof class_1542 var10
            && WalkSafety.worthCollecting(var10.method_6983())
            && (var9 == null || var9 <= this.now)
            && fits(var10.method_6983())) {
            double var11 = var10.method_23317() - var1.field_1352;
            double var13 = var10.method_23318() - var1.field_1351;
            double var15 = var10.method_23321() - var1.field_1350;
            double var17 = var11 * var11 + var15 * var15;
            if (Math.abs(var13) < 2.5 && var17 < var2 && var17 > 0.5 && var17 < var5) {
               var5 = var17;
               var4 = var10;
            }
         }
      }

      if (var4 == null) {
         this.collecting = null;
         return false;
      } else {
         this.collecting = var4;
         int var19 = this.collectTime.merge(var4, 1, Integer::sum);
         class_243 var20 = new class_243(var4.method_23317(), var4.method_23318(), var4.method_23321());
         if (var19 <= 100 && WalkSafety.safeToward(var20)) {
            this.focus = null;
            this.status = "Collecting drops";
            this.walkSafely(var20, 0.3);
            return true;
         } else {
            this.ignoredItems.put(var4, this.now + 1200);
            this.collecting = null;
            this.worker.release();
            return false;
         }
      }
   }

   private static boolean fits(class_1799 var0) {
      if (mc.field_1724.method_31548().method_7376() >= 0) {
         return true;
      } else {
         for (int var1 = 0; var1 < 36; var1++) {
            class_1799 var2 = mc.field_1724.method_31548().method_5438(var1);
            if (var2.method_31574(var0.method_7909()) && var2.method_7947() < 64) {
               return true;
            }
         }

         return false;
      }
   }

   private boolean tickWalk(double var1) {
      if (this.scanTimer-- <= 0) {
         this.scanTimer = 10;
         this.scanFar();
      }

      if (this.walkTarget != null
         && (this.walkTicks++ > 300 || !this.harvestable(this.walkTarget, mc.field_1687.method_8320(this.walkTarget)) || this.ignoredNow(this.walkTarget))) {
         if (this.walkTicks > 300) {
            this.ignore(this.walkTarget, 1200);
         }

         this.walkTarget = null;
      }

      if (this.walkTarget == null) {
         class_243 var3 = mc.field_1724.method_73189();
         double var4 = Double.MAX_VALUE;

         for (class_2338 var7 : this.farTargets) {
            if (!this.ignoredNow(var7) && this.harvestable(var7, mc.field_1687.method_8320(var7))) {
               double var8 = class_243.method_24953(var7).method_1025(var3);
               if (var8 < var4) {
                  var4 = var8;
                  this.walkTarget = var7;
               }
            }
         }

         this.walkTicks = 0;
      }

      if (this.walkTarget == null) {
         return false;
      } else {
         this.focus = this.walkTarget;
         this.status = "Walking to crops";
         if (!this.walkSafely(class_243.method_24953(this.walkTarget), Math.max(0.5, var1 - 2.0))) {
            if (!this.inReach(this.walkTarget)) {
               this.ignore(this.walkTarget, 600);
            }

            this.walkTarget = null;
         }

         return true;
      }
   }

   private void scanFar() {
      this.farTargets.clear();
      int var1 = this.scanRadius.get();
      class_2338 var2 = mc.field_1724.method_24515();

      for (class_2338 var4 : class_2338.method_10097(var2.method_10069(-var1, -4, -var1), var2.method_10069(var1, 4, var1))) {
         class_2680 var5 = mc.field_1687.method_8320(var4);
         if (!var5.method_26215() && this.harvestable(var4, var5)) {
            this.farTargets.add(var4.method_10062());
            if (this.farTargets.size() > 512) {
               break;
            }
         }
      }
   }

   private boolean walkSafely(class_243 var1, double var2) {
      boolean var4 = this.worker.walkTo(var1, var2);
      if (var4 && this.noTrample.get() && this.nearFarmland()) {
         mc.field_1690.field_1903.method_23481(false);
      }

      return var4;
   }

   private boolean nearFarmland() {
      class_2338 var1 = mc.field_1724.method_24515();

      for (class_2338 var3 : class_2338.method_10097(var1.method_10069(-1, -1, -1), var1.method_10069(1, 0, 1))) {
         if (id(mc.field_1687.method_8320(var3)).equals("minecraft:farmland")) {
            return true;
         }
      }

      return false;
   }

   private class_2338 nearest(double var1, boolean var3) {
      int var4 = (int)Math.ceil(var1);
      class_2338 var5 = mc.field_1724.method_24515();
      class_243 var6 = mc.field_1724.method_33571();
      class_2338 var7 = null;
      double var8 = Double.MAX_VALUE;

      for (class_2338 var11 : class_2338.method_10097(var5.method_10069(-var4, -2, -var4), var5.method_10069(var4, var4, var4))) {
         class_2680 var12 = mc.field_1687.method_8320(var11);
         if (!var12.method_26215() && !this.ignoredNow(var11) && (var3 ? this.harvestable(var11, var12) : this.boneMealable(var12))) {
            double var13 = class_243.method_24953(var11).method_1025(var6);
            if (var13 <= var1 * var1 && var13 < var8) {
               var8 = var13;
               var7 = var11.method_10062();
            }
         }
      }

      return var7;
   }

   private boolean tickUse() {
      AutoFarm.Use var1 = this.use;
      class_2680 var2 = mc.field_1687.method_8320(var1.spot());

      boolean var3 = switch (var1.kind()) {
         case BERRY -> this.harvestable(var1.spot(), var2);
         case BONE_MEAL -> this.boneMealable(var2);
         case COCOA -> var2.method_26215();
      };
      if (this.now - var1.started() <= 60 && var3 && this.inReach(var1.pos())) {
         if (var1.item() != null) {
            int var4 = InvUtil.findHotbar((Predicate<class_1799>)(var1x -> ItemUtil.id(var1x).equals(var1.item())));
            if (var4 < 0) {
               int var5 = InvUtil.findInventory(var1x -> ItemUtil.id(var1x).equals(var1.item()));
               if (var5 < 9) {
                  this.use = null;
                  return false;
               }

               var4 = InvUtil.firstEmptyHotbar() >= 0 ? InvUtil.firstEmptyHotbar() : InvUtil.selectedSlot();
               InvUtil.swapToHotbar(var5, var4);
            }

            InvUtil.select(var4);
         } else if (ItemUtil.id(mc.field_1724.method_6047()).equals("minecraft:bone_meal")) {
            int var10 = InvUtil.findHotbar((Predicate<class_1799>)(var0 -> !ItemUtil.id(var0).equals("minecraft:bone_meal")));
            InvUtil.select(var10 >= 0 ? var10 : (InvUtil.firstEmptyHotbar() >= 0 ? InvUtil.firstEmptyHotbar() : (InvUtil.selectedSlot() + 1) % 9));
         }

         class_243 var11 = class_243.method_24953(var1.pos());
         class_2350 var12 = var1.face() != null ? var1.face() : class_2350.method_58251(mc.field_1724.method_33571().method_1020(var11));
         class_243 var6 = var11.method_1031(var12.method_10148() * 0.45, var12.method_10164() * 0.45, var12.method_10165() * 0.45);
         this.worker.release();
         if (this.humanRotations.get()) {
            if (!HumanAim.stepTo(var6, this.rotateSpeed.get().intValue(), 2.5F, this.worker.humanizer.noise())) {
               return true;
            }

            this.click(new class_3965(var6, var12, var1.pos(), false));
         } else {
            float var7 = mc.field_1724.method_36454();
            float var8 = mc.field_1724.method_36455();
            float[] var9 = RotationUtil.rotationsTo(var6);
            mc.field_1724.method_36456(var9[0]);
            mc.field_1724.method_36457(var9[1]);
            mc.field_1724.field_3944.method_52787(new class_2831(var9[0], var9[1], mc.field_1724.method_24828(), mc.field_1724.field_5976));
            this.click(new class_3965(var6, var12, var1.pos(), false));
            mc.field_1724.method_36456(var7);
            mc.field_1724.method_36457(var8);
         }

         if (var1.kind() == AutoFarm.UseKind.BERRY) {
            this.harvested++;
         } else if (var1.kind() == AutoFarm.UseKind.COCOA) {
            this.replants.remove(var1.spot());
         }

         this.use = null;
         this.cooldown = Math.max(this.delay.get(), var1.kind() == AutoFarm.UseKind.BONE_MEAL ? 3 : 0);
         return true;
      } else {
         if (var3) {
            this.ignore(var1.pos(), 100);
         }

         this.use = null;
         return false;
      }
   }

   private void click(class_3965 var1) {
      class_1269 var2 = mc.field_1761.method_2896(mc.field_1724, class_1268.field_5808, var1);
      if (var2.method_23665()) {
         mc.field_1724.method_6104(class_1268.field_5808);
      }
   }

   @Override
   public void onRender3D(Render3D var1) {
      if (this.render.get() && this.focus != null) {
         var1.box(new class_238(this.focus), HudManager.accent(), 50, false);
      }
   }

   @Override
   public String getInfo() {
      return this.harvested > 0 ? String.valueOf(this.harvested) : null;
   }

   @Override
   public List<String> details() {
      ArrayList var1 = new ArrayList();
      var1.add("Status: " + this.status);
      var1.add("Harvested: " + this.harvested + " · waiting to replant: " + this.replants.size());
      return var1;
   }

   private record Replant(String block, class_2680 state, int since) {
   }

   private record Use(AutoFarm.UseKind kind, class_2338 pos, class_2350 face, String item, class_2338 spot, int started) {
   }

   private static enum UseKind {
      BERRY,
      BONE_MEAL,
      COCOA;
   }
}
