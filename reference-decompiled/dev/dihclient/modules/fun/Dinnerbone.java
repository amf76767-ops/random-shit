package dev.dihclient.modules.fun;

import dev.dihclient.module.Category;
import dev.dihclient.module.Module;
import dev.dihclient.module.ModuleManager;
import dev.dihclient.setting.BoolSetting;
import net.minecraft.class_1309;
import net.minecraft.class_1657;
import net.minecraft.class_310;

public class Dinnerbone extends Module {
   public final BoolSetting players = this.bool("Players", "Flip other players.", true);
   public final BoolSetting mobs = this.bool("Mobs", "Flip mobs and animals.", true);
   public final BoolSetting self = this.bool("Self", "Flip yourself too (third person / inventory).", false);

   public Dinnerbone() {
      super("Dinnerbone", Category.FUN, "Renders players and mobs upside down. Only you see it.");
   }

   public static boolean flip(class_1309 var0) {
      if (!ModuleManager.on(Dinnerbone.class)) {
         return false;
      } else {
         Dinnerbone var1 = ModuleManager.of(Dinnerbone.class);
         return var0 == class_310.method_1551().field_1724 ? var1.self.get() : var0 instanceof class_1657 ? var1.players.get() : var1.mobs.get();
      }
   }
}
