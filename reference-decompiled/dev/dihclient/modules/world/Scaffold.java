package dev.dihclient.modules.world;

import dev.dihclient.autobuild.PlacementSolver;
import dev.dihclient.module.Category;
import dev.dihclient.module.Module;
import dev.dihclient.setting.BoolSetting;
import dev.dihclient.setting.DoubleSetting;
import dev.dihclient.setting.IntSetting;
import dev.dihclient.util.InvUtil;
import dev.dihclient.util.KeyUtil;
import dev.dihclient.util.MoveUtil;
import java.util.LinkedHashSet;
import net.minecraft.class_1268;
import net.minecraft.class_1269;
import net.minecraft.class_1747;
import net.minecraft.class_1799;
import net.minecraft.class_2248;
import net.minecraft.class_2338;
import net.minecraft.class_2346;
import net.minecraft.class_2350;
import net.minecraft.class_243;
import net.minecraft.class_2680;
import net.minecraft.class_3965;

public class Scaffold extends Module {
   public final BoolSetting autoSwitch = this.bool("Auto Switch", "Selects a block from the hotbar automatically.", true).legacy("scaffold.autoSwitch");
   public final BoolSetting restoreSlot = this.bool("Restore Slot", "Returns to the previous slot when disabled.", true).legacy("scaffold.restoreSlot");
   public final BoolSetting keepY = this.bool("Keep Y", "Keeps bridging at the height where you enabled the module.", false).legacy("scaffold.keepY");
   public final BoolSetting predict = this.bool("Predict", "Also places blocks ahead of you in your movement direction.", true).legacy("scaffold.predict");
   public final DoubleSetting expand = this.dbl("Expand", "How far ahead blocks are placed.", 0.75, 0.0, 4.0, 0.05)
      .legacy("scaffold.expand")
      .visibleWhen(this.predict::get);
   public final IntSetting delay = this.integer("Delay", "Ticks between placements.", 0, 0, 10).legacy("scaffold.delayTicks");
   public final IntSetting perTick = this.integer("Blocks/Tick", "Maximum placements per tick.", 2, 1, 6);
   public final BoolSetting safeEdge = this.bool("Safe Edge", "Sneaks at edges when no block could be placed.", true).legacy("scaffold.safeEdge");
   public final BoolSetting tower = this.bool("Tower", "Holding jump while standing still builds straight up.", true);
   private int previousSlot = -1;
   private int keepYLevel;
   private int wait;
   private boolean sneakForced;

   public Scaffold() {
      super("Scaffold", Category.WORLD, "Fast predictive bridging: places hotbar blocks below you and ahead before you reach the edge.");
   }

   @Override
   protected void onEnable() {
      this.previousSlot = -1;
      if (mc.field_1724 != null) {
         this.keepYLevel = (int)Math.floor(mc.field_1724.method_23318()) - 1;
      }
   }

   @Override
   protected void onDisable() {
      if (mc.field_1724 != null && this.restoreSlot.get() && this.previousSlot >= 0) {
         InvUtil.select(this.previousSlot);
      }

      this.previousSlot = -1;
      this.releaseSneak();
   }

   private static boolean isPlaceable(class_1799 var0) {
      if (!(var0.method_7909() instanceof class_1747 var1)) {
         return false;
      } else {
         class_2248 var4 = var1.method_7711();
         class_2680 var3 = var4.method_9564();
         return var3.method_26234(mc.field_1687, class_2338.field_10980) && !(var4 instanceof class_2346) && !var3.method_31709();
      }
   }

   private boolean ensureBlockInHand() {
      if (isPlaceable(mc.field_1724.method_6047())) {
         return true;
      } else if (!this.autoSwitch.get()) {
         return false;
      } else {
         int var1 = InvUtil.findHotbar(Scaffold::isPlaceable);
         if (var1 < 0) {
            return false;
         } else {
            if (this.previousSlot < 0) {
               this.previousSlot = InvUtil.selectedSlot();
            }

            InvUtil.select(var1);
            return true;
         }
      }
   }

