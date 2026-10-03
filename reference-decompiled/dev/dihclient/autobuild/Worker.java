package dev.dihclient.autobuild;

import dev.dihclient.modules.world.SafeRoute;
import dev.dihclient.util.Hammer;
import dev.dihclient.util.HumanAim;
import dev.dihclient.util.Humanizer;
import dev.dihclient.util.InvUtil;
import dev.dihclient.util.KeyUtil;
import dev.dihclient.util.RotationUtil;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Predicate;
import net.minecraft.class_1268;
import net.minecraft.class_1269;
import net.minecraft.class_1747;
import net.minecraft.class_1792;
import net.minecraft.class_1799;
import net.minecraft.class_2338;
import net.minecraft.class_2350;
import net.minecraft.class_243;
import net.minecraft.class_2680;
import net.minecraft.class_310;
import net.minecraft.class_2828.class_2831;

public final class Worker {
   private static final class_310 mc = class_310.method_1551();
   private boolean walking;
   private int stuck;
   private class_243 lastPos;
   private class_2338 mining;
   private int miningTicks;
   private boolean mineAligned;
   public boolean human = true;
   public boolean guard = true;
   public float speed = 32.0F;
   private PlacementSolver.Click pending;
   private class_2338 pendingPos;
   private int pendingSlot;
   private int pendingTicks;
   private boolean aligned;
   public final Humanizer humanizer = new Humanizer();
   private int cooldownUntil;
   private float targetSpeed = 32.0F;
   private float pendingYaw;
   private float pendingPitch;
   private class_243 mineJitter = class_243.field_1353;
   public boolean hammer;
   public Predicate<class_2338> hammerAllowed = var0 -> false;
   public Predicate<class_2338> hammerWanted = var0 -> false;
   private final Set<class_2338> square = new HashSet<>();
   private boolean useHammer;
   private class_2350 hammerFace;
   private class_2338 hammerTarget;
   private class_2338 noHammer;
   public static int toolSaver = 8;

   private boolean waiting() {
      if (!this.human) {
         return false;
      } else {
         int var1 = mc.field_1724.field_6012;
         if (this.cooldownUntil - var1 > 40) {
            this.cooldownUntil = 0;
         }

         return var1 < this.cooldownUntil;
      }
   }

   private void pauseAfterAction() {
      this.cooldownUntil = mc.field_1724.field_6012 + this.humanizer.pause();
   }

   public boolean walkTo(class_243 var1, double var2) {
      double var4 = var1.field_1352 - mc.field_1724.method_23317();
      double var6 = var1.field_1350 - mc.field_1724.method_23321();
      if (Math.sqrt(var4 * var4 + var6 * var6) <= var2) {
         this.release();
         return false;
      } else {
         String var8 = this.guard ? SafeRoute.blocked(var1, true) : null;
         if (var8 != null) {
            int var11 = this.stuck;
            this.release();
            this.stuck = var11 + 1;
            SafeRoute.note("Stopped: " + var8);
            return true;
         } else {
            float var9 = (float)Math.toDegrees(Math.atan2(var6, var4)) - 90.0F;
            mc.field_1724.method_36456(RotationUtil.approachAngle(mc.field_1724.method_36454(), var9, 25.0F));
            mc.field_1690.field_1894.method_23481(true);
            this.walking = true;
            class_243 var10 = mc.field_1724.method_73189();
            this.stuck = this.lastPos != null && var10.method_1025(this.lastPos) < 0.0025 ? this.stuck + 1 : 0;
            this.lastPos = var10;
            mc.field_1690
               .field_1903
               .method_23481(
                  (mc.field_1724.field_5976 || this.stuck > 8) && mc.field_1724.method_24828() || mc.field_1724.field_5976 && mc.field_1724.method_5799()
               );
            mc.field_1690.field_1913.method_23481(this.stuck > 30 && this.stuck / 20 % 2 == 0);
            return true;
         }
      }
   }

   public int stuckFor() {
      return this.stuck;
   }

   public boolean isStuck() {
      return this.stuck > 80;
   }

   public void release() {
      if (this.walking && mc.field_1690 != null) {
         mc.field_1690.field_1894.method_23481(KeyUtil.isPhysicallyDown(mc.field_1690.field_1894));
         mc.field_1690.field_1903.method_23481(KeyUtil.isPhysicallyDown(mc.field_1690.field_1903));
         mc.field_1690.field_1913.method_23481(KeyUtil.isPhysicallyDown(mc.field_1690.field_1913));
         this.walking = false;
         this.stuck = 0;
      }
   }

   public class_2338 mining() {
      return this.mining;
   }

   public Set<class_2338> square() {
      return this.square;
   }

