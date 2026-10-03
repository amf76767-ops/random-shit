package dev.dihclient.modules.fun;

import dev.dihclient.DIHClient;
import dev.dihclient.module.Category;
import dev.dihclient.module.Module;
import dev.dihclient.module.ModuleManager;
import dev.dihclient.setting.BoolSetting;
import dev.dihclient.setting.DoubleSetting;
import net.minecraft.class_10055;
import net.minecraft.class_310;
import net.minecraft.class_630;

public class BigHead extends Module {
   public final DoubleSetting size = this.dbl("Size", "Head scale.", 2.5, 0.3, 5.0, 0.1);
   public final BoolSetting self = this.bool("Self", "Also applies to your own player (third person / inventory).", false);
   public final BoolSetting onlyFriends = this.bool("Only Friends", "Only your friends get big heads.", false);
   public final BoolSetting wobble = this.bool("Wobble", "Heads pulse a little.", false);

   public BigHead() {
      super("Big Head", Category.FUN, "Renders players with giant heads. Only you see it.");
   }

   public static void apply(class_630 var0, class_630 var1, class_10055 var2) {
      if (ModuleManager.on(BigHead.class)) {
         BigHead var3 = ModuleManager.of(BigHead.class);
         class_310 var4 = class_310.method_1551();
         if (var4.field_1724 == null || var2.field_53528 != var4.field_1724.method_5628() || var3.self.get()) {
            if (!var3.onlyFriends.get() || var2.field_53525 != null && DIHClient.social().isFriend(var2.field_53525.getString())) {
               float var5 = var3.size.getFloat();
               if (var3.wobble.get()) {
                  var5 *= 1.0F + 0.08F * (float)Math.sin(System.currentTimeMillis() / 180.0 + var2.field_53528);
               }

               scale(var0, var5);
               scale(var1, var5);
            }
         }
      }
   }

   private static void scale(class_630 var0, float var1) {
      var0.field_37938 *= var1;
      var0.field_37939 *= var1;
      var0.field_37940 *= var1;
   }
}
