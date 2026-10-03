package dev.dihclient.gui;

import dev.dihclient.hud.HudManager;
import dev.dihclient.modules.render.Waypoints;
import dev.dihclient.modules.world.Goto;
import dev.dihclient.render.Gfx;
import dev.dihclient.util.ColorUtil;
import dev.dihclient.util.Notifications;
import dev.dihclient.waypoint.WaypointManager;
import java.util.Arrays;
import java.util.List;
import net.minecraft.class_11905;
import net.minecraft.class_11908;
import net.minecraft.class_11909;
import net.minecraft.class_243;
import net.minecraft.class_2561;
import net.minecraft.class_310;
import net.minecraft.class_332;
import net.minecraft.class_437;

public class WaypointsScreen extends class_437 implements TextInputScreen {
   private static final int ROW_H = 22;
   private final class_437 parent;
   private final WaypointManager manager = WaypointManager.get();
   private int scroll;
   private WaypointManager.Waypoint selected;
   private WaypointsScreen.Input input;
   private String text = "";
   private String error = "";

   public WaypointsScreen(class_437 var1) {
      super(class_2561.method_43470("Waypoints"));
      this.parent = var1;
   }

   @Override
   public boolean isTyping() {
      return this.input != null;
   }

   public boolean method_25421() {
      return false;
   }

   private int panelX() {
      return this.field_22789 / 2 - 180;
   }

   private int panelW() {
      return 360;
   }

   private int listY() {
      return 76;
   }

   private int visibleRows() {
      return Math.max(1, (this.field_22790 - this.listY() - 76) / 22);
   }

   private List<WaypointManager.Waypoint> list() {
      List var1 = this.manager.here();
      class_310 var2 = class_310.method_1551();
      if (var2.field_1724 != null) {
         class_243 var3 = var2.field_1724.method_73189();
         var1.sort((var1x, var2x) -> Double.compare(dist(var1x, var3), dist(var2x, var3)));
      }

      return var1;
   }

   private static double dist(WaypointManager.Waypoint var0, class_243 var1) {
      class_243 var2 = Waypoints.posHere(var0, true);
      return var2 == null ? 1.0E9 : var2.method_1022(var1);
   }

   private WaypointManager.Waypoint selectedWaypoint(List<WaypointManager.Waypoint> var1) {
      return this.selected != null && var1.contains(this.selected) ? this.selected : null;
   }

   public void method_25394(class_332 var1, int var2, int var3, float var4) {
      super.method_25394(var1, var2, var3, var4);
      int var5 = HudManager.accent();
      int var6 = this.panelX();
      int var7 = this.panelW();
      List var8 = this.list();
      Gfx.panel(var1, var6 - 8, 12, var7 + 16, this.field_22790 - 24, var5);
      Gfx.text(var1, "Waypoints", var6, 20, var5);
      Gfx.text(var1, "Server: " + WaypointManager.worldKey() + " · " + Waypoints.shortDim(WaypointManager.dimKey()), var6, 34, -7564380);
      if (this.input != null) {
         this.drawInput(var1, var2, var3, var6, var7);
      } else {
         int var9 = (var7 - 12) / 4;
         this.button(var1, var2, var3, var6, 50, var9, "+ Here", true);
         this.button(var1, var2, var3, var6 + var9 + 4, 50, var9, "+ Coordinates", true);
         this.button(var1, var2, var3, var6 + 2 * (var9 + 4), 50, var9, "Goto Coords", true);
         this.button(var1, var2, var3, var6 + 3 * (var9 + 4), 50, var9, Goto.running() ? "Stop Goto" : "Goto: idle", Goto.running());
         this.drawList(var1, var2, var3, var6, var7, var8);
         boolean var10 = this.selectedWaypoint(var8) != null;
         int var11 = this.field_22790 - 66;
         int var12 = (var7 - 16) / 5;
         this.button(var1, var2, var3, var6, var11, var12, "Goto", var10);
         this.button(var1, var2, var3, var6 + var12 + 4, var11, var12, var10 && !this.selectedWaypoint(var8).visible ? "Show" : "Hide", var10);
         this.button(var1, var2, var3, var6 + 2 * (var12 + 4), var11, var12, "Rename", var10);
         this.button(var1, var2, var3, var6 + 3 * (var12 + 4), var11, var12, "Colour", var10);
         this.button(var1, var2, var3, var6 + 4 * (var12 + 4), var11, var12, "Delete", var10);
      }

      Gfx.round(var1, var6, this.field_22790 - 40, var7, 16, -14868182);
      String var13 = !this.error.isEmpty() ? this.error : (Goto.running() ? "Goto: " + Goto.statusText() : "Click a waypoint to select it");
      Gfx.text(var1, var13, var6 + 6, this.field_22790 - 36, this.error.isEmpty() ? -7564380 : -43691);
      Gfx.textCentered(var1, "Esc = back", this.field_22789 / 2, this.field_22790 - 18, -7564380);
   }

