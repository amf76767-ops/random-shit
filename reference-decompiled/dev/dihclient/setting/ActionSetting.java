package dev.dihclient.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonNull;

public class ActionSetting extends Setting<Boolean> {
   private final Runnable action;

   public ActionSetting(String var1, String var2, Runnable var3) {
      super(var1, var2, false);
      this.action = var3;
   }

   public void run() {
      this.action.run();
   }

   @Override
   public JsonElement toJson() {
      return JsonNull.INSTANCE;
   }

   @Override
   public void fromJson(JsonElement var1) {
   }

   @Override
   public void fromLegacyString(String var1) {
   }

   @Override
   public String displayValue() {
      return "RUN";
   }
}
