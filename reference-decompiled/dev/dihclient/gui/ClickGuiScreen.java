package dev.dihclient.gui;

import dev.dihclient.DIHClient;
import dev.dihclient.config.GuiState;
import dev.dihclient.gui.theme.Anim;
import dev.dihclient.gui.theme.Skin;
import dev.dihclient.gui.theme.Theme;
import dev.dihclient.module.Category;
import dev.dihclient.module.Module;
import dev.dihclient.module.ModuleManager;
import dev.dihclient.modules.client.ClickGui;
import dev.dihclient.render.Gfx;
import dev.dihclient.setting.ActionSetting;
import dev.dihclient.setting.BoolSetting;
import dev.dihclient.setting.ColorSetting;
import dev.dihclient.setting.DoubleSetting;
import dev.dihclient.setting.EnumSetting;
import dev.dihclient.setting.IdListSetting;
import dev.dihclient.setting.IntSetting;
import dev.dihclient.setting.Setting;
import dev.dihclient.setting.StringSetting;
import dev.dihclient.util.ColorUtil;
import dev.dihclient.util.KeyUtil;
import java.awt.Color;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import net.minecraft.class_10799;
import net.minecraft.class_1109;
import net.minecraft.class_1113;
import net.minecraft.class_11905;
import net.minecraft.class_11908;
import net.minecraft.class_11909;
import net.minecraft.class_124;
import net.minecraft.class_2561;
import net.minecraft.class_2960;
import net.minecraft.class_332;
import net.minecraft.class_3417;
import net.minecraft.class_437;
import net.minecraft.class_7532;

public class ClickGuiScreen extends class_437 implements TextInputScreen {
   private static final class_2960 LOGO = class_2960.method_60655("dihclient", "textures/gui/logo.png");
   private static final int SIDEBAR_W = 116;
   private static final int SETTINGS_W = 184;
   private static final int TOP_H = 34;
   private static final int RADIUS = 7;
   private static final Map<Category, String> ICONS = new EnumMap<>(Category.class);
   private static Module selected;
   private static final Set<ColorSetting> OPEN_COLORS = new HashSet<>();
   private final long openedAt = System.currentTimeMillis();
   private String search = "";
   private boolean searchFocused;
   private float listScroll;
   private float listScrollTarget;
   private float setScroll;
   private float setScrollTarget;
   private int listContentH;
   private int setContentH;
   private Module binding;
   private StringSetting editing;
   private ClickGuiScreen.Drag drag;
   private boolean draggingWindow;
   private int dragOffX;
   private int dragOffY;
   private final List<ClickGuiScreen.Hit> hits = new ArrayList<>();
   private int wx;
   private int wy;
   private int ww;
   private int wh;
   private List<Module> frameModules;
   private static final Map<ClickGuiScreen.WrapKey, List<String>> WRAPS = new HashMap<>();
   private static long wrapsClearedAt;

   public ClickGuiScreen() {
      super(class_2561.method_43470("DIHClient"));
   }

   private static ClickGui gui() {
      return ModuleManager.of(ClickGui.class);
   }

   private static float scale() {
      ClickGui var0 = gui();
      return var0 == null ? 1.0F : var0.scale.getFloat();
   }

   private void hit(int var1, int var2, int var3, int var4, ClickGuiScreen.ClickAction var5) {
      this.hits.add(new ClickGuiScreen.Hit(var1, var2, var3, var4, var5, null));
   }

   private void hitDrag(int var1, int var2, int var3, int var4, ClickGuiScreen.Drag var5) {
      this.hits.add(new ClickGuiScreen.Hit(var1, var2, var3, var4, null, var5));
   }

   private void sound() {
      ClickGui var1 = gui();
      if (var1 != null && var1.clickSound.get() && this.field_22787 != null) {
         this.field_22787.method_1483().method_4873((class_1113)class_1109.method_47978(class_3417.field_15015, var1.clickPitch.getFloat()));
      }
   }

   @Override
   public boolean isTyping() {
      return this.searchFocused || !this.search.isEmpty() || this.editing != null || this.binding != null;
   }

   public boolean method_25421() {
      return false;
   }

   public void method_25419() {
      DIHClient.config().save();
      super.method_25419();
   }

   public void method_25420(class_332 var1, int var2, int var3, float var4) {
      ClickGui var5 = gui();
      if (var5 != null && var5.blur.get()) {
         super.method_25420(var1, var2, var3, var4);
      }

      float var6 = Anim.easeOutCubic((float)(System.currentTimeMillis() - this.openedAt) / 250.0F);
      var1.method_25296(0, 0, this.field_22789, this.field_22790, Skin.dimTop(var6), Skin.dimBottom(var6));
   }

   private List<Module> visibleModules() {
      if (this.frameModules == null) {
         this.frameModules = this.computeVisibleModules();
      }

      return this.frameModules;
   }

   private List<Module> computeVisibleModules() {
      Object var1;
      if (!this.search.isEmpty()) {
         String var2 = this.search.toLowerCase(Locale.ROOT);
         var1 = new ArrayList();

         for (Module var4 : DIHClient.modules().all()) {
            if (!var4.isHidden() && (var4.name().toLowerCase(Locale.ROOT).contains(var2) || var4.description().toLowerCase(Locale.ROOT).contains(var2))) {
               var1.add(var4);
            }
         }
      } else {
         var1 = DIHClient.modules().byCategory(GuiState.category);
      }

      return (List<Module>)var1;
   }

   public void method_25394(class_332 var1, int var2, int var3, float var4) {
      Skin.begin();

      try {
         this.renderSkinned(var1, var2, var3, var4);
      } finally {
         Skin.end();
      }
   }

