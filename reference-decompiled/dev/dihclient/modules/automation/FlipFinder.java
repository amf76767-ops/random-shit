package dev.dihclient.modules.automation;

import dev.dihclient.autobuild.AhReader;
import dev.dihclient.autobuild.AutoBuy;
import dev.dihclient.module.Category;
import dev.dihclient.module.Module;
import dev.dihclient.setting.BoolSetting;
import dev.dihclient.setting.DoubleSetting;
import dev.dihclient.setting.IntSetting;
import dev.dihclient.setting.StringSetting;
import dev.dihclient.util.ItemUtil;
import dev.dihclient.util.Money;
import dev.dihclient.util.Notifications;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.class_1792;

public class FlipFinder extends Module {
   public final StringSetting items = this.text(
      "Items", "Items to watch, comma separated, e.g. \"diamond_block, netherite_ingot, totem_of_undying\". Empty = scans the general /ah pages.", "", 256
   );
   public final StringSetting command = this.text("Command", "Command that opens the auction house (the item name is added).", "ah", 16);
   public final IntSetting pages = this.integer("Pages Per Item", "How many /ah pages are read per item.", 3, 1, 10);
   public final IntSetting sortClicks = this.integer(
      "Sort Clicks", "Clicks the sort button this many times after /ah opens (to reach e.g. 'Lowest Price' / 'Recently Listed'). 0 = leave as is.", 0, 0, 6
   );
   public final DoubleSetting margin = this.dbl(
      "Min Margin", "Flip = cheapest listing is at least this far below the typical price (0.30 = 30 % cheaper).", 0.3, 0.05, 0.9, 0.01
   );
   public final DoubleSetting minProfit = this.dbl("Min Profit", "Ignore flips that earn less than this in total.", 0.0, 0.0, 1.0E9, 100.0);
   public final DoubleSetting fee = this.dbl("Sell Fee %", "Fee when you resell (subtracted from the profit). 0 if there is none.", 0.0, 0.0, 30.0, 0.5);
   public final IntSetting minListings = this.integer(
      "Min Listings", "Listings needed in one scan to trust its median. Fewer = only the saved history is used.", 4, 2, 30
   );
   public final IntSetting interval = this.integer(
      "Auto Scan (s)", "Scans again every this many seconds while the module is on. 0 = only with 'Scan Now'.", 0, 0, 3600
   );
   public final IntSetting delay = this.integer(
      "Delay Between Items", "Ticks to wait between two /ah commands (keeps you from being spam-kicked).", 40, 20, 200
   );
   public final BoolSetting autoBuy = this.bool(
      "Auto Buy", "Buys the best flip automatically (uses AuctionHouse limits, balance check and confirm handling). OFF by default.", false
   );
   public final DoubleSetting maxSpend = this.dbl("Max Spend", "Auto Buy never spends more than this on one listing (0 = no limit).", 0.0, 0.0, 1.0E9, 1000.0);
   private FlipFinder.Stage stage = FlipFinder.Stage.IDLE;
   private final List<String> queue = new ArrayList<>();
   private String current = "";
   private int ticks;
   private int page;
   private int sortDone;
   private String signature = "";
   private final List<AhReader.Row> seen = new ArrayList<>();
   private final Map<String, List<AhReader.Row>> byItem = new HashMap<>();
   private final Map<String, double[]> history = new HashMap<>();
   private final List<FlipFinder.Flip> flips = new ArrayList<>();
   private final AutoBuy buyer = new AutoBuy();
   private long nextAuto;
   private String status = "Idle";
   private boolean loaded;

   public FlipFinder() {
      super("FlipFinder", Category.AUTOMATION, "Scans /ah for underpriced listings (flips), keeps a price history, optional auto-buy. Made for DonutSMP.");
      this.action("Scan Now", "Starts a scan of all watched items.", this::startScan);
      this.action("Stop", "Stops the scan.", this::abort);
      this.action("Clear History", "Forgets all saved prices.", () -> {
         this.history.clear();
         this.save();
         Notifications.info("FlipFinder", "Price history cleared");
      });
   }

   private static Path file() {
      return FabricLoader.getInstance().getGameDir().resolve("dihclient").resolve("ah-prices.txt");
   }

   private void load() {
      this.history.clear();

      try {
         for (String var2 : Files.readAllLines(file())) {
            String[] var3 = var2.split("\t");
            if (var3.length >= 3) {
               this.history.put(var3[0], new double[]{Double.parseDouble(var3[1]), Double.parseDouble(var3[2])});
            }
         }
      } catch (Exception var4) {
      }

      this.loaded = true;
   }

   private void save() {
      try {
         Files.createDirectories(file().getParent());
         ArrayList var1 = new ArrayList();

         for (Entry var3 : this.history.entrySet()) {
            var1.add((String)var3.getKey() + "\t" + ((double[])var3.getValue())[0] + "\t" + ((double[])var3.getValue())[1]);
         }

         Files.write(file(), var1);
      } catch (Exception var4) {
      }
   }

