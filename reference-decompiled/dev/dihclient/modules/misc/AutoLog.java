package dev.dihclient.modules.misc;

import dev.dihclient.module.Category;
import dev.dihclient.module.Module;
import dev.dihclient.module.ModuleManager;
import dev.dihclient.modules.player.AutoReconnect;
import dev.dihclient.setting.BoolSetting;
import dev.dihclient.setting.DoubleSetting;
import dev.dihclient.setting.EnumSetting;
import dev.dihclient.setting.IntSetting;
import net.minecraft.class_2561;

public class AutoLog extends Module {
   public final BoolSetting healthEnabled = this.bool("Health Check", "Disconnects on low health.", true).legacy("autoLog.health.enabled");
   public final DoubleSetting health = this.dbl("Health", "Health threshold.", 6.0, 1.0, 20.0, 0.5)
      .legacy("autoLog.health")
      .visibleWhen(this.healthEnabled::get);
   public final BoolSetting heightEnabled = this.bool(
         "Height Check", "Disconnects when you go below this Y level (off by default – mining would kick you).", false
      )
      .legacy("autoLog.height.enabled");
   public final IntSetting height = this.integer("Height", "Y level threshold.", 10, -64, 320).legacy("autoLog.height").visibleWhen(this.heightEnabled::get);
   public final EnumSetting<AutoLog.HeightMode> heightMode = this.mode("Height Mode", "Disconnect below or above the Y level.", AutoLog.HeightMode.BELOW)
      .legacy("autoLog.heightMode")
      .visibleWhen(this.heightEnabled::get);
   public final BoolSetting disableReconnect = this.bool("Stop Reconnect", "Turns AutoReconnect off when AutoLog triggers.", true);
   public final BoolSetting disableAfter = this.bool("Disable After", "Turns AutoLog off after it triggered.", true);

   public AutoLog() {
      super("Auto Log", Category.MISC, "Disconnects on low health or at a configured Y level.");
   }

   @Override
   public void onTick() {
      if (!mc.field_1724.method_68878() && !mc.field_1724.method_7325()) {
         String var1 = null;
         float var2 = mc.field_1724.method_6032();
         if (this.healthEnabled.get() && var2 > 0.0F && var2 <= this.health.get()) {
            var1 = "health " + String.format("%.1f", var2);
         }

         double var3 = mc.field_1724.method_23318();
         if (var1 == null && this.heightEnabled.get()) {
            if (this.heightMode.is(AutoLog.HeightMode.BELOW) && var3 < this.height.get().intValue()) {
               var1 = "Y " + (int)var3 + " below " + this.height.get();
            }

            if (this.heightMode.is(AutoLog.HeightMode.ABOVE) && var3 > this.height.get().intValue()) {
               var1 = "Y " + (int)var3 + " above " + this.height.get();
            }
         }

         if (var1 != null) {
            if (this.disableReconnect.get() && ModuleManager.on(AutoReconnect.class)) {
               ModuleManager.of(AutoReconnect.class).setEnabled(false);
            }

            if (this.disableAfter.get()) {
               this.setEnabled(false);
            }

            mc.field_1687.method_8525(class_2561.method_43470("[DIHClient] Auto Log: " + var1));
         }
      }
   }

   public static enum HeightMode {
      BELOW,
      ABOVE;
   }
}
