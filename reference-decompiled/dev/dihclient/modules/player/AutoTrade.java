package dev.dihclient.modules.player;

import dev.dihclient.module.Category;
import dev.dihclient.module.Module;
import dev.dihclient.setting.BoolSetting;
import dev.dihclient.setting.IdListSetting;
import dev.dihclient.setting.IntSetting;
import dev.dihclient.util.InvUtil;
import dev.dihclient.util.ItemUtil;
import dev.dihclient.util.Notifications;
import dev.dihclient.util.RegistryUtil;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.class_1703;
import net.minecraft.class_1713;
import net.minecraft.class_1735;
import net.minecraft.class_1792;
import net.minecraft.class_1799;
import net.minecraft.class_3966;
import net.minecraft.class_465;

public class AutoTrade extends Module {
   private static final int IN1 = 0;
   private static final int IN2 = 1;
   private static final int OUT = 2;
   public final IdListSetting only = this.ids(
      "Only Results", "Only repeats trades that give one of these items. Empty list = any trade you pick.", IdListSetting.Kind.ITEM, new String[0]
   );
   public final IntSetting delay = this.integer("Delay", "Ticks between two moves.", 2, 0, 20);
   public final IntSetting keep = this.integer(
      "Keep", "Stops refilling once you have this many or fewer of a price item left (e.g. keep 64 emeralds).", 0, 0, 2304
   );
   public final BoolSetting closeWhenDone = this.bool("Close When Done", "Closes the villager screen when the trade is sold out or items run out.", false);
   private boolean lookingAtTrader;
   private int sinceUse = 1000;
   private class_1792 need1;
   private class_1792 need2;
   private class_1792 result;
   private int wait;
   private int idle;
   private int trades;
   private boolean done;
   private String status = "Pick a trade in the villager screen";

   public AutoTrade() {
      super("AutoTrade", Category.AUTOMATION, "Repeats a villager trade: pick it once, it keeps trading until it is sold out or you run out of items.");
   }

   @Override
   protected void onEnable() {
      this.reset();
      this.trades = 0;
   }

   private void reset() {
      this.need1 = null;
      this.need2 = null;
      this.result = null;
      this.wait = 0;
      this.idle = 0;
      this.done = false;
      this.status = "Pick a trade in the villager screen";
   }

   private static boolean merchant(class_465 var0) {
      String var1 = var0.getClass().getName();
      String var2 = var0.method_17577().getClass().getName();
      return var1.endsWith("class_492") || var2.endsWith("class_1728") || var1.endsWith("MerchantScreen") || var2.endsWith("MerchantScreenHandler");
   }

   private static List<class_1735> slots(class_1703 var0) {
      ArrayList var1 = new ArrayList(40);

      for (Object var3 : var0.field_7761) {
         var1.add((class_1735)var3);
      }

      return var1;
   }

   @Override
   public void onTick() {
      if (inGame()) {
         if (mc.field_1755 == null) {
            String var2 = mc.field_1765 instanceof class_3966 var3 ? RegistryUtil.entityId(var3.method_17782()) : "";
            this.lookingAtTrader = var2.equals("minecraft:villager") || var2.equals("minecraft:wandering_trader");
            this.sinceUse = mc.field_1690.field_1904.method_1434() && this.lookingAtTrader ? 0 : this.sinceUse + 1;
            if (this.need1 != null || this.done) {
               this.reset();
            }
         } else if (mc.field_1755 instanceof class_465 var1
            && (merchant(var1) || this.lookingAtTrader && this.sinceUse < 40 && slots(var1.method_17577()).size() == 39)) {
            class_1703 var7 = var1.method_17577();
            List var8 = slots(var7);
            if (this.done && var8.size() == 39) {
               class_1799 var9 = ((class_1735)var8.get(2)).method_7677();
               class_1799 var5 = ((class_1735)var8.get(0)).method_7677();
               if (!var9.method_7960() && (var9.method_7909() != this.result || !var5.method_7960() && var5.method_7909() != this.need1)) {
                  this.reset();
               }
            }

            if (!this.done && var8.size() == 39) {
               if (this.wait > 0) {
                  this.wait--;
               } else if (this.step(var7, var8)) {
                  this.idle = 0;
                  this.wait = this.delay.get();
               } else if (this.need1 != null && ++this.idle > 20) {
                  this.finish("Trade sold out or items used up");
               }
            }
         }
      }
   }

   private void finish(String var1) {
      this.status = var1 + " (" + this.trades + " trades)";
      Notifications.info("AutoTrade", this.status);
      if (this.closeWhenDone.get()) {
         mc.field_1724.method_7346();
      }

      this.done = true;
   }

