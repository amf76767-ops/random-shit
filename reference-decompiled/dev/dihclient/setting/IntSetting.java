package dev.dihclient.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

public class IntSetting extends Setting<Integer> {
   private final int min;
   private final int max;

   public IntSetting(String var1, String var2, int var3, int var4, int var5) {
      super(var1, var2, var3);
      this.min = var4;
      this.max = var5;
   }

   public int min() {
      return this.min;
   }

   public int max() {
      return this.max;
   }

   protected Integer sanitize(Integer var1) {
      return var1 == null ? null : Math.max(this.min, Math.min(this.max, var1));
   }

   public void setFraction(double var1) {
      this.set((int)Math.round(this.min + (this.max - this.min) * Math.max(0.0, Math.min(1.0, var1))));
   }

   public double fraction() {
      return this.max == this.min ? 0.0 : (double)(this.value - this.min) / (this.max - this.min);
   }

   @Override
   public JsonElement toJson() {
      return new JsonPrimitive(this.value);
   }

   @Override
   public void fromJson(JsonElement var1) {
      if (var1 != null && var1.isJsonPrimitive()) {
         this.set(var1.getAsInt());
      }
   }

   @Override
   public void fromLegacyString(String var1) {
      try {
         this.set((int)Math.round(Double.parseDouble(var1.trim())));
      } catch (Exception var3) {
      }
   }

   @Override
   public String displayValue() {
      return Integer.toString(this.value);
   }
}
