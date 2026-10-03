package dev.dihclient.modules.render;

import dev.dihclient.module.Category;
import dev.dihclient.module.Module;
import dev.dihclient.module.ModuleManager;
import dev.dihclient.setting.DoubleSetting;
import net.minecraft.class_1297;
import net.minecraft.class_1657;
import net.minecraft.class_310;

public class Nametags extends Module {
   public final DoubleSetting range = this.dbl("Range", "Players within this distance always show their nameplate.", 96.0, 8.0, 512.0, 8.0)
      .legacy("nametags.range");

   public Nametags() {
      super("Nametags", Category.RENDER, "Forces player nameplates to stay visible (also when sneaking / behind walls) up to the configured distance.");
   }

   public static boolean forceLabel(class_1297 var0, double var1) {
      if (!ModuleManager.on(Nametags.class) || !(var0 instanceof class_1657)) {
         return false;
      } else if (var0 == class_310.method_1551().field_1724) {
         return false;
      } else {
         double var3 = ModuleManager.of(Nametags.class).range.get();
         return var1 <= var3 * var3;
      }
   }
}
