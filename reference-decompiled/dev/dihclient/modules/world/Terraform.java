package dev.dihclient.modules.world;

import dev.dihclient.autobuild.PlacementSolver;
import dev.dihclient.autobuild.Worker;
import dev.dihclient.hud.HudManager;
import dev.dihclient.module.Category;
import dev.dihclient.module.Module;
import dev.dihclient.render.Render3D;
import dev.dihclient.setting.BoolSetting;
import dev.dihclient.setting.DoubleSetting;
import dev.dihclient.setting.EnumSetting;
import dev.dihclient.setting.IdListSetting;
import dev.dihclient.setting.IntSetting;
import dev.dihclient.util.HumanAim;
import dev.dihclient.util.InvUtil;
import dev.dihclient.util.KeyUtil;
import dev.dihclient.util.Notifications;
import dev.dihclient.util.RegistryUtil;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import net.minecraft.class_1747;
import net.minecraft.class_1792;
import net.minecraft.class_2248;
import net.minecraft.class_2338;
import net.minecraft.class_2350;
import net.minecraft.class_238;
import net.minecraft.class_243;
import net.minecraft.class_2680;
import net.minecraft.class_3965;
import net.minecraft.class_2338.class_2339;
import net.minecraft.class_2902.class_2903;

public class Terraform extends Module {
   public final EnumSetting<Terraform.Area> area = this.mode(
      "Area", "Radius: square around where you enable it. Selection: between Pos 1 and Pos 2.", Terraform.Area.RADIUS
   );
   public final IntSetting radius = this.integer("Radius", "Half size of the square.", 8, 1, 48).visibleWhen(() -> this.area.get() == Terraform.Area.RADIUS);
   public final IntSetting yOffset = this.integer("Y Offset", "Target surface relative to your feet when enabling (-1 = the block you stand on).", -1, -30, 30);
   public final BoolSetting remove = this.bool("Remove Above", "Mines everything above the surface.", true);
   public final IntSetting removeHeight = this.integer("Remove Height", "How far above the surface to clear.", 32, 1, 128).visibleWhen(this.remove::get);
   public final BoolSetting fill = this.bool("Fill Below", "Fills holes, water and lava up to the surface.", true);
   public final IntSetting fillDepth = this.integer("Fill Depth", "How deep holes get filled.", 3, 1, 32).visibleWhen(this.fill::get);
   public final BoolSetting fluids = this.bool("Remove Fluids", "Water / lava above the surface: blocks the source, then mines it away.", true);
   public final IdListSetting fillers = this.ids(
      "Filler Blocks",
      "Blocks used for filling.",
      IdListSetting.Kind.BLOCK,
      new String[]{
         "minecraft:dirt",
         "minecraft:cobblestone",
         "minecraft:stone",
         "minecraft:cobbled_deepslate",
         "minecraft:deepslate",
         "minecraft:netherrack",
         "minecraft:andesite",
         "minecraft:diorite",
         "minecraft:granite",
         "minecraft:tuff",
         "minecraft:blackstone"
      }
   );
   public final DoubleSetting reach = this.dbl("Reach", "Mining / placing distance.", 4.5, 2.0, 6.0, 0.1);
   public final IntSetting placesPerTick = this.integer("Places/Tick", "Filler blocks per tick.", 1, 1, 4);
   public final BoolSetting walk = this.bool("Walk", "Walks to the next spot that needs work.", true);
   public final BoolSetting humanRotations = this.bool(
      "Human Rotations", "Turns your real camera to every block before mining / placing, like a player. Off = silent server-side rotations.", true
   );
   public final IntSetting rotateSpeed = this.integer("Rotate Speed", "Maximum head turn per tick in degrees (lower = calmer).", 32, 8, 90)
      .visibleWhen(this.humanRotations::get);
   public final IntSetting randomness = this.integer(
         "Randomness",
         "0 = robot-exact. Higher = turn speed varies per block, it aims at random spots of the face and takes small random pauses (sometimes a longer one).",
         25,
         0,
         100
      )
      .visibleWhen(this.humanRotations::get);
   public final BoolSetting topDown = this.bool(
      "Top Down",
      "First pillars up to the highest point of the area, then digs it down layer by layer from the top – never undercuts a hill from the side (no falling sand / gravel, no caves opening next to you).",
      false
   );
   public final BoolSetting hammer = this.bool(
      "3x3 Pickaxe",
      "Uses your 3x3 pickaxe (set its name in the \"3x3 Pickaxe\" module): mines only the centre of every 3x3 square, never where the square would leave the area or dig below the surface.",
      false
   );
   public final BoolSetting render = this.bool("Render", "Shows the area and the target surface.", true);
   private class_2338 pos1;
   private class_2338 pos2;
   private int minX;
   private int maxX;
   private int minZ;
   private int maxZ;
   private int targetY;
   private boolean[] needs;
   private int scanCursor;
   private int remaining = -1;
   private class_2338 walkTarget;
   private final Worker worker = new Worker();
   private String status = "Idle";
   private int doneTicks;
   private int layerY = Integer.MIN_VALUE;
   private boolean onTop;
   private class_2338 towerBase;
   private int towerTicks;
   private boolean towerAimed;
   private boolean climbing;
   private int towerX;
   private int towerZ;
   private int lastTowerY;
   private int towerFails;
   private int towerPauseUntil;
   private String climbWarn;
   private Set<String> fillerSource;
   private Set<class_2248> fillerCache = Set.of();

