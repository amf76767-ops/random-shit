package dev.dihclient.account;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import dev.dihclient.DIHClient;
import dev.dihclient.util.Notifications;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.class_310;

public final class AccountManager {
   private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
   private final List<Account> accounts = new ArrayList<>();
   private volatile String status = "";
   private volatile boolean busy;
   private AuthApi.DeviceCode pendingCode;
   private Thread deviceThread;
   private static AccountManager instance;

   public AccountManager() {
      this.load();
   }

   public static synchronized AccountManager get() {
      if (instance == null) {
         instance = new AccountManager();
      }

      return instance;
   }

   public List<Account> accounts() {
      return this.accounts;
   }

   public String status() {
      return this.status;
   }

   public boolean busy() {
      return this.busy;
   }

   public AuthApi.DeviceCode pendingCode() {
      return this.pendingCode;
   }

   private static Path file() {
      return FabricLoader.getInstance().getGameDir().resolve("dihclient").resolve("accounts.json");
   }

   public void remove(Account var1) {
      this.accounts.remove(var1);
      this.save();
   }

   public void add(Account var1) {
      for (int var2 = 0; var2 < this.accounts.size(); var2++) {
         if (this.accounts.get(var2).uuid.equals(var1.uuid) && this.accounts.get(var2).kind == var1.kind) {
            this.accounts.set(var2, var1);
            this.save();
            return;
         }
      }

      this.accounts.add(var1);
      this.save();
   }

   public void login(Account var1) {
      if (!this.busy) {
         this.busy = true;
         this.status = "Logging in as " + var1.name + " …";
         Thread var2 = new Thread(() -> {
            try {
               AuthApi.Result var2x = var1.resolve();
               var1.apply(var2x);
               this.save();
               this.applyOnClient(var2x, var1.label());
            } catch (AuthApi.AuthException var6) {
               this.status = "Login failed: " + var6.getMessage();
               Notifications.error("Account", var6.getMessage());
            } finally {
               this.busy = false;
            }
         }, "DIH-Account-Login");
         var2.setDaemon(true);
         var2.start();
      }
   }

   public void addFromToken(Account.Kind var1, String var2) {
      if (!this.busy && var2 != null && !var2.isBlank()) {
         this.busy = true;
         this.status = "Checking token …";
         Thread var3 = new Thread(() -> {
            try {
               AuthApi.Result var3x = var1 == Account.Kind.SESSION ? AuthApi.loginWithSessionToken(var2) : AuthApi.loginWithRefreshToken(var2);
               Account var4 = Account.fromResult(var1, var3x);
               this.add(var4);
               this.applyOnClient(var3x, var4.label());
            } catch (AuthApi.AuthException var8) {
               this.status = "Failed: " + var8.getMessage();
               Notifications.error("Account", var8.getMessage());
            } finally {
               this.busy = false;
            }
         }, "DIH-Account-Token");
         var3.setDaemon(true);
         var3.start();
      }
   }

   public void startMicrosoft() {
      if (!this.busy) {
         this.busy = true;
         this.pendingCode = null;
         this.status = "Contacting Microsoft …";
         this.deviceThread = new Thread(() -> {
            try {
               AuthApi.DeviceCode var1 = AuthApi.startDeviceCode();
               this.pendingCode = var1;
               this.status = "Enter code " + var1.userCode() + " at " + var1.url();
               long var2 = System.currentTimeMillis() + 900000L;

               while (System.currentTimeMillis() < var2 && !Thread.currentThread().isInterrupted()) {
                  Thread.sleep(Math.max(1, var1.interval()) * 1000L);

                  try {
                     AuthApi.Result var4 = AuthApi.pollDeviceCode(var1.deviceCode());
                     Account var5 = Account.fromResult(Account.Kind.MICROSOFT, var4);
                     this.add(var5);
                     this.applyOnClient(var4, "Microsoft");
                     return;
                  } catch (AuthApi.AuthException var10) {
                     if (!var10.pending) {
                        this.status = "Failed: " + var10.getMessage();
                        Notifications.error("Account", var10.getMessage());
                        return;
                     }
                  }
               }

               this.status = "Login timed out";
            } catch (Exception var11) {
               this.status = "Failed: " + var11.getMessage();
            } finally {
               this.busy = false;
               this.pendingCode = null;
            }
         }, "DIH-Account-MS");
         this.deviceThread.setDaemon(true);
         this.deviceThread.start();
      }
   }

   public void cancel() {
      if (this.deviceThread != null) {
         this.deviceThread.interrupt();
      }

      this.busy = false;
      this.pendingCode = null;
      this.status = "Cancelled";
   }

   private void applyOnClient(AuthApi.Result var1, String var2) {
      class_310.method_1551().execute(() -> {
         String var3 = SessionApplier.apply(var1.name(), var1.uuid(), var1.mcToken(), var1.xuid());
         if (var3 == null) {
            this.status = "Logged in as " + var1.name() + " (" + var2 + ")";
            Notifications.info("Account", "Now playing as " + var1.name());
         } else {
            this.status = "Got the token, but could not swap session: " + var3;
            Notifications.warn("Account", "Session swap failed: " + var3);
         }
      });
   }

   public void load() {
      this.accounts.clear();

      try {
         Path var1 = file();
         if (Files.exists(var1)) {
            JsonElement var2 = JsonParser.parseString(Files.readString(var1));
            JsonArray var3 = var2.isJsonArray() ? var2.getAsJsonArray() : var2.getAsJsonObject().getAsJsonArray("accounts");
            if (var3 != null) {
               for (int var4 = 0; var4 < var3.size(); var4++) {
                  JsonElement var5 = var3.get(var4);

                  try {
                     this.accounts.add(Account.fromJson(var5.getAsJsonObject()));
                  } catch (Exception var7) {
                  }
               }
            }
         }
      } catch (Exception var8) {
         DIHClient.LOG.warn("[DIHClient] could not read accounts", var8);
      }
   }

   public void save() {
      try {
         Path var1 = file();
         Files.createDirectories(var1.getParent());
         JsonArray var2 = new JsonArray();

         for (Account var4 : this.accounts) {
            var2.add(var4.toJson());
         }

         Files.writeString(var1, GSON.toJson(var2));
      } catch (Exception var5) {
         DIHClient.LOG.warn("[DIHClient] could not save accounts", var5);
      }
   }
}
