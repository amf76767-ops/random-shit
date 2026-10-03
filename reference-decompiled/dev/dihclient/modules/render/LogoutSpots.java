package dev.dihclient.modules.render;

import dev.dihclient.DIHClient;
import dev.dihclient.module.Category;
import dev.dihclient.module.Module;
import dev.dihclient.render.Render3D;
import dev.dihclient.setting.BoolSetting;
import dev.dihclient.setting.ColorSetting;
import dev.dihclient.setting.IntSetting;
import dev.dihclient.util.Notifications;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Map.Entry;
import net.minecraft.class_1657;
import net.minecraft.class_238;
import net.minecraft.class_243;

public class LogoutSpots extends Module {
   public final ColorSetting color = this.color("Color", "Box colour.", -43521);
   public final BoolSetting label = this.bool("Label", "Name, health and time above the box.", true);
   public final IntSetting scale = this.integer("Label Scale", "Size of the label.", 15, 5, 40).visibleWhen(this.label::get);
   public final BoolSetting notify = this.bool("Notify", "Toast when someone logs out near you and when they come back.", true);
   public final BoolSetting tracer = this.bool("Tracer", "Line to each logout spot.", false);
   public final BoolSetting friends = this.bool("Friends", "Also track friends.", true);
   public final IntSetting keepMinutes = this.integer("Keep Minutes", "Spots older than this disappear (0 = keep until they rejoin or you leave).", 0, 0, 240);
   private final Map<UUID, LogoutSpots.Snapshot> seen = new HashMap<>();
   private final Map<UUID, LogoutSpots.Snapshot> pending = new HashMap<>();
   private final Map<UUID, Integer> pendingAge = new HashMap<>();
   private final Map<UUID, LogoutSpots.Spot> spots = new LinkedHashMap<>();

   public LogoutSpots() {
      super("Logout Spots", Category.RENDER, "Marks where visible players logged out – they come back at exactly that spot.");
   }

   @Override
   protected void onEnable() {
      this.seen.clear();
      this.pending.clear();
      this.pendingAge.clear();
   }

   @Override
   public void onWorldChange() {
      this.seen.clear();
      this.pending.clear();
      this.pendingAge.clear();
      this.spots.clear();
   }

   private boolean inTab(UUID var1) {
      return mc.method_1562() != null && mc.method_1562().method_2871(var1) != null;
   }

   @Override
   public void onTick() {
      String var1 = mc.field_1687.method_27983().method_29177().toString();
      HashMap var2 = new HashMap();

      for (class_1657 var4 : mc.field_1687.method_18456()) {
         if (var4 != mc.field_1724 && (this.friends.get() || !DIHClient.social().isFriend(var4))) {
            var2.put(var4.method_5667(), new LogoutSpots.Snapshot(var4.method_7334().name(), var4.method_5829(), var4.method_6032() + var4.method_6067(), var1));
         }
      }

      for (Entry var11 : this.seen.entrySet()) {
         if (!var2.containsKey(var11.getKey())) {
            this.pending.put((UUID)var11.getKey(), (LogoutSpots.Snapshot)var11.getValue());
            this.pendingAge.put((UUID)var11.getKey(), 0);
         }
      }

      this.seen.clear();
      this.seen.putAll(var2);
      Iterator var10 = this.pending.entrySet().iterator();

      while (var10.hasNext()) {
         Entry var12 = (Entry)var10.next();
         UUID var5 = (UUID)var12.getKey();
         int var6 = this.pendingAge.merge(var5, 1, Integer::sum);
         if (!this.inTab(var5)) {
            LogoutSpots.Snapshot var7 = (LogoutSpots.Snapshot)var12.getValue();
            this.spots.put(var5, new LogoutSpots.Spot(var7.name(), var7.box(), var7.health(), var7.dim(), System.currentTimeMillis()));
            if (this.notify.get()) {
               class_243 var8 = var7.box().method_1005();
               Notifications.warn(
                  "Logout Spot",
                  var7.name()
                     + " logged out @ "
                     + (int)var8.field_1352
                     + " "
                     + (int)var8.field_1351
                     + " "
                     + (int)var8.field_1350
                     + " ("
                     + Math.round(var7.health())
                     + " HP)"
               );
            }

            var10.remove();
            this.pendingAge.remove(var5);
         } else if (var6 > 40 || var2.containsKey(var5)) {
            var10.remove();
            this.pendingAge.remove(var5);
         }
      }

      Iterator var13 = this.spots.entrySet().iterator();
      long var14 = this.keepMinutes.get().intValue() * 60000L;

      while (var13.hasNext()) {
         Entry var15 = (Entry)var13.next();
         if (this.inTab((UUID)var15.getKey())) {
            if (this.notify.get()) {
               Notifications.info("Logout Spot", ((LogoutSpots.Spot)var15.getValue()).name() + " is back online");
            }

            var13.remove();
         } else if (var14 > 0L && System.currentTimeMillis() - ((LogoutSpots.Spot)var15.getValue()).time() > var14) {
            var13.remove();
         }
      }
   }

   private static String ago(long var0) {
      long var2 = var0 / 1000L;
      if (var2 < 60L) {
         return var2 + "s";
      } else {
         return var2 < 3600L ? var2 / 60L + "m" : var2 / 3600L + "h" + var2 % 3600L / 60L + "m";
      }
   }

   @Override
   public void onRender3D(Render3D var1) {
      if (!this.spots.isEmpty()) {
         String var2 = mc.field_1687.method_27983().method_29177().toString();
         int var3 = this.color.get() | 0xFF000000;

         for (LogoutSpots.Spot var5 : this.spots.values()) {
            if (var5.dim().equals(var2)) {
               var1.box(var5.box(), var3, 50, true);
               class_243 var6 = new class_243(var5.box().method_1005().field_1352, var5.box().field_1325 + 0.4, var5.box().method_1005().field_1350);
               if (this.tracer.get()) {
                  var1.tracer(var5.box().method_1005(), var3);
               }

               if (this.label.get()) {
                  String var7 = var5.name() + " · " + Math.round(var5.health()) + " HP · " + ago(System.currentTimeMillis() - var5.time());
                  double var8 = mc.field_1724.method_73189().method_1022(var6);
                  float var10 = this.scale.get().intValue() / 10.0F * (float)Math.max(1.0, var8 / 8.0);
                  var1.text(var7, var6, var3, var10);
               }
            }
         }
      }
   }

   @Override
   public String getInfo() {
      return Integer.toString(this.spots.size());
   }

   @Override
   public List<String> details() {
      ArrayList var1 = new ArrayList();

      for (LogoutSpots.Spot var3 : this.spots.values()) {
         class_243 var4 = var3.box().method_1005();
         var1.add(
            var3.name()
               + " – "
               + (int)var4.field_1352
               + " "
               + (int)var4.field_1351
               + " "
               + (int)var4.field_1350
               + " ("
               + ago(System.currentTimeMillis() - var3.time())
               + " ago)"
         );
      }

      if (var1.isEmpty()) {
         var1.add("No logout spots.");
      }

      return var1;
   }

   private record Snapshot(String name, class_238 box, float health, String dim) {
   }

   private record Spot(String name, class_238 box, float health, String dim, long time) {
   }
}
