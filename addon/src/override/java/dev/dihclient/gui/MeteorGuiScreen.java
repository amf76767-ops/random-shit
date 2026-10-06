package dev.dihclient.gui;

import dev.dihclient.DIHClient;
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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import net.minecraft.class_1109;
import net.minecraft.class_1113;
import net.minecraft.class_11905;
import net.minecraft.class_11908;
import net.minecraft.class_11909;
import net.minecraft.class_2561;
import net.minecraft.class_310;
import net.minecraft.class_332;
import net.minecraft.class_3417;
import net.minecraft.class_437;

public class MeteorGuiScreen extends class_437 implements TextInputScreen {
   private static final int PANEL_W = 124;
   private static final int HEADER_H = 22;
   private static final int ROW_H = 16;
   private static final int INSET = 4;
   private static final int PAD = 4;
   private static final int GAP = 6;
   private static final Map<Category, MeteorGuiScreen.Panel> PANELS = new EnumMap<>(Category.class);
   private static final List<Category> ORDER = new ArrayList<>();
   private static final Set<Module> EXPANDED = new HashSet<>();
   private static final Set<ColorSetting> OPEN_COLORS = new HashSet<>();
   private static boolean loaded;
   private final long openedAt = System.currentTimeMillis();
   private final List<MeteorGuiScreen.Hit> hits = new ArrayList<>();
   private String search = "";
   private boolean searchFocused;
   private Module binding;
   private StringSetting editing;
   private MeteorGuiScreen.Drag drag;
   private String tooltip;
   private double mouseX;
   private double mouseY;
   private int sw;
   private int sh;
   private static final int BIND_H = 14;
   private String tipText;
   private List<String> tipLines;
   private long tipAt;

   public MeteorGuiScreen() {
      super(class_2561.method_43470("DIHClient"));
      load();
   }

   public static class_437 create() {
      ClickGui var0 = gui();
      if (var0 != null && var0.layout.get() != ClickGui.Layout.METEOR) {
         var0.layout.set(ClickGui.Layout.METEOR);
      }

      return new MeteorGuiScreen();
   }

   public static void onLayoutChanged() {
   }

   private static ClickGui gui() {
      return ModuleManager.of(ClickGui.class);
   }

   private static float scale() {
      ClickGui var0 = gui();
      return var0 == null ? 1.0F : var0.scale.getFloat();
   }

   private static Path stateFile() {
      try {
         return DIHClient.config().dir().resolve("meteor-gui.txt");
      } catch (Throwable var1) {
         return null;
      }
   }

   private static void load() {
      if (!loaded) {
         loaded = true;

         for (Category var3 : Category.values()) {
            PANELS.put(var3, new MeteorGuiScreen.Panel());
            ORDER.add(var3);
         }

         Path var8 = stateFile();
         if (var8 != null && Files.exists(var8)) {
            try {
               for (String var10 : Files.readAllLines(var8, StandardCharsets.UTF_8)) {
                  String[] var11 = var10.trim().split("\\s+");
                  if (var11.length >= 4) {
                     try {
                        Category var4 = Category.valueOf(var11[0]);
                        MeteorGuiScreen.Panel var5 = PANELS.get(var4);
                        var5.x = Integer.parseInt(var11[1]);
                        var5.y = Integer.parseInt(var11[2]);
                        var5.open = Boolean.parseBoolean(var11[3]);
                     } catch (Exception var6) {
                     }
                  }
               }
            } catch (Exception var7) {
               DIHClient.LOG.warn("[DIHClient] could not read meteor-gui.txt", var7);
            }
         }
      }
   }

   private static void save() {
      Path var0 = stateFile();
      if (var0 != null) {
         StringBuilder var1 = new StringBuilder();

         for (Category var3 : ORDER) {
            MeteorGuiScreen.Panel var4 = PANELS.get(var3);
            var1.append(var3.name()).append(' ').append(var4.x).append(' ').append(var4.y).append(' ').append(var4.open).append('\n');
         }

         try {
            Files.createDirectories(var0.getParent());
            Files.writeString(var0, var1.toString(), StandardCharsets.UTF_8);
         } catch (Exception var5) {
            DIHClient.LOG.warn("[DIHClient] could not write meteor-gui.txt", var5);
         }
      }
   }

   public static void resetLayout() {
      for (MeteorGuiScreen.Panel var1 : PANELS.values()) {
         var1.x = -1;
         var1.y = -1;
         var1.open = true;
         var1.scroll = var1.scrollTarget = 0.0F;
      }

      save();
   }

   private void hit(int var1, int var2, int var3, int var4, MeteorGuiScreen.ClickAction var5) {
      if (!this.lock) {
         this.hits.add(new MeteorGuiScreen.Hit(var1, var2, var3, var4, var5));
      }
   }

   private void sound() {
      ClickGui var1 = gui();
      if (var1 != null && var1.clickSound.get() && this.field_22787 != null) {
         this.field_22787.method_1483().method_4873((class_1113)class_1109.method_47978(class_3417.field_15015, var1.clickPitch.getFloat()));
      }
   }

   private static String lower(String var0) {
      return var0.toLowerCase(Locale.ROOT);
   }

   private List<Module> modulesOf(Category var1) {
      ArrayList var2 = new ArrayList();
      String var3 = lower(this.search);

      for (Module var5 : DIHClient.modules().byCategory(var1)) {
         if (!var5.isHidden() && (var3.isEmpty() || lower(var5.name()).contains(var3) || lower(var5.description()).contains(var3))) {
            var2.add(var5);
         }
      }

      return var2;
   }

   private void placeDefaults() {
      int var1 = 10;
      int var2 = 40;
      int var3 = 0;

      for (Category var7 : Category.values()) {
         MeteorGuiScreen.Panel var8 = PANELS.get(var7);
         if (var8.x < 0 || var8.y < 0) {
            if (var1 + PANEL_W > this.sw - 6) {
               var1 = 10;
               var2 += Math.max(var3, 120) + 10;
               var3 = 0;
            }

            var8.x = var1;
            var8.y = var2;
            var1 += PANEL_W + 8;
            var3 = Math.max(var3, HEADER_H + Math.min(200, DIHClient.modules().byCategory(var7).size() * ROW_H));
         }
      }
   }