   @Override
   protected void onEnable() {
      if (!this.loaded) {
         this.load();
      }

      this.nextAuto = System.currentTimeMillis() + 3000L;
   }

   @Override
   protected void onDisable() {
      this.abort();
   }

   private void abort() {
      this.stage = FlipFinder.Stage.IDLE;
      this.queue.clear();
      this.buyer.reset();
      if (AhReader.open()) {
         AhReader.close();
      }

      this.status = "Idle";
   }

   private void startScan() {
      if (inGame()) {
         if (!this.loaded) {
            this.load();
         }

         if (!this.isEnabled()) {
            this.setEnabled(true);
         }

         this.queue.clear();
         this.byItem.clear();
         this.flips.clear();

         for (String var4 : this.items.get().split(",")) {
            String var5 = var4.trim().toLowerCase(Locale.ROOT).replace("minecraft:", "").replace(' ', '_');
            if (!var5.isEmpty()) {
               this.queue.add(var5);
            }
         }

         if (this.queue.isEmpty()) {
            this.queue.add("");
         }

         this.stage = FlipFinder.Stage.OPEN;
         this.ticks = 0;
         this.status = "Starting scan";
      }
   }

   @Override
   public void onTick() {
      if (inGame()) {
         if (this.buyer.isActive()) {
            this.status = this.buyer.tick();
            if (!this.buyer.isActive()) {
               Notifications.info("FlipFinder", this.buyer.lastResult());
               this.stage = FlipFinder.Stage.IDLE;
            }
         } else {
            this.ticks++;
            switch (this.stage) {
               case IDLE:
                  if (this.interval.get() > 0 && System.currentTimeMillis() >= this.nextAuto && mc.field_1755 == null) {
                     this.startScan();
                  }
                  break;
               case OPEN:
                  if (this.queue.isEmpty()) {
                     this.finishScan();
                  } else {
                     this.current = this.queue.get(0);
                     String var1 = this.command.get().trim().replaceFirst("^/", "");
                     AhReader.command(this.current.isEmpty() ? var1 : var1 + " " + this.current);
                     this.stage = FlipFinder.Stage.WAIT;
                     this.ticks = 0;
                     this.page = 1;
                     this.sortDone = 0;
                     this.seen.clear();
                     this.status = "Opening /" + var1 + " " + this.current;
                  }
                  break;
               case WAIT:
                  if (AhReader.open() && this.ticks > 10 && (AhReader.hasItems() || this.ticks > 60)) {
                     this.stage = this.sortClicks.get() > 0 ? FlipFinder.Stage.SORT : FlipFinder.Stage.SCAN;
                     this.ticks = 0;
                  } else if (this.ticks > 100) {
                     Notifications.warn("FlipFinder", "/ah did not open for \"" + this.current + "\"");
                     this.nextItem();
                  }
                  break;
               case SORT:
                  if (!AhReader.open()) {
                     this.nextItem();
                  } else if (this.ticks % 8 == 0) {
                     if (this.sortDone >= this.sortClicks.get()) {
                        this.stage = FlipFinder.Stage.SCAN;
                        this.ticks = 0;
                     } else {
                        int var5 = AhReader.sortButton();
                        if (var5 < 0) {
                           this.stage = FlipFinder.Stage.SCAN;
                           this.ticks = 0;
                        } else {
                           AhReader.click(var5);
                           this.sortDone++;
                        }
                     }
                  }
                  break;
               case SCAN:
                  if (!AhReader.open()) {
                     this.nextItem();
                  } else {
                     for (AhReader.Row var3 : AhReader.rows()) {
                        if (this.current.isEmpty() || var3.id().endsWith(":" + this.current)) {
                           this.seen.add(new AhReader.Row(var3.id(), this.page * 100 + var3.slot() % 100, var3.count(), var3.unit(), var3.total()));
                        }
                     }

                     int var4 = AhReader.nextButton();
                     if (this.page < this.pages.get() && var4 >= 0) {
                        this.signature = AhReader.signature();
                        AhReader.click(var4);
                        this.page++;
                        this.stage = FlipFinder.Stage.TURN;
                        this.ticks = 0;
                        this.status = "Scanning " + (this.current.isEmpty() ? "/ah" : this.current) + " page " + this.page;
                     } else {
                        this.collect();
                        this.nextItem();
                     }
                  }
                  break;
               case TURN:
                  if (!AhReader.open()) {
                     this.collect();
                     this.nextItem();
                  } else if (this.ticks > 4 && !AhReader.signature().equals(this.signature)) {
                     this.stage = FlipFinder.Stage.SCAN;
                     this.ticks = 0;
                  } else if (this.ticks > 40) {
                     this.collect();
                     this.nextItem();
                  }
                  break;
               case PAUSE:
                  if (this.ticks >= this.delay.get()) {
                     this.stage = FlipFinder.Stage.OPEN;
                     this.ticks = 0;
                  }
            }
         }
      }
   }

   private void collect() {
      for (AhReader.Row var2 : this.seen) {
         this.byItem.computeIfAbsent(var2.id(), var0 -> new ArrayList<>()).add(var2);
      }
   }

