package dev.dihclient.modules.movement;

import dev.dihclient.module.Category;
import dev.dihclient.module.Module;
import dev.dihclient.module.ModuleManager;
import dev.dihclient.setting.BoolSetting;
import dev.dihclient.setting.DoubleSetting;
import dev.dihclient.setting.EnumSetting;
import dev.dihclient.setting.IntSetting;
import dev.dihclient.util.MoveUtil;
import dev.dihclient.util.Notifications;
import net.minecraft.class_10255;
import net.minecraft.class_1297;
import net.minecraft.class_238;
import net.minecraft.class_638;

public class BoatNoClip extends Module {
   public final EnumSetting<BoatNoClip.Mode> mode = this.mode(
      "Method",
      "Phase: fly straight through every block (Jump = up, Sprint = down). Hop: server-safe flying that teleports past floors, ceilings and walls. Clip: only teleports through floors/ceilings on key press.",
      BoatNoClip.Mode.PHASE
   );
   public final DoubleSetting speed = this.dbl("Speed", "Flying speed (blocks/tick).", 0.9, 0.1, 3.0, 0.05)
      .visibleWhen(() -> this.mode.get() != BoatNoClip.Mode.CLIP);
   public final DoubleSetting vertical = this.dbl("Vertical", "Up/down speed.", 0.45, 0.05, 2.0, 0.05)
      .visibleWhen(() -> this.mode.get() != BoatNoClip.Mode.CLIP);
   public final BoolSetting hover = this.bool("Hover", "Phase: the boat stays at its height when you don't press Jump/Sprint.", true)
      .visibleWhen(() -> this.mode.get() == BoatNoClip.Mode.PHASE);
   public final BoolSetting wallHop = this.bool(
         "Pass Walls", "Flying into a wall hops the boat to the nearest height where it can continue (through the ceiling/floor).", true
      )
      .visibleWhen(() -> this.mode.get() == BoatNoClip.Mode.HOP);
   public final BoolSetting antiKick = this.bool("Anti Kick", "Dips the boat a tiny bit every 2 seconds so the server doesn't kick you for floating.", true)
      .visibleWhen(() -> this.mode.get() != BoatNoClip.Mode.CLIP);
   public final IntSetting range = this.integer("Range", "Max blocks per clip. Vanilla accepts at most 10 for vehicles.", 9, 2, 9)
      .visibleWhen(() -> this.mode.get() != BoatNoClip.Mode.PHASE);
   public final BoolSetting needFloor = this.bool("Need Floor", "Only clip to spots with solid ground under them.", true)
      .visibleWhen(() -> this.mode.get() == BoatNoClip.Mode.CLIP);
   public final BoolSetting allVehicles = this.bool("All Vehicles", "Also horses, pigs, striders, minecarts …", false);
   private class_1297 last;
   private boolean jumpWas;
   private boolean sprintWas;
   private int cooldown;
   private int lastJumpTap = -100;
   private int lastSprintTap = -100;
   private double tx;
   private double ty;
   private double tz;
   private boolean hasTarget;
   private boolean gravityWasOff;
   private int flyTicks;

   private boolean tap(boolean var1, boolean var2) {
      if (var1 && ModuleManager.on(BoatFly.class)) {
         int var3 = mc.field_1724.field_6012;
         if (var2) {
            boolean var5 = var3 - this.lastJumpTap <= 8;
            this.lastJumpTap = var5 ? -100 : var3;
            return var5;
         } else {
            boolean var4 = var3 - this.lastSprintTap <= 8;
            this.lastSprintTap = var4 ? -100 : var3;
            return var4;
         }
      } else {
         return var1;
      }
   }

   public BoatNoClip() {
      super("BoatNoClip", Category.MOVEMENT, "Sit in a boat and fly straight through blocks (Jump = up, Sprint = down). Hop mode is the server-safe fallback.");
   }

   private void release() {
      if (this.last != null) {
         this.last.field_5960 = false;
         if (!this.gravityWasOff) {
            this.last.method_5875(false);
         }
      }

      this.last = null;
      this.hasTarget = false;
   }

   @Override
   protected void onEnable() {
      this.jumpWas = true;
      this.sprintWas = true;
      this.cooldown = 0;
   }

   @Override
   protected void onDisable() {
      this.release();
   }

   @Override
   public void onWorldChange() {
      this.last = null;
   }

   private boolean fits(class_1297 var1) {
      return var1 != null && (this.allVehicles.get() || var1 instanceof class_10255);
   }

   @Override
   public void onTick() {
      class_1297 var1 = mc.field_1724.method_5854();
      if (var1 != this.last) {
         this.release();
      }

      if (this.mode.get() != BoatNoClip.Mode.PHASE && this.last != null) {
         this.release();
      }

      if (this.mode.get() == BoatNoClip.Mode.HOP) {
         if (this.fits(var1)) {
            this.tickFly(var1);
         }
      } else if (this.mode.get() == BoatNoClip.Mode.PHASE) {
         if (this.fits(var1)) {
            this.tickPhase(var1);
         }
      } else {
         boolean var2 = mc.field_1690.field_1903.method_1434();
         boolean var3 = mc.field_1690.field_1867.method_1434();
         boolean var4 = this.tap(var2 && !this.jumpWas, true);
         boolean var5 = this.tap(var3 && !this.sprintWas, false);
         this.jumpWas = var2;
         this.sprintWas = var3;
         if (this.cooldown > 0) {
            this.cooldown--;
         } else if (this.fits(var1) && (var4 || var5)) {
            int var6 = var4 ? 1 : -1;
            double var7 = NoClip.findTarget(var1, var6, this.range.get(), this.needFloor.get(), 1.6);
            if (Double.isNaN(var7)) {
               Notifications.info("BoatNoClip", var6 > 0 ? "No free space above within range." : "No free space below within range.");
               this.cooldown = 10;
            } else {
               var1.method_5814(var1.method_23317(), var7, var1.method_23321());
               var1.method_18800(0.0, 0.0, 0.0);
               var1.field_6017 = 0.0;
               this.cooldown = 5;
            }
         }
      }
   }

