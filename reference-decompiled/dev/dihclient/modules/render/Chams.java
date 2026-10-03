package dev.dihclient.modules.render;

import dev.dihclient.module.Category;
import dev.dihclient.module.Module;
import dev.dihclient.setting.ColorSetting;

public class Chams extends Module {
   public final ColorSetting color = this.color("Color", "Outline colour of players.", -43521);
   public final ColorSetting friendColor = this.color("Friend Color", "Outline colour of friends.", -11740828);

   public Chams() {
      super("Chams", Category.RENDER, "Uses Minecraft's glow outline on players so they stay visible through terrain.");
   }
}
