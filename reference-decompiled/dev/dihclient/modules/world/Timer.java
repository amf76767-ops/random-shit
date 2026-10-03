package dev.dihclient.modules.world;

import dev.dihclient.module.Category;
import dev.dihclient.module.Module;
import dev.dihclient.setting.DoubleSetting;

public class Timer extends Module {
   public final DoubleSetting speed = this.dbl("Speed", "Client tick speed multiplier (1 = vanilla).", 1.0, 0.1, 20.0, 0.05).legacy("timer.speed");

   public Timer() {
      super("Timer", Category.WORLD, "Scales the client tick rate. Values above 1 speed the game up, below 1 slow it down.");
   }

   @Override
   public String getInfo() {
      return this.speed.displayValue() + "x";
   }
}
