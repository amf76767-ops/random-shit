package dev.dihclient.util;

import dev.dihclient.DIHClient;
import dev.dihclient.module.Module;
import dev.dihclient.setting.ActionSetting;
import dev.dihclient.setting.Setting;
import dev.dihclient.waypoint.WaypointManager;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.class_310;

public final class BugReport {
   public static final String VERSION = "5.3";
   private static final class_310 mc = class_310.method_1551();
   private static final ArrayDeque<String> ERRORS = new ArrayDeque<>();

   private BugReport() {
   }

   public static void error(String var0, Throwable var1) {
      StringWriter var2 = new StringWriter();
      var1.printStackTrace(new PrintWriter(var2));
      String var3 = var2.toString();
      String[] var4 = var3.split("\n");
      StringBuilder var5 = new StringBuilder(LocalTime.now().withNano(0) + " " + var0 + "\n");

      for (int var6 = 0; var6 < Math.min(var4.length, 14); var6++) {
         var5.append("    ").append(var4[var6].trim()).append('\n');
      }

      synchronized (ERRORS) {
         if (ERRORS.isEmpty() || !ERRORS.peekLast().substring(9).equals(var5.substring(9))) {
            ERRORS.addLast(var5.toString());

            while (ERRORS.size() > 20) {
               ERRORS.removeFirst();
            }
         }
      }
   }

   private static boolean secret(Setting<?> var0) {
      String var1 = var0.name().toLowerCase(Locale.ROOT);
      return var1.contains("webhook") || var1.contains("token") || var1.contains("password") || var1.contains("channel");
   }

   public static Path write() {
      StringBuilder var0 = new StringBuilder();
      var0.append("DIHClient bug report\n====================\n");
      var0.append("Client: DIHClient v").append("5.3").append('\n');
      var0.append("Time: ").append(LocalDateTime.now().withNano(0)).append('\n');
      var0.append("Java: ").append(System.getProperty("java.version")).append(" · OS: ").append(System.getProperty("os.name")).append('\n');

      try {
         var0.append("Mods: ")
            .append(FabricLoader.getInstance().isModLoaded("sodium") ? "sodium " : "")
            .append(FabricLoader.getInstance().isModLoaded("iris") ? "iris " : "")
            .append(FabricLoader.getInstance().isModLoaded("meteor-client") ? "meteor " : "")
            .append('\n');
      } catch (Throwable var10) {
      }

      if (mc.field_1724 != null) {
         var0.append("Server: ").append(mc.method_1558() == null ? "singleplayer" : mc.method_1558().field_3761).append('\n');
         var0.append(
            String.format(
               Locale.ROOT,
               "Position: %.1f %.1f %.1f in %s%n",
               mc.field_1724.method_23317(),
               mc.field_1724.method_23318(),
               mc.field_1724.method_23321(),
               WaypointManager.dimKey()
            )
         );
         var0.append("Screen: ").append(mc.field_1755 == null ? "none" : mc.field_1755.getClass().getSimpleName()).append('\n');
         var0.append("Balance: ").append(Money.known() ? Money.format(Money.balance()) + " (" + Money.source() + ")" : "unknown").append('\n');
      } else {
         var0.append("Not in a world\n");
      }

      var0.append("\n--- Modules that are on ---\n");

      for (Module var2 : DIHClient.modules().all()) {
         if (var2.isEnabled() && var2.isToggleable()) {
            String var3 = null;

            try {
               var3 = var2.getInfo();
            } catch (Throwable var9) {
            }

            var0.append("* ").append(var2.name()).append(var3 == null ? "" : " [" + var3 + "]").append('\n');

            try {
               List var4 = var2.details();
               if (var4 != null) {
                  for (String var6 : var4) {
                     var0.append("    ").append(Money.strip(var6)).append('\n');
                  }
               }
            } catch (Throwable var13) {
            }
         }
      }

      var0.append("\n--- Changed settings ---\n");

      for (Module var19 : DIHClient.modules().all()) {
         StringBuilder var24 = new StringBuilder();

         for (Setting var30 : var19.settings()) {
            if (!(var30 instanceof ActionSetting) && var30.get() != null && !var30.get().equals(var30.defaultValue())) {
               var24.append("    ").append(var30.name()).append(" = ").append(secret(var30) ? "(hidden)" : var30.displayValue()).append('\n');
            }
         }

         if (var24.length() > 0) {
            var0.append(var19.name()).append(var19.isEnabled() ? " (on)" : "").append('\n').append((CharSequence)var24);
         }
      }

      var0.append("\n--- Last messages ---\n");

      for (String var20 : Notifications.recent()) {
         var0.append(var20).append('\n');
      }

      var0.append("\n--- Last errors ---\n");
      synchronized (ERRORS) {
         if (ERRORS.isEmpty()) {
            var0.append("none\n");
         }

         for (String var25 : ERRORS) {
            var0.append(var25);
         }
      }

      var0.append("\n--- Game log (DIHClient lines and errors, last 150) ---\n");

      try {
         Path var17 = FabricLoader.getInstance().getGameDir().resolve("logs").resolve("latest.log");
         if (Files.exists(var17)) {
            List var22 = Files.readAllLines(var17, StandardCharsets.UTF_8);
            ArrayList var26 = new ArrayList();

            for (String var31 : var22) {
               String var32 = var31.toLowerCase(Locale.ROOT);
               if (var32.contains("dihclient") || var32.contains("exception") || var32.contains("/error]") || var32.contains("\tat ")) {
                  var26.add(var31);
               }
            }

            for (int var29 = Math.max(0, var26.size() - 150); var29 < var26.size(); var29++) {
               var0.append((String)var26.get(var29)).append('\n');
            }
         }
      } catch (Throwable var11) {
         var0.append("(log not readable: ").append(var11.getMessage()).append(")\n");
      }

      try {
         Path var18 = FabricLoader.getInstance().getGameDir().resolve("dihclient").resolve("reports");
         Files.createDirectories(var18);
         Path var23 = var18.resolve("report-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")) + ".txt");
         Files.writeString(var23, var0.toString(), StandardCharsets.UTF_8);
         if (mc.field_1774 != null) {
            mc.field_1774.method_1455(var23.toAbsolutePath().toString());
         }

         return var23;
      } catch (Throwable var8) {
         DIHClient.LOG.error("[DIHClient] bug report failed", var8);
         return null;
      }
   }

   public static void writeAndTell() {
      Path var0 = write();
      if (var0 == null) {
         Notifications.error("Bug Report", "Could not write the report");
      } else {
         Notifications.chat("§aBug report saved:§f " + var0.toAbsolutePath() + " §7(path copied – send me that file)");
         Notifications.push("Bug Report", "Saved " + var0.getFileName(), Notifications.Type.SUCCESS);
      }
   }
}
