package dev.dihclient.hud;

import dev.dihclient.DIHClient;
import dev.dihclient.gui.HudEditorScreen;
import dev.dihclient.gui.theme.Anim;
import dev.dihclient.gui.theme.Theme;
import dev.dihclient.hud.elements.ArmorElement;
import dev.dihclient.hud.elements.ChunkRadarElement;
import dev.dihclient.hud.elements.CoordsElement;
import dev.dihclient.hud.elements.KeystrokesElement;
import dev.dihclient.hud.elements.ModuleListElement;
import dev.dihclient.hud.elements.PotionElement;
import dev.dihclient.hud.elements.RadarElement;
import dev.dihclient.hud.elements.ServerElement;
import dev.dihclient.hud.elements.StatsElement;
import dev.dihclient.hud.elements.TargetElement;
import dev.dihclient.hud.elements.WatermarkElement;
import dev.dihclient.modules.client.Hud;
import dev.dihclient.render.Gfx;
import dev.dihclient.util.ColorUtil;
import dev.dihclient.util.Notifications;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.class_124;
import net.minecraft.class_2561;
import net.minecraft.class_310;
import net.minecraft.class_332;

public final class HudManager {
   private final List<HudElement> elements = new ArrayList<>();

   public void registerDefaults() {
      this.elements.add(new WatermarkElement());
      this.elements.add(new ModuleListElement());
      this.elements.add(new StatsElement());
      this.elements.add(new CoordsElement());
      this.elements.add(new ServerElement());
      this.elements.add(new PotionElement());
      this.elements.add(new ArmorElement());
      this.elements.add(new TargetElement());
      this.elements.add(new KeystrokesElement());
      this.elements.add(new RadarElement());
      this.elements.add(new ChunkRadarElement());
   }

   public List<HudElement> elements() {
      return Collections.unmodifiableList(this.elements);
   }

   public static Hud hud() {
      return HudStyle.hud();
   }

   public static int accent() {
      return Theme.accent();
   }

   public static void drawElement(class_332 var0, HudElement var1, int var2, int var3, float var4, boolean var5) {
      var0.method_51448().pushMatrix();
      var0.method_51448().translate(var2, var3);
      var0.method_51448().scale(var1.scale(), var1.scale());

      try {
         var1.render(var0, var4, var5);
      } catch (Throwable var10) {
         DIHClient.LOG.error("[DIHClient] HUD element {} failed", var1.id(), var10);
      } finally {
         var0.method_51448().popMatrix();
      }
   }

   public void render(class_332 var1, float var2) {
      class_310 var3 = class_310.method_1551();
      if (!var3.field_1690.field_1842 && var3.field_1724 != null) {
         boolean var4 = var3.field_1755 instanceof HudEditorScreen;
         Hud var5 = hud();
         Gfx.setTextShadow(var5 == null || var5.textShadow.get());

         try {
            if (var5 != null && var5.isEnabled() && !var4) {
               int var6 = var1.method_51421();
               int var7 = var1.method_51443();

               for (HudElement var9 : this.elements) {
                  if (var9.shouldRender()) {
                     var9.updateSides(var6, var7);
                     drawElement(var1, var9, var9.screenX(var6), var9.screenY(var7), var2, false);
                  }
               }
            }

            DIHClient.modules().render2D(var1, var2);
            this.renderToasts(var1);
            this.renderAlert(var1);
         } finally {
            Gfx.setTextShadow(true);
         }
      }
   }

   private void renderToasts(class_332 var1) {
      List var2 = Notifications.toasts();
      if (!var2.isEmpty()) {
         int var3 = var1.method_51421();
         int var4 = var1.method_51443();
         float var5 = var4 - 8;

         for (int var6 = var2.size() - 1; var6 >= 0; var6--) {
            Notifications.Toast var7 = (Notifications.Toast)var2.get(var6);
            int var8 = Math.max(120, Math.max(Gfx.width(var7.title) + 6, Gfx.width(var7.message)) + 36);
            byte var9 = 28;
            float var10 = var7.progress();
            float var11 = Anim.easeOutCubic(Math.min(1.0F, var7.age / 6.0F));
            float var12 = var10 > 0.85F ? Anim.easeOutCubic((1.0F - var10) / 0.15F) : 1.0F;
            float var13 = Math.min(var11, var12);
            float var14 = var5 - var9;
            float var15 = Anim.get(var7, "y", var14, 14.0F);

            int var16 = switch (var7.type) {
               case SUCCESS -> -11870592;
               case WARNING -> -278748;
               case ERROR -> -495247;
               default -> Theme.accent();
            };

            String var17 = switch (var7.type) {
               case SUCCESS -> "✔";
               case WARNING -> "!";
               case ERROR -> "✖";
               default -> "i";
            };
            int var18 = (int)var15;
            int var19 = var3 - 6 - var8 + (int)((var8 + 12) * (1.0F - var13));
            Gfx.shadow(var1, var19, var18, var8, var9, 6, 3, 0.9F);
            Gfx.rect(var1, var19, var18, var8, var9, 6, -234025196);
            Gfx.rect(var1, var19 + 6, var18 + 7, 14, 14, 7, ColorUtil.withAlpha(var16, 60));
            Gfx.textCentered(var1, var17, var19 + 13, var18 + 10, var16);
            var1.method_51439(Gfx.font(), class_2561.method_43470(var7.title).method_27692(class_124.field_1067), var19 + 26, var18 + 5, -1446670, true);
            Gfx.text(var1, var7.message, var19 + 26, var18 + 16, -7564380);
            int var20 = (int)((var8 - 12) * (1.0F - var10));
            if (var20 > 0) {
               Gfx.rect(var1, var19 + 6, var18 + var9 - 3, var20, 2, 1, ColorUtil.withAlpha(var16, 200));
            }

            var5 = var15 - 4.0F;
         }
      }
   }

   private void renderAlert(class_332 var1) {
      String var2 = Notifications.alertText();
      if (var2 != null) {
         int var3 = var1.method_51421();
         int var4 = Notifications.alertTicks();
         float var5 = (float)(Math.sin(System.currentTimeMillis() / 120.0) * 0.5 + 0.5);
         int var6 = (int)(Gfx.width(var2) * 1.6F) + 24;
         int var7 = (var3 - var6) / 2;
         byte var8 = 30;
         Gfx.shadow(var1, var7, var8, var6, 26, 8, 4, 1.0F);
         Gfx.rect(var1, var7, var8, var6, 26, 8, ColorUtil.blend(-532673524, -528476136, var5));
         Gfx.outline(var1, var7, var8, var6, 26, 8, ColorUtil.withAlpha(-495247, 200));
         Gfx.text(var1, var2, var7 + 12.0F, var8 + 7.0F, var4 % 10 < 5 ? -1 : -12080, 1.6F);
      }
   }
}
