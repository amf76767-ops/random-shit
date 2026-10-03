package dev.dihclient.modules.render;

import dev.dihclient.autobuild.BuildRuntime;
import dev.dihclient.gui.theme.Theme;
import dev.dihclient.hud.HudStyle;
import dev.dihclient.module.Category;
import dev.dihclient.module.Module;
import dev.dihclient.module.ModuleManager;
import dev.dihclient.modules.automation.StashSorter;
import dev.dihclient.modules.automation.TaskQueue;
import dev.dihclient.modules.movement.ElytraBot;
import dev.dihclient.modules.player.AutoFish;
import dev.dihclient.modules.player.AutoRestock;
import dev.dihclient.modules.world.AutoBuild;
import dev.dihclient.modules.world.AutoFarm;
import dev.dihclient.modules.world.AutoMine;
import dev.dihclient.modules.world.Goto;
import dev.dihclient.modules.world.SafeRoute;
import dev.dihclient.modules.world.Terraform;
import dev.dihclient.modules.world.Tunnel;
import dev.dihclient.render.Gfx;
import dev.dihclient.setting.BoolSetting;
import dev.dihclient.setting.DoubleSetting;
import dev.dihclient.setting.EnumSetting;
import dev.dihclient.setting.IntSetting;
import dev.dihclient.waypoint.WaypointManager;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import net.minecraft.class_1792;
import net.minecraft.class_2338;
import net.minecraft.class_332;
import net.minecraft.class_3532;

public class NavigationHud extends Module {
   public final EnumSetting<NavigationHud.Corner> corner = this.mode("Position", "Screen corner of the panel.", NavigationHud.Corner.TOP_LEFT);
   public final IntSetting offsetX = this.integer("Offset X", "Distance from the screen edge.", 4, 0, 600);
   public final IntSetting offsetY = this.integer("Offset Y", "Distance from the screen edge.", 70, 0, 600);
   public final DoubleSetting scale = this.dbl("Scale", "Size of the panel.", 1.0, 0.5, 2.0, 0.05);
   public final BoolSetting showGoto = this.bool("Goto", "Target, distance, direction, progress and time left.", true);
   public final BoolSetting showBuild = this.bool("AutoBuild", "Progress, speed, time left and finish time.", true);
   public final BoolSetting showMaterials = this.bool("Missing Materials", "What AutoBuild still needs.", true).visibleWhen(this.showBuild::get);
   public final BoolSetting showBots = this.bool("Other Bots", "TaskQueue, StashSorter, Tunnel, AutoMine, Terraform, AutoFarm, AutoFish, AutoRestock.", true);
   public final BoolSetting showWaypoint = this.bool("Nearest Waypoint", "Nearest waypoint with direction when Goto is off.", true);
   public final BoolSetting showSafety = this.bool("SafeRoute Warnings", "Why a bot stopped (lava, drop, mob).", true);
   public final BoolSetting onlyActive = this.bool("Only When Active", "Hides the panel while no bot is running.", false);
   private final List<NavigationHud.Section> sections = new ArrayList<>();
   private double speed;
   private double lastX = Double.NaN;
   private double lastZ;
   private String materials;
   private int tick;

   public NavigationHud() {
      super("NavigationHUD", Category.RENDER, "Live panel for Goto & all bots: distance, direction, progress, time left (also how long AutoBuild still needs).");
   }

   public static String time(int var0) {
      if (var0 < 0) {
         return "…";
      } else if (var0 < 60) {
         return var0 + "s";
      } else {
         return var0 < 3600 ? var0 / 60 + "m " + var0 % 60 + "s" : var0 / 3600 + "h " + var0 % 3600 / 60 + "m";
      }
   }

   public static String arrow(double var0, double var2) {
      double var4 = var0 - mc.field_1724.method_23317();
      double var6 = var2 - mc.field_1724.method_23321();
      float var8 = (float)Math.toDegrees(Math.atan2(var6, var4)) - 90.0F;
      float var9 = class_3532.method_15393(var8 - mc.field_1724.method_36454());
      int var10 = Math.floorMod(Math.round(var9 / 45.0F), 8);
      return new String[]{"↑", "↗", "→", "↘", "↓", "↙", "←", "↖"}[var10];
   }

