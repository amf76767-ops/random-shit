package dev.dihclient.modules.player;

import dev.dihclient.module.Category;
import dev.dihclient.module.Module;
import dev.dihclient.module.ModuleManager;
import dev.dihclient.modules.combat.AutoInvTotem;
import dev.dihclient.modules.combat.AutoTotem;
import dev.dihclient.setting.BoolSetting;
import dev.dihclient.setting.IntSetting;
import dev.dihclient.util.InvUtil;
import dev.dihclient.util.ItemUtil;
import dev.dihclient.util.Notifications;
import java.util.ArrayList;
import java.util.List;
import java.util.Map.Entry;
import java.util.function.Predicate;
import net.minecraft.class_1268;
import net.minecraft.class_1661;
import net.minecraft.class_1735;
import net.minecraft.class_1799;

public class AutoMend extends Module {
   public final BoolSetting throwBottles = this.bool(
      "Throw Bottles", "Throws Bottles o' Enchanting at your feet. Off = only swaps items, e.g. while you stand at an XP farm.", true
   );
   public final IntSetting bottleDelay = this.integer("Bottle Delay", "Ticks between two bottles.", 2, 0, 20).visibleWhen(this.throwBottles::get);
   public final BoolSetting useOffhand = this.bool(
      "Use Offhand", "Puts damaged Mending tools from your inventory into the offhand one by one. Skipped while AutoTotem keeps a totem there.", true
   );
   public final BoolSetting restore = this.bool("Restore Offhand", "Puts your old offhand item back when everything is repaired.", true);
   public final BoolSetting stopWhenDone = this.bool("Stop When Done", "Turns off when nothing is left to repair (or you run out of bottles).", true);
   public final BoolSetting watch = this.bool(
      "Watch", "Stays on in the background and starts repairing by itself as soon as a Mending item drops below Start At.", false
   );
   public final IntSetting startAt = this.integer("Start At %", "Watch: starts when an item has this much durability left or less.", 30, 1, 95)
      .visibleWhen(this.watch::get);
   public final BoolSetting safeOnly = this.bool("Only When Safe", "Watch: waits while you are being hit.", true).visibleWhen(this.watch::get);
   private boolean active;
   private int idleUntil;
   private int lastHurt;
   private int wait;
   private String restoreId;
   private int previousSlot = -1;
   private boolean warnedTotem;
   private int repaired;
   private String status = "Idle";

   public AutoMend() {
      super("AutoMend", Category.AUTOMATION, "Repairs your Mending tools and armour with XP bottles – swaps them through your offhand one after another.");
   }

   @Override
   protected void onEnable() {
      this.wait = 0;
      this.restoreId = null;
      this.previousSlot = -1;
      this.warnedTotem = false;
      this.repaired = 0;
      this.status = "Idle";
      this.active = false;
      this.idleUntil = 0;
   }

   private int lowestPercent(boolean var1) {
      int var2 = 101;
      ArrayList var3 = new ArrayList();
      var3.add(mc.field_1724.method_6079());
      var3.add(mc.field_1724.method_6047());

      for (int var4 = 0; var1 && var4 < 36; var4++) {
         var3.add(mc.field_1724.method_31548().method_5438(var4));
      }

      int var7 = 0;

      for (Object var6 : mc.field_1724.field_7498.field_7761) {
         if (var7 >= 5 && var7 <= 8) {
            var3.add(((class_1735)var6).method_7677());
         }

         var7++;
      }

      for (class_1799 var9 : var3) {
         if (needsRepair(var9) && var9.method_7936() > 0) {
            var2 = Math.min(var2, (var9.method_7936() - var9.method_7919()) * 100 / var9.method_7936());
         }
      }

      return var2;
   }

   @Override
   protected void onDisable() {
      if (inGame()) {
         this.restoreOffhand();
         if (this.previousSlot >= 0) {
            InvUtil.select(this.previousSlot);
         }
      }

      this.previousSlot = -1;
   }

   public static boolean mending(class_1799 var0) {
      if (!var0.method_7960() && var0.method_7963()) {
         for (Object var2 : var0.method_58657().method_57539()) {
            if (String.valueOf(((Entry)var2).getKey()).contains("minecraft:mending")) {
               return true;
            }
         }

         return false;
      } else {
         return false;
      }
   }

   private static boolean needsRepair(class_1799 var0) {
      return mending(var0) && var0.method_7919() > 0;
   }

   private boolean totemInOffhand() {
      AutoTotem var1 = ModuleManager.of(AutoTotem.class);
      AutoInvTotem var2 = ModuleManager.of(AutoInvTotem.class);
      return var1 != null && var1.isEnabled() || var2 != null && var2.isEnabled() && var2.offhand.get();
   }

   private boolean equippedNeedsRepair() {
      if (!needsRepair(mc.field_1724.method_6079()) && !needsRepair(mc.field_1724.method_6047())) {
         int var1 = 0;

         for (Object var3 : mc.field_1724.field_7498.field_7761) {
            if (var1 >= 5 && var1 <= 8 && needsRepair(((class_1735)var3).method_7677())) {
               return true;
            }

            var1++;
         }

         return false;
      } else {
         return true;
      }
   }

