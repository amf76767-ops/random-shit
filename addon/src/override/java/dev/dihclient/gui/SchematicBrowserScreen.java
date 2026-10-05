package dev.dihclient.gui;

import dev.dihclient.autobuild.BuildPlan;
import dev.dihclient.autobuild.BuildRuntime;
import dev.dihclient.autobuild.Schematic;
import dev.dihclient.autobuild.SchematicLoader;
import dev.dihclient.hud.HudManager;
import dev.dihclient.modules.world.AutoBuild;
import dev.dihclient.render.Gfx;
import dev.dihclient.setting.ActionSetting;
import dev.dihclient.setting.BoolSetting;
import dev.dihclient.setting.DoubleSetting;
import dev.dihclient.setting.EnumSetting;
import dev.dihclient.setting.IntSetting;
import dev.dihclient.setting.Setting;
import dev.dihclient.util.ColorUtil;
import dev.dihclient.util.InvUtil;
import dev.dihclient.util.Notifications;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import net.minecraft.class_11905;
import net.minecraft.class_11908;
import net.minecraft.class_11909;
import net.minecraft.class_156;
import net.minecraft.class_1792;
import net.minecraft.class_1799;
import net.minecraft.class_1935;
import net.minecraft.class_2338;
import net.minecraft.class_2415;
import net.minecraft.class_2470;
import net.minecraft.class_2561;
import net.minecraft.class_310;
import net.minecraft.class_332;
import net.minecraft.class_437;

public class SchematicBrowserScreen extends class_437 {
   private final class_437 parent;
   private final AutoBuild module;
   private final List<Path> allFiles = new ArrayList<>();
   private final List<Path> files = new ArrayList<>();
   private String search = "";
   private Path selectedFile;
   private int fileScroll;
   private int matScroll;
   private Schematic schematic;
   private BuildPlan plan;
   private class_2470 rotation = class_2470.field_11467;
   private class_2415 mirror = class_2415.field_11302;
   private String error;
   private final Map<class_1792, Integer> materials = new LinkedHashMap<>();
   private SchematicBrowserScreen.Tab tab = SchematicBrowserScreen.Tab.MATERIALS;
   private int viewLayer;
   private int[][] layerColors;
   private final List<int[]> buttons = new ArrayList<>();
   private int optScroll;
   private final List<Setting<?>> curated = new ArrayList<>();
   private final List<Setting<?>> quick = new ArrayList<>();
   private static final String[] OPTION_NAMES = new String[]{
      "Source", "Mode", "Order", "Walk", "Smart Path", "Pillar Up", "Supports", "Remove Supports", "Escape Water", "Human Rotations", "Predict",
      "Fix Wrong Blocks", "Clear Area", "Adjust States", "Restock From Chests", "Hotbar Refill", "Creative Stacks", "AutoBuy (/ah)", "Sneak Place", "Reach",
      "Rotate Speed", "Randomness", "Blocks/Tick", "Delay", "Max Fall", "Max Attempts", "From Layer", "To Layer", "Pause Near Players",
      "Pause On Damage", "Finish Sound"
   };
   private static final String[][] QUICK_NAMES = new String[][]{
      {"Walk", "WALK"}, {"Smart Path", "SMART PATH"}, {"Pillar Up", "PILLAR"}, {"Human Rotations", "HUMAN"}, {"Restock From Chests", "RESTOCK"},
      {"Fix Wrong Blocks", "FIX WRONG"}, {"Clear Area", "CLEAR AREA"}, {"Plan First", "PLAN FIRST"}
   };

   public SchematicBrowserScreen(class_437 var1, AutoBuild var2) {
      super(class_2561.method_43470("AutoBuild"));
      this.parent = var1;
      this.module = var2;
      this.refresh();
      this.collectOptions();
   }

   public boolean method_25421() {
      return false;
   }

   private void collectOptions() {
      for (String name : OPTION_NAMES) {
         Setting<?> found = this.find(name);
         if (found != null) {
            this.curated.add(found);
         }
      }

      for (String[] q : QUICK_NAMES) {
         Setting<?> found = this.find(q[0]);
         if (found != null) {
            this.quick.add(found);
         }
      }
   }

