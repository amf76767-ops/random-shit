package dev.dihclient.modules.combat;

import dev.dihclient.module.Category;
import dev.dihclient.module.Module;
import dev.dihclient.setting.BoolSetting;
import dev.dihclient.setting.DoubleSetting;
import dev.dihclient.setting.IntSetting;
import dev.dihclient.util.CombatUtil;
import dev.dihclient.util.InvUtil;
import dev.dihclient.util.ItemUtil;
import dev.dihclient.util.KeyUtil;
import dev.dihclient.util.Notifications;
import dev.dihclient.util.RotationUtil;
import net.minecraft.class_1268;
import net.minecraft.class_1657;
import net.minecraft.class_1799;
import net.minecraft.class_243;

public class MaceCombo extends Module {
   public final DoubleSetting range = this.dbl("Range", "Starts the combo when a player is this close (blocks).", 7.0, 3.0, 16.0, 0.5);
   public final DoubleSetting minFall = this.dbl(
      "Min Fall", "Only smashes after falling at least this many blocks (mace damage grows with the fall).", 3.0, 1.5, 20.0, 0.5
   );
   public final BoolSetting autoJump = this.bool("Jump First", "Jumps and throws the wind charge at the top of the jump (launches much higher).", true);
   public final IntSetting cooldown = this.integer("Cooldown", "Ticks to wait after a combo.", 40, 10, 200);
   public final BoolSetting restoreSlot = this.bool("Restore Slot", "Goes back to the slot you had before.", true);
   private MaceCombo.Stage stage = MaceCombo.Stage.IDLE;
   private int ticks;
   private int wait;
   private int prevSlot = -1;
   private double launchY;
   private double peakY;
   private boolean thrown;
   private class_1657 target;

   public MaceCombo() {
      super("MaceCombo", Category.COMBAT, "Wind charge launch + mace smash: jumps up with a wind charge, dives onto the target and hits with the mace.");
   }

   private static boolean isMace(class_1799 var0) {
      return !var0.method_7960() && ItemUtil.id(var0).equals("minecraft:mace");
   }

   private static boolean isWind(class_1799 var0) {
      return !var0.method_7960() && ItemUtil.id(var0).equals("minecraft:wind_charge");
   }

   @Override
   protected void onDisable() {
      this.finish(false);
   }

   private void releaseKeys() {
      if (mc.field_1690 != null) {
         mc.field_1690.field_1894.method_23481(KeyUtil.isPhysicallyDown(mc.field_1690.field_1894));
         mc.field_1690.field_1903.method_23481(KeyUtil.isPhysicallyDown(mc.field_1690.field_1903));
      }
   }

   private void finish(boolean var1) {
      this.releaseKeys();
      if (this.restoreSlot.get() && this.prevSlot >= 0 && mc.field_1724 != null) {
         InvUtil.select(this.prevSlot);
      }

      this.prevSlot = -1;
      this.stage = MaceCombo.Stage.IDLE;
      this.wait = this.cooldown.get();
      this.thrown = false;
   }

   private void aimAt(class_1657 var1, boolean var2, float var3) {
      float[] var4 = RotationUtil.rotationsTo(var1);
      mc.field_1724.method_36456(RotationUtil.approachAngle(mc.field_1724.method_36454(), var4[0], var3));
      if (!var2) {
         mc.field_1724.method_36457(RotationUtil.approachAngle(mc.field_1724.method_36455(), var4[1], var3));
      }
   }

   @Override
   public void onTick() {
      if (inGame() && !mc.field_1724.method_31549().field_7479) {
         if (this.wait > 0) {
            this.wait--;
         }

         class_243 var1 = mc.field_1724.method_18798();
         double var2 = mc.field_1724.method_23318();
         switch (this.stage) {
            case IDLE:
               if (this.wait > 0 || mc.field_1755 != null || !mc.field_1724.method_24828()) {
                  return;
               }

               class_1657 var4 = CombatUtil.findTarget(this.range.get());
               if (var4 == null || InvUtil.findHotbar(MaceCombo::isMace) < 0 || InvUtil.findHotbar(MaceCombo::isWind) < 0) {
                  return;
               }

               this.target = var4;
               this.prevSlot = InvUtil.selectedSlot();
               this.stage = MaceCombo.Stage.LAUNCH;
               this.ticks = 0;
               this.thrown = false;
               break;
            case LAUNCH:
               this.ticks++;
               if (this.target == null || !this.target.method_5805() || this.ticks > 30) {
                  this.finish(false);
                  return;
               }

               mc.field_1724.method_36457(90.0F);
               if (this.autoJump.get() && mc.field_1724.method_24828() && this.ticks < 6) {
                  mc.field_1690.field_1903.method_23481(true);
                  return;
               }

               if (this.autoJump.get() && !mc.field_1724.method_24828() && var1.field_1351 > 0.12) {
                  mc.field_1690.field_1903.method_23481(false);
                  return;
               }

               int var5 = InvUtil.findHotbar(MaceCombo::isWind);
               if (var5 < 0) {
                  this.finish(false);
                  return;
               }

               mc.field_1690.field_1903.method_23481(KeyUtil.isPhysicallyDown(mc.field_1690.field_1903));
               this.launchY = var2;
               this.peakY = var2;
               InvUtil.select(var5);
               mc.field_1761.method_2919(mc.field_1724, class_1268.field_5808);
               mc.field_1724.method_6104(class_1268.field_5808);
               this.stage = MaceCombo.Stage.RISE;
               this.ticks = 0;
               break;
            case RISE:
               this.ticks++;
               this.peakY = Math.max(this.peakY, var2);
               if (this.target == null || !this.target.method_5805() || this.ticks > 70) {
                  this.finish(false);
                  return;
               }

               this.aimAt(this.target, true, 30.0F);
               mc.field_1690.field_1894.method_23481(true);
               if (this.ticks >= 2 && var1.field_1351 < -0.05 && this.peakY - this.launchY >= 1.5) {
                  int var9 = InvUtil.findHotbar(MaceCombo::isMace);
                  if (var9 < 0) {
                     this.finish(false);
                     return;
                  }

                  InvUtil.select(var9);
                  this.stage = MaceCombo.Stage.SLAM;
                  this.ticks = 0;
               }
               break;
            case SLAM:
               this.ticks++;
               this.peakY = Math.max(this.peakY, var2);
               if (this.target == null || !this.target.method_5805() || this.ticks > 80) {
                  this.finish(false);
                  return;
               }

               this.aimAt(this.target, false, 40.0F);
               mc.field_1690.field_1894.method_23481(true);
               double var6 = this.peakY - var2;
               float var8 = mc.field_1724.method_5739(this.target);
               if (var6 >= this.minFall.get() && var8 <= 3.3F && CombatUtil.attack(this.target, 0.5F)) {
                  Notifications.info("MaceCombo", "Smash from " + (int)var6 + " blocks");
                  this.finish(true);
               } else if (mc.field_1724.method_24828() && this.ticks > 3) {
                  this.finish(false);
               }
         }
      }
   }

   @Override
   public String getInfo() {
      return this.stage == MaceCombo.Stage.IDLE ? null : this.stage.name().toLowerCase();
   }

   private static enum Stage {
      IDLE,
      LAUNCH,
      RISE,
      SLAM;
   }
}
