package dev.dihclient.modules.render;

import dev.dihclient.gui.WaypointsScreen;
import dev.dihclient.module.Category;
import dev.dihclient.module.Module;
import dev.dihclient.render.Render3D;
import dev.dihclient.setting.BoolSetting;
import dev.dihclient.setting.DoubleSetting;
import dev.dihclient.setting.IntSetting;
import dev.dihclient.util.Notifications;
import dev.dihclient.waypoint.WaypointManager;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import net.minecraft.class_238;
import net.minecraft.class_243;
import net.minecraft.class_2561;
import net.minecraft.class_418;

public class Waypoints extends Module {
   public static final int[] COLORS = new int[]{-16711681, -43691, -11141291, -171, -43521, -22016, -1, -7829368};
   public final BoolSetting beam = this.bool("Beam", "Light beam from bedrock to the sky at every waypoint.", true);
   public final BoolSetting labels = this.bool("Text", "Name above the waypoint.", true);
   public final BoolSetting distance = this.bool("Distance", "Adds the distance to the text.", true).visibleWhen(this.labels::get);
   public final DoubleSetting textSize = this.dbl("Text Size", "Size of the text.", 1.0, 0.4, 3.0, 0.1).visibleWhen(this.labels::get);
   public final IntSetting maxDistance = this.integer("Max Distance", "Hide waypoints further away than this (blocks). 0 = show all.", 0, 0, 100000);
   public final BoolSetting otherDimension = this.bool(
      "Other Dimension", "Shows Nether waypoints in the Overworld (×8) and Overworld ones in the Nether (÷8) – handy for portals.", true
   );
   public final BoolSetting deathPoints = this.bool("Death Waypoints", "Sets a waypoint where you die.", true);
   public final IntSetting keepDeaths = this.integer("Keep Deaths", "How many death waypoints are kept.", 3, 1, 20).visibleWhen(this.deathPoints::get);
   public final BoolSetting deathChat = this.bool("Death Coords In Chat", "Writes your death position into your chat (only you see it).", true)
      .visibleWhen(this.deathPoints::get);
   private boolean deadHandled;

   public Waypoints() {
      super("Waypoints", Category.RENDER, "Saves places (base, portals, stashes …) with beams and distance in the world. Sets a waypoint where you die.");
      this.action("Open Manager", "List, add, rename, delete waypoints and walk to them with Goto.", this::open);
      this.action("Add Here", "Adds a waypoint at your position.", Waypoints::addHere);
   }

   private void open() {
      mc.method_1507(new WaypointsScreen(mc.field_1755));
   }

   public static void addHere() {
      if (mc.field_1724 != null) {
         WaypointManager var0 = WaypointManager.get();
         String var1 = var0.nextName();
         int var2 = COLORS[var0.here().size() % COLORS.length];
         var0.add(
            var1,
            (int)Math.floor(mc.field_1724.method_23317()),
            (int)Math.floor(mc.field_1724.method_23318()),
            (int)Math.floor(mc.field_1724.method_23321()),
            var2
         );
         Notifications.info("Waypoints", "Added " + var1);
      }
   }

   @Override
   public void onTick() {
      if (inGame()) {
         boolean var1 = mc.field_1755 instanceof class_418 || mc.field_1724.method_6032() <= 0.0F;
         if (var1 && !this.deadHandled) {
            this.deadHandled = true;
            if (this.deathPoints.get()) {
               int var2 = (int)Math.floor(mc.field_1724.method_23317());
               int var3 = (int)Math.floor(mc.field_1724.method_23318());
               int var4 = (int)Math.floor(mc.field_1724.method_23321());
               WaypointManager var5 = WaypointManager.get();
               WaypointManager.Waypoint var6 = var5.add("Death " + new SimpleDateFormat("HH:mm").format(new Date()), var2, var3, var4, -43691);
               var6.death = true;
               var5.trimDeaths(this.keepDeaths.get());
               if (this.deathChat.get()) {
                  mc.field_1724
                     .method_7353(
                        class_2561.method_43470("§c[Waypoints] §fYou died at §e" + var2 + " " + var3 + " " + var4 + "§f (" + shortDim(var6.dim) + ")"), false
                     );
               }
            }
         } else if (!var1) {
            this.deadHandled = false;
         }
      }
   }