   private void drawList(class_332 var1, int var2, int var3, int var4, int var5, List<WaypointManager.Waypoint> var6) {
      int var7 = this.visibleRows();
      class_310 var8 = class_310.method_1551();
      class_243 var9 = var8.field_1724 == null ? class_243.field_1353 : var8.field_1724.method_73189();
      if (var6.isEmpty()) {
         Gfx.text(var1, "No waypoints on this server yet – add one above.", var4, this.listY() + 6, -7564380);
      }

      for (int var10 = 0; var10 < var7 && this.scroll + var10 < var6.size(); var10++) {
         int var11 = this.scroll + var10;
         WaypointManager.Waypoint var12 = (WaypointManager.Waypoint)var6.get(var11);
         int var13 = this.listY() + var10 * 22;
         boolean var14 = Gfx.inside(var2, var3, var4, var13, var5, 21);
         boolean var15 = var12 == this.selected;
         Gfx.round(var1, var4, var13, var5, 20, var15 ? ColorUtil.withAlpha(HudManager.accent(), 90) : (var14 ? 822083583 : 419430399));
         Gfx.round(var1, var4 + 4, var13 + 5, 4, 10, var12.color | 0xFF000000);
         Gfx.text(var1, var12.name + (var12.visible ? "" : "  (hidden)"), var4 + 14, var13 + 3, var12.visible ? -1 : -7564380);
         double var16 = dist(var12, var9);
         String var18 = var12.x + " " + var12.y + " " + var12.z + "  ·  " + Waypoints.shortDim(var12.dim) + (var16 < 1.0E8 ? "  ·  " + (int)var16 + "m" : "");
         Gfx.text(var1, var18, var4 + 14, var13 + 12, -7564380);
      }
   }

   private void drawInput(class_332 var1, int var2, int var3, int var4, int var5) {
      String var6 = switch (this.input) {
         case ADD_HERE -> "Name of the new waypoint (at your position):";
         case ADD_COORDS -> "Name and coordinates, e.g.  Base 120 64 -300   (or  Base 120 -300,  ~ = your position):";
         case RENAME -> "New name:";
         case GOTO_COORDS -> "Coordinates to walk to:  x y z  or  x z";
      };
      Gfx.text(var1, var6, var4, 54, -1446670);
      Gfx.round(var1, var4, 68, var5, 20, -14868182);
      String var7 = this.text + (System.currentTimeMillis() / 500L % 2L == 0L ? "_" : "");
      Gfx.text(var1, var7, var4 + 6, 74, -1);
      this.button(var1, var2, var3, var4, 96, 100, "OK (Enter)", true);
      this.button(var1, var2, var3, var4 + 108, 96, 100, "Cancel (Esc)", true);
   }

   private boolean button(class_332 var1, int var2, int var3, int var4, int var5, int var6, String var7, boolean var8) {
      boolean var9 = var8 && Gfx.inside(var2, var3, var4, var5, var6, 18);
      Gfx.round(var1, var4, var5, var6, 18, var8 ? (var9 ? ColorUtil.withAlpha(HudManager.accent(), 150) : -14079703) : -13421773);
      Gfx.textCentered(var1, var7, var4 + var6 / 2, var5 + 5, var8 ? -1 : -9539986);
      return var9;
   }

   private static boolean hit(int var0, int var1, int var2, int var3, int var4) {
      return Gfx.inside(var0, var1, var2, var3, var4, 18);
   }

   private void begin(WaypointsScreen.Input var1, String var2) {
      this.input = var1;
      this.text = var2;
      this.error = "";
   }