   private void bringToFront(Category var1) {
      ORDER.remove(var1);
      ORDER.add(var1);
   }

   @Override
   public boolean isTyping() {
      return this.searchFocused || !this.search.isEmpty() || this.editing != null || this.binding != null;
   }

   public boolean method_25421() {
      return false;
   }

   private static final long INTRO_MS = 320L;
   private static final long STAGGER_MS = 34L;
   private static final long OUTRO_MS = 170L;
   private boolean closing;
   private long closeAt;
   private Category dragging;
   private boolean lock;

   private static boolean animOn() {
      ClickGui var0 = gui();
      return var0 == null || var0.animations.get();
   }

   private float outro(long var1) {
      return this.closing ? 1.0F - Anim.easeOutCubic((float)(var1 - this.closeAt) / (float)OUTRO_MS) : 1.0F;
   }

   private float intro(long var1, int var3) {
      return animOn() ? Anim.easeOutCubic((float)(var1 - this.openedAt - var3 * STAGGER_MS) / (float)INTRO_MS) : 1.0F;
   }

   public void method_25419() {
      if (!this.closing) {
         if (animOn()) {
            this.closing = true;
            this.closeAt = System.currentTimeMillis();
         } else {
            this.finishClose();
         }
      }
   }

   private void finishClose() {
      save();
      DIHClient.config().save();
      super.method_25419();
   }

   public void method_25420(class_332 var1, int var2, int var3, float var4) {
      ClickGui var5 = gui();
      if (var5 != null && var5.blur.get()) {
         super.method_25420(var1, var2, var3, var4);
      }

      long var6 = System.currentTimeMillis();
      float var8 = (animOn() ? Anim.easeOutCubic((float)(var6 - this.openedAt) / 300.0F) : 1.0F) * this.outro(var6);
      var1.method_25296(0, 0, this.field_22789, this.field_22790, Skin.dimTop(var8), Skin.dimBottom(var8));
   }

   public void method_25394(class_332 var1, int var2, int var3, float var4) {
      Skin.begin();

      try {
         this.renderSkinned(var1, var2, var3, var4);
      } finally {
         Gfx.setFade(1.0F);
         Skin.end();
      }
   }

   private void renderSkinned(class_332 var1, int var2, int var3, float var4) {
      if (this.closing && System.currentTimeMillis() - this.closeAt >= OUTRO_MS) {
         this.finishClose();
      } else {
         super.method_25394(var1, var2, var3, var4);
         ClickGui var5 = gui();
         if (var5 != null) {
            Anim.setEnabled(var5.animations.get());
         }

         this.hits.clear();
         this.tooltip = null;
         float var6 = scale();
         this.sw = (int)(this.field_22789 / var6);
         this.sh = (int)(this.field_22790 / var6);
         this.mouseX = var2 / var6;
         this.mouseY = var3 / var6;
         this.placeDefaults();
         var1.method_51448().pushMatrix();
         var1.method_51448().scale(var6, var6);
         this.renderTopBar(var1);

         for (Category var8 : ORDER) {
            this.renderPanel(var1, var8);
         }

         this.renderBottomBar(var1);
         this.renderTooltip(var1);
         var1.method_51448().popMatrix();
      }
   }

   private void renderTopBar(class_332 var1) {
      long var2 = System.currentTimeMillis();
      float var4 = this.intro(var2, 0) * this.outro(var2);
      Gfx.setFade(var4);
      this.lock = var4 < 0.9F;
      int var5 = 176;
      int var6 = (this.sw - var5) / 2;
      int var7 = 8 + Math.round((1.0F - var4) * -8.0F);
      float var8 = Anim.get(this, "mSearchFocus", !this.searchFocused && this.search.isEmpty() ? 0.0F : 1.0F, 14.0F);
      Gfx.softShadow(var1, var6, var7, var5, 20, Skin.radius(5), 12, 0.9F * Skin.shadow());
      Gfx.rect(var1, var6, var7, var5, 20, 5, Skin.c(-535555048));
      Gfx.outline(var1, var6, var7, var5, 20, 5, ColorUtil.blend(Skin.c(587202559), Theme.withAlpha(Theme.accent(), 0.85F), var8));
      String var9 = this.search.isEmpty() ? "Search modules…" : this.search + (this.searchFocused && System.currentTimeMillis() / 500L % 2L == 0L ? "_" : "");
      Gfx.text(var1, "⌕", var6 + 9, var7 + 6, ColorUtil.blend(Skin.c(-10788238), Theme.accent(), var8));
      Gfx.text(var1, Gfx.trim(var9, var5 - 30), var6 + 21, var7 + 6, this.search.isEmpty() ? Skin.c(-10788238) : Skin.c(-1446670));
      this.hit(var6, var7, var5, 20, (var1x, var3x, var5x) -> {
         this.searchFocused = true;
         if (var5x == 1) {
            this.search = "";
         }
      });
      if (this.binding != null) {
         String var10 = "Press a key for " + this.binding.name() + "  ·  Esc = unbind";
         int var11 = Gfx.width(var10) + 20;
         int var12 = (this.sw - var11) / 2;
         Gfx.softShadow(var1, var12, var7 + 26, var11, 18, Skin.radius(5), 10, 0.8F);
         Gfx.rect(var1, var12, var7 + 26, var11, 18, 5, ColorUtil.withAlpha(Theme.accent(), 235));
         Gfx.text(var1, var10, var12 + 10, var7 + 31, Skin.c(-233959403) | 0xFF000000);
      }

      this.lock = false;
      Gfx.setFade(1.0F);
   }

