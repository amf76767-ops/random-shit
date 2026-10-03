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
import dev.dihclient.util.JunkUtil;
import dev.dihclient.util.Notifications;
import dev.dihclient.util.RegistryUtil;
import dev.dihclient.util.WalkSafety;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import net.minecraft.class_1297;
import net.minecraft.class_1542;
import net.minecraft.class_2246;
import net.minecraft.class_2248;
import net.minecraft.class_2338;
import net.minecraft.class_2350;
import net.minecraft.class_238;
import net.minecraft.class_243;
import net.minecraft.class_2680;
import net.minecraft.class_3959;
import net.minecraft.class_3965;
import net.minecraft.class_2338.class_2339;
import net.minecraft.class_239.class_240;
import net.minecraft.class_3959.class_242;
import net.minecraft.class_3959.class_3960;

public class AutoMine extends Module {
   private static final class_2350[] ALL = new class_2350[]{
      class_2350.field_11033, class_2350.field_11036, class_2350.field_11043, class_2350.field_11035, class_2350.field_11039, class_2350.field_11034
   };
   public final IdListSetting blocks = this.ids(
         "Blocks",
         "What to mine.",
         IdListSetting.Kind.BLOCK,
         new String[]{
            "minecraft:diamond_ore",
            "minecraft:deepslate_diamond_ore",
            "minecraft:ancient_debris",
            "minecraft:emerald_ore",
            "minecraft:deepslate_emerald_ore",
            "minecraft:gold_ore",
            "minecraft:deepslate_gold_ore",
            "minecraft:iron_ore",
            "minecraft:deepslate_iron_ore",
            "minecraft:lapis_ore",
            "minecraft:deepslate_lapis_ore",
            "minecraft:redstone_ore",
            "minecraft:deepslate_redstone_ore"
         }
      )
      .onChange(this::rebuild);
   public final IdListSetting protect = this.ids(
         "Never Break",
         "Blocks that are never dug through. An ore behind one of these is skipped.",
         IdListSetting.Kind.BLOCK,
         new String[]{
            "minecraft:chest",
            "minecraft:trapped_chest",
            "minecraft:barrel",
            "minecraft:ender_chest",
            "minecraft:spawner",
            "minecraft:obsidian",
            "minecraft:crying_obsidian",
            "minecraft:budding_amethyst",
            "minecraft:reinforced_deepslate"
         }
      )
      .onChange(this::rebuild);
   public final IntSetting radius = this.integer("Search Radius", "How far around you ores are searched.", 24, 6, 48);
   public final DoubleSetting reach = this.dbl("Reach", "Mining distance.", 4.5, 2.0, 6.0, 0.1);
   public final BoolSetting exposedOnly = this.bool(
      "Only Exposed", "Only goes for ores that touch air or water (cave ores). Use this on servers with anti-xray – hidden ores there are fake.", false
   );
   public final BoolSetting vein = this.bool("Vein Mine", "After an ore, mines the rest of the vein first.", true);
   public final BoolSetting avoidLava = this.bool("Avoid Lava", "Never breaks a block that touches lava.", true);
   public final BoolSetting collect = this.bool("Collect Drops", "Walks over the drops after mining an ore.", true);
   public final IntSetting minHealth = this.integer("Min Health", "Turns off when your health drops to this (hearts × 2). 0 = never.", 8, 0, 19);
   public final BoolSetting dropJunk = this.bool(
      "Drop Junk", "When the inventory is full, throws stone, dirt, netherrack … away instead of stopping (keeps 1 stack(s) for building).", true
   );
   public final IdListSetting junk = this.ids(
         "Junk",
         "Items Drop Junk throws away.",
         IdListSetting.Kind.ITEM,
         new String[]{
            "minecraft:cobblestone",
            "minecraft:cobbled_deepslate",
            "minecraft:netherrack",
            "minecraft:dirt",
            "minecraft:gravel",
            "minecraft:andesite",
            "minecraft:diorite",
            "minecraft:granite",
            "minecraft:tuff",
            "minecraft:calcite",
            "minecraft:blackstone",
            "minecraft:basalt",
            "minecraft:smooth_basalt",
            "minecraft:flint",
            "minecraft:rotten_flesh"
         }
      )
      .visibleWhen(this.dropJunk::get);
   public final IntSetting toolSaver = this.integer(
      "Tool Saver", "Never uses a tool with less durability left; swaps in a fresh pickaxe from the inventory or stops (0 = off).", 8, 0, 200
   );
   public final BoolSetting stopWhenFull = this.bool("Stop When Full", "Turns off when your inventory is full.", true);
   public final IntSetting timeout = this.integer("Give Up After", "Seconds to try one ore before it is skipped.", 40, 10, 180);
   public final BoolSetting humanRotations = this.bool(
      "Human Rotations", "Turns your real camera to every block before digging, like a player. Off = silent server-side rotations.", true
   );
   public final IntSetting rotateSpeed = this.integer("Rotate Speed", "Maximum head turn per tick in degrees.", 36, 8, 90)
      .visibleWhen(this.humanRotations::get);
   public final IntSetting randomness = this.integer(
         "Randomness", "0 = robot-exact. Higher = turn speed varies, aims at random spots and takes small pauses.", 25, 0, 100
      )
      .visibleWhen(this.humanRotations::get);
   public final BoolSetting hammer = this.bool(
      "3x3 Pickaxe",
      "Uses your 3x3 pickaxe (name set in the \"3x3 Pickaxe\" module) where the whole square is safe: nothing you stand on, no lava, no protected blocks, no sand / gravel over your head. Elsewhere it takes a normal pickaxe.",
      false
   );
   public final BoolSetting render = this.bool("Render", "Shows the ore it is going for and the next blocks to dig.", true);
   private final Worker worker = new Worker();
   private Set<class_2248> targets = Set.of();
   private Set<class_2248> protectedBlocks = Set.of();
   private List<class_2338> found = new ArrayList<>();
   private List<class_2338> candidates = new ArrayList<>();
   private class_2338 scanOrigin;
   private int scanIndex;
   private int scanR;
   private int scanH;
   private final Map<class_2338, Integer> blacklist = new HashMap<>();
   private final Map<class_1297, Integer> ignoredItems = new WeakHashMap<>();
   private class_2338 target;
   private int targetTicks;
   private class_2338 lastMined;
   private int collectUntil;
   private class_1297 collecting;
   private final Map<class_1297, Integer> collectTime = new WeakHashMap<>();
   private class_243 lastPos;
   private int idleTicks;
   private final List<class_2338> nextDig = new ArrayList<>();
   private class_2338 stepFloor;
   private String status = "Idle";
   private int mined;
   private int now;

