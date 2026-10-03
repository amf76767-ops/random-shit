package dev.dihclient.modules.player;

import dev.dihclient.autobuild.AutoBuy;
import dev.dihclient.autobuild.Worker;
import dev.dihclient.module.Category;
import dev.dihclient.module.Module;
import dev.dihclient.module.ModuleManager;
import dev.dihclient.modules.world.AutoBuild;
import dev.dihclient.modules.world.SafeRoute;
import dev.dihclient.setting.BoolSetting;
import dev.dihclient.setting.IdListSetting;
import dev.dihclient.setting.IntSetting;
import dev.dihclient.util.HumanAim;
import dev.dihclient.util.ItemUtil;
import dev.dihclient.util.MoveUtil;
import dev.dihclient.util.Notifications;
import dev.dihclient.util.RegistryUtil;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.Predicate;
import net.minecraft.class_1268;
import net.minecraft.class_1661;
import net.minecraft.class_1703;
import net.minecraft.class_1713;
import net.minecraft.class_1723;
import net.minecraft.class_1735;
import net.minecraft.class_1792;
import net.minecraft.class_1799;
import net.minecraft.class_2338;
import net.minecraft.class_2350;
import net.minecraft.class_243;
import net.minecraft.class_3965;
import net.minecraft.class_465;

public class AutoCraft extends Module {
   public final IdListSetting targets = this.ids(
      "Craft",
      "Items to craft (whenever the ingredients are in your inventory). Recipes are built in: torches, sticks, planks, chests, furnaces, arrows, bread, paper, bucket, golden carrot / apple, ingot ↔ block ↔ nugget and more.",
      IdListSetting.Kind.ITEM,
      new String[]{"minecraft:torch"}
   );
   public final IntSetting until = this.integer("Craft Until", "Stops when your inventory holds this many of the item (0 = craft everything).", 256, 0, 2304);
   public final BoolSetting useTable = this.bool("Use Crafting Table", "Uses a crafting table nearby for recipes that don't fit your 2x2 grid.", true);
   public final IntSetting tableRange = this.integer("Table Range", "How far away a crafting table may be.", 10, 3, 24).visibleWhen(this.useTable::get);
   public final BoolSetting walk = this.bool("Walk To Table", "Walks to the table. Off = only tables within reach.", true).visibleWhen(this.useTable::get);
   public final BoolSetting human = this.bool("Human", "Turns to the table and clicks slowly, one item at a time.", false);
   public final IntSetting clicks = this.integer("Clicks/Tick", "Inventory clicks per tick.", 12, 1, 40).visibleWhen(() -> !this.human.get());
   public final IntSetting delay = this.integer("Delay", "Ticks between two clicks in human mode.", 3, 1, 10).visibleWhen(this.human::get);
   public final BoolSetting onlyIdle = this.bool("Only When Idle", "Waits while you move or a bot (Goto, AutoMine, AutoBuild …) is running.", true);
   public final BoolSetting buyMissing = this.bool(
      "Buy Missing (/ah)", "When an ingredient is missing, buys it from the auction house with /ah. Off by default – it spends your in-game money!", false
   );
   public final IntSetting maxBuys = this.integer("Max Buys", "Purchases per run (until you turn the module off and on again).", 3, 1, 20)
      .visibleWhen(this.buyMissing::get);
   private static final List<AutoCraft.Recipe> RECIPES = new ArrayList<>();
   private AutoCraft.Stage stage = AutoCraft.Stage.IDLE;
   private final Deque<AutoCraft.Op> ops = new ArrayDeque<>();
   private final Map<String, Integer> banned = new HashMap<>();
   private final Worker worker = new Worker();
   private final AutoBuy buyer = new AutoBuy();
   private AutoCraft.Recipe current;
   private boolean needTable;
   private class_2338 table;
   private int ticks;
   private int checkWait;
   private int now;
   private int wait;
   private int crafted;
   private int buys;
   private boolean aimed;
   private int openTicks;
   private String status = "Idle";
   private String buyingFor;

   private static AutoCraft.Ing id(String var0) {
      return new AutoCraft.Ing(var1 -> var1.equals(var0), var0);
   }