   private void renderSkinned(class_332 var1, int var2, int var3, float var4) {
      super.method_25394(var1, var2, var3, var4);
      ClickGui var5 = gui();
      if (var5 != null) {
         Anim.setEnabled(var5.animations.get());
      }

      this.hits.clear();
      this.frameModules = null;
      float var6 = scale();
      int var7 = (int)(this.field_22789 / var6);
      int var8 = (int)(this.field_22790 / var6);
      double var9 = var2 / var6;
      double var11 = var3 / var6;
      this.ww = Math.min(600, var7 - 16);
      this.wh = Math.min(360, var8 - 16);
      if (GuiState.windowX >= 0 && GuiState.windowY >= 0) {
         this.wx = Math.max(0, Math.min(var7 - this.ww, GuiState.windowX));
         this.wy = Math.max(0, Math.min(var8 - this.wh, GuiState.windowY));
      } else {
         this.wx = (var7 - this.ww) / 2;
         this.wy = (var8 - this.wh) / 2;
      }

      float var13 = Anim.easeOutBack((float)(System.currentTimeMillis() - this.openedAt) / 260.0F);
      var1.method_51448().pushMatrix();
      var1.method_51448().scale(var6, var6);
      float var14 = 0.92F + 0.08F * var13;
      var1.method_51448().translate(this.wx + this.ww / 2.0F, this.wy + this.wh / 2.0F);
      var1.method_51448().scale(var14, var14);
      var1.method_51448().translate(-(this.wx + this.ww / 2.0F), -(this.wy + this.wh / 2.0F));
      Gfx.shadow(var1, this.wx, this.wy, this.ww, this.wh, 7, 8, 1.0F);
      Gfx.rect(var1, this.wx, this.wy, this.ww, this.wh, 7, Skin.c(-233959403));
      this.renderSidebar(var1, var9, var11);
      this.renderTopBar(var1, var9, var11);
      this.renderModuleList(var1, var9, var11);
      this.renderSettingsPane(var1, var9, var11);
      Gfx.outline(var1, this.wx, this.wy, this.ww, this.wh, 7, Skin.c(587202559));
      var1.method_51448().popMatrix();
   }

   private void renderSidebar(class_332 var1, double var2, double var4) {
      int var6 = this.wx;
      int var7 = this.wy;
      Gfx.rect(var1, var6, var7, 116, this.wh, 7, Skin.c(-16052975));
      var1.method_25294(var6 + 116 - 7, var7, var6 + 116, var7 + this.wh, Skin.c(-16052975));
      var1.method_25294(var6 + 116, var7, var6 + 116 + 1, var7 + this.wh, Skin.c(587202559));
      byte var8 = 16;
      int var9 = var8 * 512 / 284;
      var1.method_25302(class_10799.field_56883, LOGO, var6 + 8, var7 + 9, 0.0F, 0.0F, var9, var8, 512, 284, 512, 284);
      Gfx.text(var1, "client", var6 + 8 + var9 + 2, var7 + 17, Skin.c(-7564380));
      Gfx.text(var1, "v3", var6 + 12.0F, var7 + 28.0F, Skin.c(-10788238), 0.7F);
      byte var10 = 21;
      int var11 = var7 + 44;
      Category[] var12 = Category.values();
      int var13 = this.search.isEmpty() ? GuiState.category.ordinal() : -1;
      float var14 = Anim.get(this, "catIndicator", var13 < 0 ? -1.0F : var13, 14.0F);
      if (var13 >= 0) {
         int var15 = (int)(var11 + var14 * var10);
         Gfx.rect(var1, var6 + 6, var15, 104, var10 - 2, 5, ColorUtil.withAlpha(Theme.accent(), 45));
         Gfx.rect(var1, var6 + 6, var15 + 4, 2, var10 - 10, 1, Theme.accentAt(0.0));
      }

      int[] var27 = new int[var12.length];

      for (Module var17 : DIHClient.modules().all()) {
         if (!var17.isHidden() && var17.isEnabled() && var17.isToggleable()) {
            var27[var17.category().ordinal()]++;
         }
      }

      for (int var28 = 0; var28 < var12.length; var28++) {
         Category var30 = var12[var28];
         int var18 = var11 + var28 * var10;
         boolean var19 = var28 == var13;
         boolean var20 = Gfx.inside(var2, var4, var6 + 6, var18, 104, var10 - 2);
         float var21 = Anim.get(var30, "hover", var20 ? 1.0F : 0.0F, 16.0F);
         if (!var19 && var21 > 0.01F) {
            Gfx.rect(var1, var6 + 6, var18, 104, var10 - 2, 5, Theme.withAlpha(Skin.c(352321535), var21));
         }

         int var22 = var19 ? Skin.c(-1446670) : ColorUtil.blend(Skin.c(-7564380), Skin.c(-1446670), var21);
         Gfx.text(var1, ICONS.getOrDefault(var30, "•"), var6 + 14, var18 + 5, var19 ? Theme.accentAt(var28 * 0.1) : var22);
         Gfx.text(var1, var30.title, var6 + 28, var18 + 5, var22);
         long var23 = var27[var28];
         if (var23 > 0L) {
            String var25 = Long.toString(var23);
            int var26 = Gfx.width(var25) + 6;
            Gfx.rect(var1, var6 + 116 - 12 - var26, var18 + 4, var26, 11, 5, var19 ? ColorUtil.withAlpha(Theme.accent(), 140) : Skin.c(-14868182));
            Gfx.text(var1, var25, var6 + 116 - 12 - var26 + 3, var18 + 6, Skin.c(-1446670));
         }

         this.hit(var6 + 6, var18, 104, var10 - 2, (var2x, var4x, var6x) -> {
            GuiState.category = var30;
            this.search = "";
            this.searchFocused = false;
            this.listScrollTarget = 0.0F;
            this.sound();
         });
      }

      int var29 = var7 + this.wh - 50;
      boolean var31 = Gfx.inside(var2, var4, var6 + 8, var29, 100, 16);
      Gfx.rect(var1, var6 + 8, var29, 100, 16, 5, var31 ? Skin.c(-14341579) : Skin.c(-15328992));
      Gfx.textCentered(var1, "✎ HUD Editor", var6 + 58, var29 + 4, var31 ? Skin.c(-1446670) : Skin.c(-7564380));
      this.hit(var6 + 8, var29, 100, 16, (var1x, var3, var5) -> {
         this.sound();
         if (this.field_22787 != null) {
            this.field_22787.method_1507(new HudEditorScreen(this));
         }
      });
      if (this.field_22787 != null && this.field_22787.field_1724 != null) {
         int var32 = var7 + this.wh - 26;
         class_7532.method_52722(var1, this.field_22787.field_1724.method_52814(), var6 + 10, var32, 14);
         Gfx.text(var1, Gfx.trim(this.field_22787.field_1724.method_7334().name(), 76), var6 + 29, var32 + 1, Skin.c(-1446670));
         String var33 = this.field_22787.method_1542()
            ? "Singleplayer"
            : (this.field_22787.method_1558() != null ? this.field_22787.method_1558().field_3761 : "");
         Gfx.text(var1, Gfx.trim(var33, 108), var6 + 29.0F, var32 + 10.0F, Skin.c(-10788238), 0.7F);
      }
   }

