package dev.dihclient.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

public class StringSetting extends Setting<String> {
   private final int maxLength;

   public StringSetting(String var1, String var2, String var3, int var4) {
      super(var1, var2, var3);
      this.maxLength = var4;
   }

   protected String sanitize(String var1) {
      if (var1 == null) {
         return "";
      } else {
         return var1.length() > this.maxLength ? var1.substring(0, this.maxLength) : var1;
      }
   }

   public int maxLength() {
      return this.maxLength;
   }

   @Override
   public JsonElement toJson() {
      return new JsonPrimitive(this.value);
   }

   @Override
   public void fromJson(JsonElement var1) {
      if (var1 != null && var1.isJsonPrimitive()) {
         this.set(var1.getAsString());
      }
   }

   @Override
   public void fromLegacyString(String var1) {
      this.set(var1);
   }

   @Override
   public String displayValue() {
      return this.value.length() > 14 ? this.value.substring(0, 13) + "…" : this.value;
   }
}
