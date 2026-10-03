package dev.dihclient.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import java.util.Locale;

public class ColorSetting extends Setting<Integer> {
   public ColorSetting(String var1, String var2, int var3) {
      super(var1, var2, var3);
   }

   public int red() {
      return this.value >> 16 & 0xFF;
   }

   public int green() {
      return this.value >> 8 & 0xFF;
   }

   public int blue() {
      return this.value & 0xFF;
   }

   public int alpha() {
      return this.value >>> 24 & 0xFF;
   }

   public void setChannel(int var1, int var2) {
      var2 = Math.max(0, Math.min(255, var2));

      byte var3 = switch (var1) {
         case 0 -> 16;
         case 1 -> 8;
         case 2 -> 0;
         default -> 24;
      };
      this.set(this.value & ~(255 << var3) | var2 << var3);
   }

   public int channel(int var1) {
      return switch (var1) {
         case 0 -> this.red();
         case 1 -> this.green();
         case 2 -> this.blue();
         default -> this.alpha();
      };
   }

   public int withAlpha(int var1) {
      return this.value & 16777215 | (var1 & 0xFF) << 24;
   }

   @Override
   public JsonElement toJson() {
      return new JsonPrimitive(String.format(Locale.ROOT, "%08X", this.value));
   }

   @Override
   public void fromJson(JsonElement var1) {
      if (var1 != null && var1.isJsonPrimitive()) {
         this.fromLegacyString(var1.getAsString());
      }
   }

   @Override
   public void fromLegacyString(String var1) {
      if (var1 != null) {
         String var2 = var1.trim().replace("#", "");

         try {
            long var3 = Long.parseLong(var2, 16);
            if (var2.length() <= 6) {
               var3 |= 4278190080L;
            }

            this.set((int)var3);
         } catch (NumberFormatException var5) {
         }
      }
   }

   @Override
   public String displayValue() {
      return String.format(Locale.ROOT, "#%06X", this.value & 16777215);
   }
}
