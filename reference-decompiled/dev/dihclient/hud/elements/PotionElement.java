package dev.dihclient.hud.elements;

import dev.dihclient.hud.HudElement;
import dev.dihclient.hud.HudStyle;
import dev.dihclient.render.Gfx;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Locale;
import net.minecraft.class_10799;
import net.minecraft.class_1291;
import net.minecraft.class_1293;
import net.minecraft.class_329;
import net.minecraft.class_332;

public class PotionElement extends HudElement {
   private static final String[] ROMAN = new String[]{"", " II", " III", " IV", " V", " VI", " VII", " VIII", " IX", " X"};

   public PotionElement() {
      super("potions", "Potion Effects", "potions", -1, -24, () -> HudStyle.hud().potions);
   }

   @Override
   public boolean shouldRender() {
      return this.isEnabled() && !mc.field_1724.method_6026().isEmpty();
   }

   @Override
   public void render(class_332 var1, float var2, boolean var3) {
      ArrayList var4 = new ArrayList(mc.field_1724.method_6026());
      var4.sort(Comparator.comparingInt(class_1293::method_5584).reversed());
      if (var4.isEmpty()) {
         this.width = 90;
         this.height = 18;
         if (var3) {
            HudStyle.panel(var1, 0, 0, this.width, this.height);
            Gfx.text(var1, "Potion Effects", 6, 5, -10788238);
         }
      } else {
         byte var5 = 16;
         int var6 = 0;
         int var7 = var4.size();
         String[] var8 = new String[var7];
         String[] var9 = new String[var7];

         for (int var10 = 0; var10 < var7; var10++) {
            class_1293 var11 = (class_1293)var4.get(var10);
            var8[var10] = label(var11);
            var9[var10] = time(var11);
            var6 = Math.max(var6, Gfx.width(var8[var10]) + Gfx.width(var9[var10]) + 34);
         }

         this.width = var6;
         this.height = var4.size() * var5 + 4;
         HudStyle.panel(var1, 0, 0, this.width, this.height);
         byte var18 = 2;

         for (int var19 = 0; var19 < var7; var19++) {
            class_1293 var12 = (class_1293)var4.get(var19);
            class_1291 var13 = (class_1291)var12.method_5579().comp_349();
            int var14 = 0xFF000000 | var13.method_5556();
            var1.method_52706(class_10799.field_56883, class_329.method_71644(var12.method_5579()), 4, var18 + 2, 12, 12);
            Gfx.text(var1, var8[var19], 20, var18 + 4, var14);
            String var15 = var9[var19];
            boolean var16 = !var12.method_48559() && var12.method_5584() < 200;
            int var17 = var16 && System.currentTimeMillis() / 400L % 2L == 0L ? -495247 : -7564380;
            Gfx.text(var1, var15, this.width - 5 - Gfx.width(var15), var18 + 4, var17);
            var18 += var5;
         }
      }
   }

   private static String label(class_1293 var0) {
      int var1 = var0.method_5578();
      return ((class_1291)var0.method_5579().comp_349()).method_5560().getString()
         + (var1 >= 1 && var1 < ROMAN.length ? ROMAN[var1] : (var1 >= ROMAN.length ? " " + (var1 + 1) : ""));
   }

   private static String time(class_1293 var0) {
      if (var0.method_48559()) {
         return "∞";
      } else {
         int var1 = var0.method_5584() / 20;
         if (var1 < 0) {
            return String.format(Locale.ROOT, "%d:%02d", var1 / 60, var1 % 60);
         } else {
            int var2 = var1 % 60;
            return var1 / 60 + (var2 < 10 ? ":0" : ":") + var2;
         }
      }
   }
}