   public static String shortDim(String var0) {
      return switch (var0) {
         case "minecraft:overworld" -> "Overworld";
         case "minecraft:the_nether" -> "Nether";
         case "minecraft:the_end" -> "End";
         default -> var0.substring(var0.indexOf(58) + 1);
      };
   }

   public static class_243 posHere(WaypointManager.Waypoint var0, boolean var1) {
      String var2 = WaypointManager.dimKey();
      if (var0.dim.equals(var2)) {
         return new class_243(var0.x + 0.5, var0.y, var0.z + 0.5);
      } else if (var1 && var0.dim.equals("minecraft:the_nether") && var2.equals("minecraft:overworld")) {
         return new class_243(var0.x * 8 + 0.5, var0.y, var0.z * 8 + 0.5);
      } else {
         return var1 && var0.dim.equals("minecraft:overworld") && var2.equals("minecraft:the_nether")
            ? new class_243(Math.floor(var0.x / 8.0) + 0.5, var0.y, Math.floor(var0.z / 8.0) + 0.5)
            : null;
      }
   }

   @Override
   public void onRender3D(Render3D var1) {
      List var2 = WaypointManager.get().here();
      if (!var2.isEmpty() && mc.field_1687 != null) {
         class_243 var3 = var1.camera();
         double var4 = mc.field_1687.method_31607();

         for (WaypointManager.Waypoint var7 : var2) {
            if (var7.visible) {
               class_243 var8 = posHere(var7, this.otherDimension.get());
               if (var8 != null) {
                  boolean var9 = !var7.dim.equals(WaypointManager.dimKey());
                  double var10 = var8.method_1022(var3);
                  if (this.maxDistance.get() <= 0 || !(var10 > this.maxDistance.get().intValue())) {
                     int var12 = var7.color | 0xFF000000;
                     if (this.beam.get()) {
                        class_238 var13 = new class_238(
                           var8.field_1352 - 0.25, var4, var8.field_1350 - 0.25, var8.field_1352 + 0.25, 320.0, var8.field_1350 + 0.25
                        );
                        if (var1.visible(var13, 0.0)) {
                           var1.boxFilled(var13, var12 & 16777215 | (var9 ? 50 : 90) << 24, true);
                        }
                     }

                     class_238 var19 = new class_238(
                        var8.field_1352 - 0.5, var8.field_1351, var8.field_1350 - 0.5, var8.field_1352 + 0.5, var8.field_1351 + 1.0, var8.field_1350 + 0.5
                     );
                     if (var1.visible(var19, 0.0)) {
                        var1.boxOutline(var19, var12, true);
                     }

                     if (this.labels.get()) {
                        class_243 var14 = var8.method_1031(0.0, 1.8, 0.0);
                        double var15 = var14.method_1022(var3);
                        if (var15 > 96.0) {
                           var14 = var3.method_1019(var14.method_1020(var3).method_1021(96.0 / var15));
                        }

                        float var17 = (float)(Math.max(1.2, Math.min(var15, 96.0) / 9.0) * this.textSize.get());
                        String var18 = var7.name + (var9 ? " (" + shortDim(var7.dim) + ")" : "");
                        if (this.distance.get()) {
                           var18 = var18 + " · " + (int)var10 + "m";
                        }

                        var1.text(var18, var14, var12, var17);
                     }
                  }
               }
            }
         }
      }
   }

   @Override
   public String getInfo() {
      int var1 = WaypointManager.get().here().size();
      return var1 > 0 ? String.valueOf(var1) : null;
   }
}
