package dev.dihclient.modules.player;

import dev.dihclient.module.Category;
import dev.dihclient.module.Module;
import dev.dihclient.module.ModuleManager;
import dev.dihclient.setting.IntSetting;
import net.minecraft.class_412;
import net.minecraft.class_419;
import net.minecraft.class_442;
import net.minecraft.class_500;
import net.minecraft.class_639;
import net.minecraft.class_642;

public class AutoReconnect extends Module implements ModuleManager.MenuTicking {
   public final IntSetting delay = this.integer("Delay", "Seconds to wait on the disconnect screen.", 5, 1, 120).legacy("autoReconnect.delaySeconds");
   private class_642 lastServer;
   private int ticks;

   public AutoReconnect() {
      super("AutoReconnect", Category.PLAYER, "Reconnects to the last multiplayer server after a delay when you get disconnected.");
   }

   @Override
   public void onTick() {
      class_642 var1 = mc.method_1558();
      if (var1 != null && mc.field_1687 != null) {
         this.lastServer = var1;
      }

      if (mc.field_1755 instanceof class_419 && this.lastServer != null) {
         if (++this.ticks >= this.delay.get() * 20) {
            this.ticks = 0;
            class_642 var2 = this.lastServer;
            class_412.method_36877(new class_500(new class_442()), mc, class_639.method_2950(var2.field_3761), var2, false, null);
         }
      } else {
         this.ticks = 0;
      }
   }

   public int secondsLeft() {
      return mc.field_1755 instanceof class_419 && this.lastServer != null ? Math.max(0, this.delay.get() - this.ticks / 20) : -1;
   }
}
