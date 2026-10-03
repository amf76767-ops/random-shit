package dev.dihclient.modules.world;

import dev.dihclient.DIHClient;
import dev.dihclient.autobuild.MapArtGenerator;
import dev.dihclient.autobuild.Schematic;
import dev.dihclient.autobuild.SchematicLoader;
import dev.dihclient.autobuild.SchematicWriter;
import dev.dihclient.module.Category;
import dev.dihclient.module.Module;
import dev.dihclient.module.ModuleManager;
import dev.dihclient.setting.BoolSetting;
import dev.dihclient.setting.EnumSetting;
import dev.dihclient.setting.IdListSetting;
import dev.dihclient.setting.IntSetting;
import dev.dihclient.setting.StringSetting;
import dev.dihclient.util.ImageLoader;
import dev.dihclient.util.Notifications;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Stream;
import javax.imageio.ImageIO;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.class_156;
import net.minecraft.class_2248;
import net.minecraft.class_2346;
import net.minecraft.class_2960;
import net.minecraft.class_3620;
import net.minecraft.class_7923;

public class MapArt extends Module {
   public final StringSetting image = this.text("Image", "Picture in .minecraft/dihclient/mapart (empty = first). Use Choose Image.", "", 200);
   public final IntSetting mapsX = this.integer("Maps Wide", "Width in maps (1 map = 128 blocks).", 1, 1, 4);
   public final IntSetting mapsZ = this.integer("Maps High", "Height in maps.", 1, 1, 4);
   public final EnumSetting<MapArtGenerator.Mode> mode = this.mode(
      "Mode",
      "Flat: one height, 1 shade per block (easy to build). Staircase: 3 shades per block (many more colours) – columns go up and down.",
      MapArtGenerator.Mode.FLAT
   );
   public final EnumSetting<MapArtGenerator.Fit> fit = this.mode(
      "Fit", "Crop: fill the map, cut edges. Fit: whole picture, empty borders. Stretch: distort to fill.", MapArtGenerator.Fit.CROP
   );
   public final BoolSetting dither = this.bool(
      "Dither", "Mixes colours in patterns for smoother gradients (photos). Off = clean flat areas (logos, pixel art).", true
   );
   public final BoolSetting noobline = this.bool("Noobline", "Adds a row north of the art so the first row gets the right shade.", true);
   public final EnumSetting<MapArtGenerator.Supports> supports = this.mode(
      "Supports",
      "Staircase: Under = one block under raised blocks, Full = solid columns (easiest to build, more blocks), None.",
      MapArtGenerator.Supports.UNDER
   );
   public final StringSetting supportBlock = this.text("Support Block", "Block for the noobline and supports.", "minecraft:cobblestone", 64);
   public final EnumSetting<MapArt.Preset> preset = this.mode(
      "Palette Preset",
      "Custom: the Palette list below. Wool / Concrete / Terracotta / Carpet: only that material (carpet art is cheap and flat). Cheap: Custom without expensive blocks (diamond, gold, emerald …).",
      MapArt.Preset.CUSTOM
   );
   public final IntSetting maxColours = this.integer(
      "Max Colours", "Uses only the N most needed blocks (fewer different materials to collect). 0 = no limit.", 0, 0, 61
   );
   public final IntSetting brightness = this.integer("Brightness", "Makes the picture brighter or darker before converting (%).", 0, -60, 60);
   public final IntSetting contrast = this.integer("Contrast", "More or less contrast before converting (%).", 0, -60, 60);
   public final IntSetting saturation = this.integer("Saturation", "Colour strength before converting (100 = unchanged, 0 = black & white).", 100, 0, 200);
   public final IdListSetting palette;
   private static final String[] DEFAULT_PALETTE = new String[]{
      "minecraft:white_wool",
      "minecraft:orange_wool",
      "minecraft:magenta_wool",
      "minecraft:light_blue_wool",
      "minecraft:yellow_wool",
      "minecraft:lime_wool",
      "minecraft:pink_wool",
      "minecraft:gray_wool",
      "minecraft:light_gray_wool",
      "minecraft:cyan_wool",
      "minecraft:purple_wool",
      "minecraft:blue_wool",
      "minecraft:brown_wool",
      "minecraft:green_wool",
      "minecraft:red_wool",
      "minecraft:black_wool",
      "minecraft:white_terracotta",
      "minecraft:orange_terracotta",
      "minecraft:magenta_terracotta",
      "minecraft:light_blue_terracotta",
      "minecraft:yellow_terracotta",
      "minecraft:lime_terracotta",
      "minecraft:pink_terracotta",
      "minecraft:gray_terracotta",
      "minecraft:light_gray_terracotta",
      "minecraft:cyan_terracotta",
      "minecraft:purple_terracotta",
      "minecraft:blue_terracotta",
      "minecraft:brown_terracotta",
      "minecraft:green_terracotta",
      "minecraft:red_terracotta",
      "minecraft:black_terracotta",
      "minecraft:snow_block",
      "minecraft:stone",
      "minecraft:oak_planks",
      "minecraft:spruce_planks",
      "minecraft:birch_planks",
      "minecraft:dark_oak_planks",
      "minecraft:acacia_planks",
      "minecraft:crimson_planks",
      "minecraft:warped_planks",
      "minecraft:mangrove_planks",
      "minecraft:cherry_planks",
      "minecraft:pale_oak_planks",
      "minecraft:crimson_hyphae",
      "minecraft:warped_hyphae",
      "minecraft:crimson_nylium",
      "minecraft:warped_nylium",
      "minecraft:warped_wart_block",
      "minecraft:nether_wart_block",
      "minecraft:clay",
      "minecraft:diorite",
      "minecraft:granite",
      "minecraft:iron_block",
      "minecraft:gold_block",
      "minecraft:diamond_block",
      "minecraft:emerald_block",
      "minecraft:lapis_block",
      "minecraft:redstone_block",
      "minecraft:netherrack",
      "minecraft:obsidian",
      "minecraft:quartz_block",
      "minecraft:prismarine",
      "minecraft:packed_ice",
      "minecraft:moss_block",
      "minecraft:grass_block",
      "minecraft:mushroom_stem",
      "minecraft:deepslate",
      "minecraft:raw_iron_block",
      "minecraft:verdant_froglight",
      "minecraft:dirt",
      "minecraft:oak_leaves",
      "minecraft:sandstone",
      "minecraft:red_sandstone",
      "minecraft:soul_soil",
      "minecraft:white_concrete",
      "minecraft:black_concrete"
   };
   private volatile boolean working;
   private String lastInfo;
   private volatile Path lastPreview;
   private volatile Map<String, Integer> lastCounts;
   private volatile String lastName;
   private static final String[] COLOURS = new String[]{
      "white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray", "light_gray", "cyan", "purple", "blue", "brown", "green", "red", "black"
   };
   private static final List<String> EXPENSIVE = List.of(
      "minecraft:diamond_block",
      "minecraft:emerald_block",
      "minecraft:gold_block",
      "minecraft:iron_block",
      "minecraft:lapis_block",
      "minecraft:redstone_block",
      "minecraft:netherite_block",
      "minecraft:raw_iron_block",
      "minecraft:raw_gold_block",
      "minecraft:verdant_froglight",
      "minecraft:warped_wart_block",
      "minecraft:prismarine",
      "minecraft:packed_ice",
      "minecraft:quartz_block"
   );