   @Override
   public void onTick() {
      if (mc.field_1755 == null) {
         if (this.wait > 0) {
            this.wait--;
         } else {
            int var1 = this.keepY.get() ? this.keepYLevel : (int)Math.floor(mc.field_1724.method_23318()) - 1;
            if (!this.keepY.get()) {
               this.keepYLevel = var1;
            }

            LinkedHashSet var2 = new LinkedHashSet();
            var2.add(class_2338.method_49637(mc.field_1724.method_23317(), var1, mc.field_1724.method_23321()));
            if (this.predict.get() && MoveUtil.isMoving()) {
               class_243 var3 = mc.field_1724.method_18798();
               double[] var4 = MoveUtil.direction(1.0);

               for (double var5 = 0.25; var5 <= this.expand.get() + 0.01; var5 += 0.25) {
                  var2.add(
                     class_2338.method_49637(
                        mc.field_1724.method_23317() + var4[0] * var5 + var3.field_1352, var1, mc.field_1724.method_23321() + var4[1] * var5 + var3.field_1350
                     )
                  );
               }
            }

            boolean var9 = this.tower.get() && mc.field_1690.field_1903.method_1434() && !MoveUtil.isMoving();
            int var10 = 0;
            boolean var11 = false;

            for (Object var7 : var2) {
               class_2338 var8 = (class_2338)var7;
               if (mc.field_1687.method_8320(var8).method_45474()) {
                  var11 = true;
                  if (!this.ensureBlockInHand()) {
                     break;
                  }

                  if (this.place(var8)) {
                     if (++var10 >= this.perTick.get()) {
                        break;
                     }
                  }
               }
            }

            if (var9 && var10 > 0) {
               class_243 var12 = mc.field_1724.method_18798();
               mc.field_1724.method_18800(var12.field_1352 * 0.3, 0.42, var12.field_1350 * 0.3);
            }

            if (var10 > 0) {
               this.wait = this.delay.get();
            }

            if (this.safeEdge.get() && var11 && var10 == 0 && MoveUtil.edgeAhead(0.3)) {
               mc.field_1690.field_1832.method_23481(true);
               this.sneakForced = true;
            } else {
               this.releaseSneak();
            }
         }
      }
   }

   private void releaseSneak() {
      if (this.sneakForced && mc.field_1690 != null) {
         mc.field_1690.field_1832.method_23481(KeyUtil.isPhysicallyDown(mc.field_1690.field_1832));
         this.sneakForced = false;
      }
   }

   private boolean place(class_2338 var1) {
      for (class_2350 var5 : new class_2350[]{
         class_2350.field_11033, class_2350.field_11043, class_2350.field_11035, class_2350.field_11039, class_2350.field_11034, class_2350.field_11036
      }) {
         class_2338 var6 = var1.method_10093(var5);
         class_2680 var7 = mc.field_1687.method_8320(var6);
         if (!var7.method_45474() && !var7.method_26218(mc.field_1687, var6).method_1110() && !var7.method_31709() && !PlacementSolver.interactive(var7)) {
            class_2350 var8 = var5.method_10153();
            class_243 var9 = class_243.method_24953(var6).method_1031(var8.method_10148() * 0.5, var8.method_10164() * 0.5, var8.method_10165() * 0.5);
            if (!(var9.method_1025(mc.field_1724.method_33571()) > 25.0)) {
               class_1269 var10 = mc.field_1761.method_2896(mc.field_1724, class_1268.field_5808, new class_3965(var9, var8, var6, false));
               if (var10.method_23665()) {
                  mc.field_1724.method_6104(class_1268.field_5808);
                  return true;
               }
            }
         }
      }

      return false;
   }

   @Override
   public String getInfo() {
      return Integer.toString(InvUtil.count(Scaffold::isPlaceable));
   }
}