   public Terraform() {
      super("Terraform", Category.AUTOMATION, "Flattens an area to one height: mines hills, fills holes, water and lava. Walks around until it's done.");
      this.action("Set Pos 1", "Corner 1 = your position.", () -> {
         if (mc.field_1724 != null) {
            this.pos1 = mc.field_1724.method_24515();
            Notifications.info("Terraform", "Pos 1: " + this.pos1.method_23854());
         }
      });
      this.action("Set Pos 2", "Corner 2 = your position.", () -> {
         if (mc.field_1724 != null) {
            this.pos2 = mc.field_1724.method_24515();
            Notifications.info("Terraform", "Pos 2: " + this.pos2.method_23854());
         }
      });
   }

   @Override
   protected void onEnable() {
      if (!inGame()) {
         this.setEnabledSilently(false);
      } else {
         class_2338 var1 = mc.field_1724.method_24515();
         this.targetY = var1.method_10264() + this.yOffset.get();
         if (this.area.get() == Terraform.Area.SELECTION) {
            if (this.pos1 == null || this.pos2 == null) {
               Notifications.warn("Terraform", "Set Pos 1 and Pos 2 first");
               this.setEnabled(false);
               return;
            }

            this.minX = Math.min(this.pos1.method_10263(), this.pos2.method_10263());
            this.maxX = Math.max(this.pos1.method_10263(), this.pos2.method_10263());
            this.minZ = Math.min(this.pos1.method_10260(), this.pos2.method_10260());
            this.maxZ = Math.max(this.pos1.method_10260(), this.pos2.method_10260());
         } else {
            int var2 = this.radius.get();
            this.minX = var1.method_10263() - var2;
            this.maxX = var1.method_10263() + var2;
            this.minZ = var1.method_10260() - var2;
            this.maxZ = var1.method_10260() + var2;
         }

         this.needs = new boolean[(this.maxX - this.minX + 1) * (this.maxZ - this.minZ + 1)];
         Arrays.fill(this.needs, true);
         this.scanCursor = 0;
         this.remaining = -1;
         this.doneTicks = 0;
         this.layerY = Integer.MIN_VALUE;
         this.onTop = false;
         this.towerBase = null;
         this.climbing = false;
         this.towerFails = 0;
         this.towerPauseUntil = 0;
         this.climbWarn = null;
         this.walkTarget = null;
         Notifications.info("Terraform", "Flattening " + (this.maxX - this.minX + 1) + "x" + (this.maxZ - this.minZ + 1) + " at Y " + this.targetY);
      }
   }

   @Override
   protected void onDisable() {
      this.worker.reset();
      this.towerBase = null;
      this.climbing = false;
      if (mc.field_1690 != null) {
         mc.field_1690.field_1903.method_23481(KeyUtil.isPhysicallyDown(mc.field_1690.field_1903));
      }

      this.status = "Idle";
   }

