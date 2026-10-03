package dev.dihclient.modules.movement;

import dev.dihclient.module.Category;
import dev.dihclient.module.Module;
import dev.dihclient.setting.BoolSetting;
import dev.dihclient.setting.DoubleSetting;
import dev.dihclient.util.MoveUtil;
import net.minecraft.class_10255;
import net.minecraft.class_1297;

public class BoatFly extends Module {
   public final DoubleSetting speed = this.dbl("Speed", "Horizontal speed in blocks/tick.", 1.0, 0.1, 5.0, 0.1);
   public final DoubleSetting vertical = this.dbl("Vertical", "Up/down speed.", 0.4, 0.05, 2.0, 0.05);
   public final DoubleSetting fall = this.dbl("Glide", "Downward drift when no key is held (0 = hover).", 0.0, 0.0, 0.2, 0.01);
   public final BoolSetting allVehicles = this.bool("All Vehicles", "Also horses, pigs, striders, minecarts …", false);
   public final BoolSetting faceView = this.bool("Face View", "Turns the vehicle where you look.", true);
   private class_1297 vehicle;
   private boolean hadNoGravity;

   public BoatFly() {
      super("BoatFly", Category.MOVEMENT, "Fly with boats or other vehicles. Jump = up, sprint key = down.");
   }

   private void releaseVehicle() {
      if (this.vehicle != null) {
         this.vehicle.method_5875(this.hadNoGravity);
         this.vehicle = null;
      }
   }

   @Override
   protected void onDisable() {
      this.releaseVehicle();
   }

   @Override
   public void onWorldChange() {
      this.vehicle = null;
   }

   @Override
   public void onTick() {
      class_1297 var1 = mc.field_1724.method_5854();
      if (var1 != this.vehicle) {
         this.releaseVehicle();
      }

      if (var1 != null && (this.allVehicles.get() || var1 instanceof class_10255)) {
         if (this.vehicle == null) {
            this.vehicle = var1;
            this.hadNoGravity = var1.method_5740();
            var1.method_5875(true);
         }

         if (this.faceView.get()) {
            var1.method_36456(mc.field_1724.method_36454());
         }

         double var2 = -this.fall.get();
         if (mc.field_1690.field_1903.method_1434()) {
            var2 = this.vertical.get();
         } else if (mc.field_1690.field_1867.method_1434()) {
            var2 = -this.vertical.get();
         }

         double var4 = 0.0;
         double var6 = 0.0;
         if (MoveUtil.isMoving()) {
            double[] var8 = MoveUtil.direction(this.speed.get());
            var4 = var8[0];
            var6 = var8[1];
         }

         var1.method_18800(var4, var2, var6);
         var1.field_6017 = 0.0;
      }
   }

   @Override
   public String getInfo() {
      return String.format("%.1f", this.speed.get());
   }
}
