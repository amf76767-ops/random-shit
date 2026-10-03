package dev.dihclient.autobuild;

import dev.dihclient.modules.automation.AuctionHouse;
import dev.dihclient.util.InvUtil;
import dev.dihclient.util.ItemUtil;
import dev.dihclient.util.Money;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.class_1661;
import net.minecraft.class_1703;
import net.minecraft.class_1713;
import net.minecraft.class_1735;
import net.minecraft.class_1792;
import net.minecraft.class_1799;
import net.minecraft.class_1802;
import net.minecraft.class_1836;
import net.minecraft.class_2561;
import net.minecraft.class_310;
import net.minecraft.class_465;
import net.minecraft.class_7923;
import net.minecraft.class_1792.class_9635;

public final class AutoBuy {
   private static final class_310 mc = class_310.method_1551();
   private static final String[] NEXT = new String[]{"nächste", "naechste", "weiter", "next", "vorwärts", "vorwaerts", "→", "»", ">>", "forward"};
   private static final String[] PREV = new String[]{"vorherige", "zurück", "zurueck", "previous", "prev", "back", "←", "«", "<<"};
   private static final String[] PRICE = new String[]{
      "preis", "price", "kosten", "cost", "kaufen für", "buy for", "wert", "value", "$", "€", "coins", "geld", "money"
   };
   private static final String[] EACH = new String[]{"pro stück", "pro stueck", "je stück", "each", "per item", "per piece", "/stk", "/stück"};
   private class_1792 item;
   private AutoBuy.Stage stage = AutoBuy.Stage.OPEN;
   private int ticks;
   private int cooldown;
   private int page;
   private int targetPage;
   private String signature = "";
   private final List<AutoBuy.Offer> offers = new ArrayList<>();
   private AutoBuy.Offer best;
   private int countBefore;
   private int reopen;
   private int rescans;
   private long clickedAt;
   private double balanceBefore = Double.NaN;
   private String clickSignature = "";
   private int clickSync = -1;
   private static final String[] BOUGHT = new String[]{"gekauft", "bought", "purchased", "erfolgreich", "du hast", "erhalten"};
   private String status = "";
   private String result = "";
   private boolean bought;
   private class_1792 last;

   public boolean isActive() {
      return this.item != null;
   }

   public void reset() {
      this.item = null;
      this.stage = AutoBuy.Stage.OPEN;
      this.ticks = 0;
   }

   public void begin(class_1792 var1) {
      if (this.cooldown > 0) {
         this.cooldown--;
      } else {
         this.beginNow(var1);
      }
   }

   public void beginNow(class_1792 var1) {
      this.item = var1;
      this.last = var1;
      this.offers.clear();
      this.best = null;
      this.page = 1;
      this.targetPage = 1;
      this.reopen = 0;
      this.rescans = 0;
      this.bought = false;
      this.ticks = 0;
      AuctionHouse var2 = AuctionHouse.get();
      if (var2 == null || !var2.checkBalance.get() || Money.known() && Money.age() <= 30L) {
         this.stage = AutoBuy.Stage.OPEN;
      } else {
         Money.refresh();
         this.stage = AutoBuy.Stage.WAIT_BALANCE;
      }
   }

   public String status() {
      return this.status;
   }

   public String lastResult() {
      return this.result;
   }

   public boolean lastBought() {
      return this.bought;
   }

   public class_1792 lastItem() {
      return this.last;
   }

   private String name() {
      return this.item.method_63680().getString();
   }

   private static List<class_1735> containerSlots(class_1703 var0) {
      ArrayList var1 = new ArrayList();

      for (Object var3 : var0.field_7761) {
         class_1735 var4 = (class_1735)var3;
         if (!(var4.field_7871 instanceof class_1661)) {
            var1.add(var4);
         }
      }

      return var1;
   }