   public AutoMine() {
      super("AutoMine", Category.AUTOMATION, "Finds ores near you, tunnels to them and mines the whole vein. Stays away from lava.");
   }

   private void rebuild() {
      this.targets = RegistryUtil.blocks(this.blocks.get());
      this.protectedBlocks = RegistryUtil.blocks(this.protect.get());
   }

   @Override
   protected void onEnable() {
      this.rebuild();
      this.found = new ArrayList<>();
      this.candidates = new ArrayList<>();
      this.scanOrigin = null;
      this.blacklist.clear();
      this.ignoredItems.clear();
      this.target = null;
      this.lastMined = null;
      this.stepFloor = null;
      this.nextDig.clear();
      this.collectUntil = 0;
      this.collectTime.clear();
      this.mined = 0;
      this.status = "Searching";
   }

   @Override
   protected void onDisable() {
      this.worker.reset();
      this.target = null;
      this.nextDig.clear();
      this.status = "Idle";
   }

   @Override
   public void onWorldChange() {
      if (this.isEnabled()) {
         this.setEnabled(false);
      }
   }

   private class_2680 state(class_2338 var1) {
      return mc.field_1687.method_8320(var1);
   }

   private boolean isTarget(class_2338 var1) {
      return this.targets.contains(this.state(var1).method_26204());
   }

   private boolean solid(class_2338 var1) {
      class_2680 var2 = this.state(var1);
      return !var2.method_26215() && (var2.method_26227().method_15769() || !var2.method_45474()) && !var2.method_26218(mc.field_1687, var1).method_1110();
   }

