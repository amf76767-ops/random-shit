package dev.dihclient.modules.render;

import dev.dihclient.mixin.accessor.GameRendererAccessor;
import dev.dihclient.module.Category;
import dev.dihclient.module.Module;
import dev.dihclient.setting.BoolSetting;

public class NoRender extends Module {
   public final BoolSetting hurtCam = this.bool("Hurt Cam", "Removes the camera tilt when you take damage.", true).legacy("noRender.hurtCam");
   public final BoolSetting distortion = this.bool("Distortion", "Removes nausea / portal screen distortion.", true).legacy("noRender.distortion");

   public NoRender() {
      super("NoRender", Category.RENDER, "Suppresses distracting screen effects.");
   }

   @Override
   public void onTick() {
      if (this.distortion.get()) {
         GameRendererAccessor var1 = (GameRendererAccessor)mc.field_1773;
         var1.dih$setNauseaEffectTime(0.0F);
         var1.dih$setNauseaEffectSpeed(0.0F);
      }
   }
}
