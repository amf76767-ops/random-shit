package dev.dihclient.modules.combat;

import dev.dihclient.DIHClient;
import dev.dihclient.module.Category;
import dev.dihclient.module.Module;
import dev.dihclient.setting.BoolSetting;
import dev.dihclient.setting.DoubleSetting;
import dev.dihclient.setting.IntSetting;
import dev.dihclient.util.RotationUtil;
import net.minecraft.class_1297;
import net.minecraft.class_1309;
import net.minecraft.class_1569;
import net.minecraft.class_1657;
import net.minecraft.class_1753;
import net.minecraft.class_1764;
import net.minecraft.class_1799;
import net.minecraft.class_1802;
import net.minecraft.class_243;
import net.minecraft.class_3532;

public class BowAimbot extends Module {
   public final BoolSetting players = this.bool("Players", "Target players (friends are skipped).", true);
   public final BoolSetting mobs = this.bool("Monsters", "Target hostile mobs.", false);
   public final BoolSetting animals = this.bool("Animals", "Target other living things.", false);
   public final DoubleSetting range = this.dbl("Range", "Maximum target distance.", 60.0, 10.0, 120.0, 1.0);
   public final IntSetting fov = this.integer("FOV", "Only targets within this angle of your crosshair.", 90, 10, 360);
   public final BoolSetting predict = this.bool("Predict", "Leads moving targets.", true);
   public final DoubleSetting smooth = this.dbl("Smoothing", "1 = snap, lower = smoother aim.", 0.6, 0.05, 1.0, 0.05);
   public final BoolSetting throughWalls = this.bool("Through Walls", "Also targets entities you cannot see.", false);
   private class_1309 target;

   public BowAimbot() {
      super("BowAimbot", Category.COMBAT, "Aims bows, crossbows and tridents for you – with arrow drop and movement prediction.");
   }

   private double velocity() {
      class_1799 var1 = mc.field_1724.method_6030();
      if (mc.field_1724.method_6115()) {
         if (var1.method_31574(class_1802.field_8102)) {
            return Math.max(0.1, (double)class_1753.method_7722(mc.field_1724.method_6048())) * 3.0;
         }

         if (var1.method_31574(class_1802.field_8547)) {
            return 2.5;
         }
      }

      class_1799 var2 = mc.field_1724.method_6047();
      return var2.method_31574(class_1802.field_8399) && class_1764.method_7781(var2) ? 3.15 : 0.0;
   }

   private boolean wanted(class_1297 var1) {
      if (!(var1 instanceof class_1309 var2 && var1 != mc.field_1724 && var2.method_5805() && !var1.method_5767())) {
         return false;
      } else if (var1 instanceof class_1657 var3) {
         return this.players.get() && !var3.method_7325() && !DIHClient.social().isFriend(var3);
      } else {
         return var1 instanceof class_1569 ? this.mobs.get() : this.animals.get();
      }
   }

   private float[] solve(class_1309 var1, double var2) {
      class_243 var4 = mc.field_1724.method_33571();
      class_243 var5 = var1.method_73189().method_1031(0.0, var1.method_17682() * 0.5, 0.0);
      if (this.predict.get()) {
         double var6 = var4.method_1022(var5) / var2;
         class_243 var8 = var1.method_73189().method_1023(var1.field_6014, var1.field_6036, var1.field_5969);
         var5 = var5.method_1031(var8.field_1352 * var6, 0.0, var8.field_1350 * var6);
      }

      double var22 = var5.field_1352 - var4.field_1352;
      double var23 = var5.field_1351 - var4.field_1351;
      double var10 = var5.field_1350 - var4.field_1350;
      double var12 = Math.sqrt(var22 * var22 + var10 * var10);
      double var14 = 0.05;
      double var16 = var2 * var2;
      double var18 = var16 * var16 - var14 * (var14 * var12 * var12 + 2.0 * var23 * var16);
      float var20 = (float)Math.toDegrees(Math.atan2(var10, var22)) - 90.0F;
      float var21 = var18 < 0.0 ? -45.0F : (float)(-Math.toDegrees(Math.atan((var16 - Math.sqrt(var18)) / (var14 * var12))));
      return new float[]{class_3532.method_15393(var20), class_3532.method_15363(var21, -90.0F, 90.0F)};
   }

   @Override
   public void onTick() {
      double var1 = this.velocity();
      if (var1 <= 0.0) {
         this.target = null;
      } else {
         class_1309 var3 = null;
         double var4 = Double.MAX_VALUE;

         for (class_1297 var7 : mc.field_1687.method_18112()) {
            if (this.wanted(var7) && !(mc.field_1724.method_5739(var7) > this.range.get()) && (this.throughWalls.get() || mc.field_1724.method_6057(var7))) {
               float[] var8 = this.solve((class_1309)var7, var1);
               double var9 = class_3532.method_15393(var8[0] - mc.field_1724.method_36454());
               double var11 = Math.abs(var9);
               if (!(var11 > this.fov.get().intValue() / 2.0) && var11 < var4) {
                  var4 = var11;
                  var3 = (class_1309)var7;
               }
            }
         }

         this.target = var3;
         if (var3 != null) {
            float[] var13 = this.solve(var3, var1);
            RotationUtil.smoothLook(var13, this.smooth.get());
         }
      }
   }

   @Override
   public String getInfo() {
      return this.target == null ? null : this.target.method_5477().getString();
   }
}