   public MapArt() {
      super("MapArt", Category.AUTOMATION, "Turns a picture into map art and builds it with AutoBuild (exact map colours, staircase shading, dithering).");
      this.palette = this.ids("Palette", "Blocks the art may use (one per map colour, first in list wins).", IdListSetting.Kind.BLOCK, DEFAULT_PALETTE);
      this.lastInfo = "";
      this.action("Choose Image", "Pick a PNG / JPG / GIF from your computer.", this::chooseImage);
      this.action("Generate & Preview", "Creates the map art schematic and shows it (snaps to the map grid). Enter = build.", this::generate);
      this.action("Open Folder", "Opens .minecraft/dihclient/mapart.", () -> class_156.method_668().method_672(dir().toFile()));
      this.action("Open Preview", "Opens the preview picture of the last generated map art.", () -> {
         if (this.lastPreview != null && Files.exists(this.lastPreview)) {
            class_156.method_668().method_672(this.lastPreview.toFile());
         } else {
            Notifications.info("MapArt", "Generate something first");
         }
      });
      this.action("Export Materials", "Writes the material list of the last map art (stacks + shulker boxes) next to the preview.", this::exportMaterials);
   }

   @Override
   public boolean isToggleable() {
      return false;
   }

   public static Path dir() {
      Path var0 = FabricLoader.getInstance().getGameDir().resolve("dihclient").resolve("mapart");

      try {
         Files.createDirectories(var0);
      } catch (Exception var2) {
      }

      return var0;
   }

