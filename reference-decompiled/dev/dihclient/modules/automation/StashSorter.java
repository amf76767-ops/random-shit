package dev.dihclient.modules.automation;

import dev.dihclient.autobuild.Restock;
import dev.dihclient.autobuild.Worker;
import dev.dihclient.hud.HudManager;
import dev.dihclient.module.Category;
import dev.dihclient.module.Module;
import dev.dihclient.module.ModuleManager;
import dev.dihclient.render.Render3D;
import dev.dihclient.setting.BoolSetting;
import dev.dihclient.setting.EnumSetting;
import dev.dihclient.setting.IdListSetting;
import dev.dihclient.setting.IntSetting;
import dev.dihclient.setting.StringSetting;
import dev.dihclient.util.HumanAim;
import dev.dihclient.util.ItemUtil;
import dev.dihclient.util.Notifications;
import dev.dihclient.waypoint.WaypointManager;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import net.minecraft.class_1268;
import net.minecraft.class_1661;
import net.minecraft.class_1703;
import net.minecraft.class_1713;
import net.minecraft.class_1735;
import net.minecraft.class_1792;
import net.minecraft.class_1799;
import net.minecraft.class_2338;
import net.minecraft.class_2350;
import net.minecraft.class_238;
import net.minecraft.class_243;
import net.minecraft.class_2680;
import net.minecraft.class_3965;
import net.minecraft.class_465;

public class StashSorter extends Module {
   public final EnumSetting<StashSorter.Mode> mode = this.mode(
      "Mode",
      "Scan: only look inside and build the catalog. Sort: bring equal items together. Deposit: empty your inventory into the chests.",
      StashSorter.Mode.SORT
   );
   public final IntSetting radius = this.integer("Radius", "Containers within this many blocks are used.", 16, 4, 48);
   public final IntSetting speed = this.integer("Stacks/Tick", "Stacks moved per tick (1 looks most human).", 2, 1, 27);
   public final BoolSetting keepHotbar = this.bool("Keep Hotbar", "Deposit never takes items out of your hotbar.", true);
   public final BoolSetting keepTools = this.bool("Keep Tools & Armor", "Deposit keeps everything with durability.", true);
   public final IdListSetting keep = this.ids(
      "Keep Items",
      "Deposit never stores these.",
      IdListSetting.Kind.ITEM,
      new String[]{
         "minecraft:ender_pearl",
         "minecraft:totem_of_undying",
         "minecraft:golden_apple",
         "minecraft:enchanted_golden_apple",
         "minecraft:golden_carrot",
         "minecraft:cooked_beef",
         "minecraft:bread",
         "minecraft:firework_rocket",
         "minecraft:water_bucket",
         "minecraft:torch"
      }
   );
   public final BoolSetting fillAny = this.bool("Fill Any Chest", "Deposit: what no chest has yet goes into any container with space.", true);
   public final BoolSetting human = this.bool("Human Rotations", "Looks at each container before opening it.", true);
   public final StringSetting find = this.text("Find", "Item to look for with the Find action (name or id, e.g. \"diamond\").", "", 64);
   public final BoolSetting render = this.bool("Render", "Highlights the container being used and the Find results.", true);
   private static final Map<class_2338, Map<class_1792, Integer>> catalog = new HashMap<>();
   private static String catalogWorld = "";
   private final Worker worker = new Worker();
   private final List<class_2338> order = new ArrayList<>();
   private final Set<class_2338> doneThisRound = new HashSet<>();
   private final Map<class_1792, class_2338> home = new HashMap<>();
   private final Map<class_1792, Integer> carried = new HashMap<>();
   private final List<class_2338> found = new ArrayList<>();
   private long foundUntil;
   private StashSorter.Stage stage = StashSorter.Stage.IDLE;
   private class_2338 current;
   private int ticks;
   private int round;
   private int movedThisRound;
   private int movedTotal;
   private boolean aimed;
   private String status = "Idle";
   private boolean finished;

   public StashSorter() {
      super("StashSorter", Category.AUTOMATION, "Scans, sorts and fills your chests: brings equal items together, empties your inventory, finds any item.");
      this.action("Find", "Shows (and highlights) which containers hold the Find item.", this::runFind);
      this.action("Forget Catalog", "Clears what was seen in the containers.", () -> {
         catalog.clear();
         Notifications.info("StashSorter", "Catalog cleared");
      });
   }

