package dev.dihclient.modules.world;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.dihclient.waypoint.WaypointManager;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.class_2338;

public final class DangerMemory {
   private static final List<DangerMemory.Spot> all = new ArrayList<>();
   private static boolean loaded;

   private DangerMemory() {
   }

   private static Path file() {
      return FabricLoader.getInstance().getGameDir().resolve("dihclient").resolve("dangers.json");
   }

   private static void load() {
      if (!loaded) {
         loaded = true;

         try {
            Path var0 = file();
            if (Files.exists(var0)) {
               JsonElement var1 = JsonParser.parseString(Files.readString(var0));
               if (var1.isJsonArray()) {
                  JsonArray var2 = var1.getAsJsonArray();

                  for (int var3 = 0; var3 < var2.size(); var3++) {
                     try {
                        JsonObject var4 = var2.get(var3).getAsJsonObject();
                        DangerMemory.Spot var5 = new DangerMemory.Spot();
                        var5.world = var4.get("world").getAsString();
                        var5.dim = var4.get("dim").getAsString();
                        var5.x = var4.get("x").getAsInt();
                        var5.y = var4.get("y").getAsInt();
                        var5.z = var4.get("z").getAsInt();
                        var5.cause = var4.has("cause") ? var4.get("cause").getAsString() : "?";
                        var5.weight = var4.has("weight") ? var4.get("weight").getAsInt() : 1;
                        var5.time = var4.has("time") ? var4.get("time").getAsLong() : 0L;
                        all.add(var5);
                     } catch (Exception var6) {
                     }
                  }
               }
            }
         } catch (Exception var7) {
         }
      }
   }

   private static void save() {
      try {
         JsonArray var0 = new JsonArray();

         for (DangerMemory.Spot var2 : all) {
            JsonObject var3 = new JsonObject();
            var3.addProperty("world", var2.world);
            var3.addProperty("dim", var2.dim);
            var3.addProperty("x", var2.x);
            var3.addProperty("y", var2.y);
            var3.addProperty("z", var2.z);
            var3.addProperty("cause", var2.cause);
            var3.addProperty("weight", var2.weight);
            var3.addProperty("time", var2.time);
            var0.add(var3);
         }

         Path var5 = file();
         Files.createDirectories(var5.getParent());
         Files.writeString(var5, new GsonBuilder().setPrettyPrinting().create().toJson(var0));
      } catch (Exception var4) {
      }
   }

   public static synchronized void record(class_2338 var0, String var1, int var2) {
      load();
      String var3 = WaypointManager.worldKey();
      String var4 = WaypointManager.dimKey();

      for (DangerMemory.Spot var6 : all) {
         if (var6.world.equals(var3)
            && var6.dim.equals(var4)
            && Math.abs(var6.x - var0.method_10263()) <= 2
            && Math.abs(var6.y - var0.method_10264()) <= 2
            && Math.abs(var6.z - var0.method_10260()) <= 2) {
            var6.weight = Math.min(10, var6.weight + var2);
            var6.cause = var1;
            var6.time = System.currentTimeMillis();
            save();
            return;
         }
      }

      DangerMemory.Spot var7 = new DangerMemory.Spot();
      var7.world = var3;
      var7.dim = var4;
      var7.x = var0.method_10263();
      var7.y = var0.method_10264();
      var7.z = var0.method_10260();
      var7.cause = var1;
      var7.weight = var2;
      var7.time = System.currentTimeMillis();
      all.add(var7);
      if (all.size() > 2000) {
         all.remove(0);
      }

      save();
   }

   public static synchronized List<DangerMemory.Spot> near(class_2338 var0, double var1) {
      load();
      String var3 = WaypointManager.worldKey();
      String var4 = WaypointManager.dimKey();
      ArrayList var5 = new ArrayList();
      double var6 = var1 * var1;

      for (DangerMemory.Spot var9 : all) {
         if (var9.world.equals(var3) && var9.dim.equals(var4)) {
            double var10 = var9.x - var0.method_10263();
            double var12 = var9.y - var0.method_10264();
            double var14 = var9.z - var0.method_10260();
            if (var10 * var10 + var12 * var12 + var14 * var14 <= var6) {
               var5.add(var9);
            }
         }
      }

      return var5;
   }

   public static synchronized int forgetHere() {
      load();
      String var0 = WaypointManager.worldKey();
      int var1 = all.size();
      all.removeIf(var1x -> var1x.world.equals(var0));
      save();
      return var1 - all.size();
   }

   public static synchronized int count() {
      load();
      String var0 = WaypointManager.worldKey();
      int var1 = 0;

      for (DangerMemory.Spot var3 : all) {
         if (var3.world.equals(var0)) {
            var1++;
         }
      }

      return var1;
   }

   public static final class Spot {
      public String world;
      public String dim;
      public int x;
      public int y;
      public int z;
      public String cause;
      public int weight;
      public long time;
   }
}
