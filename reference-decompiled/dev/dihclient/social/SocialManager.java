package dev.dihclient.social;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.dihclient.DIHClient;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import net.minecraft.class_1657;

public final class SocialManager {
   private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
   private final Path file;
   private final Set<String> friends = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
   private final Set<String> enemies = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);

   public SocialManager(Path var1) {
      this.file = var1.resolve("social.json");
   }

   public boolean isFriend(class_1657 var1) {
      return var1 != null && this.friends.contains(name(var1));
   }

   public boolean isEnemy(class_1657 var1) {
      return var1 != null && this.enemies.contains(name(var1));
   }

   public boolean isFriend(String var1) {
      return this.friends.contains(var1);
   }

   public boolean isEnemy(String var1) {
      return this.enemies.contains(var1);
   }

   public boolean toggleFriend(String var1) {
      boolean var2;
      if (this.friends.remove(var1)) {
         var2 = false;
      } else {
         this.friends.add(var1);
         this.enemies.remove(var1);
         var2 = true;
      }

      this.save();
      return var2;
   }

   public boolean toggleEnemy(String var1) {
      boolean var2;
      if (this.enemies.remove(var1)) {
         var2 = false;
      } else {
         this.enemies.add(var1);
         this.friends.remove(var1);
         var2 = true;
      }

      this.save();
      return var2;
   }

   public Set<String> friends() {
      return Collections.unmodifiableSet(this.friends);
   }

   public Set<String> enemies() {
      return Collections.unmodifiableSet(this.enemies);
   }

   public void clearFriends() {
      this.friends.clear();
      this.save();
   }

   public void clearEnemies() {
      this.enemies.clear();
      this.save();
   }

   public void importLegacy(String var1, boolean var2) {
      for (String var6 : var1.split(",")) {
         String var7 = var6.trim();
         if (!var7.isEmpty()) {
            (var2 ? this.friends : this.enemies).add(var7);
         }
      }

      this.save();
   }

   public static String name(class_1657 var0) {
      return var0.method_7334().name();
   }

   public void load() {
      try {
         if (!Files.exists(this.file)) {
            return;
         }

         JsonObject var1 = JsonParser.parseString(Files.readString(this.file, StandardCharsets.UTF_8)).getAsJsonObject();
         if (var1.has("friends")) {
            var1.getAsJsonArray("friends").forEach(var1x -> this.friends.add(var1x.getAsString()));
         }

         if (var1.has("enemies")) {
            var1.getAsJsonArray("enemies").forEach(var1x -> this.enemies.add(var1x.getAsString()));
         }
      } catch (Exception var2) {
         DIHClient.LOG.error("[DIHClient] social load failed", var2);
      }
   }

   public void save() {
      try {
         Files.createDirectories(this.file.getParent());
         JsonObject var1 = new JsonObject();
         JsonArray var2 = new JsonArray();
         this.friends.forEach(var2::add);
         JsonArray var3 = new JsonArray();
         this.enemies.forEach(var3::add);
         var1.add("friends", var2);
         var1.add("enemies", var3);
         Files.writeString(this.file, GSON.toJson(var1), StandardCharsets.UTF_8);
      } catch (Exception var4) {
         DIHClient.LOG.error("[DIHClient] social save failed", var4);
      }
   }

   public static String lower(String var0) {
      return var0.toLowerCase(Locale.ROOT);
   }
}