   public static void run(StashSorter.Mode var0) {
      StashSorter var1 = ModuleManager.of(StashSorter.class);
      if (var1 != null) {
         if (var1.isEnabled()) {
            var1.setEnabled(false);
         }

         var1.mode.set(var0);
         var1.setEnabled(true);
      }
   }

   public boolean finished() {
      return this.finished;
   }

   private static void checkWorld() {
      String var0 = WaypointManager.worldKey() + "|" + WaypointManager.dimKey();
      if (!var0.equals(catalogWorld)) {
         catalog.clear();
         catalogWorld = var0;
      }
   }

   private static String name(class_1792 var0) {
      return var0.method_63680().getString();
   }

   private void runFind() {
      String var1 = this.find.get().trim().toLowerCase(Locale.ROOT);
      if (var1.isEmpty()) {
         Notifications.warn("StashSorter", "Type an item into Find first");
      } else {
         checkWorld();
         this.found.clear();
         LinkedHashMap var2 = new LinkedHashMap();

         for (Entry var4 : catalog.entrySet()) {
            for (Entry var6 : ((Map)var4.getValue()).entrySet()) {
               if (matches((class_1792)var6.getKey(), var1)) {
                  var2.merge((class_2338)var4.getKey(), var6.getValue() + "x " + name((class_1792)var6.getKey()), (var0, var1x) -> var0 + ", " + var1x);
               }
            }
         }

         for (Entry var10 : Restock.snapshot().entrySet()) {
            if (!var2.containsKey(var10.getKey())) {
               for (class_1792 var14 : (Set)var10.getValue()) {
                  if (matches(var14, var1)) {
                     var2.merge((class_2338)var10.getKey(), name(var14), (var0, var1x) -> var0 + ", " + var1x);
                  }
               }
            }
         }

         if (var2.isEmpty()) {
            Notifications.chat("StashSorter: no known container holds \"" + var1 + "\" (run Scan first).");
         } else {
            class_243 var9 = mc.field_1724 == null ? new class_243(0.0, 0.0, 0.0) : mc.field_1724.method_73189();
            ArrayList var11 = new ArrayList(var2.keySet());
            var11.sort((var1x, var2x) -> Double.compare(class_243.method_24953(var1x).method_1025(var9), class_243.method_24953(var2x).method_1025(var9)));
            Notifications.chat("StashSorter: \"" + var1 + "\" found in " + var11.size() + " container(s):");

            for (int var13 = 0; var13 < Math.min(8, var11.size()); var13++) {
               class_2338 var15 = (class_2338)var11.get(var13);
               int var7 = (int)Math.sqrt(class_243.method_24953(var15).method_1025(var9));
               Notifications.chat(
                  "  " + var15.method_10263() + " " + var15.method_10264() + " " + var15.method_10260() + " (" + var7 + "m): " + (String)var2.get(var15)
               );
            }

            this.found.addAll(var11);
            this.foundUntil = System.currentTimeMillis() + 60000L;
         }
      }
   }

   private static boolean matches(class_1792 var0, String var1) {
      return ItemUtil.id(var0).contains(var1) || name(var0).toLowerCase(Locale.ROOT).contains(var1);
   }

   @Override
   protected void onEnable() {
      if (!inGame()) {
         this.setEnabledSilently(false);
      } else {
         checkWorld();
         this.finished = false;
         this.order.clear();
         this.order.addAll(Restock.nearbyContainers(this.radius.get().intValue()));
         if (this.order.isEmpty()) {
            Notifications.warn("StashSorter", "No chests, barrels or shulker boxes within " + this.radius.get() + " blocks");
            this.finished = true;
            this.setEnabledSilently(false);
         } else {
            this.home.clear();
            this.carried.clear();
            this.doneThisRound.clear();
            this.round = 0;
            this.movedThisRound = 0;
            this.movedTotal = 0;
            this.stage = StashSorter.Stage.IDLE;
            this.current = null;
            this.status = "Starting";
         }
      }
   }

   @Override
   protected void onDisable() {
      this.worker.reset();
      this.worker.release();
      if (this.stage == StashSorter.Stage.WORK && mc.field_1755 instanceof class_465 && mc.field_1724 != null) {
         mc.field_1724.method_7346();
      }

      this.stage = StashSorter.Stage.IDLE;
      this.current = null;
   }

   @Override
   public void onWorldChange() {
      if (this.isEnabled()) {
         this.setEnabled(false);
      }
   }