   private void renderBottomBar(class_332 var1) {
      long var2 = System.currentTimeMillis();
      float var4 = this.intro(var2, 2) * this.outro(var2);
      Gfx.setFade(var4);
      this.lock = var4 < 0.9F;
      int var5 = this.sh - 26 + Math.round((1.0F - var4) * 8.0F);
      int var6 = this.sw - 10;
      var6 = this.button(var1, var6, var5, "✎ HUD Editor", () -> {
         if (this.field_22787 != null) {
            this.field_22787.method_1507(new HudEditorScreen(this));
         }
      }, false);
      var6 = this.button(var1, var6 - 6, var5, "↺ Reset", MeteorGuiScreen::resetLayout, false);
      var6 = this.themeButton(var1, var6 - 6, var5);
      Gfx.text(var1, "L-Click: toggle  ·  R-Click: settings  ·  M-Click: bind  ·  drag headers to move", 10.0F, var5 + 7.0F, Skin.c(-10788238), 0.75F);
      this.lock = false;
      Gfx.setFade(1.0F);
   }

   private int themeButton(class_332 var1, int var2, int var3) {
      ClickGui var4 = gui();
      if (var4 == null) {
         return var2;
      } else {
         String var5 = Skin.profile().title();
         int var6 = Gfx.width(var5) + 44;
         int var7 = var2 - var6;
         boolean var8 = !this.lock && Gfx.inside(this.mouseX, this.mouseY, var7, var3, var6, 20);
         float var9 = Anim.get(this, "bTheme", var8 ? 1.0F : 0.0F, 16.0F);
         Gfx.softShadow(var1, var7, var3, var6, 20, Skin.radius(5), 10, 0.8F * Skin.shadow());
         Gfx.rect(var1, var7, var3, var6, 20, 5, ColorUtil.blend(Skin.c(-535555048), Skin.c(-14341579), var9));
         Gfx.outline(var1, var7, var3, var6, 20, 5, Skin.c(587202559));
         Gfx.rect(var1, var7 + 8, var3 + 7, 6, 6, 3, Theme.accent());
         Gfx.text(var1, var5, var7 + 20, var3 + 6, ColorUtil.blend(Skin.c(-7564380), Skin.c(-1446670), var9));
         Gfx.text(var1, "›", var7 + var6 - 11, var3 + 6, Skin.c(-10788238));
         this.hit(var7, var3, var6, 20, (var2x, var4x, var6x) -> {
            ClickGui.Look[] var7x = ClickGui.Look.values();
            int var8x = var4.look.get().ordinal() + (var6x == 1 ? -1 : 1);
            var4.look.set(var7x[(var8x + var7x.length) % var7x.length]);
            DIHClient.config().markDirty();
            this.sound();
         });
         return var7;
      }
   }

   private int button(class_332 var1, int var2, int var3, String var4, Runnable var5, boolean var6) {
      int var7 = Gfx.width(var4) + 20;
      int var8 = var2 - var7;
      boolean var9 = !this.lock && Gfx.inside(this.mouseX, this.mouseY, var8, var3, var7, 20);
      float var10 = Anim.get(this, "b" + var4, var9 ? 1.0F : 0.0F, 16.0F);
      Gfx.softShadow(var1, var8, var3, var7, 20, Skin.radius(5), 10, 0.8F * Skin.shadow());
      Gfx.rect(var1, var8, var3, var7, 20, 5, ColorUtil.blend(Skin.c(-535555048), Skin.c(-14341579), var10));
      Gfx.outline(var1, var8, var3, var7, 20, 5, Skin.c(587202559));
      Gfx.text(var1, var4, var8 + 10, var3 + 6, ColorUtil.blend(Skin.c(-7564380), Skin.c(-1446670), var10));
      this.hit(var8, var3, var7, 20, (var2x, var4x, var6x) -> {
         this.sound();
         var5.run();
      });
      return var8;
   }

   private int expandedHeightNow(Module var1) {
      float var2 = Anim.get(var1, "mExp", EXPANDED.contains(var1) ? 1.0F : 0.0F, 15.0F);
      return var2 <= 0.002F ? 0 : Math.max(1, Math.round(this.expandedHeight(var1) * var2));
   }

