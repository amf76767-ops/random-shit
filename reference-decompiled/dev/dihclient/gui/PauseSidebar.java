package dev.dihclient.gui;

import dev.dihclient.DIHClient;
import dev.dihclient.gui.theme.Theme;
import dev.dihclient.modules.client.DihChat;
import dev.dihclient.modules.movement.ElytraBot;
import dev.dihclient.modules.world.Goto;
import dev.dihclient.render.Gfx;
import dev.dihclient.util.BugReport;
import dev.dihclient.util.ConfigShare;
import dev.dihclient.util.Money;
import dev.dihclient.waypoint.WaypointManager;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import net.minecraft.class_310;
import net.minecraft.class_332;
import net.minecraft.class_640;

public final class PauseSidebar {
   private static final class_310 mc = class_310.method_1551();
   private static final int W = 184;
   private static final String[] TABS = new String[]{"Chat", "Orte", "Freunde", "Server"};
   private static int tab;
   private static boolean collapsed;
   private static boolean userToggled;
   private static boolean focused;
   private static String input = "";
   private static final int[] scroll = new int[4];
   private static final List<PauseSidebar.Hit> hits = new ArrayList<>();
   private static int px;
   private static int py;
   private static int ph;
   private static boolean shown;
   private static long joinedAt;
   private static String joinedServer = "";
   private static final Map<String, List<String>> WRAPS = new HashMap<>();
   private static int wrapWidth = -1;
   private static long wrapsClearedAt;

   private PauseSidebar() {
   }

   public static boolean focused() {
      return focused && shown && !collapsed;
   }

   private static boolean fits(int var0) {
      return var0 - 184 - 8 > var0 / 2 + 106;
   }

   private static void hit(int var0, int var1, int var2, int var3, Runnable var4) {
      hits.add(new PauseSidebar.Hit(var0, var1, var2, var3, var4));
   }

   private static void button(class_332 var0, int var1, int var2, int var3, int var4, String var5, int var6, int var7, Runnable var8) {
      boolean var9 = Gfx.inside(var6, var7, var1, var2, var3, var4);
      Gfx.rect(var0, var1, var2, var3, var4, 3, var9 ? Theme.withAlpha(Theme.accent(), 0.85F) : -14539218);
      Gfx.textCentered(var0, Gfx.trim(var5, var3 - 4), var1 + var3 / 2, var2 + (var4 - 8) / 2, var9 ? -1 : -1446670);
      hit(var1, var2, var3, var4, var8);
   }

   public static void render(class_332 var0, int var1, int var2, int var3, int var4) {
      hits.clear();
      shown = true;
      String var5 = mc.method_1558() == null ? "local" : mc.method_1558().field_3761;
      if (!var5.equals(joinedServer)) {
         joinedServer = var5;
         joinedAt = System.currentTimeMillis();
      }

      if (!userToggled) {
         collapsed = !fits(var3);
      }

      if (collapsed) {
         int var12 = var3 - 34;
         button(var0, var12, 6, 28, 16, "DIH", var1, var2, () -> {
            collapsed = false;
            userToggled = true;
         });
      } else {
         px = var3 - 184 - 6;
         py = 6;
         ph = var4 - 12;
         Gfx.rect(var0, px, py, 184, ph, 6, -334622700);
         Gfx.outline(var0, px, py, 184, ph, 6, 587202559);
         Gfx.text(var0, "DIHClient", px + 8, py + 6, Theme.accent());
         Gfx.text(var0, "v5.3", px + 10 + Gfx.width("DIHClient"), py + 6, -10788238);
         button(var0, px + 184 - 18, py + 3, 14, 13, "×", var1, var2, () -> {
            collapsed = true;
            userToggled = true;
            focused = false;
         });
         int var6 = 172 / TABS.length;

         for (int var7 = 0; var7 < TABS.length; var7++) {
            int var8 = var7;
            int var9 = px + 6 + var7 * var6;
            boolean var10 = tab == var7;
            boolean var11 = Gfx.inside(var1, var2, var9, py + 20, var6 - 2, 14);
            Gfx.rect(var0, var9, py + 20, var6 - 2, 14, 3, var10 ? Theme.withAlpha(Theme.accent(), 0.9F) : (var11 ? -14012616 : -15065821));
            Gfx.textCentered(var0, TABS[var7], var9 + (var6 - 2) / 2, py + 23, var10 ? -1 : -7564380);
            hit(var9, py + 20, var6 - 2, 14, () -> {
               tab = var8;
               focused = false;
            });
         }

         int var13 = py + 40;
         int var14 = py + ph - 24;
         switch (tab) {
            case 0:
               renderChat(var0, var13, var14, var1, var2);
               break;
            case 1:
               renderWaypoints(var0, var13, var14, var1, var2);
               break;
            case 2:
               renderFriends(var0, var13, var14);
               break;
            default:
               renderServer(var0, var13, var14, var1, var2);
         }

         byte var15 = 56;
         button(var0, px + 6, var14 + 4, var15, 15, "Bug Report", var1, var2, BugReport::writeAndTell);
         button(var0, px + 8 + var15, var14 + 4, var15, 15, "Config teilen", var1, var2, ConfigShare::export);
         button(var0, px + 10 + var15 * 2, var14 + 4, var15, 15, "Import", var1, var2, () -> ConfigShare.importClipboard(false));
      }
   }