   private boolean sortNeedsScan() {
      for (class_2338 var2 : this.order) {
         if (!catalog.containsKey(var2)) {
            return true;
         }
      }

      return false;
   }

   private class_2338 nextContainer() {
      class_243 var1 = mc.field_1724.method_73189();
      class_2338 var2 = null;
      double var3 = Double.MAX_VALUE;

      for (class_2338 var6 : this.order) {
         if (!this.doneThisRound.contains(var6) && Restock.isContainer(var6)) {
            double var7 = class_243.method_24953(var6).method_1025(var1);
            if (var7 < var3) {
               var2 = var6;
               var3 = var7;
            }
         }
      }

      return var2;
   }

   private void planHomes() {
      this.home.clear();
      HashMap var1 = new HashMap();

      for (class_2338 var3 : this.order) {
         Map var4 = catalog.get(var3);
         if (var4 != null) {
            for (Entry var6 : var4.entrySet()) {
               if ((Integer)var6.getValue() > var1.getOrDefault(var6.getKey(), 0)) {
                  var1.put((class_1792)var6.getKey(), (Integer)var6.getValue());
                  this.home.put((class_1792)var6.getKey(), var3);
               }
            }
         }
      }
   }

   private int spread() {
      HashMap var1 = new HashMap();

      for (class_2338 var3 : this.order) {
         Map var4 = catalog.get(var3);
         if (var4 != null) {
            for (class_1792 var6 : var4.keySet()) {
               var1.merge(var6, 1, Integer::sum);
            }
         }
      }

      int var7 = 0;

      for (int var9 : var1.values()) {
         if (var9 > 1) {
            var7++;
         }
      }

      return var7;
   }

   private void endRound() {
      this.round++;
      this.doneThisRound.clear();
      StashSorter.Mode var1 = this.mode.get();
      boolean var2;
      if (var1 == StashSorter.Mode.SCAN) {
         var2 = true;
      } else if (var1 == StashSorter.Mode.SORT) {
         if (this.round == 1) {
            this.planHomes();
         }

         boolean var3 = this.carriedTotal() > 0;
         var2 = this.round > 1 && this.movedThisRound == 0 && !var3 || this.round > 8;
      } else {
         var2 = this.round >= (this.fillAny.get() ? 2 : 1) || this.depositable(null) == 0;
      }

      this.movedThisRound = 0;
      if (var2) {
         this.finished = true;

         String var4 = switch (var1) {
            case SCAN -> "Scanned " + this.order.size() + " containers (" + catalog.size() + " in the catalog)";
            case SORT -> "Sorted: "
               + this.movedTotal
               + " stacks moved"
               + (this.spread() > 0 ? " (" + this.spread() + " item types still in several chests – full?)" : "");
            case DEPOSIT -> "Deposited "
               + this.movedTotal
               + " stacks"
               + (this.depositable(null) > 0 ? " (" + this.depositable(null) + " left – chests full?)" : "");
         };
         Notifications.push("StashSorter", var4, Notifications.Type.SUCCESS);
         this.setEnabled(false);
      }
   }

   private int carriedTotal() {
      int var1 = 0;

      for (int var3 : this.carried.values()) {
         var1 += Math.max(0, var3);
      }

      return var1;
   }

   @Override
   public void onTick() {
      if (inGame()) {
         this.ticks++;
         switch (this.stage) {
            case IDLE:
               this.current = this.nextContainer();
               if (this.current == null) {
                  this.endRound();
               } else {
                  this.stage = StashSorter.Stage.WALK;
                  this.ticks = 0;
               }
               break;
            case WALK:
               if (!Restock.isContainer(this.current)) {
                  this.skip("container gone");
               } else {
                  class_243 var1 = class_243.method_24953(this.current);
                  if (var1.method_1022(mc.field_1724.method_33571()) <= 4.2) {
                     this.worker.release();
                     this.stage = StashSorter.Stage.OPEN;
                     this.ticks = 0;
                     this.aimed = false;
                  } else {
                     this.worker.human = this.human.get();
                     this.worker.walkTo(var1, 2.2);
                     this.status = "Walking to container (" + (this.doneThisRound.size() + 1) + "/" + this.order.size() + ")";
                     if (this.worker.isStuck() || this.ticks > 400) {
                        this.worker.release();
                        this.skip("can't reach it");
                     }
                  }
               }
               break;
            case OPEN:
               this.tickOpen();
               break;
            case WORK:
               this.tickWork();
         }
      }
   }

