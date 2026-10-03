package dev.dihclient.modules.combat;

import dev.dihclient.module.Category;
import dev.dihclient.module.Module;
import dev.dihclient.setting.BoolSetting;
import dev.dihclient.setting.DoubleSetting;
import dev.dihclient.util.CombatUtil;
import dev.dihclient.util.RotationUtil;
import net.minecraft.class_742;

public class AimAssist extends Module {
   public final DoubleSetting range = this.dbl("Range", "Maximum target distance.", 6.0, 1.0, 12.0, 0.1).legacy("aim.range");
   public final DoubleSetting fov = this.dbl("FOV", "Only targets inside this field of view (degrees).", 100.0, 10.0, 360.0, 1.0).legacy("aim.fov");
   public final DoubleSetting speed = this.dbl("Speed", "How strongly the view is nudged each tick.", 0.18, 0.01, 1.0, 0.01).legacy("aim.speed");
   public final BoolSetting onlyAttack = this.bool("Only Attack", "Only assists while the attack key is held.", false).legacy("aim.onlyAttack");

   public AimAssist() {
      super("AimAssist", Category.COMBAT, "Smoothly nudges your view toward the nearest player inside range and FOV.");
   }

   @Override
   public void onTick() {
      if (mc.field_1755 == null) {
         if (!this.onlyAttack.get() || mc.field_1690.field_1886.method_1434()) {
            class_742 var1 = null;
            double var2 = this.fov.get() / 2.0;

            for (class_742 var5 : mc.field_1687.method_18456()) {
               if (CombatUtil.isValidTarget(var5) && !(mc.field_1724.method_5739(var5) > this.range.get())) {
                  double var6 = RotationUtil.angleTo(var5);
                  if (var6 <= var2) {
                     var2 = var6;
                     var1 = var5;
                  }
               }
            }

            if (var1 != null) {
               RotationUtil.smoothLook(RotationUtil.rotationsTo(var1), this.speed.get());
            }
         }
      }
   }
}
