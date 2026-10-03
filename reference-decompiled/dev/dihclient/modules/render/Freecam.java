package dev.dihclient.modules.render;

import dev.dihclient.module.Category;
import dev.dihclient.module.Module;
import dev.dihclient.module.ModuleManager;
import dev.dihclient.setting.BoolSetting;
import dev.dihclient.setting.DoubleSetting;
import dev.dihclient.util.KeyUtil;
import dev.dihclient.util.Notifications;
import java.util.Locale;
import net.minecraft.class_1297;
import net.minecraft.class_1675;
import net.minecraft.class_2338;
import net.minecraft.class_2350;
import net.minecraft.class_238;
import net.minecraft.class_239;
import net.minecraft.class_243;
import net.minecraft.class_3532;
import net.minecraft.class_3959;
import net.minecraft.class_3965;
import net.minecraft.class_3966;
import net.minecraft.class_4184;
import net.minecraft.class_5498;
import net.minecraft.class_239.class_240;
import net.minecraft.class_3959.class_242;
import net.minecraft.class_3959.class_3960;

public class Freecam extends Module {
   public final DoubleSetting speed = this.dbl("Speed", "Flying speed. Also changeable with the mouse wheel.", 1.0, 0.05, 10.0, 0.05).legacy("freecam.speed");
   public final DoubleSetting scrollSensitivity = this.dbl(
      "Scroll Sensitivity", "How much the mouse wheel changes the speed. 0 = wheel scrolls the hotbar.", 0.25, 0.0, 2.0, 0.05
   );
   public final BoolSetting staySneaking = this.bool("Stay Crouching", "If you were sneaking when you enabled Freecam, keep sneaking.", true);
   public final BoolSetting toggleOnDamage = this.bool("Toggle On Damage", "Disables Freecam when you take damage.", false);
   public final BoolSetting toggleOnDeath = this.bool("Toggle On Death", "Disables Freecam when you die.", false);
   public final BoolSetting toggleOnLog = this.bool("Toggle On Log", "Disables Freecam when you leave / change world.", true);
   public final BoolSetting reloadChunks = this.bool("Reload Chunks", "Disables cave culling so everything renders while you fly through walls.", true);
   public final BoolSetting playerInteract = this.bool(
      "Player Interaction", "Clicks still go to the block / entity your PLAYER looks at – keep mining (or placing) while the camera flies around.", false
   );
   public final BoolSetting rotate = this.bool("Rotate", "Rotates your player to the block or entity the camera looks at.", false)
      .visibleWhen(() -> !this.playerInteract.get());
   public final BoolSetting staticView = this.bool("Static View", "Turns off view bobbing while in Freecam.", true);
   public class_243 pos = class_243.field_1353;
   public class_243 prevPos = class_243.field_1353;
   public float yaw;
   public float pitch;
   private class_5498 perspective;
   private boolean wasSneaking;
   private boolean previousCulling;
   private boolean previousBob;
   private boolean changedCulling;
   private boolean changedBob;
   private int lastHurtTime;

   public Freecam() {
      super("Freecam", Category.RENDER, "Free camera like Meteor: fly around while your player stays put. Mouse wheel = speed.");
   }

   public static Freecam active() {
      return ModuleManager.on(Freecam.class) ? ModuleManager.of(Freecam.class) : null;
   }

   @Override
   protected void onEnable() {
      if (!inGame()) {
         this.setEnabledSilently(false);
      } else {
         class_4184 var1 = mc.field_1773.method_19418();
         this.pos = this.prevPos = var1.method_71156();
         this.yaw = var1.method_19330();
         this.pitch = var1.method_19329();
         this.perspective = mc.field_1690.method_31044();
         mc.field_1690.method_31043(class_5498.field_26664);
         this.wasSneaking = mc.field_1690.field_1832.method_1434();
         this.lastHurtTime = mc.field_1724.field_6235;
         this.previousCulling = mc.field_1730;
         this.changedCulling = this.reloadChunks.get();
         if (this.changedCulling) {
            mc.field_1730 = false;
            mc.field_1769.method_3292();
         }

         this.previousBob = (Boolean)mc.field_1690.method_42448().method_41753();
         this.changedBob = this.staticView.get();
         if (this.changedBob) {
            mc.field_1690.method_42448().method_41748(false);
         }

         this.unpressMovement();
      }
   }

