package dev.dihclient.modules.movement;

import dev.dihclient.module.Category;
import dev.dihclient.module.Module;
import dev.dihclient.module.ModuleManager;
import dev.dihclient.setting.BoolSetting;
import dev.dihclient.setting.DoubleSetting;
import dev.dihclient.setting.EnumSetting;
import dev.dihclient.setting.IntSetting;
import dev.dihclient.util.MoveUtil;
import net.minecraft.class_2828.class_2829;

public class PacketFly extends Module {
   public final EnumSetting<PacketFly.Bounds> bounds = this.mode(
      "Bounds",
      "Extra out-of-range packet after each move that makes many servers accept the step and reset instead of rubber-banding.",
      PacketFly.Bounds.ALTERNATE
   );
   public final IntSetting factor = this.integer("Factor", "Steps per tick. Higher = faster, but more packets (too high = kicked / set back).", 2, 1, 10);
   public final DoubleSetting step = this.dbl(
      "Step", "Distance of one step in blocks. 0.0625 is the largest that servers accept without a speed check.", 0.0624, 0.01, 0.2, 0.001
   );
   public final BoolSetting phase = this.bool("Phase", "Walk through blocks (client ignores collisions). Off = only fly.", true);
   public final BoolSetting antiKick = this.bool("Anti Kick", "Drops a little now and then while hovering so \"flying is not enabled\" doesn't kick you.", true);
   public final BoolSetting fullStop = this.bool("Freeze", "Cancels your own movement completely – only the steps move you.", true);
   private int tick;
   private int boundsFlip;

   public PacketFly() {
      super("PacketFly", Category.MOVEMENT, "Phase and fly through blocks with position packets – no boat needed. Server-dependent: raise Factor slowly.");
   }

   public static boolean active() {
      return ModuleManager.on(PacketFly.class) && ModuleManager.of(PacketFly.class).phase.get();
   }

   @Override
   protected void onEnable() {
      this.tick = 0;
      this.boundsFlip = 0;
   }

   @Override
   protected void onDisable() {
      if (mc.field_1724 != null) {
         mc.field_1724.field_5960 = mc.field_1724.method_7325();
      }
   }

   @Override
   public void onTick() {
      if (inGame() && mc.method_1562() != null) {
         this.tick++;
         if (this.phase.get()) {
            mc.field_1724.field_5960 = true;
         } else {
            mc.field_1724.field_5960 = mc.field_1724.method_7325();
         }

         mc.field_1724.field_6017 = 0.0;
         double var1 = this.step.get();
         double[] var3 = MoveUtil.isMoving() ? MoveUtil.direction(var1) : new double[]{0.0, 0.0};
         double var4 = 0.0;
         if (mc.field_1690.field_1903.method_1434()) {
            var4 = var1;
         } else if (mc.field_1690.field_1832.method_1434()) {
            var4 = -var1;
         }

         boolean var6 = var3[0] == 0.0 && var3[1] == 0.0 && var4 == 0.0;
         if (var6 && this.antiKick.get()) {
            var4 = this.tick % 40 == 0 ? -0.04 : 0.0;
         }

         double var7 = mc.field_1724.method_23317();
         double var9 = mc.field_1724.method_23318();
         double var11 = mc.field_1724.method_23321();
         int var13 = this.factor.get();
         if (!var6 || var4 != 0.0) {
            for (int var14 = 0; var14 < var13; var14++) {
               var7 += var3[0];
               var9 += var4;
               var11 += var3[1];
               mc.method_1562().method_52787(new class_2829(var7, var9, var11, false, false));
               this.sendBounds(var7, var9, var11);
            }

            mc.field_1724.method_5814(var7, var9, var11);
         }

         if (this.fullStop.get()) {
            mc.field_1724.method_18800(0.0, 0.0, 0.0);
         }
      }
   }

   private void sendBounds(double var1, double var3, double var5) {
      PacketFly.Bounds var7 = this.bounds.get();
      if (var7 != PacketFly.Bounds.NONE) {
         double var8 = switch (var7) {
            case UP -> 1337.0;
            case DOWN -> -1337.0;
            default -> this.boundsFlip++ % 2 == 0 ? 1337.0 : -1337.0;
         };
         mc.method_1562().method_52787(new class_2829(var1, var3 + var8, var5, false, false));
      }
   }

   @Override
   public String getInfo() {
      return this.factor.get() + "x";
   }

   public static enum Bounds {
      NONE,
      UP,
      DOWN,
      ALTERNATE;
   }
}
