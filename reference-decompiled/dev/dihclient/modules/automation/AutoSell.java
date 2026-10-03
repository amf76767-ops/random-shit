package dev.dihclient.modules.automation;

import dev.dihclient.module.Category;
import dev.dihclient.module.Module;
import dev.dihclient.setting.BoolSetting;
import dev.dihclient.setting.EnumSetting;
import dev.dihclient.setting.IdListSetting;
import dev.dihclient.setting.IntSetting;
import dev.dihclient.setting.StringSetting;
import dev.dihclient.util.ItemUtil;
import dev.dihclient.util.Money;
import dev.dihclient.util.Notifications;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.class_1661;
import net.minecraft.class_1703;
import net.minecraft.class_1713;
import net.minecraft.class_1735;
import net.minecraft.class_1799;
import net.minecraft.class_465;

public class AutoSell extends Module {
   public final StringSetting command = this.text("Command", "Command that opens the sell container.", "sell", 16);
   public final EnumSetting<AutoSell.Mode> mode = this.mode(
      "Sell", "List: only the items in Sell Items. Everything: everything except Keep Items, hotbar and tools.", AutoSell.Mode.LIST
   );
   public final IdListSetting items = this.ids("Sell Items", "Items that are sold (mode List).", IdListSetting.Kind.ITEM, new String[0]);
   public final IdListSetting keep = this.ids(
         "Keep Items",
         "Never sold (mode Everything).",
         IdListSetting.Kind.ITEM,
         new String[]{
            "minecraft:ender_pearl",
            "minecraft:totem_of_undying",
            "minecraft:golden_apple",
            "minecraft:enchanted_golden_apple",
            "minecraft:elytra",
            "minecraft:firework_rocket",
            "minecraft:shulker_box",
            "minecraft:ender_chest",
            "minecraft:experience_bottle",
            "minecraft:obsidian"
         }
      )
      .visibleWhen(() -> this.mode.get() == AutoSell.Mode.EVERYTHING);
   public final BoolSetting keepHotbar = this.bool("Keep Hotbar", "Never sells what is in your hotbar.", true);
   public final BoolSetting keepTools = this.bool("Keep Tools & Armor", "Never sells anything with durability.", true);
   public final IntSetting fill = this.integer("Fill %", "Clicks the sell button when the container is this full.", 50, 10, 95);
   public final IntSetting delay = this.integer("Delay", "Ticks between two item moves.", 2, 0, 10);
   public final BoolSetting whenFull = this.bool("Auto When Full", "Starts selling by itself when your inventory is full.", false);
   private AutoSell.Stage stage = AutoSell.Stage.IDLE;
   private int ticks;
   private int wait;
   private int placed;
   private int soldStacks;
   private int rounds;
   private double moneyBefore = Double.NaN;
   private long lastAuto;
   private String status = "Idle";
   private final Set<Integer> refused = new HashSet<>();
   private final Map<Integer, Integer> tries = new HashMap<>();
   private int moves;
   private int baseFilled;

   public AutoSell() {
      super("AutoSell", Category.AUTOMATION, "Sells your items with /sell: fills the sell container halfway, clicks the sell button, repeats.");
      this.action("Sell Now", "Sells everything sellable in your inventory now.", this::start);
   }

   private void start() {
      if (inGame()) {
         if (!this.sellableSlots(null).isEmpty()) {
            if (!this.isEnabled()) {
               this.setEnabled(true);
            }

            Money.refresh();
            this.moneyBefore = Money.balance();
            this.refused.clear();
            this.tries.clear();
            this.moves = 0;
            this.soldStacks = 0;
            this.rounds = 0;
            this.stage = AutoSell.Stage.OPEN;
            this.ticks = 0;
         } else {
            Notifications.info(
               "AutoSell", this.mode.get() == AutoSell.Mode.LIST && this.items.get().isEmpty() ? "Add items to Sell Items first" : "Nothing to sell"
            );
         }
      }
   }