   private boolean step(class_1703 var1, List<class_1735> var2) {
      int var3 = var1.field_7763;
      class_1799 var4 = var1.method_34255();
      if (!var4.method_7960()) {
         for (int var8 = 3; var8 < 39; var8++) {
            class_1799 var9 = ((class_1735)var2.get(var8)).method_7677();
            if (var9.method_7960() || var9.method_7909() == var4.method_7909() && var9.method_7947() < var9.method_7909().method_7882()) {
               this.click(var3, var8);
               return true;
            }
         }

         return false;
      } else {
         class_1799 var5 = ((class_1735)var2.get(2)).method_7677();
         class_1799 var6 = ((class_1735)var2.get(0)).method_7677();
         class_1799 var7 = ((class_1735)var2.get(1)).method_7677();
         if (this.need1 != null && !var5.method_7960() && (var5.method_7909() != this.result || !var6.method_7960() && var6.method_7909() != this.need1)) {
            this.need1 = null;
         }

         if (this.need1 == null) {
            if (!var5.method_7960() && !var6.method_7960()) {
               if (!this.only.get().isEmpty() && !this.only.contains(ItemUtil.id(var5))) {
                  this.status = "That trade is not on the Only Results list";
                  return false;
               } else {
                  this.need1 = var6.method_7909();
                  this.need2 = var7.method_7960() ? null : var7.method_7909();
                  this.result = var5.method_7909();
                  this.status = "Trading for " + shortId(ItemUtil.id(var5));
                  this.wait = Math.max(this.delay.get(), 6);
                  return false;
               }
            } else {
               this.status = "Pick a trade in the villager screen";
               return false;
            }
         } else if (!var5.method_7960() && var5.method_7909() == this.result) {
            if (!this.hasRoom(var2, var5)) {
               this.finish("Inventory full");
               return false;
            } else {
               InvUtil.quickMove(var3, 2);
               this.trades++;
               return true;
            }
         } else {
            return this.topUp(var1, var2, var3, 0, this.need1) ? true : this.need2 != null && this.topUp(var1, var2, var3, 1, this.need2);
         }
      }
   }

   private boolean topUp(class_1703 var1, List<class_1735> var2, int var3, int var4, class_1792 var5) {
      class_1799 var6 = ((class_1735)var2.get(var4)).method_7677();
      if (var6.method_7960() || var6.method_7909() == var5 && var6.method_7947() < var6.method_7909().method_7882()) {
         int var7 = 0;

         for (int var8 = 3; var8 < 39; var8++) {
            class_1799 var9 = ((class_1735)var2.get(var8)).method_7677();
            if (var9.method_7909() == var5) {
               var7 += var9.method_7947();
            }
         }

         if (var7 <= this.keep.get()) {
            return false;
         } else {
            for (int var12 = 3; var12 < 39; var12++) {
               class_1799 var13 = ((class_1735)var2.get(var12)).method_7677();
               if (!var13.method_7960() && var13.method_7909() == var5) {
                  int var10 = var6.method_7960() ? 0 : var6.method_7947();
                  this.click(var3, var12);
                  this.click(var3, var4);
                  if (!var1.method_34255().method_7960()) {
                     this.click(var3, var12);
                  }

                  class_1799 var11 = ((class_1735)var2.get(var4)).method_7677();
                  if (!var11.method_7960() && var11.method_7947() > var10) {
                     return true;
                  }

                  this.finish("Can't use those items");
                  return false;
               }
            }

            return false;
         }
      } else {
         return false;
      }
   }

   private boolean hasRoom(List<class_1735> var1, class_1799 var2) {
      for (int var3 = 3; var3 < 39; var3++) {
         class_1799 var4 = ((class_1735)var1.get(var3)).method_7677();
         if (var4.method_7960() || var4.method_7909() == var2.method_7909() && var4.method_7947() + var2.method_7947() <= var4.method_7909().method_7882()) {
            return true;
         }
      }

      return false;
   }

   private static String shortId(String var0) {
      return var0.substring(var0.indexOf(58) + 1);
   }

   private void click(int var1, int var2) {
      mc.field_1761.method_2906(var1, var2, 0, class_1713.field_7790, mc.field_1724);
   }

   @Override
   public String getInfo() {
      return this.trades > 0 ? String.valueOf(this.trades) : null;
   }

   @Override
   public List<String> details() {
      ArrayList var1 = new ArrayList();
      var1.add("Status: " + this.status);
      return var1;
   }
}