   private static AutoCraft.Ing any(String var0, String... var1) {
      ArrayList var2 = new ArrayList<>(List.of(var1));
      return new AutoCraft.Ing(var2::contains, var0);
   }

   private static AutoCraft.Ing planks() {
      return new AutoCraft.Ing(var0 -> var0.endsWith("_planks"), "minecraft:oak_planks");
   }

   private static AutoCraft.Ing stone() {
      return any("minecraft:cobblestone", "minecraft:cobblestone", "minecraft:cobbled_deepslate", "minecraft:blackstone");
   }

   private static void add(String var0, int var1, String[] var2, Object... var3) {
      LinkedHashMap var4 = new LinkedHashMap();

      for (byte var5 = 0; var5 < var3.length; var5 += 2) {
         var4.put((Character)var3[var5], (AutoCraft.Ing)var3[var5 + 1]);
      }

      RECIPES.add(new AutoCraft.Recipe("minecraft:" + var0, var1, var2, var4));
   }

   private static void compress(String var0, String var1) {
      add(var1, 1, new String[]{"III", "III", "III"}, 'I', id("minecraft:" + var0));
      add(var0, 9, new String[]{"B"}, 'B', id("minecraft:" + var1));
   }

   private static void compress4(String var0, String var1) {
      add(var1, 1, new String[]{"II", "II"}, 'I', id("minecraft:" + var0));
   }

   public AutoCraft() {
      super(
         "AutoCraft",
         Category.AUTOMATION,
         "Crafts torches, planks, blocks, chests and more by itself – 2x2 grid or a crafting table nearby, optional /ah shopping."
      );
   }

   @Override
   protected void onEnable() {
      this.reset();
      this.buys = 0;
      this.crafted = 0;
   }

   @Override
   protected void onDisable() {
      this.reset();
   }

   @Override
   public void onWorldChange() {
      this.reset();
   }

   private void reset() {
      this.ops.clear();
      this.worker.reset();
      this.buyer.reset();
      this.stage = AutoCraft.Stage.IDLE;
      this.current = null;
      this.table = null;
      this.status = "Idle";
      if (mc.field_1724 != null && mc.field_1755 instanceof class_465 && this.needTable) {
         mc.field_1724.method_7346();
      }

      this.needTable = false;
   }

   private class_1703 handler() {
      if (this.needTable) {
         return mc.field_1755 instanceof class_465 var1 ? var1.method_17577() : null;
      } else {
         return mc.field_1724.field_7498;
      }
   }

   private static List<class_1735> slots(class_1703 var0) {
      ArrayList var1 = new ArrayList();

      for (Object var3 : var0.field_7761) {
         var1.add((class_1735)var3);
      }

      return var1;
   }

   private static List<class_1735> craftSlots(class_1703 var0) {
      ArrayList var1 = new ArrayList();

      for (class_1735 var3 : slots(var0)) {
         if (!(var3.field_7871 instanceof class_1661)) {
            var1.add(var3);
         }
      }

      return var1;
   }

   private boolean mainSlot(class_1703 var1, int var2) {
      boolean var3 = var1 == mc.field_1724.field_7498;
      int var4 = var3 ? 9 : slots(var1).size() - 36;
      return var2 >= var4 && var2 < var4 + 36;
   }

   private int[] bestStack(class_1703 var1, AutoCraft.Ing var2) {
      int[] var3 = null;

      for (class_1735 var5 : slots(var1)) {
         if (this.mainSlot(var1, var5.field_7874)) {
            class_1799 var6 = var5.method_7677();
            if (!var6.method_7960() && var2.test.test(ItemUtil.id(var6)) && (var3 == null || var6.method_7947() > var3[1])) {
               var3 = new int[]{var5.field_7874, var6.method_7947()};
            }
         }
      }

      return var3;
   }

   private static int total(class_1703 var0, String var1) {
      int var2 = 0;

      for (class_1735 var4 : slots(var0)) {
         if (var4.field_7871 instanceof class_1661 && ItemUtil.id(var4.method_7677()).equals(var1)) {
            var2 += var4.method_7677().method_7947();
         }
      }

      return var2;
   }

