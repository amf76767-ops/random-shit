package dev.dihclient.modules.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.dihclient.DIHClient;
import dev.dihclient.module.Category;
import dev.dihclient.module.Module;
import dev.dihclient.module.ModuleManager;
import dev.dihclient.setting.ActionSetting;
import dev.dihclient.setting.BoolSetting;
import dev.dihclient.setting.IntSetting;
import dev.dihclient.setting.StringSetting;
import dev.dihclient.util.Notifications;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.Disconnect;
import net.minecraft.class_642;

public class DiscordAlarm extends Module {
   public final StringSetting webhook = this.text("Webhook URL", "Discord → Channel settings → Integrations → Webhooks → Copy URL.", "", 256);
   public final BoolSetting warnings = this.bool("Warnings", "Forward warning toasts from all modules (Player Bypass, finders, notifier …).", true);
   public final BoolSetting errors = this.bool("Errors", "Forward error toasts.", true);
   public final BoolSetting alerts = this.bool("Big Alerts", "Forward the big centre alerts (usually duplicates a warning).", false);
   public final StringSetting filter = this.text("Only Modules", "Comma separated module/toast titles to forward. Empty = all.", "", 256);
   public final BoolSetting death = this.bool("On Death", "Message when you die, with coordinates.", true);
   public final BoolSetting disconnect = this.bool("On Disconnect", "Message when you get disconnected or kicked.", true);
   public final BoolSetting onlyUnfocused = this.bool("Only When Tabbed Out", "Only send while the Minecraft window is not focused.", false);
   public final BoolSetting coords = this.bool("Add Position", "Adds your current position and dimension to every message.", false);
   public final StringSetting mention = this.text("Mention", "Put in front of messages, e.g. @everyone or <@your-user-id>. Empty = none.", "", 64);
   public final IntSetting minGap = this.integer("Min Gap", "Seconds between messages; everything in between is bundled.", 3, 1, 60);
   public final ActionSetting test = this.action("Send Test", "Sends a test message to the webhook.", this::sendTest);
   private static final ExecutorService HTTP = Executors.newSingleThreadExecutor(var0 -> {
      Thread var1 = new Thread(var0, "DIHClient-Discord");
      var1.setDaemon(true);
      return var1;
   });
   private final Deque<String> queue = new ArrayDeque<>();
   private long lastSend;
   private boolean wasDead;
   private int failures;

   public DiscordAlarm() {
      super(
         "Discord Alarm", Category.CLIENT, "Sends alarms from all modules to a Discord webhook – get Player Bypass, Watchlist and finder alerts on your phone."
      );
      ClientPlayConnectionEvents.DISCONNECT.register((Disconnect)(var1, var2) -> {
         if (this.isEnabled() && this.disconnect.get()) {
            this.enqueue("\ud83d\udd0c Disconnected from " + this.serverName());
            this.flush(true);
         }
      });
   }

   public static void onToast(String var0, String var1, Notifications.Type var2) {
      DiscordAlarm var3 = instance();
      if (var3 != null
         && (var2 != Notifications.Type.WARNING || var3.warnings.get())
         && (var2 != Notifications.Type.ERROR || var3.errors.get())
         && (var2 == Notifications.Type.WARNING || var2 == Notifications.Type.ERROR)
         && var3.passesFilter(var0)) {
         var3.enqueue((var2 == Notifications.Type.ERROR ? "❗ " : "⚠️ ") + "**" + var0 + "** – " + var1);
      }
   }

   public static void onAlert(String var0) {
      DiscordAlarm var1 = instance();
      if (var1 != null && var1.alerts.get()) {
         var1.enqueue("\ud83d\udea8 **" + var0 + "**");
      }
   }

   private static DiscordAlarm instance() {
      return DIHClient.modules() != null && ModuleManager.on(DiscordAlarm.class) ? ModuleManager.of(DiscordAlarm.class) : null;
   }

   private boolean passesFilter(String var1) {
      String var2 = this.filter.get().trim();
      if (var2.isEmpty()) {
         return true;
      } else {
         String var3 = var1.toLowerCase(Locale.ROOT);

         for (String var7 : var2.split("\\s*,\\s*")) {
            if (!var7.isBlank() && var3.contains(var7.toLowerCase(Locale.ROOT))) {
               return true;
            }
         }

         return false;
      }
   }

   private String serverName() {
      class_642 var1 = mc.method_1558();
      return var1 != null ? var1.field_3761 : "singleplayer";
   }

   private String position() {
      if (mc.field_1724 != null && mc.field_1687 != null) {
         String var1 = mc.field_1687.method_27983().method_29177().method_12832();
         return " · "
            + (int)mc.field_1724.method_23317()
            + " "
            + (int)mc.field_1724.method_23318()
            + " "
            + (int)mc.field_1724.method_23321()
            + " ("
            + var1
            + ")";
      } else {
         return "";
      }
   }

