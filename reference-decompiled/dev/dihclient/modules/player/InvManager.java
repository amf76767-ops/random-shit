package dev.dihclient.modules.player;

import dev.dihclient.module.Category;
import dev.dihclient.module.Module;
import dev.dihclient.module.ModuleManager;
import dev.dihclient.modules.combat.AutoInvTotem;
import dev.dihclient.modules.world.AutoBuild;
import dev.dihclient.modules.world.AutoFarm;
import dev.dihclient.modules.world.AutoMine;
import dev.dihclient.modules.world.MapArt;
import dev.dihclient.modules.world.Scaffold;
import dev.dihclient.modules.world.Terraform;
import dev.dihclient.modules.world.Tunnel;
import dev.dihclient.setting.BoolSetting;
import dev.dihclient.setting.EnumSetting;
import dev.dihclient.setting.IdListSetting;
import dev.dihclient.setting.IntSetting;
import dev.dihclient.util.InvUtil;
import dev.dihclient.util.ItemUtil;
import dev.dihclient.util.Notifications;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import net.minecraft.class_1661;
import net.minecraft.class_1713;
import net.minecraft.class_1747;
import net.minecraft.class_1792;
import net.minecraft.class_1799;
import net.minecraft.class_243;
import net.minecraft.class_490;
import net.minecraft.class_9334;

public class InvManager extends Module {
   private static final Set<String> BUILD_BLOCKS = Set.of(
      "minecraft:cobblestone",
      "minecraft:cobbled_deepslate",
      "minecraft:netherrack",
      "minecraft:stone",
      "minecraft:dirt",
      "minecraft:deepslate",
      "minecraft:andesite",
      "minecraft:diorite",
      "minecraft:granite",
      "minecraft:tuff",
      "minecraft:blackstone",
      "minecraft:basalt",
      "minecraft:end_stone",
      "minecraft:sandstone"
   );
   private static final Set<String> BAD_FOOD = Set.of(
      "minecraft:rotten_flesh",
      "minecraft:spider_eye",
      "minecraft:poisonous_potato",
      "minecraft:pufferfish",
      "minecraft:chicken",
      "minecraft:suspicious_stew",
      "minecraft:golden_apple",
      "minecraft:enchanted_golden_apple",
      "minecraft:chorus_fruit"
   );
   public final BoolSetting dropJunk = this.bool("Drop Junk", "Throws out every item on the Junk list.", true);
   public final IdListSetting junk = this.ids(
         "Junk", "Items that get thrown out.", IdListSetting.Kind.ITEM, new String[]{"minecraft:rotten_flesh", "minecraft:poisonous_potato"}
      )
      .visibleWhen(this.dropJunk::get);
   public final BoolSetting hotbar = this.bool(
      "Hotbar Layout", "Keeps the item types below in their hotbar slots – and swaps in better tools when you pick them up.", true
   );
   public final EnumSetting<InvManager.Kind> slot1 = this.slot(1, InvManager.Kind.SWORD);
   public final EnumSetting<InvManager.Kind> slot2 = this.slot(2, InvManager.Kind.PICKAXE);
   public final EnumSetting<InvManager.Kind> slot3 = this.slot(3, InvManager.Kind.AXE);
   public final EnumSetting<InvManager.Kind> slot4 = this.slot(4, InvManager.Kind.BLOCKS);
   public final EnumSetting<InvManager.Kind> slot5 = this.slot(5, InvManager.Kind.NONE);
   public final EnumSetting<InvManager.Kind> slot6 = this.slot(6, InvManager.Kind.NONE);
   public final EnumSetting<InvManager.Kind> slot7 = this.slot(7, InvManager.Kind.NONE);
   public final EnumSetting<InvManager.Kind> slot8 = this.slot(8, InvManager.Kind.GAPPLE);
   public final EnumSetting<InvManager.Kind> slot9 = this.slot(9, InvManager.Kind.FOOD);
   public final BoolSetting autoSort = this.bool("Auto Sort", "Sorts the main inventory (not the hotbar) every time you open it.", false);
   public final IntSetting delay = this.integer("Delay", "Ticks between two moves (0 = instant).", 2, 0, 20);
   public final BoolSetting onlyInInventory = this.bool("Only In Inventory", "Only moves items while your inventory screen is open.", false);
   public final BoolSetting pauseMoving = this.bool("Pause While Moving", "Waits until you stand still.", false);
   public final BoolSetting pauseAutomation = this.bool(
      "Pause For Automation", "No hotbar swaps while AutoBuild, AutoMine, AutoFarm, Tunnel, Scaffold, Terraform or MapArt are on.", true
   );
   private final List<EnumSetting<InvManager.Kind>> slots = List.of(
      this.slot1, this.slot2, this.slot3, this.slot4, this.slot5, this.slot6, this.slot7, this.slot8, this.slot9
   );
   private int wait;
   private boolean sorting;
   private boolean sortOnly;
   private boolean wasInInventory;
   private int moves;
   private static final IdentityHashMap<class_1792, String> IDS = new IdentityHashMap<>();

