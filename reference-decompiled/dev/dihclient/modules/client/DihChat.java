package dev.dihclient.modules.client;

import dev.dihclient.module.Category;
import dev.dihclient.module.Module;
import dev.dihclient.module.ModuleManager;
import dev.dihclient.setting.BoolSetting;
import dev.dihclient.setting.StringSetting;
import dev.dihclient.util.Notifications;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.LocalTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class DihChat extends Module {
   private static final String RELAY = "https://ntfy.sh/";
   public final StringSetting prefix = this.text("Prefix", "Messages starting with this go to the DIHClient chat instead of the server.", "#", 4);
   public final StringSetting channel = this.text(
      "Channel",
      "\"global\" = everyone. Any other name is a separate room – only people with the same channel name see it (use something hard to guess for a private room).",
      "global",
      32
   );
   public final BoolSetting showServer = this.bool("Show Server", "Adds the server you are on to your messages.", false);
   public final StringSetting ignore = this.text("Ignore", "Names whose messages are hidden, separated by commas.", "", 256);
   public final BoolSetting joinInfo = this.bool("Status Messages", "Shows when the chat connects / reconnects.", true);
   private static final ExecutorService SEND = Executors.newSingleThreadExecutor(var0 -> {
      Thread var1 = new Thread(var0, "DIHClient-Chat-Send");
      var1.setDaemon(true);
      return var1;
   });
   private volatile Thread reader;
   private volatile HttpURLConnection stream;
   private volatile boolean running;
   private volatile String lastId;
   private volatile boolean announced;
   private long lastSend;
   private static final ArrayDeque<String[]> HISTORY = new ArrayDeque<>();
   private static volatile boolean connected;

   public static List<String[]> history() {
      synchronized (HISTORY) {
         return new ArrayList<>(HISTORY);
      }
   }

   public static boolean connected() {
      DihChat var0 = instance();
      return var0 != null && var0.isEnabled() && connected;
   }

   public static void sendText(String var0) {
      DihChat var1 = instance();
      if (var1 != null && var1.isEnabled() && var0 != null && !var0.isBlank()) {
         var1.send(var0.trim());
      }
   }

   public DihChat() {
      super("DIHChat", Category.CLIENT, "Chat with every DIHClient user on any server: type # in front of a message. Uses the free relay ntfy.sh.");
   }

   public static DihChat instance() {
      return ModuleManager.of(DihChat.class);
   }

   private String topic() {
      String var1 = this.channel.get().trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_-]", "");
      if (var1.isEmpty()) {
         var1 = "global";
      }

      return "dihclient_chat_v1_" + var1;
   }

   public static boolean onOutgoing(String var0) {
      DihChat var1 = instance();
      if (var1 != null && var1.isEnabled() && var0 != null) {
         String var2 = var1.prefix.get();
         if (!var2.isEmpty() && var0.startsWith(var2)) {
            String var3 = var0.substring(var2.length()).trim();
            if (!var3.isEmpty()) {
               var1.send(var3);
            }

            return true;
         } else {
            return false;
         }
      } else {
         return false;
      }
   }

   private void send(String var1) {
      long var2 = System.currentTimeMillis();
      if (var2 - this.lastSend < 1500L) {
         Notifications.chat("§cDIHChat: slow down a little");
      } else {
         this.lastSend = var2;
         String var4 = mc.field_1724 == null ? "Player" : mc.field_1724.method_7334().name();
         String var5 = var1.length() > 256 ? var1.substring(0, 256) : var1;
         if (this.showServer.get() && mc.method_1558() != null) {
            var5 = var5 + " §8(" + mc.method_1558().field_3761 + ")";
         }

         String var6 = this.topic();
         String var7 = var5;
         String var8 = var4.replaceAll("[^A-Za-z0-9_]", "");
         SEND.execute(() -> {
            try {
               HttpURLConnection var3 = (HttpURLConnection)URI.create("https://ntfy.sh/" + var6).toURL().openConnection();
               var3.setRequestMethod("POST");
               var3.setConnectTimeout(8000);
               var3.setReadTimeout(8000);
               var3.setDoOutput(true);
               var3.setRequestProperty("Title", var8.isEmpty() ? "Player" : var8);
               var3.setRequestProperty("Tags", "dihclient");
               var3.setRequestProperty("Content-Type", "text/plain; charset=utf-8");

               try (OutputStream var4x = var3.getOutputStream()) {
                  var4x.write(var7.getBytes(StandardCharsets.UTF_8));
               }

               int var10 = var3.getResponseCode();
               var3.disconnect();
               if (var10 == 429) {
                  mc.execute(() -> Notifications.chat("§cDIHChat: too many messages – wait a bit (relay limit)"));
               } else if (var10 >= 300) {
                  mc.execute(() -> Notifications.chat("§cDIHChat: could not send (HTTP " + var10 + ")"));
               }
            } catch (Exception var9) {
               mc.execute(() -> Notifications.chat("§cDIHChat: could not send – no connection to the relay"));
            }
         });
      }
   }

   @Override
   protected void onEnable() {
      this.running = true;
      this.announced = false;
      this.lastId = null;
      Thread var1 = new Thread(this::readLoop, "DIHClient-Chat");
      var1.setDaemon(true);
      this.reader = var1;
      var1.start();
   }

   @Override
   protected void onDisable() {
      this.running = false;
      HttpURLConnection var1 = this.stream;
      if (var1 != null) {
         try {
            var1.disconnect();
         } catch (Exception var3) {
         }
      }

      Thread var2 = this.reader;
      if (var2 != null) {
         var2.interrupt();
      }

      this.reader = null;
   }

   private boolean alive() {
      return this.running && this.reader == Thread.currentThread();
   }

   private void readLoop() {
      int var1 = 0;
      String var2 = this.topic();

      while (this.alive()) {
         try {
            String var3 = this.lastId != null ? this.lastId : "5m";
            HttpURLConnection var4 = (HttpURLConnection)URI.create("https://ntfy.sh/" + var2 + "/json?since=" + var3).toURL().openConnection();
            var4.setConnectTimeout(10000);
            var4.setReadTimeout(120000);
            this.stream = var4;

            label82:
            try (BufferedReader var5 = new BufferedReader(new InputStreamReader(var4.getInputStream(), StandardCharsets.UTF_8))) {
               if (this.alive()) {
                  connected = true;
                  if (!this.announced && this.joinInfo.get()) {
                     this.announced = true;
                     String var6 = this.prefix.get();
                     mc.execute(() -> Notifications.chat("§aDIHChat connected§7 – type §f" + var6 + "message§7 to talk to all DIHClient users"));
                  }

                  var1 = 0;

                  while (true) {
                     String var12;
                     if (!this.alive() || (var12 = var5.readLine()) == null) {
                        break label82;
                     }

                     if (!this.topic().equals(var2)) {
                        this.lastId = null;
                        var1 = -1;
                        break label82;
                     }

                     this.handle(var12);
                  }
               }
               break;
            }
         } catch (Exception var11) {
         }

         if (!this.alive()) {
            break;
         }

         connected = false;
         var2 = this.topic();
         if (var1 < 0) {
            var1 = 0;
         } else {
            if (++var1 == 3 && this.joinInfo.get()) {
               mc.execute(() -> Notifications.chat("§cDIHChat: relay not reachable – retrying"));
            }

            try {
               Thread.sleep(Math.min(60000L, 5000L * var1));
            } catch (InterruptedException var9) {
               break;
            }

            var2 = this.topic();
         }
      }
   }

   private void handle(String var1) {
      if (var1.contains("\"event\":\"message\"")) {
         String var2 = field(var1, "id");
         String var3 = field(var1, "title");
         String var4 = field(var1, "message");
         if (var2 != null) {
            this.lastId = var2;
         }

         if (var4 != null) {
            String var5 = clean(var3 == null ? "?" : var3, 16);
            String var6 = clean(var4, 300);

            for (String var10 : this.ignore.get().split(",")) {
               if (!var10.isBlank() && var10.trim().equalsIgnoreCase(var5)) {
                  return;
               }
            }

            synchronized (HISTORY) {
               HISTORY.addLast(new String[]{LocalTime.now().withNano(0).withSecond(0).toString(), var5, var6});

               while (HISTORY.size() > 100) {
                  HISTORY.removeFirst();
               }
            }

            mc.execute(() -> Notifications.chat("§d✉ §e" + var5 + "§7: §f" + var6));
         }
      }
   }

   private static String clean(String var0, int var1) {
      StringBuilder var2 = new StringBuilder();

      for (int var3 = 0; var3 < var0.length() && var2.length() < var1; var3++) {
         char var4 = var0.charAt(var3);
         var2.append(var4 < ' ' ? ' ' : var4);
      }

      return var2.toString();
   }

   static String field(String var0, String var1) {
      String var2 = "\"" + var1 + "\":\"";
      int var3 = var0.indexOf(var2);
      if (var3 < 0) {
         return null;
      } else {
         StringBuilder var4 = new StringBuilder();

         for (int var5 = var3 + var2.length(); var5 < var0.length(); var5++) {
            char var6 = var0.charAt(var5);
            if (var6 == '"') {
               return var4.toString();
            }

            if (var6 == '\\' && var5 + 1 < var0.length()) {
               char var7 = var0.charAt(++var5);
               switch (var7) {
                  case 'b':
                     var4.append('\b');
                     break;
                  case 'c':
                  case 'd':
                  case 'e':
                  case 'g':
                  case 'h':
                  case 'i':
                  case 'j':
                  case 'k':
                  case 'l':
                  case 'm':
                  case 'o':
                  case 'p':
                  case 'q':
                  case 's':
                  default:
                     var4.append(var7);
                     break;
                  case 'f':
                     var4.append('\f');
                     break;
                  case 'n':
                     var4.append('\n');
                     break;
                  case 'r':
                     var4.append('\r');
                     break;
                  case 't':
                     var4.append('\t');
                     break;
                  case 'u':
                     if (var5 + 4 < var0.length()) {
                        try {
                           var4.append((char)Integer.parseInt(var0.substring(var5 + 1, var5 + 5), 16));
                        } catch (NumberFormatException var9) {
                        }

                        var5 += 4;
                     }
               }
            } else {
               var4.append(var6);
            }
         }

         return null;
      }
   }

   @Override
   public List<String> details() {
      ArrayList var1 = new ArrayList();
      var1.add("Channel: " + this.channel.get() + " · prefix " + this.prefix.get());
      return var1;
   }
}
