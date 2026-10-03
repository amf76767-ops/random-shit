package dev.dihclient.modules.world;

import dev.dihclient.autobuild.PlacementSolver;
import dev.dihclient.autobuild.Worker;
import dev.dihclient.hud.HudManager;
import dev.dihclient.module.Category;
import dev.dihclient.module.Module;
import dev.dihclient.module.ModuleManager;
import dev.dihclient.modules.player.AutoEat;
import dev.dihclient.render.Render3D;
import dev.dihclient.setting.BoolSetting;
import dev.dihclient.setting.DoubleSetting;
import dev.dihclient.setting.EnumSetting;
import dev.dihclient.setting.IdListSetting;
import dev.dihclient.setting.IntSetting;
import dev.dihclient.util.InvUtil;
import dev.dihclient.util.JunkUtil;
import dev.dihclient.util.Notifications;
import dev.dihclient.util.RegistryUtil;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import net.minecraft.class_1747;
import net.minecraft.class_1799;
import net.minecraft.class_1802;
import net.minecraft.class_2246;
import net.minecraft.class_2248;
import net.minecraft.class_2338;
import net.minecraft.class_2350;
import net.minecraft.class_238;
import net.minecraft.class_243;
import net.minecraft.class_2680;
import net.minecraft.class_3486;

