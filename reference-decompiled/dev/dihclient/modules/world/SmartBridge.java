package dev.dihclient.modules.world;

import dev.dihclient.autobuild.PlacementSolver;
import dev.dihclient.autobuild.Worker;
import dev.dihclient.module.Category;
import dev.dihclient.module.Module;
import dev.dihclient.module.ModuleManager;
import dev.dihclient.setting.BoolSetting;
import dev.dihclient.setting.IntSetting;
import dev.dihclient.util.HumanAim;
import dev.dihclient.util.Humanizer;
import dev.dihclient.util.InvUtil;
import dev.dihclient.util.KeyUtil;
import dev.dihclient.util.MoveUtil;
import dev.dihclient.util.RotationUtil;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import net.minecraft.class_1268;
import net.minecraft.class_1269;
import net.minecraft.class_1747;
import net.minecraft.class_1799;
import net.minecraft.class_2248;
import net.minecraft.class_2338;
import net.minecraft.class_2346;
import net.minecraft.class_2350;
import net.minecraft.class_243;
import net.minecraft.class_2680;
import net.minecraft.class_3965;
import net.minecraft.class_2828.class_2831;

public class SmartBridge extends Module {
   public final BoolSetting human = this.bool(
      "Human",
      "Turns your real camera to every block and clicks like a player: visible faces only, one block at a time, small pauses. Off = fast silent placing.",
      false
   );
   public final IntSetting rotateSpeed = this.integer("Rotate Speed", "Maximum head turn per tick in degrees.", 45, 10, 90).visibleWhen(this.human::get);
   public final IntSetting randomness = this.integer("Randomness", "0 = robot-exact. Higher = more human turning and pauses.", 20, 0, 100)
      .visibleWhen(this.human::get);
   public final BoolSetting restoreView = this.bool("Restore View", "Turns back to your walking direction after each block.", true)
      .visibleWhen(this.human::get);
   public final IntSetting lookahead = this.integer("Lookahead", "Ticks of movement predicted – blocks are placed where you will be.", 4, 1, 12);
   public final IntSetting perTick = this.integer("Blocks/Tick", "Maximum placements per tick.", 3, 1, 8).visibleWhen(() -> !this.human.get());
   public final BoolSetting keepY = this.bool("Keep Y", "Keeps bridging at the height where you enabled it.", false);
   public final BoolSetting catchFall = this.bool("Catch Fall", "Places a block under you when you walk off an edge or fall.", true);
   public final BoolSetting tower = this.bool("Tower", "Holding jump while standing still builds straight up.", true);
   public final BoolSetting safeEdge = this.bool("Safe Edge", "Sneaks at the edge while the next block is not placed yet.", true);
   public final BoolSetting autoSwitch = this.bool("Auto Switch", "Selects a block from the hotbar automatically.", true);
   public final BoolSetting restoreSlot = this.bool("Restore Slot", "Returns to the previous slot when disabled.", true);
   private final Humanizer humanizer = new Humanizer();
   private int previousSlot = -1;
   private int keepLevel;
   private int groundLevel;
   private boolean sneakForced;
   private class_2338 pendingPos;
   private PlacementSolver.Click pending;
   private float pendingYaw;
   private float pendingPitch;
   private float pendingSpeed;
   private boolean aligned;
   private int pendingTicks;
   private int cooldown;
   private float walkYaw = Float.NaN;
   private int placed;

   public SmartBridge() {
      super(
         "SmartBridge", Category.AUTOMATION, "Scaffold with lookahead: predicts your path and places blocks before you reach the edge. Human mode available."
      );
   }

   public static boolean busy() {
      SmartBridge var0 = ModuleManager.of(SmartBridge.class);
      return var0 != null && var0.isEnabled() && var0.pending != null;
   }

   public static int blockCount() {
      return mc.field_1724 == null ? 0 : InvUtil.count(SmartBridge::isPlaceable);
   }

   @Override
   protected void onEnable() {
      this.previousSlot = -1;
      this.pending = null;
      this.walkYaw = Float.NaN;
      this.cooldown = 0;
      this.placed = 0;
      if (mc.field_1724 != null) {
         this.keepLevel = this.groundLevel = (int)Math.floor(mc.field_1724.method_23318()) - 1;
      }
   }

   @Override
   protected void onDisable() {
      if (mc.field_1724 != null && this.restoreSlot.get() && this.previousSlot >= 0) {
         InvUtil.select(this.previousSlot);
      }

      this.previousSlot = -1;
      this.pending = null;
      this.releaseSneak();
   }

   private static boolean isPlaceable(class_1799 var0) {
      return var0.method_7909() instanceof class_1747 var1 && placeableBlock(var1.method_7711());
   }

   public static boolean placeableBlock(class_2248 var0) {
      if (mc.field_1687 == null) {
         return false;
      } else {
         class_2680 var1 = var0.method_9564();
         return var1.method_26234(mc.field_1687, class_2338.field_10980) && !(var0 instanceof class_2346) && !var1.method_31709();
      }
   }

