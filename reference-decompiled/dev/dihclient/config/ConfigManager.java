package dev.dihclient.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.dihclient.DIHClient;
import dev.dihclient.hud.HudElement;
import dev.dihclient.module.Module;
import dev.dihclient.setting.ActionSetting;
import dev.dihclient.setting.Setting;
import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Properties;
import net.fabricmc.loader.api.FabricLoader;

public final class ConfigManager {
   private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
   public static final int PROFILE_SLOTS = 3;
   private final Path dir;
   private final Path file;
   private boolean dirty;
   private int autosaveTicks;

   public ConfigManager(Path var1) {
      this.dir = var1;
      this.file = var1.resolve("config.json");
   }

   public Path dir() {
      return this.dir;
   }

   public void markDirty() {
      this.dirty = true;
   }

   public void tickAutosave() {
      if (this.dirty) {
         if (++this.autosaveTicks >= 100) {
            this.autosaveTicks = 0;
            this.save();
         }
      }
   }

   public void load() {
      try {
         Files.createDirectories(this.dir);
         if (Files.exists(this.file)) {
            this.apply(read(this.file));
         } else {
            this.applyDefaultStates();
            this.importLegacy();
            this.save();
         }
      } catch (Exception var2) {
         DIHClient.LOG.error("[DIHClient] config load failed", var2);
      }

      this.dirty = false;
   }

   public void save() {
      try {
         Files.createDirectories(this.dir);
         write(this.file, this.serialize());
         this.dirty = false;
      } catch (Exception var2) {
         DIHClient.LOG.error("[DIHClient] config save failed", var2);
      }
   }

   public boolean saveProfile(int var1) {
      try {
         Path var2 = this.profilePath(var1);
         Files.createDirectories(var2.getParent());
         write(var2, this.serialize());
         return true;
      } catch (Exception var3) {
         DIHClient.LOG.error("[DIHClient] profile save failed", var3);
         return false;
      }
   }

   public boolean loadProfile(int var1) {
      Path var2 = this.profilePath(var1);
      if (!Files.exists(var2)) {
         return false;
      } else {
         try {
            this.apply(read(var2));
            this.save();
            return true;
         } catch (Exception var4) {
            DIHClient.LOG.error("[DIHClient] profile load failed", var4);
            return false;
         }
      }
   }

   public boolean profileExists(int var1) {
      return Files.exists(this.profilePath(var1));
   }

   public Path profilePath(int var1) {
      return this.dir.resolve("profiles").resolve("profile-" + var1 + ".json");
   }

   private JsonObject serialize() {
      JsonObject var1 = new JsonObject();
      var1.addProperty("version", 1);
      JsonObject var2 = new JsonObject();

      for (Module var4 : DIHClient.modules().all()) {
         JsonObject var5 = new JsonObject();
         var5.addProperty("enabled", var4.isEnabled());
         var5.addProperty("key", var4.keybind());
         var5.addProperty("notify", var4.showToggleNotification());
         JsonObject var6 = new JsonObject();

         for (Setting var8 : var4.settings()) {
            if (!(var8 instanceof ActionSetting)) {
               var6.add(var8.id(), var8.toJson());
            }
         }

         var5.add("settings", var6);
         var2.add(var4.id(), var5);
      }

      var1.add("modules", var2);
      JsonObject var9 = new JsonObject();

      for (HudElement var11 : DIHClient.hud().elements()) {
         JsonObject var12 = new JsonObject();
         var12.addProperty("x", var11.x());
         var12.addProperty("y", var11.y());
         var12.addProperty("scale", var11.scale());
         var9.add(var11.id(), var12);
      }

      var1.add("hud", var9);
      var1.add("gui", GuiState.toJson());
      return var1;
   }

   private void apply(JsonObject var1) {
      JsonObject var2 = obj(var1, "modules");

      for (Module var4 : DIHClient.modules().all()) {
         JsonObject var5 = var2 == null ? null : obj(var2, var4.id());
         if (var5 != null) {
            JsonObject var6 = obj(var5, "settings");
            if (var6 != null) {
               for (Setting var8 : var4.settings()) {
                  JsonElement var9 = var6.get(var8.id());
                  if (var9 != null) {
                     try {
                        var8.fromJson(var9);
                     } catch (Exception var11) {
                     }
                  }
               }
            }

            if (var5.has("key")) {
               var4.setKeybindSilently(var5.get("key").getAsInt());
            }

            if (var5.has("notify")) {
               var4.setShowToggleNotification(var5.get("notify").getAsBoolean());
            }

            if (var5.has("enabled")) {
               var4.setEnabledSilently(var5.get("enabled").getAsBoolean());
            }
         }
      }

      JsonObject var12 = obj(var1, "hud");
      if (var12 != null) {
         for (HudElement var15 : DIHClient.hud().elements()) {
            JsonObject var16 = obj(var12, var15.id());
            if (var16 != null && var16.has("x") && var16.has("y")) {
               var15.setPosition(var16.get("x").getAsInt(), var16.get("y").getAsInt());
            }

            if (var16 != null && var16.has("scale")) {
               var15.setScale(var16.get("scale").getAsFloat());
            }
         }
      }

      JsonObject var14 = obj(var1, "gui");
      if (var14 != null) {
         GuiState.fromJson(var14);
      }
   }