   private int possible(AutoCraft.Recipe var1) {
      class_1723 var2 = mc.field_1724.field_7498;
      int var3 = Integer.MAX_VALUE;

      for (Entry var5 : var1.tokens.entrySet()) {
         int[] var6 = this.bestStack(var2, (AutoCraft.Ing)var5.getValue());
         if (var6 == null) {
            return 0;
         }

         var3 = Math.min(var3, var6[1] / Math.max(1, var1.cells((Character)var5.getKey()).size()));
      }

      return var3 == Integer.MAX_VALUE ? 0 : var3;
   }

   private AutoCraft.Recipe recipeFor(String var1, boolean var2) {
      AutoCraft.Recipe var3 = null;
      int var4 = 0;

      for (AutoCraft.Recipe var6 : RECIPES) {
         if (var6.out.equals(var1) && (var2 || var6.fits(2))) {
            int var7 = this.possible(var6);
            if (var7 > var4) {
               var4 = var7;
               var3 = var6;
            }
         }
      }

      return var3;
   }

   private boolean roomFor(String var1) {
      class_1792 var2 = ItemUtil.item(var1);
      if (var2 == null) {
         return false;
      } else if (mc.field_1724.method_31548().method_7376() >= 0) {
         return true;
      } else {
         for (int var3 = 0; var3 < 36; var3++) {
            class_1799 var4 = mc.field_1724.method_31548().method_5438(var3);
            if (!var4.method_7960() && ItemUtil.id(var4).equals(var1) && var4.method_7947() < var4.method_7909().method_7882()) {
               return true;
            }
         }

         return false;
      }
   }

   private boolean busyElsewhere() {
      return this.onlyIdle.get() && (MoveUtil.isMoving() || SafeRoute.botRunning() || ModuleManager.on(AutoBuild.class));
   }

   private void plan() {
      boolean var1 = false;
      Iterator var2 = this.targets.get().iterator();

      String var3;
      AutoCraft.Recipe var4;
      boolean var5;
      while (true) {
         if (!var2.hasNext()) {
            if (var1 && this.buyMissing.get() && this.buys < this.maxBuys.get()) {
               this.planBuy();
            }

            return;
         }

         var3 = (String)var2.next();
         if (this.banned.getOrDefault(var3, 0) <= this.now
            && (this.until.get() <= 0 || total(mc.field_1724.field_7498, var3) < this.until.get())
            && this.roomFor(var3)) {
            var4 = this.recipeFor(var3, false);
            var5 = false;
            if (var4 == null && this.useTable.get()) {
               var4 = this.recipeFor(var3, true);
               var5 = var4 != null;
            }

            if (var4 != null) {
               if (!var5) {
                  break;
               }

               class_2338 var6 = this.findTable();
               if (var6 != null) {
                  this.table = var6;
                  break;
               }

               this.status = "No crafting table nearby for " + prettyName(var3);
            } else {
               var1 = true;
            }
         }
      }

      this.current = var4;
      this.needTable = var5;
      this.ticks = 0;
      this.aimed = false;
      this.stage = var5 ? AutoCraft.Stage.WALK : AutoCraft.Stage.PLACE;
      if (!var5) {
         this.buildOps(mc.field_1724.field_7498);
      }

      this.status = "Crafting " + prettyName(var3);
   }

   private void planBuy() {
      for (String var2 : this.targets.get()) {
         if (this.until.get() <= 0 || total(mc.field_1724.field_7498, var2) < this.until.get()) {
            for (AutoCraft.Recipe var4 : RECIPES) {
               if (var4.out.equals(var2)) {
                  for (Entry var6 : var4.tokens.entrySet()) {
                     if (this.bestStack(mc.field_1724.field_7498, (AutoCraft.Ing)var6.getValue()) == null) {
                        class_1792 var7 = ItemUtil.item(((AutoCraft.Ing)var6.getValue()).buy);
                        if (var7 != null) {
                           this.buyingFor = var2;
                           this.buys++;
                           this.buyer.begin(var7);
                           if (this.buyer.isActive()) {
                              this.stage = AutoCraft.Stage.BUY;
                              this.status = "Buying " + prettyName(((AutoCraft.Ing)var6.getValue()).buy) + " (/ah)";
                              return;
                           }

                           this.buys--;
                        }
                     }
                  }
               }
            }
         }
      }
   }