   private List<Setting<?>> currentOptions() {
      Setting<?> source = this.find("Source");
      List<Setting<?>> out = new ArrayList<>();
      if (source != null) {
         out.add(source);
      }

      for (Setting<?> setting : this.module.settings()) {
         if (setting != source && setting.isVisible()) {
            out.add(setting);
         }
      }

      return out;
   }

   public static void openOptions(class_437 parent, AutoBuild module) {
      SchematicBrowserScreen screen = new SchematicBrowserScreen(parent, module);
      screen.tab = SchematicBrowserScreen.Tab.OPTIONS;
      class_310.method_1551().method_1507(screen);
   }

   private Setting<?> find(String name) {
      for (Setting<?> setting : this.module.settings()) {
         if (setting.name().equals(name)) {
            return setting;
         }
      }

      return null;
   }

   private String quickLabel(Setting<?> setting) {
      for (String[] q : QUICK_NAMES) {
         if (q[0].equals(setting.name())) {
            return q[1];
         }
      }

      return setting.name();
   }

   private void refresh() {
      this.allFiles.clear();
      this.allFiles.addAll(SchematicLoader.list(AutoBuild.schematicDir()));
      this.applySearch();
      if (this.selectedFile == null && !this.files.isEmpty()) {
         this.select(this.files.get(0));
      }
   }

   private void applySearch() {
      this.files.clear();
      String var1 = this.search.toLowerCase(Locale.ROOT);

      for (Path var3 : this.allFiles) {
         if (var1.isEmpty() || var3.getFileName().toString().toLowerCase(Locale.ROOT).contains(var1)) {
            this.files.add(var3);
         }
      }

      this.fileScroll = 0;
   }

   private void select(Path var1) {
      this.selectedFile = var1;
      this.schematic = null;
      this.plan = null;
      this.error = null;
      this.rotation = class_2470.field_11467;
      this.mirror = class_2415.field_11302;
      this.viewLayer = 0;

      try {
         this.schematic = SchematicLoader.load(var1);
         this.rebuildPlan();
      } catch (Exception var3) {
         this.error = var3.getMessage() == null ? var3.getClass().getSimpleName() : var3.getMessage();
      }
   }

   private void rebuildPlan() {
      this.plan = new BuildPlan(this.schematic, class_2338.field_10980, this.rotation, this.mirror);
      this.materials.clear();
      this.plan
         .materials()
         .entrySet()
         .stream()
         .sorted((var0, var1) -> Integer.compare(var1.getValue(), var0.getValue()))
         .forEach(var1 -> this.materials.put(var1.getKey(), var1.getValue()));
      this.matScroll = 0;
      this.viewLayer = Math.min(this.viewLayer, this.plan.sizeY - 1);
      this.buildLayer();
   }

   private void buildLayer() {
      if (this.plan != null) {
         this.layerColors = new int[this.plan.sizeX][this.plan.sizeZ];

         for (BuildPlan.Planned var2 : this.plan.blocks) {
            if (var2.layer() == this.viewLayer) {
               this.layerColors[var2.pos().method_10263()][var2.pos().method_10260()] = 0xFF000000 | var2.state().method_26204().method_26403().field_16011;
            }
         }
      }
   }

   private void button(class_332 var1, int var2, int var3, int var4, int var5, int var6, String var7, boolean var8, int var9, int var10) {
      int var11 = HudManager.accent();
      boolean var12 = Gfx.inside(var9, var10, var3, var4, var5, var6);
      Gfx.round(var1, var3, var4, var5, var6, var8 ? (var12 ? var11 : ColorUtil.darker(var11, 0.8F)) : (var12 ? -13354422 : -14868182));
      Gfx.textCentered(var1, var7, var3 + var5 / 2, var4 + (var6 - 8) / 2, -1);
      this.buttons.add(new int[]{var3, var4, var5, var6, var2});
   }