   private static List<String> wrapCached(String var0, int var1) {
      long var2 = System.currentTimeMillis();
      if (var1 != wrapWidth || WRAPS.size() > 512 || var2 - wrapsClearedAt > 5000L) {
         WRAPS.clear();
         wrapWidth = var1;
         wrapsClearedAt = var2;
      }

      List var4 = WRAPS.get(var0);
      if (var4 == null) {
         var4 = wrap(var0, var1);
         WRAPS.put(var0, var4);
      }

      return var4;
   }

   private static List<String> wrap(String var0, int var1) {
      ArrayList var2 = new ArrayList();
      StringBuilder var3 = new StringBuilder();

      for (String var7 : var0.split(" ")) {
         String var8 = var3.length() == 0 ? var7 : var3 + " " + var7;
         if (Gfx.width(var8) > var1 && var3.length() > 0) {
            var2.add(var3.toString());
            var3 = new StringBuilder(var7);
         } else {
            var3 = new StringBuilder(var8);
         }

         while (Gfx.width(var3.toString()) > var1 && var3.length() > 1) {
            int var9 = var3.length() - 1;

            while (var9 > 1 && Gfx.width(var3.substring(0, var9)) > var1) {
               var9--;
            }

            var2.add(var3.substring(0, var9));
            var3 = new StringBuilder(var3.substring(var9));
         }
      }

      if (var3.length() > 0) {
         var2.add(var3.toString());
      }

      return var2;
   }

   private static void renderChat(class_332 var0, int var1, int var2, int var3, int var4) {
      DihChat var5 = DihChat.instance();
      if (var5 != null && var5.isEnabled()) {
         boolean var6 = DihChat.connected();
         Gfx.rect(var0, px + 8, var1 + 3, 5, 5, 2, var6 ? -11870592 : -278748);
         Gfx.text(var0, (var6 ? "verbunden" : "verbinde …") + " · #" + var5.channel.get(), px + 17, var1 + 1, -7564380);
         int var7 = var1 + 13;
         int var8 = var2 - 22;
         ArrayList var9 = new ArrayList();

         for (String[] var11 : DihChat.history()) {
            List var12 = wrapCached(var11[1] + ": " + var11[2], 166);

            for (int var13 = 0; var13 < var12.size(); var13++) {
               var9.add(new Object[]{var12.get(var13), var13 == 0 ? var11[1] : null});
            }
         }

         int var17 = Math.max(1, (var8 - var7) / 10);
         scroll[0] = Math.max(0, Math.min(scroll[0], Math.max(0, var9.size() - var17)));
         int var18 = Math.max(0, var9.size() - var17 - scroll[0]);
         int var19 = var7;
         if (var9.isEmpty()) {
            Gfx.text(var0, "Noch keine Nachrichten.", px + 8, var7 + 2, -10788238);
         }

         var0.method_44379(px, var7, px + 184, var8);

         for (int var20 = var18; var20 < Math.min(var9.size(), var18 + var17); var20++) {
            Object[] var14 = (Object[])var9.get(var20);
            String var15 = (String)var14[0];
            String var16 = (String)var14[1];
            if (var16 != null && var15.startsWith(var16 + ":")) {
               Gfx.text(var0, var16 + ":", px + 8, var19, Theme.accent());
               Gfx.text(var0, var15.substring(var16.length() + 1), px + 8 + Gfx.width(var16 + ":"), var19, -1446670);
            } else {
               Gfx.text(var0, var15, px + 8, var19, -1446670);
            }

            var19 += 10;
         }

         var0.method_44380();
         int var21 = var2 - 19;
         Gfx.rect(var0, px + 6, var21, 172, 16, 3, focused ? -14933976 : -15394787);
         Gfx.outline(var0, px + 6, var21, 172, 16, 3, focused ? Theme.accent() : 587202559);
         String var22 = input.isEmpty() && !focused ? "Nachricht an alle …" : input;
         String var23 = var22;

         while (Gfx.width(var23) > 160 && var23.length() > 0) {
            var23 = var23.substring(1);
         }

         boolean var24 = focused && System.currentTimeMillis() / 500L % 2L == 0L;
         Gfx.text(var0, var23 + (var24 ? "_" : ""), px + 10, var21 + 4, input.isEmpty() && !focused ? -10788238 : -1446670);
         hit(px + 6, var21, 172, 16, () -> focused = true);
      } else {
         Gfx.text(var0, "DIHChat ist aus.", px + 8, var1 + 4, -1446670);
         Gfx.text(var0, "Chat mit allen DIHClient-Usern,", px + 8, var1 + 18, -7564380);
         Gfx.text(var0, "auf jedem Server.", px + 8, var1 + 28, -7564380);
         button(var0, px + 8, var1 + 44, 168, 16, "Einschalten", var3, var4, () -> {
            if (var5 != null) {
               var5.setEnabled(true);
            }
         });
      }
   }

