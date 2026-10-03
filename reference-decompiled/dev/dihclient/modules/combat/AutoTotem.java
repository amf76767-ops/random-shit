package dev.dihclient.modules.combat;

import dev.dihclient.module.Category;
import dev.dihclient.module.Module;
import dev.dihclient.setting.BoolSetting;
import dev.dihclient.setting.DoubleSetting;
import dev.dihclient.setting.EnumSetting;
import dev.dihclient.setting.IntSetting;
import dev.dihclient.util.InvUtil;
import dev.dihclient.util.Notifications;
import net.minecraft.class_1297;
import net.minecraft.class_1511;
import net.minecraft.class_1799;
import net.minecraft.class_1802;
import net.minecraft.class_465;
import net.minecraft.class_490;
import net.minecraft.class_746;

public class AutoTotem extends Module {
   public final EnumSetting<AutoTotem.Mode> mode = this.mode(
      "Mode", "Strict: always a totem in the offhand. Smart: only when in danger (health, elytra, fall, crystals).", AutoTotem.Mode.STRICT
   );
   public final BoolSetting always = this.bool("Always", "Old setting – same as Mode Strict.", false).legacy("autoTotem.always").visibleWhen(() -> false);
   public final DoubleSetting health = this.dbl("Health", "Smart: equip when health + absorption is at or below this (2 = one heart).", 10.0, 1.0, 36.0, 0.5)
      .legacy("autoTotem.health")
      .visibleWhen(() -> this.mode.get() == AutoTotem.Mode.SMART);
   public final BoolSetting elytra = this.bool("Elytra", "Smart: always hold a totem while gliding.", true)
      .visibleWhen(() -> this.mode.get() == AutoTotem.Mode.SMART);
   public final BoolSetting fall = this.bool("Fall", "Smart: equip when the coming fall would kill you.", true)
      .visibleWhen(() -> this.mode.get() == AutoTotem.Mode.SMART);
   public final BoolSetting crystals = this.bool("Crystals", "Smart: equip when an end crystal is close.", true)
      .visibleWhen(() -> this.mode.get() == AutoTotem.Mode.SMART);
   public final DoubleSetting crystalRange = this.dbl("Crystal Range", "Distance to a crystal that counts as danger.", 7.0, 2.0, 12.0, 0.5)
      .visibleWhen(() -> this.mode.get() == AutoTotem.Mode.SMART && this.crystals.get());
   public final BoolSetting restore = this.bool(
         "Restore Offhand", "Smart: puts your previous offhand item (shield, gapple …) back when the danger is over.", true
      )
      .visibleWhen(() -> this.mode.get() == AutoTotem.Mode.SMART);
   public final IntSetting delay = this.integer("Delay", "Ticks between swaps (a pop is always replaced instantly).", 0, 0, 20);
   public final BoolSetting keepHotbar = this.bool(
      "Keep Hotbar Totems", "Takes totems from the main inventory first so hotbar totems stay for Auto Inv Totem / manual use.", true
   );
   public final BoolSetting popNotify = this.bool("Pop Alert", "Toast with the totems left when one pops.", true);
   public final IntSetting lowWarning = this.integer("Low Warning", "Warns when this many totems or fewer are left (0 = off).", 2, 0, 16);
   private int cooldown;
   private boolean hadTotem;
   private int restoreSlot = -1;
   private int lastCount = -1;
   private boolean warned;

   public AutoTotem() {
      super(
         "AutoTotem",
         Category.COMBAT,
         "Keeps a totem in your offhand. Strict = always, Smart = when in danger (health, elytra, fall, crystals). Pops are replaced instantly."
      );
   }

   @Override
   protected void onEnable() {
      this.cooldown = 0;
      this.restoreSlot = -1;
      this.lastCount = -1;
      this.warned = false;
      this.hadTotem = mc.field_1724 != null && isTotem(mc.field_1724.method_6079());
      if (this.always.get()) {
         this.mode.set(AutoTotem.Mode.STRICT);
         this.always.set(false);
      }
   }