   public InvManager() {
      super("InvManager", Category.PLAYER, "Drops junk, keeps a fixed hotbar layout with your best tools and sorts your inventory.");
      this.action("Sort Now", "Sorts the main inventory once.", this::requestSort);
   }

   private EnumSetting<InvManager.Kind> slot(int var1, InvManager.Kind var2) {
      return this.mode("Slot " + var1, "What belongs in hotbar slot " + var1 + ".", var2).visibleWhen(() -> this.hotbar.get());
   }

   private void requestSort() {
      if (inGame()) {
         this.sorting = true;
         if (!this.isEnabled()) {
            this.sortOnly = true;
            this.setEnabledSilently(true);
         }
      }
   }

   @Override
   protected void onEnable() {
      this.wait = 0;
      this.moves = 0;
      this.wasInInventory = false;
   }

   @Override
   protected void onDisable() {
      this.sorting = false;
      this.sortOnly = false;
   }

   private static int tier(String var0) {
      String var1 = var0.substring(var0.indexOf(58) + 1);
      if (var1.startsWith("netherite_")) {
         return 70;
      } else if (var1.startsWith("diamond_")) {
         return 60;
      } else if (var1.startsWith("iron_")) {
         return 50;
      } else if (var1.startsWith("copper_")) {
         return 40;
      } else if (var1.startsWith("stone_")) {
         return 30;
      } else if (var1.startsWith("golden_")) {
         return 20;
      } else {
         return var1.startsWith("wooden_") ? 10 : 0;
      }
   }

   private static int foodRank(String var0) {
      return switch (var0) {
         case "minecraft:golden_carrot" -> 6;
         case "minecraft:cooked_beef", "minecraft:cooked_porkchop" -> 5;
         case "minecraft:cooked_mutton", "minecraft:cooked_salmon", "minecraft:cooked_chicken" -> 4;
         case "minecraft:bread", "minecraft:baked_potato", "minecraft:cooked_cod", "minecraft:cooked_rabbit", "minecraft:pumpkin_pie" -> 3;
         default -> 1;
      };
   }

   private static String id(class_1799 var0) {
      return IDS.computeIfAbsent(var0.method_7909(), ItemUtil::id);
   }

   static double score(InvManager.Kind var0, class_1799 var1) {
      if (var1.method_7960()) {
         return -1.0;
      } else if (var0 == InvManager.Kind.NONE) {
         return -1.0;
      } else {
         String var2 = id(var1);
         double var3 = Math.min(1.0, ItemUtil.durabilityLeft(var1) / 2000.0) * 0.5;

         return switch (var0) {
            case NONE -> -1.0;
            case SWORD -> var2.endsWith("_sword") ? tier(var2) + var3 : -1.0;
            case AXE -> var2.endsWith("_axe") ? tier(var2) + var3 : -1.0;
            case PICKAXE -> var2.endsWith("_pickaxe") ? tier(var2) + var3 : -1.0;
            case SHOVEL -> var2.endsWith("_shovel") ? tier(var2) + var3 : -1.0;
            case HOE -> var2.endsWith("_hoe") ? tier(var2) + var3 : -1.0;
            case MACE -> var2.equals("minecraft:mace") ? 1.0 + var3 : -1.0;
            case BOW -> var2.equals("minecraft:bow") ? 1.0 + var3 : -1.0;
            case CROSSBOW -> var2.equals("minecraft:crossbow") ? 1.0 + var3 : -1.0;
            case TRIDENT -> var2.equals("minecraft:trident") ? 1.0 + var3 : -1.0;
            case SHIELD -> var2.equals("minecraft:shield") ? 1.0 + var3 : -1.0;
            case BLOCKS -> !BUILD_BLOCKS.contains(var2) && !var2.endsWith("_planks") ? -1.0 : var1.method_7947() / 100.0;
            case FOOD -> var1.method_58694(class_9334.field_50075) != null && !BAD_FOOD.contains(var2) ? foodRank(var2) + var1.method_7947() / 100.0 : -1.0;
            case GAPPLE -> var2.equals("minecraft:enchanted_golden_apple") ? 2.0 : (var2.equals("minecraft:golden_apple") ? 1.0 : -1.0);
            case TOTEM -> var2.equals("minecraft:totem_of_undying") ? 1.0 : -1.0;
            case PEARL -> var2.equals("minecraft:ender_pearl") ? var1.method_7947() / 100.0 : -1.0;
            case WATER_BUCKET -> var2.equals("minecraft:water_bucket") ? 1.0 : -1.0;
            case LAVA_BUCKET -> var2.equals("minecraft:lava_bucket") ? 1.0 : -1.0;
            case CRYSTAL -> var2.equals("minecraft:end_crystal") ? var1.method_7947() / 100.0 : -1.0;
            case OBSIDIAN -> var2.equals("minecraft:obsidian") ? var1.method_7947() / 100.0 : -1.0;
            case FIREWORK -> var2.equals("minecraft:firework_rocket") ? var1.method_7947() / 100.0 : -1.0;
            case TORCH -> var2.equals("minecraft:torch") ? var1.method_7947() / 100.0 : -1.0;
         };
      }
   }

