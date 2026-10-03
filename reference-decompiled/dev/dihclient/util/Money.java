package dev.dihclient.util;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.class_2561;
import net.minecraft.class_266;
import net.minecraft.class_268;
import net.minecraft.class_269;
import net.minecraft.class_310;
import net.minecraft.class_8646;
import net.minecraft.class_9011;

public final class Money {
   private static final class_310 mc = class_310.method_1551();
   private static final Pattern NUMBER = Pattern.compile("(\\d[\\d.,' ]*\\d|\\d)(?:\\s*(k|tsd|m|mio|b|bn|mrd|t)(?![a-z]))?", 2);
   private static final Pattern MONEY_WORD = Pattern.compile(
      "(geld|money|balance|kontostand|konto|guthaben|bal\\b|coins?|münzen|muenzen|dollar|euro|gold|bank|purse|cash|\\$|€)", 2
   );
   private static final Pattern COLOR_CODE = Pattern.compile("§.");
   private static final Pattern PLAYER_CHAT = Pattern.compile("^[A-Za-z0-9_]{3,16}: .*");
   private static double balance = Double.NaN;
   private static long balanceAt;
   private static String source = "";
   private static double sessionStart = Double.NaN;
   private static String sessionServer = "";
   private static long awaitBalUntil;
   private static long lastBalCommand;
   private static int ticks;
   private static final ArrayDeque<Object[]> CHAT = new ArrayDeque<>();

   private Money() {
   }

   public static List<String> chatSince(long var0) {
      ArrayList var2 = new ArrayList();
      synchronized (CHAT) {
         for (Object[] var5 : CHAT) {
            if ((Long)var5[0] >= var0) {
               var2.add((String)var5[1]);
            }
         }

         return var2;
      }
   }

   public static double balance() {
      return balance;
   }

   public static boolean known() {
      return !Double.isNaN(balance);
   }

   public static String source() {
      return source;
   }

   public static long age() {
      return balanceAt == 0L ? -1L : (System.currentTimeMillis() - balanceAt) / 1000L;
   }

   public static double session() {
      return !Double.isNaN(balance) && !Double.isNaN(sessionStart) ? balance - sessionStart : 0.0;
   }

   private static void set(double var0, String var2) {
      String var3 = mc.method_1558() == null ? "local" : mc.method_1558().field_3761;
      if (!var3.equals(sessionServer)) {
         sessionServer = var3;
         sessionStart = var0;
      } else if (Double.isNaN(sessionStart)) {
         sessionStart = var0;
      }

      balance = var0;
      balanceAt = System.currentTimeMillis();
      source = var2;
   }

   public static void tick() {
      if (mc.field_1687 != null && mc.field_1724 != null) {
         if (++ticks % 20 == 0) {
            double var0 = fromSidebar();
            if (!Double.isNaN(var0)) {
               set(var0, "scoreboard");
            }
         }
      }
   }

   public static void requestBal() {
      long var0 = System.currentTimeMillis();
      if (mc.field_1724 != null && var0 - lastBalCommand > 20000L) {
         lastBalCommand = var0;
         awaitBalUntil = var0 + 6000L;
         mc.field_1724.field_3944.method_45730("bal");
      }
   }

   public static void refresh() {
      double var0 = mc.field_1687 == null ? Double.NaN : fromSidebar();
      if (!Double.isNaN(var0)) {
         set(var0, "scoreboard");
      } else {
         requestBal();
      }
   }

   public static void onChat(String var0) {
      if (var0 == null || !strip(var0).startsWith("[DIH]")) {
         if (var0 != null && !var0.startsWith("[DIH]")) {
            synchronized (CHAT) {
               CHAT.addLast(new Object[]{System.currentTimeMillis(), strip(var0).toLowerCase(Locale.ROOT)});

               while (CHAT.size() > 40) {
                  CHAT.removeFirst();
               }
            }
         }

         if (var0 != null && !var0.startsWith("[DIH]") && System.currentTimeMillis() < awaitBalUntil) {
            String var6 = strip(var0);
            boolean var2 = var6.startsWith("<") || var6.contains("»") || var6.contains(">>") || PLAYER_CHAT.matcher(var6).matches();
            if (!var2 && MONEY_WORD.matcher(var6).find()) {
               double var3 = moneyIn(var6);
               if (!Double.isNaN(var3)) {
                  awaitBalUntil = 0L;
                  set(var3, "/bal");
               }
            }
         }
      }
   }