   private void renderPanel(class_332 var1, Category var2) {
      MeteorGuiScreen.Panel var3 = PANELS.get(var2);
      List<Module> var4 = this.modulesOf(var2);
      if (this.search.isEmpty() || !var4.isEmpty()) {
         var3.x = Math.max(0, Math.min(this.sw - PANEL_W, var3.x));
         var3.y = Math.max(0, Math.min(this.sh - HEADER_H, var3.y));
         long var5 = System.currentTimeMillis();
         int var7 = var2.ordinal();
         float var8 = this.intro(var5, var7 + 1);
         float var9 = this.outro(var5);
         float var10 = var8 * var9;
         if (!(var10 <= 0.01F)) {
            boolean var11 = var3.open || !this.search.isEmpty();
            float var12 = Anim.get(var3, "open", var11 ? 1.0F : 0.0F, 14.0F);
            float var13 = Anim.get(var3, "lift", this.drag != null && this.dragging == var2 ? 1.0F : 0.0F, 14.0F);
            int var14 = 0;

            for (Module var16 : var4) {
               var14 += ROW_H + this.expandedHeightNow(var16);
            }

            var14 += 8;
            var3.contentH = var14;
            int var17 = Math.max(40, this.sh - var3.y - HEADER_H - 38);
            int var18 = Math.round(Math.min(var14, var17) * var12);
            var3.bodyTop = var3.y + HEADER_H;
            var3.bodyH = var18;
            var3.scrollTarget = Math.max(0.0F, Math.min(var3.scrollTarget, (float)Math.max(0, var14 - var17)));
            var3.scroll = Anim.get(var3, "scroll", var3.scrollTarget, 18.0F);
            int var19 = var3.x;
            int var20 = var3.y - Math.round(var13 * 2.0F);
            int var21 = HEADER_H + var18;
            float var22 = 0.965F + 0.035F * var10;
            float var23 = (1.0F - var8) * 14.0F + (1.0F - var9) * 8.0F;
            this.lock = var10 < 0.92F;
            Gfx.setFade(var10);
            var1.method_51448().pushMatrix();
            var1.method_51448().translate(var19 + PANEL_W / 2.0F, var20 + var21 / 2.0F + var23);
            var1.method_51448().scale(var22, var22);
            var1.method_51448().translate(-(var19 + PANEL_W / 2.0F), -(var20 + var21 / 2.0F));
            Skin.Profile var24 = Skin.profile();
            int var25 = Theme.accentAt(var7 * 0.1);
            Gfx.softShadow(var1, var19, var20, PANEL_W, var21, Skin.radius(6), 14 + Math.round(var13 * 6.0F), (0.95F + var13 * 0.6F) * var24.shadow());
            int var26 = Skin.c(-300937196) & 16777215 | Skin.panelAlpha() << 24;
            Gfx.rect(var1, var19, var20, PANEL_W, var21, 6, var26);
            boolean var27 = var18 <= 1;
            float var28 = Anim.get(var3, "hdr", !this.lock && Gfx.inside(this.mouseX, this.mouseY, var19, var20, PANEL_W, HEADER_H) ? 1.0F : 0.0F, 16.0F);
            int var29 = ColorUtil.blend(Skin.c(-15592422), Skin.c(-15328992), var28);
            if (var24.header() == Skin.Header.TINT) {
               var29 = ColorUtil.blend(var29, ColorUtil.withAlpha(var25, 255), 0.13F + 0.06F * var28);
            }

            if (var27) {
               Gfx.rect(var1, var19, var20, PANEL_W, HEADER_H, 6, var29);
            } else {
               Gfx.rectTop(var1, var19, var20, PANEL_W, HEADER_H, Skin.radius(6), var29);
            }

            if (var24.header() != Skin.Header.DOT) {
               Gfx.hFade(var1, var19 + 6, var20 + HEADER_H - 1, PANEL_W - 12, 1, ColorUtil.withAlpha(var25, 200), ColorUtil.withAlpha(Theme.accentAt(var7 * 0.1 + 0.4), 0), 14);
            }

            int var30 = var19 + 10;
            if (var24.header() == Skin.Header.DOT) {
               Gfx.rect(var1, var19 + 9, var20 + 9, 5, 5, 2, var25);
               var30 = var19 + 19;
            }

            Gfx.text(var1, var2.title, var30, var20 + 7, Skin.c(-1446670));
            long var31 = 0L;

            for (Module var34 : DIHClient.modules().all()) {
               if (var34.category() == var2 && !var34.isHidden() && var34.isEnabled() && var34.isToggleable()) {
                  var31++;
               }
            }

            int var45 = var19 + PANEL_W - 20;
            Gfx.text(var1, var11 ? "▾" : "▸", var45 + 4, var20 + 7, Skin.c(-10788238));
            if (var31 > 0L) {
               String var35 = Long.toString(var31);
               int var36 = Gfx.width(var35) + 9;
               var45 -= var36 + 2;
               Gfx.rect(var1, var45, var20 + 5, var36, 12, 5, ColorUtil.withAlpha(var25, 56));
               Gfx.text(var1, var35, var45 + 4, var20 + 7, ColorUtil.blend(var25, Skin.c(-1446670), 0.45F));
            }

            this.hit(var19, var3.y, PANEL_W, HEADER_H, (var4x, var6x, var8x) -> {
               this.bringToFront(var2);
               if (var8x == 1 || var8x == 0 && var4x >= var3.x + PANEL_W - 22) {
                  var3.open = !var3.open;
                  this.sound();
               } else if (var8x == 0) {
                  int var9x = (int)var4x - var3.x;
                  int var10x = (int)var6x - var3.y;
                  this.dragging = var2;
                  this.drag = (var3xx, var5x) -> {
                     var3.x = (int)var3xx - var9x;
                     var3.y = (int)var5x - var10x;
                  };
               }
            });
            if (!var27) {
               int var46 = var20 + HEADER_H;
               int var47 = var46 + var18;
               var1.method_44379(var19, var46, var19 + PANEL_W, var47);
               int var37 = var46 + 4 - (int)var3.scroll;
               boolean var38 = this.mouseY >= var46 && this.mouseY < var47;

               for (int var39 = 0; var39 < var4.size(); var39++) {
                  Module var40 = (Module)var4.get(var39);
                  int var41 = this.expandedHeightNow(var40);
                  if (var37 + ROW_H + var41 >= var46 && var37 <= var47) {
                     this.renderModuleRow(var1, var40, var19, var37, var46, var47, var38, var7 + var39 * 0.05, var24);
                     if (var41 > 0) {
                        this.renderExpanded(var1, var40, var19, var37 + ROW_H, var41, var46, var47, var38);
                     }
                  }

                  var37 += ROW_H + var41;
               }

               var1.method_44380();
               if (var14 > var18 && var18 > 10) {
                  int var42 = Math.max(12, var18 * var18 / var14);
                  int var43 = var46 + (int)((var18 - var42) * (var3.scroll / Math.max(1.0F, (float)(var14 - var18))));
                  Gfx.rect(var1, var19 + PANEL_W - 4, var43 + 2, 2, var42 - 4, 1, Skin.c(1442840575));
               }
            }

            Gfx.outline(var1, var19, var20, PANEL_W, var21, 6, Skin.c(587202559));
            var1.method_51448().popMatrix();
            this.lock = false;
            Gfx.setFade(1.0F);
         }
      }
   }

