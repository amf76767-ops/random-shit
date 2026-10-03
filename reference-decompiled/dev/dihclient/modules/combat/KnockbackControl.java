package dev.dihclient.modules.combat;

import dev.dihclient.module.Category;
import dev.dihclient.module.Module;
import dev.dihclient.setting.BoolSetting;
import dev.dihclient.setting.DoubleSetting;
import dev.dihclient.setting.IntSetting;
import dev.dihclient.util.InvUtil;
import dev.dihclient.util.WalkSafety;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.class_1802;

public class KnockbackControl extends Module {
   public final DoubleSetting horizontal = this.dbl("Horizontal", "Multiplier for incoming horizontal knockback.", 0.5, 0.0, 1.0, 0.01)
      .legacy("knockback.horizontal");
   public final DoubleSetting vertical = this.dbl("Vertical", "Multiplier for incoming vertical knockback.", 0.5, 0.0, 1.0, 0.01).legacy("knockback.vertical");
   public final BoolSetting separateExplosions = this.bool(
      "Separate Explosions", "Own multipliers for explosions (TNT, crystals, beds, anchors, wind charges).", false
   );
   public final DoubleSetting explosionH = this.dbl("Explosion Horizontal", "Multiplier for horizontal explosion knockback.", 0.5, 0.0, 1.0, 0.01)
      .visibleWhen(this.separateExplosions::get);
   public final DoubleSetting explosionV = this.dbl("Explosion Vertical", "Multiplier for vertical explosion knockback.", 0.5, 0.0, 1.0, 0.01)
      .visibleWhen(this.separateExplosions::get);
   public final BoolSetting ignoreLiquids = this.bool(
      "Ignore In Water/Lava", "No knockback at all while you are in water or lava – nothing pushes you out of a clutch or into lava.", true
   );
   public final BoolSetting keepWind = this.bool(
      "Keep Wind Charge Boost", "Your own wind charge boosts stay at full strength (no reduction for a moment after you used one).", true
   );
   public final IntSetting chance = this.integer(
      "Chance", "Chance in percent that a hit is reduced at all. Below 100 it looks less like a client.", 100, 0, 100
   );
   private int ticks;
   private int lastWind = -1000;
   private int windCount = -1;

   public KnockbackControl() {
      super("KnockbackControl", Category.COMBAT, "Velocity: scales knockback and explosions, ignores it in liquids, keeps your own wind charge boost.");
   }

   @Override
   public void onTick() {
      this.ticks++;
      if (mc.field_1724 != null) {
         int var1 = InvUtil.count(var0 -> var0.method_31574(class_1802.field_49098));
         if (this.windCount >= 0 && var1 < this.windCount) {
            this.lastWind = this.ticks;
         }

         this.windCount = var1;
      }
   }

   @Override
   protected void onEnable() {
      this.windCount = -1;
   }

   public double[] scale(boolean var1) {
      if (mc.field_1724 == null) {
         return new double[]{1.0, 1.0};
      } else if (this.chance.get() < 100 && ThreadLocalRandom.current().nextInt(100) >= this.chance.get()) {
         return new double[]{1.0, 1.0};
      } else if (var1 && this.keepWind.get() && this.ticks - this.lastWind <= 15) {
         return new double[]{1.0, 1.0};
      } else if (!this.ignoreLiquids.get() || !mc.field_1724.method_5799() && !WalkSafety.lava(mc.field_1724.method_24515())) {
         return var1 && this.separateExplosions.get()
            ? new double[]{this.explosionH.get(), this.explosionV.get()}
            : new double[]{this.horizontal.get(), this.vertical.get()};
      } else {
         return new double[]{0.0, 0.0};
      }
   }

   @Override
   public String getInfo() {
      return String.format(Locale.ROOT, "H%.0f%% V%.0f%%", this.horizontal.get() * 100.0, this.vertical.get() * 100.0);
   }
}