   private boolean lava(class_2338 var1) {
      return this.state(var1).method_27852(class_2246.field_10164);
   }

   private boolean touchesLava(class_2338 var1) {
      for (class_2350 var5 : ALL) {
         if (this.lava(var1.method_10093(var5))) {
            return true;
         }
      }

      return false;
   }

   private boolean exposed(class_2338 var1) {
      for (class_2350 var5 : ALL) {
         class_2680 var6 = this.state(var1.method_10093(var5));
         if (var6.method_26215() || !var6.method_26227().method_15769()) {
            return true;
         }
      }

      return false;
   }

   private boolean safe(class_2338 var1) {
      class_2680 var2 = this.state(var1);
      if (var2.method_26214(mc.field_1687, var1) < 0.0F || this.protectedBlocks.contains(var2.method_26204())) {
         return false;
      } else if (this.supports(var1)) {
         return false;
      } else {
         return this.inMyColumn(var1) && falling(RegistryUtil.blockId(this.state(var1.method_10084())))
            ? false
            : !this.avoidLava.get() || !this.touchesLava(var1) && !this.lava(var1);
      }
   }

   private boolean supports(class_2338 var1) {
      class_238 var2 = mc.field_1724.method_5829();
      int var3 = (int)Math.floor(var2.field_1322 - 0.001);
      if (var1.method_10264() != var3 && var1.method_10264() != var3 - 1) {
         return false;
      } else {
         int var4 = (int)Math.floor(var2.field_1323);
         int var5 = (int)Math.floor(var2.field_1320 - 1.0E-4);
         int var6 = (int)Math.floor(var2.field_1321);
         int var7 = (int)Math.floor(var2.field_1324 - 1.0E-4);
         return var1.method_10263() >= var4 && var1.method_10263() <= var5 && var1.method_10260() >= var6 && var1.method_10260() <= var7;
      }
   }

   private boolean inMyColumn(class_2338 var1) {
      class_238 var2 = mc.field_1724.method_5829();
      return var1.method_10263() >= (int)Math.floor(var2.field_1323)
         && var1.method_10263() <= (int)Math.floor(var2.field_1320 - 1.0E-4)
         && var1.method_10260() >= (int)Math.floor(var2.field_1321)
         && var1.method_10260() <= (int)Math.floor(var2.field_1324 - 1.0E-4)
         && var1.method_10264() >= (int)Math.floor(var2.field_1322);
   }

   private static boolean falling(String var0) {
      return var0.equals("minecraft:sand")
         || var0.equals("minecraft:red_sand")
         || var0.equals("minecraft:gravel")
         || var0.equals("minecraft:suspicious_sand")
         || var0.equals("minecraft:suspicious_gravel")
         || var0.endsWith("_concrete_powder")
         || var0.endsWith("anvil")
         || var0.equals("minecraft:pointed_dripstone");
   }

   private boolean blacklisted(class_2338 var1) {
      Integer var2 = this.blacklist.get(var1);
      return var2 != null && var2 > this.now;
   }

   private void skip(String var1) {
      if (this.target != null) {
         this.blacklist.put(this.target, this.now + 6000);
      }

      this.status = var1;
      this.target = null;
      this.worker.reset();
      this.nextDig.clear();
      this.stepFloor = null;
   }

   private void scanStep() {
      if (this.scanOrigin == null) {
         this.scanOrigin = mc.field_1724.method_24515();
         this.scanR = this.radius.get();
         this.scanH = Math.min(this.scanR, 16);
         this.scanIndex = 0;
         this.found = new ArrayList<>();
      }

      int var1 = this.scanR * 2 + 1;
      int var2 = this.scanH * 2 + 1;
      int var3 = var1 * var1 * var2;
      int var4 = Math.min(var3, this.scanIndex + 12000);
      class_2339 var5 = new class_2339();

      for (int var6 = this.scanIndex; var6 < var4; var6++) {
         int var7 = var6 % var1 - this.scanR;
         int var8 = var6 / var1 % var1 - this.scanR;
         int var9 = var6 / (var1 * var1) - this.scanH;
         var5.method_10103(this.scanOrigin.method_10263() + var7, this.scanOrigin.method_10264() + var9, this.scanOrigin.method_10260() + var8);
         if (this.targets.contains(mc.field_1687.method_8320(var5).method_26204())) {
            this.found.add(var5.method_10062());
            if (this.found.size() > 4096) {
               var4 = var3;
               break;
            }
         }
      }

      this.scanIndex = var4;
      if (this.scanIndex >= var3) {
         this.candidates = this.found;
         this.scanOrigin = null;
      }
   }

