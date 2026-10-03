package dev.dihclient.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

public class BoolSetting extends Setting<Boolean> {
   public BoolSetting(String var1, String var2, boolean var3) {
      super(var1, var2, var3);
   }

   public void toggle() {
      this.set(!this.value);
   }

   @Override
   public JsonElement toJson() {
      return new JsonPrimitive(this.value);
   }

   @Override
   public void fromJson(JsonElement var1) {
      if (var1 != null && var1.isJsonPrimitive()) {
         this.set(var1.getAsBoolean());
      }
   }

   @Override
   public void fromLegacyString(String var1) {
      if (var1 != null) {
         this.set(Boolean.parseBoolean(var1.trim()));
      }
   }

   @Override
   public String displayValue() {
      return this.value ? "ON" : "OFF";
   }
}
