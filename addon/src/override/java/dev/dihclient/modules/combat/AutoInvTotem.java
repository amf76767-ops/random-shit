package dev.dihclient.modules.combat;

import dev.dihclient.module.Category;
import dev.dihclient.module.Module;
import dev.dihclient.setting.BoolSetting;
import dev.dihclient.setting.IntSetting;
import java.util.Random;
import net.minecraft.class_1713;
import net.minecraft.class_1799;
import net.minecraft.class_1802;
import net.minecraft.class_490;
import net.minecraft.class_746;

public class AutoInvTotem extends Module {
   public final BoolSetting offhand = this.bool("Offhand", "Puts a totem into the offhand (like hovering it and pressing F).", true);
   public final BoolSetting hotbar = this.bool("Hotbar Totem", "Keeps a totem in one hotbar slot for a quick main-hand totem.", true);
   public final IntSetting hotbarSlot = this.integer("Hotbar Slot", "Hotbar slot (1-9) that should hold a totem.", 9, 1, 9).visibleWhen(this.hotbar::get);
   public final IntSetting openDelay = this.integer("Open Delay", "Ticks to wait after the inventory opens before the first click.", 2, 0, 20);
   public final IntSetting minDelay = this.integer("Min Delay", "Minimum ticks between two clicks.", 1, 0, 20);
   public final IntSetting maxDelay = this.integer("Max Delay", "Maximum ticks between two clicks (random in between = more human).", 3, 0, 20);
   public final BoolSetting autoOpen = this.bool(
      "Auto Open", "Opens your inventory by itself when the offhand totem is gone and you have more (no other screen may be open).", false
   );
   public final BoolSetting autoClose = this.bool("Auto Close", "Closes the inventory again when it opened it and everything is refilled.", true)
      .visibleWhen(this.autoOpen::get);
   public final BoolSetting silent = this.bool(
      "Silent", "Refills the offhand and the hotbar slot without opening the inventory (inventory clicks only, nothing on screen). Off = only while you have the inventory open.", true
   );
   private final Random random = new Random();
   private int openTicks;
   private int cooldown;
   private int reopenCooldown;
   private boolean openedByUs;

   public AutoInvTotem() {
      super(
         "Auto Inv Totem",
         Category.COMBAT,
         "Refills your offhand and a hotbar slot with totems while your inventory is open – hover + swap key with human delays. Auto Open can open the inventory for you after a pop."
      );
   }

   @Override
   protected void onEnable() {
      this.openTicks = 0;
      this.cooldown = 0;
      this.openedByUs = false;
   }

   private static boolean isTotem(class_1799 var0) {
      return var0.method_31574(class_1802.field_8288);
   }

   private int findTotem(boolean var1) {
      class_746 var2 = mc.field_1724;
      int var3 = this.hotbarSlot.get() - 1;

      for (int var4 = 9; var4 < 36; var4++) {
         if (isTotem(var2.method_31548().method_5438(var4))) {
            return var4;
         }
      }

      if (var1) {
         for (int var5 = 0; var5 < 9; var5++) {
            if ((!this.hotbar.get() || var5 != var3) && isTotem(var2.method_31548().method_5438(var5))) {
               return var5;
            }
         }
      }

      return -1;
   }

   private boolean offhandNeeds() {
      return this.offhand.get() && !isTotem(mc.field_1724.method_6079());
   }

   private boolean hotbarNeeds() {
      return this.hotbar.get() && !isTotem(mc.field_1724.method_31548().method_5438(this.hotbarSlot.get() - 1));
   }

   private int nextDelay() {
      int var1 = Math.min(this.minDelay.get(), this.maxDelay.get());
      int var2 = Math.max(this.minDelay.get(), this.maxDelay.get());
      return var1 + (var2 > var1 ? this.random.nextInt(var2 - var1 + 1) : 0);
   }

   private void swap(int var1, int var2) {
      int var3 = var1 < 9 ? 36 + var1 : var1;
      mc.field_1761.method_2906(mc.field_1724.field_7498.field_7763, var3, var2, class_1713.field_7791, mc.field_1724);
   }

   @Override
   public void onTick() {
      if (this.reopenCooldown > 0) {
         this.reopenCooldown--;
      }

      if (this.silent.get() && mc.field_1755 == null) {
         this.openTicks = 0;
         if (this.cooldown > 0) {
            this.cooldown--;
            return;
         }

         if (this.offhandNeeds()) {
            int slot = this.findTotem(true);
            if (slot >= 0) {
               this.swap(slot, 40);
               this.cooldown = this.nextDelay();
               return;
            }
         }

         if (this.hotbarNeeds()) {
            int slot = this.findTotem(false);
            if (slot >= 0) {
               this.swap(slot, this.hotbarSlot.get() - 1);
               this.cooldown = this.nextDelay();
            }
         }

         return;
      }

      if (mc.field_1755 instanceof class_490) {
         if (this.openTicks++ < this.openDelay.get()) {
            return;
         }

         if (this.cooldown > 0) {
            this.cooldown--;
            return;
         }

         if (this.offhandNeeds()) {
            int var1 = this.findTotem(true);
            if (var1 >= 0) {
               this.swap(var1, 40);
               this.cooldown = this.nextDelay();
               return;
            }
         }

         if (this.hotbarNeeds()) {
            int var2 = this.findTotem(false);
            if (var2 >= 0) {
               this.swap(var2, this.hotbarSlot.get() - 1);
               this.cooldown = this.nextDelay();
               return;
            }
         }

         if (this.openedByUs && this.autoClose.get()) {
            this.openedByUs = false;
            this.reopenCooldown = 40;
            mc.field_1724.method_7346();
         }
      } else {
         this.openTicks = 0;
         if (mc.field_1755 != null) {
            this.openedByUs = false;
         } else if (this.autoOpen.get() && this.reopenCooldown == 0 && this.offhandNeeds() && this.findTotem(true) >= 0) {
            this.openedByUs = true;
            this.reopenCooldown = 40;
            mc.method_1507(new class_490(mc.field_1724));
         }
      }
   }

   @Override
   public String getInfo() {
      int var1 = 0;

      for (int var2 = 0; var2 < 36; var2++) {
         if (isTotem(mc.field_1724.method_31548().method_5438(var2))) {
            var1 += mc.field_1724.method_31548().method_5438(var2).method_7947();
         }
      }

      return Integer.toString(var1 + (isTotem(mc.field_1724.method_6079()) ? 1 : 0));
   }
}