   private class_2338 pickTarget() {
      class_243 var1 = mc.field_1724.method_33571();
      class_2338 var2 = null;
      double var3 = Double.MAX_VALUE;

      for (class_2338 var6 : this.candidates) {
         if (!this.blacklisted(var6)
            && this.isTarget(var6)
            && (!this.exposedOnly.get() || this.exposed(var6))
            && (!this.avoidLava.get() || !this.touchesLava(var6))) {
            class_243 var7 = class_243.method_24953(var6);
            double var8 = var7.method_1022(var1) + Math.abs(var7.field_1351 - var1.field_1351) * 0.5;
            if (this.vein.get() && this.lastMined != null && var6.method_10262(this.lastMined) <= 3.0) {
               var8 -= 1000.0;
            }

            if (var8 < var3) {
               var3 = var8;
               var2 = var6;
            }
         }
      }

      return var2;
   }

   @Override
   public void onTick() {
      if (inGame()) {
         this.now++;
         if (mc.field_1755 != null) {
            this.worker.release();
         } else if (this.minHealth.get() > 0 && mc.field_1724.method_6032() <= this.minHealth.get().intValue()) {
            Notifications.warn("AutoMine", "Health low – stopped");
            this.setEnabled(false);
         } else {
            Worker.toolSaver = Math.max(1, this.toolSaver.get());
            if (this.toolSaver.get() > 0 && this.now % 20 == 0 && !JunkUtil.ensurePickaxe(this.toolSaver.get(), this.hammer.get())) {
               Notifications.warn("AutoMine", "Tool Saver: no pickaxe with more than " + this.toolSaver.get() + " durability – stopped");
               this.setEnabled(false);
            } else if (this.dropJunk.get() && mc.field_1724.method_31548().method_7376() < 0 && JunkUtil.drop(this.junk.get(), 2, 1) > 0) {
               this.worker.release();
               this.status = "Dropping junk";
            } else if (this.stopWhenFull.get() && mc.field_1724.method_31548().method_7376() < 0) {
               Notifications.warn("AutoMine", "Inventory full – stopped after " + this.mined + " ores");
               this.setEnabled(false);
            } else {
               this.worker.human = this.humanRotations.get();
               this.worker.speed = this.rotateSpeed.get().intValue();
               this.worker.humanizer.amount = this.randomness.get().intValue() / 100.0;
               this.worker.hammer = this.hammer.get();
               this.worker.hammerAllowed = var1x -> this.safe(var1x) && !var1x.equals(this.stepFloor);
               this.worker.hammerWanted = var1x -> this.isTarget(var1x) || this.solid(var1x);
               this.scanStep();
               double var1 = this.reach.get();
               if (this.worker.mining() != null) {
                  class_2338 var3 = this.worker.mining();
                  if (this.worker.mine(var3, var1)) {
                     this.idleTicks = 0;
                     return;
                  }

                  if (this.target != null && this.state(this.target).method_26215()) {
                     this.onMined(this.target);
                  }
               }

               if (!this.collect.get() || this.now >= this.collectUntil || !this.tickCollect()) {
                  if (this.target != null && !this.isTarget(this.target)) {
                     this.target = null;
                  }

                  if (this.target == null) {
                     this.target = this.pickTarget();
                     this.targetTicks = 0;
                     this.idleTicks = 0;
                     if (this.target == null) {
                        this.worker.release();
                        this.nextDig.clear();
                        this.status = this.candidates.isEmpty() ? "No ores in range" : "Searching";
                        return;
                     }
                  }

                  if (++this.targetTicks > this.timeout.get() * 20) {
                     this.skip("Gave up on an ore");
                  } else {
                     class_243 var7 = mc.field_1724.method_73189();
                     if (this.lastPos != null && var7.method_1025(this.lastPos) < 1.0E-4) {
                        if (++this.idleTicks > 200) {
                           this.skip("Stuck – skipped an ore");
                           return;
                        }
                     } else {
                        this.idleTicks = 0;
                     }

                     this.lastPos = var7;
                     class_243 var4 = class_243.method_24953(this.target);
                     if (var4.method_1022(mc.field_1724.method_33571()) <= var1) {
                        class_2338 var5 = this.obstruction(this.target);
                        boolean var6 = this.supports(this.target);
                        if (var5 != null || !var6 || this.solid(this.target.method_10074()) && !this.lava(this.target.method_10074())) {
                           if (var5 == null && !this.safe(this.target)) {
                              var5 = this.target;
                           } else if (var5 == null) {
                              this.nextDig.clear();
                              this.status = "Mining " + RegistryUtil.pretty(RegistryUtil.blockId(this.state(this.target)));
                              this.worker.release();
                              if (!this.worker.mine(this.target, var1)) {
                                 this.skip("Can't mine that one");
                              }

                              return;
                           }
                        } else {
                           var5 = this.target;
                        }

                        if (!var5.equals(this.target) && this.safe(var5)) {
                           this.nextDig.clear();
                           this.nextDig.add(var5);
                           this.status = "Clearing the way";
                           this.worker.release();
                           if (!this.worker.mine(var5, var1)) {
                              this.skip("Blocked");
                           }

                           return;
                        }
                     }

                     this.move();
                  }
               }
            }
         }
      }
   }

