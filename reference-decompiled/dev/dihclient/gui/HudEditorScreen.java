package dev.dihclient.gui;

import dev.dihclient.DIHClient;
import dev.dihclient.gui.theme.Anim;
import dev.dihclient.gui.theme.Theme;
import dev.dihclient.hud.HudElement;
import dev.dihclient.hud.HudManager;
import dev.dihclient.hud.HudStyle;
import dev.dihclient.modules.client.Hud;
import dev.dihclient.render.Gfx;
import dev.dihclient.util.ColorUtil;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.class_11908;
import net.minecraft.class_11909;
import net.minecraft.class_2561;
import net.minecraft.class_310;
import net.minecraft.class_332;
import net.minecraft.class_437;
import org.lwjgl.glfw.GLFW;

public class HudEditorScreen extends class_437 {
   private static final int SNAP = 5;
   private static final int PAD = 4;
   private final class_437 parent;
   private HudElement dragging;
   private int offX;
   private int offY;
   private final List<int[]> guides = new ArrayList<>();
   private boolean sidebarOpen = true;
   private final List<int[]> sideHits = new ArrayList<>();

   public HudEditorScreen(class_437 var1) {
      super(class_2561.method_43470("HUD Editor"));
      this.parent = var1 instanceof HudEditorScreen ? null : var1;
   }

   public boolean method_25421() {
      return false;
   }

   public void method_25420(class_332 var1, int var2, int var3, float var4) {
      var1.method_25294(0, 0, this.field_22789, this.field_22790, 1610612736);
   }

   private boolean snapping() {
      Hud var1 = HudStyle.hud();
      return (var1 == null || var1.snapGrid.get()) && !hasShiftDown();
   }

   private static boolean hasShiftDown() {
      class_310 var0 = class_310.method_1551();
      long var1 = var0.method_22683().method_4490();
      return GLFW.glfwGetKey(var1, 340) == 1 || GLFW.glfwGetKey(var1, 344) == 1;
   }

   public void method_25394(class_332 var1, int var2, int var3, float var4) {
      super.method_25394(var1, var2, var3, var4);
      int var5 = Theme.accent();
      Hud var6 = HudStyle.hud();
      Gfx.setTextShadow(var6 == null || var6.textShadow.get());
      var1.method_25294(this.field_22789 / 2, 0, this.field_22789 / 2 + 1, this.field_22790, 352321535);
      var1.method_25294(0, this.field_22790 / 2, this.field_22789, this.field_22790 / 2 + 1, 352321535);
      HudElement var7 = null;

      for (HudElement var9 : DIHClient.hud().elements()) {
         if (var9.isEnabled()) {
            var9.updateSides(this.field_22789, this.field_22790);
            int var10 = var9.screenX(this.field_22789);
            int var11 = var9.screenY(this.field_22790);
            HudManager.drawElement(var1, var9, var10, var11, var4, true);
            var10 = var9.screenX(this.field_22789);
            var11 = var9.screenY(this.field_22790);
            boolean var12 = this.dragging == null && Gfx.inside(var2, var3, var10, var11, var9.scaledWidth(), var9.scaledHeight());
            if (var12) {
               var7 = var9;
            }

            float var13 = Anim.get(var9, "editorHover", !var12 && var9 != this.dragging ? 0.0F : 1.0F, 16.0F);
            int var14 = ColorUtil.blend(1090519039, var5, var13);
            Gfx.outline(var1, var10 - 2, var11 - 2, var9.scaledWidth() + 4, var9.scaledHeight() + 4, 3, var14);
         }
      }

      for (int[] var17 : this.guides) {
         if (var17[0] == 1) {
            var1.method_25294(var17[1], 0, var17[1] + 1, this.field_22790, ColorUtil.withAlpha(var5, 200));
         } else {
            var1.method_25294(0, var17[1], this.field_22789, var17[1] + 1, ColorUtil.withAlpha(var5, 200));
         }
      }

      HudElement var16 = this.dragging != null ? this.dragging : var7;
      if (var16 != null) {
         int var18 = var16.screenX(this.field_22789);
         int var21 = var16.screenY(this.field_22790);
         String var23 = var16.title() + "  " + Math.round(var16.scale() * 100.0F) + "%";
         int var24 = Gfx.width(var23) + 10;
         int var25 = var21 - 16 < 0 ? var21 + var16.scaledHeight() + 4 : var21 - 16;
         Gfx.rect(var1, var18, var25, var24, 13, 6, ColorUtil.withAlpha(var5, 230));
         Gfx.text(var1, var23, var18 + 5, var25 + 3, -1);
      }

      this.renderSidebar(var1, var2, var3);
      String var19 = "Drag · Scroll = scale · Right click = reset scale · Shift = no snapping · Tab = sidebar · Esc = done";
      Gfx.text(var1, var19, (this.field_22789 - Gfx.width(var19) * 0.75F) / 2.0F, this.field_22790 - 10.0F, -7564380, 0.75F);
      Gfx.setTextShadow(true);
   }