   @Override
   protected void onDisable() {
      if (this.stage != AutoSell.Stage.IDLE && mc.field_1755 instanceof class_465 && mc.field_1724 != null) {
         mc.field_1724.method_7346();
      }

      this.stage = AutoSell.Stage.IDLE;
      this.status = "Idle";
   }

   private List<Integer> sellableSlots(class_1703 var1) {
      ArrayList var2 = new ArrayList();
      if (var1 == null) {
         for (int var3 = 0; var3 < 36; var3++) {
            if (!this.refused.contains(var3) && this.sellable(mc.field_1724.method_31548().method_5438(var3), var3 < 9)) {
               var2.add(var3);
            }
         }
      } else {
         for (Object var4 : var1.field_7761) {
            class_1735 var5 = (class_1735)var4;
            int var6 = var5.method_34266();
            if (var5.field_7871 instanceof class_1661 && var6 < 36 && !this.refused.contains(var6) && this.sellable(var5.method_7677(), var6 < 9)) {
               var2.add(var5.field_7874);
            }
         }
      }

      return var2;
   }

   private boolean sellable(class_1799 var1, boolean var2) {
      if (var1.method_7960()) {
         return false;
      } else if (var2 && this.keepHotbar.get()) {
         return false;
      } else if (this.keepTools.get() && var1.method_7963()) {
         return false;
      } else {
         String var3 = ItemUtil.id(var1);
         return this.mode.get() == AutoSell.Mode.LIST ? this.items.contains(var3) : !this.keep.contains(var3);
      }
   }

   @Override
   public void onTick() {
      if (inGame()) {
         if (this.stage == AutoSell.Stage.IDLE) {
            if (this.whenFull.get()
               && mc.field_1755 == null
               && mc.field_1724.method_31548().method_7376() < 0
               && System.currentTimeMillis() - this.lastAuto > 30000L
               && !this.sellableSlots(null).isEmpty()) {
               this.lastAuto = System.currentTimeMillis();
               this.start();
            }
         } else {
            this.ticks++;
            class_465 var1 = mc.field_1755 instanceof class_465 var2 ? var2 : null;
            switch (this.stage) {
               case OPEN:
                  mc.field_1724.field_3944.method_45730(this.command.get().trim().replaceFirst("^/", ""));
                  this.stage = AutoSell.Stage.WAIT_SCREEN;
                  this.ticks = 0;
                  this.status = "Opening /" + this.command.get();
                  break;
               case WAIT_SCREEN:
                  if (var1 != null && this.ticks > 6) {
                     this.stage = AutoSell.Stage.FILL;
                     this.ticks = 0;
                     this.placed = 0;
                     this.baseFilled = filled(var1.method_17577());
                  } else if (this.ticks > 100) {
                     this.done("/" + this.command.get() + " did not open");
                  }
                  break;
               case FILL:
                  if (var1 == null) {
                     this.done("The sell container was closed");
                  } else if (this.wait > 0) {
                     this.wait--;
                  } else {
                     this.fillStep(var1.method_17577());
                  }
                  break;
               case AFTER_SELL:
                  if (this.ticks > 12) {
                     if (this.sellableSlots(null).isEmpty() || this.rounds > 60) {
                        this.done(null);
                     } else if (var1 != null) {
                        if (filled(var1.method_17577()) > this.baseFilled && this.ticks < 60) {
                           this.status = "Waiting for the server to sell";
                        } else {
                           this.stage = AutoSell.Stage.FILL;
                           this.placed = 0;
                           this.ticks = 0;
                        }
                     } else {
                        this.stage = AutoSell.Stage.OPEN;
                        this.ticks = 0;
                     }
                  }
            }
         }
      }
   }

   private static int filled(class_1703 var0) {
      ArrayList var1 = new ArrayList();

      for (Object var3 : var0.field_7761) {
         class_1735 var4 = (class_1735)var3;
         if (!(var4.field_7871 instanceof class_1661)) {
            var1.add(var4);
         }
      }

      int var5 = 0;

      for (int var6 = 0; var6 < var1.size() - 1; var6++) {
         if (!((class_1735)var1.get(var6)).method_7677().method_7960()) {
            var5++;
         }
      }

      return var5;
   }

