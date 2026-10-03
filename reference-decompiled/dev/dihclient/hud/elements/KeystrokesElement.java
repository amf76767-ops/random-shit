package dev.dihclient.hud.elements;

import dev.dihclient.gui.theme.Anim;
import dev.dihclient.gui.theme.Theme;
import dev.dihclient.hud.HudElement;
import dev.dihclient.hud.HudStyle;
import dev.dihclient.render.Gfx;
import dev.dihclient.util.ColorUtil;
import dev.dihclient.util.KeyUtil;
import java.util.ArrayDeque;
import java.util.Deque;
import net.minecraft.class_304;
import net.minecraft.class_332;

public class KeystrokesElement extends HudElement {
   private final Deque<Long> leftClicks = new ArrayDeque<>();
   private final Deque<Long> rightClicks = new ArrayDeque<>();
   private boolean lastLeft;
   private boolean lastRight;

   public KeystrokesElement() {
      super("keys", "Keystrokes", "keys", 4, 150, () -> HudStyle.hud().keystrokes);
   }

   private void key(class_332 var1, String var2, String var3, class_304 var4, int var5, int var6, int var7, int var8) {
      boolean var9 = KeyUtil.isPhysicallyDown(var4);
      float var10 = Anim.get(var4, "keystroke", var9 ? 1.0F : 0.0F, 22.0F);
      int var11 = Theme.accentAt(var5 / 100.0);
      int var12 = ColorUtil.blend(HudStyle.bgColor(), ColorUtil.withAlpha(var11, 220), var10);
      Gfx.rect(var1, var5, var6, var7, var8, 4, var12);
      int var13 = ColorUtil.blend(-1446670, -15921388, var10);
      if (var3 == null) {
         Gfx.textCentered(var1, var2, var5 + var7 / 2, var6 + (var8 - 8) / 2, var13);
      } else {
         Gfx.textCentered(var1, var2, var5 + var7 / 2, var6 + 3, var13);
         Gfx.text(var1, var3, var5 + var7 / 2.0F - Gfx.width(var3) * 0.35F, var6 + 12.0F, ColorUtil.withAlpha(var13, 180), 0.7F);
      }
   }

   private static int cps(Deque<Long> var0) {
      long var1 = System.currentTimeMillis();

      while (!var0.isEmpty() && var1 - var0.peekFirst() > 1000L) {
         var0.pollFirst();
      }

      return var0.size();
   }

   @Override
   public void render(class_332 var1, float var2, boolean var3) {
      boolean var4 = KeyUtil.isPhysicallyDown(mc.field_1690.field_1886);
      boolean var5 = KeyUtil.isPhysicallyDown(mc.field_1690.field_1904);
      if (var4 && !this.lastLeft) {
         this.leftClicks.addLast(System.currentTimeMillis());
      }

      if (var5 && !this.lastRight) {
         this.rightClicks.addLast(System.currentTimeMillis());
      }

      this.lastLeft = var4;
      this.lastRight = var5;
      byte var6 = 22;
      byte var7 = 2;
      this.width = var6 * 3 + var7 * 2;
      this.height = var6 * 2 + var7 + 20 + var7 + 10;
      this.key(var1, "W", null, mc.field_1690.field_1894, var6 + var7, 0, var6, var6);
      this.key(var1, "A", null, mc.field_1690.field_1913, 0, var6 + var7, var6, var6);
      this.key(var1, "S", null, mc.field_1690.field_1881, var6 + var7, var6 + var7, var6, var6);
      this.key(var1, "D", null, mc.field_1690.field_1849, (var6 + var7) * 2, var6 + var7, var6, var6);
      int var8 = (var6 + var7) * 2;
      int var9 = (this.width - var7) / 2;
      this.key(var1, "LMB", cps(this.leftClicks) + " CPS", mc.field_1690.field_1886, 0, var8, var9, 20);
      this.key(var1, "RMB", cps(this.rightClicks) + " CPS", mc.field_1690.field_1904, var9 + var7, var8, this.width - var9 - var7, 20);
      int var10 = var8 + 20 + var7;
      boolean var11 = KeyUtil.isPhysicallyDown(mc.field_1690.field_1903);
      float var12 = Anim.get(mc.field_1690.field_1903, "keystroke", var11 ? 1.0F : 0.0F, 22.0F);
      Gfx.rect(var1, 0, var10, this.width, 10, 4, ColorUtil.blend(HudStyle.bgColor(), ColorUtil.withAlpha(Theme.accent(), 220), var12));
      Gfx.rect(var1, this.width / 2 - 12, var10 + 4, 24, 2, 1, ColorUtil.blend(-1446670, -15921388, var12));
   }
}