   private void renderModuleRow(class_332 var1, Module var2, int var3, int var4, int var5, int var6, boolean var7, double var8, Skin.Profile var9) {
      int var10 = var3 + INSET;
      int var11 = PANEL_W - INSET * 2;
      boolean var12 = !this.lock && var7 && Gfx.inside(this.mouseX, this.mouseY, var3, var4, PANEL_W, ROW_H);
      boolean var13 = var2.isEnabled() && var2.isToggleable();
      float var14 = Anim.get(var2, "mHover", var12 ? 1.0F : 0.0F, 16.0F);
      float var15 = Anim.get(var2, "mOn", var13 ? 1.0F : 0.0F, 12.0F);
      int var16 = Theme.accentAt(var8);
      if (var14 > 0.01F) {
         Gfx.rect(var1, var10, var4 + 1, var11, ROW_H - 2, 4, Theme.withAlpha(Skin.c(419430399), var14));
      }

      if (var15 > 0.01F) {
         Gfx.rect(var1, var10, var4 + 1, var11, ROW_H - 2, 4, Theme.withAlpha(ColorUtil.withAlpha(var16, 52), var15));
         if (var9.rows() == Skin.Rows.BAR) {
            Gfx.rect(var1, var10 + 2, var4 + 4, 2, ROW_H - 8, 1, Theme.withAlpha(var16, var15));
         } else {
            Gfx.rect(var1, var10 + var11 - 6, var4 + 6, 4, 4, 2, Theme.withAlpha(var16, var15));
         }
      }

      int var17 = ColorUtil.blend(ColorUtil.blend(Skin.c(-7564380), Skin.c(-1446670), var14), Skin.c(-1446670), var15);
      String var18 = this.binding == var2 ? "..." : (var2.keybind() >= 0 ? KeyUtil.keyName(var2.keybind()) : null);
      int var19 = var10 + var11 - 8;
      boolean var20 = EXPANDED.contains(var2);
      if (!var13 || var9.rows() == Skin.Rows.BAR) {
         Gfx.text(var1, var20 ? "▾" : "▸", var19 - 4, var4 + 4, var20 ? var16 : Skin.c(-10788238));
      } else {
         Gfx.text(var1, var20 ? "▾" : "▸", var19 - 10, var4 + 4, var20 ? var16 : Skin.c(-10788238));
         var19 -= 6;
      }

      var19 -= 8;
      if (var18 != null) {
         int var21 = (int)(Gfx.width(var18) * 0.7F) + 2;
         var19 -= var21;
         Gfx.text(var1, var18, var19, var4 + 5.5F, this.binding == var2 ? var16 : Skin.c(-10788238), 0.7F);
         var19 -= 3;
      }

      int var22 = var10 + (var9.rows() == Skin.Rows.BAR ? 8 : 7);
      Gfx.text(var1, Gfx.trim(var2.name(), var19 - var22 - 2), var22, var4 + 4, var17);
      if (var12) {
         this.tooltip = var2.description();
      }

      int var23 = Math.max(var4, var5);
      int var24 = Math.min(var4 + ROW_H, var6) - var23;
      if (var24 > 0) {
         this.hit(var3, var23, PANEL_W, var24, (var2x, var4x, var6x) -> {
            if (var6x == 0) {
               if (var2.isToggleable()) {
                  var2.toggle();
               } else {
                  this.toggleExpanded(var2);
               }

               this.sound();
            } else if (var6x == 1) {
               this.toggleExpanded(var2);
               this.sound();
            } else if (var6x == 2) {
               this.binding = var2;
            }
         });
      }
   }

   private void toggleExpanded(Module var1) {
      if (!EXPANDED.remove(var1)) {
         EXPANDED.add(var1);
      }

      this.editing = null;
   }

   private int expandedHeight(Module var1) {
      int var2 = 20;

      for (Setting var4 : var1.settings()) {
         if (var4.isVisible()) {
            var2 += this.settingHeight(var4);
         }
      }

      return var2 + 6;
   }

   private int settingHeight(Setting<?> var1) {
      if (var1 instanceof DoubleSetting || var1 instanceof IntSetting) {
         return 21;
      } else if (var1 instanceof StringSetting) {
         return 26;
      } else if (var1 instanceof ColorSetting var2) {
         return OPEN_COLORS.contains(var2) ? 85 : 13;
      } else {
         return var1 instanceof ActionSetting ? 16 : 13;
      }
   }

   private void renderExpanded(class_332 var1, Module var2, int var3, int var4, int var5, int var6, int var7, boolean var8) {
      int var9 = var3 + INSET;
      int var10 = PANEL_W - INSET * 2;
      int var11 = var9 + 6;
      int var12 = var10 - 12;
      int var13 = Math.min(var7, var4 + var5);
      Gfx.rect(var1, var9, var4, var10, var5, 5, Skin.c(1711276032));
      var1.method_44379(var9, Math.max(var4, var6), var9 + var10, Math.min(var4 + var5, var7));
      String var14 = this.binding == var2 ? "press a key" : "Bind: " + KeyUtil.keyName(var2.keybind());
      int var15 = (int)(Gfx.width(var14) * 0.8F) + 10;
      int var16 = var4 + 4;
      boolean var17 = !this.lock && var8 && Gfx.inside(this.mouseX, this.mouseY, var11, var16, var15, 12);
      Gfx.rect(
         var1, var11, var16, var15, 12, 5, this.binding == var2 ? ColorUtil.withAlpha(Theme.accent(), 160) : (var17 ? Skin.c(-14341579) : Skin.c(-14868182))
      );
      Gfx.text(var1, var14, var11 + 5.0F, var16 + 3.5F, Skin.c(-1446670), 0.8F);
      this.addClippedHit(var11, var16, var15, 12, var6, var13, (var2x, var4x, var6x) -> this.binding = var6x == 1 ? null : var2);
      if (var2.isToggleable()) {
         String var18 = var2.showToggleNotification() ? "♪ Toast" : "♪ Off";
         int var19 = (int)(Gfx.width(var18) * 0.8F) + 10;
         int var20 = var11 + var15 + 4;
         boolean var21 = !this.lock && var8 && Gfx.inside(this.mouseX, this.mouseY, var20, var16, var19, 12);
         Gfx.rect(var1, var20, var16, var19, 12, 5, var21 ? Skin.c(-14341579) : Skin.c(-14868182));
         Gfx.text(var1, var18, var20 + 5.0F, var16 + 3.5F, var2.showToggleNotification() ? Skin.c(-1446670) : Skin.c(-10788238), 0.8F);
         this.addClippedHit(var20, var16, var19, 12, var6, var13, (var2x, var4x, var6x) -> {
            var2.setShowToggleNotification(!var2.showToggleNotification());
            DIHClient.config().markDirty();
            this.sound();
         });
      }

      int var22 = var4 + 20;

      for (Setting var24 : var2.settings()) {
         if (var24.isVisible()) {
            int var25 = this.settingHeight(var24);
            if (var22 + var25 >= var6 && var22 <= var13) {
               this.renderSetting(var1, var24, var11, var22, var12, var6, var13, var8);
            }

            var22 += var25;
         }
      }

      var1.method_44380();
   }

