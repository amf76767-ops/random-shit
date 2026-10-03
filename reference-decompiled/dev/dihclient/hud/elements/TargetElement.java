package dev.dihclient.hud.elements;

import dev.dihclient.DIHClient;
import dev.dihclient.gui.theme.Anim;
import dev.dihclient.hud.HudElement;
import dev.dihclient.hud.HudStyle;
import dev.dihclient.module.ModuleManager;
import dev.dihclient.modules.render.DamageNumbers;
import dev.dihclient.modules.render.TargetHud;
import dev.dihclient.render.Gfx;
import dev.dihclient.util.ColorUtil;
import java.util.Locale;
import net.minecraft.class_124;
import net.minecraft.class_1297;
import net.minecraft.class_1304;
import net.minecraft.class_1657;
import net.minecraft.class_1799;
import net.minecraft.class_2561;
import net.minecraft.class_332;
import net.minecraft.class_742;
import net.minecraft.class_7532;

public class TargetElement extends HudElement {
   private static final class_1304[] ARMOR = new class_1304[]{
      class_1304.field_6169, class_1304.field_6174, class_1304.field_6172, class_1304.field_6166, class_1304.field_6173
   };
   private class_1657 last;

   public TargetElement() {
      super("target", "Target HUD", "target", -1, -80, () -> null);
   }

   @Override
   public boolean isEnabled() {
      return ModuleManager.on(TargetHud.class);
   }

   @Override
   public void setEnabled(boolean var1) {
      TargetHud var2 = ModuleManager.of(TargetHud.class);
      if (var2 != null) {
         var2.setEnabled(var1);
      }
   }

   private class_1657 target() {
      TargetHud var1 = ModuleManager.of(TargetHud.class);
      return var1 == null ? null : var1.current();
   }

   @Override
   public boolean shouldRender() {
      if (!this.isEnabled()) {
         return false;
      } else {
         class_1657 var1 = this.target();
         if (var1 != null) {
            this.last = var1;
         }

         float var2 = Anim.get(this, "show", var1 != null ? 1.0F : 0.0F, 10.0F);
         return var2 > 0.02F && this.last != null;
      }
   }

   @Override
   public void render(class_332 var1, float var2, boolean var3) {
      this.width = 156;
      this.height = 48;
      Object var4 = var3 ? mc.field_1724 : this.target();
      if (var4 == null) {
         var4 = this.last;
      }

      if (var4 != null) {
         float var5 = var3 ? 1.0F : Anim.get(this, "show", this.target() != null ? 1.0F : 0.0F, 10.0F);
         var1.method_51448().pushMatrix();
         float var6 = 0.9F + 0.1F * Anim.easeOutCubic(var5);
         var1.method_51448().translate(this.width / 2.0F, this.height / 2.0F);
         var1.method_51448().scale(var6, var6);
         var1.method_51448().translate(-this.width / 2.0F, -this.height / 2.0F);
         HudStyle.accentPanel(var1, 0, 0, this.width, this.height);
         if (var4 instanceof class_742 var7) {
            class_7532.method_52722(var1, var7.method_52814(), 6, 6, 36);
            if (((class_1657)var4).field_6235 > 0) {
               Gfx.rect(var1, 6, 6, 36, 36, 0, (int)(((class_1657)var4).field_6235 / 10.0F * 110.0F) << 24 | 16719904);
            }
         }

         byte var26 = 48;
         String var8 = var4.method_5477().getString();
         int var9 = DIHClient.social().isFriend((class_1657)var4) ? -11870592 : (DIHClient.social().isEnemy((class_1657)var4) ? -495247 : -1446670);
         var1.method_51439(
            Gfx.font(), class_2561.method_43470(Gfx.trim(var8, this.width - var26 - 34)).method_27692(class_124.field_1067), var26, 6, var9, true
         );
         float var10 = mc.field_1724.method_5739((class_1297)var4);
         String var11 = String.format(Locale.ROOT, "%.1fm", var10);
         Gfx.text(var1, var11, this.width - 6 - Gfx.width(var11), 6, -10788238);
         float var12 = var4.method_6032();
         float var13 = Math.max(1.0F, var4.method_6063());
         float var14 = var4.method_6067();
         float var15 = Anim.get(var4, "hudHp", var12 / var13, 8.0F);
         float var16 = Anim.get(var4, "hudHpSlow", var12 / var13, 2.5F);
         byte var18 = 20;
         int var19 = this.width - var26 - 6;
         Gfx.rect(var1, var26, var18, var19, 6, 3, 1090519039);
         if (var16 > var15) {
            Gfx.rect(var1, var26, var18, Math.max(6, (int)(var19 * var16)), 6, 3, -1426063361);
         }

         int var20 = ColorUtil.health(var15);
         Gfx.rect(var1, var26, var18, Math.max(6, (int)(var19 * var15)), 6, 3, var20);
         if (var14 > 0.0F) {
            Gfx.rect(var1, var26, var18 + 7, Math.max(4, (int)(var19 * Math.min(1.0F, var14 / var13))), 2, 1, -10166);
         }

         String var21 = String.format(Locale.ROOT, "%.1f", var12 + var14);
         Gfx.text(var1, var21 + " ❤", var26, 31, var20);
         int var22 = this.width - 6;

         for (int var23 = ARMOR.length - 1; var23 >= 0; var23--) {
            class_1799 var24 = var4.method_6118(ARMOR[var23]);
            if (!var24.method_7960()) {
               var22 -= 13;
               var1.method_51448().pushMatrix();
               var1.method_51448().translate(var22, 29.0F);
               var1.method_51448().scale(0.75F, 0.75F);
               var1.method_51427(var24, 0, 0);
               var1.method_51448().popMatrix();
            }
         }

         if (var4 != mc.field_1724) {
            float var27 = mc.field_1724.method_6032() + mc.field_1724.method_6067();
            float var29 = var12 + var14;
            String var25 = var27 >= var29 ? "▲" : "▼";
            Gfx.text(var1, var25, var26 + Gfx.width(var21 + " ❤") + 4, 31, var27 >= var29 ? -11870592 : -495247);
         }

         DamageNumbers var28 = ModuleManager.of(DamageNumbers.class);
         if (var28 != null && var28.isEnabled() && var28.recentDamage() > 0.0F) {
            float var30 = var28.fade();
            String var31 = String.format(Locale.ROOT, "-%.1f", var28.recentDamage());
            Gfx.text(
               var1,
               var31,
               20.0F - Gfx.width(var31) / 2.0F * 1.2F + 4.0F,
               -4.0F - (1.0F - var30) * 8.0F,
               ColorUtil.withAlpha(-495247, Math.max(10, (int)(255.0F * var30))),
               1.2F
            );
         }

         var1.method_51448().popMatrix();
      }
   }
}