   public boolean mine(class_2338 var1, double var2) {
      if (this.mining != null
         && !var1.equals(this.mining)
         && this.useHammer
         && this.square.contains(var1)
         && !mc.field_1687.method_8320(this.mining).method_26215()) {
         var1 = this.mining;
      }

      class_2680 var4 = mc.field_1687.method_8320(var1);
      if (!var4.method_26215()
         && (var4.method_26227().method_15769() || !var4.method_45474())
         && !(var4.method_26214(mc.field_1687, var1) < 0.0F)
         && !(class_243.method_24953(var1).method_1022(mc.field_1724.method_33571()) > var2 + 0.5)) {
         if (!var1.equals(this.mining)) {
            if (this.waiting()) {
               return true;
            }

            this.stopMining();
            this.useHammer = false;
            this.square.clear();
            if (this.hammer && Hammer.available() && !var1.equals(this.noHammer)) {
               class_2338 var5 = Hammer.bestCenter(var1, var2, this.hammerAllowed, this.hammerWanted);
               if (var5 != null) {
                  this.hammerTarget = var1.method_10062();
                  var1 = var5;
                  var4 = mc.field_1687.method_8320(var5);
                  this.useHammer = true;
                  this.hammerFace = Hammer.face(var5, mc.field_1724.method_33571());
                  this.square.addAll(Hammer.square(var5, this.hammerFace));
               }
            }

            this.mining = var1.method_10062();
            this.miningTicks = 0;
            this.mineAligned = false;
            this.targetSpeed = this.humanizer.speed(this.speed);
            class_2350 var9 = class_2350.method_58251(mc.field_1724.method_33571().method_1020(class_243.method_24953(var1)));
            this.mineJitter = this.humanizer.jitter(class_243.field_1353, var9, 0.35);
         }

         if (++this.miningTicks > 600) {
            this.stopMining();
            return false;
         } else {
            class_243 var10 = class_243.method_24953(var1);
            class_2350 var6 = class_2350.method_58251(mc.field_1724.method_33571().method_1020(var10));
            if (!this.useHammer || var6 == this.hammerFace && Hammer.safe(var1, var6, this.hammerAllowed) && Hammer.available()) {
               if (!this.useHammer || !this.square.contains(var1)) {
                  selectBestTool(var4, true);
               } else if (!Hammer.select()) {
                  selectBestTool(var4, true);
               }

               if (this.human) {
                  this.release();
                  class_243 var11 = var10.method_1031(var6.method_10148() * 0.49, var6.method_10164() * 0.49, var6.method_10165() * 0.49)
                     .method_1019(this.mineJitter);
                  boolean var8 = HumanAim.stepTo(var11, this.targetSpeed, 3.0F, this.humanizer.noise());
                  if (!var8 || !this.mineAligned) {
                     this.mineAligned = var8;
                     return true;
                  }
               } else {
                  float[] var12 = RotationUtil.rotationsTo(var10);
                  mc.field_1724.field_3944.method_52787(new class_2831(var12[0], var12[1], mc.field_1724.method_24828(), mc.field_1724.field_5976));
               }

               if (mc.field_1724.method_68878()) {
                  mc.field_1761.method_2910(var1, var6);
               } else {
                  mc.field_1761.method_2902(var1, var6);
               }

               mc.field_1724.method_6104(class_1268.field_5808);
               return true;
            } else {
               class_2338 var7 = this.hammerTarget;
               this.stopMining();
               this.noHammer = var7;
               return true;
            }
         }
      } else {
         if (this.mining != null && var1.equals(this.mining) && this.human) {
            this.pauseAfterAction();
         }

         this.stopMining();
         return false;
      }
   }

   public void stopMining() {
      if (this.mining != null && mc.field_1761 != null) {
         mc.field_1761.method_2925();
      }

      this.mining = null;
      this.useHammer = false;
      this.hammerFace = null;
      this.hammerTarget = null;
      this.square.clear();
   }

   public static void selectBestTool(class_2680 var0) {
      selectBestTool(var0, false);
   }

   public static void selectBestTool(class_2680 var0, boolean var1) {
      int var2 = -1;
      float var3 = 1.0F;

      for (int var4 = 0; var4 < 9; var4++) {
         class_1799 var5 = mc.field_1724.method_31548().method_5438(var4);
         if ((!var1 || !Hammer.isHammer(var5)) && (!var5.method_7963() || var5.method_7936() - var5.method_7919() >= toolSaver)) {
            float var6 = var5.method_7924(var0);
            if (var6 > var3) {
               var3 = var6;
               var2 = var4;
            }
         }
      }

      if (var2 >= 0) {
         InvUtil.select(var2);
      } else if (var1 && Hammer.isHammer(mc.field_1724.method_31548().method_5438(InvUtil.selectedSlot()))) {
         for (int var7 = 0; var7 < 9; var7++) {
            if (!Hammer.isHammer(mc.field_1724.method_31548().method_5438(var7))) {
               InvUtil.select(var7);
               break;
            }
         }
      }
   }