   private static String prettyName(String var0) {
      return RegistryUtil.pretty(var0);
   }

   private class_2338 findTable() {
      class_2338 var1 = null;
      double var2 = Double.MAX_VALUE;
      int var4 = this.tableRange.get();
      class_2338 var5 = mc.field_1724.method_24515();

      for (class_2338 var7 : class_2338.method_10097(var5.method_10069(-var4, -3, -var4), var5.method_10069(var4, 3, var4))) {
         if (RegistryUtil.blockId(mc.field_1687.method_8320(var7)).equals("minecraft:crafting_table")) {
            double var8 = class_243.method_24953(var7).method_1025(mc.field_1724.method_73189());
            if (var8 < var2) {
               var2 = var8;
               var1 = var7.method_10062();
            }
         }
      }

      return var1;
   }

   private void buildOps(class_1703 var1) {
      this.ops.clear();
      AutoCraft.Recipe var2 = this.current;
      List var3 = craftSlots(var1);
      int var4 = var3.size() - 1 >= 9 ? 3 : 2;
      LinkedHashMap var5 = new LinkedHashMap();
      int var6 = Integer.MAX_VALUE;

      for (Entry var8 : var2.tokens.entrySet()) {
         int[] var9 = this.bestStack(var1, (AutoCraft.Ing)var8.getValue());
         if (var9 == null) {
            this.stage = AutoCraft.Stage.IDLE;
            return;
         }

         var5.put((Character)var8.getKey(), var9);
         var6 = Math.min(var6, var9[1] / Math.max(1, var2.cells((Character)var8.getKey()).size()));
      }

      int var15 = var6;
      if (this.until.get() > 0) {
         int var17 = this.until.get() - total(var1, var2.out);
         var15 = Math.min(var6, Math.max(1, (var17 + var2.count - 1) / var2.count));
      }

      var15 = Math.max(1, Math.min(var15, 64));

      for (Entry var19 : var5.entrySet()) {
         int[] var10 = (int[])var19.getValue();
         List var11 = var2.cells((Character)var19.getKey());
         this.ops.add(new AutoCraft.Op(var10[0], 0, class_1713.field_7790));
         if (var11.size() == 1 && var15 >= var10[1]) {
            int[] var20 = (int[])var11.get(0);
            this.ops.add(new AutoCraft.Op(1 + var20[0] * var4 + var20[1], 0, class_1713.field_7790));
         } else {
            for (int var12 = 0; var12 < var15; var12++) {
               for (int[] var14 : var11) {
                  this.ops.add(new AutoCraft.Op(1 + var14[0] * var4 + var14[1], 1, class_1713.field_7790));
               }
            }
         }

         this.ops.add(new AutoCraft.Op(var10[0], 0, class_1713.field_7790));
      }
   }

   @Override
   public void onTick() {
      if (inGame()) {
         this.now++;
         if (this.wait > 0) {
            this.wait--;
         } else {
            switch (this.stage) {
               case IDLE:
                  if (mc.field_1755 == null && ++this.checkWait >= 10 && !this.busyElsewhere() && !mc.field_1724.method_31549().field_7477) {
                     this.checkWait = 0;
                     this.status = "Idle";
                     this.plan();
                  }
                  break;
               case WALK:
                  this.tickWalk();
                  break;
               case OPEN:
                  this.tickOpen();
                  break;
               case PLACE:
                  this.tickPlace();
                  break;
               case WAIT_RESULT:
                  this.tickWaitResult();
                  break;
               case TAKE:
                  this.tickTake();
                  break;
               case WAIT_TAKE:
                  this.stage = AutoCraft.Stage.CLEAN;
                  this.wait = 1;
                  break;
               case CLEAN:
                  this.tickClean();
                  break;
               case BUY:
                  this.tickBuy();
            }
         }
      }
   }

   private void tickBuy() {
      if (this.buyer.isActive()) {
         this.status = this.buyer.tick();
      } else {
         this.stage = AutoCraft.Stage.IDLE;
         this.checkWait = 10;
      }
   }

