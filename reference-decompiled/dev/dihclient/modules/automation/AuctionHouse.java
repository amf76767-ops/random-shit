package dev.dihclient.modules.automation;

import dev.dihclient.autobuild.AutoBuy;
import dev.dihclient.module.Category;
import dev.dihclient.module.Module;
import dev.dihclient.module.ModuleManager;
import dev.dihclient.setting.BoolSetting;
import dev.dihclient.setting.DoubleSetting;
import dev.dihclient.setting.IntSetting;
import dev.dihclient.setting.StringSetting;
import dev.dihclient.util.ItemUtil;
import dev.dihclient.util.Money;
import dev.dihclient.util.Notifications;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.class_1792;

public class AuctionHouse extends Module {
   public final StringSetting command = this.text("Command", "Command that opens the auction house for an item (the item name is added).", "ah", 16);
   public final DoubleSetting maxPrice = this.dbl("Max Price / Piece", "Never pays more than this per single item (0 = no limit).", 0.0, 0.0, 100000.0, 1.0);
   public final StringSetting limits = this.text(
      "Item Limits", "Own limits per item, e.g. \"firework_rocket=20, obsidian=50, diamond_block=900\" (price per piece). Overrides Max Price.", "", 256
   );
   public final IntSetting pages = this.integer("Pages To Scan", "How many pages of listings are compared before buying the cheapest.", 3, 1, 10);
   public final BoolSetting checkBalance = this.bool("Check Balance", "Reads your money (scoreboard or /bal) and never buys what you can't afford.", true);
   public final BoolSetting unknownPrice = this.bool(
      "Allow Unknown Price", "Buys listings whose price can't be read from the tooltip (only when there is no price limit).", false
   );
   public final StringSetting buyItem = this.text("Buy Item", "Item for the Buy action (id or name, e.g. \"obsidian\").", "", 64);
   public final IntSetting buyTimes = this.integer("Buy Listings", "How many listings the Buy action buys one after another.", 1, 1, 64);
   private final AutoBuy manual = new AutoBuy();
   private int manualLeft;

   public AuctionHouse() {
      super("AuctionHouse", Category.AUTOMATION, "Smart /ah buyer: scans all listings, only the exact item, cheapest per piece, price limit and balance check.");
      this.action(
         "Buy",
         "Buys the Buy Item (as many listings as set).",
         () -> {
            class_1792 var1 = ItemUtil.item(
               this.buyItem.get().contains(":")
                  ? this.buyItem.get().trim()
                  : "minecraft:" + this.buyItem.get().trim().toLowerCase(Locale.ROOT).replace(' ', '_')
            );
            if (var1 != null && !ItemUtil.id(var1).equals("minecraft:air")) {
               this.manualLeft = this.buyTimes.get();
               this.manual.reset();
               this.manual.beginNow(var1);
               if (!this.isEnabled()) {
                  this.setEnabled(true);
               }
            } else {
               Notifications.warn("AuctionHouse", "Unknown item \"" + this.buyItem.get() + "\"");
            }
         }
      );
      this.action(
         "Read Balance",
         "Reads your money now (scoreboard or /bal).",
         () -> {
            Money.refresh();
            Notifications.info(
               "AuctionHouse", Money.known() ? "Balance: " + Money.format(Money.balance()) + " (" + Money.source() + ")" : "Asking the server (/bal) …"
            );
         }
      );
   }

   public static AuctionHouse get() {
      return ModuleManager.of(AuctionHouse.class);
   }

   public double limitFor(class_1792 var1) {
      String var2 = ItemUtil.id(var1);
      String var3 = var2.substring(var2.indexOf(58) + 1);

      for (String var7 : this.limits.get().split(",")) {
         int var8 = var7.indexOf(61);
         if (var8 > 0) {
            String var9 = var7.substring(0, var8).trim().toLowerCase(Locale.ROOT).replace("minecraft:", "");
            if (var9.equals(var3)) {
               double var10 = Money.amountIn(var7.substring(var8 + 1));
               if (!Double.isNaN(var10)) {
                  return var10;
               }
            }
         }
      }

      return this.maxPrice.get();
   }

   @Override
   public void onTick() {
      if (inGame()) {
         if (this.manual.isActive()) {
            this.manual.tick();
            if (!this.manual.isActive()) {
               if (--this.manualLeft > 0 && this.manual.lastBought()) {
                  this.manual.beginNow(this.manual.lastItem());
               } else {
                  this.manualLeft = 0;
                  Notifications.info("AuctionHouse", this.manual.lastResult());
               }
            }
         }
      }
   }

   @Override
   public List<String> details() {
      ArrayList var1 = new ArrayList();
      var1.add("Balance: " + (Money.known() ? Money.format(Money.balance()) + " (" + Money.source() + ")" : "unknown"));
      if (this.manual.isActive()) {
         var1.add("Buying: " + this.manual.status());
      } else if (!this.manual.lastResult().isEmpty()) {
         var1.add("Last: " + this.manual.lastResult());
      }

      return var1;
   }
}