   private void renderSidebar(class_332 var1, int var2, int var3) {
      this.sideHits.clear();
      List var4 = DIHClient.hud().elements();
      short var5 = 128;
      byte var6 = 6;
      byte var7 = 6;
      float var8 = Anim.get(this, "sidebar", this.sidebarOpen ? 1.0F : 0.0F, 14.0F);
      int var9 = var4.size();
      int var10 = 24 + var9 * 16 + 44;
      int var11 = (int)(18.0F + (var10 - 18) * var8);
      Gfx.shadow(var1, var6, var7, var5, var11, 7, 5, 1.0F);
      Gfx.rect(var1, var6, var7, var5, var11, 7, -233959403);
      Gfx.outline(var1, var6, var7, var5, var11, 7, 587202559);
      Gfx.accentText(var1, "HUD Editor", var6 + 8, var7 + 5, 0.0);
      Gfx.text(var1, this.sidebarOpen ? "▴" : "▾", var6 + var5 - 12, var7 + 5, -7564380);
      this.sideHits.add(new int[]{var6, var7, var5, 18, -3});
      if (!(var8 < 0.95F)) {
         int var12 = var7 + 22;

         for (int var13 = 0; var13 < var4.size(); var13++) {
            HudElement var14 = (HudElement)var4.get(var13);
            boolean var15 = Gfx.inside(var2, var3, var6 + 4, var12, var5 - 8, 15);
            if (var15) {
               Gfx.rect(var1, var6 + 4, var12, var5 - 8, 15, 4, -14341579);
            }

            Gfx.text(var1, var14.title(), var6 + 10, var12 + 4, var14.isEnabled() ? -1446670 : -10788238);
            float var16 = Anim.get(var14, "editorSwitch", var14.isEnabled() ? 1.0F : 0.0F, 14.0F);
            Gfx.toggle(var1, var6 + var5 - 28, var12 + 3, var16, Theme.accentAt(var13 * 0.08));
            this.sideHits.add(new int[]{var6 + 4, var12, var5 - 8, 15, var13});
            var12 += 16;
         }

         var12 += 4;
         boolean var19 = Gfx.inside(var2, var3, var6 + 6, var12, (var5 - 16) / 2, 16);
         Gfx.rect(var1, var6 + 6, var12, (var5 - 16) / 2, 16, 6, var19 ? -14341579 : -14868182);
         Gfx.textCentered(var1, "Reset", var6 + 6 + (var5 - 16) / 4, var12 + 4, -1446670);
         this.sideHits.add(new int[]{var6 + 6, var12, (var5 - 16) / 2, 16, -1});
         int var20 = var6 + 10 + (var5 - 16) / 2;
         boolean var21 = Gfx.inside(var2, var3, var20, var12, (var5 - 16) / 2, 16);
         Gfx.rect(var1, var20, var12, (var5 - 16) / 2, 16, 6, ColorUtil.withAlpha(Theme.accent(), var21 ? 255 : 190));
         Gfx.textCentered(var1, "Done", var20 + (var5 - 16) / 4, var12 + 4, -1);
         this.sideHits.add(new int[]{var20, var12, (var5 - 16) / 2, 16, -2});
         var12 += 22;
         String var22 = "Style: " + (HudStyle.glass() ? "Glass" : "Minimal");
         Gfx.text(var1, var22, var6 + 8.0F, var12, -10788238, 0.75F);
      }
   }

   private HudElement elementAt(double var1, double var3) {
      List var5 = DIHClient.hud().elements();

      for (int var6 = var5.size() - 1; var6 >= 0; var6--) {
         HudElement var7 = (HudElement)var5.get(var6);
         if (var7.isEnabled()
            && Gfx.inside(var1, var3, var7.screenX(this.field_22789), var7.screenY(this.field_22790), var7.scaledWidth(), var7.scaledHeight())) {
            return var7;
         }
      }

      return null;
   }

