package dev.dihclient.modules.misc;

import dev.dihclient.module.Category;
import dev.dihclient.module.Module;
import dev.dihclient.setting.BoolSetting;
import dev.dihclient.setting.StringSetting;
import dev.dihclient.util.Notifications;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import net.minecraft.class_3417;
import net.minecraft.class_640;

public class Watchlist extends Module {
   public final StringSetting names = this.text("Names", "Comma separated player names to watch for.", "", 512).legacy("watchlist");
   public final StringSetting alarmText = this.text("Alarm Text", "Alert text. {player} is replaced by the name.", "WATCHLIST: {player} spotted!", 96)
      .legacy("watchlist.alarmText");
   public final BoolSetting tabList = this.bool("Tab List", "Also alarms when the player is only in the tab list (not nearby).", true);
   public final BoolSetting sound = this.bool("Sound", "Plays an alarm sound.", true);
   private final Set<String> present = new HashSet<>();

   public Watchlist() {
      super("Watchlist Alarm", Category.MISC, "Alarms when a watched username appears in render distance or the tab list.");
   }

   private Set<String> watched() {
      HashSet var1 = new HashSet();

      for (String var5 : this.names.get().split("[,; ]+")) {
         if (!var5.isBlank()) {
            var1.add(var5.trim().toLowerCase(Locale.ROOT));
         }
      }

      return var1;
   }

   @Override
   public void onWorldChange() {
      this.present.clear();
   }

   @Override
   public void onTick() {
      if (mc.field_1724.field_6012 % 10 == 0) {
         Set var1 = this.watched();
         if (!var1.isEmpty()) {
            HashSet var2 = new HashSet();
            mc.field_1687.method_18456().forEach(var1x -> var2.add(var1x.method_7334().name()));
            if (this.tabList.get() && mc.method_1562() != null) {
               for (class_640 var4 : mc.method_1562().method_2880()) {
                  var2.add(var4.method_2966().name());
               }
            }

            for (String var8 : var2) {
               String var5 = var8.toLowerCase(Locale.ROOT);
               if (var1.contains(var5) && !var5.equals(mc.field_1724.method_7334().name().toLowerCase(Locale.ROOT)) && this.present.add(var5)) {
                  String var6 = this.alarmText.get().replace("{player}", var8);
                  Notifications.alert(var6, 100);
                  Notifications.warn("Watchlist", var8 + " is online");
                  if (this.sound.get()) {
                     mc.field_1724.method_5783(class_3417.field_17265, 1.0F, 1.0F);
                  }
               }
            }

            this.present.removeIf(var1x -> var2.stream().noneMatch(var1xx -> var1xx.equalsIgnoreCase(var1x)));
         }
      }
   }

   @Override
   public List<String> details() {
      return List.of("Watching: " + (this.names.get().isBlank() ? "-" : this.names.get()), "Currently present: " + this.present.size());
   }
}