   public void method_25394(class_332 var1, int var2, int var3, float var4) {
      super.method_25394(var1, var2, var3, var4);
      this.buttons.clear();
      int var5 = HudManager.accent();
      byte var6 = 8;
      byte var7 = 28;
      int var8 = Math.min(this.field_22789 - 16, 600);
      int var9 = (this.field_22789 - var8) / 2;
      int var10 = this.field_22790 - var7 - 72;
      short var11 = 180;
      Gfx.text(var1, "AutoBuild · Schematics", var9, 10, var5);
      String var12 = ".minecraft/dihclient/schematics";
      Gfx.text(var1, var12, var9 + var8 - Gfx.width(var12), 10, -7564380);
      Gfx.panel(var1, var9, var7, var11, var10, var5);
      String var13 = this.search.isEmpty() ? "Type to search …" : this.search + "_";
      Gfx.round(var1, var9 + 4, var7 + 4, var11 - 8, 14, -14868182);
      Gfx.text(var1, Gfx.trim(var13, var11 - 16), var9 + 8, var7 + 7, this.search.isEmpty() ? -7564380 : -1);
      byte var14 = 14;
      int var15 = var7 + 22;
      int var16 = (var10 - 26) / var14;
      if (this.files.isEmpty()) {
         Gfx.text(var1, this.allFiles.isEmpty() ? "No files found." : "No match.", var9 + 6, var15 + 4, -1446670);
         Gfx.text(var1, ".litematic .schem .schematic", var9 + 6, var15 + 16, -7564380);
      }

      for (int var17 = 0; var17 < var16 && this.fileScroll + var17 < this.files.size(); var17++) {
         Path var18 = this.files.get(this.fileScroll + var17);
         int var19 = var15 + var17 * var14;
         boolean var20 = var18.equals(this.selectedFile);
         if (var20) {
            var1.method_25294(var9 + 2, var19, var9 + var11 - 2, var19 + var14 - 1, ColorUtil.withAlpha(var5, 90));
         } else if (Gfx.inside(var2, var3, var9, var19, var11, var14)) {
            var1.method_25294(var9 + 2, var19, var9 + var11 - 2, var19 + var14 - 1, 553648127);
         }

         Gfx.text(var1, Gfx.trim(var18.getFileName().toString(), var11 - 12), var9 + 6, var19 + 3, var20 ? -1 : -1446670);
      }

      int var24 = var9 + var11 + var6;
      int var25 = var8 - var11 - var6;
      Gfx.panel(var1, var24, var7, var25, var10, var5);
      if (this.error != null) {
         Gfx.text(var1, "Could not read schematic:", var24 + 6, var7 + 6, -495247);
         Gfx.text(var1, Gfx.trim(this.error, var25 - 12), var24 + 6, var7 + 18, -1446670);
      } else if (this.plan == null) {
         Gfx.text(var1, "Select a schematic on the left.", var24 + 6, var7 + 6, -7564380);
      } else {
         Gfx.text(var1, Gfx.trim(this.schematic.name(), var25 - 12), var24 + 6, var7 + 6, -1);
         Gfx.text(
            var1,
            this.schematic.format + " · " + this.plan.sizeX + "×" + this.plan.sizeY + "×" + this.plan.sizeZ + " · " + this.plan.blocks.size() + " blocks",
            var24 + 6,
            var7 + 18,
            -7564380
         );
         this.button(var1, 20, var24 + var25 - 150, var7 + 4, 72, 14, "Rotate " + this.rotLabel(), this.rotation != class_2470.field_11467, var2, var3);
         this.button(var1, 21, var24 + var25 - 74, var7 + 4, 70, 14, "Mirror " + this.mirLabel(), this.mirror != class_2415.field_11302, var2, var3);
         this.button(var1, 30, var24 + 6, var7 + 32, 70, 14, "MATERIALS", this.tab == SchematicBrowserScreen.Tab.MATERIALS, var2, var3);
         this.button(var1, 31, var24 + 80, var7 + 32, 60, 14, "LAYERS", this.tab == SchematicBrowserScreen.Tab.LAYERS, var2, var3);
         this.button(var1, 32, var24 + 144, var7 + 32, 70, 14, "OPTIONS", this.tab == SchematicBrowserScreen.Tab.OPTIONS, var2, var3);
         if (this.tab == SchematicBrowserScreen.Tab.MATERIALS) {
            this.renderMaterials(var1, var24, var7 + 52, var25, var10 - 56);
         } else if (this.tab == SchematicBrowserScreen.Tab.OPTIONS) {
            this.renderOptions(var1, var24, var7 + 52, var25, var10 - 56, var2, var3);
         } else {
            this.renderLayers(var1, var24, var7 + 52, var25, var10 - 56, var2, var3);
         }
      }

      int var26 = var7 + var10 + 6;
      byte var27 = 16;
      boolean var21 = this.plan != null && this.field_22787 != null && this.field_22787.field_1724 != null;
      this.button(var1, 1, var9, var26, 70, var27, "PREVIEW", var21, var2, var3);
      this.button(var1, 2, var9 + 74, var26, 80, var27, "BUILD HERE", var21, var2, var3);
      this.button(var1, 8, var9 + 158, var26, 60, var27, "VERIFY", var21, var2, var3);
      String var22 = BuildRuntime.savedInfo();
      this.button(var1, 3, var9 + 222, var26, 62, var27, "RESUME", var22 != null && this.field_22787 != null && this.field_22787.field_1724 != null, var2, var3);
      this.button(var1, 4, var9 + 288, var26, 56, var27, "EXPORT", false, var2, var3);
      this.button(var1, 5, var9 + 348, var26, 56, var27, "FOLDER", false, var2, var3);
      this.button(var1, 6, var9 + 408, var26, 60, var27, "REFRESH", false, var2, var3);
      this.button(var1, 7, var9 + var8 - 60, var26, 60, var27, this.module.runtime().isRunning() ? "STOP" : "CLOSE", false, var2, var3);
      int quickY = var26 + var27 + 4;
      int quickX = var9;

      for (int q = 0; q < this.quick.size(); q++) {
         Setting<?> setting = this.quick.get(q);
         String label = this.quickLabel(setting);
         int width = Gfx.width(label) + 16;
         this.button(var1, 60 + q, quickX, quickY, width, 14, label, setting.get() instanceof Boolean on && on, var2, var3);
         quickX += width + 4;
      }

      String var23 = var22 != null ? "Saved: " + var22 : "Preview: aim with the crosshair, R/M/arrows/PgUp/PgDn, Enter builds";
      Gfx.text(var1, Gfx.trim(var23, var8), var9, quickY + 14 + 4, -7564380);
   }

