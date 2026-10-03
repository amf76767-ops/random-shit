package dev.dihclient.gui;

import dev.dihclient.DIHClient;
import dev.dihclient.hud.HudManager;
import dev.dihclient.render.Gfx;
import dev.dihclient.setting.IdListSetting;
import dev.dihclient.util.ColorUtil;
import dev.dihclient.util.RegistryUtil;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.class_11905;
import net.minecraft.class_11908;
import net.minecraft.class_11909;
import net.minecraft.class_1792;
import net.minecraft.class_1799;
import net.minecraft.class_2248;
import net.minecraft.class_2561;
import net.minecraft.class_2960;
import net.minecraft.class_332;
import net.minecraft.class_437;
import net.minecraft.class_7923;

public class TargetSelectorScreen extends class_437 implements TextInputScreen {
   private static final int ROW_H = 18;
   private final class_437 parent;
   private final IdListSetting setting;
   private final List<String> all = new ArrayList<>();
   private List<String> filtered = new ArrayList<>();
   private String query = "";
   private int scroll;
   private boolean onlySelected;

   public TargetSelectorScreen(class_437 var1, IdListSetting var2) {
      super(class_2561.method_43470("Select " + var2.name()));
      this.parent = var1;
      this.setting = var2;
      if (var2.kind() == IdListSetting.Kind.BLOCK) {
         for (Object var4 : class_7923.field_41175.method_10235()) {
            this.all.add(var4.toString());
         }
      } else if (var2.kind() == IdListSetting.Kind.ITEM) {
         for (Object var7 : class_7923.field_41178.method_10235()) {
            if (!var7.toString().equals("minecraft:air")) {
               this.all.add(var7.toString());
            }
         }
      } else {
         for (Object var8 : class_7923.field_41177.method_10235()) {
            this.all.add(var8.toString());
         }
      }

      this.all.sort(String::compareTo);
      this.refilter();
   }

   @Override
   public boolean isTyping() {
      return true;
   }

   public boolean method_25421() {
      return false;
   }

   private void refilter() {
      String var1 = this.query.toLowerCase(Locale.ROOT);
      this.filtered = new ArrayList<>();

      for (String var3 : this.all) {
         if (this.setting.contains(var3) && var3.contains(var1)) {
            this.filtered.add(var3);
         }
      }

      if (!this.onlySelected) {
         for (String var5 : this.all) {
            if (!this.setting.contains(var5) && var5.contains(var1)) {
               this.filtered.add(var5);
            }
         }
      }

      this.scroll = Math.max(0, Math.min(this.scroll, Math.max(0, this.filtered.size() - this.visibleRows())));
   }

   private int listX() {
      return this.field_22789 / 2 - 150;
   }

   private int listY() {
      return 52;
   }

   private int visibleRows() {
      return Math.max(1, (this.field_22790 - this.listY() - 36) / 18);
   }

   public void method_25394(class_332 var1, int var2, int var3, float var4) {
      super.method_25394(var1, var2, var3, var4);
      int var5 = HudManager.accent();
      int var6 = this.listX();
      short var7 = 300;
      Gfx.panel(var1, var6 - 6, 8, var7 + 12, this.field_22790 - 16, var5);
      Gfx.text(var1, "Select " + this.setting.name() + " (" + this.setting.get().size() + " selected)", var6, 14, var5);
      Gfx.round(var1, var6, 28, var7 - 90, 16, -14868182);
      String var8 = this.query.isEmpty() ? "Type to search…" : this.query + (System.currentTimeMillis() / 500L % 2L == 0L ? "_" : "");
      Gfx.text(var1, var8, var6 + 5, 32, this.query.isEmpty() ? -7564380 : -1446670);
      Gfx.round(var1, var6 + var7 - 84, 28, 84, 16, this.onlySelected ? var5 : -14868182);
      Gfx.textCentered(var1, "Only selected", var6 + var7 - 42, 32, -1446670);
      int var9 = this.visibleRows();

      for (int var10 = 0; var10 < var9 && this.scroll + var10 < this.filtered.size(); var10++) {
         String var11 = this.filtered.get(this.scroll + var10);
         int var12 = this.listY() + var10 * 18;
         boolean var13 = this.setting.contains(var11);
         boolean var14 = Gfx.inside(var2, var3, var6, var12, var7, 17);
         Gfx.round(var1, var6, var12, var7, 16, var13 ? ColorUtil.withAlpha(var5, 90) : (var14 ? 822083583 : 419430399));
         if (this.setting.kind() == IdListSetting.Kind.BLOCK) {
            class_2248 var15 = (class_2248)class_7923.field_41175.method_63535(class_2960.method_60654(var11));
            class_1799 var16 = new class_1799(var15.method_8389());
            if (!var16.method_7960()) {
               var1.method_51427(var16, var6 + 1, var12);
            }
         } else if (this.setting.kind() == IdListSetting.Kind.ITEM) {
            class_1792 var18 = (class_1792)class_7923.field_41178.method_63535(class_2960.method_60654(var11));
            class_1799 var19 = new class_1799(var18);
            if (!var19.method_7960()) {
               var1.method_51427(var19, var6 + 1, var12);
            }
         }

         Gfx.text(var1, RegistryUtil.pretty(var11), var6 + 20, var12 + 4, var13 ? -1 : -1446670);
         Gfx.text(var1, var11, var6 + var7 - 4 - Gfx.width(var11), var12 + 4, -7564380);
      }

      String var17 = "Click toggles · Esc/Enter = done · " + this.filtered.size() + " results";
      Gfx.textCentered(var1, var17, this.field_22789 / 2, this.field_22790 - 22, -7564380);
   }

   public boolean method_25402(class_11909 var1, boolean var2) {
      double var3 = var1.comp_4798();
      double var5 = var1.comp_4799();
      int var7 = this.listX();
      short var8 = 300;
      if (Gfx.inside(var3, var5, var7 + var8 - 84, 28, 84, 16)) {
         this.onlySelected = !this.onlySelected;
         this.refilter();
         return true;
      } else {
         if (var3 >= var7 && var3 < var7 + var8 && var5 >= this.listY()) {
            int var9 = this.scroll + (int)((var5 - this.listY()) / 18.0);
            if (var9 >= 0 && var9 < this.filtered.size()) {
               this.setting.toggle(this.filtered.get(var9));
               DIHClient.config().markDirty();
               return true;
            }
         }

         return super.method_25402(var1, var2);
      }
   }

   public boolean method_25401(double var1, double var3, double var5, double var7) {
      this.scroll = Math.max(0, Math.min(Math.max(0, this.filtered.size() - this.visibleRows()), this.scroll - (int)(var7 * 3.0)));
      return true;
   }

   public boolean method_25404(class_11908 var1) {
      int var2 = var1.comp_4795();
      if (var2 == 259) {
         if (!this.query.isEmpty()) {
            this.query = this.query.substring(0, this.query.length() - 1);
            this.refilter();
         }

         return true;
      } else if (var2 != 256 && var2 != 257 && var2 != 335) {
         return super.method_25404(var1);
      } else {
         this.method_25419();
         return true;
      }
   }

   public boolean method_25400(class_11905 var1) {
      if (!var1.method_74227()) {
         return false;
      } else {
         this.query = this.query + var1.method_74226().toLowerCase(Locale.ROOT);
         this.scroll = 0;
         this.refilter();
         return true;
      }
   }

   public void method_25419() {
      DIHClient.config().save();
      if (this.field_22787 != null) {
         this.field_22787.method_1507(this.parent);
      }
   }
}
