package dev.dihclient.config;

import com.google.gson.JsonObject;
import dev.dihclient.module.Category;

public final class GuiState {
   public static int windowX = -1;
   public static int windowY = -1;
   public static Category category = Category.COMBAT;

   private GuiState() {
   }

   public static void resetLayout() {
      windowX = -1;
      windowY = -1;
   }

   static JsonObject toJson() {
      JsonObject var0 = new JsonObject();
      var0.addProperty("x", windowX);
      var0.addProperty("y", windowY);
      var0.addProperty("category", category.name());
      return var0;
   }

   static void fromJson(JsonObject var0) {
      if (var0.has("x") && var0.get("x").isJsonPrimitive()) {
         windowX = var0.get("x").getAsInt();
      }

      if (var0.has("y") && var0.get("y").isJsonPrimitive()) {
         windowY = var0.get("y").getAsInt();
      }

      if (var0.has("category")) {
         try {
            category = Category.valueOf(var0.get("category").getAsString());
         } catch (Exception var2) {
         }
      }
   }
}