   private int contentX() {
      return this.wx + 116 + 1;
   }

   private int listW() {
      return this.ww - 116 - 1 - 184;
   }

   private void renderTopBar(class_332 var1, double var2, double var4) {
      int var6 = this.contentX() + 12;
      int var7 = this.wy;
      this.hitDrag(this.contentX(), this.wy, this.listW(), 34, null);
      String var8 = this.search.isEmpty() ? GuiState.category.title : "Search";
      Gfx.text(var1, var8, var6, var7 + 11.0F, Skin.c(-1446670), 1.25F);
      List var9 = this.visibleModules();
      String var10 = this.search.isEmpty() ? var9.size() + " modules" : var9.size() + " results";
      Gfx.text(var1, var10, var6 + Gfx.width(var8) * 1.25F + 6.0F, var7 + 14.0F, Skin.c(-10788238), 0.75F);
      byte var11 = 118;
      int var12 = this.contentX() + this.listW() - var11 - 10;
      int var13 = var7 + 9;
      float var14 = Anim.get(this, "searchFocus", !this.searchFocused && this.search.isEmpty() ? 0.0F : 1.0F, 14.0F);
      Gfx.rect(var1, var12, var13, var11, 16, 8, Skin.c(-15328992));
      if (var14 > 0.01F) {
         Gfx.outline(var1, var12, var13, var11, 16, 8, Theme.withAlpha(Theme.accent(), var14));
      }

      String var15 = this.search.isEmpty() ? "Search…" : this.search + (this.searchFocused && System.currentTimeMillis() / 500L % 2L == 0L ? "_" : "");
      Gfx.text(var1, "⌕", var12 + 6, var13 + 4, Skin.c(-10788238));
      Gfx.text(var1, Gfx.trim(var15, var11 - 22), var12 + 16, var13 + 4, this.search.isEmpty() ? Skin.c(-10788238) : Skin.c(-1446670));
      this.hit(var12, var13, var11, 16, (var1x, var3, var5) -> {
         this.searchFocused = true;
         if (var5 == 1) {
            this.search = "";
         }
      });
      var1.method_25294(this.contentX() + 10, this.wy + 34 - 1, this.contentX() + this.listW() - 10, this.wy + 34, Skin.c(587202559));
      if (this.binding != null) {
         String var16 = "Press a key for " + this.binding.name() + "  ·  Esc = unbind";
         int var17 = Gfx.width(var16) + 16;
         int var18 = this.contentX() + (this.listW() - var17) / 2;
         Gfx.rect(var1, var18, this.wy + this.wh - 26, var17, 18, 9, ColorUtil.withAlpha(Theme.accent(), 230));
         Gfx.text(var1, var16, var18 + 8, this.wy + this.wh - 21, -1);
      }
   }

   private void renderModuleList(class_332 var1, double var2, double var4) {
      List var6 = this.visibleModules();
      int var7 = this.contentX() + 10;
      int var8 = this.wy + 34 + 6;
      int var9 = this.listW() - 20;
      int var10 = this.wh - 34 - 12;
      boolean var11 = gui() == null || gui().descriptions.get();
      int var12 = var11 ? 32 : 22;
      byte var13 = 4;
      this.listContentH = var6.size() * (var12 + var13);
      this.listScrollTarget = Math.max(0.0F, Math.min(this.listScrollTarget, (float)Math.max(0, this.listContentH - var10)));
      this.listScroll = Anim.get(this, "listScroll", this.listScrollTarget, 18.0F);
      var1.method_44379(var7 - 2, var8, var7 + var9 + 2, var8 + var10);
      int var14 = var8 - (int)this.listScroll;

      for (int var15 = 0; var15 < var6.size(); var15++) {
         Module var16 = (Module)var6.get(var15);
         if (var14 + var12 >= var8 && var14 <= var8 + var10) {
            this.renderCard(var1, var16, var7, var14, var9, var12, var2, var4, var8, var8 + var10, var11, var15);
         }

         var14 += var12 + var13;
      }

      var1.method_44380();
      if (var6.isEmpty()) {
         Gfx.textCentered(var1, "No modules found", var7 + var9 / 2, var8 + 20, Skin.c(-10788238));
      }

      this.scrollbar(var1, var7 + var9 + 3, var8, var10, this.listScroll, this.listContentH);
      var1.method_25296(var7 - 2, var8, var7 + var9 + 2, var8 + 6, Skin.c(-233959403), Skin.c(921621));
      var1.method_25296(var7 - 2, var8 + var10 - 6, var7 + var9 + 2, var8 + var10, Skin.c(921621), Skin.c(-233959403));
   }

