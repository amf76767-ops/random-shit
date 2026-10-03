package dev.dihclient.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

public class EnumSetting<E extends Enum<E>> extends Setting<E> {
   private final E[] constants;

   public EnumSetting(String var1, String var2, E var3) {
      super(var1, var2, (E)var3);
      this.constants = (E[])var3.getDeclaringClass().getEnumConstants();
   }

   public boolean is(E var1) {
      return this.value == var1;
   }

   public void cycle(boolean var1) {
      int var2 = this.value.ordinal() + (var1 ? 1 : -1);
      if (var2 < 0) {
         var2 = this.constants.length - 1;
      }

      if (var2 >= this.constants.length) {
         var2 = 0;
      }

      this.set(this.constants[var2]);
   }

   public E[] constants() {
      return this.constants;
   }

   @Override
   public JsonElement toJson() {
      return new JsonPrimitive(this.value.name());
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
         for (Enum var5 : this.constants) {
            if (var5.name().equalsIgnoreCase(var1.trim())) {
               this.set((E)var5);
               return;
            }
         }
      }
   }

   @Override
   public String displayValue() {
      String var1 = this.value.name().toLowerCase().replace('_', ' ');
      return Character.toUpperCase(var1.charAt(0)) + var1.substring(1);
   }
}