   private void renderMaterials(class_332 var1, int var2, int var3, int var4, int var5) {
      int var6 = var5 / 18;
      List<Entry<class_1792, Integer>> var7 = new ArrayList<>(this.materials.entrySet());
      int var8 = 0;

      for (Entry<class_1792, Integer> var10 : var7) {
         if (InvUtil.count(var1x -> var1x.method_31574((class_1792)var10.getKey())) >= (Integer)var10.getValue()) {
            var8++;
         }
      }

      Gfx.text(var1, var8 + "/" + var7.size() + " complete", var2 + var4 - 8 - Gfx.width(var8 + "/" + var7.size() + " complete"), var3 - 16, -7564380);

      for (int var15 = 0; var15 < var6 && this.matScroll + var15 < var7.size(); var15++) {
         Entry<class_1792, Integer> var16 = var7.get(this.matScroll + var15);
         int var11 = var3 + var15 * 18;
         var1.method_51427(new class_1799((class_1935)var16.getKey()), var2 + 6, var11);
         Gfx.text(var1, Gfx.trim(((class_1792)var16.getKey()).method_63680().getString(), var4 - 130), var2 + 26, var11 + 4, -1446670);
         int var12 = InvUtil.count(var1x -> var1x.method_31574((class_1792)var16.getKey()));
         int var13 = ((class_1792)var16.getKey()).method_7882();
         String var14 = var12 + " / " + var16.getValue() + "  (" + ((Integer)var16.getValue() + var13 - 1) / var13 + " st)";
         Gfx.text(var1, var14, var2 + var4 - 8 - Gfx.width(var14), var11 + 4, var12 >= var16.getValue() ? -11870592 : -278748);
      }
   }