   private static int dist(double var0, double var2) {
      return (int)Math.hypot(var0 - mc.field_1724.method_23317(), var2 - mc.field_1724.method_23321());
   }

   private static String finishClock(int var0) {
      if (var0 < 0) {
         return null;
      } else {
         LocalTime var1 = LocalTime.now().plusSeconds(var0);
         return String.format(Locale.ROOT, "%02d:%02d", var1.getHour(), var1.getMinute());
      }
   }

   @Override
   public void onTick() {
      if (inGame()) {
         double var1 = mc.field_1724.method_23317();
         double var3 = mc.field_1724.method_23321();
         if (!Double.isNaN(this.lastX)) {
            double var5 = Math.hypot(var1 - this.lastX, var3 - this.lastZ) * 20.0;
            if (var5 < 60.0) {
               this.speed = this.speed * 0.95 + var5 * 0.05;
            }
         }

         this.lastX = var1;
         this.lastZ = var3;
         if (this.tick++ % 40 == 0) {
            this.materials = this.missingText();
         }

         if (this.tick % 5 == 0) {
            this.rebuild();
         }
      }
   }

   private String missingText() {
      AutoBuild var1 = ModuleManager.of(AutoBuild.class);
      if (var1 != null && this.showMaterials.get() && var1.runtime().isBuilding()) {
         Map var2 = var1.runtime().missingMaterials();
         if (var2.isEmpty()) {
            return null;
         } else {
            Entry var3 = (Entry)var2.entrySet().iterator().next();
            return "Missing: "
               + ((class_1792)var3.getKey()).method_63680().getString()
               + " ×"
               + var3.getValue()
               + (var2.size() > 1 ? " +" + (var2.size() - 1) + " more" : "");
         }
      } else {
         return null;
      }
   }