   public static int blockInHotbar(Predicate<class_1792> var0) {
      for (int var1 = 0; var1 < 9; var1++) {
         class_1792 var2 = mc.field_1724.method_31548().method_5438(var1).method_7909();
         if (var2 instanceof class_1747 && var0.test(var2)) {
            return var1;
         }
      }

      int var3 = InvUtil.findInventory(var1x -> var1x.method_7909() instanceof class_1747 && var0.test(var1x.method_7909()));
      if (var3 >= 9) {
         int var4 = InvUtil.firstEmptyHotbar();
         if (var4 < 0) {
            var4 = 8;
         }

         InvUtil.swapToHotbar(var3, var4);
         return var4;
      } else {
         return -1;
      }
   }

   public boolean busy() {
      return this.pending != null;
   }

   public boolean tickPlacing(double var1) {
      if (this.pending == null) {
         return false;
      } else {
         class_1799 var3 = mc.field_1724.method_31548().method_5438(this.pendingSlot);
         if (++this.pendingTicks <= 60
            && mc.field_1687.method_8320(this.pendingPos).method_45474()
            && !var3.method_7960()
            && var3.method_7909() instanceof class_1747
            && !mc.field_1687.method_8320(this.pending.hit().method_17777()).method_26215()
            && !(this.pending.hit().method_17784().method_1022(mc.field_1724.method_33571()) > var1 + 0.6)) {
            InvUtil.select(this.pendingSlot);
            this.release();
            boolean var4 = HumanAim.step(this.pendingYaw, this.pendingPitch, this.targetSpeed, 1.0F, this.humanizer.noise());
            if (var4 && this.aligned) {
               class_1269 var5 = mc.field_1761.method_2896(mc.field_1724, class_1268.field_5808, this.pending.hit());
               if (var5.method_23665()) {
                  mc.field_1724.method_6104(class_1268.field_5808);
               }

               this.pending = null;
               this.pauseAfterAction();
            } else {
               this.aligned = var4;
            }

            return true;
         } else {
            this.pending = null;
            return false;
         }
      }
   }

   public boolean place(class_2338 var1, Predicate<class_1792> var2, double var3) {
      if (this.pending != null && this.tickPlacing(var3)) {
         return true;
      } else if (this.waiting() && mc.field_1687.method_8320(var1).method_45474()) {
         return true;
      } else if (!mc.field_1687.method_8320(var1).method_45474()) {
         return false;
      } else {
         int var5 = blockInHotbar(var2);
         if (var5 < 0) {
            return false;
         } else {
            class_1799 var6 = mc.field_1724.method_31548().method_5438(var5);
            if (var6.method_7909() instanceof class_1747 var7) {
               PlacementSolver.Click var10 = PlacementSolver.solve(var1, var7.method_7711().method_9564(), var6, var3);
               if (var10 == null) {
                  return false;
               } else if (this.human) {
                  this.targetSpeed = this.humanizer.speed(this.speed);
                  if (var10.aimed()) {
                     float[] var9 = RotationUtil.rotationsTo(this.humanizer.jitter(var10.hit().method_17784(), var10.hit().method_17780(), 0.08));
                     this.pendingYaw = var9[0];
                     this.pendingPitch = var9[1];
                  } else {
                     this.pendingYaw = var10.yaw();
                     this.pendingPitch = var10.pitch();
                  }

                  this.pending = var10;
                  this.pendingPos = var1.method_10062();
                  this.pendingSlot = var5;
                  this.pendingTicks = 0;
                  this.aligned = false;
                  InvUtil.select(var5);
                  this.tickPlacing(var3);
                  return true;
               } else {
                  InvUtil.select(var5);
                  return click(var10);
               }
            } else {
               return false;
            }
         }
      }
   }

   public static boolean click(PlacementSolver.Click var0) {
      float var1 = mc.field_1724.method_36454();
      float var2 = mc.field_1724.method_36455();
      mc.field_1724.method_36456(var0.yaw());
      mc.field_1724.method_36457(var0.pitch());
      mc.field_1724.field_3944.method_52787(new class_2831(var0.yaw(), var0.pitch(), mc.field_1724.method_24828(), mc.field_1724.field_5976));
      class_1269 var3 = mc.field_1761.method_2896(mc.field_1724, class_1268.field_5808, var0.hit());
      if (var3.method_23665()) {
         mc.field_1724.method_6104(class_1268.field_5808);
      }

      mc.field_1724.method_36456(var1);
      mc.field_1724.method_36457(var2);
      return var3.method_23665();
   }

   public void reset() {
      this.release();
      this.stopMining();
      this.pending = null;
      this.cooldownUntil = 0;
      this.noHammer = null;
      this.lastPos = null;
   }
}
