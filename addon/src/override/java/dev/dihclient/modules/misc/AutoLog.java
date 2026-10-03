package dev.dihclient.modules.misc;

import dev.dihclient.module.Category;
import dev.dihclient.module.Module;
import dev.dihclient.module.ModuleManager;
import dev.dihclient.modules.player.AutoReconnect;
import dev.dihclient.setting.BoolSetting;
import dev.dihclient.setting.DoubleSetting;
import dev.dihclient.setting.EnumSetting;
import dev.dihclient.setting.IntSetting;
import net.minecraft.class_1657;
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
   public final BoolSetting playerEnabled = this.bool("Player Nearby", "Disconnects when another player comes closer than the radius.", false);
   public final IntSetting playerRadius = this.integer("Player Radius", "Blocks.", 32, 4, 128).visibleWhen(this.playerEnabled::get);
   public final BoolSetting disableReconnect = this.bool("Stop Reconnect", "Turns AutoReconnect off when AutoLog triggers.", true);
   public final BoolSetting disableAfter = this.bool("Disable After", "Turns AutoLog off after it triggered.", true);

   public AutoLog() {
      super("Auto Log", Category.MISC, "Disconnects on low health or at a configured Y level.");
   }

   @Override
   public void onTick() {
      if (!mc.field_1724.method_7325()) {   // spectators only: creative counts too, so the Y check works while you build
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

         if (var1 == null && this.playerEnabled.get()) {
            double var5 = (double)this.playerRadius.get().intValue() * this.playerRadius.get().intValue();
            for (class_1657 var7 : mc.field_1687.method_18456()) {
               if (var7 != mc.field_1724 && var7.method_5628() >= 0 && var7.method_5805() && !var7.method_7325() && mc.field_1724.method_5739(var7) * mc.field_1724.method_5739(var7) <= var5) {
                  var1 = "player " + var7.method_5477().getString() + " nearby";
                  break;
               }
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
