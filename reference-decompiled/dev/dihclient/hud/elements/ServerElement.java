package dev.dihclient.hud.elements;

import dev.dihclient.hud.HudElement;
import dev.dihclient.hud.HudStyle;
import dev.dihclient.render.Gfx;
import net.minecraft.class_332;
import net.minecraft.class_642;

public class ServerElement extends HudElement {
   public ServerElement() {
      super("server", "Server Info", "server", 4, -30, () -> HudStyle.hud().serverInfo);
   }

   @Override
   public void render(class_332 var1, float var2, boolean var3) {
      class_642 var4 = mc.method_1558();
      String var5 = mc.method_1542() ? "Singleplayer" : (var4 != null ? var4.field_3761 : "Unknown");
      int var6 = mc.method_1562() != null ? mc.method_1562().method_2880().size() : 0;
      String var7 = mc.method_1562() != null ? mc.method_1562().method_52790() : null;
      String var8 = var6 + " online" + (var7 != null ? " · " + var7 : "");
      int var9 = WatermarkElement.ping();
      String var10 = var9 + "ms";
      this.width = Math.max(Gfx.width(var5) + Gfx.width(var10) + 18, Gfx.width(var8) + 16);
      this.height = 26;
      HudStyle.accentPanel(var1, 0, 0, this.width, this.height);
      Gfx.rect(var1, 6, 7, 4, 4, 2, var9 > 150 ? -495247 : (var9 > 80 ? -278748 : -11870592));
      Gfx.text(var1, var5, 13, 4, -1446670);
      Gfx.text(var1, var10, this.width - 6 - Gfx.width(var10), 4, -10788238);
      Gfx.text(var1, var8, 6, 15, -7564380);
   }
}
