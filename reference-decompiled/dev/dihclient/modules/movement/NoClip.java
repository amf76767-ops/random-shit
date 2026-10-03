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
import net.minecraft.class_1297;
import net.minecraft.class_2246;
import net.minecraft.class_2338;
import net.minecraft.class_238;
import net.minecraft.class_243;
import net.minecraft.class_2680;
import net.minecraft.class_634;
import net.minecraft.class_638;
import net.minecraft.class_746;
import net.minecraft.class_2828.class_2829;
import net.minecraft.class_2828.class_2831;

public class NoClip extends Module {
   public final EnumSetting<NoClip.Mode> mode = this.mode(
      "Method",
      "Clip: server-safe, teleports through floors/ceilings (Sneak = down, Sneak+Jump = up). Fall/Fly: client-side only, works in singleplayer or on servers without movement checks.",
      NoClip.Mode.CLIP
   );
   public final IntSetting range = this.integer("Range", "Max blocks per clip. Vanilla accepts about 20.", 12, 2, 20)
      .visibleWhen(() -> this.mode.get() == NoClip.Mode.CLIP);
   public final BoolSetting needFloor = this.bool("Need Floor", "Only clip to spots with solid ground under them (never into a cave drop or lava).", true)
      .visibleWhen(() -> this.mode.get() == NoClip.Mode.CLIP);
   public final BoolSetting doubleTap = this.bool(
         "Double Tap", "Clip down only on a double tap of Sneak, so normal sneaking at an edge never drops you into a cave.", true
      )
      .visibleWhen(() -> this.mode.get() == NoClip.Mode.CLIP);
   public final BoolSetting clipUp = this.bool("Clip Up", "Sneak + Jump clips upward through the ceiling.", true)
      .visibleWhen(() -> this.mode.get() == NoClip.Mode.CLIP);
   public final DoubleSetting speed = this.dbl("Speed", "Horizontal speed in Fly mode (blocks/tick).", 0.4, 0.05, 3.0, 0.05)
      .visibleWhen(() -> this.mode.get() == NoClip.Mode.FLY);
   public final DoubleSetting vertical = this.dbl("Vertical", "Up/down speed in Fly mode.", 0.4, 0.05, 3.0, 0.05)
      .visibleWhen(() -> this.mode.get() == NoClip.Mode.FLY);
   public final DoubleSetting fallSpeed = this.dbl(
         "Max Fall Speed", "Caps the fall speed in Fall mode so you can stop in time (3.9 = vanilla).", 1.0, 0.1, 3.9, 0.1
      )
      .visibleWhen(() -> this.mode.get() == NoClip.Mode.FALL);
   private boolean sneakWas;
   private boolean jumpWas;
   private int cooldown;
   private int lastSneakTap = -100;

   public NoClip() {
      super(
         "NoClip",
         Category.MOVEMENT,
         "Go through blocks. Clip mode works on normal servers: Sneak drops you through the floor, Sneak+Jump pops you through the ceiling. Fall/Fly are client-side phasing."
      );
   }

   public static boolean active() {
      return !ModuleManager.on(NoClip.class) ? false : ModuleManager.of(NoClip.class).mode.get() != NoClip.Mode.CLIP;
   }

   @Override
   protected void onEnable() {
      this.sneakWas = true;
      this.jumpWas = true;
      this.cooldown = 0;
   }

   @Override
   protected void onDisable() {
      if (mc.field_1724 != null) {
         mc.field_1724.field_5960 = mc.field_1724.method_7325();
      }
   }

   @Override
   public void onTick() {
      if (this.mode.get() == NoClip.Mode.CLIP) {
         this.tickClip();
      } else {
         this.tickPhase();
      }
   }

   private void tickClip() {
      class_746 var1 = mc.field_1724;
      if (var1.field_5960 && !var1.method_7325()) {
         var1.field_5960 = false;
      }

      boolean var2 = mc.field_1690.field_1832.method_1434();
      boolean var3 = mc.field_1690.field_1903.method_1434();
      boolean var4 = var2 && !this.sneakWas;
      if (var4 && this.doubleTap.get()) {
         int var5 = var1.field_6012;
         boolean var6 = var5 - this.lastSneakTap <= 8;
         this.lastSneakTap = var6 ? -100 : var5;
         var4 = var6;
      }

      boolean var7 = var3 && !this.jumpWas;
      this.sneakWas = var2;
      this.jumpWas = var3;
      if (this.cooldown > 0) {
         this.cooldown--;
      } else if (var1.method_5854() == null) {
         if (var7 && var2 && this.clipUp.get()) {
            this.clip(1);
         } else if (var4 && !var3 && var1.method_24828()) {
            this.clip(-1);
         }
      }
   }

