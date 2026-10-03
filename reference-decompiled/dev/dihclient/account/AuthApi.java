package dev.dihclient.account;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpClient.Redirect;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpRequest.Builder;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.StringJoiner;
import java.util.UUID;
import java.util.Map.Entry;

public final class AuthApi {
   private static final String CLIENT_ID = "00000000441cc96b";
   private static final String SCOPE = "service::user.auth.xboxlive.com::MBI_SSL";
   private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15L)).followRedirects(Redirect.NORMAL).build();

   private AuthApi() {
   }

   private static String enc(String var0) {
      return URLEncoder.encode(var0, StandardCharsets.UTF_8);
   }

   private static String form(Map<String, String> var0) {
      StringJoiner var1 = new StringJoiner("&");

      for (Entry var3 : var0.entrySet()) {
         var1.add(enc((String)var3.getKey()) + "=" + enc((String)var3.getValue()));
      }

      return var1.toString();
   }

   private static JsonObject send(HttpRequest var0) throws AuthApi.AuthException {
      try {
         HttpResponse var1 = HTTP.send(var0, BodyHandlers.ofString());
         String var2 = (String)var1.body();
         JsonElement var3 = JsonParser.parseString(var2 != null && !var2.isBlank() ? var2 : "{}");
         return var3.isJsonObject() ? var3.getAsJsonObject() : new JsonObject();
      } catch (Exception var4) {
         throw new AuthApi.AuthException("Network error: " + var4.getClass().getSimpleName());
      }
   }

   private static JsonObject postForm(String var0, Map<String, String> var1) throws AuthApi.AuthException {
      return send(
         HttpRequest.newBuilder(URI.create(var0))
            .timeout(Duration.ofSeconds(20L))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .header("Accept", "application/json")
            .POST(BodyPublishers.ofString(form(var1)))
            .build()
      );
   }

   private static JsonObject postJson(String var0, String var1, String var2) throws AuthApi.AuthException {
      Builder var3 = HttpRequest.newBuilder(URI.create(var0))
         .timeout(Duration.ofSeconds(20L))
         .header("Content-Type", "application/json")
         .header("Accept", "application/json")
         .POST(BodyPublishers.ofString(var1));
      if (var2 != null) {
         var3 = var3.header("x-xbl-contract-version", var2);
      }

      return send(var3.build());
   }

   private static String str(JsonObject var0, String var1) {
      return var0 != null && var0.has(var1) && !var0.get(var1).isJsonNull() ? var0.get(var1).getAsString() : null;
   }

   public static AuthApi.DeviceCode startDeviceCode() throws AuthApi.AuthException {
      LinkedHashMap var0 = new LinkedHashMap();
      var0.put("client_id", "00000000441cc96b");
      var0.put("scope", "service::user.auth.xboxlive.com::MBI_SSL");
      var0.put("response_type", "device_code");
      JsonObject var1 = postForm("https://login.live.com/oauth20_connect.srf", var0);
      String var2 = str(var1, "user_code");
      if (var2 == null) {
         throw new AuthApi.AuthException("Microsoft refused the login request (" + str(var1, "error") + ")");
      } else {
         String var3 = str(var1, "verification_uri");
         int var4 = var1.has("interval") ? var1.get("interval").getAsInt() : 5;
         return new AuthApi.DeviceCode(var2, str(var1, "device_code"), var3 == null ? "https://microsoft.com/link" : var3, Math.max(1, var4));
      }
   }

   public static AuthApi.Result pollDeviceCode(String var0) throws AuthApi.AuthException {
      LinkedHashMap var1 = new LinkedHashMap();
      var1.put("client_id", "00000000441cc96b");
      var1.put("grant_type", "urn:ietf:params:oauth:grant-type:device_code");
      var1.put("device_code", var0);
      JsonObject var2 = postForm("https://login.live.com/oauth20_token.srf", var1);
      String var3 = str(var2, "access_token");
      if (var3 != null) {
         return finishMicrosoft(var3, str(var2, "refresh_token"));
      } else {
         String var4 = str(var2, "error");
         if ("authorization_pending".equals(var4) || "slow_down".equals(var4)) {
            throw new AuthApi.AuthException("pending", true);
         } else if ("expired_token".equals(var4)) {
            throw new AuthApi.AuthException("The code expired – start again");
         } else if ("authorization_declined".equals(var4)) {
            throw new AuthApi.AuthException("Login was declined");
         } else {
            throw new AuthApi.AuthException("Microsoft login failed (" + var4 + ")");
         }
      }
   }

   public static AuthApi.Result loginWithRefreshToken(String var0) throws AuthApi.AuthException {
      LinkedHashMap var1 = new LinkedHashMap();
      var1.put("client_id", "00000000441cc96b");
      var1.put("grant_type", "refresh_token");
      var1.put("scope", "service::user.auth.xboxlive.com::MBI_SSL");
      var1.put("refresh_token", var0.trim());
      JsonObject var2 = postForm("https://login.live.com/oauth20_token.srf", var1);
      String var3 = str(var2, "access_token");
      if (var3 == null) {
         throw new AuthApi.AuthException("Refresh token was rejected (" + str(var2, "error") + ")");
      } else {
         String var4 = str(var2, "refresh_token");
         return finishMicrosoft(var3, var4 == null ? var0.trim() : var4);
      }
   }

   public static AuthApi.Result loginWithSessionToken(String var0) throws AuthApi.AuthException {
      String var1 = var0.trim();
      if (var1.toLowerCase().startsWith("bearer ")) {
         var1 = var1.substring(7).trim();
      }

      JsonObject var2 = profile(var1);
      return new AuthApi.Result(str(var2, "name"), dashUuid(str(var2, "id")), var1, null, null);
   }

   private static AuthApi.Result finishMicrosoft(String var0, String var1) throws AuthApi.AuthException {
      JsonObject var2 = postJson(
         "https://user.auth.xboxlive.com/user/authenticate",
         "{\"Properties\":{\"AuthMethod\":\"RPS\",\"SiteName\":\"user.auth.xboxlive.com\",\"RpsTicket\":\""
            + var0
            + "\"},\"RelyingParty\":\"http://auth.xboxlive.com\",\"TokenType\":\"JWT\"}",
         "1"
      );
      String var3 = str(var2, "Token");
      String var4 = uhs(var2);
      if (var3 != null && var4 != null) {
         JsonObject var5 = postJson(
            "https://xsts.auth.xboxlive.com/xsts/authorize",
            "{\"Properties\":{\"SandboxId\":\"RETAIL\",\"UserTokens\":[\""
               + var3
               + "\"]},\"RelyingParty\":\"rp://api.minecraftservices.com/\",\"TokenType\":\"JWT\"}",
            "1"
         );
         if (var5.has("XErr")) {
            long var11 = var5.get("XErr").getAsLong();
            if (var11 == 2148916233L) {
               throw new AuthApi.AuthException("This account has no Xbox profile yet");
            } else if (var11 == 2148916238L) {
               throw new AuthApi.AuthException("Child account – must be added to a family");
            } else {
               throw new AuthApi.AuthException("Xbox says no (XErr " + var11 + ")");
            }
         } else {
            String var6 = str(var5, "Token");
            String var7 = uhs(var5);
            if (var6 == null) {
               throw new AuthApi.AuthException("XSTS authorization failed");
            } else {
               JsonObject var8 = postJson(
                  "https://api.minecraftservices.com/authentication/login_with_xbox", "{\"identityToken\":\"XBL3.0 x=" + var7 + ";" + var6 + "\"}", null
               );
               String var9 = str(var8, "access_token");
               if (var9 == null) {
                  throw new AuthApi.AuthException("Minecraft services rejected the login");
               } else {
                  JsonObject var10 = profile(var9);
                  return new AuthApi.Result(str(var10, "name"), dashUuid(str(var10, "id")), var9, xuidFromXsts(var5), var1);
               }
            }
         }
      } else {
         throw new AuthApi.AuthException("Xbox Live login failed");
      }
   }

   private static JsonObject profile(String var0) throws AuthApi.AuthException {
      JsonObject var1 = send(
         HttpRequest.newBuilder(URI.create("https://api.minecraftservices.com/minecraft/profile"))
            .timeout(Duration.ofSeconds(20L))
            .header("Authorization", "Bearer " + var0)
            .header("Accept", "application/json")
            .GET()
            .build()
      );
      if (str(var1, "id") != null && str(var1, "name") != null) {
         return var1;
      } else if (!var1.has("error") && !var1.has("errorMessage")) {
         throw new AuthApi.AuthException("Token is invalid or expired");
      } else {
         throw new AuthApi.AuthException("No Minecraft profile: does this account own the game?");
      }
   }

   private static String uhs(JsonObject var0) {
      try {
         JsonArray var1 = var0.getAsJsonObject("DisplayClaims").getAsJsonArray("xui");
         return var1.get(0).getAsJsonObject().get("uhs").getAsString();
      } catch (Exception var2) {
         return null;
      }
   }

   private static String xuidFromXsts(JsonObject var0) {
      try {
         return var0.getAsJsonObject("DisplayClaims").getAsJsonArray("xui").get(0).getAsJsonObject().get("xid").getAsString();
      } catch (Exception var2) {
         return null;
      }
   }

   private static UUID dashUuid(String var0) {
      if (var0 == null) {
         return UUID.randomUUID();
      } else if (var0.contains("-")) {
         return UUID.fromString(var0);
      } else {
         return var0.length() == 32
            ? UUID.fromString(
               var0.substring(0, 8) + "-" + var0.substring(8, 12) + "-" + var0.substring(12, 16) + "-" + var0.substring(16, 20) + "-" + var0.substring(20)
            )
            : UUID.nameUUIDFromBytes(var0.getBytes(StandardCharsets.UTF_8));
      }
   }

   public static final class AuthException extends Exception {
      public final boolean pending;

      public AuthException(String var1, boolean var2) {
         super(var1);
         this.pending = var2;
      }

      public AuthException(String var1) {
         this(var1, false);
      }
   }

   public record DeviceCode(String userCode, String deviceCode, String url, int interval) {
   }

   public record Result(String name, UUID uuid, String mcToken, String xuid, String refreshToken) {
   }
}
