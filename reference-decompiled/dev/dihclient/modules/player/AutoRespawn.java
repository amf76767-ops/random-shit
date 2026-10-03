package dev.dihclient.modules.player;

import dev.dihclient.module.Category;
import dev.dihclient.module.Module;
import dev.dihclient.setting.IntSetting;
import net.minecraft.class_418;

public class AutoRespawn extends Module {
   public final IntSetting delay = this.integer("Delay", "Ticks to wait before respawning.", 8, 0, 100).legacy("autoRespawn.delayTicks");
   private int waited;

   public AutoRespawn() {
      super("AutoRespawn", Category.PLAYER, "Requests a respawn automatically after your configured delay.");
   }

   @Override
   public void onTick() {
      if (!(mc.field_1755 instanceof class_418)) {
         this.waited = 0;
      } else {
         if (++this.waited >= this.delay.get()) {
            mc.field_1724.method_7331();
            mc.method_1507(null);
            this.waited = 0;
         }
      }
   }
}