   @Override
   public void onWorldChange() {
      if (this.isEnabled()) {
         this.setEnabled(false);
      }
   }

   private boolean inArea(class_2338 var1) {
      return var1.method_10263() >= this.minX && var1.method_10263() <= this.maxX && var1.method_10260() >= this.minZ && var1.method_10260() <= this.maxZ;
   }

   private Set<class_2248> fillerBlocks() {
      Set var1 = this.fillers.get();
      if (var1 != this.fillerSource) {
         this.fillerCache = RegistryUtil.blocks(var1);
         this.fillerSource = var1;
      }

      return this.fillerCache;
   }

   private boolean isFiller(class_1792 var1, Set<class_2248> var2) {
      return var1 instanceof class_1747 var3 && var2.contains(var3.method_7711());
   }

   private boolean needsRemove(class_2338 var1) {
      class_2680 var2 = mc.field_1687.method_8320(var1);
      return !var2.method_26215() && !(var2.method_26214(mc.field_1687, var1) < 0.0F) ? var2.method_26227().method_15769() || !var2.method_45474() : false;
   }

   private boolean needsFluidBlock(class_2338 var1) {
      class_2680 var2 = mc.field_1687.method_8320(var1);
      return !var2.method_26227().method_15769() && var2.method_26227().method_15771() && var2.method_45474();
   }

   private boolean needsFill(class_2338 var1) {
      return mc.field_1687.method_8320(var1).method_45474();
   }

   private boolean columnNeedsWork(int var1, int var2) {
      if (!mc.field_1687.method_2935().method_12123(var1 >> 4, var2 >> 4)) {
         return true;
      } else {
         class_2339 var3 = new class_2339();
         if (this.remove.get()) {
            int var4 = Math.min(mc.field_1687.method_8624(class_2903.field_13202, var1, var2) - 1, this.targetY + this.removeHeight.get());

            for (int var5 = var4; var5 > this.targetY; var5--) {
               var3.method_10103(var1, var5, var2);
               if (this.needsRemove(var3) || this.fluids.get() && this.needsFluidBlock(var3)) {
                  return true;
               }
            }
         }

         if (this.fill.get()) {
            var3.method_10103(var1, this.targetY, var2);
            if (this.needsFill(var3)) {
               return true;
            }
         }

         return false;
      }
   }

   private boolean openFill(class_2338 var1) {
      class_2339 var2 = new class_2339();

      for (int var3 = var1.method_10264(); var3 <= this.targetY; var3++) {
         var2.method_10103(var1.method_10263(), var3, var1.method_10260());
         if (!this.needsFill(var2)) {
            return false;
         }
      }

      return true;
   }

   private void scanColumns() {
      int var1 = this.maxX - this.minX + 1;
      int var2 = this.needs.length;

      for (int var3 = 0; var3 < Math.min(var2, 1500); var3++) {
         int var4 = this.scanCursor;
         this.scanCursor = (this.scanCursor + 1) % var2;
         this.needs[var4] = this.columnNeedsWork(this.minX + var4 % var1, this.minZ + var4 / var1);
         if (this.scanCursor == 0) {
            int var5 = 0;

            for (boolean var9 : this.needs) {
               if (var9) {
                  var5++;
               }
            }

            this.remaining = var5;
         }
      }
   }