   private boolean ensureBlockInHand() {
      if (isPlaceable(mc.field_1724.method_6047())) {
         return true;
      } else if (!this.autoSwitch.get()) {
         return false;
      } else {
         int var1 = InvUtil.findHotbar(SmartBridge::isPlaceable);
         if (var1 < 0 && Goto.running()) {
            var1 = Worker.blockInHotbar(var0 -> var0 instanceof class_1747 var1x && placeableBlock(var1x.method_7711()));
         }

         if (var1 < 0) {
            return false;
         } else {
            if (this.previousSlot < 0) {
               this.previousSlot = InvUtil.selectedSlot();
            }

            InvUtil.select(var1);
            return true;
         }
      }
   }

   private boolean towering() {
      return this.tower.get() && mc.field_1690.field_1903.method_1434() && !MoveUtil.isMoving();
   }

   private int level() {
      double var1 = mc.field_1724.method_23318();
      int var3 = (int)Math.floor(var1) - 1;
      if (mc.field_1724.method_24828()) {
         this.groundLevel = var3;
      }

      if (this.towering()) {
         return var3;
      } else if (this.keepY.get()) {
         return this.keepLevel;
      } else if (mc.field_1724.method_24828()) {
         return var3;
      } else if (var1 >= this.groundLevel + 1.0 - 0.3) {
         return this.groundLevel;
      } else {
         return this.catchFall.get() ? var3 : Integer.MIN_VALUE;
      }
   }

   private List<class_2338> predict(int var1) {
      LinkedHashSet var2 = new LinkedHashSet();
      double var3 = mc.field_1724.method_23317();
      double var5 = mc.field_1724.method_23321();
      class_2338 var7 = class_2338.method_49637(var3, var1, var5);
      var2.add(var7);
      class_243 var8 = mc.field_1724.method_18798();
      double var9 = var8.field_1352;
      double var11 = var8.field_1350;
      if (MoveUtil.isMoving()) {
         double[] var13 = MoveUtil.direction(Math.max(0.2, Math.hypot(var9, var11)));
         var9 = var9 * 0.5 + var13[0] * 0.5;
         var11 = var11 * 0.5 + var13[1] * 0.5;
      } else if (Math.hypot(var9, var11) < 0.03) {
         return new ArrayList<>(var2);
      }

      for (int var17 = 1; var17 <= this.lookahead.get(); var17++) {
         var3 += var9;
         var5 += var11;
         class_2338 var14 = class_2338.method_49637(var3, var1, var5);
         if (!var14.equals(var7)) {
            int var15 = var14.method_10263() - var7.method_10263();
            int var16 = var14.method_10260() - var7.method_10260();
            if (var15 != 0 && var16 != 0) {
               var2.add(var7.method_10069(Math.abs(var9) >= Math.abs(var11) ? var15 : 0, 0, Math.abs(var9) >= Math.abs(var11) ? 0 : var16));
            }

            var2.add(var14);
            var7 = var14;
         }
      }

      return new ArrayList<>(var2);
   }

   @Override
   public void onTick() {
      if (inGame() && mc.field_1755 == null) {
         this.humanizer.amount = this.randomness.get().intValue() / 100.0;
         int var1 = this.level();
         if (!this.keepY.get()) {
            this.keepLevel = var1 == Integer.MIN_VALUE ? this.keepLevel : var1;
         }

         boolean var2 = false;
         boolean var3 = false;
         List var4 = Goto.bridgeTargets();
         if (var4 != null) {
            ArrayList var5 = new ArrayList();

            for (class_2338 var7 : var4) {
               if (mc.field_1687.method_8320(var7).method_45474()) {
                  var5.add(var7);
               }
            }

            var2 = !var5.isEmpty();
            if (var2 && this.ensureBlockInHand()) {
               var3 = this.human.get() ? this.tickHuman(var5) : this.tickFast(var5);
            }
         } else if (var1 != Integer.MIN_VALUE) {
            ArrayList var8 = new ArrayList();

            for (class_2338 var11 : this.predict(var1)) {
               if (mc.field_1687.method_8320(var11).method_45474()) {
                  var8.add(var11);
               }
            }

            var2 = !var8.isEmpty();
            if (var2 && this.ensureBlockInHand()) {
               var3 = this.human.get() ? this.tickHuman(var8) : this.tickFast(var8);
            }
         }

         if (this.human.get()
            && this.pending == null
            && !var2
            && this.restoreView.get()
            && !Float.isNaN(this.walkYaw)
            && HumanAim.step(this.walkYaw, mc.field_1724.method_36455(), this.rotateSpeed.get().intValue(), 2.0F, this.humanizer.noise())) {
            this.walkYaw = Float.NaN;
         }

         boolean var9 = var2 && !var3 && mc.field_1724.method_24828() && MoveUtil.edgeAhead(0.3);
         if (!this.safeEdge.get() || !var9 && (this.pending == null || !mc.field_1724.method_24828() || !MoveUtil.edgeAhead(0.45))) {
            this.releaseSneak();
         } else {
            mc.field_1690.field_1832.method_23481(true);
            this.sneakForced = true;
         }
      } else {
         this.releaseSneak();
      }
   }

