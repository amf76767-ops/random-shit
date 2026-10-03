package dev.dihclient.gui;

import dev.dihclient.account.Account;
import dev.dihclient.account.AccountManager;
import dev.dihclient.account.AuthApi;
import dev.dihclient.hud.HudManager;
import dev.dihclient.render.Gfx;
import dev.dihclient.util.ColorUtil;
import java.util.List;
import net.minecraft.class_11905;
import net.minecraft.class_11908;
import net.minecraft.class_11909;
import net.minecraft.class_2561;
import net.minecraft.class_310;
import net.minecraft.class_332;
import net.minecraft.class_437;

public class AccountsScreen extends class_437 implements TextInputScreen {
   private static final int ROW_H = 22;
   private final class_437 parent;
   private final AccountManager manager = AccountManager.get();
   private int scroll;
   private int selected = -1;
   private Account.Kind inputKind;
   private String input = "";

   public AccountsScreen(class_437 var1) {
      super(class_2561.method_43470("DIHClient Accounts"));
      this.parent = var1;
   }

   @Override
   public boolean isTyping() {
      return this.inputKind != null;
   }

   public boolean method_25421() {
      return false;
   }

   private int panelX() {
      return this.field_22789 / 2 - 170;
   }

   private int panelW() {
      return 340;
   }

   private int listY() {
      return 74;
   }

   private int visibleRows() {
      return Math.max(1, (this.field_22790 - this.listY() - 96) / 22);
   }

   public void method_25394(class_332 var1, int var2, int var3, float var4) {
      super.method_25394(var1, var2, var3, var4);
      int var5 = HudManager.accent();
      int var6 = this.panelX();
      int var7 = this.panelW();
      Gfx.panel(var1, var6 - 8, 12, var7 + 16, this.field_22790 - 24, var5);
      Gfx.text(var1, "Accounts", var6, 20, var5);
      Gfx.text(var1, "Logged in features use the selected account's token.", var6, 34, -7564380);
      if (this.inputKind != null) {
         this.drawInput(var1, var2, var3, var6, var7);
      } else {
         AuthApi.DeviceCode var8 = this.manager.pendingCode();
         if (var8 != null) {
            Gfx.round(var1, var6, 50, var7, 44, -14868182);
            Gfx.text(var1, "Open " + var8.url() + " and enter this code:", var6 + 8, 56, -1446670);
            Gfx.text(var1, var8.userCode(), var6 + 8, 72, var5);
            Gfx.text(var1, "(waiting for you to finish in the browser…)", var6 + 8 + Gfx.width(var8.userCode()) + 12, 74, -7564380);
         } else {
            this.button(var1, var2, var3, var6, 50, var7 / 3 - 4, "+ Microsoft", true);
            this.button(var1, var2, var3, var6 + var7 / 3 + 2, 50, var7 / 3 - 4, "+ Session token", true);
            this.button(var1, var2, var3, var6 + 2 * (var7 / 3) + 4, 50, var7 / 3 - 6, "+ Refresh token", true);
         }

         this.drawList(var1, var2, var3, var6, var7);
         int var9 = this.field_22790 - 66;
         boolean var10 = this.selected >= 0 && this.selected < this.manager.accounts().size();
         this.button(var1, var2, var3, var6, var9, var7 / 2 - 4, "Log in", var10);
         this.button(var1, var2, var3, var6 + var7 / 2 + 4, var9, var7 / 2 - 4, "Remove", var10);
      }

      Gfx.round(var1, var6, this.field_22790 - 40, var7, 16, -14868182);
      Gfx.text(var1, this.manager.status().isEmpty() ? "Ready" : this.manager.status(), var6 + 6, this.field_22790 - 36, this.manager.busy() ? var5 : -7564380);
      Gfx.textCentered(var1, "Esc = back", this.field_22789 / 2, this.field_22790 - 18, -7564380);
   }

   private void drawList(class_332 var1, int var2, int var3, int var4, int var5) {
      List var6 = this.manager.accounts();
      int var7 = this.visibleRows();
      if (var6.isEmpty()) {
         Gfx.text(var1, "No accounts yet – add one above.", var4, this.listY() + 6, -7564380);
      }

      for (int var8 = 0; var8 < var7 && this.scroll + var8 < var6.size(); var8++) {
         int var9 = this.scroll + var8;
         Account var10 = (Account)var6.get(var9);
         int var11 = this.listY() + var8 * 22;
         boolean var12 = Gfx.inside(var2, var3, var4, var11, var5, 21);
         boolean var13 = var9 == this.selected;
         Gfx.round(var1, var4, var11, var5, 20, var13 ? ColorUtil.withAlpha(HudManager.accent(), 90) : (var12 ? 822083583 : 419430399));
         Gfx.text(var1, var10.name, var4 + 8, var11 + 3, -1);
         Gfx.text(var1, var10.label() + "  ·  " + var10.uuid.toString().substring(0, 8), var4 + 8, var11 + 12, -7564380);
      }
   }

   private void drawInput(class_332 var1, int var2, int var3, int var4, int var5) {
      String var6 = this.inputKind == Account.Kind.SESSION ? "Paste a Minecraft session (access) token:" : "Paste a Microsoft refresh token:";
      Gfx.text(var1, var6, var4, 54, -1446670);
      Gfx.round(var1, var4, 68, var5, 20, -14868182);
      String var7 = this.input.isEmpty() ? "click Paste or start typing…" : mask(this.input);
      Gfx.text(var1, var7, var4 + 6, 74, this.input.isEmpty() ? -7564380 : -1);
      this.button(var1, var2, var3, var4, 96, 80, "Paste", true);
      this.button(var1, var2, var3, var4 + 88, 96, 80, "Confirm", !this.input.isBlank());
      this.button(var1, var2, var3, var4 + 176, 96, 80, "Cancel", true);
      Gfx.text(var1, "Tokens are stored locally in dihclient/accounts.json.", var4, 124, -7564380);
   }

