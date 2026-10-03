package dev.dihclient.modules.client;

import dev.dihclient.DIHClient;
import dev.dihclient.module.Category;
import dev.dihclient.module.Module;
import dev.dihclient.module.ModuleManager;
import dev.dihclient.render.Gfx;
import dev.dihclient.setting.BoolSetting;
import dev.dihclient.util.ColorUtil;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Locale;
import net.minecraft.class_1657;
import net.minecraft.class_1934;
import net.minecraft.class_310;
import net.minecraft.class_332;
import net.minecraft.class_640;
import net.minecraft.class_7532;

public class BetterTablist extends Module {
   public final BoolSetting health = this.bool("Health", "Shows the health of loaded players.", true).legacy("betterTab.health");
   public final BoolSetting distance = this.bool("Distance", "Shows the distance to loaded players.", true).legacy("betterTab.distance");
   public final BoolSetting ping = this.bool("Ping", "Shows latency.", true);

   public BetterTablist() {
      super("BetterTablist", Category.CLIENT, "Replaces the TAB list with a compact list showing health, distance, ping and friend/enemy markers.");
   }

   public static void renderIfHeld(class_332 var0) {
      class_310 var1 = class_310.method_1551();
      if (var1.field_1690.field_1907.method_1434() && var1.method_1562() != null && var1.field_1724 != null) {
         BetterTablist var2 = ModuleManager.of(BetterTablist.class);
         ArrayList var3 = new ArrayList(var1.method_1562().method_45732());
         var3.sort(
            Comparator.<class_640, Boolean>comparing(var0x -> var0x.method_2958() == class_1934.field_9219)
               .thenComparing(var0x -> var0x.method_2966().name().toLowerCase(Locale.ROOT))
         );
         byte var4 = 12;
         int var5 = Math.max(1, Math.min(20, (var1.method_22683().method_4502() - 60) / var4));
         int var6 = Math.max(1, (var3.size() + var5 - 1) / var5);
         short var7 = 190;
         int var8 = var6 * var7 + 8;
         int var9 = Math.min(var3.size(), var5);
         int var10 = (var1.method_22683().method_4486() - var8) / 2;
         byte var11 = 12;
         int var12 = ModuleManager.of(ClickGui.class).accent.get();
         Gfx.panel(var0, var10, var11, var8, var9 * var4 + 24, var12);
         String var13 = var3.size() + " players";
         var0.method_25303(var1.field_1772, var13, var10 + 6, var11 + 5, -1);

         for (int var14 = 0; var14 < var3.size(); var14++) {
            class_640 var15 = (class_640)var3.get(var14);
            int var16 = var14 / var5;
            int var17 = var14 % var5;
            int var18 = var10 + 4 + var16 * var7;
            int var19 = var11 + 19 + var17 * var4;
            if ((var17 & 1) == 0) {
               var0.method_25294(var18, var19 - 1, var18 + var7 - 4, var19 + var4 - 1, 587202559);
            }

            class_7532.method_52722(var0, var15.method_52810(), var18 + 1, var19, 9);
            String var20 = var15.method_2966().name();
            int var21 = DIHClient.social().isFriend(var20)
               ? -11740828
               : (DIHClient.social().isEnemy(var20) ? -46261 : (var15.method_2958() == class_1934.field_9219 ? -7697782 : -1184275));
            var0.method_25303(var1.field_1772, var20, var18 + 13, var19 + 1, var21);
            StringBuilder var22 = new StringBuilder();
            class_1657 var23 = var1.field_1687.method_18470(var15.method_2966().id());
            if (var23 != null) {
               if (var2.health.get()) {
                  var22.append(String.format(Locale.ROOT, "%.0f❤ ", var23.method_6032() + var23.method_6067()));
               }

               if (var2.distance.get() && var23 != var1.field_1724) {
                  var22.append(String.format(Locale.ROOT, "%.0fm ", var1.field_1724.method_5739(var23)));
               }
            }

            if (var2.ping.get()) {
               var22.append(var15.method_2959()).append("ms");
            }

            String var24 = var22.toString().trim();
            int var25 = var23 != null ? ColorUtil.health(var23.method_6032() / Math.max(1.0F, var23.method_6063())) : -5592406;
            var0.method_25303(var1.field_1772, var24, var18 + var7 - 8 - var1.field_1772.method_1727(var24), var19 + 1, var25);
         }
      }
   }
}
