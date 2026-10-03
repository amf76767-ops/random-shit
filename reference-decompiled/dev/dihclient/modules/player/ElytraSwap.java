package dev.dihclient.modules.player;

import dev.dihclient.mixin.accessor.MinecraftClientAccessor;
import dev.dihclient.module.Category;
import dev.dihclient.module.Module;
import dev.dihclient.util.InvUtil;
import dev.dihclient.util.Notifications;
import java.util.function.Predicate;
import net.minecraft.class_1304;
import net.minecraft.class_1713;
import net.minecraft.class_1799;
import net.minecraft.class_1802;
import net.minecraft.class_3489;

public class ElytraSwap extends Module {
   private int restore = -1;
   private int restoreTicks;

   public ElytraSwap() {
      super("Elytra Swap", Category.PLAYER, "Bind a key: swaps between an elytra and your best chestplate.");
   }

   @Override
   public boolean isActionModule() {
      return true;
   }

   private static boolean isChestplate(class_1799 var0) {
      return var0.method_31573(class_3489.field_48296);
   }

   @Override
   public void onAction() {
      if (inGame() && mc.field_1755 == null) {
         boolean var1 = mc.field_1724.method_6118(class_1304.field_6174).method_31574(class_1802.field_8833);
         Predicate var2 = var1 ? ElytraSwap::isChestplate : var0 -> var0.method_31574(class_1802.field_8833);
         int var3 = InvUtil.findHotbar(var2);
         if (var3 >= 0) {
            int var7 = InvUtil.selectedSlot();
            InvUtil.select(var3);
            ((MinecraftClientAccessor)mc).dih$doItemUse();
            if (var7 != var3) {
               this.restore = var7;
               this.restoreTicks = 1;
            }
         } else {
            int var4 = InvUtil.findInventory(var2);
            if (var4 < 0) {
               Notifications.warn(this.name(), var1 ? "No chestplate found" : "No elytra found");
            } else {
               int var5 = mc.field_1724.field_7498.field_7763;
               int var6 = InvUtil.toScreenSlot(var4);
               mc.field_1761.method_2906(var5, var6, 0, class_1713.field_7790, mc.field_1724);
               mc.field_1761.method_2906(var5, 6, 0, class_1713.field_7790, mc.field_1724);
               mc.field_1761.method_2906(var5, var6, 0, class_1713.field_7790, mc.field_1724);
            }
         }
      }
   }

   @Override
   public void onTick() {
      if (this.restoreTicks > 0 && --this.restoreTicks == 0 && this.restore >= 0) {
         InvUtil.select(this.restore);
         this.restore = -1;
      }
   }
}