   private void addClippedHit(int var1, int var2, int var3, int var4, int var5, int var6, MeteorGuiScreen.ClickAction var7) {
      int var8 = Math.max(var2, var5);
      int var9 = Math.min(var2 + var4, var6) - var8;
      if (var9 > 0) {
         this.hit(var1, var8, var3, var9, var7);
      }
   }

   private void addClippedDrag(int var1, int var2, int var3, int var4, int var5, int var6, MeteorGuiScreen.ClickAction var7, MeteorGuiScreen.Drag var8) {
      this.addClippedHit(var1, var2, var3, var4, var5, var6, (var3x, var5x, var7x) -> {
         if (var7 != null) {
            var7.click(var3x, var5x, var7x);
         }

         if (var7x == 0 && var8 != null) {
            this.drag = var8;
            var8.drag(var3x, var5x);
         }
      });
   }

   private void renderSetting(class_332 var1, Setting<?> var2, int var3, int var4, int var5, int var6, int var7, boolean var8) {
      int var9 = Theme.accent();
      boolean var10 = var8 && Gfx.inside(this.mouseX, this.mouseY, var3, var4, var5, this.settingHeight(var2));
      int var11 = var10 ? Skin.c(-1446670) : ColorUtil.blend(Skin.c(-7564380), Skin.c(-1446670), 0.35F);
      String var12 = var2.name();
      if (var10 && var2.description() != null && !var2.description().isEmpty()) {
         this.tooltip = var2.description();
      }

      if (var2 instanceof BoolSetting var13) {
         Gfx.text(var1, Gfx.trim(var12, (int)((var5 - 16) / 0.85F)), var3, var4 + 3.0F, var11, 0.85F);
         int var19 = var3 + var5 - 16;
         float var20 = Anim.get(var13, "mChk", var13.get() ? 1.0F : 0.0F, 16.0F);
         Gfx.rect(var1, var19, var4 + 2, 16, 9, 4, ColorUtil.blend(Skin.c(-13881027), var9, var20));
         Gfx.rect(var1, var19 + 1 + Math.round(var20 * 7.0F), var4 + 3, 7, 7, 3, Skin.c(-1446670));
         this.addClippedHit(var3, var4, var5, 12, var6, var7, (var2x, var4x, var6x) -> {
            if (var6x == 1) {
               var13.reset();
            } else {
               var13.toggle();
            }

            DIHClient.config().markDirty();
            this.sound();
         });
      } else if (var2 instanceof DoubleSetting || var2 instanceof IntSetting) {
         String var29 = var2.displayValue();
         float var34 = Gfx.width(var29) * 0.85F;
         Gfx.text(var1, Gfx.trim(var12, (int)((var5 - var34 - 4.0F) / 0.85F)), var3, var4 + 2.0F, var11, 0.85F);
         Gfx.text(var1, var29, var3 + var5 - var34, var4 + 2.0F, var9, 0.85F);
         double var37 = var2 instanceof DoubleSetting var23 ? var23.fraction() : ((IntSetting)var2).fraction();
         float var38 = Anim.get(var2, "mSlider", (float)var37, 20.0F);
         Gfx.slider(var1, var3, var4 + 13, var5, var38, var9, var10);
         this.addClippedDrag(var3 - 2, var4 + 9, var5 + 4, 11, var6, var7, (var1x, var3x, var5x) -> {
            if (var5x == 1) {
               var2.reset();
               DIHClient.config().markDirty();
            }
         }, (var3x, var5x) -> {
            double var7x = (var3x - var3) / var5;
            if (var2 instanceof DoubleSetting var9x) {
               var9x.setFraction(var7x);
            } else {
               ((IntSetting)var2).setFraction(var7x);
            }

            DIHClient.config().markDirty();
         });
      } else if (var2 instanceof EnumSetting var14) {
         String var24 = var14.displayValue();
         float var30 = Gfx.width(var24) * 0.85F;
         Gfx.text(var1, Gfx.trim(var12, (int)((var5 - var30 - 10.0F) / 0.85F)), var3, var4 + 3.0F, var11, 0.85F);
         Gfx.text(var1, var24, var3 + var5 - var30, var4 + 3.0F, var9, 0.85F);
         this.addClippedHit(var3, var4, var5, 12, var6, var7, (var2x, var4x, var6x) -> {
            var14.cycle(var6x != 1);
            DIHClient.config().markDirty();
            this.sound();
         });
      } else if (var2 instanceof ColorSetting var15) {
         Gfx.text(var1, Gfx.trim(var12, (int)((var5 - 22) / 0.85F)), var3, var4 + 3.0F, var11, 0.85F);
         int var25 = var3 + var5 - 18;
         checker(var1, var25, var4 + 2, 18, 9);
         Gfx.rect(var1, var25, var4 + 2, 18, 9, 2, var15.get());
         Gfx.outline(var1, var25, var4 + 2, 18, 9, 2, Skin.c(1090519039));
         this.addClippedHit(var3, var4, var5, 12, var6, var7, (var2x, var4x, var6x) -> {
            if (var6x == 1) {
               var15.reset();
            } else if (!OPEN_COLORS.remove(var15)) {
               OPEN_COLORS.add(var15);
            }

            DIHClient.config().markDirty();
            this.sound();
         });
         if (OPEN_COLORS.contains(var15)) {
            this.renderColorPicker(var1, var15, var3, var4 + 14, var5, var6, var7);
         }
      } else if (var2 instanceof IdListSetting var16) {
         String var26 = "Edit · " + var16.get().size();
         int var31 = (int)(Gfx.width(var26) * 0.85F) + 8;
         Gfx.text(var1, Gfx.trim(var12, (int)((var5 - var31 - 4) / 0.85F)), var3, var4 + 3.0F, var11, 0.85F);
         boolean var21 = var8 && Gfx.inside(this.mouseX, this.mouseY, var3 + var5 - var31, var4 + 1, var31, 11);
         Gfx.rect(var1, var3 + var5 - var31, var4 + 1, var31, 11, 4, ColorUtil.withAlpha(var9, var21 ? 200 : 110));
         Gfx.text(var1, var26, var3 + var5 - var31 + 4.0F, var4 + 3.5F, Skin.onCard(), 0.85F);
         this.addClippedHit(var3 + var5 - var31, var4 + 1, var31, 11, var6, var7, (var2x, var4x, var6x) -> {
            this.sound();
            if (this.field_22787 != null) {
               this.field_22787.method_1507(new TargetSelectorScreen(this, var16));
            }
         });
      } else if (var2 instanceof StringSetting var17) {
         Gfx.text(var1, Gfx.trim(var12, (int)(var5 / 0.85F)), var3, var4 + 1.0F, var11, 0.85F);
         boolean var27 = this.editing == var17;
         Gfx.rect(var1, var3, var4 + 11, var5, 13, 4, Skin.c(-15328992));
         if (var27) {
            Gfx.outline(var1, var3, var4 + 11, var5, 13, 4, var9);
         }

         String var32 = var17.get();
         String var35 = var27 ? var32 + (System.currentTimeMillis() / 500L % 2L == 0L ? "_" : "") : (var32.isEmpty() ? "empty" : var32);

         while (Gfx.width(var35) * 0.85F > var5 - 8 && var35.length() > 1) {
            var35 = var35.substring(1);
         }

         Gfx.text(var1, var35, var3 + 4.0F, var4 + 14.0F, var32.isEmpty() && !var27 ? Skin.c(-10788238) : Skin.c(-1446670), 0.85F);
         this.addClippedHit(var3, var4 + 11, var5, 13, var6, var7, (var2x, var4x, var6x) -> {
            if (var6x == 1) {
               var17.reset();
               this.editing = null;
            } else {
               this.editing = var17;
            }

            DIHClient.config().markDirty();
         });
      } else if (var2 instanceof ActionSetting var18) {
         boolean var28 = var8 && Gfx.inside(this.mouseX, this.mouseY, var3, var4 + 1, var5, 13);
         float var33 = Anim.get(var18, "mBtn", var28 ? 1.0F : 0.0F, 16.0F);
         Gfx.rect(var1, var3, var4 + 1, var5, 13, 4, ColorUtil.blend(Skin.c(-14868182), ColorUtil.withAlpha(var9, 210), var33));
         float var36 = Gfx.width(var12) * 0.85F;
         Gfx.text(var1, var12, var3 + (var5 - var36) / 2.0F, var4 + 4.0F, Skin.c(-1446670), 0.85F);
         this.addClippedHit(var3, var4 + 1, var5, 13, var6, var7, (var2x, var4x, var6x) -> {
            this.sound();
            var18.run();
         });
      }
   }