   private static void renderWaypoints(class_332 var0, int var1, int var2, int var3, int var4) {
      ArrayList var5 = new ArrayList<>(WaypointManager.get().here());
      if (mc.field_1724 != null) {
         var5.sort((var0x, var1x) -> Double.compare(dist(var0x), dist(var1x)));
      }

      if (var5.isEmpty()) {
         Gfx.text(var0, "Keine Wegpunkte hier.", px + 8, var1 + 4, -10788238);
      } else {
         int var6 = Math.max(1, (var2 - var1) / 24);
         scroll[1] = Math.max(0, Math.min(scroll[1], Math.max(0, var5.size() - var6)));
         int var7 = var1;

         for (int var8 = scroll[1]; var8 < Math.min(var5.size(), scroll[1] + var6); var8++) {
            WaypointManager.Waypoint var9 = (WaypointManager.Waypoint)var5.get(var8);
            Gfx.rect(var0, px + 6, var7, 172, 22, 3, -15394787);
            Gfx.text(var0, Gfx.trim(var9.name, 104), px + 10, var7 + 3, -1446670);
            Gfx.text(var0, var9.x + " " + var9.y + " " + var9.z + " · " + (int)dist(var9) + "m", px + 10, var7 + 12, -10788238);
            button(var0, px + 184 - 66, var7 + 4, 28, 14, "Lauf", var3, var4, () -> {
               Goto.start(var9.x, var9.y, var9.z, var9.name);
               mc.method_1507(null);
            });
            button(var0, px + 184 - 36, var7 + 4, 28, 14, "Flug", var3, var4, () -> {
               ElytraBot.start(var9.x, var9.y, var9.z, var9.name);
               mc.method_1507(null);
            });
            var7 += 24;
         }
      }
   }

   private static double dist(WaypointManager.Waypoint var0) {
      return mc.field_1724 == null ? 0.0 : Math.hypot(var0.x + 0.5 - mc.field_1724.method_23317(), var0.z + 0.5 - mc.field_1724.method_23321());
   }

   private static Set<String> online() {
      HashSet var0 = new HashSet();

      try {
         if (mc.method_1562() != null) {
            for (Object var2 : mc.method_1562().method_2880()) {
               var0.add(((class_640)var2).method_2966().name().toLowerCase(Locale.ROOT));
            }
         }
      } catch (Throwable var3) {
      }

      return var0;
   }

   private static void renderFriends(class_332 var0, int var1, int var2) {
      ArrayList var3 = new ArrayList<>(DIHClient.social().friends());
      Set var4 = online();
      var3.sort((var1x, var2x) -> {
         boolean var3x = var4.contains(var1x.toLowerCase(Locale.ROOT));
         boolean var4x = var4.contains(var2x.toLowerCase(Locale.ROOT));
         return var3x != var4x ? (var3x ? -1 : 1) : var1x.compareToIgnoreCase(var2x);
      });
      long var5 = var3.stream().filter(var1x -> var4.contains(var1x.toLowerCase(Locale.ROOT))).count();
      Gfx.text(var0, var3.size() + " Freunde · " + var5 + " hier online", px + 8, var1 + 1, -7564380);
      if (var3.isEmpty()) {
         Gfx.text(var0, "Freunde fügst du in ClickGUI →", px + 8, var1 + 16, -10788238);
         Gfx.text(var0, "Client → Friends hinzu.", px + 8, var1 + 26, -10788238);
      } else {
         int var7 = Math.max(1, (var2 - var1 - 14) / 12);
         scroll[2] = Math.max(0, Math.min(scroll[2], Math.max(0, var3.size() - var7)));
         int var8 = var1 + 14;

         for (int var9 = scroll[2]; var9 < Math.min(var3.size(), scroll[2] + var7); var9++) {
            String var10 = (String)var3.get(var9);
            boolean var11 = var4.contains(var10.toLowerCase(Locale.ROOT));
            Gfx.rect(var0, px + 9, var8 + 3, 5, 5, 2, var11 ? -11870592 : -12959928);
            Gfx.text(var0, var10, px + 18, var8 + 1, var11 ? -1446670 : -10788238);
            if (var11) {
               Gfx.text(var0, "online", px + 184 - 10 - Gfx.width("online"), var8 + 1, -11870592);
            }

            var8 += 12;
         }
      }
   }