   private static boolean upgrades(InvManager.Kind var0) {
      return switch (var0) {
         case SWORD, AXE, PICKAXE, SHOVEL, HOE, GAPPLE -> true;
         default -> false;
      };
   }

   @Override
   public void onTick() {
      if (inGame() && !mc.field_1724.method_68878()) {
         boolean var1 = mc.field_1755 instanceof class_490;
         if (var1 && !this.wasInInventory && this.autoSort.get()) {
            this.sorting = true;
         }

         this.wasInInventory = var1;
         if (mc.field_1755 == null || var1) {
            if (!this.onlyInInventory.get() || var1 || this.sortOnly) {
               if (this.pauseMoving.get()) {
                  class_243 var2 = mc.field_1724.method_18798();
                  if (var2.field_1352 * var2.field_1352 + var2.field_1350 * var2.field_1350 > 1.0E-4) {
                     return;
                  }
               }

               if (this.wait > 0) {
                  this.wait--;
               } else if (mc.field_1724.field_7498.method_34255().method_7960()) {
                  boolean var3 = false;
                  if (!this.sortOnly) {
                     if (this.dropJunk.get()) {
                        var3 = this.tickJunk();
                     }

                     if (!var3 && this.hotbar.get() && (!this.pauseAutomation.get() || !this.automationRunning())) {
                        var3 = this.tickHotbar();
                     }
                  }

                  if (!var3 && this.sorting) {
                     var3 = this.tickSort();
                     if (!var3) {
                        this.sorting = false;
                        if (this.moves > 0) {
                           Notifications.info("InvManager", "Inventory sorted");
                        }

                        this.moves = 0;
                        if (this.sortOnly) {
                           this.sortOnly = false;
                           this.setEnabledSilently(false);
                           return;
                        }
                     }
                  }

                  if (var3) {
                     this.moves++;
                     this.wait = this.delay.get();
                  }
               }
            }
         }
      }
   }

   private boolean automationRunning() {
      return ModuleManager.on(AutoBuild.class)
         || ModuleManager.on(AutoMine.class)
         || ModuleManager.on(AutoFarm.class)
         || ModuleManager.on(Tunnel.class)
         || ModuleManager.on(Scaffold.class)
         || ModuleManager.on(Terraform.class)
         || ModuleManager.on(MapArt.class);
   }

   private int syncId() {
      return mc.field_1724.field_7498.field_7763;
   }

   private void pickup(int var1) {
      mc.field_1761.method_2906(this.syncId(), var1, 0, class_1713.field_7790, mc.field_1724);
   }

   private boolean tickJunk() {
      class_1661 var1 = mc.field_1724.method_31548();

      for (int var2 = 0; var2 < 36; var2++) {
         class_1799 var3 = var1.method_5438(var2);
         if (!var3.method_7960() && this.junk.contains(id(var3))) {
            this.pickup(InvUtil.toScreenSlot(var2));
            this.pickup(-999);
            return true;
         }
      }

      return false;
   }

   private InvManager.Kind kindOf(int var1) {
      if (var1 >= 0 && var1 < 9) {
         AutoInvTotem var2 = ModuleManager.of(AutoInvTotem.class);
         return var2 != null && var2.isEnabled() && var2.hotbar.get() && var2.hotbarSlot.get() - 1 == var1 ? InvManager.Kind.TOTEM : this.slots.get(var1).get();
      } else {
         return InvManager.Kind.NONE;
      }
   }