   private void onMined(class_2338 var1) {
      this.mined++;
      this.lastMined = var1.method_10062();
      this.target = null;
      this.collectUntil = this.now + 60;
   }

   private class_2338 obstruction(class_2338 var1) {
      class_243 var2 = mc.field_1724.method_33571();
      class_3965 var3 = mc.field_1687
         .method_17742(new class_3959(var2, class_243.method_24953(var1), class_3960.field_17559, class_242.field_1348, mc.field_1724));
      if (var3 != null && var3.method_17783() == class_240.field_1332) {
         class_2338 var4 = var3.method_17777();
         return var4.equals(var1) ? null : var4.method_10062();
      } else {
         return null;
      }
   }

   private void move() {
      class_2338 var1 = mc.field_1724.method_24515();
      int var2 = this.target.method_10263() - var1.method_10263();
      int var3 = this.target.method_10264() - var1.method_10264();
      int var4 = this.target.method_10260() - var1.method_10260();
      ArrayList var5 = new ArrayList(2);
      if (var2 == 0 && var4 == 0) {
         var5.add(mc.field_1724.method_5735());
      } else if (Math.abs(var2) >= Math.abs(var4)) {
         var5.add(var2 > 0 ? class_2350.field_11034 : class_2350.field_11039);
         if (var4 != 0) {
            var5.add(var4 > 0 ? class_2350.field_11035 : class_2350.field_11043);
         }
      } else {
         var5.add(var4 > 0 ? class_2350.field_11035 : class_2350.field_11043);
         if (var2 != 0) {
            var5.add(var2 > 0 ? class_2350.field_11034 : class_2350.field_11039);
         }
      }

      if (var3 >= 2 || var3 <= -2) {
         for (class_2350 var9 : new class_2350[]{class_2350.field_11043, class_2350.field_11035, class_2350.field_11039, class_2350.field_11034}) {
            if (!var5.contains(var9)) {
               var5.add(var9);
            }
         }
      }

      for (class_2350 var11 : var5) {
         AutoMine.Step var12 = this.plan(var1, var11, var3);
         if (var12 != null) {
            this.run(var12);
            return;
         }
      }

      this.skip("No safe way there");
   }