   private void scrollbar(class_332 var1, int var2, int var3, int var4, float var5, int var6) {
      if (var6 > var4) {
         int var7 = Math.max(16, var4 * var4 / var6);
         int var8 = var3 + (int)((var4 - var7) * (var5 / (var6 - var4)));
         Gfx.rect(var1, var2, var8, 2, var7, 1, Skin.c(1090519039));
      }
   }

   private void renderCard(
      class_332 var1, Module var2, int var3, int var4, int var5, int var6, double var7, double var9, int var11, int var12, boolean var13, int var14
   ) {
      boolean var15 = var9 >= var11 && var9 < var12;
      boolean var16 = var15 && Gfx.inside(var7, var9, var3, var4, var5, var6);
      boolean var17 = var2.isEnabled() && var2.isToggleable();
      boolean var18 = var2 == selected;
      float var19 = Anim.get(var2, "cardHover", var16 ? 1.0F : 0.0F, 16.0F);
      float var20 = Anim.get(var2, "cardOn", var17 ? 1.0F : 0.0F, 12.0F);
      int var21 = Theme.accentAt(var14 * 0.08);
      int var22 = ColorUtil.blend(Skin.c(-15328992), Skin.c(-14341579), var19);
      Gfx.rect(var1, var3, var4, var5, var6, 6, var22);
      if (var20 > 0.01F) {
         Gfx.rect(var1, var3, var4, var5, var6, 6, Theme.withAlpha(ColorUtil.withAlpha(var21, 40), var20));
         Gfx.rect(var1, var3 + 1, var4 + 5, 2, var6 - 10, 1, Theme.withAlpha(var21, var20));
      }

      if (var18) {
         Gfx.outline(var1, var3, var4, var5, var6, 6, ColorUtil.withAlpha(var21, 180));
      }

      int var23 = var3 + 10;
      int var24 = var17 ? Skin.onCard() : ColorUtil.blend(Skin.c(-7564380), Skin.c(-1446670), var19);
      int var25 = var13 ? var4 + 6 : var4 + 7;
      var1.method_51439(Gfx.font(), class_2561.method_43470(var2.name()).method_27692(class_124.field_1067), var23, var25, var24, false);
      if (var13) {
         String var26 = Gfx.trim(var2.description(), (int)((var5 - 90) / 0.72F));
         Gfx.text(var1, var26, var23, var4 + 19.0F, Skin.c(-10788238), 0.72F);
      }

      int var30 = var3 + var5 - 8;
      if (var2.isToggleable()) {
         var30 -= 18;
         Gfx.toggle(var1, var30, var4 + (var6 - 10) / 2, var20, var21);
      } else {
         var30 -= 10;
         Gfx.text(var1, "›", var30 + 2, var4 + (var6 - 8) / 2, Skin.c(-10788238));
      }

      String var27 = this.binding == var2 ? "..." : (var2.keybind() >= 0 ? KeyUtil.keyName(var2.keybind()) : null);
      if (var27 != null) {
         int var28 = (int)(Gfx.width(var27) * 0.7F) + 8;
         var30 -= var28 + 5;
         Gfx.rect(var1, var30, var4 + (var6 - 11) / 2, var28, 11, 3, Skin.c(-14868182));
         Gfx.text(var1, var27, var30 + 4.0F, var4 + (var6 - 11) / 2.0F + 3.0F, this.binding == var2 ? var21 : Skin.c(-7564380), 0.7F);
      }

      String var32 = var2.isEnabled() ? var2.getInfo() : null;
      if (var32 != null && !var13) {
         String var29 = Gfx.trim(var32, 60);
         Gfx.text(var1, var29, var30 - 6 - Gfx.width(var29), var4 + 7, Skin.c(-10788238));
      }

      if (var15) {
         this.hit(var3, var4, var5, var6, (var2x, var4x, var6x) -> {
            if (var6x == 0) {
               if (var2.isToggleable()) {
                  var2.toggle();
               }

               this.selectModule(var2);
               this.sound();
            } else if (var6x == 1) {
               this.selectModule(var2);
               this.sound();
            } else if (var6x == 2) {
               this.binding = var2;
            }
         });
      }
   }

   private void selectModule(Module var1) {
      if (selected != var1) {
         selected = var1;
         this.setScrollTarget = 0.0F;
         this.editing = null;
      }
   }