   private void clip(int var1) {
      class_746 var2 = mc.field_1724;
      double var3 = findTarget(var2, var1, this.range.get(), this.needFloor.get(), 0.0);
      if (Double.isNaN(var3)) {
         Notifications.info("NoClip", var1 > 0 ? "No free space above within range." : "No free space below within range.");
         this.cooldown = 10;
      } else {
         double var5 = var2.method_23317();
         double var7 = var2.method_23321();
         double var9 = Math.abs(var3 - var2.method_23318());
         int var11 = Math.max(1, (int)Math.ceil(var9 / 9.5));
         class_634 var12 = mc.method_1562();
         if (var12 != null) {
            for (int var13 = 0; var13 < var11 - 1; var13++) {
               var12.method_52787(new class_2831(var2.method_36454(), var2.method_36455(), var2.method_24828(), false));
            }

            var2.method_5814(var5, var3, var7);
            var2.method_18800(0.0, 0.0, 0.0);
            var2.field_6017 = 0.0;
            var12.method_52787(new class_2829(var5, var3, var7, this.needFloor.get(), false));
            this.cooldown = 4;
         }
      }
   }

   public static double findTarget(class_1297 var0, int var1, int var2, boolean var3, double var4) {
      class_638 var6 = mc.field_1687;
      class_238 var7 = var0.method_5829();
      if (var4 > 0.0) {
         var7 = new class_238(var7.field_1323, var7.field_1322, var7.field_1321, var7.field_1320, var7.field_1325 + var4, var7.field_1324);
      }

      double var8 = var0.method_23318();
      double var10 = Math.floor(var8 + 1.0E-4);
      boolean var12 = false;

      for (int var13 = 1; var13 <= var2; var13++) {
         double var14 = var10 + var1 * var13;
         if (var14 <= var6.method_31607()) {
            return Double.NaN;
         }

         class_238 var16 = var7.method_989(0.0, var14 - var8, 0.0);
         if (!var6.method_8587(var0, var16)) {
            var12 = true;
         } else if (var12) {
            if (!var3) {
               return var14;
            }

            if (!var6.method_8587(var0, var16.method_989(0.0, -0.5, 0.0)) && !lava(var6, var0, var14)) {
               return var14;
            }
         }
      }

      return Double.NaN;
   }

   private static boolean lava(class_638 var0, class_1297 var1, double var2) {
      class_243 var4 = var1.method_73189();

      for (int var5 = -1; var5 <= 1; var5++) {
         class_2680 var6 = var0.method_8320(class_2338.method_49637(var4.field_1352, var2 + var5, var4.field_1350));
         if (var6.method_27852(class_2246.field_10164)) {
            return true;
         }
      }

      return false;
   }

   private void tickPhase() {
      mc.field_1724.field_5960 = true;
      mc.field_1724.field_6017 = 0.0;
      mc.field_1724.method_24830(false);
      class_243 var1 = mc.field_1724.method_18798();
      if (this.mode.get() == NoClip.Mode.FLY) {
         double var2 = 0.0;
         if (mc.field_1690.field_1903.method_1434()) {
            var2 = this.vertical.get();
         } else if (mc.field_1690.field_1832.method_1434()) {
            var2 = -this.vertical.get();
         }

         double[] var4 = MoveUtil.isMoving() ? MoveUtil.direction(this.speed.get()) : new double[]{0.0, 0.0};
         mc.field_1724.method_18800(var4[0], var2, var4[1]);
      } else if (var1.field_1351 < -this.fallSpeed.get()) {
         mc.field_1724.method_18800(var1.field_1352, -this.fallSpeed.get(), var1.field_1350);
      }
   }

   @Override
   public String getInfo() {
      return this.mode.displayValue();
   }

   public static enum Mode {
      CLIP,
      FALL,
      FLY;
   }
}
