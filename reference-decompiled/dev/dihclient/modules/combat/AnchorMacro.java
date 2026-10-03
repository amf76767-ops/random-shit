package dev.dihclient.modules.combat;

import dev.dihclient.mixin.accessor.MinecraftClientAccessor;
import dev.dihclient.module.Category;
import dev.dihclient.module.Module;
import dev.dihclient.setting.BoolSetting;
import dev.dihclient.setting.IntSetting;
import dev.dihclient.util.InvUtil;
import net.minecraft.class_1802;
import net.minecraft.class_2246;
import net.minecraft.class_2680;
import net.minecraft.class_3965;
import net.minecraft.class_4969;

public class AnchorMacro extends Module {
   public final IntSetting delay = this.integer("Delay", "Ticks between two glowstone insertions.", 3, 0, 10);
   public final BoolSetting restoreSlot = this.bool("Restore Slot", "Switches back to the anchor/previous slot after charging.", true);
   private int cooldown;
   private int previous = -1;
   private int restoreIn;

   public AnchorMacro() {
      super("Anchor Macro", Category.COMBAT, "When an uncharged respawn anchor is under your crosshair, one glowstone is inserted automatically.");
   }

   @Override
   public void onTick() {
      if (this.restoreIn > 0 && --this.restoreIn == 0 && this.previous >= 0) {
         if (this.restoreSlot.get()) {
            InvUtil.select(this.previous);
         }

         this.previous = -1;
      }

      if (this.cooldown > 0) {
         this.cooldown--;
      } else if (mc.field_1755 == null && mc.field_1765 instanceof class_3965 var1) {
         class_2680 var4 = mc.field_1687.method_8320(var1.method_17777());
         if (var4.method_27852(class_2246.field_23152) && (Integer)var4.method_11654(class_4969.field_23153) <= 0) {
            int var3 = InvUtil.findHotbar(class_1802.field_8801);
            if (var3 >= 0) {
               if (this.previous < 0) {
                  this.previous = InvUtil.selectedSlot();
               }

               InvUtil.select(var3);
               ((MinecraftClientAccessor)mc).dih$doItemUse();
               this.restoreIn = 2;
               this.cooldown = this.delay.get();
            }
         }
      }
   }
}