   private static boolean isTotem(class_1799 var0) {
      return var0.method_31574(class_1802.field_8288);
   }

   private boolean inDanger(class_746 var1) {
      float var2 = var1.method_6032() + var1.method_6067();
      if (var2 <= this.health.get()) {
         return true;
      } else if (this.elytra.get() && var1.method_6128()) {
         return true;
      } else {
         if (this.fall.get() && !var1.method_24828() && !var1.method_5799() && !var1.method_5771() && !var1.method_6128()) {
            double var3 = var1.field_6017 - 3.0;
            if (var1.method_18798().field_1351 < -0.5) {
               var3 += 2.0;
            }

            if (var3 >= var2 - 1.0F) {
               return true;
            }
         }

         if (this.crystals.get() && mc.field_1687 != null) {
            double var7 = this.crystalRange.get() * this.crystalRange.get();

            for (class_1297 var6 : mc.field_1687.method_18112()) {
               if (var6 instanceof class_1511 && var6.method_5858(var1) <= var7) {
                  return true;
               }
            }
         }

         return false;
      }
   }

   private int findTotem() {
      class_746 var1 = mc.field_1724;
      if (this.keepHotbar.get()) {
         for (int var2 = 9; var2 < 36; var2++) {
            if (isTotem(var1.method_31548().method_5438(var2))) {
               return var2;
            }
         }
      }

      return InvUtil.findInventory(AutoTotem::isTotem);
   }

   @Override
   public void onTick() {
      class_746 var1 = mc.field_1724;
      boolean var2 = isTotem(var1.method_6079());
      int var3 = InvUtil.count(AutoTotem::isTotem);
      boolean var4 = this.hadTotem && !var2 && var1.method_6079().method_7960();
      this.hadTotem = var2;
      if (var4) {
         this.cooldown = 0;
         this.restoreSlot = -1;
         if (this.popNotify.get()) {
            Notifications.warn("AutoTotem", "Totem popped! " + var3 + " left");
         }
      }

      if (this.lowWarning.get() > 0 && var3 <= this.lowWarning.get() && this.lastCount > var3 && !this.warned) {
         Notifications.alert("ONLY " + var3 + " TOTEM" + (var3 == 1 ? "" : "S") + " LEFT", 60);
         this.warned = true;
      } else if (var3 > this.lowWarning.get()) {
         this.warned = false;
      }

      this.lastCount = var3;
      if (this.cooldown > 0) {
         this.cooldown--;
      } else if (!(mc.field_1755 instanceof class_465) || mc.field_1755 instanceof class_490) {
         boolean var5 = this.mode.get() == AutoTotem.Mode.STRICT || var4 || this.inDanger(var1);
         if (var5) {
            if (!var2) {
               int var6 = this.findTotem();
               if (var6 >= 0) {
                  boolean var7 = !var1.method_6079().method_7960();
                  InvUtil.swapToOffhand(var6);
                  this.restoreSlot = var7 && this.mode.get() == AutoTotem.Mode.SMART && this.restore.get() ? var6 : -1;
                  this.hadTotem = true;
                  this.cooldown = this.delay.get();
               }
            }
         } else if (this.restoreSlot >= 0 && var2) {
            class_1799 var8 = var1.method_31548().method_5438(this.restoreSlot);
            if (!var8.method_7960() && !isTotem(var8)) {
               InvUtil.swapToOffhand(this.restoreSlot);
               this.hadTotem = false;
               this.cooldown = this.delay.get();
            }

            this.restoreSlot = -1;
         }
      }
   }

   @Override
   public String getInfo() {
      return Integer.toString(InvUtil.count(AutoTotem::isTotem));
   }

   public static enum Mode {
      STRICT,
      SMART;
   }
}
