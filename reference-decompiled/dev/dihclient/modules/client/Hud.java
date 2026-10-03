package dev.dihclient.modules.client;

import dev.dihclient.gui.HudEditorScreen;
import dev.dihclient.module.Category;
import dev.dihclient.module.Module;
import dev.dihclient.setting.BoolSetting;
import dev.dihclient.setting.DoubleSetting;
import dev.dihclient.setting.EnumSetting;
import dev.dihclient.setting.IntSetting;
import dev.dihclient.setting.StringSetting;

public class Hud extends Module {
   public final EnumSetting<Hud.Style> style = this.mode("Style", "Glass: rounded translucent panels. Minimal: text only.", Hud.Style.GLASS);
   public final IntSetting opacity = this.integer("Opacity", "Background opacity of HUD panels.", 170, 0, 255);
   public final BoolSetting textShadow = this.bool("Text Shadow", "Draws text with a shadow.", true);
   public final StringSetting clientName = this.text("Client Name", "Text of the watermark.", "DIHClient", 24);
   public final BoolSetting watermark = this.bool("Watermark", "Logo with FPS, ping and time.", true);
   public final BoolSetting activeModules = this.bool("Active Modules", "Animated list of enabled modules.", true).legacy("hud.activeModules");
   public final EnumSetting<Hud.ListSort> listSort = this.mode("List Sort", "Sort order of the module list.", Hud.ListSort.WIDTH)
      .visibleWhen(this.activeModules::get);
   public final BoolSetting listSuffix = this.bool("List Info", "Shows extra info (range, mode ...) next to module names.", true)
      .visibleWhen(this.activeModules::get);
   public final BoolSetting listLowercase = this.bool("Lowercase", "Lowercase module list.", false).visibleWhen(this.activeModules::get);
   public final BoolSetting coordinates = this.bool("Coordinates", "Position, facing and nether/overworld coordinates.", true).legacy("hud.coordinates");
   public final BoolSetting stats = this.bool("Stats", "FPS, ping, speed.", true).legacy("hud.stats");
   public final BoolSetting potions = this.bool("Potion Effects", "Active status effects with remaining time.", true);
   public final BoolSetting keystrokes = this.bool("Keystrokes", "WASD, mouse buttons with CPS, space.", false).legacy("hud.keystrokes");
   public final BoolSetting chunkRadar = this.bool(
      "Chunk Radar", "Mini map of chunks marked by the finders (Sus ChunkFinder, Spawner Finder …) and Netherite deposits. Works with every renderer.", false
   );
   public final IntSetting chunkRadarRange = this.integer("Chunk Radar Range", "Radius in chunks.", 8, 3, 24).visibleWhen(this.chunkRadar::get);
   public final BoolSetting radar = this.bool("Radar", "Top-down player radar.", false).legacy("hud.radar");
   public final DoubleSetting radarRange = this.dbl("Radar Range", "Radar range in blocks.", 48.0, 8.0, 256.0, 4.0)
      .legacy("hud.radarRange")
      .visibleWhen(this.radar::get);
   public final BoolSetting armor = this.bool("Armor", "Armor and held item with durability.", true).legacy("hud.armor");
   public final BoolSetting serverInfo = this.bool("Server Info", "Server address, players, brand.", false).legacy("hud.serverInfo");
   public final BoolSetting snapGrid = this.bool("Snapping", "Snaps elements to edges, centre and other elements in the editor.", true).legacy("hud.snapGrid");

   public Hud() {
      super("HUD", Category.CLIENT, "DIHClient HUD. 'Edit HUD' opens the editor: drag to move, scroll to scale.");
      this.action("Edit HUD", "Opens the HUD editor.", () -> mc.execute(() -> mc.method_1507(new HudEditorScreen(mc.field_1755))));
   }

   public boolean glass() {
      return this.style.get() == Hud.Style.GLASS;
   }

   public static enum ListSort {
      WIDTH,
      ALPHABET;
   }

   public static enum Style {
      GLASS,
      MINIMAL;
   }
}