   private void renderSettingsPane(class_332 var1, double var2, double var4) {
      int var6 = this.wx + this.ww - 184;
      int var7 = this.wy;
      short var8 = 184;
      Gfx.rect(var1, var6, var7, var8, this.wh, 7, Skin.c(-15592422));
      var1.method_25294(var6, var7, var6 + 7, var7 + this.wh, Skin.c(-15592422));
      var1.method_25294(var6, var7, var6 + 1, var7 + this.wh, Skin.c(587202559));
      Module var9 = selected;
      if (var9 == null) {
         Gfx.textCentered(var1, "Select a module", var6 + var8 / 2, var7 + this.wh / 2 - 10, Skin.c(-7564380));
         Gfx.text(
            var1,
            "right click a card to open it",
            var6 + var8 / 2.0F - Gfx.width("right click a card to open it") * 0.35F,
            var7 + this.wh / 2.0F + 2.0F,
            Skin.c(-10788238),
            0.7F
         );
      } else {
         int var10 = var6 + 10;
         int var11 = var8 - 20;
         Gfx.text(var1, Gfx.trim(var9.name(), (int)(var11 / 1.15F)), var10, var7 + 11.0F, Skin.c(-1446670), 1.15F);
         int var12 = var7 + 25;
         List var13 = wrap(var9.description(), (int)(var11 / 0.72F));

         for (int var14 = 0; var14 < Math.min(3, var13.size()); var14++) {
            Gfx.text(var1, (String)var13.get(var14), var10, var12, Skin.c(-10788238), 0.72F);
            var12 += 7;
         }

         var12 += 4;
         String var30 = this.binding == var9 ? "press a key" : "Bind: " + KeyUtil.keyName(var9.keybind());
         int var15 = Gfx.width(var30) + 12;
         boolean var16 = Gfx.inside(var2, var4, var10, var12, var15, 14);
         Gfx.rect(
            var1, var10, var12, var15, 14, 7, this.binding == var9 ? ColorUtil.withAlpha(Theme.accent(), 160) : (var16 ? Skin.c(-14341579) : Skin.c(-14868182))
         );
         Gfx.text(var1, var30, var10 + 6, var12 + 3, Skin.c(-1446670));
         this.hit(var10, var12, var15, 14, (var2x, var4x, var6x) -> this.binding = var6x == 1 ? null : var9);
         if (var9.isToggleable()) {
            String var17 = var9.showToggleNotification() ? "♪ Toast" : "♪ Off";
            int var18 = Gfx.width(var17) + 12;
            int var19 = var10 + var15 + 4;
            Gfx.rect(var1, var19, var12, var18, 14, 7, Gfx.inside(var2, var4, var19, var12, var18, 14) ? Skin.c(-14341579) : Skin.c(-14868182));
            Gfx.text(var1, var17, var19 + 6, var12 + 3, var9.showToggleNotification() ? Skin.c(-1446670) : Skin.c(-10788238));
            this.hit(var19, var12, var18, 14, (var2x, var4x, var6x) -> {
               var9.setShowToggleNotification(!var9.showToggleNotification());
               DIHClient.config().markDirty();
               this.sound();
            });
         }

         var12 += 20;
         var1.method_25294(var10, var12, var10 + var11, var12 + 1, Skin.c(587202559));
         var12 += 5;
         int var31 = var12;
         int var32 = var7 + this.wh - var12 - 6;
         int var33 = this.settingsHeight(var9, var11);
         this.setContentH = var33;
         this.setScrollTarget = Math.max(0.0F, Math.min(this.setScrollTarget, (float)Math.max(0, var33 - var32)));
         this.setScroll = Anim.get(this, "setScroll", this.setScrollTarget, 18.0F);
         var1.method_44379(var6 + 2, var12, var6 + var8, var12 + var32);
         int var20 = var12 - (int)this.setScroll;
         boolean var21 = var4 >= var12 && var4 < var12 + var32;

         for (Setting var23 : var9.settings()) {
            if (var23.isVisible()) {
               int var24 = this.settingHeight(var23);
               if (var20 + var24 >= var31 && var20 <= var31 + var32) {
                  this.renderSetting(var1, var23, var10, var20, var11, var2, var4, var21);
               }

               var20 += var24;
            }
         }

         List var36 = var9.details();
         if (!var36.isEmpty()) {
            var20 += 4;
            var1.method_25294(var10, var20, var10 + var11, var20 + 1, Skin.c(587202559));
            var20 += 5;

            for (String var38 : var36) {
               for (String var26 : wrap(var38, (int)(var11 / 0.72F))) {
                  Gfx.text(var1, var26, var10, var20, Skin.c(-7564380), 0.72F);
                  var20 += 8;
               }
            }
         }

         var1.method_44380();
         if (var9.settings().isEmpty() && var36.isEmpty()) {
            Gfx.text(var1, "No settings", var10, var31 + 4, Skin.c(-10788238));
         }

         this.scrollbar(var1, var6 + var8 - 4, var31, var32, this.setScroll, var33);
      }
   }

   private int settingsHeight(Module var1, int var2) {
      int var3 = 0;

      for (Setting var5 : var1.settings()) {
         if (var5.isVisible()) {
            var3 += this.settingHeight(var5);
         }
      }

      List var7 = var1.details();
      if (!var7.isEmpty()) {
         var3 += 10;

         for (String var6 : var7) {
            var3 += wrap(var6, (int)(var2 / 0.72F)).size() * 8;
         }
      }

      return var3 + 4;
   }

   private int settingHeight(Setting<?> var1) {
      if (var1 instanceof DoubleSetting || var1 instanceof IntSetting) {
         return 25;
      } else if (var1 instanceof StringSetting) {
         return 31;
      } else if (var1 instanceof ColorSetting var2) {
         return OPEN_COLORS.contains(var2) ? 98 : 20;
      } else {
         return var1 instanceof ActionSetting ? 21 : 20;
      }
   }

