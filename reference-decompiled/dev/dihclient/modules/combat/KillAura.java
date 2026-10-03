package dev.dihclient.modules.combat;

import dev.dihclient.module.Category;
import dev.dihclient.module.Module;
import dev.dihclient.setting.BoolSetting;
import dev.dihclient.setting.DoubleSetting;
import dev.dihclient.util.CombatUtil;
import dev.dihclient.util.RotationUtil;
import net.minecraft.class_1657;

public class KillAura extends Module {
   public final DoubleSetting range = this.dbl("Range", "Attack range in blocks.", 4.2, 1.0, 8.0, 0.1).legacy("killAura.range");
   public final DoubleSetting cooldown = this.dbl("Cooldown", "Required attack-cooldown progress before swinging (1 = full charge).", 0.92, 0.0, 1.0, 0.01)
      .legacy("killAura.cooldown");
   public final BoolSetting rotate = this.bool("Rotate", "Turns your view toward the target.", true).legacy("killAura.rotate");
   public final DoubleSetting rotateSpeed = this.dbl("Rotate Speed", "Rotation smoothing (1 = instant).", 0.32, 0.05, 1.0, 0.01)
      .legacy("killAura.rotateSpeed")
      .visibleWhen(this.rotate::get);
   public final BoolSetting onlyWithWeapon = this.bool("Only Weapon", "Only attacks while holding a sword, axe, mace or trident.", false);

   public KillAura() {
      super("KillAura", Category.COMBAT, "Attacks the nearest player inside the configured range when the attack cooldown is ready.");
   }

   @Override
   protected void onDisable() {
      CombatUtil.setTarget(null);
   }

   @Override
   public void onTick() {
      if (mc.field_1755 != null) {
         CombatUtil.setTarget(null);
      } else {
         class_1657 var1 = CombatUtil.findTarget(this.range.get());
         CombatUtil.setTarget(var1);
         if (var1 != null && (!this.onlyWithWeapon.get() || CombatUtil.weaponScore(mc.field_1724.method_6047(), false) > 0)) {
            if (this.rotate.get()) {
               RotationUtil.smoothLook(RotationUtil.rotationsTo(var1), this.rotateSpeed.get());
            }

            CombatUtil.attack(var1, this.cooldown.getFloat());
         }
      }
   }

   @Override
   public String getInfo() {
      class_1657 var1 = CombatUtil.target();
      return var1 == null ? null : var1.method_5477().getString();
   }
}