   @Override
   public void onTick() {
      if (this.needs != null) {
         if (mc.field_1755 != null) {
            this.worker.release();
            if (this.towerBase != null) {
               mc.field_1690.field_1903.method_23481(KeyUtil.isPhysicallyDown(mc.field_1690.field_1903));
               this.towerBase = null;
            }
         } else {
            this.scanColumns();
            double var1 = this.reach.get();
            this.worker.human = this.humanRotations.get();
            this.worker.speed = this.rotateSpeed.get().intValue();
            this.worker.humanizer.amount = this.randomness.get().intValue() / 100.0;
            this.worker.hammer = this.hammer.get();
            this.worker.hammerAllowed = var1x -> this.inArea(var1x)
               && var1x.method_10264() > this.targetY
               && var1x.method_10264() <= this.targetY + this.removeHeight.get()
               && (!this.topDown.get() || this.layerY == Integer.MIN_VALUE || var1x.method_10264() >= this.layerY)
               && (!this.topDownActive() || !this.underFeet(var1x))
               && this.needsRemove(var1x);
            this.worker.hammerWanted = this.worker.hammerAllowed;
            PlacementSolver.visibleOnly = true;
            if (this.topDown.get() && this.remove.get()) {
               if (mc.field_1724.field_6012 % 10 == 0 || this.layerY == Integer.MIN_VALUE) {
                  this.layerY = this.topLayer();
               }

               if (this.towerBase != null) {
                  this.tickTower();
                  return;
               }
            } else {
               this.layerY = Integer.MIN_VALUE;
               this.climbing = false;
               if (this.towerBase != null) {
                  mc.field_1690.field_1903.method_23481(KeyUtil.isPhysicallyDown(mc.field_1690.field_1903));
                  this.towerBase = null;
               }
            }

            if (this.worker.busy() && this.worker.tickPlacing(var1)) {
               this.status = "Placing";
            } else if (this.worker.mining() != null && this.worker.mine(this.worker.mining(), var1)) {
               this.status = "Mining";
            } else {
               Set var3 = this.fillerBlocks();
               class_243 var4 = mc.field_1724.method_33571();
               class_2338 var5 = class_2338.method_49638(var4);
               int var6 = (int)Math.ceil(var1);
               class_2338 var7 = null;
               class_2338 var8 = null;
               double var9 = Double.MAX_VALUE;
               double var11 = Double.MAX_VALUE;
               ArrayList var13 = new ArrayList();

               for (class_2338 var15 : class_2338.method_10097(var5.method_10069(-var6, -var6, -var6), var5.method_10069(var6, var6, var6))) {
                  if (this.inArea(var15)) {
                     double var16 = var15.method_10263() + 0.5 - var4.field_1352;
                     double var18 = var15.method_10264() + 0.5 - var4.field_1351;
                     double var20 = var15.method_10260() + 0.5 - var4.field_1350;
                     double var22 = var16 * var16 + var18 * var18 + var20 * var20;
                     if (!(var22 > var1 * var1)) {
                        int var24 = var15.method_10264();
                        if (var24 > this.targetY && var24 <= this.targetY + this.removeHeight.get()) {
                           if (this.fluids.get() && this.needsFluidBlock(var15)) {
                              if (var22 < var11) {
                                 var11 = var22;
                                 var8 = var15.method_10062();
                              }
                           } else if (this.remove.get() && this.needsRemove(var15) && (!this.topDownActive() || var24 >= this.layerY)) {
                              double var25 = var22 - var24 * 1000 + (this.topDownActive() && this.underFeet(var15) ? 500.0 : 0.0);
                              if (var25 < var9) {
                                 var9 = var25;
                                 var7 = var15.method_10062();
                              }
                           }
                        } else if (this.fill.get()
                           && var24 <= this.targetY
                           && var24 > this.targetY - this.fillDepth.get()
                           && this.needsFill(var15)
                           && this.openFill(var15)) {
                           var13.add(var15.method_10062());
                        }
                     }
                  }
               }

               if (var8 != null && this.worker.place(var8, var2 -> this.isFiller(var2, var3), var1)) {
                  this.status = "Blocking fluid";
               } else if (var7 != null) {
                  this.worker.mine(var7, var1);
                  this.worker.release();
                  this.status = "Mining";
               } else {
                  if (!var13.isEmpty()) {
                     if (Worker.blockInHotbar(var2 -> this.isFiller(var2, var3)) < 0) {
                        this.status = "No filler blocks – add dirt / cobblestone …";
                     } else {
                        var13.sort(
                           (var1x, var2) -> var1x.method_10264() != var2.method_10264()
                              ? Integer.compare(var1x.method_10264(), var2.method_10264())
                              : Double.compare(class_243.method_24953(var1x).method_1025(var4), class_243.method_24953(var2).method_1025(var4))
                        );
                        int var27 = 0;

                        for (class_2338 var31 : var13) {
                           if (var27 >= this.placesPerTick.get()) {
                              break;
                           }

                           if (this.worker.place(var31, var2 -> this.isFiller(var2, var3), var1)) {
                              var27++;
                           }
                        }

                        if (var27 > 0) {
                           this.worker.release();
                           this.status = "Filling";
                           return;
                        }
                     }
                  }

                  this.climbWarn = null;
                  if (this.topDownActive() && this.climbIfBelow()) {
                     return;
                  }

                  if (mc.field_1724.field_6012 % 10 == 0 || this.walkTarget == null || !this.needs[this.index(this.walkTarget)]) {
                     this.walkTarget = this.topDownActive() ? this.nearestTop() : this.nearestNeedy();
                     if (this.walkTarget == null) {
                        this.walkTarget = this.nearestNeedy();
                     }
                  }

                  if (this.walkTarget != null) {
                     this.doneTicks = 0;
                     if (this.walk.get()) {
                        boolean var28 = this.topDownActive() && mc.field_1724.method_24515().method_10264() <= this.layerY;
                        boolean var30 = this.worker.walkTo(class_243.method_24955(this.walkTarget), var28 ? 0.4 : Math.max(1.0, var1 - 2.0));
                        if (this.worker.isStuck()) {
                           this.status = "Stuck – help me a bit";
                        } else if (this.climbWarn != null) {
                           this.status = this.climbWarn;
                        } else if (!var30) {
                           this.status = this.topDown.get() ? "Rest of this column is out of reach" : "Out of reach here – try Top Down";
                        } else {
                           this.status = "Walking";
                        }
                     } else {
                        this.status = "Move closer to the rest of the area";
                     }
                  } else {
                     this.worker.release();
                     if (this.remaining == 0 && ++this.doneTicks > 40) {
                        Notifications.push("Terraform", "Area is flat!", Notifications.Type.SUCCESS);
                        this.setEnabled(false);
                     } else {
                        this.status = "Checking area …";
                     }
                  }
               }
            }
         }
      }
   }

