package dev.dihclient.modules.render;

import dev.dihclient.mixin.accessor.SimpleOptionAccessor;
import dev.dihclient.module.Category;
import dev.dihclient.module.Module;
import dev.dihclient.setting.BoolSetting;

public class Fullbright extends Module {
   public final BoolSetting suppressDarkness = this.bool("No Darkness", "Suppresses the Darkness effect pulsing.", true).legacy("fullbright.suppressDarkness");
   private Double previousGamma;

   public Fullbright() {
      super("Fullbright", Category.RENDER, "Maximum brightness everywhere.");
   }

   @Override
   public void onTick() {
      if (this.previousGamma == null) {
         this.previousGamma = (Double)mc.field_1690.method_42473().method_41753();
      }

      ((SimpleOptionAccessor)mc.field_1690.method_42473()).dih$setValueRaw(16.0);
   }

   @Override
   protected void onDisable() {
      if (this.previousGamma != null && mc.field_1690 != null) {
         ((SimpleOptionAccessor)mc.field_1690.method_42473()).dih$setValueRaw(Math.min(1.0, this.previousGamma));
      }

      this.previousGamma = null;
   }
}
