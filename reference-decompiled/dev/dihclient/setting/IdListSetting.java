package dev.dihclient.setting;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class IdListSetting extends Setting<Set<String>> {
   private final IdListSetting.Kind kind;

   public IdListSetting(String var1, String var2, IdListSetting.Kind var3, String... var4) {
      super(var1, var2, Collections.unmodifiableSet(new LinkedHashSet<>(List.of(var4))));
      this.kind = var3;
      this.value = new LinkedHashSet<>(this.defaultValue);
   }

   public IdListSetting.Kind kind() {
      return this.kind;
   }

   public boolean contains(String var1) {
      return this.value.contains(var1);
   }

   public void toggle(String var1) {
      LinkedHashSet var2 = new LinkedHashSet<>(this.value);
      if (!var2.remove(var1)) {
         var2.add(var1);
      }

      this.set(var2);
   }

   protected Set<String> sanitize(Set<String> var1) {
      if (var1 == null) {
         return null;
      } else {
         LinkedHashSet var2 = new LinkedHashSet();

         for (String var4 : var1) {
            if (var4 != null) {
               String var5 = var4.trim().toLowerCase();
               if (!var5.isEmpty()) {
                  if (!var5.contains(":")) {
                     var5 = "minecraft:" + var5;
                  }

                  var2.add(var5);
               }
            }
         }

         return var2;
      }
   }

   @Override
   public JsonElement toJson() {
      JsonArray var1 = new JsonArray();
      this.value.forEach(var1::add);
      return var1;
   }

   @Override
   public void fromJson(JsonElement var1) {
      if (var1 != null && var1.isJsonArray()) {
         LinkedHashSet var2 = new LinkedHashSet();
         JsonArray var3 = var1.getAsJsonArray();

         for (int var4 = 0; var4 < var3.size(); var4++) {
            var2.add(var3.get(var4).getAsString());
         }

         this.set(var2);
      }
   }

   @Override
   public void fromLegacyString(String var1) {
      if (var1 != null) {
         LinkedHashSet var2 = new LinkedHashSet();

         for (String var6 : var1.split(",")) {
            var2.add(var6);
         }

         this.set(var2);
      }
   }

   @Override
   public String displayValue() {
      return this.value.size() + " selected";
   }

   public static enum Kind {
      BLOCK,
      ENTITY,
      ITEM;
   }
}