   private static void renderServer(class_332 var0, int var1, int var2, int var3, int var4) {
      ArrayList var5 = new ArrayList();
      var5.add(new String[]{"Server", mc.method_1558() == null ? "Einzelspieler" : mc.method_1558().field_3761});
      int var6 = mc.method_1562() == null ? 0 : mc.method_1562().method_2880().size();
      var5.add(new String[]{"Spieler", String.valueOf(var6)});
      int var7 = -1;

      try {
         if (mc.method_1562() != null && mc.field_1724 != null) {
            for (Object var9 : mc.method_1562().method_2880()) {
               class_640 var10 = (class_640)var9;
               if (var10.method_2966().name().equals(mc.field_1724.method_7334().name())) {
                  var7 = var10.method_2959();
               }
            }
         }
      } catch (Throwable var13) {
      }

      var5.add(new String[]{"Ping", var7 < 0 ? "?" : var7 + " ms"});
      long var14 = (System.currentTimeMillis() - joinedAt) / 60000L;
      var5.add(new String[]{"Spielzeit", var14 >= 60L ? var14 / 60L + "h " + var14 % 60L + "m" : var14 + "m"});
      var5.add(new String[]{"Kontostand", Money.known() ? Money.format(Money.balance()) : "unbekannt"});
      if (Money.known()) {
         double var15 = Money.session();
         var5.add(new String[]{"Diese Sitzung", (var15 >= 0.0 ? "+" : "") + Money.format(var15)});
         var5.add(new String[]{"Quelle", Money.source() + " (" + Money.age() + "s alt)"});
      }

      int var16 = var1 + 2;

      for (String[] var12 : var5) {
         Gfx.text(var0, var12[0], px + 8, var16, -7564380);
         Gfx.text(var0, Gfx.trim(var12[1], 94), px + 184 - 8 - Gfx.width(Gfx.trim(var12[1], 94)), var16, -1446670);
         var16 += 12;
      }

      button(var0, px + 8, var16 + 6, 168, 16, "Kontostand lesen", var3, var4, Money::refresh);
   }

   public static boolean click(double var0, double var2) {
      if (!shown) {
         return false;
      } else {
         for (int var4 = hits.size() - 1; var4 >= 0; var4--) {
            PauseSidebar.Hit var5 = hits.get(var4);
            if (Gfx.inside(var0, var2, var5.x(), var5.y(), var5.w(), var5.h())) {
               focused = false;
               var5.action().run();
               return true;
            }
         }

         boolean var6 = !collapsed && Gfx.inside(var0, var2, px, py, 184, ph);
         focused = false;
         return var6;
      }
   }

   public static boolean scroll(double var0, double var2, double var4) {
      if (shown && !collapsed && Gfx.inside(var0, var2, px, py, 184, ph)) {
         int var6 = var4 > 0.0 ? 1 : -1;
         scroll[tab] = Math.max(0, scroll[tab] + (tab == 0 ? var6 * 2 : -var6));
         return true;
      } else {
         return false;
      }
   }

   public static boolean key(int var0, int var1) {
      if (!focused()) {
         return false;
      } else {
         switch (var0) {
            case 86:
               if ((var1 & 2) != 0 && mc.field_1774 != null) {
                  String var2 = mc.field_1774.method_1460();
                  if (var2 != null) {
                     type(var2.replace('\n', ' '));
                  }

                  return true;
               }

               return false;
            case 256:
               focused = false;
               return true;
            case 257:
            case 335:
               if (!input.isBlank()) {
                  DihChat.sendText(input);
                  input = "";
                  scroll[0] = 0;
               }

               return true;
            case 259:
               if (!input.isEmpty()) {
                  input = (var1 & 2) != 0 ? "" : input.substring(0, input.length() - 1);
               }

               return true;
            default:
               return true;
         }
      }
   }

   public static boolean type(String var0) {
      if (!focused()) {
         return false;
      } else {
         for (char var4 : var0.toCharArray()) {
            if (var4 >= ' ' && var4 != 127 && input.length() < 256) {
               input = input + var4;
            }
         }

         return true;
      }
   }

   public static void hidden() {
      shown = false;
      focused = false;
      hits.clear();
   }

   private record Hit(int x, int y, int w, int h, Runnable action) {
   }
}