   static List<String> lines(class_1799 var0) {
      ArrayList var1 = new ArrayList();
      var1.add(Money.strip(var0.method_7964().getString()).toLowerCase(Locale.ROOT));

      try {
         List var2 = var0.method_7950(class_9635.field_51353, mc.field_1724, class_1836.field_41070);

         for (int var3 = 1; var3 < var2.size(); var3++) {
            var1.add(Money.strip(((class_2561)var2.get(var3)).getString()).toLowerCase(Locale.ROOT));
         }
      } catch (Throwable var4) {
      }

      return var1;
   }

   private static boolean has(String var0, String[] var1) {
      for (String var5 : var1) {
         if (var0.contains(var5)) {
            return true;
         }
      }

      return false;
   }

   static double unitPrice(class_1799 var0) {
      List var1 = lines(var0);
      int var2 = Math.max(1, var0.method_7947());

      for (int var3 = 1; var3 < var1.size(); var3++) {
         String var4 = (String)var1.get(var3);
         if (has(var4, PRICE) && !var4.contains("verkäufer") && !var4.contains("seller") && !var4.contains("läuft") && !var4.contains("expires")) {
            String var5 = var4.substring(Math.max(0, indexOfPriceWord(var4)));
            double var6 = Money.amountIn(var5);
            if (!Double.isNaN(var6)) {
               if (has(var4, EACH)) {
                  double var8 = var6;

                  for (String var13 : var5.split("[()\\[\\]/|,;]")) {
                     double var14 = Money.amountIn(var13);
                     if (!Double.isNaN(var14) && var14 > 0.0 && var14 < var8) {
                        var8 = var14;
                     }
                  }

                  return var8;
               }

               return var6 / var2;
            }
         }
      }

      return Double.NaN;
   }

   private static int indexOfPriceWord(String var0) {
      int var1 = Integer.MAX_VALUE;

      for (String var5 : PRICE) {
         int var6 = var0.indexOf(var5);
         if (var6 >= 0 && var6 < var1 && !var5.equals("$") && !var5.equals("€")) {
            var1 = var6;
         }
      }

      return var1 == Integer.MAX_VALUE ? 0 : var1;
   }

   private int findButton(List<class_1735> var1, String[] var2) {
      for (class_1735 var4 : var1) {
         class_1799 var5 = var4.method_7677();
         if (!var5.method_7960() && !var5.method_31574(this.item) && has(Money.strip(var5.method_7964().getString()).toLowerCase(Locale.ROOT), var2)) {
            return var4.field_7874;
         }
      }

      return -1;
   }

   private static String signature(List<class_1735> var0) {
      StringBuilder var1 = new StringBuilder();

      for (class_1735 var3 : var0) {
         class_1799 var4 = var3.method_7677();
         var1.append(var4.method_7960() ? "-" : ItemUtil.id(var4) + var4.method_7947() + "@" + unitPrice(var4)).append(',');
      }

      return var1.toString();
   }

   private int count() {
      return InvUtil.count(var1 -> var1.method_31574(this.item));
   }

   private void click(class_1703 var1, int var2) {
      mc.field_1761.method_2906(var1.field_7763, var2, 0, class_1713.field_7790, mc.field_1724);
   }