   public boolean method_25402(class_11909 var1, boolean var2) {
      int var3 = (int)var1.comp_4798();
      int var4 = (int)var1.comp_4799();
      int var5 = this.panelX();
      int var6 = this.panelW();
      List var7 = this.list();
      if (this.input != null) {
         if (hit(var3, var4, var5, 96, 100)) {
            this.confirm();
         } else if (hit(var3, var4, var5 + 108, 96, 100)) {
            this.input = null;
         }

         return true;
      } else {
         int var8 = (var6 - 12) / 4;
         if (hit(var3, var4, var5, 50, var8)) {
            this.begin(WaypointsScreen.Input.ADD_HERE, this.manager.nextName());
            return true;
         } else if (hit(var3, var4, var5 + var8 + 4, 50, var8)) {
            this.begin(WaypointsScreen.Input.ADD_COORDS, "");
            return true;
         } else if (hit(var3, var4, var5 + 2 * (var8 + 4), 50, var8)) {
            this.begin(WaypointsScreen.Input.GOTO_COORDS, "");
            return true;
         } else if (hit(var3, var4, var5 + 3 * (var8 + 4), 50, var8) && Goto.running()) {
            Goto.stop();
            return true;
         } else {
            int var9 = this.visibleRows();

            for (int var10 = 0; var10 < var9 && this.scroll + var10 < var7.size(); var10++) {
               if (Gfx.inside(var3, var4, var5, this.listY() + var10 * 22, var6, 21)) {
                  this.selected = (WaypointManager.Waypoint)var7.get(this.scroll + var10);
                  return true;
               }
            }

            WaypointManager.Waypoint var15 = this.selectedWaypoint(var7);
            if (var15 != null) {
               int var11 = this.field_22790 - 66;
               int var12 = (var6 - 16) / 5;
               if (hit(var3, var4, var5, var11, var12)) {
                  this.gotoWaypoint(var15);
                  return true;
               }

               if (hit(var3, var4, var5 + var12 + 4, var11, var12)) {
                  var15.visible = !var15.visible;
                  this.manager.save();
                  return true;
               }

               if (hit(var3, var4, var5 + 2 * (var12 + 4), var11, var12)) {
                  this.begin(WaypointsScreen.Input.RENAME, var15.name);
                  return true;
               }

               if (hit(var3, var4, var5 + 3 * (var12 + 4), var11, var12)) {
                  int var13 = 0;

                  for (int var14 = 0; var14 < Waypoints.COLORS.length; var14++) {
                     if ((Waypoints.COLORS[var14] | 0xFF000000) == (var15.color | 0xFF000000)) {
                        var13 = var14 + 1;
                     }
                  }

                  var15.color = Waypoints.COLORS[var13 % Waypoints.COLORS.length];
                  this.manager.save();
                  return true;
               }

               if (hit(var3, var4, var5 + 4 * (var12 + 4), var11, var12)) {
                  this.manager.remove(var15);
                  this.selected = null;
                  return true;
               }
            }

            return super.method_25402(var1, var2);
         }
      }
   }

   private void gotoWaypoint(WaypointManager.Waypoint var1) {
      class_243 var2 = Waypoints.posHere(var1, true);
      if (var2 == null) {
         this.error = "That waypoint is in " + Waypoints.shortDim(var1.dim) + " – you are in " + Waypoints.shortDim(WaypointManager.dimKey());
      } else {
         boolean var3 = var1.dim.equals(WaypointManager.dimKey());
         Goto.start((int)Math.floor(var2.field_1352), var3 ? (int)Math.floor(var2.field_1351) : Integer.MIN_VALUE, (int)Math.floor(var2.field_1350), var1.name);
         this.closeToGame();
      }
   }

   public static int[] parseCoords(String[] var0, int var1) {
      class_310 var2 = class_310.method_1551();
      int var3 = var0.length - var1;
      if (var3 != 2 && var3 != 3) {
         return null;
      } else {
         double[] var4 = var2.field_1724 == null
            ? new double[]{0.0, 64.0, 0.0}
            : new double[]{var2.field_1724.method_23317(), var2.field_1724.method_23318(), var2.field_1724.method_23321()};
         int[] var5 = new int[4];

         try {
            if (var3 == 3) {
               var5[0] = coord(var0[var1], var4[0]);
               var5[1] = coord(var0[var1 + 1], var4[1]);
               var5[2] = coord(var0[var1 + 2], var4[2]);
               var5[3] = 1;
            } else {
               var5[0] = coord(var0[var1], var4[0]);
               var5[1] = (int)Math.floor(var4[1]);
               var5[2] = coord(var0[var1 + 1], var4[2]);
               var5[3] = 0;
            }

            return var5;
         } catch (NumberFormatException var7) {
            return null;
         }
      }
   }