   private boolean topDownActive() {
      return this.topDown.get() && this.remove.get() && this.layerY != Integer.MIN_VALUE && this.layerY > this.targetY;
   }

   private int topLayer() {
      int var1 = Integer.MIN_VALUE;
      int var2 = this.maxX - this.minX + 1;

      for (int var3 = 0; var3 < this.needs.length; var3++) {
         if (this.needs[var3]) {
            int var4 = this.minX + var3 % var2;
            int var5 = this.minZ + var3 / var2;
            if (mc.field_1687.method_2935().method_12123(var4 >> 4, var5 >> 4)
               && (
                  var1 == Integer.MIN_VALUE
                     || Math.min(mc.field_1687.method_8624(class_2903.field_13202, var4, var5) - 1, this.targetY + this.removeHeight.get()) > var1
               )) {
               int var6 = this.columnTop(var4, var5);
               if (var6 > var1) {
                  var1 = var6;
               }
            }
         }
      }

      return var1;
   }

   private int columnTop(int var1, int var2) {
      int var3 = Math.min(mc.field_1687.method_8624(class_2903.field_13202, var1, var2) - 1, this.targetY + this.removeHeight.get());
      class_2339 var4 = new class_2339();

      for (int var5 = var3; var5 > this.targetY; var5--) {
         var4.method_10103(var1, var5, var2);
         if (this.needsRemove(var4)) {
            return var5;
         }
      }

      return Integer.MIN_VALUE;
   }

   private boolean underFeet(class_2338 var1) {
      class_2338 var2 = mc.field_1724.method_24515();
      return var1.method_10263() == var2.method_10263() && var1.method_10260() == var2.method_10260() && var1.method_10264() < var2.method_10264();
   }