   private void renderSetting(class_332 var1, Setting<?> var2, int var3, int var4, int var5, double var6, double var8, boolean var10) {
      int var11 = Theme.accent();
      boolean var12 = var10 && Gfx.inside(var6, var8, var3, var4, var5, this.settingHeight(var2));
      int var13 = var12 ? Skin.c(-1446670) : ColorUtil.blend(Skin.c(-7564380), Skin.c(-1446670), 0.4F);
      String var14 = var2.name();
      if (var2 instanceof BoolSetting var15) {
         Gfx.text(var1, Gfx.trim(var14, var5 - 26), var3, var4 + 5, var13);
         float var21 = Anim.get(var15, "sw", var15.get() ? 1.0F : 0.0F, 14.0F);
         Gfx.toggle(var1, var3 + var5 - 18, var4 + 4, var21, var11);
         if (var10) {
            this.hit(var3, var4, var5, 18, (var2x, var4x, var6x) -> {
               var15.toggle();
               DIHClient.config().markDirty();
               this.sound();
            });
         }
      } else if (var2 instanceof DoubleSetting || var2 instanceof IntSetting) {
         String var30 = var2.displayValue();
         Gfx.text(var1, Gfx.trim(var14, var5 - Gfx.width(var30) - 8), var3, var4 + 3, var13);
         Gfx.text(var1, var30, var3 + var5 - Gfx.width(var30), var4 + 3, var11);
         double var34 = var2 instanceof DoubleSetting var37 ? var37.fraction() : ((IntSetting)var2).fraction();
         float var38 = Anim.get(var2, "slider", (float)var34, 20.0F);
         Gfx.slider(var1, var3, var4 + 15, var5, var38, var11, var12);
         if (var10) {
            this.hits.add(new ClickGuiScreen.Hit(var3 - 2, var4 + 10, var5 + 4, 14, (var1x, var3x, var5x) -> {
               if (var5x == 1) {
                  var2.reset();
                  DIHClient.config().markDirty();
               }
            }, (var3x, var5x) -> {
               double var7 = (var3x - var3) / var5;
               if (var2 instanceof DoubleSetting var9) {
                  var9.setFraction(var7);
               } else {
                  ((IntSetting)var2).setFraction(var7);
               }

               DIHClient.config().markDirty();
            }));
         }
      } else if (var2 instanceof EnumSetting var16) {
         Gfx.text(var1, Gfx.trim(var14, var5 / 2 - 4), var3, var4 + 5, var13);
         String var25 = var16.displayValue();
         int var22 = Math.max(60, Gfx.width(var25) + 24);
         int var23 = var3 + var5 - var22;
         Gfx.rect(var1, var23, var4 + 2, var22, 14, 7, Skin.c(-14868182));
         Gfx.text(var1, "‹", var23 + 5, var4 + 5, Skin.c(-10788238));
         Gfx.text(var1, "›", var23 + var22 - 9, var4 + 5, Skin.c(-10788238));
         Gfx.textCentered(var1, var25, var23 + var22 / 2, var4 + 5, var11);
         if (var10) {
            this.hit(var23, var4 + 2, var22, 14, (var4x, var6x, var8x) -> {
               boolean var9 = var8x == 1 ? false : var4x >= var23 + var22 / 2.0;
               var16.cycle(var9);
               DIHClient.config().markDirty();
               this.sound();
            });
         }
      } else if (var2 instanceof ColorSetting var17) {
         Gfx.text(var1, Gfx.trim(var14, var5 - 30), var3, var4 + 5, var13);
         int var26 = var3 + var5 - 22;
         checker(var1, var26, var4 + 3, 22, 12);
         Gfx.rect(var1, var26, var4 + 3, 22, 12, 4, var17.get());
         Gfx.outline(var1, var26, var4 + 3, 22, 12, 4, Skin.c(1090519039));
         if (var10) {
            this.hit(var3, var4, var5, 18, (var2x, var4x, var6x) -> {
               if (var6x == 1) {
                  var17.reset();
               } else if (!OPEN_COLORS.remove(var17)) {
                  OPEN_COLORS.add(var17);
               }

               DIHClient.config().markDirty();
               this.sound();
            });
         }

         if (OPEN_COLORS.contains(var17)) {
            this.renderColorPicker(var1, var17, var3, var4 + 20, var5, var10);
         }
      } else if (var2 instanceof IdListSetting var18) {
         Gfx.text(var1, Gfx.trim(var14, var5 / 2), var3, var4 + 5, var13);
         String var27 = "Edit · " + var18.get().size();
         int var31 = Gfx.width(var27) + 14;
         boolean var35 = var10 && Gfx.inside(var6, var8, var3 + var5 - var31, var4 + 2, var31, 14);
         Gfx.rect(var1, var3 + var5 - var31, var4 + 2, var31, 14, 7, var35 ? ColorUtil.withAlpha(var11, 200) : ColorUtil.withAlpha(var11, 110));
         Gfx.text(var1, var27, var3 + var5 - var31 + 7, var4 + 5, Skin.onCard());
         if (var10) {
            this.hit(var3 + var5 - var31, var4 + 2, var31, 14, (var2x, var4x, var6x) -> {
               this.sound();
               if (this.field_22787 != null) {
                  this.field_22787.method_1507(new TargetSelectorScreen(this, var18));
               }
            });
         }
      } else if (var2 instanceof StringSetting var19) {
         Gfx.text(var1, var14, var3, var4 + 2, var13);
         boolean var28 = this.editing == var19;
         Gfx.rect(var1, var3, var4 + 13, var5, 15, 5, Skin.c(-15328992));
         if (var28) {
            Gfx.outline(var1, var3, var4 + 13, var5, 15, 5, var11);
         }

         String var32 = var19.get();
         String var36 = var28 ? var32 + (System.currentTimeMillis() / 500L % 2L == 0L ? "_" : "") : (var32.isEmpty() ? "empty" : var32);
         String var24 = var36;

         while (Gfx.width(var24) > var5 - 10 && var24.length() > 1) {
            var24 = var24.substring(1);
         }

         Gfx.text(var1, var24, var3 + 5, var4 + 17, var32.isEmpty() && !var28 ? Skin.c(-10788238) : Skin.c(-1446670));
         if (var10) {
            this.hit(var3, var4 + 13, var5, 15, (var2x, var4x, var6x) -> {
               if (var6x == 1) {
                  var19.reset();
                  this.editing = null;
               } else {
                  this.editing = var19;
               }

               DIHClient.config().markDirty();
            });
         }
      } else if (var2 instanceof ActionSetting var20) {
         boolean var29 = var10 && Gfx.inside(var6, var8, var3, var4 + 2, var5, 16);
         float var33 = Anim.get(var20, "btn", var29 ? 1.0F : 0.0F, 16.0F);
         Gfx.rect(var1, var3, var4 + 2, var5, 16, 6, ColorUtil.blend(Skin.c(-14868182), ColorUtil.withAlpha(var11, 210), var33));
         Gfx.textCentered(var1, var14, var3 + var5 / 2, var4 + 6, Skin.c(-1446670));
         if (var10) {
            this.hit(var3, var4 + 2, var5, 16, (var2x, var4x, var6x) -> {
               this.sound();
               var20.run();
            });
         }
      }
   }