   private void renderLayers(class_332 var1, int var2, int var3, int var4, int var5, int var6, int var7) {
      this.button(var1, 40, var2 + 6, var3, 16, 14, "<", false, var6, var7);
      this.button(var1, 41, var2 + 86, var3, 16, 14, ">", false, var6, var7);
      Gfx.textCentered(var1, "Layer " + (this.viewLayer + 1) + "/" + this.plan.sizeY, var2 + 54, var3 + 3, -1);
      int var8 = 0;

      for (int[] var12 : this.layerColors) {
         for (int var16 : var12) {
            if (var16 != 0) {
               var8++;
            }
         }
      }

      Gfx.text(var1, var8 + " blocks · scroll = layer", var2 + 110, var3 + 3, -7564380);
      int var18 = var3 + 20;
      int var19 = var5 - 24;
      int var20 = var4 - 12;
      int var21 = Math.max(1, Math.min(var20 / Math.max(1, this.plan.sizeX), var19 / Math.max(1, this.plan.sizeZ)));
      int var22 = var2 + 6 + (var20 - var21 * this.plan.sizeX) / 2;
      int var23 = var18 + (var19 - var21 * this.plan.sizeZ) / 2;
      var1.method_25294(var22 - 1, var23 - 1, var22 + var21 * this.plan.sizeX + 1, var23 + var21 * this.plan.sizeZ + 1, 822083583);

      for (int var24 = 0; var24 < this.plan.sizeX; var24++) {
         for (int var25 = 0; var25 < this.plan.sizeZ; var25++) {
            int var17 = this.layerColors[var24][var25];
            if (var17 != 0) {
               var1.method_25294(var22 + var24 * var21, var23 + var25 * var21, var22 + (var24 + 1) * var21, var23 + (var25 + 1) * var21, var17);
            } else if (var21 >= 4) {
               var1.method_25294(var22 + var24 * var21, var23 + var25 * var21, var22 + (var24 + 1) * var21, var23 + (var25 + 1) * var21, 268435456);
            }
         }
      }

      Gfx.text(var1, "N", var22 + var21 * this.plan.sizeX / 2 - 2, var23 - 10, -7564380);
   }

   private String rotLabel() {
      return switch (this.rotation) {
         case field_11467 -> "0°";
         case field_11463 -> "90°";
         case field_11464 -> "180°";
         case field_11465 -> "270°";
         default -> throw new MatchException(null, null);
      };
   }

   private String mirLabel() {
      return switch (this.mirror) {
         case field_11302 -> "off";
         case field_11300 -> "N-S";
         case field_11301 -> "E-W";
         default -> throw new MatchException(null, null);
      };
   }

   public boolean method_25402(class_11909 var1, boolean var2) {
      double var3 = var1.comp_4798();
      double var5 = var1.comp_4799();

      for (int[] var8 : this.buttons) {
         if (Gfx.inside(var3, var5, var8[0], var8[1], var8[2], var8[3])) {
            this.action(var8[4]);
            return true;
         }
      }

      int var12 = Math.min(this.field_22789 - 16, 600);
      int var13 = (this.field_22789 - var12) / 2;
      byte var9 = 28;
      int var10 = var9 + 22;
      if (var3 >= var13 && var3 < var13 + 180 && var5 >= var10) {
         int var11 = this.fileScroll + (int)((var5 - var10) / 14.0);
         if (var11 >= 0 && var11 < this.files.size()) {
            this.select(this.files.get(var11));
            return true;
         }
      }

      return super.method_25402(var1, var2);
   }

