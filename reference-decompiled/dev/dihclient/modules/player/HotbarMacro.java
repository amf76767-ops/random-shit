package dev.dihclient.modules.player;

import dev.dihclient.mixin.accessor.MinecraftClientAccessor;
import dev.dihclient.module.Category;
import dev.dihclient.module.Module;
import dev.dihclient.setting.BoolSetting;
import dev.dihclient.setting.IntSetting;
import dev.dihclient.util.InvUtil;
import dev.dihclient.util.Notifications;
import net.minecraft.class_1792;

public class HotbarMacro extends Module {
   private final class_1792 item;
   public final BoolSetting restoreSlot = this.bool("Restore Slot", "Switches back to the previous slot.", true).legacy("keyItem.restoreSlot");
   public final IntSetting delay = this.integer("Delay", "Ticks between pressing the key and the item being used (20 ticks = 1 second).", 0, 0, 40);
   private int pending;
   private int restore = -1;
   private int restoreTicks;

   public HotbarMacro(String var1, String var2, class_1792 var3) {
      super(var1, Category.PLAYER, var2 + " Bind a key to trigger it.");
      this.item = var3;
   }

   @Override
   public boolean isActionModule() {
      return true;
   }

   @Override
   public void onAction() {
      if (inGame() && mc.field_1755 == null) {
         if (this.delay.get() > 0) {
            if (this.pending == 0) {
               this.pending = this.delay.get();
            }
         } else {
            this.use();
         }
      }
   }

   private void use() {
      if (inGame() && mc.field_1755 == null) {
         int var1 = InvUtil.findHotbar(this.item);
         if (var1 < 0) {
            Notifications.warn(this.name(), "No " + this.item.method_63680().getString() + " in hotbar");
         } else {
            int var2 = InvUtil.selectedSlot();
            InvUtil.select(var1);
            ((MinecraftClientAccessor)mc).dih$doItemUse();
            if (this.restoreSlot.get() && var2 != var1) {
               this.restore = var2;
               this.restoreTicks = 1;
            }
         }
      }
   }

   @Override
   public void onTick() {
      if (this.pending > 0 && --this.pending == 0) {
         this.use();
      }

      if (this.restoreTicks > 0 && --this.restoreTicks == 0 && this.restore >= 0) {
         InvUtil.select(this.restore);
         this.restore = -1;
      }
   }
}