   @Override
   protected void onDisable() {
      if (mc.field_1690 != null) {
         if (this.perspective != null) {
            mc.field_1690.method_31043(this.perspective);
         }

         this.perspective = null;
         if (this.changedCulling) {
            mc.field_1730 = this.previousCulling;
            if (mc.field_1769 != null) {
               mc.field_1769.method_3292();
            }
         }

         if (this.changedBob) {
            mc.field_1690.method_42448().method_41748(this.previousBob);
         }

         this.changedCulling = false;
         this.changedBob = false;
         this.restoreMovement();
      }
   }

   private void restoreMovement() {
      mc.field_1690.field_1894.method_23481(KeyUtil.isPhysicallyDown(mc.field_1690.field_1894));
      mc.field_1690.field_1881.method_23481(KeyUtil.isPhysicallyDown(mc.field_1690.field_1881));
      mc.field_1690.field_1913.method_23481(KeyUtil.isPhysicallyDown(mc.field_1690.field_1913));
      mc.field_1690.field_1849.method_23481(KeyUtil.isPhysicallyDown(mc.field_1690.field_1849));
      mc.field_1690.field_1903.method_23481(KeyUtil.isPhysicallyDown(mc.field_1690.field_1903));
      mc.field_1690.field_1832.method_23481(KeyUtil.isPhysicallyDown(mc.field_1690.field_1832));
   }

   private void unpressMovement() {
      mc.field_1690.field_1894.method_23481(false);
      mc.field_1690.field_1881.method_23481(false);
      mc.field_1690.field_1913.method_23481(false);
      mc.field_1690.field_1849.method_23481(false);
      mc.field_1690.field_1903.method_23481(false);
      mc.field_1690.field_1832.method_23481(false);
   }

   @Override
   public void onWorldChange() {
      if (this.isEnabled() && this.toggleOnLog.get()) {
         this.setEnabled(false);
      }
   }

   @Override
   public void onTick() {
      if (!this.toggleOnDeath.get() || !mc.field_1724.method_29504() && !(mc.field_1724.method_6032() <= 0.0F)) {
         int var1 = mc.field_1724.field_6235;
         if (this.toggleOnDamage.get() && var1 > this.lastHurtTime) {
            this.setEnabled(false);
         } else {
            this.lastHurtTime = var1;
            this.prevPos = this.pos;
            if (mc.field_1755 == null) {
               double var2 = (KeyUtil.isPhysicallyDown(mc.field_1690.field_1894) ? 1 : 0) - (KeyUtil.isPhysicallyDown(mc.field_1690.field_1881) ? 1 : 0);
               double var4 = (KeyUtil.isPhysicallyDown(mc.field_1690.field_1913) ? 1 : 0) - (KeyUtil.isPhysicallyDown(mc.field_1690.field_1849) ? 1 : 0);
               double var6 = (KeyUtil.isPhysicallyDown(mc.field_1690.field_1903) ? 1 : 0) - (KeyUtil.isPhysicallyDown(mc.field_1690.field_1832) ? 1 : 0);
               double var8 = 0.5 * this.speed.get() * (KeyUtil.isPhysicallyDown(mc.field_1690.field_1867) ? 2 : 1);
               double var10 = Math.toRadians(this.yaw);
               double var12 = -Math.sin(var10);
               double var14 = Math.cos(var10);
               double var16 = Math.cos(var10);
               double var18 = Math.sin(var10);
               double var20 = var12 * var2 + var16 * var4;
               double var22 = var14 * var2 + var18 * var4;
               double var24 = Math.sqrt(var20 * var20 + var22 * var22);
               if (var24 > 1.0) {
                  var20 /= var24;
                  var22 /= var24;
               }

               this.pos = this.pos.method_1031(var20 * var8, var6 * var8, var22 * var8);
            }

            if (this.rotate.get() && !this.playerInteract.get() && mc.field_1765 != null && mc.field_1765.method_17783() != class_240.field_1333) {
               class_243 var26 = mc.field_1765.method_17784();
               class_243 var3 = mc.field_1724.method_33571();
               class_243 var27 = var26.method_1020(var3);
               float var5 = (float)Math.toDegrees(Math.atan2(var27.field_1350, var27.field_1352)) - 90.0F;
               float var28 = (float)(
                  -Math.toDegrees(Math.atan2(var27.field_1351, Math.sqrt(var27.field_1352 * var27.field_1352 + var27.field_1350 * var27.field_1350)))
               );
               mc.field_1724.method_36456(var5);
               mc.field_1724.method_36457(class_3532.method_15363(var28, -90.0F, 90.0F));
            }
         }
      } else {
         this.setEnabled(false);
      }
   }