   private void applyDefaultStates() {
      for (String var4 : new String[]{"Sus ChunkFinder", "ChunkFinder", "HUD", "Notifications", "ClickGUI"}) {
         Module var5 = DIHClient.modules().get(var4);
         if (var5 != null) {
            var5.setEnabledSilently(true);
         }
      }
   }

   private void importLegacy() {
      Path var1 = FabricLoader.getInstance().getConfigDir();
      Path var2 = var1.resolve("dihclient.properties");
      if (!Files.exists(var2)) {
         var2 = var1.resolve("utilityclient.properties");
      }

      if (Files.exists(var2)) {
         Properties var3 = new Properties();

         try (BufferedReader var4 = Files.newBufferedReader(var2, StandardCharsets.UTF_8)) {
            var3.load(var4);
         } catch (IOException var18) {
            DIHClient.LOG.warn("[DIHClient] could not read legacy config", var18);
            return;
         }

         int var19 = 0;

         for (Module var6 : DIHClient.modules().all()) {
            String var7 = var3.getProperty("module." + var6.name());
            if (var7 == null) {
               var7 = var3.getProperty("module." + legacyName(var6));
            }

            if (var7 != null) {
               var6.setEnabledSilently(Boolean.parseBoolean(var7));
            }

            String var8 = var3.getProperty("moduleBind." + var6.name());
            if (var8 == null) {
               var8 = var3.getProperty("moduleBind." + legacyName(var6));
            }

            if (var8 != null) {
               try {
                  int var9 = Integer.parseInt(var8.trim());
                  var6.setKeybindSilently(var9 <= 0 ? -1 : var9);
               } catch (NumberFormatException var16) {
               }
            }

            String var26 = var3.getProperty("moduleNotify." + var6.name());
            if (var26 != null) {
               var6.setShowToggleNotification(Boolean.parseBoolean(var26));
            }

            for (Setting var11 : var6.settings()) {
               if (var11.legacyKey() != null) {
                  String var12 = var3.getProperty(var11.legacyKey());
                  if (var12 != null) {
                     var11.fromLegacyString(var12);
                     var19++;
                  }
               }
            }
         }

         for (HudElement var22 : DIHClient.hud().elements()) {
            String var24 = var3.getProperty("hud.pos." + var22.legacyId() + ".x");
            String var25 = var3.getProperty("hud.pos." + var22.legacyId() + ".y");
            if (var24 != null && var25 != null) {
               try {
                  var22.setPosition(Integer.parseInt(var24.trim()), Integer.parseInt(var25.trim()));
               } catch (NumberFormatException var15) {
               }
            }
         }

         String var21 = var3.getProperty("friends");
         if (var21 != null) {
            DIHClient.social().importLegacy(var21, true);
         }

         String var23 = var3.getProperty("enemies");
         if (var23 != null) {
            DIHClient.social().importLegacy(var23, false);
         }

         try {
            Files.move(var2, var2.resolveSibling(var2.getFileName() + ".migrated"), StandardCopyOption.REPLACE_EXISTING);
         } catch (IOException var14) {
         }

         DIHClient.LOG.info("[DIHClient] imported {} settings from legacy config", var19);
      }
   }

   private static String legacyName(Module var0) {
      String var1 = var0.name();

      return switch (var1) {
         case "HUD" -> "HUD";
         case "Netherite Finder" -> "Netherite Finder";
         case "Profiles" -> "Configs";
         default -> var0.name();
      };
   }

   private static JsonObject read(Path var0) throws IOException {
      JsonObject var3;
      try (BufferedReader var1 = Files.newBufferedReader(var0, StandardCharsets.UTF_8)) {
         JsonElement var2 = JsonParser.parseReader(var1);
         var3 = var2 != null && var2.isJsonObject() ? var2.getAsJsonObject() : new JsonObject();
      }

      return var3;
   }

   private static void write(Path var0, JsonObject var1) throws IOException {
      Path var2 = var0.resolveSibling(var0.getFileName() + ".tmp");
      Files.writeString(var2, GSON.toJson(var1), StandardCharsets.UTF_8);
      Files.move(var2, var0, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
   }

   private static JsonObject obj(JsonObject var0, String var1) {
      JsonElement var2 = var0.get(var1);
      return var2 != null && var2.isJsonObject() ? var2.getAsJsonObject() : null;
   }
}