   private class_2338 nearestTop() {
      int var1 = this.maxX - this.minX + 1;
      class_2338 var2 = null;
      double var3 = Double.MAX_VALUE;
      double var5 = mc.field_1724.method_23317();
      double var7 = mc.field_1724.method_23321();

      for (int var9 = 0; var9 < this.needs.length; var9++) {
         if (this.needs[var9]) {
            int var10 = this.minX + var9 % var1;
            int var11 = this.minZ + var9 / var1;
            double var12 = (var10 + 0.5 - var5) * (var10 + 0.5 - var5) + (var11 + 0.5 - var7) * (var11 + 0.5 - var7);
            if (var12 < var3 && this.columnTop(var10, var11) >= this.layerY) {
               var3 = var12;
               var2 = new class_2338(var10, this.layerY + 1, var11);
            }
         }
      }

      return var2;
   }

   private boolean climbIfBelow() {
      class_2338 var1 = mc.field_1724.method_24515();
      int var2 = var1.method_10264();
      if (var2 > this.layerY) {
         this.onTop = true;
         this.climbing = false;
         this.towerFails = 0;
         return false;
      } else {
         this.onTop = false;
         if (this.climbing && (var1.method_10263() != this.towerX || var1.method_10260() != this.towerZ)) {
            this.climbing = false;
         }

         if (!mc.field_1724.method_24828()) {
            if (this.climbing) {
               this.worker.release();
               this.status = "Top down: pillaring up to Y " + (this.layerY + 1);
               return true;
            } else {
               return false;
            }
         } else {
            int var3 = mc.field_1724.field_6012;
            if (this.towerPauseUntil - var3 > 200) {
               this.towerPauseUntil = 0;
            }

            if (var3 < this.towerPauseUntil) {
               this.climbWarn = "Top down: can't pillar here – walking on";
               return false;
            } else {
               if (!this.climbing) {
                  class_2338 var4 = this.nearestTop();
                  if (var4 != null
                     && (
                        Math.hypot(var4.method_10263() + 0.5 - mc.field_1724.method_23317(), var4.method_10260() + 0.5 - mc.field_1724.method_23321()) > 1.2
                           || var2 == this.layerY
                     )
                     && this.worker.stuckFor() < 20) {
                     return false;
                  }
               }

               class_2338 var6 = var1.method_10086(2);
               if (!mc.field_1687.method_8320(var6).method_26215()) {
                  if (this.inArea(var6) && var6.method_10264() > this.targetY && this.needsRemove(var6) && this.worker.mine(var6, this.reach.get())) {
                     this.status = "Top down: clearing above my head";
                     return true;
                  } else {
                     this.climbing = false;
                     this.climbWarn = "Top down: blocked above my head – can't pillar";
                     return false;
                  }
               } else {
                  Set var5 = this.fillerBlocks();
                  if (Worker.blockInHotbar(var2x -> this.isFiller(var2x, var5)) < 0) {
                     this.climbing = false;
                     this.climbWarn = "Top down: need filler blocks to pillar up";
                     return false;
                  } else {
                     if (this.climbing && var2 <= this.lastTowerY) {
                        if (++this.towerFails >= 4) {
                           this.climbing = false;
                           this.towerFails = 0;
                           this.towerPauseUntil = var3 + 100;
                           this.climbWarn = "Top down: pillar doesn't grow – walking on";
                           return false;
                        }
                     } else {
                        this.towerFails = 0;
                     }

                     this.worker.release();
                     this.worker.stopMining();
                     this.climbing = true;
                     this.towerX = var1.method_10263();
                     this.towerZ = var1.method_10260();
                     this.lastTowerY = var2;
                     this.towerBase = var1;
                     this.towerTicks = 0;
                     this.towerAimed = !this.humanRotations.get();
                     this.status = "Top down: pillaring up to Y " + (this.layerY + 1);
                     return true;
                  }
               }
            }
         }
      }
   }

