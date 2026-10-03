package dev.dihclient.modules.fun;

import dev.dihclient.module.Category;
import dev.dihclient.module.Module;
import dev.dihclient.module.ModuleManager;
import dev.dihclient.setting.BoolSetting;
import dev.dihclient.setting.DoubleSetting;
import dev.dihclient.util.MoveUtil;
import net.minecraft.class_243;

public class DrunkMode extends Module {
   public final DoubleSetting level = this.dbl("Level", "How drunk you are.", 1.0, 0.1, 3.0, 0.1);
   public final BoolSetting sway = this.bool("Camera Sway", "The camera slowly swings around.", true);
   public final BoolSetting warp = this.bool("Screen Warp", "Wobbly screen distortion like nausea.", true);
   public final BoolSetting stagger = this.bool("Stagger", "You drift sideways a bit while walking.", true);

   public DrunkMode() {
      super("Drunk Mode", Category.FUN, "Wobbly camera, warped screen and staggering steps.");
   }

   private static double t() {
      return System.currentTimeMillis() / 1000.0;
   }

   public static float yawOffset() {
      if (!ModuleManager.on(DrunkMode.class)) {
         return 0.0F;
      } else {
         DrunkMode var0 = ModuleManager.of(DrunkMode.class);
         if (!var0.sway.get()) {
            return 0.0F;
         } else {
            double var1 = t();
            return (float)((Math.sin(var1 * 0.9) * 6.0 + Math.sin(var1 * 2.3) * 2.0) * var0.level.get());
         }
      }
   }

   public static float pitchOffset() {
      if (!ModuleManager.on(DrunkMode.class)) {
         return 0.0F;
      } else {
         DrunkMode var0 = ModuleManager.of(DrunkMode.class);
         if (!var0.sway.get()) {
            return 0.0F;
         } else {
            double var1 = t();
            return (float)((Math.sin(var1 * 0.7 + 1.3) * 4.0 + Math.cos(var1 * 1.9) * 1.5) * var0.level.get());
         }
      }
   }

   @Override
   public void onTick() {
      if (this.warp.get()) {
         float var1 = (float)Math.min(1.0, 0.35 * this.level.get());
         mc.field_1724.field_44912 = var1;
         mc.field_1724.field_44911 = var1;
      }

      if (this.stagger.get() && mc.field_1724.method_24828() && MoveUtil.isMoving()) {
         double var8 = t();
         double var3 = Math.sin(var8 * 1.3) * 0.03 * this.level.get();
         double var5 = Math.toRadians(mc.field_1724.method_36454());
         class_243 var7 = mc.field_1724.method_18798();
         mc.field_1724.method_18800(var7.field_1352 + Math.cos(var5) * var3, var7.field_1351, var7.field_1350 + Math.sin(var5) * var3);
      }
   }

   @Override
   protected void onDisable() {
      if (mc.field_1724 != null) {
         mc.field_1724.field_44911 = 0.0F;
         mc.field_1724.field_44912 = 0.0F;
      }
   }

   @Override
   public String getInfo() {
      return String.format("%.1f‰", this.level.get());
   }
}