   private void action(int var1) {
      if (this.optionAction(var1)) {
         return;
      }

      switch (var1) {
         case 1:
            if (this.plan != null && this.field_22787 != null && this.field_22787.field_1724 != null) {
               this.module.runtime().preview(this.schematic, this.rotation, this.mirror);
               if (!this.module.isEnabled()) {
                  this.module.setEnabled(true);
               }

               this.closeKeepModule();
            }
            break;
         case 2:
            if (this.plan != null && this.field_22787 != null && this.field_22787.field_1724 != null) {
               this.module.runtime().start(this.schematic, this.rotation, this.mirror);
               if (!this.module.isEnabled()) {
                  this.module.setEnabled(true);
               }

               this.closeKeepModule();
            }
            break;
         case 3:
            if (this.field_22787 != null && this.field_22787.field_1724 != null && this.module.runtime().resume()) {
               if (!this.module.isEnabled()) {
                  this.module.setEnabled(true);
               }

               this.closeKeepModule();
            }
            break;
         case 4:
            if (this.plan == null) {
               return;
            }

            try {
               Path var2 = BuildRuntime.exportMaterials(this.plan, AutoBuild.schematicDir());
               Notifications.info("AutoBuild", "Saved " + var2.getFileName());
               class_156.method_668().method_672(var2.toFile());
            } catch (Exception var3) {
               Notifications.error("AutoBuild", "Export failed: " + var3.getMessage());
            }
            break;
         case 5:
            class_156.method_668().method_672(AutoBuild.schematicDir().toFile());
            break;
         case 6:
            this.refresh();
            break;
         case 7:
            if (this.module.runtime().isRunning()) {
               this.module.runtime().stop();
            }

            this.method_25419();
            break;
         case 8:
            if (this.plan != null && this.field_22787 != null && this.field_22787.field_1724 != null) {
               this.module.startVerifyPreview(this.schematic, this.rotation, this.mirror);
               this.closeKeepModule();
            }
         case 9:
         case 10:
         case 11:
         case 12:
         case 13:
         case 14:
         case 15:
         case 16:
         case 17:
         case 18:
         case 19:
         case 22:
         case 23:
         case 24:
         case 25:
         case 26:
         case 27:
         case 28:
         case 29:
         case 32:
         case 33:
         case 34:
         case 35:
         case 36:
         case 37:
         case 38:
         case 39:
         default:
            break;
         case 20:
            this.rotation = this.rotation.method_10501(class_2470.field_11463);
            this.rebuildPlan();
            break;
         case 21:
            this.mirror = switch (this.mirror) {
               case field_11302 -> class_2415.field_11300;
               case field_11300 -> class_2415.field_11301;
               case field_11301 -> class_2415.field_11302;
               default -> throw new MatchException(null, null);
            };
            this.rebuildPlan();
            break;
         case 30:
            this.tab = SchematicBrowserScreen.Tab.MATERIALS;
            break;
         case 31:
            this.tab = SchematicBrowserScreen.Tab.LAYERS;
            break;
         case 40:
            this.changeLayer(-1);
            break;
         case 41:
            this.changeLayer(1);
      }
   }

   private boolean optionAction(int id) {
      if (id >= 60 && id < 60 + this.quick.size()) {
         if (this.quick.get(id - 60) instanceof BoolSetting bool) {
            bool.toggle();
         }

         return true;
      } else if (id == 32) {
         this.tab = SchematicBrowserScreen.Tab.OPTIONS;
         return true;
      } else if (id >= 100 && id < 200) {
         List<Setting<?>> opts = this.currentOptions();
         if (id - 100 < opts.size() && opts.get(id - 100) instanceof BoolSetting bool) {
            bool.toggle();
         }

         return true;
      } else if (id >= 200 && id < 400) {
         int index = (id - 200) / 2;
         int dir = (id - 200) % 2 == 0 ? -1 : 1;
         List<Setting<?>> opts = this.currentOptions();
         if (index < opts.size()) {
            Setting<?> setting = opts.get(index);
            if (setting instanceof IntSetting number) {
               int step = Math.max(1, (number.max() - number.min()) / 24);
               number.set(number.get() + dir * step);
            } else if (setting instanceof DoubleSetting number) {
               number.set(number.get() + dir * Math.max(number.step(), 0.05));
            }
         }

         return true;
      } else if (id >= 400 && id < 500) {
         List<Setting<?>> opts = this.currentOptions();
         if (id - 400 < opts.size() && opts.get(id - 400) instanceof EnumSetting<?> choice) {
            choice.cycle(true);
         }

         return true;
      } else if (id >= 500 && id < 600) {
         List<Setting<?>> opts = this.currentOptions();
         if (id - 500 < opts.size() && opts.get(id - 500) instanceof ActionSetting action) {
            action.run();
         }

         return true;
      } else {
         return false;
      }
   }

