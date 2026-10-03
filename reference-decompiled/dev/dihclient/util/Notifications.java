package dev.dihclient.util;

import dev.dihclient.module.Module;
import dev.dihclient.module.ModuleManager;
import dev.dihclient.modules.client.DiscordAlarm;
import dev.dihclient.modules.client.NotificationsModule;
import java.time.LocalTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.minecraft.class_2561;
import net.minecraft.class_310;

public final class Notifications {
   private static final List<Notifications.Toast> TOASTS = new ArrayList<>();
   private static String alertText;
   private static int alertTicks;
   private static final ArrayDeque<String> LOG = new ArrayDeque<>();

   private static void log(String var0) {
      synchronized (LOG) {
         LOG.addLast(LocalTime.now().withNano(0) + " " + var0);

         while (LOG.size() > 80) {
            LOG.removeFirst();
         }
      }
   }

   public static List<String> recent() {
      synchronized (LOG) {
         return new ArrayList<>(LOG);
      }
   }

   private Notifications() {
   }

   public static void toggle(Module var0) {
      NotificationsModule var1 = ModuleManager.of(NotificationsModule.class);
      if (var1 != null && var1.isEnabled() && var1.toggles.get()) {
         push(var0.name(), var0.isEnabled() ? "enabled" : "disabled", var0.isEnabled() ? Notifications.Type.SUCCESS : Notifications.Type.INFO);
      }
   }

   public static void info(String var0, String var1) {
      push(var0, var1, Notifications.Type.INFO);
   }

   public static void warn(String var0, String var1) {
      push(var0, var1, Notifications.Type.WARNING);
   }

   public static void error(String var0, String var1) {
      push(var0, var1, Notifications.Type.ERROR);
   }

   public static void push(String var0, String var1, Notifications.Type var2) {
      log(var2 + " " + var0 + ": " + var1);
      NotificationsModule var3 = ModuleManager.of(NotificationsModule.class);
      boolean var4 = var3 == null || var3.isEnabled();
      if (var4) {
         synchronized (TOASTS) {
            TOASTS.add(new Notifications.Toast(var0, var1, var2, 60));

            while (TOASTS.size() > 6) {
               TOASTS.remove(0);
            }
         }
      }

      if (var3 != null && var3.isEnabled() && var3.chat.get()) {
         chat(var0 + ": " + var1);
      }

      DiscordAlarm.onToast(var0, var1, var2);
   }

   public static void alert(String var0, int var1) {
      alertText = var0;
      alertTicks = var1;
      DiscordAlarm.onAlert(var0);
   }

   public static String alertText() {
      return alertTicks > 0 ? alertText : null;
   }

   public static int alertTicks() {
      return alertTicks;
   }

   public static void chat(String var0) {
      class_310 var1 = class_310.method_1551();
      if (var1.field_1724 != null) {
         var1.field_1724.method_7353(class_2561.method_43470("§9[DIH]§r " + var0), false);
      }
   }

   public static void tick() {
      if (alertTicks > 0) {
         alertTicks--;
      }

      synchronized (TOASTS) {
         Iterator var1 = TOASTS.iterator();

         while (var1.hasNext()) {
            Notifications.Toast var2 = (Notifications.Toast)var1.next();
            if (++var2.age >= var2.duration) {
               var1.remove();
            }
         }
      }
   }

   public static List<Notifications.Toast> toasts() {
      synchronized (TOASTS) {
         return (List<Notifications.Toast>)(TOASTS.isEmpty() ? List.of() : new ArrayList<>(TOASTS));
      }
   }

   public static final class Toast {
      public final String title;
      public final String message;
      public final Notifications.Type type;
      public final int duration;
      public int age;

      Toast(String var1, String var2, Notifications.Type var3, int var4) {
         this.title = var1;
         this.message = var2;
         this.type = var3;
         this.duration = var4;
      }

      public float progress() {
         return Math.min(1.0F, (float)this.age / this.duration);
      }
   }

   public static enum Type {
      INFO,
      SUCCESS,
      WARNING,
      ERROR;
   }
}