   private boolean tickFast(List<class_2338> var1) {
      int var2 = 0;

      for (class_2338 var4 : var1) {
         if (mc.field_1687.method_8320(var4).method_45474() && this.placeFast(var4)) {
            if (++var2 >= this.perTick.get()) {
               break;
            }
         }
      }

      if (var2 > 0) {
         this.placed += var2;
         if (this.towering()) {
            class_243 var5 = mc.field_1724.method_18798();
            mc.field_1724.method_18800(var5.field_1352 * 0.3, 0.42, var5.field_1350 * 0.3);
         }
      }

      return var2 > 0;
   }

   private static boolean support(class_2338 var0) {
      class_2680 var1 = mc.field_1687.method_8320(var0);
      return !var1.method_45474() && !var1.method_26218(mc.field_1687, var0).method_1110() && !var1.method_31709() && !PlacementSolver.interactive(var1);
   }

   private boolean placeFast(class_2338 var1) {
      class_243 var2 = mc.field_1724.method_33571();
      class_3965 var3 = null;
      double var4 = Double.MAX_VALUE;

      for (class_2350 var9 : new class_2350[]{
         class_2350.field_11033, class_2350.field_11043, class_2350.field_11035, class_2350.field_11039, class_2350.field_11034, class_2350.field_11036
      }) {
         class_2338 var10 = var1.method_10093(var9);
         if (support(var10)) {
            class_2350 var11 = var9.method_10153();
            class_243 var12 = class_243.method_24953(var10).method_1031(var11.method_10148() * 0.5, var11.method_10164() * 0.5, var11.method_10165() * 0.5);
            double var13 = var12.method_1025(var2);
            if (var13 <= 20.25 && var13 < var4) {
               var4 = var13;
               var3 = new class_3965(var12, var11, var10, false);
            }
         }
      }

      if (var3 == null) {
         return false;
      } else {
         float[] var15 = RotationUtil.rotationsTo(var3.method_17784());
         mc.field_1724.field_3944.method_52787(new class_2831(var15[0], var15[1], mc.field_1724.method_24828(), mc.field_1724.field_5976));
         class_1269 var16 = mc.field_1761.method_2896(mc.field_1724, class_1268.field_5808, var3);
         if (var16.method_23665()) {
            mc.field_1724.method_6104(class_1268.field_5808);
            return true;
         } else {
            return false;
         }
      }
   }

   private boolean tickHuman(List<class_2338> var1) {
      if (this.pending != null) {
         return this.tickPending();
      } else if (this.cooldown > 0) {
         this.cooldown--;
         return false;
      } else {
         class_1799 var2 = mc.field_1724.method_6047();
         if (var2.method_7909() instanceof class_1747 var3) {
            for (class_2338 var5 : var1) {
               PlacementSolver.Click var6 = PlacementSolver.solve(var5, var3.method_7711().method_9564(), var2, 4.5, true);
               if (var6 != null && var6.aimed()) {
                  if (Float.isNaN(this.walkYaw)) {
                     this.walkYaw = mc.field_1724.method_36454();
                  }

                  float[] var7 = RotationUtil.rotationsTo(this.humanizer.jitter(var6.hit().method_17784(), var6.hit().method_17780(), 0.08));
                  this.pending = var6;
                  this.pendingPos = var5;
                  this.pendingYaw = var7[0];
                  this.pendingPitch = var7[1];
                  this.pendingSpeed = this.humanizer.speed(this.rotateSpeed.get().intValue());
                  this.pendingTicks = 0;
                  this.aligned = false;
                  return this.tickPending();
               }
            }

            return false;
         } else {
            return false;
         }
      }
   }

   private boolean tickPending() {
      class_3965 var1 = this.pending.hit();
      if (++this.pendingTicks <= 30
         && mc.field_1687.method_8320(this.pendingPos).method_45474()
         && support(var1.method_17777())
         && !(var1.method_17784().method_1025(mc.field_1724.method_33571()) > 25.0)
         && isPlaceable(mc.field_1724.method_6047())) {
         boolean var2 = HumanAim.step(this.pendingYaw, this.pendingPitch, this.pendingSpeed, 1.0F, this.humanizer.noise());
         if (var2 && this.aligned) {
            class_1269 var3 = mc.field_1761.method_2896(mc.field_1724, class_1268.field_5808, var1);
            if (var3.method_23665()) {
               mc.field_1724.method_6104(class_1268.field_5808);
               this.placed++;
            }

            this.pending = null;
            this.cooldown = this.humanizer.pause();
            return var3.method_23665();
         } else {
            this.aligned = var2;
            return true;
         }
      } else {
         this.pending = null;
         return false;
      }
   }

   private void releaseSneak() {
      if (this.sneakForced && mc.field_1690 != null) {
         mc.field_1690.field_1832.method_23481(KeyUtil.isPhysicallyDown(mc.field_1690.field_1832) || Goto.holdingSneak());
         this.sneakForced = false;
      }
   }

   @Override
   public String getInfo() {
      return (this.human.get() ? "Human " : "") + InvUtil.count(SmartBridge::isPlaceable);
   }
}
