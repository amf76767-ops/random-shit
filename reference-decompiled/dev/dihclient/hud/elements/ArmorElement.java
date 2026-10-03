package dev.dihclient.hud.elements;

import dev.dihclient.hud.HudElement;
import dev.dihclient.hud.HudStyle;
import dev.dihclient.render.Gfx;
import dev.dihclient.util.ColorUtil;
import net.minecraft.class_1304;
import net.minecraft.class_1799;
import net.minecraft.class_332;

public class ArmorElement extends HudElement {
   private static final class_1304[] SLOTS = new class_1304[]{
      class_1304.field_6169, class_1304.field_6174, class_1304.field_6172, class_1304.field_6166, class_1304.field_6173
   };

   public ArmorElement() {
      super("armor", "Armor", "armor", -1, -1, () -> HudStyle.hud().armor);
   }

   @Override
   public void render(class_332 var1, float var2, boolean var3) {
      byte var4 = 20;
      this.width = SLOTS.length * var4 + 6;
      this.height = 26;
      HudStyle.panel(var1, 0, 0, this.width, this.height);

      for (int var5 = 0; var5 < SLOTS.length; var5++) {
         class_1799 var6 = mc.field_1724.method_6118(SLOTS[var5]);
         int var7 = 3 + var5 * var4;
         if (var5 == SLOTS.length - 1) {
            var1.method_25294(var7 - 1, 5, var7, 21, 822083583);
         }

         if (var6.method_7960()) {
            Gfx.rect(var1, var7 + 3, 5, 14, 14, 3, 352321535);
         } else {
            var1.method_51427(var6, var7 + 2, 3);
            if (var6.method_7963()) {
               float var8 = 1.0F - (float)var6.method_7919() / var6.method_7936();
               Gfx.bar(var1, var7 + 3, 21, 14, 2, var8, 1342177280, ColorUtil.health(var8));
            } else if (var6.method_7947() > 1) {
               String var9 = Integer.toString(var6.method_7947());
               Gfx.text(var1, var9, var7 + 19 - Gfx.width(var9), 13, -1446670);
            }
         }
      }
   }
}