   private void tickWalk() {
      this.ticks++;
      if (this.table == null || !RegistryUtil.blockId(mc.field_1687.method_8320(this.table)).equals("minecraft:crafting_table") || this.ticks > 600) {
         this.fail("Crafting table not reachable");
      } else if (mc.field_1755 != null) {
         this.fail("A screen is open");
      } else if (class_243.method_24953(this.table).method_1022(mc.field_1724.method_33571()) <= 4.0) {
         this.worker.release();
         this.stage = AutoCraft.Stage.OPEN;
         this.ticks = 0;
         this.openTicks = 0;
      } else if (this.walk.get()) {
         this.worker.walkTo(class_243.method_24953(this.table), 3.0);
         if (this.worker.isStuck()) {
            this.fail("Can't reach the crafting table");
         }
      } else {
         this.fail("Crafting table too far away");
      }
   }

   private void tickOpen() {
      this.ticks++;
      if (++this.openTicks > 120) {
         this.fail("The crafting table did not open");
      } else {
         if (this.ticks == 1 || this.human.get() && !this.aimed) {
            if (this.human.get()) {
               this.aimed = HumanAim.stepTo(class_243.method_24953(this.table), 30.0F, 3.0F);
               if (!this.aimed) {
                  this.ticks = 0;
                  return;
               }
            }

            class_243 var1 = class_243.method_24953(this.table);
            class_2350 var2 = class_2350.method_58251(mc.field_1724.method_33571().method_1020(var1));
            mc.field_1761
               .method_2896(
                  mc.field_1724,
                  class_1268.field_5808,
                  new class_3965(var1.method_1031(var2.method_10148() * 0.5, var2.method_10164() * 0.5, var2.method_10165() * 0.5), var2, this.table, false)
               );
            mc.field_1724.method_6104(class_1268.field_5808);
         }

         if (mc.field_1755 instanceof class_465 var3 && craftSlots(var3.method_17577()).size() == 10 && this.ticks >= 2) {
            this.stage = AutoCraft.Stage.PLACE;
            this.buildOps(var3.method_17577());
            this.wait = this.human.get() ? this.delay.get() : 0;
         } else if (this.ticks > 30) {
            this.fail("The crafting table did not open");
         }
      }
   }

   private int perTick() {
      return this.human.get() ? 1 : this.clicks.get();
   }

   private void click(class_1703 var1, AutoCraft.Op var2) {
      mc.field_1761.method_2906(var1.field_7763, var2.slot(), var2.button(), var2.type(), mc.field_1724);
   }

   private void tickPlace() {
      class_1703 var1 = this.handler();
      if (var1 == null) {
         this.fail("Crafting screen closed");
      } else if (this.ops.isEmpty()) {
         this.stage = this.current == null ? AutoCraft.Stage.IDLE : AutoCraft.Stage.WAIT_RESULT;
         this.ticks = 0;
      } else {
         for (int var2 = 0; var2 < this.perTick() && !this.ops.isEmpty(); var2++) {
            this.click(var1, this.ops.poll());
         }

         if (this.human.get()) {
            this.wait = this.delay.get();
         }
      }
   }

   private void tickWaitResult() {
      class_1703 var1 = this.handler();
      if (var1 == null) {
         this.fail("Crafting screen closed");
      } else {
         this.ticks++;
         List var2 = craftSlots(var1);
         class_1799 var3 = var2.isEmpty() ? null : ((class_1735)var2.get(0)).method_7677();
         if (var3 != null && !var3.method_7960() && ItemUtil.id(var3).equals(this.current.out)) {
            this.stage = AutoCraft.Stage.TAKE;
            this.wait = this.human.get() ? this.delay.get() : 0;
         } else if (this.ticks > 30) {
            this.banned.put(this.current.out, this.now + 600);
            this.stage = AutoCraft.Stage.CLEAN;
            this.status = "Recipe did not work – skipped for a while";
         }
      }
   }

   private void tickTake() {
      class_1703 var1 = this.handler();
      if (var1 == null) {
         this.fail("Crafting screen closed");
      } else {
         this.click(var1, new AutoCraft.Op(0, 0, class_1713.field_7794));
         this.crafted = this.crafted + Math.max(0, this.current.count);
         this.stage = AutoCraft.Stage.WAIT_TAKE;
         this.wait = 6;
      }
   }

