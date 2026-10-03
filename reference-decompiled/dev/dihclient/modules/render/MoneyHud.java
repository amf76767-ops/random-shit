package dev.dihclient.modules.render;

import dev.dihclient.gui.theme.Theme;
import dev.dihclient.hud.HudStyle;
import dev.dihclient.module.Category;
import dev.dihclient.module.Module;
import dev.dihclient.render.Gfx;
import dev.dihclient.setting.BoolSetting;
import dev.dihclient.setting.DoubleSetting;
import dev.dihclient.setting.EnumSetting;
import dev.dihclient.setting.IntSetting;
import dev.dihclient.util.Money;
import net.minecraft.class_332;

public class MoneyHud extends Module {
   public final EnumSetting<NavigationHud.Corner> corner = this.mode("Position", "Screen corner.", NavigationHud.Corner.TOP_RIGHT);
   public final IntSetting offsetX = this.integer("Offset X", "Distance from the screen edge.", 4, 0, 600);
   public final IntSetting offsetY = this.integer("Offset Y", "Distance from the screen edge.", 4, 0, 600);
   public final DoubleSetting scale = this.dbl("Scale", "Size.", 1.0, 0.5, 2.0, 0.05);
   public final BoolSetting session = this.bool("Session", "Shows how much you won or spent since joining the server.", true);
   public final BoolSetting askBal = this.bool("Use /bal", "Sends /bal every few minutes when the scoreboard shows no balance.", true);
   public final IntSetting balEvery = this.integer("/bal Every", "Minutes between two /bal (only when the scoreboard has none).", 5, 1, 60)
      .visibleWhen(this.askBal::get);
   private long lastAsk;

   public MoneyHud() {
      super("MoneyHUD", Category.RENDER, "Shows your balance (scoreboard or /bal) and the money won / spent this session.");
   }

   @Override
   protected void onEnable() {
      this.lastAsk = 0L;
   }

   @Override
   public void onTick() {
      if (inGame() && this.askBal.get() && !"scoreboard".equals(Money.source())) {
         long var1 = System.currentTimeMillis();
         if (this.lastAsk == 0L || var1 - this.lastAsk > this.balEvery.get().intValue() * 60000L) {
            this.lastAsk = var1;
            Money.refresh();
         }
      }
   }

   @Override
   public void onRender2D(class_332 var1, float var2) {
      if (inGame() && !mc.field_1690.field_1842) {
         String var3 = "Money: " + (Money.known() ? Money.format(Money.balance()) : "?");
         double var4 = Money.session();
         String var6 = this.session.get() && Money.known() ? (var4 >= 0.0 ? "+" : "") + Money.format(var4) + " this session" : null;
         int var7 = Math.max(Gfx.width(var3), var6 == null ? 0 : Gfx.width(var6)) + 10;
         int var8 = var6 == null ? 14 : 24;
         float var9 = this.scale.getFloat();
         int var10 = Math.round(var7 * var9);
         int var11 = Math.round(var8 * var9);
         int var12 = var1.method_51421();
         int var13 = var1.method_51443();
         NavigationHud.Corner var14 = this.corner.get();

         int var15 = switch (var14) {
            case TOP_LEFT, BOTTOM_LEFT -> this.offsetX.get();
            case TOP_RIGHT, BOTTOM_RIGHT -> var12 - var10 - this.offsetX.get();
            case TOP_CENTER -> (var12 - var10) / 2 + this.offsetX.get();
         };
         int var16 = var14 != NavigationHud.Corner.BOTTOM_LEFT && var14 != NavigationHud.Corner.BOTTOM_RIGHT
            ? this.offsetY.get()
            : var13 - var11 - this.offsetY.get();
         var15 = Math.max(0, Math.min(var12 - var10, var15));
         var16 = Math.max(0, Math.min(var13 - var11, var16));
         var1.method_51448().pushMatrix();
         var1.method_51448().translate(var15, var16);
         var1.method_51448().scale(var9, var9);

         try {
            HudStyle.panel(var1, 0, 0, var7, var8);
            Gfx.text(var1, var3, 5, 3, Theme.accent());
            if (var6 != null) {
               Gfx.text(var1, var6, 5, 13, var4 > 0.0 ? -11870592 : (var4 < 0.0 ? -495247 : -7564380));
            }
         } finally {
            var1.method_51448().popMatrix();
         }
      }
   }

   @Override
   public String getInfo() {
      return Money.known() ? Money.format(Money.balance()) : null;
   }
}
