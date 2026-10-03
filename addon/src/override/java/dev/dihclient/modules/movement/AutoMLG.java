package dev.dihclient.modules.movement;

import dev.dihclient.module.Category;
import dev.dihclient.module.Module;
import dev.dihclient.setting.BoolSetting;
import dev.dihclient.setting.DoubleSetting;
import dev.dihclient.setting.EnumSetting;
import dev.dihclient.util.InvUtil;
import dev.dihclient.util.ItemUtil;
import dev.dihclient.util.Notifications;
import dev.dihclient.util.RegistryUtil;
import dev.dihclient.util.WalkSafety;
import dev.dihclient.waypoint.WaypointManager;
import java.util.function.Predicate;
import net.minecraft.class_1268;
import net.minecraft.class_1269;
import net.minecraft.class_1799;
import net.minecraft.class_2338;
import net.minecraft.class_2350;
import net.minecraft.class_243;
import net.minecraft.class_2680;
import net.minecraft.class_3965;
import net.minecraft.class_2828.class_2831;

public class AutoMLG extends Module {
   public final EnumSetting<AutoMLG.Method> method = this.mode(
      "Method", "Auto: water bucket, then powder snow, then cobweb. Or force one of them.", AutoMLG.Method.AUTO
   );
   public final DoubleSetting minFall = this.dbl(
      "Min Fall", "Only when the whole fall would be at least this many blocks (3 = first damage).", 4.0, 3.0, 30.0, 0.5
   );
   public final BoolSetting pickUp = this.bool("Pick Up", "Takes the water / powder snow back with the bucket after landing.", true);
   public final BoolSetting restoreSlot = this.bool("Restore Slot", "Returns to the slot you had selected.", true);
   public final BoolSetting notify = this.bool("Notify", "Shows a message when it saved you.", false);
   private static final double REACH_FEET = 4.2;   // bucket reach is 4.5; a fall of 3.9 blocks per tick must not jump over the window
   private AutoMLG.Stage stage = AutoMLG.Stage.IDLE;
   private int stageTicks;
   private int restore = -1;
   private int restoreIn;
   private int cooldown;
   private int saves;
   private int pickTries;
   private String liquid = "";

   public AutoMLG() {
      super("AutoMLG", Category.MOVEMENT, "Water bucket / powder snow / cobweb clutch: lands you safely from any height and picks the bucket up again.");
   }

   @Override
   protected void onEnable() {
      this.stage = AutoMLG.Stage.IDLE;
      this.cooldown = 0;
   }

   @Override
   protected void onDisable() {
      this.giveBackSlot();
      this.stage = AutoMLG.Stage.IDLE;
   }

   private void giveBackSlot() {
      if (mc.field_1724 != null && this.restore >= 0 && this.restoreSlot.get()) {
         InvUtil.select(this.restore);
      }

      this.restore = -1;
      this.restoreIn = 0;
   }

   private static boolean has(String var0, class_1799 var1) {
      return !var1.method_7960() && ItemUtil.id(var1).equals(var0);
   }

   private static Predicate<class_1799> is(String var0) {
      return var1 -> has(var0, var1);
   }

   private static double groundDistance() {
      double var0 = mc.field_1724.method_23318();
      int var2 = (int)Math.floor(mc.field_1724.method_23317());
      int var3 = (int)Math.floor(mc.field_1724.method_23321());
      int var4 = (int)Math.floor(var0 - 0.001);

      for (int var5 = var4; var5 > var4 - 14; var5--) {
         class_2338 var6 = new class_2338(var2, var5, var3);
         class_2680 var7 = mc.field_1687.method_8320(var6);
         if (!var7.method_26227().method_15769() && !WalkSafety.lava(var6)) {
            return Double.NaN;
         }

         if (WalkSafety.solid(var6)) {
            return var0 - (var5 + 1.0);
         }
      }

      return Double.NaN;
   }

   private boolean falling() {
      return !mc.field_1724.method_24828()
         && mc.field_1724.method_18798().field_1351 < -0.45
         && !mc.field_1724.method_6128()
         && !mc.field_1724.method_31549().field_7479
         && !mc.field_1724.method_5799()
         && mc.field_1724.method_5854() == null;
   }

   private String choose() {
      boolean var1 = WaypointManager.dimKey().contains("nether");
      AutoMLG.Method var2 = this.method.get();
      boolean var3 = var2 == AutoMLG.Method.AUTO || var2 == AutoMLG.Method.WATER;
      boolean var4 = var2 == AutoMLG.Method.AUTO || var2 == AutoMLG.Method.POWDER_SNOW;
      boolean var5 = var2 == AutoMLG.Method.AUTO || var2 == AutoMLG.Method.COBWEB;
      if (var3 && !var1 && InvUtil.findHotbar(is("minecraft:water_bucket")) >= 0) {
         return "minecraft:water_bucket";
      } else if (var4 && InvUtil.findHotbar(is("minecraft:powder_snow_bucket")) >= 0) {
         return "minecraft:powder_snow_bucket";
      } else {
         return var5 && InvUtil.findHotbar(is("minecraft:cobweb")) >= 0 ? "minecraft:cobweb" : null;
      }
   }

