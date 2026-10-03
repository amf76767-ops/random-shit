package dev.dihclient.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import java.util.Locale;

public class DoubleSetting extends Setting<Double> {
   private final double min;
   private final double max;
   private final double step;

   public DoubleSetting(String var1, String var2, double var3, double var5, double var7, double var9) {
      super(var1, var2, var3);
      this.min = var5;
      this.max = var7;
      this.step = var9;
   }

   public double min() {
      return this.min;
   }

   public double max() {
      return this.max;
   }

   public double step() {
      return this.step;
   }

   public float getFloat() {
      return this.value.floatValue();
   }

   protected Double sanitize(Double var1) {
      if (var1 != null && !var1.isNaN()) {
         double var2 = Math.max(this.min, Math.min(this.max, var1));
         if (this.step > 0.0) {
            var2 = Math.round(var2 / this.step) * this.step;
         }

         return Math.round(var2 * 1000000.0) / 1000000.0;
      } else {
         return null;
      }
   }

   public void setFraction(double var1) {
      this.set(this.min + (this.max - this.min) * Math.max(0.0, Math.min(1.0, var1)));
   }

   public double fraction() {
      return this.max == this.min ? 0.0 : (this.value - this.min) / (this.max - this.min);
   }

   @Override
   public JsonElement toJson() {
      return new JsonPrimitive(this.value);
   }

   @Override
   public void fromJson(JsonElement var1) {
      if (var1 != null && var1.isJsonPrimitive()) {
         this.set(var1.getAsDouble());
      }
   }

   @Override
   public void fromLegacyString(String var1) {
      try {
         this.set(Double.parseDouble(var1.trim()));
      } catch (Exception var3) {
      }
   }

   @Override
   public String displayValue() {
      int var1 = this.step >= 1.0 ? 0 : (this.step >= 0.1 ? 1 : (this.step >= 0.01 ? 2 : 3));
      return String.format(Locale.ROOT, "%." + var1 + "f", this.value);
   }
}
