package dev.dihclient.modules.combat;

import dev.dihclient.mixin.accessor.MinecraftClientAccessor;
import dev.dihclient.module.Category;
import dev.dihclient.module.Module;
import dev.dihclient.setting.BoolSetting;
import dev.dihclient.setting.DoubleSetting;
import dev.dihclient.setting.IntSetting;
import dev.dihclient.util.InvUtil;
import net.minecraft.class_1293;
import net.minecraft.class_1294;
import net.minecraft.class_1799;
import net.minecraft.class_1802;
import net.minecraft.class_1844;
import net.minecraft.class_9334;
import net.minecraft.class_2828.class_2831;

public class AutoPot extends Module {
   public final DoubleSetting health = this.dbl("Health", "Throws a potion below this health.", 10.0, 1.0, 20.0, 0.5).legacy("autoPot.health");
   public final IntSetting delay = this.integer("Delay", "Ticks between two potions.", 20, 1, 100).legacy("autoPot.delayTicks");
   public final BoolSetting restore = this.bool("Restore", "Restores your slot and view afterwards.", true).legacy("autoPot.restore");
   private int cooldown;
   private int restoreSlot = -1;
   private float restorePitch;
   private int restoreTicks;

   public AutoPot() {
      super("AutoPot", Category.COMBAT, "Throws a healing/regeneration splash potion from the hotbar below a health threshold.");
   }

   public static boolean isHealing(class_1799 var0) {
      if (!var0.method_31574(class_1802.field_8436)) {
         return false;
      } else {
         class_1844 var1 = (class_1844)var0.method_58694(class_9334.field_49651);
         if (var1 == null) {
            return false;
         } else {
            for (class_1293 var3 : var1.method_57397()) {
               if (var3.method_5579().equals(class_1294.field_5915) || var3.method_5579().equals(class_1294.field_5924)) {
                  return true;
               }
            }

            return false;
         }
      }
   }

   @Override
   public void onTick() {
      if (this.restoreTicks > 0 && --this.restoreTicks == 0) {
         if (this.restore.get()) {
            if (this.restoreSlot >= 0) {
               InvUtil.select(this.restoreSlot);
            }

            mc.field_1724.method_36457(this.restorePitch);
         }

         this.restoreSlot = -1;
      }

      if (this.cooldown > 0) {
         this.cooldown--;
      } else if (mc.field_1755 == null && !(mc.field_1724.method_6032() > this.health.get())) {
         int var1 = InvUtil.findHotbar(AutoPot::isHealing);
         if (var1 >= 0) {
            this.restoreSlot = InvUtil.selectedSlot();
            this.restorePitch = mc.field_1724.method_36455();
            InvUtil.select(var1);
            mc.field_1724.method_36457(90.0F);
            mc.field_1724.field_3944.method_52787(new class_2831(mc.field_1724.method_36454(), 90.0F, mc.field_1724.method_24828(), mc.field_1724.field_5976));
            ((MinecraftClientAccessor)mc).dih$doItemUse();
            this.restoreTicks = 2;
            this.cooldown = this.delay.get();
         }
      }
   }
}