   private void chooseImage() {
      ImageLoader.pickFile("Choose a picture for map art").whenComplete((var1, var2) -> mc.execute(() -> {
         if (var2 == null && var1 != null) {
            try {
               Path var3 = dir().resolve(var1.getFileName().toString());
               if (!var1.toAbsolutePath().normalize().equals(var3.toAbsolutePath().normalize())) {
                  Files.copy(var1, var3, StandardCopyOption.REPLACE_EXISTING);
               }

               this.image.set(var3.getFileName().toString());
               Notifications.info("MapArt", "Image: " + var3.getFileName() + " – now press Generate & Preview");
            } catch (Exception var4) {
               Notifications.error("MapArt", "Could not copy " + var1.getFileName());
            }
         }
      }));
   }

   private Path imageFile() {
      ArrayList var1 = new ArrayList();

      try (Stream var2 = Files.list(dir())) {
         var2.filter(var0 -> Files.isRegularFile(var0) && ImageLoader.supported(var0) && !var0.getFileName().toString().endsWith("_preview.png"))
            .sorted()
            .forEach(var1::add);
      } catch (Exception var7) {
      }

      for (Path var3 : var1) {
         if (var3.getFileName().toString().equalsIgnoreCase(this.image.get().trim())) {
            return var3;
         }
      }

      return var1.isEmpty() ? null : (Path)var1.get(0);
   }

   private List<MapArtGenerator.PaletteEntry> buildPalette() {
      ArrayList var1 = new ArrayList();
      HashSet var2 = new HashSet();

      for (String var4 : this.presetIds()) {
         class_2960 var5 = class_2960.method_12829(var4);
         if (var5 != null) {
            class_2248 var6 = (class_2248)class_7923.field_41175.method_17966(var5).orElse(null);
            if (var6 != null && !(var6 instanceof class_2346)) {
               class_3620 var7 = var6.method_26403();
               if (var7 != null && var7.field_16021 != 0 && var7 != class_3620.field_16019 && var2.add(var7.field_16021)) {
                  var1.add(new MapArtGenerator.PaletteEntry(class_7923.field_41175.method_10221(var6).toString(), var7.field_16011));
               }
            }
         }
      }

      return var1;
   }

   private List<String> presetIds() {
      ArrayList var1 = new ArrayList();
      switch ((MapArt.Preset)this.preset.get()) {
         case WOOL:
            for (String var19 : COLOURS) {
               var1.add("minecraft:" + var19 + "_wool");
            }
            break;
         case CONCRETE:
            for (String var18 : COLOURS) {
               var1.add("minecraft:" + var18 + "_concrete");
            }
            break;
         case TERRACOTTA:
            var1.add("minecraft:terracotta");

            for (String var17 : COLOURS) {
               var1.add("minecraft:" + var17 + "_terracotta");
            }
            break;
         case CARPET:
            for (String var5 : COLOURS) {
               var1.add("minecraft:" + var5 + "_carpet");
            }

            var1.add("minecraft:moss_carpet");
            break;
         case CHEAP:
            for (String var3 : this.palette.get()) {
               if (!EXPENSIVE.contains(var3)) {
                  var1.add(var3);
               }
            }
            break;
         default:
            var1.addAll(this.palette.get());
      }

      return var1;
   }