   @Override
   public void onTick() {
      if (inGame() && mc.field_1755 == null) {
         if (this.wait > 0) {
            this.wait--;
         } else {
            if (mc.field_1724.field_6235 > 0) {
               this.lastHurt = mc.field_1724.field_6012;
            }

            boolean var1 = this.useOffhand.get() && !this.totemInOffhand() && !ItemUtil.id(mc.field_1724.method_6079()).equals("minecraft:totem_of_undying");
            if (this.watch.get() && !this.active) {
               if (mc.field_1724.field_6012 % 20 != 0 || mc.field_1724.field_6012 < this.idleUntil) {
                  return;
               }

               int var2 = this.lowestPercent(var1);
               this.status = var2 > 100 ? "Watching – nothing damaged" : "Watching – lowest " + var2 + "%";
               if (var2 > this.startAt.get() || this.safeOnly.get() && mc.field_1724.field_6012 - this.lastHurt < 100) {
                  return;
               }

               this.active = true;
               Notifications.info("AutoMend", "Repairing (an item is at " + var2 + "%)");
            }

            if (this.useOffhand.get() && !var1 && !this.warnedTotem) {
               this.warnedTotem = true;
               Notifications.info("AutoMend", "Totem in the offhand – only repairing held and worn items");
            }

            class_1661 var6 = mc.field_1724.method_31548();
            if (var1 && !needsRepair(mc.field_1724.method_6079())) {
               for (int var3 = 0; var3 < 36; var3++) {
                  class_1799 var4 = var6.method_5438(var3);
                  if (needsRepair(var4) && var3 != InvUtil.selectedSlot()) {
                     if (this.restoreId == null) {
                        class_1799 var5 = mc.field_1724.method_6079();
                        this.restoreId = var5.method_7960() ? "" : ItemUtil.id(var5);
                     } else if (!mc.field_1724.method_6079().method_7960()) {
                        this.repaired++;
                     }

                     InvUtil.swapToOffhand(var3);
                     this.status = "Repairing " + ItemUtil.id(var4).substring(ItemUtil.id(var4).indexOf(58) + 1);
                     this.wait = 2;
                     return;
                  }
               }
            }

            if (!this.equippedNeedsRepair()) {
               this.status = "Everything repaired";
               if (this.restoreOffhand()) {
                  this.wait = 2;
               } else {
                  if (this.watch.get()) {
                     if (this.active) {
                        Notifications.info("AutoMend", "All Mending items repaired – watching again");
                     }

                     this.active = false;
                     if (this.previousSlot >= 0) {
                        InvUtil.select(this.previousSlot);
                        this.previousSlot = -1;
                     }
                  } else if (this.stopWhenDone.get()) {
                     Notifications.push("AutoMend", "All Mending items repaired", Notifications.Type.SUCCESS);
                     this.setEnabled(false);
                  }
               }
            } else {
               if (this.throwBottles.get()) {
                  int var7 = InvUtil.findHotbar((Predicate<class_1799>)(var0 -> ItemUtil.id(var0).equals("minecraft:experience_bottle")));
                  if (var7 < 0) {
                     int var10 = InvUtil.findInventory(var0 -> ItemUtil.id(var0).equals("minecraft:experience_bottle"));
                     if (var10 < 9) {
                        this.status = "Out of XP bottles";
                        if (this.watch.get()) {
                           if (this.active) {
                              Notifications.warn("AutoMend", "Out of XP bottles – trying again in a minute");
                           }

                           this.active = false;
                           this.idleUntil = mc.field_1724.field_6012 + 1200;
                        } else if (this.stopWhenDone.get()) {
                           Notifications.warn("AutoMend", "Out of XP bottles");
                           this.setEnabled(false);
                        }

                        return;
                     }

                     var7 = InvUtil.firstEmptyHotbar();

                     for (int var11 = 0; var7 < 0 && var11 < 9; var11++) {
                        if (var11 != InvUtil.selectedSlot() && !needsRepair(var6.method_5438(var11))) {
                           var7 = var11;
                        }
                     }

                     if (var7 < 0) {
                        var7 = InvUtil.selectedSlot();
                     }

                     InvUtil.swapToHotbar(var10, var7);
                     this.wait = 1;
                     return;
                  }

                  if (this.previousSlot < 0) {
                     this.previousSlot = InvUtil.selectedSlot();
                  }

                  InvUtil.select(var7);
                  float var9 = mc.field_1724.method_36455();
                  mc.field_1724.method_36457(90.0F);
                  mc.field_1761.method_2919(mc.field_1724, class_1268.field_5808);
                  mc.field_1724.method_36457(var9);
                  this.status = "Throwing XP";
                  this.wait = this.bottleDelay.get();
               } else {
                  this.status = "Waiting for XP";
               }
            }
         }
      }
   }

   private boolean restoreOffhand() {
      if (this.restore.get() && this.restoreId != null && !this.totemInOffhand()) {
         String var1 = this.restoreId;
         class_1799 var2 = mc.field_1724.method_6079();
         if (var1.isEmpty() ? !var2.method_7960() : !ItemUtil.id(var2).equals(var1)) {
            int var3 = var1.isEmpty() ? findEmpty() : InvUtil.findInventory(var1x -> ItemUtil.id(var1x).equals(var1));
            this.restoreId = null;
            if (var3 >= 0) {
               InvUtil.swapToOffhand(var3);
               return true;
            } else {
               return false;
            }
         } else {
            this.restoreId = null;
            return false;
         }
      } else {
         return false;
      }
   }

   private static int findEmpty() {
      class_1661 var0 = mc.field_1724.method_31548();

      for (int var1 = 9; var1 < 36; var1++) {
         if (var0.method_5438(var1).method_7960()) {
            return var1;
         }
      }

      return -1;
   }

   @Override
   public List<String> details() {
      ArrayList var1 = new ArrayList();
      var1.add("Status: " + this.status);
      if (this.repaired > 0) {
         var1.add("Finished items: " + this.repaired);
      }

      return var1;
   }
}