   private void tickTower() {
      if (!this.towerAimed) {
         this.status = "Top down: looking down to pillar";
         if (HumanAim.step(mc.field_1724.method_36454(), 88.0F, this.rotateSpeed.get().intValue(), 2.5F)) {
            this.towerAimed = true;
         }
      } else {
         this.towerTicks++;
         if (this.towerTicks == 1) {
            mc.field_1690.field_1903.method_23481(true);
            this.status = "Top down: pillaring up to Y " + (this.layerY + 1);
         } else {
            mc.field_1690.field_1903.method_23481(KeyUtil.isPhysicallyDown(mc.field_1690.field_1903));
            if (mc.field_1724.method_23318() >= this.towerBase.method_10264() + 1.05 || this.towerTicks > 12) {
               class_2338 var1 = mc.field_1724.method_24515();
               if (mc.field_1724.method_23318() >= this.towerBase.method_10264() + 1.0
                  && var1.method_10263() == this.towerBase.method_10263()
                  && var1.method_10260() == this.towerBase.method_10260()
                  && mc.field_1687.method_8320(this.towerBase).method_45474()) {
                  Set var2 = this.fillerBlocks();
                  int var3 = Worker.blockInHotbar(var2x -> this.isFiller(var2x, var2));
                  if (var3 >= 0) {
                     InvUtil.select(var3);
                     class_2338 var4 = this.towerBase.method_10074();
                     class_243 var5 = new class_243(var4.method_10263() + 0.5, var4.method_10264() + 1.0, var4.method_10260() + 0.5);
                     Worker.click(
                        new PlacementSolver.Click(new class_3965(var5, class_2350.field_11036, var4, false), mc.field_1724.method_36454(), 90.0F, true)
                     );
                  }
               }

               this.towerBase = null;
            }
         }
      }
   }

   private int index(class_2338 var1) {
      return (var1.method_10260() - this.minZ) * (this.maxX - this.minX + 1) + (var1.method_10263() - this.minX);
   }

   private class_2338 nearestNeedy() {
      int var1 = this.maxX - this.minX + 1;
      class_2338 var2 = null;
      double var3 = Double.MAX_VALUE;
      double var5 = mc.field_1724.method_23317();
      double var7 = mc.field_1724.method_23321();

      for (int var9 = 0; var9 < this.needs.length; var9++) {
         if (this.needs[var9]) {
            int var10 = this.minX + var9 % var1;
            int var11 = this.minZ + var9 / var1;
            double var12 = (var10 + 0.5 - var5) * (var10 + 0.5 - var5) + (var11 + 0.5 - var7) * (var11 + 0.5 - var7);
            if (var12 < var3) {
               var3 = var12;
               var2 = new class_2338(var10, this.targetY + 1, var11);
            }
         }
      }

      return var2;
   }

   @Override
   public void onRender3D(Render3D var1) {
      if (this.render.get() && this.needs != null) {
         int var2 = HudManager.accent();
         var1.box(new class_238(this.minX, this.targetY + 1, this.minZ, this.maxX + 1, this.targetY + 1.02, this.maxZ + 1), var2, 25, false);
         var1.boxOutline(
            new class_238(
               this.minX, this.targetY - this.fillDepth.get() + 1, this.minZ, this.maxX + 1, this.targetY + this.removeHeight.get() + 1, this.maxZ + 1
            ),
            var2 & 1627389951,
            false
         );
         if (this.worker.mining() != null) {
            var1.box(new class_238(this.worker.mining()), -49088, 60, false);
         }

         if (this.walkTarget != null) {
            var1.boxOutline(new class_238(this.walkTarget), -12517568, false);
         }
      }
   }

   @Override
   public String getInfo() {
      return this.remaining < 0 ? "…" : this.remaining + " left";
   }

   @Override
   public List<String> details() {
      ArrayList var1 = new ArrayList();
      var1.add("Status: " + this.status);
      if (this.needs != null) {
         var1.add("Area " + (this.maxX - this.minX + 1) + "x" + (this.maxZ - this.minZ + 1) + " · surface Y " + this.targetY);
         var1.add(this.remaining < 0 ? "Scanning …" : this.remaining + " of " + this.needs.length + " columns still need work");
         if (this.topDownActive()) {
            var1.add("Top down: layer Y " + this.layerY + (this.onTop ? "" : " (climbing up first)"));
         }
      }

      if (this.pos1 != null || this.pos2 != null) {
         var1.add("Pos 1: " + (this.pos1 == null ? "-" : this.pos1.method_23854()) + " · Pos 2: " + (this.pos2 == null ? "-" : this.pos2.method_23854()));
      }

      return var1;
   }

   public static enum Area {
      RADIUS,
      SELECTION;
   }
}
