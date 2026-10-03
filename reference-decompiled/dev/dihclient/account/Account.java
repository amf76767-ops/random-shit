package dev.dihclient.account;

import com.google.gson.JsonObject;
import java.util.UUID;

public final class Account {
   public Account.Kind kind;
   public String name = "?";
   public UUID uuid = UUID.randomUUID();
   public String secret = "";
   public String mcToken;
   public String xuid;

   public Account() {
   }

   public Account(Account.Kind var1) {
      this.kind = var1;
   }

   public static Account fromResult(Account.Kind var0, AuthApi.Result var1) {
      Account var2 = new Account(var0);
      var2.apply(var1);
      return var2;
   }

   public void apply(AuthApi.Result var1) {
      this.name = var1.name();
      this.uuid = var1.uuid();
      this.mcToken = var1.mcToken();
      this.xuid = var1.xuid();
      if (this.kind == Account.Kind.SESSION) {
         this.secret = var1.mcToken();
      } else if (var1.refreshToken() != null) {
         this.secret = var1.refreshToken();
      }
   }

   public AuthApi.Result resolve() throws AuthApi.AuthException {
      return switch (this.kind) {
         case MICROSOFT, REFRESH -> AuthApi.loginWithRefreshToken(this.secret);
         case SESSION -> AuthApi.loginWithSessionToken(this.secret);
      };
   }

   public String label() {
      return switch (this.kind) {
         case MICROSOFT -> "Microsoft";
         case REFRESH -> "Refresh token";
         case SESSION -> "Session token";
      };
   }

   public JsonObject toJson() {
      JsonObject var1 = new JsonObject();
      var1.addProperty("kind", this.kind.name());
      var1.addProperty("name", this.name);
      var1.addProperty("uuid", this.uuid.toString());
      var1.addProperty("secret", this.secret);
      if (this.xuid != null) {
         var1.addProperty("xuid", this.xuid);
      }

      return var1;
   }

   public static Account fromJson(JsonObject var0) {
      Account var1 = new Account(Account.Kind.valueOf(var0.get("kind").getAsString()));
      var1.name = var0.has("name") ? var0.get("name").getAsString() : "?";

      try {
         var1.uuid = UUID.fromString(var0.get("uuid").getAsString());
      } catch (Exception var3) {
      }

      var1.secret = var0.has("secret") ? var0.get("secret").getAsString() : "";
      var1.xuid = var0.has("xuid") ? var0.get("xuid").getAsString() : null;
      return var1;
   }

   public static enum Kind {
      MICROSOFT,
      REFRESH,
      SESSION;
   }
}