   private static String mask(String var0) {
      int var1 = var0.length();
      return var1 <= 14 ? var0.substring(0, Math.min(6, var1)) + "…" : var0.substring(0, 8) + "…(" + var1 + " chars)…" + var0.substring(var1 - 4);
   }

   private boolean button(class_332 var1, int var2, int var3, int var4, int var5, int var6, String var7, boolean var8) {
      boolean var9 = var8 && Gfx.inside(var2, var3, var4, var5, var6, 18);
      Gfx.round(var1, var4, var5, var6, 18, var8 ? (var9 ? ColorUtil.withAlpha(HudManager.accent(), 150) : -14079703) : -13421773);
      Gfx.textCentered(var1, var7, var4 + var6 / 2, var5 + 5, var8 ? -1 : -9539986);
      return var9;
   }

   public boolean method_25402(class_11909 var1, boolean var2) {
      int var3 = (int)var1.comp_4798();
      int var4 = (int)var1.comp_4799();
      int var5 = this.panelX();
      int var6 = this.panelW();
      if (this.inputKind != null) {
         if (this.hit(var3, var4, var5, 96, 80)) {
            this.paste();
         } else if (this.hit(var3, var4, var5 + 88, 96, 80) && !this.input.isBlank()) {
            this.manager.addFromToken(this.inputKind, this.input.trim());
            this.inputKind = null;
            this.input = "";
         } else if (this.hit(var3, var4, var5 + 176, 96, 80)) {
            this.inputKind = null;
            this.input = "";
         }

         return true;
      } else {
         if (this.manager.pendingCode() == null && !this.manager.busy()) {
            if (this.hit(var3, var4, var5, 50, var6 / 3 - 4)) {
               this.manager.startMicrosoft();
               return true;
            }

            if (this.hit(var3, var4, var5 + var6 / 3 + 2, 50, var6 / 3 - 4)) {
               this.inputKind = Account.Kind.SESSION;
               return true;
            }

            if (this.hit(var3, var4, var5 + 2 * (var6 / 3) + 4, 50, var6 / 3 - 6)) {
               this.inputKind = Account.Kind.REFRESH;
               return true;
            }
         }

         List var7 = this.manager.accounts();
         int var8 = this.visibleRows();

         for (int var9 = 0; var9 < var8 && this.scroll + var9 < var7.size(); var9++) {
            if (this.hit(var3, var4, var5, this.listY() + var9 * 22, var6)) {
               this.selected = this.scroll + var9;
               return true;
            }
         }

         int var10 = this.field_22790 - 66;
         if (this.selected >= 0 && this.selected < var7.size()) {
            if (this.hit(var3, var4, var5, var10, var6 / 2 - 4)) {
               this.manager.login((Account)var7.get(this.selected));
               return true;
            }

            if (this.hit(var3, var4, var5 + var6 / 2 + 4, var10, var6 / 2 - 4)) {
               this.manager.remove((Account)var7.get(this.selected));
               this.selected = -1;
               return true;
            }
         }

         return super.method_25402(var1, var2);
      }
   }

   private boolean hit(int var1, int var2, int var3, int var4, int var5) {
      return this.hit(var1, var2, var3, var4, var5, 18);
   }

   private boolean hit(int var1, int var2, int var3, int var4, int var5, int var6) {
      return Gfx.inside(var1, var2, var3, var4, var5, var6);
   }

   private void paste() {
      try {
         String var1 = class_310.method_1551().field_1774.method_1460();
         if (var1 != null) {
            this.input = var1.trim();
         }
      } catch (Throwable var2) {
      }
   }

   public boolean method_25401(double var1, double var3, double var5, double var7) {
      int var9 = this.manager.accounts().size();
      this.scroll = Math.max(0, Math.min(Math.max(0, var9 - this.visibleRows()), this.scroll - (int)(var7 * 2.0)));
      return true;
   }

   public boolean method_25404(class_11908 var1) {
      int var2 = var1.comp_4795();
      if (this.inputKind != null) {
         if (var2 == 259 && !this.input.isEmpty()) {
            this.input = this.input.substring(0, this.input.length() - 1);
            return true;
         } else if (var2 != 257 && var2 != 335) {
            if (var2 == 256) {
               this.inputKind = null;
               this.input = "";
               return true;
            } else {
               if (var2 == 86) {
                  this.paste();
               }

               return super.method_25404(var1);
            }
         } else {
            if (!this.input.isBlank()) {
               this.manager.addFromToken(this.inputKind, this.input.trim());
               this.inputKind = null;
               this.input = "";
            }

            return true;
         }
      } else if (var2 == 256) {
         this.method_25419();
         return true;
      } else {
         return super.method_25404(var1);
      }
   }

   public boolean method_25400(class_11905 var1) {
      if (this.inputKind != null && var1.method_74227()) {
         String var2 = var1.method_74226();
         if (var2 != null && !var2.equals("\n")) {
            this.input = this.input + var2;
         }

         return true;
      } else {
         return false;
      }
   }

   public void method_25419() {
      if (this.field_22787 != null) {
         this.field_22787.method_1507(this.parent);
      }
   }
}
