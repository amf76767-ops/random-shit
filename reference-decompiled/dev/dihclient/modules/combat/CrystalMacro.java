package dev.dihclient.modules.combat;

import dev.dihclient.mixin.accessor.MinecraftClientAccessor;
import dev.dihclient.module.Category;
import dev.dihclient.module.Module;
import dev.dihclient.setting.BoolSetting;
import dev.dihclient.setting.IntSetting;
import dev.dihclient.util.InvUtil;
import net.minecraft.class_1268;
import net.minecraft.class_1511;
import net.minecraft.class_1802;
import net.minecraft.class_2246;
import net.minecraft.class_2680;
import net.minecraft.class_3965;
import net.minecraft.class_3966;

public class CrystalMacro extends Module {
   public final BoolSetting requireUseKey = this.bool("Require Use Key", "Only works while the use key (right mouse) is held.", true);
   public final IntSetting delay = this.integer("Delay", "Ticks between two actions.", 2, 0, 10);
   public final BoolSetting restoreSlot = this.bool("Restore Slot", "Switches back to your previous slot afterwards.", true);
   private int cooldown;
   private int previous = -1;
   private int idleTicks;

   public CrystalMacro() {
      super("Crystal Macro", Category.COMBAT, "Places an End Crystal on the obsidian/bedrock you aim at and breaks crystals under your crosshair.");
   }

   @Override
   public void onTick() {
      if (this.cooldown > 0) {
         this.cooldown--;
      } else if (mc.field_1755 == null && (!this.requireUseKey.get() || mc.field_1690.field_1904.method_1434())) {
         if (mc.field_1765 instanceof class_3966 var1 && var1.method_17782() instanceof class_1511 var2) {
            mc.field_1761.method_2918(mc.field_1724, var2);
            mc.field_1724.method_6104(class_1268.field_5808);
            this.cooldown = this.delay.get();
         } else if (mc.field_1765 instanceof class_3965 var7) {
            class_2680 var8 = mc.field_1687.method_8320(var7.method_17777());
            if (!var8.method_27852(class_2246.field_10540) && !var8.method_27852(class_2246.field_9987)) {
               this.idle();
               return;
            }

            int var5 = InvUtil.findHotbar(class_1802.field_8301);
            if (var5 < 0) {
               this.idle();
               return;
            }

            this.idleTicks = 0;
            if (this.previous < 0 && var5 != InvUtil.selectedSlot()) {
               this.previous = InvUtil.selectedSlot();
            }

            InvUtil.select(var5);
            ((MinecraftClientAccessor)mc).dih$doItemUse();
            this.cooldown = this.delay.get();
         } else {
            this.idle();
         }
      } else {
         this.restore();
      }
   }

   private void idle() {
      if (++this.idleTicks >= 10) {
         this.restore();
      }
   }

   private void restore() {
      this.idleTicks = 0;
      if (this.previous >= 0 && this.restoreSlot.get()) {
         InvUtil.select(this.previous);
      }

      this.previous = -1;
   }

   @Override
   protected void onDisable() {
      if (inGame()) {
         this.restore();
      }
   }
}