   private static void checker(class_332 var0, int var1, int var2, int var3, int var4) {
      for (byte var5 = 0; var5 < var3; var5 = (byte)(var5 + 3)) {
         for (byte var6 = 0; var6 < var4; var6 = (byte)(var6 + 3)) {
            var0.method_25294(
               var1 + var5, var2 + var6, var1 + Math.min(var3, var5 + 3), var2 + Math.min(var4, var6 + 3), (var5 + var6) / 3 % 2 == 0 ? -12959925 : -14276045
            );
         }
      }
   }

   private void renderColorPicker(class_332 var1, ColorSetting var2, int var3, int var4, int var5, boolean var6) {
      int var7 = var2.get();
      float[] var8 = Color.RGBtoHSB(var7 >> 16 & 0xFF, var7 >> 8 & 0xFF, var7 & 0xFF, null);
      int var9 = var7 >>> 24 & 0xFF;
      int var10 = var5 - 14;
      byte var11 = 56;
      int var12 = 0xFF000000 | Color.HSBtoRGB(var8[0], 1.0F, 1.0F);

      for (byte var13 = 0; var13 < var10; var13 = (byte)(var13 + 2)) {
         int var14 = ColorUtil.blend(-1, var12, (float)var13 / (var10 - 1));
         var1.method_25296(var3 + var13, var4, var3 + Math.min(var10, var13 + 2), var4 + var11, var14, -16777216);
      }

      int var21 = var3 + (int)(var8[1] * (var10 - 1));
      int var22 = var4 + (int)((1.0F - var8[2]) * (var11 - 1));
      Gfx.outline(var1, var21 - 2, var22 - 2, 5, 5, 1, -1);
      int var15 = var3 + var10 + 4;

      for (int var16 = 0; var16 < 6; var16++) {
         int var17 = 0xFF000000 | Color.HSBtoRGB(var16 / 6.0F, 1.0F, 1.0F);
         int var18 = 0xFF000000 | Color.HSBtoRGB((var16 + 1) / 6.0F, 1.0F, 1.0F);
         int var19 = var4 + var16 * var11 / 6;
         int var20 = var4 + (var16 + 1) * var11 / 6;
         var1.method_25296(var15, var19, var15 + 10, var20, var17, var18);
      }

      int var23 = var4 + (int)(var8[0] * (var11 - 1));
      var1.method_25294(var15 - 1, var23, var15 + 11, var23 + 1, -1);
      int var24 = var4 + var11 + 5;
      checker(var1, var3, var24, var5, 8);
      Gfx.hGradient(var1, var3, var24, var5, 8, var7 & 16777215, var7 | 0xFF000000);
      int var25 = var3 + (int)(var9 / 255.0F * (var5 - 1));
      var1.method_25294(var25, var24 - 1, var25 + 1, var24 + 9, -1);
      String var26 = var2.displayValue() + String.format(Locale.ROOT, "  %d%%", Math.round(var9 / 2.55F));
      Gfx.text(var1, var26, var3, var24 + 11.0F, Skin.c(-10788238), 0.7F);
      if (var6) {
         this.hitDrag(var3, var4, var10, var11, (var6x, var8x) -> {
            float var10x = (float)Math.max(0.0, Math.min(1.0, (var6x - var3) / (var10 - 1)));
            float var11x = (float)Math.max(0.0, Math.min(1.0, 1.0 - (var8x - var4) / (var11 - 1)));
            float[] var12x = Color.RGBtoHSB(var2.get() >> 16 & 0xFF, var2.get() >> 8 & 0xFF, var2.get() & 0xFF, null);
            float var13x = !(var12x[1] <= 0.001F) && !(var12x[2] <= 0.001F) ? var12x[0] : var8[0];
            var2.set(var2.get() & 0xFF000000 | Color.HSBtoRGB(var13x, var10x, var11x) & 16777215);
            DIHClient.config().markDirty();
         });
         this.hitDrag(var15 - 1, var4, 12, var11, (var3x, var5x) -> {
            float var7x = (float)Math.max(0.0, Math.min(0.999, (var5x - var4) / (var11 - 1)));
            float[] var8x = Color.RGBtoHSB(var2.get() >> 16 & 0xFF, var2.get() >> 8 & 0xFF, var2.get() & 0xFF, null);
            float var9x = Math.max(var8x[1], 0.01F);
            float var10x = Math.max(var8x[2], 0.01F);
            var2.set(var2.get() & 0xFF000000 | Color.HSBtoRGB(var7x, var9x, var10x) & 16777215);
            DIHClient.config().markDirty();
         });
         this.hitDrag(var3, var24 - 2, var5, 12, (var3x, var5x) -> {
            var2.setChannel(3, (int)Math.round(Math.max(0.0, Math.min(1.0, (var3x - var3) / (var5 - 1))) * 255.0));
            DIHClient.config().markDirty();
         });
      }
   }

   private static List<String> wrap(String var0, int var1) {
      long var2 = System.currentTimeMillis();
      if (WRAPS.size() > 256 || var2 - wrapsClearedAt > 2000L) {
         WRAPS.clear();
         wrapsClearedAt = var2;
      }

      return WRAPS.computeIfAbsent(new ClickGuiScreen.WrapKey(var0, var1), var0x -> wrapNow(var0x.text(), var0x.width()));
   }

   private static List<String> wrapNow(String var0, int var1) {
      ArrayList var2 = new ArrayList();
      StringBuilder var3 = new StringBuilder();

      for (String var7 : var0.split(" ")) {
         String var8 = var3.length() == 0 ? var7 : var3 + " " + var7;
         if (Gfx.width(var8) > var1 && var3.length() > 0) {
            var2.add(var3.toString());
            var3 = new StringBuilder(var7);
         } else {
            var3 = new StringBuilder(var8);
         }
      }

      if (var3.length() > 0) {
         var2.add(var3.toString());
      }

      return var2;
   }