   private void skip(String var1) {
      this.status = "Skipped a container: " + var1;
      this.doneThisRound.add(this.current);
      this.stage = StashSorter.Stage.IDLE;
      this.current = null;
   }

   private void tickOpen() {
      class_243 var1 = class_243.method_24953(this.current).method_1031(0.0, 0.49, 0.0);
      if (this.human.get() && !this.aimed) {
         if (this.ticks < 40) {
            this.aimed = HumanAim.stepTo(var1, 30.0F, 2.0F);
            this.status = "Looking at container";
            if (!this.aimed) {
               return;
            }
         }

         this.aimed = true;
         this.ticks = 0;
      } else {
         if (this.ticks == 1 || !this.human.get() && this.ticks == 0 || this.ticks == 20) {
            mc.field_1761.method_2896(mc.field_1724, class_1268.field_5808, new class_3965(var1, class_2350.field_11036, this.current, false));
            mc.field_1724.method_6104(class_1268.field_5808);
         }

         if (mc.field_1755 instanceof class_465 && this.ticks > 3) {
            this.stage = StashSorter.Stage.WORK;
            this.ticks = 0;
         } else if (this.ticks > 50) {
            this.skip("did not open");
         } else {
            this.status = "Opening container";
         }
      }
   }

   private boolean keeps(class_1799 var1, boolean var2) {
      if (var1.method_7960()) {
         return true;
      } else if (var2 && this.keepHotbar.get()) {
         return true;
      } else {
         return this.keepTools.get() && var1.method_7963() ? true : this.keep.get().contains(ItemUtil.id(var1));
      }
   }

   private int depositable(Map<class_1792, Integer> var1) {
      if (mc.field_1724 == null) {
         return 0;
      } else {
         class_1661 var2 = mc.field_1724.method_31548();
         int var3 = 0;

         for (int var4 = 0; var4 < 36; var4++) {
            class_1799 var5 = var2.method_5438(var4);
            if (!this.keeps(var5, var4 < 9) && (var1 == null || var1.containsKey(var5.method_7909()))) {
               var3++;
            }
         }

         return var3;
      }
   }

   private void tickWork() {
      if (!(mc.field_1755 instanceof class_465 var1)) {
         this.skip("closed");
      } else {
         class_1703 var18 = var1.method_17577();
         ArrayList var3 = new ArrayList();
         ArrayList var4 = new ArrayList();

         for (Object var6 : var18.field_7761) {
            class_1735 var7 = (class_1735)var6;
            if (var7.field_7871 instanceof class_1661) {
               var4.add(var7);
            } else {
               var3.add(var7);
            }
         }

         HashMap var19 = new HashMap();
         HashSet var20 = new HashSet();
         int var21 = 0;

         for (class_1735 var9 : var3) {
            class_1799 var10 = var9.method_7677();
            if (var10.method_7960()) {
               var21++;
            } else {
               var19.merge(var10.method_7909(), var10.method_7947(), Integer::sum);
               var20.add(var10.method_7909());
            }
         }

         catalog.put(this.current.method_10062(), var19);
         Restock.remember(this.current, var20);
         if (this.ticks >= 2) {
            int var22 = this.human.get() ? (this.ticks % 3 == 0 ? 1 : 0) : this.speed.get();
            int var23 = 0;
            boolean var24 = false;
            StashSorter.Mode var11 = this.mode.get();
            if (var11 == StashSorter.Mode.SORT && this.round >= 1) {
               for (int var25 = 0; var25 < var4.size() && var21 > 0; var25++) {
                  class_1735 var27 = (class_1735)var4.get(var25);
                  class_1799 var29 = var27.method_7677();
                  if (!var29.method_7960() && this.carried.getOrDefault(var29.method_7909(), 0) > 0 && this.current.equals(this.home.get(var29.method_7909()))) {
                     if (var23 >= var22) {
                        var24 = true;
                        break;
                     }

                     this.carried.merge(var29.method_7909(), -var29.method_7947(), Integer::sum);
                     this.move(var18, var27);
                     var23++;
                     var21--;
                  }
               }

               for (int var26 = 0; var26 < var3.size() && mc.field_1724.method_31548().method_7376() >= 0; var26++) {
                  class_1735 var28 = (class_1735)var3.get(var26);
                  class_1799 var30 = var28.method_7677();
                  if (!var30.method_7960()) {
                     class_2338 var31 = this.home.get(var30.method_7909());
                     if (var31 != null && !var31.equals(this.current) && Restock.isContainer(var31) && !this.sameChest(var31)) {
                        if (var23 >= var22) {
                           var24 = true;
                           break;
                        }

                        this.carried.merge(var30.method_7909(), var30.method_7947(), Integer::sum);
                        this.move(var18, var28);
                        var23++;
                     }
                  }
               }
            } else if (var11 == StashSorter.Mode.DEPOSIT) {
               boolean var12 = this.round >= 1 && this.fillAny.get();
               int var13 = var4.size();

               for (int var14 = 0; var14 < var13 && var21 > 0; var14++) {
                  class_1735 var15 = (class_1735)var4.get(var14);
                  class_1799 var16 = var15.method_7677();
                  boolean var17 = var14 >= var13 - 9;
                  if (!this.keeps(var16, var17) && (var12 || var19.containsKey(var16.method_7909()))) {
                     if (var23 >= var22) {
                        var24 = true;
                        break;
                     }

                     this.move(var18, var15);
                     var23++;
                     var21--;
                  }
               }
            }
            this.status = switch (var11) {
               case SCAN -> "Scanning (" + (this.doneThisRound.size() + 1) + "/" + this.order.size() + ")";
               case SORT -> this.round == 0
                  ? "Sort: scanning first (" + (this.doneThisRound.size() + 1) + "/" + this.order.size() + ")"
                  : "Sorting · round " + this.round + " · " + this.movedTotal + " stacks moved";
               case DEPOSIT -> "Depositing · " + this.movedTotal + " stacks";
            };
            if (var23 == 0 && !var24 || this.ticks > 400) {
               this.closeAndNext(var3.size());
            }
         }
      }
   }