   private boolean tickHotbar() {
      class_1661 var1 = mc.field_1724.method_31548();

      for (int var2 = 0; var2 < 9; var2++) {
         InvManager.Kind var3 = this.kindOf(var2);
         if (var3 != InvManager.Kind.NONE) {
            double var4 = score(var3, var1.method_5438(var2));
            if (!(var4 >= 0.0) || upgrades(var3)) {
               int var6 = -1;
               double var7 = var4;

               for (int var9 = 0; var9 < 36; var9++) {
                  if (var9 != var2) {
                     class_1799 var10 = var1.method_5438(var9);
                     double var11 = score(var3, var10);
                     if (var11 > var7 + 1.0E-6 && (var9 >= 9 || !(score(this.kindOf(var9), var10) >= 0.0))) {
                        var7 = var11;
                        var6 = var9;
                     }
                  }
               }

               if (var6 >= 0 && (var4 < 0.0 || var7 >= var4 + 1.0)) {
                  mc.field_1761.method_2906(this.syncId(), InvUtil.toScreenSlot(var6), var2, class_1713.field_7791, mc.field_1724);
                  return true;
               }
            }
         }
      }

      return false;
   }

   private static int group(class_1799 var0) {
      if (var0.method_7960()) {
         return 99;
      } else {
         String var1 = ItemUtil.id(var0);
         if (var1.endsWith("_sword")
            || var1.endsWith("_axe")
            || var1.equals("minecraft:mace")
            || var1.equals("minecraft:bow")
            || var1.equals("minecraft:crossbow")
            || var1.equals("minecraft:trident")) {
            return 0;
         } else if (var1.endsWith("_pickaxe")
            || var1.endsWith("_shovel")
            || var1.endsWith("_hoe")
            || var1.equals("minecraft:shears")
            || var1.equals("minecraft:flint_and_steel")
            || var1.equals("minecraft:fishing_rod")) {
            return 1;
         } else if (var1.endsWith("_helmet")
            || var1.endsWith("_chestplate")
            || var1.endsWith("_leggings")
            || var1.endsWith("_boots")
            || var1.equals("minecraft:elytra")
            || var1.equals("minecraft:shield")) {
            return 2;
         } else if (var1.equals("minecraft:totem_of_undying") || var1.contains("golden_apple") || var1.equals("minecraft:ender_pearl")) {
            return 3;
         } else if (var0.method_58694(class_9334.field_50075) != null) {
            return 4;
         } else {
            return var0.method_7909() instanceof class_1747 ? 6 : 5;
         }
      }
   }

   private static int compare(class_1799 var0, class_1799 var1) {
      int var2 = Integer.compare(group(var0), group(var1));
      if (var2 != 0) {
         return var2;
      } else if (var0.method_7960()) {
         return 0;
      } else {
         int var3 = ItemUtil.id(var0).compareTo(ItemUtil.id(var1));
         return var3 != 0 ? var3 : Integer.compare(var1.method_7947(), var0.method_7947());
      }
   }

   private boolean tickSort() {
      class_1661 var1 = mc.field_1724.method_31548();

      for (int var2 = 9; var2 < 36; var2++) {
         int var3 = var2;

         for (int var4 = var2 + 1; var4 < 36; var4++) {
            if (compare(var1.method_5438(var4), var1.method_5438(var3)) < 0) {
               var3 = var4;
            }
         }

         if (var3 != var2) {
            this.pickup(var3);
            this.pickup(var2);
            if (!mc.field_1724.field_7498.method_34255().method_7960()) {
               this.pickup(var3);
            }

            return true;
         }
      }

      return false;
   }

   @Override
   public String getInfo() {
      return this.sorting ? "Sorting" : null;
   }

   @Override
   public List<String> details() {
      ArrayList var1 = new ArrayList();
      StringBuilder var2 = new StringBuilder("Hotbar: ");

      for (int var3 = 0; var3 < 9; var3++) {
         InvManager.Kind var4 = this.kindOf(var3);
         var2.append(var4 == InvManager.Kind.NONE ? "-" : var4.name().toLowerCase()).append(var3 < 8 ? " · " : "");
      }

      var1.add(var2.toString());
      return var1;
   }

   public static enum Kind {
      NONE,
      SWORD,
      AXE,
      PICKAXE,
      SHOVEL,
      HOE,
      MACE,
      BOW,
      CROSSBOW,
      TRIDENT,
      SHIELD,
      BLOCKS,
      FOOD,
      GAPPLE,
      TOTEM,
      PEARL,
      WATER_BUCKET,
      LAVA_BUCKET,
      CRYSTAL,
      OBSIDIAN,
      FIREWORK,
      TORCH;
   }
}