   private void tickClean() {
      class_1703 var1 = this.handler();
      if (var1 != null) {
         List var2 = craftSlots(var1);

         for (int var3 = 1; var3 < var2.size(); var3++) {
            if (!((class_1735)var2.get(var3)).method_7677().method_7960()) {
               this.click(var1, new AutoCraft.Op(((class_1735)var2.get(var3)).field_7874, 0, class_1713.field_7794));
            }
         }

         class_1799 var8 = var1.method_34255();
         if (!var8.method_7960()) {
            int var4 = -1;

            for (class_1735 var6 : slots(var1)) {
               class_1799 var7 = var6.method_7677();
               if (this.mainSlot(var1, var6.field_7874)) {
                  if (var7.method_7960()) {
                     var4 = var6.field_7874;
                     break;
                  }

                  if (var4 < 0 && var7.method_7909() == var8.method_7909() && var7.method_7947() < var7.method_7909().method_7882()) {
                     var4 = var6.field_7874;
                  }
               }
            }

            if (var4 >= 0) {
               this.click(var1, new AutoCraft.Op(var4, 0, class_1713.field_7790));
            }
         }
      }

      if (this.needTable && mc.field_1755 instanceof class_465) {
         mc.field_1724.method_7346();
      }

      this.needTable = false;
      this.current = null;
      this.stage = AutoCraft.Stage.IDLE;
      this.checkWait = 5;
   }

   private void fail(String var1) {
      this.worker.release();
      this.status = var1;
      if (this.current != null) {
         this.banned.put(this.current.out, this.now + 400);
      }

      if (this.needTable && mc.field_1755 instanceof class_465) {
         mc.field_1724.method_7346();
      }

      this.ops.clear();
      this.needTable = false;
      this.current = null;
      this.stage = AutoCraft.Stage.IDLE;
      Notifications.warn("AutoCraft", var1);
   }

   @Override
   public String getInfo() {
      return this.stage == AutoCraft.Stage.IDLE ? (this.crafted > 0 ? String.valueOf(this.crafted) : null) : "Crafting";
   }

   @Override
   public List<String> details() {
      ArrayList var1 = new ArrayList();
      var1.add("Status: " + this.status);
      var1.add("Crafted: " + this.crafted + " · bought " + this.buys + "/" + this.maxBuys.get());
      return var1;
   }