   private ClickGuiScreen.Hit hitAt(double var1, double var3) {
      for (int var5 = this.hits.size() - 1; var5 >= 0; var5--) {
         if (this.hits.get(var5).contains(var1, var3)) {
            return this.hits.get(var5);
         }
      }

      return null;
   }

   public boolean method_25402(class_11909 var1, boolean var2) {
      float var3 = scale();
      double var4 = var1.comp_4798() / var3;
      double var6 = var1.comp_4799() / var3;
      int var8 = var1.method_74245();
      if (this.binding != null) {
         this.binding = null;
         return true;
      } else {
         ClickGuiScreen.Hit var9 = this.hitAt(var4, var6);
         boolean var10 = var9 != null && this.editing != null;
         if (!var10) {
            this.editing = null;
         }

         this.searchFocused = false;
         if (var9 == null) {
            return true;
         } else if (var9.drag() == null && var9.click() == null) {
            this.draggingWindow = var8 == 0;
            this.dragOffX = (int)var4 - this.wx;
            this.dragOffY = (int)var6 - this.wy;
            return true;
         } else {
            if (var9.drag() != null && var8 == 0) {
               this.drag = var9.drag();
               this.drag.drag(var4, var6);
            }

            if (var9.click() != null) {
               var9.click().click(var4, var6, var8);
            }

            return true;
         }
      }
   }

   public boolean method_25403(class_11909 var1, double var2, double var4) {
      float var6 = scale();
      double var7 = var1.comp_4798() / var6;
      double var9 = var1.comp_4799() / var6;
      if (this.draggingWindow) {
         GuiState.windowX = (int)var7 - this.dragOffX;
         GuiState.windowY = (int)var9 - this.dragOffY;
         return true;
      } else if (this.drag != null) {
         this.drag.drag(var7, var9);
         return true;
      } else {
         return super.method_25403(var1, var2, var4);
      }
   }

   public boolean method_25406(class_11909 var1) {
      if (this.draggingWindow || this.drag != null) {
         DIHClient.config().markDirty();
      }

      this.draggingWindow = false;
      this.drag = null;
      return super.method_25406(var1);
   }

   public boolean method_25401(double var1, double var3, double var5, double var7) {
      float var9 = scale();
      double var10 = var1 / var9;
      if (var10 >= this.wx + this.ww - 184) {
         this.setScrollTarget -= (float)(var7 * 24.0);
      } else {
         this.listScrollTarget -= (float)(var7 * 28.0);
      }

      return true;
   }

   public boolean method_25404(class_11908 var1) {
      int var2 = var1.comp_4795();
      if (this.binding != null) {
         if (var2 != 256 && var2 != 261 && var2 != 259) {
            this.binding.setKeybind(var2);
         } else {
            this.binding.setKeybind(-1);
         }

         this.binding = null;
         this.sound();
         return true;
      } else if (this.editing != null) {
         if (var2 == 259) {
            String var3 = this.editing.get();
            if (!var3.isEmpty()) {
               this.editing.set(var3.substring(0, var3.length() - 1));
            }
         } else if (var2 == 257 || var2 == 335 || var2 == 256) {
            this.editing = null;
         } else if (var2 == 86 && (var1.comp_4797() & 2) != 0 && this.field_22787 != null) {
            this.editing.set(this.editing.get() + this.field_22787.field_1774.method_1460());
         }

         DIHClient.config().markDirty();
         return true;
      } else if (var2 == 259 && !this.search.isEmpty()) {
         this.search = this.search.substring(0, this.search.length() - 1);
         this.listScrollTarget = 0.0F;
         return true;
      } else if (var2 != 256 || this.search.isEmpty() && !this.searchFocused) {
         if (this.search.isEmpty() && !this.searchFocused && DIHClient.clickGuiKey() != null && DIHClient.clickGuiKey().method_1417(var1)) {
            this.method_25419();
            return true;
         } else {
            return super.method_25404(var1);
         }
      } else {
         this.search = "";
         this.searchFocused = false;
         return true;
      }
   }

   public boolean method_25400(class_11905 var1) {
      if (!var1.method_74227()) {
         return false;
      } else {
         String var2 = var1.method_74226();
         if (this.editing != null) {
            if (this.editing.get().length() < this.editing.maxLength()) {
               this.editing.set(this.editing.get() + var2);
            }

            return true;
         } else if (this.binding != null) {
            return true;
         } else {
            if (this.search.length() < 32) {
               this.search = this.search + var2;
               this.listScrollTarget = 0.0F;
            }

            return true;
         }
      }
   }

   static {
      ICONS.put(Category.COMBAT, "⚔");
      ICONS.put(Category.MOVEMENT, "➤");
      ICONS.put(Category.PLAYER, "☺");
      ICONS.put(Category.RENDER, "◉");
      ICONS.put(Category.WORLD, "▣");
      ICONS.put(Category.BASEFINDING, "⌖");
      ICONS.put(Category.MISC, "✦");
      ICONS.put(Category.FUN, "☻");
      ICONS.put(Category.CLIENT, "⚙");
   }

   @FunctionalInterface
   private interface ClickAction {
      void click(double var1, double var3, int var5);
   }

   @FunctionalInterface
   private interface Drag {
      void drag(double var1, double var3);
   }

   private record Hit(int x, int y, int w, int h, ClickGuiScreen.ClickAction click, ClickGuiScreen.Drag drag) {
      boolean contains(double var1, double var3) {
         return var1 >= this.x && var1 < this.x + this.w && var3 >= this.y && var3 < this.y + this.h;
      }
   }

   private record WrapKey(String text, int width) {
   }
}
