package dev.dihclient.modules.fun;

import dev.dihclient.module.Category;
import dev.dihclient.module.Module;
import dev.dihclient.setting.BoolSetting;
import dev.dihclient.setting.DoubleSetting;
import dev.dihclient.setting.IntSetting;
import java.awt.Color;
import net.minecraft.class_332;
import net.minecraft.class_3414;
import net.minecraft.class_3417;
import net.minecraft.class_3419;

public class DiscoMode extends Module {
   public final DoubleSetting speed = this.dbl("Speed", "How fast the colours cycle.", 1.0, 0.1, 5.0, 0.1);
   public final IntSetting strength = this.integer("Strength", "Colour intensity.", 70, 10, 200);
   public final IntSetting bpm = this.integer("BPM", "Beats per minute for the pulse.", 120, 40, 200);
   public final BoolSetting pulse = this.bool("Pulse", "Lights flash on every beat.", true);
   public final BoolSetting strobe = this.bool("Strobe", "Hard white flashes. Careful: not good for photosensitive people!", false);
   public final BoolSetting skyFlash = this.bool("Sky Flash", "The sky lights up on every beat.", true);
   public final BoolSetting beatSound = this.bool("Beat Sound", "Plays a bass drum on every beat (only you hear it).", false);
   private long lastBeat = -1L;

   public DiscoMode() {
      super("Disco Mode", Category.FUN, "Rainbow party lights that pulse to a beat. Only you see it.");
   }

   private long beatIndex() {
      return System.currentTimeMillis() / (60000L / this.bpm.get().intValue());
   }

   private float beatPhase() {
      long var1 = 60000L / this.bpm.get().intValue();
      float var3 = (float)(System.currentTimeMillis() % var1) / (float)var1;
      return 1.0F - var3;
   }

   @Override
   public void onTick() {
      long var1 = this.beatIndex();
      if (var1 != this.lastBeat) {
         this.lastBeat = var1;
         if (this.skyFlash.get()) {
            mc.field_1687.method_8509(2);
         }

         if (this.beatSound.get()) {
            mc.field_1687
               .method_8486(
                  mc.field_1724.method_23317(),
                  mc.field_1724.method_23318(),
                  mc.field_1724.method_23321(),
                  (class_3414)class_3417.field_15047.comp_349(),
                  class_3419.field_15247,
                  0.8F,
                  1.0F,
                  false
               );
         }
      }
   }

   @Override
   public void onRender2D(class_332 var1, float var2) {
      int var3 = var1.method_51421();
      int var4 = var1.method_51443();
      double var5 = System.currentTimeMillis() / 1000.0 * this.speed.get();
      float var7 = this.pulse.get() ? this.beatPhase() : 0.5F;
      int var8 = Math.min(255, (int)(this.strength.get().intValue() * (0.45F + 0.55F * var7 * var7)));
      int var9 = Color.HSBtoRGB((float)(var5 % 1.0), 1.0F, 1.0F) & 16777215;
      int var10 = Color.HSBtoRGB((float)((var5 + 0.5) % 1.0), 1.0F, 1.0F) & 16777215;
      var1.method_25296(0, 0, var3, var4, var8 << 24 | var9, var8 << 24 | var10);
      if (this.strobe.get() && var7 > 0.85F) {
         var1.method_25294(0, 0, var3, var4, -1862270977);
      }
   }

   @Override
   public String getInfo() {
      return this.bpm.get() + " BPM";
   }
}