   private static void checker(class_332 var0, int var1, int var2, int var3, int var4) {
      for (byte var5 = 0; var5 < var3; var5 += 3) {
         for (byte var6 = 0; var6 < var4; var6 += 3) {
            var0.method_25294(
               var1 + var5, var2 + var6, var1 + Math.min(var3, var5 + 3), var2 + Math.min(var4, var6 + 3), (var5 + var6) / 3 % 2 == 0 ? -12961205 : -14277069
            );
         }
      }
   }

   private void renderColorPicker(class_332 var1, ColorSetting var2, int var3, int var4, int var5, int var6, int var7) {
      int var8 = var2.get();
      float[] var9 = Color.RGBtoHSB(var8 >> 16 & 0xFF, var8 >> 8 & 0xFF, var8 & 0xFF, null);
      int var10 = var8 >>> 24 & 0xFF;
      int var11 = var5 - 14;
      byte var12 = 46;
      int var13 = 0xFF000000 | Color.HSBtoRGB(var9[0], 1.0F, 1.0F);

      for (byte var14 = 0; var14 < var11; var14 += 2) {
         int var15 = ColorUtil.blend(-1, var13, (float)var14 / (var11 - 1));
         var1.method_25296(var3 + var14, var4, var3 + Math.min(var11, var14 + 2), var4 + var12, var15, -16777216);
      }

      int var22 = var3 + (int)(var9[1] * (var11 - 1));
      int var23 = var4 + (int)((1.0F - var9[2]) * (var12 - 1));
      Gfx.outline(var1, var22 - 2, var23 - 2, 5, 5, 1, -1);
      int var16 = var3 + var11 + 4;

      for (int var17 = 0; var17 < 6; var17++) {
         int var18 = 0xFF000000 | Color.HSBtoRGB(var17 / 6.0F, 1.0F, 1.0F);
         int var19 = 0xFF000000 | Color.HSBtoRGB((var17 + 1) / 6.0F, 1.0F, 1.0F);
         var1.method_25296(var16, var4 + var17 * var12 / 6, var16 + 10, var4 + (var17 + 1) * var12 / 6, var18, var19);
      }

      int var24 = var4 + (int)(var9[0] * (var12 - 1));
      var1.method_25294(var16 - 1, var24, var16 + 11, var24 + 1, -1);
      int var25 = var4 + var12 + 4;
      checker(var1, var3, var25, var5, 7);
      Gfx.hGradient(var1, var3, var25, var5, 7, var8 & 16777215, var8 | 0xFF000000);
      int var26 = var3 + (int)(var10 / 255.0F * (var5 - 1));
      var1.method_25294(var26, var25 - 1, var26 + 1, var25 + 8, -1);
      String var20 = var2.displayValue() + String.format(Locale.ROOT, "  %d%%", Math.round(var10 / 2.55F));
      Gfx.text(var1, var20, var3, var25 + 10.0F, Skin.c(-10788238), 0.7F);
      float var21 = var9[0];
      this.addClippedDrag(var3, var4, var11, var12, var6, var7, null, (var6x, var8x) -> {
         float var10x = (float)Math.max(0.0, Math.min(1.0, (var6x - var3) / (var11 - 1)));
         float var11x = (float)Math.max(0.0, Math.min(1.0, 1.0 - (var8x - var4) / (var12 - 1)));
         float[] var12x = Color.RGBtoHSB(var2.get() >> 16 & 0xFF, var2.get() >> 8 & 0xFF, var2.get() & 0xFF, null);
         float var13x = var12x[1] > 0.001F && var12x[2] > 0.001F ? var12x[0] : var21;
         var2.set(var2.get() & 0xFF000000 | Color.HSBtoRGB(var13x, var10x, var11x) & 16777215);
         DIHClient.config().markDirty();
      });
      this.addClippedDrag(var16 - 1, var4, 12, var12, var6, var7, null, (var3x, var5x) -> {
         float var7x = (float)Math.max(0.0, Math.min(0.999, (var5x - var4) / (var12 - 1)));
         float[] var8x = Color.RGBtoHSB(var2.get() >> 16 & 0xFF, var2.get() >> 8 & 0xFF, var2.get() & 0xFF, null);
         var2.set(var2.get() & 0xFF000000 | Color.HSBtoRGB(var7x, Math.max(var8x[1], 0.01F), Math.max(var8x[2], 0.01F)) & 16777215);
         DIHClient.config().markDirty();
      });
      this.addClippedDrag(var3, var25 - 2, var5, 11, var6, var7, null, (var3x, var5x) -> {
         var2.setChannel(3, (int)Math.round(Math.max(0.0, Math.min(1.0, (var3x - var3) / (var5 - 1))) * 255.0));
         DIHClient.config().markDirty();
      });
   }