   private void tickPhase(class_1297 var1) {
      if (this.last != var1) {
         this.last = var1;
         this.gravityWasOff = var1.method_5740();
         this.hasTarget = false;
      }

      this.flyTicks++;
      var1.field_5960 = true;
      var1.method_5875(true);
      var1.field_6017 = 0.0;
      var1.method_36456(mc.field_1724.method_36454());
      double var2 = var1.method_23317();
      double var4 = var1.method_23318();
      double var6 = var1.method_23321();
      if (!this.hasTarget || Math.abs(this.tx - var2) + Math.abs(this.ty - var4) + Math.abs(this.tz - var6) > 3.0) {
         this.tx = var2;
         this.ty = var4;
         this.tz = var6;
         this.hasTarget = true;
      }

      boolean var8 = mc.field_1690.field_1903.method_1434();
      boolean var9 = mc.field_1690.field_1867.method_1434();
      double var10 = var8 ? this.vertical.get() : (var9 ? -this.vertical.get() : (this.hover.get() ? 0.0 : -0.08));
      if (!var8 && !var9 && this.antiKick.get() && this.flyTicks % 40 == 0) {
         var10 = -0.04;
      } else if (!var8 && !var9 && this.antiKick.get() && this.flyTicks % 40 == 1 && this.hover.get()) {
         var10 = 0.04;
      }

      if (MoveUtil.isMoving()) {
         double[] var12 = MoveUtil.direction(this.speed.get());
         this.tx = this.tx + var12[0];
         this.tz = this.tz + var12[1];
      }

      this.ty = Math.max(this.ty + var10, mc.field_1687.method_31607() + 1.0);
      var1.method_5814(this.tx, this.ty, this.tz);
      var1.method_18800(0.0, 0.0, 0.0);
   }

   private void tickFly(class_1297 var1) {
      this.flyTicks++;
      var1.field_5960 = false;
      var1.field_6017 = 0.0;
      var1.method_36456(mc.field_1724.method_36454());
      boolean var2 = mc.field_1690.field_1903.method_1434();
      boolean var3 = mc.field_1690.field_1867.method_1434();
      double var4 = var2 ? this.vertical.get() : (var3 ? -this.vertical.get() : 0.0);
      if (!var2 && !var3 && this.antiKick.get() && this.flyTicks % 40 == 0) {
         var4 = -0.04;
      }

      double var6 = 0.0;
      double var8 = 0.0;
      if (MoveUtil.isMoving()) {
         double[] var10 = MoveUtil.direction(this.speed.get());
         var6 = var10[0];
         var8 = var10[1];
      }

      boolean var22 = ModuleManager.on(BoatFly.class);
      if (this.cooldown > 0) {
         this.cooldown--;
         if (!var22) {
            var1.method_18800(var6, var4 == -0.04 ? 0.0 : var4, var8);
         }
      } else {
         class_638 var11 = mc.field_1687;
         class_238 var12 = var1.method_5829();
         int var13 = this.range.get();
         double var14 = Double.NaN;
         if (var4 > 0.05 && !var11.method_8587(var1, var12.method_989(0.0, var4, 0.0))) {
            var14 = NoClip.findTarget(var1, 1, var13, false, 1.6);
         } else if (var4 < -0.05 && !var11.method_8587(var1, var12.method_989(0.0, var4, 0.0))) {
            var14 = NoClip.findTarget(var1, -1, var13, false, 1.6);
         } else if (this.wallHop.get() && (var6 != 0.0 || var8 != 0.0)) {
            double var16 = Math.sqrt(var6 * var6 + var8 * var8);
            double var18 = var6 / var16 * Math.max(0.6, var16);
            double var20 = var8 / var16 * Math.max(0.6, var16);
            if (!var11.method_8587(var1, var12.method_989(var18, 0.0, var20))) {
               var14 = this.hop(var1, var12, var18, var20, var13, var3);
            }
         }

         if (!Double.isNaN(var14)) {
            var1.method_5814(var1.method_23317(), var14, var1.method_23321());
            var1.method_18800(0.0, 0.0, 0.0);
            this.cooldown = 2;
            return;
         }

         if (!var22) {
            var1.method_18800(var6, var4, var8);
         }
      }
   }

   private double hop(class_1297 var1, class_238 var2, double var3, double var5, int var7, boolean var8) {
      class_638 var9 = mc.field_1687;
      double var10 = var1.method_23318();
      double var12 = Math.floor(var10 + 1.0E-4);
      class_238 var14 = new class_238(var2.field_1323, var2.field_1322, var2.field_1321, var2.field_1320, var2.field_1325 + 1.6, var2.field_1324);

      for (int var15 = 1; var15 <= var7; var15++) {
         for (int var16 = var8 ? -1 : 1; var8 ? var16 <= 1 : var16 >= -1; var16 += var8 ? 2 : -2) {
            double var17 = var12 + var16 * var15;
            if (!(var17 <= var9.method_31607())) {
               class_238 var19 = var14.method_989(0.0, var17 - var10, 0.0);
               if (var9.method_8587(var1, var19) && var9.method_8587(var1, var19.method_989(var3, 0.0, var5))) {
                  return var17;
               }
            }
         }
      }

      return Double.NaN;
   }

   @Override
   public String getInfo() {
      return this.mode.displayValue();
   }

   public static enum Mode {
      PHASE,
      HOP,
      CLIP;
   }
}