   private void rebuild() {
      this.sections.clear();
      boolean var1 = false;
      Goto var2 = Goto.instance();
      if (this.showGoto.get() && var2 != null && var2.goal() != null) {
         var1 = true;
         class_2338 var3 = var2.goal();
         NavigationHud.Section var4 = new NavigationHud.Section("Goto → " + var2.label());
         double var5 = var2.pathRemaining();
         int var7 = this.speed > 0.4 ? (int)(var5 / this.speed) : -1;
         var4.line(
            dist(var3.method_10263() + 0.5, var3.method_10260() + 0.5)
               + "m "
               + arrow(var3.method_10263() + 0.5, var3.method_10260() + 0.5)
               + (
                  var2.goalHasY()
                     ? " · Y "
                        + (var3.method_10264() - mc.field_1724.method_24515().method_10264() >= 0 ? "+" : "")
                        + (var3.method_10264() - mc.field_1724.method_24515().method_10264())
                     : ""
               )
               + " · ETA "
               + time(var7),
            -1446670
         );
         var4.line(var2.status(), -7564380);
         var4.progress = (float)Math.max(0.0, Math.min(1.0, 1.0 - dist(var3.method_10263() + 0.5, var3.method_10260() + 0.5) / var2.startDistance()));
         this.sections.add(var4);
      }

      ElytraBot var12 = ElytraBot.instance();
      if (this.showGoto.get() && var12 != null && var12.goal() != null) {
         var1 = true;
         class_2338 var13 = var12.goal();
         NavigationHud.Section var15 = new NavigationHud.Section("ElytraBot → " + var12.label());
         var15.line(
            (int)var12.distance()
               + "m "
               + arrow(var13.method_10263() + 0.5, var13.method_10260() + 0.5)
               + " · ETA "
               + time(var12.eta())
               + " · "
               + (int)var12.speed()
               + " b/s",
            -1446670
         );
         var15.line("Y " + (int)var12.altitude() + " · rockets " + var12.rocketsLeft() + " (" + var12.rocketsUsed() + " used)", Theme.accent());
         var15.line(var12.status(), -7564380);
         var15.progress = (float)Math.max(0.0, Math.min(1.0, 1.0 - var12.distance() / Math.max(1.0, var12.startDistance())));
         this.sections.add(var15);
      }

      AutoBuild var14 = ModuleManager.of(AutoBuild.class);
      if (this.showBuild.get() && var14 != null && var14.runtime().plan() != null) {
         BuildRuntime var16 = var14.runtime();
         BuildRuntime.Phase var6 = var16.phase();
         if (var6 == BuildRuntime.Phase.BUILDING || var6 == BuildRuntime.Phase.PAUSED || var6 == BuildRuntime.Phase.FINISHED) {
            var1 |= var6 != BuildRuntime.Phase.FINISHED;
            NavigationHud.Section var23 = new NavigationHud.Section("AutoBuild · " + var16.plan().schematic.name());
            int var8 = Math.max(1, var16.totalCount());
            float var9 = (float)var16.doneCount() / var8;
            var23.progress = var9;
            var23.line(
               String.format(Locale.ROOT, "%.1f%%", var9 * 100.0F)
                  + " · "
                  + var16.doneCount()
                  + "/"
                  + var16.totalCount()
                  + " · Layer "
                  + (var16.layer() + 1)
                  + "/"
                  + var16.layers(),
               -1446670
            );
            if (var6 == BuildRuntime.Phase.FINISHED) {
               var23.line("Done in " + time(var16.elapsed()), -11870592);
            } else {
               int var10 = var16.eta();
               String var11 = finishClock(var10);
               var23.line(
                  String.format(Locale.ROOT, "%.1f bl/s", var16.rate())
                     + " · "
                     + (var10 >= 0 ? "~" + time(var10) + " left" : "ETA …")
                     + (var11 != null ? " · done ~" + var11 : ""),
                  Theme.accent()
               );
               var23.line(var6 == BuildRuntime.Phase.PAUSED ? "PAUSED" : var16.status(), -7564380);
               if (this.materials != null) {
                  var23.line(this.materials, -278748);
               }
            }

            this.sections.add(var23);
         }
      }

      if (this.showBots.get()) {
         for (Class var20 : List.of(
            TaskQueue.class, StashSorter.class, Tunnel.class, AutoMine.class, Terraform.class, AutoFarm.class, AutoFish.class, AutoRestock.class
         )) {
            Module var24 = ModuleManager.of(var20);
            if (var24 != null && var24.isEnabled() && (var20 != AutoRestock.class || var24.getInfo() != null)) {
               var1 |= var20 != AutoRestock.class;
               String var25 = var24.getInfo();
               NavigationHud.Section var28 = new NavigationHud.Section(var24.name() + (var25 != null ? " · " + var25 : ""));
               List var30 = var24.details();
               if (var30 != null && !var30.isEmpty()) {
                  var28.line(((String)var30.get(0)).replace("Status: ", ""), -7564380);
               }

               this.sections.add(var28);
            }
         }
      }

      if (this.showWaypoint.get() && (var2 == null || var2.goal() == null)) {
         WaypointManager.Waypoint var18 = null;
         double var21 = Double.MAX_VALUE;

         for (WaypointManager.Waypoint var29 : WaypointManager.get().here()) {
            if (var29.visible) {
               double var31 = Math.hypot(var29.x + 0.5 - mc.field_1724.method_23317(), var29.z + 0.5 - mc.field_1724.method_23321());
               if (var31 < var21 && var31 > 2.0) {
                  var21 = var31;
                  var18 = var29;
               }
            }
         }

         if (var18 != null) {
            NavigationHud.Section var27 = new NavigationHud.Section("Waypoint · " + var18.name);
            var27.line((int)var21 + "m " + arrow(var18.x + 0.5, var18.z + 0.5) + " · Y " + var18.y + " · walk ~" + time((int)(var21 / 4.3)), -1446670);
            this.sections.add(var27);
         }
      }

      String var19 = this.showSafety.get() ? SafeRoute.warning() : null;
      if (var19 != null) {
         NavigationHud.Section var22 = new NavigationHud.Section("SafeRoute");
         var22.line(var19, -495247);
         this.sections.add(var22);
      }

      if (this.onlyActive.get() && !var1) {
         this.sections.clear();
      }
   }