   private void renderOptions(class_332 g, int x, int y, int w, int h, int mouseX, int mouseY) {
      List<Setting<?>> opts = this.currentOptions();
      int rows = Math.max(1, h / 18);
      this.optScroll = Math.max(0, Math.min(Math.max(0, opts.size() - rows), this.optScroll));
      for (int i = 0; i < rows && this.optScroll + i < opts.size(); i++) {
         int index = this.optScroll + i;
         Setting<?> setting = opts.get(index);
         int ry = y + i * 18;
         if (i % 2 == 0) {
            g.method_25294(x + 3, ry, x + w - 3, ry + 17, 335544320);
         }

         Gfx.text(g, Gfx.trim(setting.name(), w - 130), x + 8, ry + 5, -1446670);
         if (setting instanceof ActionSetting) {
            this.button(g, 500 + index, x + w - 58, ry + 2, 50, 14, "RUN", true, mouseX, mouseY);
         } else if (setting instanceof BoolSetting bool) {
            this.button(g, 100 + index, x + w - 58, ry + 2, 50, 14, bool.get() ? "ON" : "OFF", bool.get(), mouseX, mouseY);
         } else if (setting instanceof EnumSetting<?>) {
            this.button(g, 400 + index, x + w - 118, ry + 2, 110, 14, Gfx.trim(setting.displayValue(), 100), false, mouseX, mouseY);
         } else if (setting instanceof IntSetting || setting instanceof DoubleSetting) {
            this.button(g, 200 + index * 2, x + w - 98, ry + 2, 16, 14, "-", false, mouseX, mouseY);
            Gfx.textCentered(g, setting.displayValue(), x + w - 56, ry + 5, -1);
            this.button(g, 201 + index * 2, x + w - 26, ry + 2, 16, 14, "+", false, mouseX, mouseY);
         } else {
            Gfx.text(g, Gfx.trim(setting.displayValue(), 100), x + w - 108, ry + 5, -7564380);
         }
      }

      Gfx.text(g, opts.size() + " options · scroll for more", x + 8, y + rows * 18 + 2, -7564380);
   }

   private void changeLayer(int var1) {
      if (this.plan != null) {
         this.viewLayer = Math.max(0, Math.min(this.plan.sizeY - 1, this.viewLayer + var1));
         this.buildLayer();
      }
   }

   public boolean method_25401(double var1, double var3, double var5, double var7) {
      int var9 = Math.min(this.field_22789 - 16, 600);
      int var10 = (this.field_22789 - var9) / 2;
      if (var1 < var10 + 180) {
         this.fileScroll = Math.max(0, Math.min(Math.max(0, this.files.size() - 5), this.fileScroll - (int)var7));
      } else if (this.tab == SchematicBrowserScreen.Tab.OPTIONS) {
         this.optScroll = Math.max(0, this.optScroll - (int)var7);
      } else if (this.tab == SchematicBrowserScreen.Tab.LAYERS) {
         this.changeLayer(var7 > 0.0 ? 1 : -1);
      } else {
         this.matScroll = Math.max(0, Math.min(Math.max(0, this.materials.size() - 5), this.matScroll - (int)var7));
      }

      return true;
   }

   public boolean method_25400(class_11905 var1) {
      if (var1.method_74227()) {
         this.search = this.search + var1.method_74226();
         this.applySearch();
         return true;
      } else {
         return super.method_25400(var1);
      }
   }

   public boolean method_25404(class_11908 var1) {
      int var2 = var1.comp_4795();
      if (var2 == 256) {
         this.method_25419();
         return true;
      } else if (var2 == 259 && !this.search.isEmpty()) {
         this.search = this.search.substring(0, this.search.length() - 1);
         this.applySearch();
         return true;
      } else if (var2 == 257 && this.plan != null) {
         this.action(1);
         return true;
      } else if (var2 != 264 && var2 != 265) {
         return super.method_25404(var1);
      } else {
         int var3 = this.files.indexOf(this.selectedFile) + (var2 == 264 ? 1 : -1);
         if (var3 >= 0 && var3 < this.files.size()) {
            this.select(this.files.get(var3));
         }

         return true;
      }
   }

   private void closeKeepModule() {
      if (this.field_22787 != null) {
         this.field_22787.method_1507(this.parent instanceof SchematicBrowserScreen ? null : this.parent);
      }
   }

   public void method_25419() {
      if (this.field_22787 != null) {
         this.field_22787.method_1507(this.parent);
      }

      if (!this.module.runtime().isRunning() && this.module.isEnabled() && this.parent == null) {
         this.module.setEnabled(false);
      }
   }

   private static enum Tab {
      MATERIALS,
      LAYERS,
      OPTIONS;
   }
}