   static {
      add("torch", 4, new String[]{"C", "S"}, 'C', any("minecraft:coal", "minecraft:coal", "minecraft:charcoal"), 'S', id("minecraft:stick"));
      add("stick", 4, new String[]{"P", "P"}, 'P', planks());
      add("crafting_table", 1, new String[]{"PP", "PP"}, 'P', planks());
      add("chest", 1, new String[]{"PPP", "P P", "PPP"}, 'P', planks());
      add("furnace", 1, new String[]{"CCC", "C C", "CCC"}, 'C', stone());
      add("arrow", 4, new String[]{"F", "S", "E"}, 'F', id("minecraft:flint"), 'S', id("minecraft:stick"), 'E', id("minecraft:feather"));
      add("bread", 1, new String[]{"WWW"}, 'W', id("minecraft:wheat"));
      add("paper", 3, new String[]{"SSS"}, 'S', id("minecraft:sugar_cane"));
      add("bucket", 1, new String[]{"I I", " I "}, 'I', id("minecraft:iron_ingot"));
      add("bone_meal", 3, new String[]{"B"}, 'B', id("minecraft:bone"));
      add("golden_carrot", 1, new String[]{"NNN", "NCN", "NNN"}, 'N', id("minecraft:gold_nugget"), 'C', id("minecraft:carrot"));
      add("golden_apple", 1, new String[]{"GGG", "GAG", "GGG"}, 'G', id("minecraft:gold_ingot"), 'A', id("minecraft:apple"));

      for (String var3 : new String[]{"oak", "spruce", "birch", "jungle", "acacia", "dark_oak", "mangrove", "cherry", "pale_oak"}) {
         add(
            var3 + "_planks",
            4,
            new String[]{"L"},
            'L',
            any(
               "minecraft:" + var3 + "_log",
               "minecraft:" + var3 + "_log",
               "minecraft:" + var3 + "_wood",
               "minecraft:stripped_" + var3 + "_log",
               "minecraft:stripped_" + var3 + "_wood"
            )
         );
      }

      for (String var7 : new String[]{"crimson", "warped"}) {
         add(
            var7 + "_planks",
            4,
            new String[]{"L"},
            'L',
            any(
               "minecraft:" + var7 + "_stem",
               "minecraft:" + var7 + "_stem",
               "minecraft:" + var7 + "_hyphae",
               "minecraft:stripped_" + var7 + "_stem",
               "minecraft:stripped_" + var7 + "_hyphae"
            )
         );
      }

      add("bamboo_planks", 2, new String[]{"L"}, 'L', any("minecraft:bamboo_block", "minecraft:bamboo_block", "minecraft:stripped_bamboo_block"));
      compress("iron_ingot", "iron_block");
      compress("gold_ingot", "gold_block");
      compress("copper_ingot", "copper_block");
      compress("diamond", "diamond_block");
      compress("emerald", "emerald_block");
      compress("lapis_lazuli", "lapis_block");
      compress("redstone", "redstone_block");
      compress("coal", "coal_block");
      compress("netherite_ingot", "netherite_block");
      compress("raw_iron", "raw_iron_block");
      compress("raw_gold", "raw_gold_block");
      compress("raw_copper", "raw_copper_block");
      compress("slime_ball", "slime_block");
      compress("wheat", "hay_block");
      compress("dried_kelp", "dried_kelp_block");
      add("bone_block", 1, new String[]{"MMM", "MMM", "MMM"}, 'M', id("minecraft:bone_meal"));
      add("bone_meal", 9, new String[]{"B"}, 'B', id("minecraft:bone_block"));
      compress4("glowstone_dust", "glowstone");
      compress4("quartz", "quartz_block");
      compress4("snowball", "snow_block");
      compress4("clay_ball", "clay");
      compress4("honeycomb", "honeycomb_block");
      add("iron_nugget", 9, new String[]{"I"}, 'I', id("minecraft:iron_ingot"));
      add("gold_nugget", 9, new String[]{"I"}, 'I', id("minecraft:gold_ingot"));
      add("iron_ingot", 1, new String[]{"NNN", "NNN", "NNN"}, 'N', id("minecraft:iron_nugget"));
      add("gold_ingot", 1, new String[]{"NNN", "NNN", "NNN"}, 'N', id("minecraft:gold_nugget"));
   }

   private static final class Ing {
      final Predicate<String> test;
      final String buy;

      Ing(Predicate<String> var1, String var2) {
         this.test = var1;
         this.buy = var2;
      }
   }

   private record Op(int slot, int button, class_1713 type) {
   }

   private static final class Recipe {
      final String out;
      final int count;
      final String[] rows;
      final Map<Character, AutoCraft.Ing> tokens;

      Recipe(String var1, int var2, String[] var3, Map<Character, AutoCraft.Ing> var4) {
         this.out = var1;
         this.count = var2;
         this.rows = var3;
         this.tokens = var4;
      }

      int width() {
         int var1 = 0;

         for (String var5 : this.rows) {
            var1 = Math.max(var1, var5.length());
         }

         return var1;
      }

      boolean fits(int var1) {
         return this.rows.length <= var1 && this.width() <= var1;
      }

      List<int[]> cells(char var1) {
         ArrayList var2 = new ArrayList();

         for (int var3 = 0; var3 < this.rows.length; var3++) {
            for (int var4 = 0; var4 < this.rows[var3].length(); var4++) {
               if (this.rows[var3].charAt(var4) == var1) {
                  var2.add(new int[]{var3, var4});
               }
            }
         }

         return var2;
      }
   }

   private static enum Stage {
      IDLE,
      WALK,
      OPEN,
      PLACE,
      WAIT_RESULT,
      TAKE,
      WAIT_TAKE,
      CLEAN,
      BUY;
   }
}