   private void fillStep(class_1703 var1) {
      ArrayList var2 = new ArrayList();

      for (Object var4 : var1.field_7761) {
         class_1735 var5 = (class_1735)var4;
         if (!(var5.field_7871 instanceof class_1661)) {
            var2.add(var5);
         }
      }

      if (var2.size() < 2) {
         this.done("That is not a sell container");
      } else {
         class_1735 var13 = (class_1735)var2.get(var2.size() - 1);
         int var14 = 0;
         int var15 = -1;

         for (int var6 = 0; var6 < var2.size() - 1; var6++) {
            if (!((class_1735)var2.get(var6)).method_7677().method_7960()) {
               var14++;
            } else if (var15 < 0) {
               var15 = var6;
            }
         }

         int var16 = Math.max(1, (var2.size() * this.fill.get() + 99) / 100);
         List var7 = this.sellableSlots(var1);
         if (var14 < var16 && var15 >= 0 && !var7.isEmpty()) {
            int var8 = (Integer)var7.get(0);
            int var9 = ((class_1735)var2.get(var15)).field_7874;
            int var10 = -1;

            for (Object var12 : var1.field_7761) {
               if (((class_1735)var12).field_7874 == var8) {
                  var10 = ((class_1735)var12).method_34266();
               }
            }

            int var17 = this.tries.merge(var10, 1, Integer::sum);
            if (var17 > 2) {
               this.refused.add(var10);
               return;
            }

            if (++this.moves > 600) {
               this.done("Stopped – the server does not take the items");
               return;
            }

            mc.field_1761.method_2906(var1.field_7763, var8, 0, class_1713.field_7790, mc.field_1724);
            mc.field_1761.method_2906(var1.field_7763, var9, 0, class_1713.field_7790, mc.field_1724);
            if (!var1.method_34255().method_7960()) {
               mc.field_1761.method_2906(var1.field_7763, var8, 0, class_1713.field_7790, mc.field_1724);
            }

            this.placed++;
            this.wait = this.delay.get();
            this.status = "Filling (" + (var14 + 1) + "/" + var16 + ")";
         } else if (this.placed <= 0 && (var14 <= 0 || !var7.isEmpty())) {
            this.done(null);
         } else {
            mc.field_1761.method_2906(var1.field_7763, var13.field_7874, 0, class_1713.field_7790, mc.field_1724);
            this.soldStacks = this.soldStacks + this.placed;
            this.rounds++;
            this.stage = AutoSell.Stage.AFTER_SELL;
            this.ticks = 0;
            this.status = "Sold a batch (" + this.soldStacks + " stacks)";
         }
      }
   }

   private void done(String var1) {
      if (mc.field_1755 instanceof class_465 && mc.field_1724 != null) {
         mc.field_1724.method_7346();
      }

      this.stage = AutoSell.Stage.IDLE;
      if (var1 != null) {
         this.status = var1;
         Notifications.warn("AutoSell", var1);
      } else {
         Money.refresh();
         this.status = "Sold " + this.soldStacks + " stacks";
         Notifications.push("AutoSell", this.status, Notifications.Type.SUCCESS);
      }
   }

   @Override
   public String getInfo() {
      return this.stage == AutoSell.Stage.IDLE ? null : "selling";
   }

   @Override
   public List<String> details() {
      ArrayList var1 = new ArrayList();
      var1.add("Status: " + this.status);
      if (Money.known() && !Double.isNaN(this.moneyBefore)) {
         var1.add("Earned since start: " + Money.format(Money.balance() - this.moneyBefore));
      }

      return var1;
   }

   public static enum Mode {
      LIST,
      EVERYTHING;
   }

   private static enum Stage {
      IDLE,
      OPEN,
      WAIT_SCREEN,
      FILL,
      AFTER_SELL;
   }
}
