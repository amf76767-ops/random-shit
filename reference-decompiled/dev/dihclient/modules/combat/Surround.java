package dev.dihclient.modules.combat;

import dev.dihclient.module.Category;
import dev.dihclient.module.Module;
import dev.dihclient.render.Render3D;
import dev.dihclient.setting.BoolSetting;
import dev.dihclient.setting.IntSetting;
import dev.dihclient.util.InvUtil;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.class_1268;
import net.minecraft.class_1269;
import net.minecraft.class_1297;
import net.minecraft.class_1309;
import net.minecraft.class_1511;
import net.minecraft.class_1799;
import net.minecraft.class_1802;
import net.minecraft.class_2338;
import net.minecraft.class_2350;
import net.minecraft.class_238;
import net.minecraft.class_243;
import net.minecraft.class_2680;
import net.minecraft.class_3965;
import net.minecraft.class_2350.class_2353;

public class Surround extends Module {
   public final IntSetting perTick = this.integer("Blocks/Tick", "Placements per tick.", 4, 1, 8);
   public final BoolSetting center = this.bool("Center", "Moves you to the middle of the block first.", true);
   public final BoolSetting floor = this.bool("Floor", "Also places a block under you if it is missing.", true);
   public final BoolSetting enderChests = this.bool("Ender Chests", "Use ender chests when you run out of obsidian.", true);
   public final BoolSetting disableOnJump = this.bool("Disable On Jump", "Turns off when you jump or move away.", true);
   public final BoolSetting render = this.bool("Render", "Shows the surround blocks.", true);
   private class_2338 origin;
   private int previousSlot = -1;

   public Surround() {
      super("Surround", Category.COMBAT, "Surrounds your feet with obsidian so crystals can't reach your legs.");
   }

   @Override
   protected void onEnable() {
      this.origin = class_2338.method_49637(mc.field_1724.method_23317(), mc.field_1724.method_23318() + 0.2, mc.field_1724.method_23321());
      this.previousSlot = -1;
      if (this.center.get()) {
         mc.field_1724.method_5814(this.origin.method_10263() + 0.5, mc.field_1724.method_23318(), this.origin.method_10260() + 0.5);
         mc.field_1724.method_18800(0.0, mc.field_1724.method_18798().field_1351, 0.0);
      }
   }

   @Override
   protected void onDisable() {
      if (this.previousSlot >= 0 && mc.field_1724 != null) {
         InvUtil.select(this.previousSlot);
      }

      this.previousSlot = -1;
   }

   private List<class_2338> positions() {
      ArrayList var1 = new ArrayList();
      if (this.floor.get()) {
         var1.add(this.origin.method_10074());
      }

      for (class_2350 var3 : class_2353.field_11062) {
         var1.add(this.origin.method_10093(var3));
      }

      return var1;
   }

   private boolean isBlock(class_1799 var1) {
      return var1.method_31574(class_1802.field_8281)
         || var1.method_31574(class_1802.field_22421)
         || this.enderChests.get() && var1.method_31574(class_1802.field_8466);
   }

   private boolean selectBlock() {
      if (this.isBlock(mc.field_1724.method_6047())) {
         return true;
      } else {
         int var1 = InvUtil.findHotbar((Predicate<class_1799>)(var0 -> var0.method_31574(class_1802.field_8281)));
         if (var1 < 0) {
            var1 = InvUtil.findHotbar(this::isBlock);
         }

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

   private boolean place(class_2338 var1) {
      for (class_1297 var3 : mc.field_1687.method_8335(null, new class_238(var1))) {
         if (var3 instanceof class_1511) {
            mc.field_1761.method_2918(mc.field_1724, var3);
            mc.field_1724.method_6104(class_1268.field_5808);
            return false;
         }

         if (var3 instanceof class_1309 && var3.method_5805() && !var3.method_7325()) {
            return false;
         }
      }

      for (class_2350 var5 : class_2350.values()) {
         class_2338 var6 = var1.method_10093(var5);
         class_2680 var7 = mc.field_1687.method_8320(var6);
         if (!var7.method_45474()) {
            class_2350 var8 = var5.method_10153();
            class_243 var9 = class_243.method_24953(var6).method_1031(var8.method_10148() * 0.5, var8.method_10164() * 0.5, var8.method_10165() * 0.5);
            class_1269 var10 = mc.field_1761.method_2896(mc.field_1724, class_1268.field_5808, new class_3965(var9, var8, var6, false));
            if (var10.method_23665()) {
               mc.field_1724.method_6104(class_1268.field_5808);
               return true;
            }
         }
      }

      return false;
   }

   @Override
   public void onTick() {
      class_2338 var1 = class_2338.method_49637(mc.field_1724.method_23317(), mc.field_1724.method_23318() + 0.2, mc.field_1724.method_23321());
      if (!this.disableOnJump.get() || var1.equals(this.origin) && !mc.field_1690.field_1903.method_1434()) {
         this.origin = var1;
         int var2 = 0;

         for (class_2338 var4 : this.positions()) {
            if (mc.field_1687.method_8320(var4).method_45474()) {
               if (!this.selectBlock()) {
                  return;
               }

               if (this.place(var4)) {
                  if (++var2 >= this.perTick.get()) {
                     break;
                  }
               }
            }
         }
      } else {
         this.setEnabled(false);
      }
   }

   @Override
   public void onRender3D(Render3D var1) {
      if (this.render.get() && this.origin != null) {
         for (class_2338 var3 : this.positions()) {
            boolean var4 = !mc.field_1687.method_8320(var3).method_45474();
            var1.boxOutline(new class_238(var3), var4 ? -12517536 : -49088, false);
         }
      }
   }
}