   private synchronized void enqueue(String var1) {
      if (!this.webhook.get().isBlank() && (!this.onlyUnfocused.get() || !mc.method_1569())) {
         if (this.coords.get()) {
            var1 = var1 + this.position();
         }

         if (var1.length() > 1700) {
            var1 = var1.substring(0, 1700) + "…";
         }

         this.queue.addLast(var1);

         while (this.queue.size() > 20) {
            this.queue.removeFirst();
         }
      }
   }

   @Override
   public void onTick() {
      if (this.death.get()) {
         boolean var1 = mc.field_1724.method_29504() || mc.field_1724.method_6032() <= 0.0F;
         if (var1 && !this.wasDead) {
            this.enqueue(
               "\ud83d\udc80 You died at "
                  + (int)mc.field_1724.method_23317()
                  + " "
                  + (int)mc.field_1724.method_23318()
                  + " "
                  + (int)mc.field_1724.method_23321()
                  + " ("
                  + mc.field_1687.method_27983().method_29177().method_12832()
                  + ") on "
                  + this.serverName()
            );
         }

         this.wasDead = var1;
      }

      this.flush(false);
   }

   private synchronized void flush(boolean var1) {
      if (!this.queue.isEmpty()) {
         long var2 = System.currentTimeMillis();
         if (var1 || var2 - this.lastSend >= this.minGap.get().intValue() * 1000L) {
            this.lastSend = var2;
            ArrayList var4 = new ArrayList();
            int var5 = 0;

            while (!this.queue.isEmpty() && var5 + this.queue.peekFirst().length() < 1800) {
               String var6 = this.queue.removeFirst();
               var5 += var6.length() + 1;
               var4.add(var6);
            }

            String var7 = this.mention.get().isBlank() ? "" : this.mention.get().trim() + " ";
            this.post(var7 + String.join("\n", var4));
         }
      }
   }

   private void sendTest() {
      if (this.webhook.get().isBlank()) {
         Notifications.info(this.name(), "Enter a webhook URL first.");
      } else {
         this.post("✅ DIHClient test message" + this.position());
         Notifications.info(this.name(), "Test message sent.");
      }
   }

   private void post(String var1) {
      String var2 = this.webhook.get().trim();
      if (!var2.startsWith("https://discord.com/api/webhooks/")
         && !var2.startsWith("https://discordapp.com/api/webhooks/")
         && !var2.startsWith("https://ptb.discord.com/api/webhooks/")
         && !var2.startsWith("https://canary.discord.com/api/webhooks/")) {
         Notifications.info(this.name(), "Webhook URL looks wrong (must start with https://discord.com/api/webhooks/).");
      } else {
         JsonObject var3 = new JsonObject();
         var3.addProperty("username", "DIHClient");
         var3.addProperty("content", var1);
         JsonObject var4 = new JsonObject();
         JsonArray var5 = new JsonArray();
         var5.add("everyone");
         var5.add("users");
         var5.add("roles");
         var4.add("parse", var5);
         var3.add("allowed_mentions", var4);
         byte[] var6 = var3.toString().getBytes(StandardCharsets.UTF_8);
         HTTP.execute(() -> {
            String var3x = null;

            try {
               HttpURLConnection var4x = (HttpURLConnection)URI.create(var2).toURL().openConnection();
               var4x.setRequestMethod("POST");
               var4x.setConnectTimeout(8000);
               var4x.setReadTimeout(10000);
               var4x.setDoOutput(true);
               var4x.setRequestProperty("Content-Type", "application/json");
               var4x.setRequestProperty("User-Agent", "DIHClient");

               try (OutputStream var5x = var4x.getOutputStream()) {
                  var5x.write(var6);
               }

               int var12 = var4x.getResponseCode();
               if (var12 >= 300) {
                  var3x = "HTTP " + var12;
               }

               var4x.disconnect();
            } catch (Exception var10) {
               var3x = var10.getClass().getSimpleName();
            }

            if (var3x != null) {
               this.failures++;
               DIHClient.LOG.warn("[DIHClient] Discord webhook failed: {}", var3x);
               String var11 = var3x;
               if (this.failures == 1 || this.failures % 10 == 0) {
                  mc.execute(() -> Notifications.info("Discord Alarm", "Webhook failed (" + var11 + ")"));
               }
            } else {
               this.failures = 0;
            }
         });
      }
   }

   @Override
   public List<String> details() {
      return List.of(this.webhook.get().isBlank() ? "No webhook set" : "Webhook set", "Queued: " + this.queue.size());
   }
}