   public String tick() {
      if (this.item == null) {
         return this.status = "AutoBuy: idle";
      } else {
         this.ticks++;
         AuctionHouse var1 = AuctionHouse.get();
         class_465 var2 = mc.field_1755 instanceof class_465 var3 ? var3 : null;
         switch (this.stage) {
            case WAIT_BALANCE:
               if (Money.known() && Money.age() <= 30L || this.ticks > 80) {
                  this.stage = AutoBuy.Stage.OPEN;
                  this.ticks = 0;
               }

               return this.status = "AutoBuy: reading balance";
            case OPEN:
               String var20 = class_7923.field_41178.method_10221(this.item).method_12832();
               String var21 = var1 == null ? "ah" : var1.command.get().trim().replaceFirst("^/", "");
               this.countBefore = this.count();
               mc.field_1724.field_3944.method_45730(var21 + " " + var20);
               this.stage = AutoBuy.Stage.WAIT_SCREEN;
               this.ticks = 0;
               this.page = 1;
               return this.status = "AutoBuy: /" + var21 + " " + var20;
            case WAIT_SCREEN:
               if (var2 != null && this.ticks > 12) {
                  List var22 = containerSlots(var2.method_17577());
                  boolean var24 = false;

                  for (class_1735 var28 : var22) {
                     var24 |= !var28.method_7677().method_7960();
                  }

                  if (!var24 && this.ticks < 60) {
                     return this.status = "AutoBuy: waiting for the listings";
                  }

                  this.signature = this.best != null ? "" : signature(var22);
                  this.stage = this.best != null ? AutoBuy.Stage.GOTO_PAGE : AutoBuy.Stage.SCAN;
                  this.ticks = 0;
                  return this.status = "AutoBuy: listings open";
               }

               return this.ticks > 100 ? this.finish("AutoBuy: /ah did not open", false) : (this.status = "AutoBuy: waiting for /ah");
            case SCAN:
               if (var2 == null) {
                  return this.finish("AutoBuy: /ah was closed", false);
               } else {
                  List var5 = containerSlots(var2.method_17577());

                  for (class_1735 var25 : var5) {
                     class_1799 var27 = var25.method_7677();
                     if (!var27.method_7960() && var27.method_31574(this.item)) {
                        double var29 = unitPrice(var27);
                        this.offers
                           .add(
                              new AutoBuy.Offer(
                                 this.page, var25.field_7874, var29, Double.isNaN(var29) ? Double.NaN : var29 * var27.method_7947(), var27.method_7947()
                              )
                           );
                     }
                  }

                  int var23 = this.findButton(var5, NEXT);
                  if (this.page < (var1 == null ? 3 : var1.pages.get()) && var23 >= 0 && !var5.isEmpty()) {
                     this.signature = signature(var5);
                     this.click(var2.method_17577(), var23);
                     this.page++;
                     this.stage = AutoBuy.Stage.TURN;
                     this.ticks = 0;
                     return this.status = "AutoBuy: scanning page " + this.page;
                  }

                  return this.choose(var1, var2);
               }
            case TURN:
               if (var2 == null) {
                  return this.finish("AutoBuy: /ah was closed", false);
               } else {
                  boolean var7 = !signature(containerSlots(var2.method_17577())).equals(this.signature);
                  if (var7 && this.ticks > 4) {
                     this.stage = AutoBuy.Stage.SCAN;
                     this.ticks = 0;
                  } else if (this.ticks > 40) {
                     this.page--;
                     return this.choose(var1, var2);
                  }

                  return this.status = "AutoBuy: next page …";
               }
            case GOTO_PAGE:
               if (var2 == null) {
                  return this.finish("AutoBuy: /ah was closed", false);
               } else if (this.ticks < 6) {
                  return this.status;
               } else {
                  List var8 = containerSlots(var2.method_17577());
                  if (!this.signature.isEmpty() && signature(var8).equals(this.signature) && this.ticks < 40) {
                     return this.status = "AutoBuy: going to page " + this.best.page() + " …";
                  } else if (this.page == this.best.page()) {
                     this.stage = AutoBuy.Stage.CLICK;
                     this.ticks = 0;
                     return this.status;
                  } else {
                     int var9 = this.page < this.best.page() ? this.findButton(var8, NEXT) : this.findButton(var8, PREV);
                     if (var9 < 0) {
                        if (this.page > this.best.page() && this.reopen++ < 1) {
                           mc.field_1724.method_7346();
                           this.stage = AutoBuy.Stage.OPEN;
                           this.ticks = 0;
                           return this.status = "AutoBuy: reopening /ah";
                        }

                        return this.finish("AutoBuy: can't get back to page " + this.best.page(), false);
                     }

                     this.signature = signature(var8);
                     this.click(var2.method_17577(), var9);
                     this.page = this.page + (this.page < this.best.page() ? 1 : -1);
                     this.ticks = 0;
                     return this.status = "AutoBuy: going to page " + this.best.page();
                  }
               }
            case CLICK:
               if (var2 == null) {
                  return this.finish("AutoBuy: /ah was closed", false);
               } else if (this.ticks < 4) {
                  return this.status;
               } else {
                  class_1799 var10 = class_1799.field_8037;

                  for (Object var12 : var2.method_17577().field_7761) {
                     if (((class_1735)var12).field_7874 == this.best.slot()) {
                        var10 = ((class_1735)var12).method_7677();
                     }
                  }

                  double var30 = var10.method_7960() ? Double.NaN : unitPrice(var10);
                  boolean var13 = !var10.method_7960()
                     && var10.method_31574(this.item)
                     && (Double.isNaN(this.best.unit()) || !Double.isNaN(var30) && var30 <= this.best.unit() + 1.0E-6);
                  if (!var13) {
                     if (this.rescans++ < 2) {
                        this.offers.clear();
                        this.best = null;
                        mc.field_1724.method_7346();
                        this.stage = AutoBuy.Stage.OPEN;
                        this.ticks = 0;
                        return this.status = "AutoBuy: listing changed – scanning again";
                     }

                     return this.finish("AutoBuy: listing is gone", false);
                  } else {
                     if (mc.field_1724.method_31548().method_7376() < 0) {
                        return this.finish("AutoBuy: inventory full – not buying", false);
                     }

                     this.countBefore = this.count();
                     this.balanceBefore = Money.balance();
                     this.clickedAt = System.currentTimeMillis();
                     this.clickSignature = signature(containerSlots(var2.method_17577()));
                     this.clickSync = var2.method_17577().field_7763;
                     this.click(var2.method_17577(), this.best.slot());
                     this.stage = AutoBuy.Stage.CONFIRM;
                     this.ticks = 0;
                     return this.status = "AutoBuy: buying " + this.best.count() + "x " + this.name() + " for " + Money.format(this.best.total());
                  }
               }
            case CONFIRM:
               if (this.purchased()) {
                  return this.done();
               } else if (this.ticks < 6) {
                  return this.status = "AutoBuy: confirming";
               } else {
                  boolean var14 = var2 != null
                     && (var2.method_17577().field_7763 != this.clickSync || !signature(containerSlots(var2.method_17577())).equals(this.clickSignature))
                     && this.hasItemShown(var2);
                  if (var14) {
                     for (class_1735 var16 : containerSlots(var2.method_17577())) {
                        class_1799 var17 = var16.method_7677();
                        if (!var17.method_7960() && !var17.method_31574(this.item)) {
                           String var18 = lines(var17).get(0);
                           boolean var19 = var18.contains("verkauf")
                              || var18.contains("sell")
                              || var18.contains("abbrechen")
                              || var18.contains("cancel")
                              || var18.contains("zurück")
                              || var18.contains("back");
                           if (!var19
                              && (
                                 var17.method_31574(class_1802.field_8581)
                                    || var17.method_31574(class_1802.field_8656)
                                    || var17.method_31574(class_1802.field_8839)
                                    || var17.method_31574(class_1802.field_8120)
                                    || var18.contains("confirm")
                                    || var18.matches(".*\\bkaufen\\b.*")
                                    || var18.contains("bestätigen")
                                    || var18.contains("bestaetigen")
                                    || var18.matches(".*\\bbuy\\b.*")
                                    || var18.equals("ja")
                                    || var18.equals("yes")
                              )) {
                              this.click(var2.method_17577(), var16.field_7874);
                              this.stage = AutoBuy.Stage.DONE_WAIT;
                              this.ticks = 0;
                              return this.status = "AutoBuy: confirmed";
                           }
                        }
                     }
                  }

                  if (this.ticks > 60) {
                     return this.purchased() ? this.done() : this.finish("AutoBuy: no confirm button / not bought", false);
                  }

                  return this.status = "AutoBuy: confirming";
               }
            case DONE_WAIT:
               if (this.purchased()) {
                  return this.done();
               }

               return this.ticks > 40 ? this.finish("AutoBuy: purchase not confirmed by the server", false) : (this.status = "AutoBuy: waiting for the item");
            default:
               return this.finish("AutoBuy: idle", false);
         }
      }
   }