   private boolean sameChest(class_2338 var1) {
      return Math.abs(var1.method_10263() - this.current.method_10263()) + Math.abs(var1.method_10260() - this.current.method_10260()) == 1
         && var1.method_10264() == this.current.method_10264();
   }

   private void move(class_1703 var1, class_1735 var2) {
      mc.field_1761.method_2906(var1.field_7763, var2.field_7874, 0, class_1713.field_7794, mc.field_1724);
      this.movedThisRound++;
      this.movedTotal++;
   }

   private static class_2338 otherHalf(class_2338 var0) {
      class_2680 var1 = mc.field_1687.method_8320(var0);
      String var2 = ItemUtil.prop(var1, "type");
      String var3 = ItemUtil.prop(var1, "facing");
      if (var3 != null && ("left".equals(var2) || "right".equals(var2))) {
         class_2350 var4 = switch (var3) {
            case "north" -> class_2350.field_11043;
            case "south" -> class_2350.field_11035;
            case "west" -> class_2350.field_11039;
            case "east" -> class_2350.field_11034;
            default -> null;
         };
         if (var4 == null) {
            return null;
         } else {
            class_2350 var5 = "left".equals(var2) ? var4.method_10170() : var4.method_10170().method_10170().method_10170();
            return var0.method_10093(var5);
         }
      } else {
         return null;
      }
   }

   private void closeAndNext(int var1) {
      mc.field_1724.method_7346();
      this.doneThisRound.add(this.current);
      if (var1 >= 54) {
         class_2338 var2 = otherHalf(this.current);
         if (var2 != null && this.order.remove(var2)) {
            catalog.remove(var2);
         }
      }

      this.stage = StashSorter.Stage.IDLE;
      this.current = null;
   }

   @Override
   public void onRender3D(Render3D var1) {
      if (this.render.get()) {
         if (this.current != null && this.isEnabled()) {
            var1.box(new class_238(this.current), 809566016, -12517568, true);
         }

         if (!this.found.isEmpty() && System.currentTimeMillis() < this.foundUntil) {
            for (class_2338 var3 : this.found) {
               var1.box(new class_238(var3), 822071360, HudManager.accent(), true);
            }
         }
      }
   }

   @Override
   public String getInfo() {
      return this.isEnabled() ? this.mode.get().name().charAt(0) + this.mode.get().name().substring(1).toLowerCase(Locale.ROOT) : null;
   }

   @Override
   public List<String> details() {
      ArrayList var1 = new ArrayList();
      var1.add("Status: " + this.status);
      var1.add("Catalog: " + catalog.size() + " containers");
      return var1;
   }

   public static enum Mode {
      SCAN,
      SORT,
      DEPOSIT;
   }

   private static enum Stage {
      IDLE,
      WALK,
      OPEN,
      WORK;
   }
}