   @Override
   public void onTick() {
      if (inGame()) {
         if (this.restoreIn > 0 && --this.restoreIn == 0) {
            this.giveBackSlot();
         }

         if (this.cooldown > 0) {
            this.cooldown--;
         }

         switch (this.stage) {
            case IDLE:
               if (this.cooldown == 0 && this.falling()) {
                  double var1 = groundDistance();
                  if (!Double.isNaN(var1) && var1 <= REACH_FEET && var1 > -0.5 && mc.field_1724.field_6017 + Math.max(0.0, var1) >= this.minFall.get()) {
                     String var3 = this.choose();
                     if (var3 != null) {
                        this.place(var3);
                     }
                  }
               }
               break;
            case PLACED:
               this.stageTicks++;
               if (this.stageTicks > 200) {
                  this.stage = AutoMLG.Stage.IDLE;
               } else if (this.stageTicks >= 3 && (mc.field_1724.method_24828() || mc.field_1724.method_5799())) {
                  if (this.pickUp.get() && !this.liquid.equals("minecraft:cobweb")) {
                     this.stage = AutoMLG.Stage.PICKUP;
                     this.stageTicks = 0;
                     this.pickTries = 0;
                  } else {
                     this.stage = AutoMLG.Stage.IDLE;
                     this.cooldown = 10;
                     this.restoreIn = 1;
                  }
               }
               break;
            case PICKUP:
               this.tickPickup();
         }
      }
   }

   private void lookDown() {
      mc.field_1724.field_3944.method_52787(new class_2831(mc.field_1724.method_36454(), 90.0F, mc.field_1724.method_24828(), mc.field_1724.field_5976));
   }

   private void place(String var1) {
      int var2 = InvUtil.findHotbar(is(var1));
      if (var2 >= 0) {
         if (this.restore < 0) {
            this.restore = InvUtil.selectedSlot();
         }

         InvUtil.select(var2);
         float var3 = mc.field_1724.method_36455();
         boolean var4;
         if (var1.equals("minecraft:cobweb")) {
            double var5 = mc.field_1724.method_23318();
            class_2338 var7 = new class_2338(
               (int)Math.floor(mc.field_1724.method_23317()), (int)Math.floor(var5 - groundDistance() - 0.5), (int)Math.floor(mc.field_1724.method_23321())
            );
            this.lookDown();
            class_3965 var8 = new class_3965(class_243.method_24953(var7).method_1031(0.0, 0.5, 0.0), class_2350.field_11036, var7, false);
            class_1269 var9 = mc.field_1761.method_2896(mc.field_1724, class_1268.field_5808, var8);
            var4 = var9.method_23665();
         } else {
            mc.field_1724.method_36457(90.0F);
            this.lookDown();
            class_1269 var10 = mc.field_1761.method_2919(mc.field_1724, class_1268.field_5808);
            var4 = var10.method_23665();
            mc.field_1724.method_36457(var3);
         }

         if (var4) {
            mc.field_1724.method_6104(class_1268.field_5808);
            this.stage = AutoMLG.Stage.PLACED;
            this.stageTicks = 0;
            this.liquid = var1;
            this.saves++;
            if (this.notify.get()) {
               Notifications.info("AutoMLG", "Clutch: " + var1.substring(var1.indexOf(58) + 1).replace('_', ' '));
            }
         } else {
            this.cooldown = 1;   // try again at once, the next tick may already be too late
            this.restoreIn = 1;
         }
      }
   }

   private void tickPickup() {
      this.stageTicks++;
      int var1 = InvUtil.findHotbar(is("minecraft:bucket"));
      if (var1 >= 0 && this.pickTries < 30) {
         class_2338 var2 = mc.field_1724.method_24515();
         if (!this.liquidAround(var2)) {
            this.finishPickup();
         } else {
            InvUtil.select(var1);
            float var3 = mc.field_1724.method_36455();
            mc.field_1724.method_36457(90.0F);
            this.lookDown();
            class_1269 var4 = mc.field_1761.method_2919(mc.field_1724, class_1268.field_5808);
            if (var4.method_23665()) {
               mc.field_1724.method_6104(class_1268.field_5808);
            }

            mc.field_1724.method_36457(var3);
            this.pickTries++;
         }
      } else {
         this.finishPickup();
      }
   }

   private boolean liquidAround(class_2338 var1) {
      for (int var2 = 0; var2 >= -1; var2--) {
         class_2680 var3 = mc.field_1687.method_8320(var1.method_10086(var2));
         if (!var3.method_26227().method_15769() && !WalkSafety.lava(var1.method_10086(var2))) {
            return true;
         }

         if (this.liquid.equals("minecraft:powder_snow_bucket") && RegistryUtil.blockId(var3).equals("minecraft:powder_snow")) {
            return true;
         }
      }

      return false;
   }

   private void finishPickup() {
      this.stage = AutoMLG.Stage.IDLE;
      this.cooldown = 10;
      this.restoreIn = 1;
   }

   @Override
   public String getInfo() {
      return this.saves > 0 ? String.valueOf(this.saves) : null;
   }

   public static enum Method {
      AUTO,
      WATER,
      POWDER_SNOW,
      COBWEB;
   }

   private static enum Stage {
      IDLE,
      PLACED,
      PICKUP;
   }
}
