package dev.dihclient.modules.player;

import dev.dihclient.module.Category;
import dev.dihclient.module.Module;
import dev.dihclient.setting.StringSetting;
import dev.dihclient.util.Hammer;
import java.util.ArrayList;
import java.util.List;

public class HammerTool extends Module {
   public final StringSetting names = this.text(
      "Tool Name",
      "Words in the name or tooltip of your 3x3 pickaxe, separated by commas (e.g. \"3x3, hammer, Bohrer\"). Upper / lower case does not matter.",
      "3x3,hammer",
      128
   );

   public HammerTool() {
      super("3x3 Pickaxe", Category.PLAYER, "Recognises your 3x3 custom pickaxe for the mining bots (each bot has its own 3x3 Pickaxe switch).");
   }

   @Override
   public boolean isToggleable() {
      return false;
   }

   @Override
   public List<String> details() {
      ArrayList var1 = new ArrayList();
      int var2 = mc.field_1724 == null ? -1 : Hammer.find();
      var1.add(
         var2 < 0
            ? "No 3x3 pickaxe found in your inventory"
            : "3x3 pickaxe found: "
               + mc.field_1724.method_31548().method_5438(var2).method_7964().getString()
               + (var2 < 9 ? " (hotbar " + (var2 + 1) + ")" : " (inventory)")
      );
      return var1;
   }
}
