package dev.dihclient.waypoint;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.dihclient.DIHClient;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.class_310;
import net.minecraft.class_642;

public final class WaypointManager {
   private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
   private static WaypointManager instance;
   private final List<WaypointManager.Waypoint> all = new ArrayList<>();
   private boolean loadFailed;

   private WaypointManager() {
      this.load();
   }

   public static synchronized WaypointManager get() {
      if (instance == null) {
         instance = new WaypointManager();
      }

      return instance;
   }

   public static String worldKey() {
      class_310 var0 = class_310.method_1551();
      class_642 var1 = var0.method_1558();
      return var1 != null && var1.field_3761 != null && !var0.method_1542() ? var1.field_3761.toLowerCase(Locale.ROOT) : "singleplayer";
   }

   public static String dimKey() {
      class_310 var0 = class_310.method_1551();
      return var0.field_1687 == null ? "minecraft:overworld" : var0.field_1687.method_27983().method_29177().toString();
   }

   public List<WaypointManager.Waypoint> here() {
      String var1 = worldKey();
      ArrayList var2 = new ArrayList();

      for (WaypointManager.Waypoint var4 : this.all) {
         if (var4.world.equals(var1)) {
            var2.add(var4);
         }
      }

      return var2;
   }

   public WaypointManager.Waypoint add(String var1, int var2, int var3, int var4, int var5) {
      WaypointManager.Waypoint var6 = new WaypointManager.Waypoint(var1, worldKey(), dimKey(), var2, var3, var4, var5);
      this.all.add(var6);
      this.save();
      return var6;
   }

   public void remove(WaypointManager.Waypoint var1) {
      this.all.remove(var1);
      this.save();
   }

   public void trimDeaths(int var1) {
      ArrayList var2 = new ArrayList();

      for (WaypointManager.Waypoint var4 : this.here()) {
         if (var4.death) {
            var2.add(var4);
         }
      }

      var2.sort((var0, var1x) -> Long.compare(var1x.created, var0.created));

      for (int var5 = var1; var5 < var2.size(); var5++) {
         this.all.remove(var2.get(var5));
      }

      this.save();
   }

   public String nextName() {
      int var1 = 1;

      while (true) {
         String var2 = "Waypoint " + var1;
         boolean var3 = false;

         for (WaypointManager.Waypoint var5 : this.here()) {
            if (var5.name.equalsIgnoreCase(var2)) {
               var3 = true;
               break;
            }
         }

         if (!var3) {
            return var2;
         }

         var1++;
      }
   }

   private static Path file() {
      return FabricLoader.getInstance().getGameDir().resolve("dihclient").resolve("waypoints.json");
   }

   public void load() {
      this.all.clear();

      try {
         Path var1 = file();
         if (Files.exists(var1)) {
            JsonElement var2 = JsonParser.parseString(Files.readString(var1));
            if (var2.isJsonArray()) {
               JsonArray var3 = var2.getAsJsonArray();

               for (int var4 = 0; var4 < var3.size(); var4++) {
                  try {
                     this.all.add(WaypointManager.Waypoint.fromJson(var3.get(var4).getAsJsonObject()));
                  } catch (Exception var7) {
                  }
               }
            }
         }
      } catch (Exception var8) {
         DIHClient.LOG.warn("[DIHClient] could not read waypoints", var8);
         this.loadFailed = true;

         try {
            Files.copy(file(), file().resolveSibling("waypoints.broken-" + System.currentTimeMillis() + ".json"));
         } catch (Exception var6) {
         }
      }
   }

   public void save() {
      try {
         Path var1 = file();
         Files.createDirectories(var1.getParent());
         JsonArray var2 = new JsonArray();

         for (WaypointManager.Waypoint var4 : this.all) {
            var2.add(var4.toJson());
         }

         Path var7 = var1.resolveSibling("waypoints.json.tmp");
         Files.writeString(var7, GSON.toJson(var2));

         try {
            Files.move(var7, var1, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
         } catch (AtomicMoveNotSupportedException var5) {
            Files.move(var7, var1, StandardCopyOption.REPLACE_EXISTING);
         }

         this.loadFailed = false;
      } catch (Exception var6) {
         DIHClient.LOG.warn("[DIHClient] could not save waypoints", var6);
      }
   }

   public static final class Waypoint {
      public String name;
      public String world;
      public String dim;
      public int x;
      public int y;
      public int z;
      public int color;
      public boolean visible = true;
      public boolean death;
      public long created;

      public Waypoint(String var1, String var2, String var3, int var4, int var5, int var6, int var7) {
         this.name = var1;
         this.world = var2;
         this.dim = var3;
         this.x = var4;
         this.y = var5;
         this.z = var6;
         this.color = var7;
         this.created = System.currentTimeMillis();
      }

      JsonObject toJson() {
         JsonObject var1 = new JsonObject();
         var1.addProperty("name", this.name);
         var1.addProperty("world", this.world);
         var1.addProperty("dim", this.dim);
         var1.addProperty("x", this.x);
         var1.addProperty("y", this.y);
         var1.addProperty("z", this.z);
         var1.addProperty("color", this.color);
         var1.addProperty("visible", this.visible);
         var1.addProperty("death", this.death);
         var1.addProperty("created", this.created);
         return var1;
      }

      static WaypointManager.Waypoint fromJson(JsonObject var0) {
         WaypointManager.Waypoint var1 = new WaypointManager.Waypoint(
            var0.get("name").getAsString(),
            var0.get("world").getAsString(),
            var0.get("dim").getAsString(),
            var0.get("x").getAsInt(),
            var0.get("y").getAsInt(),
            var0.get("z").getAsInt(),
            var0.has("color") ? var0.get("color").getAsInt() : -16711681
         );
         var1.visible = !var0.has("visible") || var0.get("visible").getAsBoolean();
         var1.death = var0.has("death") && var0.get("death").getAsBoolean();
         var1.created = var0.has("created") ? var0.get("created").getAsLong() : 0L;
         return var1;
      }
   }
}
