package dev.dihclient.modules.fun;

import dev.dihclient.DIHClient;
import dev.dihclient.module.Category;
import dev.dihclient.module.Module;
import dev.dihclient.module.ModuleManager;
import dev.dihclient.setting.BoolSetting;
import dev.dihclient.setting.DoubleSetting;
import net.minecraft.class_1309;
import net.minecraft.class_1657;
import net.minecraft.class_310;

public class TinyPlayers extends Module {
   public final DoubleSetting size = this.dbl("Size", "0.3 = tiny, 1 = normal, 3 = giant.", 0.4, 0.1, 3.0, 0.05);
   public final BoolSetting self = this.bool("Self", "Also you (third person / inventory).", false);
   public final BoolSetting mobs = this.bool("Mobs", "Mobs and animals too.", false);
   public final BoolSetting skipFriends = this.bool("Skip Friends", "Friends keep their normal size.", false);
   public final BoolSetting bounce = this.bool("Bounce", "Players squash and stretch while walking.", false);

   public TinyPlayers() {
      super("Tiny Players", Category.FUN, "Makes other players tiny or giant. Only you see it, hitboxes don't change.");
   }

   public static float scale(class_1309 var0, float var1) {
      if (!ModuleManager.on(TinyPlayers.class)) {
         return 1.0F;
      } else {
         TinyPlayers var2 = ModuleManager.of(TinyPlayers.class);
         if (var0 == class_310.method_1551().field_1724) {
            if (!var2.self.get()) {
               return 1.0F;
            }
         } else if (var0 instanceof class_1657 var3) {
            if (var2.skipFriends.get() && DIHClient.social().isFriend(var3)) {
               return 1.0F;
            }
         } else if (!var2.mobs.get()) {
            return 1.0F;
         }

         float var4 = var2.size.getFloat();
         if (var2.bounce.get() && var0.method_18798().method_37268() > 1.0E-4) {
            var4 *= 1.0F + 0.12F * (float)Math.abs(Math.sin(var1 * 0.6F));
         }

         return var4;
      }
   }

   @Override
   public String getInfo() {
      return String.format("%.2fx", this.size.get());
   }
}