public class Tunnel extends Module {
   public final IntSetting width = this.integer("Width", "Tunnel width.", 3, 1, 7);
   public final IntSetting height = this.integer("Height", "Tunnel height.", 3, 2, 6);
   public final IntSetting length = this.integer("Length", "Blocks to dig. 0 = until you turn it off.", 0, 0, 100000);
   public final EnumSetting<Tunnel.Shape> shape = this.mode(
      "Shape", "Straight = level tunnel. Stairs Down / Up = goes one block down / up with every block forward (walkable staircase).", Tunnel.Shape.STRAIGHT
   );
   public final IntSetting perTick = this.integer(
      "Blocks Per Tick",
      "Blocks that break instantly (netherrack with Efficiency/Haste …) are broken this many per tick. Only without Human Rotations.",
      4,
      1,
      12
   );
   public final BoolSetting resume = this.bool(
      "Resume", "Turning it on again while standing in the old tunnel continues it (same direction, same progress).", true
   );
   public final IntSetting stopHealth = this.integer("Pause Health", "Pauses while your health is at or below this (0 = off). Hearts x2.", 8, 0, 19);
   public final BoolSetting sealFluids = this.bool("Seal Fluids", "Blocks lava and water next to the tunnel so nothing flows in.", true);
   public final BoolSetting floor = this.bool("Floor", "Closes holes in the floor (no falling into lava or caves).", true);
   public final BoolSetting torches = this.bool("Torches", "Places torches along the right wall.", false);
   public final IntSetting torchSpacing = this.integer("Torch Spacing", "Blocks between torches.", 8, 2, 16).visibleWhen(this.torches::get);
   public final IdListSetting fillers = this.ids(
      "Filler Blocks",
      "Blocks used for sealing and the floor.",
      IdListSetting.Kind.BLOCK,
      new String[]{
         "minecraft:netherrack",
         "minecraft:cobblestone",
         "minecraft:cobbled_deepslate",
         "minecraft:stone",
         "minecraft:blackstone",
         "minecraft:basalt",
         "minecraft:dirt",
         "minecraft:deepslate",
         "minecraft:andesite",
         "minecraft:diorite",
         "minecraft:granite",
         "minecraft:tuff"
      }
   );
   public final DoubleSetting reach = this.dbl("Reach", "Mining / placing distance.", 4.5, 2.0, 6.0, 0.1);
   public final BoolSetting walk = this.bool("Walk", "Walks forward as the tunnel grows.", true);
   public final BoolSetting humanRotations = this.bool(
      "Human Rotations", "Turns your real camera to every block before digging / placing, like a player. Off = silent server-side rotations.", true
   );
   public final IntSetting rotateSpeed = this.integer("Rotate Speed", "Maximum head turn per tick in degrees (lower = calmer).", 36, 8, 90)
      .visibleWhen(this.humanRotations::get);
   public final IntSetting randomness = this.integer(
         "Randomness",
         "0 = robot-exact. Higher = turn speed varies per block, it aims at random spots of the face and takes small random pauses (sometimes a longer one).",
         25,
         0,
         100
      )
      .visibleWhen(this.humanRotations::get);
   public final BoolSetting dropJunk = this.bool(
      "Drop Junk", "When the inventory is full, throws stone, dirt, netherrack … away instead of stopping (keeps 2 stack(s) for building).", true
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
   public final BoolSetting stopWhenFull = this.bool("Stop When Full", "Stops when your inventory is full.", true);
   public final BoolSetting hammer = this.bool(
      "3x3 Pickaxe",
      "Uses your 3x3 pickaxe (name set in the \"3x3 Pickaxe\" module): mines only the centre of each 3x3 square – a 3x3 tunnel goes one swing per slice. Never where the square would leave the tunnel (floor, walls, next to lava).",
      false
   );
   public final BoolSetting render = this.bool("Render", "Shows the next part of the tunnel.", true);
   private class_2338 origin;
   private class_2350 dir;
   private class_2350 right;
   private int progress;
   private int rMin;
   private int rMax;
   private final Worker worker = new Worker();
   private String status = "Idle";
   private Tunnel.Shape activeShape = Tunnel.Shape.STRAIGHT;
   private long startedAt;
   private int startProgress;
   private Set<String> fillerSource;
   private Set<class_2248> fillerCache = Set.of();

   public Tunnel() {
      super("Tunnel", Category.AUTOMATION, "Digs a tunnel / highway where you look: seals lava & water, fixes the floor, torches, walks forward.");
   }

   private Set<class_2248> fillerBlocks() {
      Set var1 = this.fillers.get();
      if (var1 != this.fillerSource) {
         this.fillerCache = RegistryUtil.blocks(var1);
         this.fillerSource = var1;
      }

      return this.fillerCache;
   }

   @Override
   protected void onEnable() {
      if (!inGame()) {
         this.setEnabledSilently(false);
      } else {
         class_2350 var1 = mc.field_1724.method_5735();
         if (this.resume.get() && this.origin != null && var1 == this.dir && this.activeShape == this.shape.get() && this.insideOldTunnel()) {
            Notifications.info("Tunnel", "Resuming at " + this.progress + " blocks towards " + this.dir.method_15434());
         } else {
            this.dir = var1;
            this.right = this.dir.method_10170();
            this.origin = mc.field_1724.method_24515();
            this.progress = 0;
            this.activeShape = this.shape.get();
            Notifications.info(
               "Tunnel", "Digging " + this.width.get() + "x" + this.height.get() + " " + this.shape.displayValue() + " towards " + this.dir.method_15434()
            );
         }

         this.rMin = -(this.width.get() - 1) / 2;
         this.rMax = this.width.get() / 2;
         this.startedAt = System.currentTimeMillis();
         this.startProgress = this.progress;
      }
   }

   @Override
   protected void onDisable() {
      this.worker.reset();
      this.status = "Idle";
   }

   @Override
   public void onWorldChange() {
      if (this.isEnabled()) {
         this.setEnabled(false);
      }
   }

   private int slope() {
      return this.activeShape == Tunnel.Shape.STAIRS_DOWN ? -1 : (this.activeShape == Tunnel.Shape.STAIRS_UP ? 1 : 0);
   }

   private int rows() {
      return this.height.get() + (this.activeShape == Tunnel.Shape.STAIRS_UP ? 1 : 0);
   }

   private class_2338 at(int var1, int var2, int var3) {
      return this.origin.method_10079(this.dir, var1).method_10079(this.right, var2).method_10086(var3 + this.slope() * var1);
   }

   private boolean insideOldTunnel() {
      class_243 var1 = mc.field_1724.method_73189();

      for (int var2 = Math.max(0, this.progress - 64); var2 <= this.progress; var2++) {
         if (class_243.method_24955(this.at(var2, 0, 0)).method_1025(var1) < 4.0) {
            return true;
         }
      }

      return false;
   }

   private boolean inTunnel(class_2338 var1) {
      for (int var2 = Math.max(0, this.progress - 1); var2 <= this.progress + 3; var2++) {
         for (int var3 = this.rMin; var3 <= this.rMax; var3++) {
            for (int var4 = 0; var4 < this.rows(); var4++) {
               if (this.at(var2, var3, var4).equals(var1)) {
                  return true;
               }
            }
         }
      }

      return false;
   }

   private boolean solid(class_2338 var1) {
      class_2680 var2 = mc.field_1687.method_8320(var1);
      return !var2.method_26215() && !(var2.method_26214(mc.field_1687, var1) < 0.0F) ? var2.method_26227().method_15769() || !var2.method_45474() : false;
   }

   private boolean torch(class_2338 var1) {
      return mc.field_1687.method_8320(var1).method_27852(class_2246.field_10336);
   }

   private boolean passable(int var1) {
      int var2 = (int)Math.floor((this.rMin + this.rMax) / 2.0);
      int var3 = (int)Math.ceil((this.rMin + this.rMax) / 2.0);

      for (int var4 = var2; var4 <= var3; var4++) {
         for (int var5 = 0; var5 <= 1; var5++) {
            class_2338 var6 = this.at(var1, var4, var5);
            if (this.solid(var6) || this.fluid(var6)) {
               return false;
            }
         }

         if (mc.field_1687.method_8320(this.at(var1, var4, -1)).method_45474()) {
            return false;
         }
      }

      return true;
   }

   private boolean hasSolid(int var1) {
      for (int var2 = this.rMin; var2 <= this.rMax; var2++) {
         for (int var3 = 0; var3 < this.rows(); var3++) {
            if (this.solid(this.at(var1, var2, var3))) {
               return true;
            }
         }
      }

      return false;
   }

   private boolean fluid(class_2338 var1) {
      return !mc.field_1687.method_8320(var1).method_26227().method_15769();
   }

   private boolean sectionClear(int var1) {
      for (int var2 = this.rMin; var2 <= this.rMax; var2++) {
         for (int var3 = 0; var3 < this.rows(); var3++) {
            class_2338 var4 = this.at(var1, var2, var3);
            if (this.solid(var4) || this.fluid(var4)) {
               return false;
            }
         }
      }

      return true;
   }

   @Override
   public void onTick() {
      if (this.origin != null) {
         Worker.toolSaver = Math.max(1, this.toolSaver.get());
         if (mc.field_1755 != null) {
            this.worker.release();
         } else if (ModuleManager.on(AutoEat.class) && ModuleManager.of(AutoEat.class).isEating()) {
            this.worker.release();
            this.worker.stopMining();
            this.status = "Paused – eating";
         } else if (this.stopHealth.get() > 0 && mc.field_1724.method_6032() <= this.stopHealth.get().intValue()) {
            this.worker.release();
            this.worker.stopMining();
            this.status = "Paused – low health";
         } else if (this.toolSaver.get() > 0 && mc.field_1724.field_6012 % 20 == 0 && !JunkUtil.ensurePickaxe(this.toolSaver.get(), this.hammer.get())) {
            Notifications.warn("Tunnel", "Tool Saver: no pickaxe with more than " + this.toolSaver.get() + " durability – stopped");
            this.setEnabled(false);
         } else if (this.dropJunk.get() && mc.field_1724.method_31548().method_7376() < 0 && JunkUtil.drop(this.junk.get(), 2, 2) > 0) {
            this.worker.release();
            this.status = "Dropping junk";
         } else if (this.stopWhenFull.get() && mc.field_1724.method_31548().method_7376() < 0 && mc.field_1724.field_6012 % 20 == 0) {
            Notifications.warn("Tunnel", "Inventory full – stopped after " + this.progress + " blocks");
            this.setEnabled(false);
         } else {
            for (int var1 = 0; var1 < 32 && (this.length.get() == 0 || this.progress < this.length.get()); var1++) {
               class_2338 var2 = this.at(this.progress, 0, 0);
               if (!mc.field_1687.method_2935().method_12123(var2.method_10263() >> 4, var2.method_10260() >> 4) || !this.sectionClear(this.progress)) {
                  break;
               }

               this.progress++;
            }

            class_2338 var17 = this.at(this.progress, 0, 0);
            if (!mc.field_1687.method_2935().method_12123(var17.method_10263() >> 4, var17.method_10260() >> 4)) {
               this.status = "Waiting for chunks";
               this.worker.release();
            } else if (this.length.get() > 0 && this.progress >= this.length.get()) {
               Notifications.push("Tunnel", "Tunnel finished (" + this.progress + " blocks)", Notifications.Type.SUCCESS);
               this.setEnabled(false);
            } else {
               double var18 = this.reach.get();
               this.worker.human = this.humanRotations.get();
               this.worker.speed = this.rotateSpeed.get().intValue();
               this.worker.humanizer.amount = this.randomness.get().intValue() / 100.0;
               this.worker.hammer = this.hammer.get();
               this.worker.hammerAllowed = var1x -> this.inTunnel(var1x) && !this.torch(var1x);
               this.worker.hammerWanted = var1x -> this.inTunnel(var1x) && this.solid(var1x) && !this.torch(var1x);
               if (this.worker.busy() && this.worker.tickPlacing(var18)) {
                  this.status = "Placing";
                  return;
               }

               Set var4 = this.fillerBlocks();
               if (this.sealFluids.get()) {
                  for (int var5 = Math.max(0, this.progress - 1); var5 <= this.progress + 2; var5++) {
                     for (class_2338 var7 : this.shell(var5)) {
                        if (this.fluid(var7) && mc.field_1687.method_8320(var7).method_45474() && this.place(var7, var4, var18)) {
                           this.worker.release();
                           this.status = "Sealing " + (mc.field_1687.method_8320(var7).method_26227().method_15767(class_3486.field_15518) ? "lava" : "water");
                           return;
                        }
                     }

                     for (int var21 = this.rMin; var21 <= this.rMax; var21++) {
                        for (int var24 = 0; var24 < this.rows(); var24++) {
                           class_2338 var8 = this.at(var5, var21, var24);
                           if (this.fluid(var8) && mc.field_1687.method_8320(var8).method_45474() && this.place(var8, var4, var18)) {
                              this.worker.release();
                              this.status = "Blocking fluid";
                              return;
                           }
                        }
                     }
                  }
               }

               if (this.floor.get()) {
                  for (int var19 = Math.max(0, this.progress - 1); var19 <= this.progress + 1; var19++) {
                     for (int var22 = this.rMin; var22 <= this.rMax; var22++) {
                        class_2338 var25 = this.at(var19, var22, -1);
                        if (mc.field_1687.method_8320(var25).method_45474() && this.place(var25, var4, var18)) {
                           this.worker.release();
                           this.status = "Fixing floor";
                           return;
                        }
                     }
                  }
               }

               if (this.worker.mining() != null && this.worker.mine(this.worker.mining(), var18)) {
                  this.status = "Digging";
               } else {
                  class_243 var20 = mc.field_1724.method_33571();
                  class_2338 var23 = null;
                  double var26 = Double.MAX_VALUE;

                  for (int var9 = this.progress; var9 <= this.progress + 2; var9++) {
                     for (int var10 = this.rMin; var10 <= this.rMax; var10++) {
                        for (int var11 = this.rows() - 1; var11 >= 0; var11--) {
                           class_2338 var12 = this.at(var9, var10, var11);
                           if (this.solid(var12)) {
                              double var13 = class_243.method_24953(var12).method_1025(var20);
                              if (!(var13 > var18 * var18)) {
                                 double var15 = var9 * 1000 - var11 * 10 + var13;
                                 if (var15 < var26) {
                                    var26 = var15;
                                    var23 = var12;
                                 }
                              }
                           }
                        }
                     }
                  }

                  if (var23 != null) {
                     this.worker.release();
                     this.worker.mine(var23, var18);
                     this.status = "Digging";
                     if (!this.humanRotations.get()) {
                        this.breakInstant(var23, var18);
                     }
                  } else {
                     if (this.torches.get()) {
                        for (int var27 = this.torchSpacing.get(); var27 < this.progress; var27 += this.torchSpacing.get()) {
                           class_2338 var29 = this.at(var27, this.rMax, 0);
                           if (!(class_243.method_24953(var29).method_1025(var20) > var18 * var18)
                              && mc.field_1687.method_8320(var29).method_26215()
                              && this.solid(var29.method_10074())
                              && this.placeTorch(var29, var18)) {
                              this.status = "Torch";
                              return;
                           }
                        }
                     }

                     if (this.walk.get()) {
                        class_2338 var28 = this.at(Math.max(0, this.progress - 1), 0, 0);
                        class_243 var30 = class_243.method_24955(var28)
                           .method_1031(
                              this.right.method_10148() * ((this.rMin + this.rMax) / 2.0), 0.0, this.right.method_10165() * ((this.rMin + this.rMax) / 2.0)
                           );
                        boolean var31 = this.hasSolid(this.progress);
                        if (var31 && this.progress > 0 && this.passable(this.progress)) {
                           var30 = var30.method_1031(this.dir.method_10148(), 0.0, this.dir.method_10165());
                        }

                        if (this.worker.walkTo(var30, 0.4)) {
                           this.status = this.worker.isStuck() ? "Stuck – help me a bit" : "Walking";
                        } else {
                           this.status = var31 ? "Out of reach – raise Reach or lower Width / Height" : "Waiting";
                        }
                     } else {
                        this.worker.release();
                        this.status = "Move forward";
                     }
                  }
               }
            }
         }
      }
   }

   private void breakInstant(class_2338 var1, double var2) {
      if (mc.field_1687.method_8320(var1).method_26215()) {
         class_243 var4 = mc.field_1724.method_33571();

         for (int var5 = 1; var5 < this.perTick.get(); var5++) {
            class_2338 var6 = null;
            double var7 = Double.MAX_VALUE;

            for (int var9 = this.progress; var9 <= this.progress + 2; var9++) {
               for (int var10 = this.rMin; var10 <= this.rMax; var10++) {
                  for (int var11 = this.rows() - 1; var11 >= 0; var11--) {
                     class_2338 var12 = this.at(var9, var10, var11);
                     if (this.solid(var12)) {
                        double var13 = class_243.method_24953(var12).method_1025(var4);
                        class_2680 var15 = mc.field_1687.method_8320(var12);
                        if (!(var13 > var2 * var2)
                           && var15.method_26165(mc.field_1724, mc.field_1687, var12) >= 1.0F
                           && var9 * 1000 - var11 * 10 + var13 < var7) {
                           var7 = var9 * 1000 - var11 * 10 + var13;
                           var6 = var12;
                        }
                     }
                  }
               }
            }

            if (var6 == null) {
               return;
            }

            this.worker.mine(var6, var2);
            if (!mc.field_1687.method_8320(var6).method_26215()) {
               return;
            }
         }
      }
   }

   private List<class_2338> shell(int var1) {
      ArrayList var2 = new ArrayList();

      for (int var3 = -1; var3 <= this.rows(); var3++) {
         var2.add(this.at(var1, this.rMin - 1, var3));
         var2.add(this.at(var1, this.rMax + 1, var3));
      }

      for (int var4 = this.rMin; var4 <= this.rMax; var4++) {
         var2.add(this.at(var1, var4, this.rows()));
         var2.add(this.at(var1, var4, -1));
      }

      return var2;
   }

   private boolean place(class_2338 var1, Set<class_2248> var2, double var3) {
      return this.worker.place(var1, var1x -> var1x instanceof class_1747 var2x && var2.contains(var2x.method_7711()), var3);
   }

   private boolean placeTorch(class_2338 var1, double var2) {
      if (this.humanRotations.get()) {
         return this.worker.place(var1, var0 -> var0 == class_1802.field_8810, var2);
      } else {
         int var4 = InvUtil.findHotbar(class_1802.field_8810);
         if (var4 < 0) {
            int var5 = InvUtil.findInventory(var0 -> var0.method_31574(class_1802.field_8810));
            if (var5 < 9) {
               return false;
            }

            var4 = InvUtil.firstEmptyHotbar() >= 0 ? InvUtil.firstEmptyHotbar() : 8;
            InvUtil.swapToHotbar(var5, var4);
         }

         class_1799 var7 = mc.field_1724.method_31548().method_5438(var4);
         PlacementSolver.Click var6 = PlacementSolver.solve(var1, class_2246.field_10336.method_9564(), var7, var2);
         if (var6 == null) {
            return false;
         } else {
            InvUtil.select(var4);
            return Worker.click(var6);
         }
      }
   }

   @Override
   public void onRender3D(Render3D var1) {
      if (this.render.get() && this.origin != null) {
         int var2 = HudManager.accent();

         for (int var3 = this.progress; var3 <= this.progress + 2; var3++) {
            class_2338 var4 = this.at(var3, this.rMin, 0);
            class_2338 var5 = this.at(var3, this.rMax, this.rows() - 1);
            class_238 var6 = new class_238(
               Math.min(var4.method_10263(), var5.method_10263()),
               var4.method_10264(),
               Math.min(var4.method_10260(), var5.method_10260()),
               Math.max(var4.method_10263(), var5.method_10263()) + 1,
               var5.method_10264() + 1,
               Math.max(var4.method_10260(), var5.method_10260()) + 1
            );
            var1.boxOutline(var6, var3 == this.progress ? var2 : var2 & 1895825407, false);
         }

         if (this.worker.mining() != null) {
            var1.box(new class_238(this.worker.mining()), -49088, 60, false);
         }
      }
   }

   @Override
   public String getInfo() {
      return this.origin == null ? null : this.progress + (this.length.get() > 0 ? "/" + this.length.get() : "") + "m";
   }

   @Override
   public List<String> details() {
      ArrayList var1 = new ArrayList();
      var1.add("Status: " + this.status);
      if (this.origin != null) {
         var1.add(
            "Dug "
               + this.progress
               + " blocks towards "
               + this.dir.method_15434()
               + " · "
               + this.width.get()
               + "x"
               + this.height.get()
               + " · "
               + this.activeShape.name().toLowerCase().replace('_', ' ')
         );
         double var2 = (System.currentTimeMillis() - this.startedAt) / 60000.0;
         if (this.isEnabled() && var2 > 0.1) {
            double var4 = (this.progress - this.startProgress) / var2;
            String var6 = String.format(Locale.ROOT, "%.1f blocks/min", var4);
            if (this.length.get() > 0 && var4 > 0.0) {
               var6 = var6 + " · ~" + (int)Math.ceil((this.length.get() - this.progress) / var4) + " min left";
            }

            var1.add(var6);
         }
      }

      return var1;
   }

   public static enum Shape {
      STRAIGHT,
      STAIRS_DOWN,
      STAIRS_UP;
   }
}