   public class_243 cameraPos(float var1) {
      return new class_243(
         class_3532.method_16436(var1, this.prevPos.field_1352, this.pos.field_1352),
         class_3532.method_16436(var1, this.prevPos.field_1351, this.pos.field_1351),
         class_3532.method_16436(var1, this.prevPos.field_1350, this.pos.field_1350)
      );
   }

   public void look(double var1, double var3) {
      this.yaw += (float)(var1 * 0.15);
      this.pitch = class_3532.method_15363(this.pitch + (float)(var3 * 0.15), -90.0F, 90.0F);
   }

   public boolean scroll(double var1) {
      if (!(this.scrollSensitivity.get() <= 0.0) && var1 != 0.0) {
         this.speed.set(class_3532.method_15350(this.speed.get() + var1 * this.scrollSensitivity.get(), 0.05, 10.0));
         Notifications.info("Freecam", String.format(Locale.ROOT, "Speed %.2f", this.speed.get()));
         return true;
      } else {
         return false;
      }
   }

   public boolean playerSneaks() {
      return this.staySneaking.get() && this.wasSneaking;
   }

   public static boolean hideHands() {
      return active() != null;
   }

   public void updateCrosshair(float var1) {
      if (!this.playerInteract.get()) {
         if (mc.field_1724 != null && mc.field_1687 != null) {
            class_243 var2 = this.cameraPos(var1);
            class_243 var3 = class_243.method_1030(this.pitch, this.yaw);
            double var4 = mc.field_1724.method_55754();
            double var6 = mc.field_1724.method_55755();
            double var8 = Math.max(var4, var6);
            class_243 var10 = var2.method_1019(var3.method_1021(var8));
            Object var11 = mc.field_1687.method_17742(new class_3959(var2, var10, class_3960.field_17559, class_242.field_1348, mc.field_1724));
            double var12 = var11.method_17784().method_1025(var2);
            if (var12 > var4 * var4) {
               var11 = class_3965.method_17778(
                  var11.method_17784(),
                  class_2350.method_10142(var3.field_1352, var3.field_1351, var3.field_1350),
                  class_2338.method_49638(var11.method_17784())
               );
               var12 = var8 * var8;
            }

            class_238 var14 = new class_238(var2, var10).method_1014(1.0);
            class_3966 var15 = class_1675.method_18075(
               mc.field_1724,
               var2,
               var2.method_1019(var3.method_1021(var6)),
               var14,
               var0 -> !((class_1297)var0).method_7325() && ((class_1297)var0).method_5863() && var0 != mc.field_1724,
               Math.min(var12, var6 * var6)
            );
            class_1297 var16 = null;
            if (var15 != null && var15.method_17784().method_1025(var2) <= var12) {
               var11 = var15;
               var16 = var15.method_17782();
            }

            mc.field_1765 = (class_239)var11;
            mc.field_1692 = var16;
         }
      }
   }

   @Override
   public String getInfo() {
      return String.format(Locale.ROOT, "%.2f", this.speed.get());
   }
}