   private void nextItem() {
      if (AhReader.open()) {
         AhReader.close();
      }

      if (!this.queue.isEmpty()) {
         this.queue.remove(0);
      }

      this.ticks = 0;
      this.stage = this.queue.isEmpty() ? FlipFinder.Stage.OPEN : FlipFinder.Stage.PAUSE;
   }

   private static double median(List<Double> var0) {
      var0.sort(null);
      int var1 = var0.size();
      return var1 % 2 == 1 ? (Double)var0.get(var1 / 2) : ((Double)var0.get(var1 / 2 - 1) + (Double)var0.get(var1 / 2)) / 2.0;
   }

   private void finishScan() {
      this.stage = FlipFinder.Stage.IDLE;
      this.nextAuto = System.currentTimeMillis() + this.interval.get().intValue() * 1000L;
      Iterator var1 = this.byItem.entrySet().iterator();

      while (true) {
         Entry var2;
         List var3;
         double var8;
         double[] var17;
         double var18;
         while (true) {
            if (!var1.hasNext()) {
               this.save();
               this.flips.sort((var0, var1x) -> Double.compare(var1x.profit(), var0.profit()));
               if (this.flips.isEmpty()) {
                  this.status = "No flips found (" + this.byItem.size() + " items)";
                  Notifications.info("FlipFinder", this.status);
                  return;
               }

               for (int var13 = 0; var13 < Math.min(3, this.flips.size()); var13++) {
                  FlipFinder.Flip var15 = this.flips.get(var13);
                  Notifications.push(
                     "FlipFinder",
                     var15.count()
                        + "x "
                        + var15.id().replace("minecraft:", "")
                        + " @ "
                        + Money.format(var15.unit())
                        + " (typical "
                        + Money.format(var15.ref())
                        + ", +"
                        + Money.format(var15.profit())
                        + ")",
                     Notifications.Type.SUCCESS
                  );
               }

               this.status = this.flips.size() + " flip(s) – best +" + Money.format(this.flips.get(0).profit());
               FlipFinder.Flip var14 = this.flips.get(0);
               if (this.autoBuy.get() && (this.maxSpend.get() <= 0.0 || var14.unit() * var14.count() <= this.maxSpend.get())) {
                  class_1792 var16 = ItemUtil.item(var14.id());
                  if (var16 != null) {
                     this.buyer.beginNow(var16);
                  }
               }

               return;
            }

            var2 = (Entry)var1.next();
            var3 = (List)var2.getValue();
            ArrayList var4 = new ArrayList();

            for (AhReader.Row var6 : var3) {
               var4.add(var6.unit());
            }

            var17 = this.history.get(var2.getKey());
            var18 = var4.size() >= this.minListings.get() ? median(new ArrayList<>(var4)) : Double.NaN;
            if (!Double.isNaN(var18) && var17 != null) {
               var8 = (var18 + var17[0]) / 2.0;
               break;
            }

            if (!Double.isNaN(var18)) {
               var8 = var18;
               break;
            }

            if (var17 != null) {
               var8 = var17[0];
               break;
            }
         }

         if (!Double.isNaN(var18)) {
            double var10 = var17 == null ? var18 : var17[0] * 0.7 + var18 * 0.3;
            this.history.put((String)var2.getKey(), new double[]{var10, System.currentTimeMillis()});
         }

         AhReader.Row var19 = null;

         for (AhReader.Row var12 : var3) {
            if (var19 == null || var12.unit() < var19.unit()) {
               var19 = var12;
            }
         }

         if (var19 != null && var19.unit() <= var8 * (1.0 - this.margin.get())) {
            double var20 = (var8 * (1.0 - this.fee.get() / 100.0) - var19.unit()) * var19.count();
            if (var20 >= this.minProfit.get()) {
               this.flips.add(new FlipFinder.Flip((String)var2.getKey(), var19.count(), var19.unit(), var8, var20, var19.slot() / 100));
            }
         }
      }
   }

   @Override
   public String getInfo() {
      return this.stage == FlipFinder.Stage.IDLE ? null : this.queue.size() + " left";
   }

   @Override
   public List<String> details() {
      ArrayList var1 = new ArrayList();
      var1.add("Status: " + this.status);
      var1.add("Saved prices: " + this.history.size() + " items");

      for (int var2 = 0; var2 < Math.min(5, this.flips.size()); var2++) {
         FlipFinder.Flip var3 = this.flips.get(var2);
         var1.add(
            var3.count()
               + "x "
               + var3.id().replace("minecraft:", "")
               + " "
               + Money.format(var3.unit())
               + " vs "
               + Money.format(var3.ref())
               + " (page "
               + var3.page()
               + ")"
         );
      }

      return var1;
   }

   private record Flip(String id, int count, double unit, double ref, double profit, int page) {
   }

   private static enum Stage {
      IDLE,
      OPEN,
      WAIT,
      SORT,
      SCAN,
      TURN,
      PAUSE,
      BUY;
   }
}