   public boolean method_25402(class_11909 var1, boolean var2) {
      double var3 = var1.comp_4798();
      double var5 = var1.comp_4799();

      for (int[] var8 : this.sideHits) {
         if (Gfx.inside(var3, var5, var8[0], var8[1], var8[2], var8[3])) {
            switch (var8[4]) {
               case -3:
                  this.sidebarOpen = !this.sidebarOpen;
                  break;
               case -2:
                  this.method_25419();
                  break;
               case -1:
                  for (HudElement var10 : DIHClient.hud().elements()) {
                     var10.resetPosition();
                  }

                  DIHClient.config().markDirty();
                  break;
               default:
                  HudElement var9 = DIHClient.hud().elements().get(var8[4]);
                  var9.setEnabled(!var9.isEnabled());
                  DIHClient.config().markDirty();
            }

            return true;
         }
      }

      HudElement var11 = this.elementAt(var3, var5);
      if (var11 == null) {
         return super.method_25402(var1, var2);
      } else if (var1.method_74245() == 1) {
         var11.setScale(1.0F);
         DIHClient.config().markDirty();
         return true;
      } else {
         this.dragging = var11;
         this.offX = (int)var3 - var11.screenX(this.field_22789);
         this.offY = (int)var5 - var11.screenY(this.field_22790);
         return true;
      }
   }

   public boolean method_25403(class_11909 var1, double var2, double var4) {
      if (this.dragging == null) {
         return super.method_25403(var1, var2, var4);
      } else {
         int var6 = this.dragging.scaledWidth();
         int var7 = this.dragging.scaledHeight();
         int var8 = (int)var1.comp_4798() - this.offX;
         int var9 = (int)var1.comp_4799() - this.offY;
         this.guides.clear();
         if (this.snapping()) {
            ArrayList var10 = new ArrayList<>(List.of(4, this.field_22789 - 4, this.field_22789 / 2));
            ArrayList var11 = new ArrayList<>(List.of(4, this.field_22790 - 4, this.field_22790 / 2));

            for (HudElement var13 : DIHClient.hud().elements()) {
               if (var13 != this.dragging && var13.isEnabled()) {
                  int var14 = var13.screenX(this.field_22789);
                  int var15 = var13.screenY(this.field_22790);
                  var10.add(var14);
                  var10.add(var14 + var13.scaledWidth());
                  var11.add(var15);
                  var11.add(var15 + var13.scaledHeight());
                  var11.add(var15 + var13.scaledHeight() + 2);
                  var11.add(var15 - 2);
               }
            }

            int[] var18 = snap(var8, var6, var10);
            int[] var19 = snap(var9, var7, var11);
            var8 = var18[0];
            var9 = var19[0];
            if (var18[1] >= 0) {
               this.guides.add(new int[]{1, var18[1]});
            }

            if (var19[1] >= 0) {
               this.guides.add(new int[]{0, var19[1]});
            }
         }

         var8 = Math.max(0, Math.min(this.field_22789 - var6, var8));
         var9 = Math.max(0, Math.min(this.field_22790 - var7, var9));
         this.dragging.setFromScreen(var8, var9, this.field_22789, this.field_22790);
         return true;
      }
   }

   private static int[] snap(int var0, int var1, List<Integer> var2) {
      int var3 = 6;
      int var4 = var0;
      int var5 = -1;

      for (int var7 : var2) {
         int[][] var8 = new int[][]{{var0, var7}, {var0 + var1 / 2, var7 - var1 / 2}, {var0 + var1, var7 - var1}};

         for (int[] var12 : var8) {
            int var13 = Math.abs(var12[0] - var7);
            if (var13 < var3) {
               var3 = var13;
               var4 = var12[1];
               var5 = var7;
            }
         }
      }

      return new int[]{var4, var5};
   }

   public boolean method_25406(class_11909 var1) {
      if (this.dragging != null) {
         DIHClient.config().markDirty();
      }

      this.dragging = null;
      this.guides.clear();
      return super.method_25406(var1);
   }

   public boolean method_25401(double var1, double var3, double var5, double var7) {
      HudElement var9 = this.elementAt(var1, var3);
      if (var9 == null) {
         return false;
      } else {
         int var10 = var9.screenX(this.field_22789) + var9.scaledWidth() / 2;
         int var11 = var9.screenY(this.field_22790) + var9.scaledHeight() / 2;
         var9.setScale(var9.scale() + (float)var7 * 0.05F);
         var9.setFromScreen(var10 - var9.scaledWidth() / 2, var11 - var9.scaledHeight() / 2, this.field_22789, this.field_22790);
         DIHClient.config().markDirty();
         return true;
      }
   }

   public boolean method_25404(class_11908 var1) {
      if (var1.comp_4795() == 258) {
         this.sidebarOpen = !this.sidebarOpen;
         return true;
      } else {
         return super.method_25404(var1);
      }
   }

   public void method_25419() {
      DIHClient.config().save();
      if (this.field_22787 != null) {
         this.field_22787.method_1507(this.parent);
      }
   }
}