   private boolean purchased() {
      if (this.count() > this.countBefore) {
         return true;
      } else if (!Double.isNaN(this.balanceBefore) && Money.known() && Money.balance() < this.balanceBefore - 0.001) {
         return true;
      } else {
         for (String var2 : Money.chatSince(this.clickedAt)) {
            if (has(var2, BOUGHT) && !var2.contains("nicht") && !var2.contains("not ") && !var2.contains("kein")) {
               return true;
            }
         }

         return false;
      }
   }

   private boolean hasItemShown(class_465 var1) {
      List var2 = containerSlots(var1.method_17577());

      for (class_1735 var4 : var2) {
         if (var4.method_7677().method_31574(this.item)) {
            return true;
         }
      }

      return var2.size() <= 27;
   }

   private String choose(AuctionHouse var1, class_465 var2) {
      double var3 = var1 == null ? 0.0 : var1.limitFor(this.item);
      boolean var5 = var1 != null && var1.checkBalance.get() && Money.known();
      boolean var6 = var1 != null && var1.unknownPrice.get() && var3 <= 0.0;
      AutoBuy.Offer var7 = null;
      int var8 = 0;
      int var9 = 0;

      for (AutoBuy.Offer var11 : this.offers) {
         if (Double.isNaN(var11.unit())) {
            if (var6 && var7 == null) {
               var7 = var11;
            }
         } else if (var3 > 0.0 && var11.unit() > var3 + 1.0E-6) {
            var8++;
         } else if (var5 && var11.total() > Money.balance() + 1.0E-6) {
            var9++;
         } else if (var7 == null || Double.isNaN(var7.unit()) || var11.unit() < var7.unit()) {
            var7 = var11;
         }
      }

      if (var7 != null) {
         this.best = var7;
         this.reopen = 0;
         this.signature = "";
         this.stage = AutoBuy.Stage.GOTO_PAGE;
         this.ticks = 0;
         return this.status = "AutoBuy: cheapest " + Money.format(var7.unit()) + "/piece on page " + var7.page() + " (" + this.offers.size() + " offers)";
      } else {
         String var12;
         if (this.offers.isEmpty()) {
            var12 = "AutoBuy: no " + this.name() + " in /ah";
         } else if (var9 > 0 && var8 == 0) {
            var12 = "AutoBuy: not enough money for " + this.name() + " (" + Money.format(Money.balance()) + ")";
         } else if (var8 > 0) {
            var12 = "AutoBuy: all " + this.name() + " cost more than " + Money.format(var3) + " each";
         } else {
            var12 = "AutoBuy: can't read the prices of " + this.name();
         }

         return this.finish(var12, false);
      }
   }

   private String done() {
      if (this.best != null && !Double.isNaN(this.best.total()) && Money.known()) {
         Money.refresh();
      }

      return this.finish(
         "AutoBuy: bought "
            + (this.best == null ? "" : this.best.count() + "x ")
            + this.name()
            + (this.best != null && !Double.isNaN(this.best.total()) ? " for " + Money.format(this.best.total()) : ""),
         true
      );
   }

   private String finish(String var1, boolean var2) {
      if (mc.field_1755 instanceof class_465 && mc.field_1724 != null) {
         mc.field_1724.method_7346();
      }

      this.bought = var2;
      this.result = var1;
      this.reset();
      this.cooldown = var2 ? 20 : 100;
      return this.status = var1;
   }

   private record Offer(int page, int slot, double unit, double total, int count) {
   }

   private static enum Stage {
      WAIT_BALANCE,
      OPEN,
      WAIT_SCREEN,
      SCAN,
      TURN,
      GOTO_PAGE,
      CLICK,
      CONFIRM,
      DONE_WAIT;
   }
}