   private BufferedImage adjust(BufferedImage var1) {
      int var2 = this.brightness.get();
      int var3 = this.contrast.get();
      int var4 = this.saturation.get();
      if (var2 == 0 && var3 == 0 && var4 == 100) {
         return var1;
      } else {
         BufferedImage var5 = new BufferedImage(var1.getWidth(), var1.getHeight(), 2);
         double var6 = var2 * 2.55;
         double var8 = (100.0 + var3) / 100.0;
         double var10 = var4 / 100.0;

         for (int var12 = 0; var12 < var1.getHeight(); var12++) {
            for (int var13 = 0; var13 < var1.getWidth(); var13++) {
               int var14 = var1.getRGB(var13, var12);
               double var15 = var14 >> 16 & 0xFF;
               double var17 = var14 >> 8 & 0xFF;
               double var19 = var14 & 0xFF;
               double var21 = 0.299 * var15 + 0.587 * var17 + 0.114 * var19;
               var15 = var21 + (var15 - var21) * var10;
               var17 = var21 + (var17 - var21) * var10;
               var19 = var21 + (var19 - var21) * var10;
               var15 = (var15 - 128.0) * var8 + 128.0 + var6;
               var17 = (var17 - 128.0) * var8 + 128.0 + var6;
               var19 = (var19 - 128.0) * var8 + 128.0 + var6;
               var5.setRGB(var13, var12, var14 & 0xFF000000 | c(var15) << 16 | c(var17) << 8 | c(var19));
            }
         }

         return var5;
      }
   }

   private static int c(double var0) {
      return (int)Math.max(0.0, Math.min(255.0, (double)Math.round(var0)));
   }

   private void exportMaterials() {
      Map var1 = this.lastCounts;
      if (var1 != null && this.lastName != null) {
         try {
            ArrayList var2 = new ArrayList(var1.entrySet());
            var2.sort((var0, var1x) -> Integer.compare((Integer)var1x.getValue(), (Integer)var0.getValue()));
            StringBuilder var3 = new StringBuilder("Materials for " + this.lastName + "\n\n");
            int var4 = 0;

            for (Entry var6 : var2) {
               int var7 = (Integer)var6.getValue();
               var4 += var7;
               var3.append(
                  String.format(Locale.ROOT, "%-36s %6d  = %d stacks + %d   (%.2f shulker boxes)%n", var6.getKey(), var7, var7 / 64, var7 % 64, var7 / 1728.0)
               );
            }

            var3.append(String.format(Locale.ROOT, "%nTotal: %d blocks (%.1f shulker boxes)%n", var4, var4 / 1728.0));
            Path var9 = dir().resolve(this.lastName + "_materials.txt");
            Files.writeString(var9, var3.toString());
            Notifications.info("MapArt", "Saved " + var9.getFileName());
            class_156.method_668().method_672(var9.toFile());
         } catch (Exception var8) {
            Notifications.error("MapArt", "Could not write the list: " + var8.getMessage());
         }
      } else {
         Notifications.info("MapArt", "Generate something first");
      }
   }

