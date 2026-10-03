package dev.dihclient.modules.misc;

import dev.dihclient.module.Category;
import dev.dihclient.module.Module;
import dev.dihclient.setting.BoolSetting;
import dev.dihclient.setting.DoubleSetting;
import dev.dihclient.setting.IntSetting;
import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.class_1268;

public class AntiAfk extends Module {
   public final IntSetting minSeconds = this.integer("Min Seconds", "Minimum pause between actions.", 7, 1, 300).legacy("antiAfk.minSeconds");
   public final IntSetting maxSeconds = this.integer("Max Seconds", "Maximum pause between actions.", 18, 1, 600).legacy("antiAfk.maxSeconds");
   public final BoolSetting jump = this.bool("Jump", "Jumps occasionally.", true).legacy("antiAfk.jump");
   public final BoolSetting rotate = this.bool("Rotate", "Turns slightly.", true).legacy("antiAfk.rotate");
   public final DoubleSetting yaw = this.dbl("Yaw", "Maximum rotation per action (degrees).", 18.0, 1.0, 180.0, 1.0).legacy("antiAfk.yaw");
   public final BoolSetting swing = this.bool("Swing", "Swings your arm.", false);
   private int wait;

   public AntiAfk() {
      super("AntiAFK", Category.MISC, "Randomized jump, rotation and swing actions at configurable intervals.");
   }

   @Override
   public void onTick() {
      if (this.wait-- <= 0) {
         ThreadLocalRandom var1 = ThreadLocalRandom.current();
         int var2 = Math.min(this.minSeconds.get(), this.maxSeconds.get());
         int var3 = Math.max(this.minSeconds.get(), this.maxSeconds.get());
         this.wait = (var2 + (var3 > var2 ? var1.nextInt(var3 - var2 + 1) : 0)) * 20;
         if (this.jump.get() && mc.field_1724.method_24828()) {
            mc.field_1724.method_6043();
         }

         if (this.rotate.get()) {
            mc.field_1724.method_36456(mc.field_1724.method_36454() + (float)((var1.nextDouble() * 2.0 - 1.0) * this.yaw.get()));
         }

         if (this.swing.get()) {
            mc.field_1724.method_6104(class_1268.field_5808);
         }
      }
   }
}