   @Override
   public void onRender2D(class_332 var1, float var2) {
      if (inGame() && !mc.field_1690.field_1842 && !this.sections.isEmpty()) {
         ArrayList var3 = new ArrayList<>(this.sections);
         int var4 = 110;
         int var5 = 4;

         for (NavigationHud.Section var7 : var3) {
            var4 = Math.max(var4, var7.width());
            var5 += var7.height();
         }

         var4 = Math.min(var4, 260);
         float var22 = this.scale.getFloat();
         int var23 = Math.round(var4 * var22);
         int var8 = Math.round(var5 * var22);
         int var9 = var1.method_51421();
         int var10 = var1.method_51443();
         NavigationHud.Corner var11 = this.corner.get();

         int var12 = switch (var11) {
            case TOP_LEFT, BOTTOM_LEFT -> this.offsetX.get();
            case TOP_RIGHT, BOTTOM_RIGHT -> var9 - var23 - this.offsetX.get();
            case TOP_CENTER -> (var9 - var23) / 2 + this.offsetX.get();
         };
         int var13 = var11 != NavigationHud.Corner.BOTTOM_LEFT && var11 != NavigationHud.Corner.BOTTOM_RIGHT
            ? this.offsetY.get()
            : var10 - var8 - this.offsetY.get();
         var12 = Math.max(0, Math.min(var9 - var23, var12));
         var13 = Math.max(0, Math.min(var10 - var8, var13));
         var1.method_51448().pushMatrix();
         var1.method_51448().translate(var12, var13);
         var1.method_51448().scale(var22, var22);

         try {
            HudStyle.panel(var1, 0, 0, var4, var5);
            int var14 = 3;

            for (int var15 = 0; var15 < var3.size(); var15++) {
               NavigationHud.Section var16 = (NavigationHud.Section)var3.get(var15);
               if (var15 > 0) {
                  Gfx.rect(var1, 5, var14 - 2, var4 - 10, 1, 0, 553648127);
               }

               Gfx.text(var1, Gfx.trim(var16.title, var4 - 10), 5, var14 + 1, Theme.accentAt(var15 * 0.15));
               var14 += 11;
               if (var16.progress >= 0.0F) {
                  Gfx.bar(var1, 5, var14, var4 - 10, 3, var16.progress, 1090519039, Theme.accent());
                  var14 += 6;
               }

               for (int var17 = 0; var17 < var16.lines.size(); var17++) {
                  Gfx.text(var1, Gfx.trim(var16.lines.get(var17), var4 - 10), 5, var14, var16.colors.get(var17));
                  var14 += 10;
               }

               var14 += 3;
            }
         } finally {
            var1.method_51448().popMatrix();
         }
      }
   }

   @Override
   public String getInfo() {
      return this.sections.isEmpty() ? null : String.valueOf(this.sections.size());
   }

   public static enum Corner {
      TOP_LEFT,
      TOP_RIGHT,
      BOTTOM_LEFT,
      BOTTOM_RIGHT,
      TOP_CENTER;
   }

   private static final class Section {
      final String title;
      final List<String> lines = new ArrayList<>();
      final List<Integer> colors = new ArrayList<>();
      float progress = -1.0F;
      private int width = -1;

      Section(String var1) {
         this.title = var1;
      }

      void line(String var1, int var2) {
         if (var1 != null && !var1.isEmpty()) {
            this.lines.add(var1);
            this.colors.add(var2);
         }
      }

      int width() {
         if (this.width < 0) {
            int var1 = Gfx.width(this.title) + 10;

            for (String var3 : this.lines) {
               var1 = Math.max(var1, Gfx.width(var3) + 10);
            }

            this.width = var1;
         }

         return this.width;
      }

      int height() {
         return 14 + (this.progress >= 0.0F ? 6 : 0) + this.lines.size() * 10;
      }
   }
}