   private AutoMine.Step plan(class_2338 var1, class_2350 var2, int var3) {
      class_2338 var4 = var1.method_10093(var2);
      ArrayList var5 = new ArrayList(3);
      class_2338 var6;
      String var7;
      if (var3 <= -2) {
         var5.add(var4.method_10084());
         var5.add(var4);
         var5.add(var4.method_10074());
         var6 = var4.method_10074();
         var7 = "Digging down";
      } else if (var3 >= 2) {
         if (!this.solid(var4)) {
            return null;
         }

         var5.add(var1.method_10086(2));
         var5.add(var4.method_10084());
         var5.add(var4.method_10086(2));
         var6 = var4.method_10084();
         var7 = "Digging up";
      } else {
         var5.add(var4.method_10084());
         var5.add(var4);
         var6 = var4;
         var7 = "Tunneling";
      }

      for (class_2338 var9 : var5) {
         if (this.solid(var9) && !this.safe(var9)) {
            return null;
         }

         if (this.lava(var9)) {
            return null;
         }
      }

      int var10 = 0;

      for (class_2338 var11 = var6.method_10074(); var10 < 5 && !this.solid(var11); var11 = var11.method_10074()) {
         if (this.lava(var11)) {
            return null;
         }

         var10++;
      }

      return var10 >= 4 ? null : new AutoMine.Step(var5, class_243.method_24955(var6), var7);
   }

   private void run(AutoMine.Step var1) {
      this.nextDig.clear();
      this.stepFloor = class_2338.method_49638(var1.dest()).method_10074();

      for (class_2338 var3 : var1.dig()) {
         if (this.solid(var3)) {
            this.nextDig.add(var3);
         }
      }

      if (!this.nextDig.isEmpty()) {
         class_2338 var4 = this.nextDig.get(0);
         this.status = var1.label();
         this.worker.release();
         if (!this.worker.mine(var4, this.reach.get())) {
            this.skip("Blocked");
         }
      } else {
         this.status = "Walking";
         this.worker.walkTo(var1.dest(), 0.25);
      }
   }

   private boolean tickCollect() {
      class_243 var1 = mc.field_1724.method_73189();
      class_1542 var2 = null;
      double var3 = 25.0;

      for (class_1297 var6 : mc.field_1687.method_18112()) {
         Integer var7 = this.ignoredItems.get(var6);
         if (var6 instanceof class_1542 var8 && WalkSafety.worthCollecting(var8.method_6983()) && (var7 == null || var7 <= this.now)) {
            double var9 = var8.method_23317() - var1.field_1352;
            double var11 = var8.method_23318() - var1.field_1351;
            double var13 = var8.method_23321() - var1.field_1350;
            double var15 = var9 * var9 + var13 * var13;
            if (Math.abs(var11) < 2.0 && var15 > 0.5 && var15 < var3) {
               var3 = var15;
               var2 = var8;
            }
         }
      }

      if (var2 == null) {
         this.collecting = null;
         return false;
      } else {
         this.collecting = var2;
         int var17 = this.collectTime.merge(var2, 1, Integer::sum);
         class_243 var18 = new class_243(var2.method_23317(), var2.method_23318(), var2.method_23321());
         if (var17 <= 80 && WalkSafety.safeToward(var18)) {
            this.status = "Collecting drops";
            this.collectUntil = Math.max(this.collectUntil, this.now + 5);
            this.worker.walkTo(var18, 0.3);
            return true;
         } else {
            this.ignoredItems.put(var2, this.now + 1200);
            this.collecting = null;
            this.worker.release();
            return false;
         }
      }
   }

   @Override
   public void onRender3D(Render3D var1) {
      if (this.render.get()) {
         if (this.target != null) {
            var1.box(new class_238(this.target), HudManager.accent(), 60, true);
         }

         for (class_2338 var3 : this.nextDig) {
            var1.boxOutline(new class_238(var3), -49088, false);
         }
      }
   }

   @Override
   public String getInfo() {
      return this.mined > 0 ? String.valueOf(this.mined) : null;
   }

   @Override
   public List<String> details() {
      ArrayList var1 = new ArrayList();
      var1.add("Status: " + this.status);
      var1.add("Mined: " + this.mined + " · ores found: " + this.candidates.size());
      if (this.target != null) {
         class_243 var2 = class_243.method_24953(this.target);
         var1.add(
            "Target: " + RegistryUtil.pretty(RegistryUtil.blockId(this.state(this.target))) + " · " + (int)var2.method_1022(mc.field_1724.method_33571()) + "m"
         );
      }

      return var1;
   }

   private record Step(List<class_2338> dig, class_243 dest, String label) {
   }
}
