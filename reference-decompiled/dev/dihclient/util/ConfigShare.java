package dev.dihclient.util;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.dihclient.DIHClient;
import dev.dihclient.module.Module;
import dev.dihclient.setting.ActionSetting;
import dev.dihclient.setting.Setting;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.Locale;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.class_310;

public final class ConfigShare {
   private static final class_310 mc = class_310.method_1551();
   private static final String PREFIX = "DIH1:";

   private ConfigShare() {
   }

   private static boolean secret(Setting<?> var0) {
      String var1 = var0.name().toLowerCase(Locale.ROOT);
      return var1.contains("webhook") || var1.contains("token") || var1.contains("password") || var1.contains("channel");
   }

   public static JsonObject toJson() {
      JsonObject var0 = new JsonObject();
      var0.addProperty("dihclient", "5.3");
      JsonObject var1 = new JsonObject();

      for (Module var3 : DIHClient.modules().all()) {
         JsonObject var4 = new JsonObject();
         if (var3.isToggleable()) {
            var4.addProperty("enabled", var3.isEnabled());
         }

         JsonObject var5 = new JsonObject();

         for (Setting var7 : var3.settings()) {
            if (!(var7 instanceof ActionSetting) && !secret(var7) && var7.get() != null && !var7.get().equals(var7.defaultValue())) {
               var5.add(var7.id(), var7.toJson());
            }
         }

         if (var5.size() > 0 || var3.isEnabled()) {
            var4.add("settings", var5);
            var1.add(var3.id(), var4);
         }
      }

      var0.add("modules", var1);
      return var0;
   }

   public static int apply(JsonObject var0, boolean var1) {
      if (var0 != null && var0.has("modules")) {
         JsonObject var2 = var0.getAsJsonObject("modules");
         int var3 = 0;

         for (Module var5 : DIHClient.modules().all()) {
            if (var2.has(var5.id())) {
               JsonObject var6 = var2.getAsJsonObject(var5.id());
               if (var6.has("settings")) {
                  JsonObject var7 = var6.getAsJsonObject("settings");

                  for (Setting var9 : var5.settings()) {
                     JsonElement var10 = var7.get(var9.id());
                     if (var10 != null && !secret(var9)) {
                        try {
                           var9.fromJson(var10);
                           var3++;
                        } catch (Exception var12) {
                        }
                     }
                  }
               }

               if (var1 && var6.has("enabled") && var5.isToggleable()) {
                  var5.setEnabled(var6.get("enabled").getAsBoolean());
               }
            }
         }

         DIHClient.config().save();
         return var3;
      } else {
         return -1;
      }
   }

   public static String encode(JsonObject var0) throws Exception {
      ByteArrayOutputStream var1 = new ByteArrayOutputStream();

      try (GZIPOutputStream var2 = new GZIPOutputStream(var1)) {
         var2.write(var0.toString().getBytes(StandardCharsets.UTF_8));
      }

      return "DIH1:" + Base64.getEncoder().encodeToString(var1.toByteArray());
   }

   public static JsonObject decode(String var0) throws Exception {
      String var1 = var0.trim();
      if (var1.startsWith("{")) {
         return JsonParser.parseString(var1).getAsJsonObject();
      } else if (!var1.startsWith("DIH1:")) {
         return null;
      } else {
         byte[] var2 = Base64.getDecoder().decode(var1.substring("DIH1:".length()).replaceAll("\\s", ""));

         JsonObject var4;
         try (GZIPInputStream var3 = new GZIPInputStream(new ByteArrayInputStream(var2))) {
            var4 = JsonParser.parseString(new String(var3.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
         }

         return var4;
      }
   }

   public static Path dir() {
      return FabricLoader.getInstance().getGameDir().resolve("dihclient").resolve("shared");
   }

   public static void export() {
      try {
         JsonObject var0 = toJson();
         String var1 = encode(var0);
         mc.field_1774.method_1455(var1);
         Files.createDirectories(dir());
         Path var2 = dir().resolve("config-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")) + ".json");
         Files.writeString(var2, var0.toString(), StandardCharsets.UTF_8);
         Notifications.push("Config", "Code copied (" + var1.length() + " characters) · file " + var2.getFileName(), Notifications.Type.SUCCESS);
      } catch (Exception var3) {
         Notifications.error("Config", "Export failed: " + var3.getMessage());
      }
   }

   public static void importClipboard(boolean var0) {
      try {
         String var1 = mc.field_1774.method_1460();
         JsonObject var2 = var1 == null ? null : decode(var1);
         int var3 = apply(var2, var0);
         if (var3 < 0) {
            Notifications.warn("Config", "No DIHClient config in the clipboard (copy a DIH1:… code first)");
         } else {
            Notifications.push("Config", "Imported " + var3 + " settings", Notifications.Type.SUCCESS);
         }
      } catch (Exception var4) {
         Notifications.error("Config", "Import failed: " + var4.getMessage());
      }
   }
}