   private static int coord(String var0, double var1) {
      if (var0.startsWith("~")) {
         String var3 = var0.substring(1);
         return (int)Math.floor(var1 + (var3.isEmpty() ? 0.0 : Double.parseDouble(var3)));
      } else {
         return (int)Math.floor(Double.parseDouble(var0));
      }
   }

   private void confirm() {
      String var1 = this.text.trim();
      class_310 var2 = class_310.method_1551();
      switch (this.input) {
         case ADD_HERE:
            if (var2.field_1724 != null) {
               String var9 = var1.isEmpty() ? this.manager.nextName() : var1;
               this.manager
                  .add(
                     var9,
                     (int)Math.floor(var2.field_1724.method_23317()),
                     (int)Math.floor(var2.field_1724.method_23318()),
                     (int)Math.floor(var2.field_1724.method_23321()),
                     Waypoints.COLORS[this.manager.here().size() % Waypoints.COLORS.length]
                  );
               Notifications.info("Waypoints", "Added " + var9);
            }

            this.input = null;
            break;
         case ADD_COORDS:
            String[] var3 = var1.split("\\s+");
            int var4 = 0;

            for (int var5 = 3; var5 >= 2; var5--) {
               if (var3.length >= var5 && parseCoords(var3, var3.length - var5) != null) {
                  var4 = var3.length - var5;
                  break;
               }
            }

            int[] var10 = var3.length >= 2 ? parseCoords(var3, var4) : null;
            if (var10 == null) {
               this.error = "Could not read the coordinates – use: Name x y z";
               return;
            }

            String var6 = var4 == 0 ? this.manager.nextName() : String.join(" ", Arrays.copyOfRange(var3, 0, var4));
            this.manager.add(var6, var10[0], var10[1], var10[2], Waypoints.COLORS[this.manager.here().size() % Waypoints.COLORS.length]);
            Notifications.info("Waypoints", "Added " + var6);
            this.input = null;
            break;
         case RENAME:
            WaypointManager.Waypoint var7 = this.selectedWaypoint(this.list());
            if (var7 != null && !var1.isEmpty()) {
               var7.name = var1;
               this.manager.save();
            }

            this.input = null;
            break;
         case GOTO_COORDS:
            int[] var8 = parseCoords(var1.split("\\s+"), 0);
            if (var8 == null) {
               this.error = "Could not read the coordinates – use: x y z  or  x z";
               return;
            }

            this.input = null;
            Goto.start(var8[0], var8[3] == 1 ? var8[1] : Integer.MIN_VALUE, var8[2], var8[0] + " " + (var8[3] == 1 ? var8[1] + " " : "") + var8[2]);
            this.closeToGame();
      }
   }

   public boolean method_25401(double var1, double var3, double var5, double var7) {
      int var9 = this.manager.here().size();
      this.scroll = Math.max(0, Math.min(Math.max(0, var9 - this.visibleRows()), this.scroll - (int)(var7 * 2.0)));
      return true;
   }

   public boolean method_25404(class_11908 var1) {
      int var2 = var1.comp_4795();
      if (this.input != null) {
         if (var2 == 259) {
            if (!this.text.isEmpty()) {
               this.text = this.text.substring(0, this.text.length() - 1);
            }

            return true;
         } else if (var2 == 257 || var2 == 335) {
            this.confirm();
            return true;
         } else if (var2 == 256) {
            this.input = null;
            return true;
         } else {
            return true;
         }
      } else if (var2 == 256) {
         this.method_25419();
         return true;
      } else {
         return super.method_25404(var1);
      }
   }

   public boolean method_25400(class_11905 var1) {
      if (this.input != null && var1.method_74227()) {
         String var2 = var1.method_74226();
         if (var2 != null && !var2.equals("\n") && this.text.length() < 64) {
            this.text = this.text + var2;
            this.error = "";
         }

         return true;
      } else {
         return false;
      }
   }

   private void closeToGame() {
      if (this.field_22787 != null) {
         this.field_22787.method_1507(null);
      }
   }

   public void method_25419() {
      if (this.field_22787 != null) {
         this.field_22787.method_1507(this.parent);
      }
   }

   private static enum Input {
      ADD_HERE,
      ADD_COORDS,
      RENAME,
      GOTO_COORDS;
   }
}