   private void generate() {
      if (this.working) {
         Notifications.info("MapArt", "Still working …");
      } else {
         Path var1 = this.imageFile();
         if (var1 == null) {
            Notifications.warn("MapArt", "No picture yet – use Choose Image");
         } else {
            List var2 = this.buildPalette();
            if (var2.size() < 2) {
               Notifications.warn("MapArt", "Palette needs at least 2 blocks with different map colours");
            } else {
               MapArtGenerator.Options var3 = new MapArtGenerator.Options();
               var3.mapsX = this.mapsX.get();
               var3.mapsZ = this.mapsZ.get();
               var3.mode = this.mode.get();
               var3.fit = this.fit.get();
               var3.dither = this.dither.get();
               var3.noobline = this.noobline.get();
               var3.supports = this.supports.get();
               String var4 = this.supportBlock.get().trim().isEmpty() ? "minecraft:cobblestone" : this.supportBlock.get().trim().toLowerCase(Locale.ROOT);
               var3.supportBlock = var4.contains(":") ? var4 : "minecraft:" + var4;
               var3.nooblineBlock = var3.supportBlock;
               short var5 = 4671;
               this.working = true;
               Notifications.info("MapArt", "Generating " + 128 * var3.mapsX + "x" + 128 * var3.mapsZ + " map art …");
               class_156.method_18349()
                  .execute(
                     () -> {
                        try {
                           BufferedImage var5x = ImageIO.read(var1.toFile());
                           if (var5x == null) {
                              throw new IllegalArgumentException("unsupported picture");
                           }

                           var5x = this.adjust(var5x);
                           MapArtGenerator.Result var6 = MapArtGenerator.generate(var5x, var2, var3);
                           int var7 = this.maxColours.get();
                           if (var7 >= 2 && var6.colorsUsed() > var7) {
                              ArrayList var8 = new ArrayList(var2);
                              Map var9 = var6.counts();
                              var8.sort((var1xx, var2xx) -> Integer.compare(var9.getOrDefault(var2xx.blockId(), 0), var9.getOrDefault(var1xx.blockId(), 0)));
                              var6 = MapArtGenerator.generate(var5x, new ArrayList<>(var8.subList(0, var7)), var3);
                           }

                           String var15 = var1.getFileName().toString().replaceAll("\\.[^.]+$", "").replaceAll("[^A-Za-z0-9_-]", "_");
                           String var16 = "mapart_"
                              + var15
                              + "_"
                              + var3.mapsX
                              + "x"
                              + var3.mapsZ
                              + (var3.mode == MapArtGenerator.Mode.STAIRCASE ? "_stair" : "_flat");
                           Path var10 = AutoBuild.schematicDir().resolve(var16 + ".schem");
                           SchematicWriter.writeSponge(var10, var6.blocks(), var5);
                           ImageIO.write(var6.preview(), "png", dir().resolve(var16 + "_preview.png").toFile());
                           this.lastPreview = dir().resolve(var16 + "_preview.png");
                           this.lastCounts = var6.counts();
                           this.lastName = var16;
                           Schematic var11 = SchematicLoader.load(var10);
                           String var12 = var6.width()
                              + "x"
                              + var6.length()
                              + ", height "
                              + var6.height()
                              + ", "
                              + var6.blocks().size()
                              + " blocks, "
                              + var6.colorsUsed()
                              + " colours";
                           mc.execute(() -> {
                              this.working = false;
                              this.lastInfo = var12;
                              Notifications.info("MapArt", "Done: " + var12 + " – aim and press Enter to build");
                              AutoBuild var3xx = ModuleManager.of(AutoBuild.class);
                              var3xx.startPreview(var11);
                           });
                        } catch (Throwable var13) {
                           DIHClient.LOG.error("[DIHClient] map art failed", var13);
                           mc.execute(() -> {
                              this.working = false;
                              Notifications.error("MapArt", "Failed: " + var13.getMessage());
                           });
                        }
                     }
                  );
            }
         }
      }
   }

   @Override
   public List<String> details() {
      ArrayList var1 = new ArrayList();
      Path var2 = this.imageFile();
      var1.add("Image: " + (var2 == null ? "none – Choose Image" : var2.getFileName()));
      var1.add("Palette: " + this.buildPalette().size() + " map colours" + (this.mode.get() == MapArtGenerator.Mode.STAIRCASE ? " × 3 shades" : ""));
      if (!this.lastInfo.isEmpty()) {
         var1.add("Last: " + this.lastInfo);
      }

      Map var3 = this.lastCounts;
      if (var3 != null) {
         ArrayList var4 = new ArrayList(var3.entrySet());
         var4.sort((var0, var1x) -> Integer.compare((Integer)var1x.getValue(), (Integer)var0.getValue()));

         for (int var5 = 0; var5 < Math.min(5, var4.size()); var5++) {
            int var6 = (Integer)((Entry)var4.get(var5)).getValue();
            var1.add(((String)((Entry)var4.get(var5)).getKey()).replace("minecraft:", "") + ": " + var6 + " (" + (var6 + 63) / 64 + " stacks)");
         }
      }

      var1.add("Preview PNG + .schem are saved; the schematic also shows up in the AutoBuild browser.");
      return var1;
   }

   public static enum Preset {
      CUSTOM,
      WOOL,
      CONCRETE,
      TERRACOTTA,
      CARPET,
      CHEAP;
   }
}
