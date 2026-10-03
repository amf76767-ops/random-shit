package dev.dihclient.modules.combat;

import dev.dihclient.module.Category;
import dev.dihclient.module.Module;
import dev.dihclient.setting.BoolSetting;
import dev.dihclient.setting.DoubleSetting;
import dev.dihclient.setting.IntSetting;
import dev.dihclient.util.CombatUtil;
import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.class_1657;
import net.minecraft.class_3966;

public class TriggerBot extends Module {
   public final DoubleSetting range = this.dbl("Range", "Maximum distance to the player under the crosshair.", 4.0, 1.0, 8.0, 0.1).legacy("trigger.range");
   public final BoolSetting cooldownCheck = this.bool("Cooldown Check", "Waits for the attack cooldown.", true).legacy("trigger.cooldownCheck");
   public final DoubleSetting cooldown = this.dbl("Cooldown", "Required cooldown progress.", 0.9, 0.0, 1.0, 0.01)
      .legacy("trigger.cooldown")
      .visibleWhen(this.cooldownCheck::get);
   public final IntSetting minDelay = this.integer("Min Delay", "Minimum random delay in ticks.", 0, 0, 20).legacy("trigger.minDelayTicks");
   public final IntSetting maxDelay = this.integer("Max Delay", "Maximum random delay in ticks.", 2, 0, 20).legacy("trigger.maxDelayTicks");
   private int wait = -1;

   public TriggerBot() {
      super("TriggerBot", Category.COMBAT, "Attacks a player under your crosshair with a configurable randomized click delay.");
   }

   @Override
   public void onTick() {
      if (mc.field_1755 == null) {
         if (mc.field_1765 instanceof class_3966 var1
            && var1.method_17782() instanceof class_1657 var2
            && CombatUtil.isValidTarget(var2)
            && !(mc.field_1724.method_5739(var2) > this.range.get())) {
            if (this.wait < 0) {
               int var6 = Math.min(this.minDelay.get(), this.maxDelay.get());
               int var4 = Math.max(this.minDelay.get(), this.maxDelay.get());
               this.wait = var6 + (var4 > var6 ? ThreadLocalRandom.current().nextInt(var4 - var6 + 1) : 0);
            }

            if (this.wait-- <= 0) {
               if (CombatUtil.attack(var2, this.cooldownCheck.get() ? this.cooldown.getFloat() : 0.0F)) {
                  this.wait = -1;
               }
            }
         } else {
            this.wait = -1;
         }
      }
   }
}