   public static List<String> sidebarLines() {
      ArrayList var0 = new ArrayList();

      try {
         class_269 var1 = mc.field_1687.method_8428();
         class_266 var2 = null;

         for (class_8646 var6 : class_8646.values()) {
            if (var6.method_52621() == 1) {
               var2 = var1.method_1189(var6);
            }
         }

         if (var2 == null) {
            return var0;
         }

         var0.add(strip(var2.method_1114().getString()));

         for (class_9011 var11 : var1.method_1184(var2)) {
            class_2561 var12 = var11.method_55387();
            class_268 var7 = var1.method_1164(var12.getString());
            var0.add(strip(class_268.method_1142(var7, var12).getString()));
         }
      } catch (Throwable var8) {
      }

      return var0;
   }

   public static double fromSidebar() {
      List var0 = sidebarLines();

      for (int var1 = 1; var1 < var0.size(); var1++) {
         String var2 = (String)var0.get(var1);
         if (MONEY_WORD.matcher(var2).find()) {
            double var3 = moneyIn(var2);
            if (!Double.isNaN(var3)) {
               return var3;
            }

            if (var1 + 1 < var0.size()) {
               double var5 = amountIn((String)var0.get(var1 + 1));
               if (!Double.isNaN(var5)) {
                  return var5;
               }
            }
         }
      }

      return Double.NaN;
   }

   public static String strip(String var0) {
      if (var0 == null) {
         return "";
      } else {
         return var0.indexOf(167) < 0 ? var0.trim() : COLOR_CODE.matcher(var0).replaceAll("").trim();
      }
   }

   public static double moneyIn(String var0) {
      String var1 = strip(var0);
      Matcher var2 = MONEY_WORD.matcher(var1);
      if (var2.find()) {
         double var3 = amountIn(var1.substring(var2.start()));
         if (!Double.isNaN(var3)) {
            return var3;
         }
      }

      return amountIn(var1);
   }

   public static double amountIn(String var0) {
      Matcher var1 = NUMBER.matcher(strip(var0));

      while (var1.find()) {
         double var2 = parseNumber(var1.group(1), var1.group(2));
         if (!Double.isNaN(var2)) {
            return var2;
         }
      }

      return Double.NaN;
   }

   public static double parseNumber(String var0, String var1) {
      String var2 = var0.replace(" ", "").replace("'", "");
      int var3 = var2.lastIndexOf(46);
      int var4 = var2.lastIndexOf(44);
      String var5;
      if (var3 >= 0 && var4 >= 0) {
         int var10 = var3 > var4 ? 46 : 44;
         int var12 = var10 == 46 ? 44 : 46;
         var5 = var2.replace(String.valueOf((char)var12), "").replace((char)var10, '.');
      } else if (var3 < 0 && var4 < 0) {
         var5 = var2;
      } else {
         int var6 = var3 >= 0 ? 46 : 44;
         int var7 = var2.length() - var2.indexOf(var6) - 1;
         boolean var8 = var2.indexOf(var6) != var2.lastIndexOf(var6);
         if (!var8 && var7 != 3) {
            var5 = var2.replace((char)var6, '.');
         } else {
            var5 = var2.replace(String.valueOf((char)var6), "");
         }
      }

      try {
         double var11 = Double.parseDouble(var5);
         if (var1 != null) {
            String var13 = var1.toLowerCase(Locale.ROOT);
            if (var13.equals("k") || var13.equals("tsd")) {
               var11 *= 1000.0;
            } else if (var13.equals("m") || var13.equals("mio")) {
               var11 *= 1000000.0;
            } else if (var13.equals("b") || var13.equals("bn") || var13.equals("mrd")) {
               var11 *= 1.0E9;
            } else if (var13.equals("t")) {
               var11 *= 1.0E12;
            }
         }

         return var11;
      } catch (NumberFormatException var9) {
         return Double.NaN;
      }
   }

   public static String format(double var0) {
      if (Double.isNaN(var0)) {
         return "?";
      } else {
         double var2 = Math.abs(var0);
         String var4;
         if (var2 >= 1.0E9) {
            var4 = String.format(Locale.ROOT, "%.2fB", var0 / 1.0E9);
         } else if (var2 >= 1000000.0) {
            var4 = String.format(Locale.ROOT, "%.2fM", var0 / 1000000.0);
         } else if (var2 >= 10000.0) {
            var4 = String.format(Locale.ROOT, "%.1fk", var0 / 1000.0);
         } else {
            var4 = var0 == Math.rint(var0) ? String.format(Locale.ROOT, "%.0f", var0) : String.format(Locale.ROOT, "%.2f", var0);
         }

         return var4;
      }
   }
}