   private void renderTooltip(class_332 var1) {
      boolean var2 = this.tooltip != null && !this.tooltip.isEmpty() && this.drag == null && !this.closing;
      if (var2) {
         long var3 = System.currentTimeMillis();
         if (this.tipLines == null || !this.tooltip.equals(this.tipText) || var3 - this.tipAt > 2000L) {
            this.tipText = this.tooltip;
            this.tipLines = wrap(this.tooltip, 170);
            this.tipAt = var3;
         }
      }

      float var14 = Anim.get(this, "tipFade", var2 ? 1.0F : 0.0F, 18.0F);
      if (this.tipLines != null && var14 > 0.02F) {
         List<String> var4 = this.tipLines;
         int var5 = 0;

         for (String var7 : var4) {
            var5 = Math.max(var5, Gfx.width(var7));
         }

         var5 += 14;
         int var13 = var4.size() * 10 + 10;
         int var15 = (int)this.mouseX + 12;
         int var8 = (int)this.mouseY + 12 + Math.round((1.0F - var14) * 4.0F);
         if (var15 + var5 > this.sw - 2) {
            var15 = (int)this.mouseX - var5 - 8;
         }

         if (var8 + var13 > this.sh - 2) {
            var8 = (int)this.mouseY - var13 - 6;
         }

         Gfx.setFade(var14);
         Gfx.softShadow(var1, var15, var8, var5, var13, Skin.radius(4), 10, Skin.shadow());
         Gfx.rect(var1, var15, var8, var5, var13, 4, Skin.c(-267382764));
         Gfx.outline(var1, var15, var8, var5, var13, 4, ColorUtil.withAlpha(Theme.accent(), 120));
         int var9 = var8 + 5;

         for (String var11 : var4) {
            Gfx.text(var1, var11, var15 + 7, var9, Skin.c(-1446670));
            var9 += 10;
         }

         Gfx.setFade(1.0F);
      }
   }

   private static List<String> wrap(String var0, int var1) {
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

   private MeteorGuiScreen.Hit hitAt(double var1, double var3) {
      for (int var5 = this.hits.size() - 1; var5 >= 0; var5--) {
         if (this.hits.get(var5).contains(var1, var3)) {
            return this.hits.get(var5);
         }
      }

      return null;
   }

   public boolean method_25402(class_11909 var1, boolean var2) {
      if (this.closing) {
         return true;
      }

      float var3 = scale();
      double var4 = var1.comp_4798() / var3;
      double var6 = var1.comp_4799() / var3;
      int var8 = var1.method_74245();
      if (this.binding != null) {
         this.binding = null;
         return true;
      } else {
         MeteorGuiScreen.Hit var9 = this.hitAt(var4, var6);
         if (var9 == null || this.editing == null) {
            this.editing = null;
         }

         this.searchFocused = false;
         if (var9 != null) {
            var9.action().click(var4, var6, var8);
         }

         return true;
      }
   }

   public boolean method_25403(class_11909 var1, double var2, double var4) {
      if (this.drag != null) {
         float var6 = scale();
         this.drag.drag(var1.comp_4798() / var6, var1.comp_4799() / var6);
         return true;
      } else {
         return super.method_25403(var1, var2, var4);
      }
   }

   public boolean method_25406(class_11909 var1) {
      if (this.drag != null) {
         DIHClient.config().markDirty();
         save();
      }

      this.drag = null;
      this.dragging = null;
      return super.method_25406(var1);
   }

   public boolean method_25401(double var1, double var3, double var5, double var7) {
      float var9 = scale();
      double var10 = var1 / var9;
      double var12 = var3 / var9;

      for (int var14 = ORDER.size() - 1; var14 >= 0; var14--) {
         MeteorGuiScreen.Panel var15 = PANELS.get(ORDER.get(var14));
         if (var10 >= var15.x && var10 < var15.x + PANEL_W && var12 >= var15.y && var12 < var15.bodyTop + var15.bodyH) {
            var15.scrollTarget -= (float)(var7 * 20.0);
            return true;
         }
      }

      return true;
   }

   public boolean method_25404(class_11908 var1) {
      if (this.closing) {
         return true;
      }

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
      if (this.closing) {
         return true;
      }

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
            }

            return true;
         }
      }
   }

   @FunctionalInterface
   private interface ClickAction {
      void click(double var1, double var3, int var5);
   }

   @FunctionalInterface
   private interface Drag {
      void drag(double var1, double var3);
   }

   private record Hit(int x, int y, int w, int h, MeteorGuiScreen.ClickAction action) {
      boolean contains(double var1, double var3) {
         return var1 >= this.x && var1 < this.x + this.w && var3 >= this.y && var3 < this.y + this.h;
      }
   }

   private static final class Panel {
      int x = -1;
      int y = -1;
      boolean open = true;
      float scroll;
      float scrollTarget;
      int bodyTop;
      int bodyH;
      int contentH;
   }
}
