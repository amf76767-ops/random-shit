package dev.dihclient.modules.player;

import dev.dihclient.autobuild.Restock;
import dev.dihclient.autobuild.Worker;
import dev.dihclient.module.Category;
import dev.dihclient.module.Module;
import dev.dihclient.module.ModuleManager;
import dev.dihclient.modules.world.AutoBuild;
import dev.dihclient.modules.world.SafeRoute;
import dev.dihclient.setting.BoolSetting;
import dev.dihclient.setting.IdListSetting;
import dev.dihclient.setting.IntSetting;
import dev.dihclient.util.InvUtil;
import dev.dihclient.util.ItemUtil;
import dev.dihclient.util.MoveUtil;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.class_1661;
import net.minecraft.class_1703;
import net.minecraft.class_1713;
import net.minecraft.class_1735;
import net.minecraft.class_1792;
import net.minecraft.class_1799;
import net.minecraft.class_2338;
import net.minecraft.class_239;
import net.minecraft.class_243;
import net.minecraft.class_3965;
import net.minecraft.class_465;
import net.minecraft.class_239.class_240;

public class AutoRestock extends Module {
   public final BoolSetting hotbarRefill = this.bool("Refill Hotbar", "Refills hotbar stacks from your inventory before they run out.", true);
   public final IntSetting refillAt = this.integer("Refill At", "Refills when a hotbar stack has this many or fewer left (0 = only when empty).", 8, 0, 63)
      .visibleWhen(this.hotbarRefill::get);
   public final BoolSetting fromChests = this.bool("From Chests", "Fetches items from nearby containers when you run low.", true);
   public final IdListSetting items = this.ids(
         "Keep Stocked",
         "Items that are fetched from containers.",
         IdListSetting.Kind.ITEM,
         new String[]{
            "minecraft:torch",
            "minecraft:cobblestone",
            "minecraft:cobbled_deepslate",
            "minecraft:golden_carrot",
            "minecraft:cooked_beef",
            "minecraft:bread",
            "minecraft:arrow",
            "minecraft:experience_bottle"
         }
      )
      .visibleWhen(this.fromChests::get);
   public final BoolSetting hotbarItems = this.bool("Hotbar Items Too", "Also keeps everything that is in your hotbar stocked.", false)
      .visibleWhen(this.fromChests::get);
   public final IntSetting minCount = this.integer("Min Count", "Fetches more when you have fewer than this.", 16, 1, 256).visibleWhen(this.fromChests::get);
   public final IntSetting takeStacks = this.integer("Take Stacks", "Stacks taken per visit.", 2, 1, 27).visibleWhen(this.fromChests::get);
   public final IntSetting range = this.integer("Range", "How far away a container may be.", 16, 4, 48).visibleWhen(this.fromChests::get);
   public final BoolSetting walk = this.bool("Walk There", "Walks to the container. Off = only containers within reach.", true)
      .visibleWhen(this.fromChests::get);
   public final BoolSetting human = this.bool("Human", "Turns your real camera to the container and takes stacks one by one.", true)
      .visibleWhen(this.fromChests::get);
   public final BoolSetting onlyIdle = this.bool(
         "Only When Idle", "Only while you don't move yourself and no bot (Goto, AutoMine, AutoBuild …) is running – AutoBuild restocks on its own.", true
      )
      .visibleWhen(this.fromChests::get);
   public final BoolSetting rememberOpened = this.bool("Remember Opened", "Remembers what's in every container you open yourself.", true);
   private final Restock restock = new Restock();
   private final Worker worker = new Worker();
   private final class_1792[] lastItem = new class_1792[9];
   private int refillWait;
   private int checkWait;
   private int recordWait;
   private int cooldown;
   private class_2338 lookedAt;
   private boolean recorded;
   private String status = "Idle";
   private int refills;

   public AutoRestock() {
      super(
         "AutoRestock", Category.AUTOMATION, "Refills the hotbar from your inventory and fetches low items from nearby chests (memory shared with AutoBuild)."
      );
      this.action("Forget Chests", "Clears the remembered container contents.", Restock::forgetAll);
   }

   @Override
   protected void onEnable() {
      for (int var1 = 0; var1 < 9; var1++) {
         this.lastItem[var1] = null;
      }

      this.status = "Idle";
   }

   @Override
   protected void onDisable() {
      this.restock.reset();
      this.worker.reset();
      this.status = "Idle";
   }

   @Override
   public void onWorldChange() {
      this.restock.reset();
      this.worker.reset();
      this.lookedAt = null;
   }

   @Override
   public void onTick() {
      if (inGame()) {
         if (this.restock.isActive()) {
            this.tickRestock();
         } else {
            this.tickRemember();
            if (mc.field_1755 != null) {
               for (int var1 = 0; var1 < 9; var1++) {
                  class_1799 var2 = mc.field_1724.method_31548().method_5438(var1);
                  this.lastItem[var1] = var2.method_7960() ? null : var2.method_7909();
               }
            } else {
               if (this.hotbarRefill.get()) {
                  this.tickRefill();
               }

               if (this.cooldown > 0) {
                  this.cooldown--;
               } else if (this.fromChests.get() && ++this.checkWait >= 40) {
                  this.checkWait = 0;
                  this.tryStartRestock();
               }
            }
         }
      }
   }

   private void tickRefill() {
      class_1661 var1 = mc.field_1724.method_31548();
      if (this.refillWait > 0) {
         this.refillWait--;
      } else {
         for (int var2 = 0; var2 < 9; var2++) {
            class_1799 var3 = var1.method_5438(var2);
            class_1792 var4 = var3.method_7960() ? this.lastItem[var2] : var3.method_7909();
            if (var4 != null
               && (var3.method_7960() || var4.method_7882() > 1 && var3.method_7947() <= this.refillAt.get() && var3.method_7947() < var4.method_7882())) {
               int var5 = this.source(var4);
               if (var5 >= 0) {
                  int var6 = mc.field_1724.field_7498.field_7763;
                  if (var3.method_7960()) {
                     InvUtil.swapToHotbar(var5, var2);
                  } else {
                     mc.field_1761.method_2906(var6, var5, 0, class_1713.field_7790, mc.field_1724);
                     mc.field_1761.method_2906(var6, 36 + var2, 0, class_1713.field_7790, mc.field_1724);
                     mc.field_1761.method_2906(var6, var5, 0, class_1713.field_7790, mc.field_1724);
                  }

                  this.refills++;
                  this.refillWait = 3;
                  this.lastItem[var2] = var4;
                  return;
               }
            }

            if (!var3.method_7960()) {
               this.lastItem[var2] = var3.method_7909();
            } else if (this.source(var4) < 0) {
               this.lastItem[var2] = null;
            }
         }
      }
   }

   private int source(class_1792 var1) {
      if (var1 == null) {
         return -1;
      } else {
         class_1661 var2 = mc.field_1724.method_31548();
         int var3 = -1;
         int var4 = 0;

         for (int var5 = 9; var5 < 36; var5++) {
            class_1799 var6 = var2.method_5438(var5);
            if (!var6.method_7960() && var6.method_7909() == var1 && var6.method_7947() > var4) {
               var4 = var6.method_7947();
               var3 = var5;
            }
         }

         return var3;
      }
   }

   private void tickRemember() {
      if (this.rememberOpened.get()) {
         if (mc.field_1755 == null) {
            this.recorded = false;
            class_239 var2 = mc.field_1765;
            this.lookedAt = var2 instanceof class_3965 var3 && var2.method_17783() == class_240.field_1332 && Restock.isContainer(var3.method_17777())
               ? var3.method_17777().method_10062()
               : null;
         } else if (!this.recorded && this.lookedAt != null && mc.field_1755 instanceof class_465 var1) {
            class_1703 var10 = var1.method_17577();
            if (var10 != mc.field_1724.field_7498) {
               HashSet var11 = new HashSet();
               boolean var4 = false;

               for (Object var6 : var10.field_7761) {
                  class_1735 var7 = (class_1735)var6;
                  if (!(var7.field_7871 instanceof class_1661)) {
                     class_1799 var8 = var7.method_7677();
                     if (!var8.method_7960()) {
                        var11.add(var8.method_7909());
                     }

                     var4 = true;
                  }
               }

               if (var4 && (!var11.isEmpty() || ++this.recordWait > 20)) {
                  Restock.remember(this.lookedAt, var11);
                  this.recorded = true;
                  this.recordWait = 0;
               }
            }
         }
      }
   }

   private Set<class_1792> tracked() {
      LinkedHashSet var1 = new LinkedHashSet<>(ItemUtil.items(this.items.get()));
      if (this.hotbarItems.get()) {
         for (int var2 = 0; var2 < 9; var2++) {
            class_1799 var3 = mc.field_1724.method_31548().method_5438(var2);
            if (!var3.method_7960() && var3.method_7909().method_7882() > 1) {
               var1.add(var3.method_7909());
            }
         }
      }

      return var1;
   }

   private void tryStartRestock() {
      if (!mc.field_1724.method_68878() && mc.field_1724.method_31548().method_7376() >= 0) {
         if (!this.onlyIdle.get() || !MoveUtil.isMoving() && !SafeRoute.botRunning() && !ModuleManager.on(AutoBuild.class)) {
            Set var1 = this.tracked();
            LinkedHashSet var2 = new LinkedHashSet();

            for (class_1792 var4 : var1) {
               if (InvUtil.count(var1x -> var1x.method_31574(var4)) < this.minCount.get()) {
                  var2.add(var4);
               }
            }

            double var7 = this.walk.get() ? this.range.get().intValue() : 4.5;

            for (class_1792 var6 : var2) {
               if (this.restock.begin(var6, var2, var7)) {
                  this.restock.limit = this.takeStacks.get();
                  this.restock.maxStacks = this.human.get() ? 1 : this.takeStacks.get();
                  this.restock.human = this.human.get();
                  this.restock.speed = 30.0F;
                  this.status = "Fetching " + var6.method_63680().getString();
                  return;
               }
            }
         }
      }
   }

   private void tickRestock() {
      class_2338 var1 = this.restock.target();
      if (var1 != null) {
         if (this.walk.get()) {
            this.worker.walkTo(class_243.method_24953(var1), 3.5);
            if (this.worker.isStuck()) {
               this.worker.release();
               this.restock.reset();
               this.status = "Can't reach the container";
               this.cooldown = 200;
               return;
            }
         }
      } else {
         this.worker.release();
      }

      this.status = this.restock.tick(4.5);
      if (!this.restock.isActive()) {
         this.worker.release();
         this.cooldown = 100;
      }
   }

   @Override
   public String getInfo() {
      return this.restock.isActive() ? "Fetching" : (this.refills > 0 ? String.valueOf(this.refills) : null);
   }

   @Override
   public List<String> details() {
      ArrayList var1 = new ArrayList();
      var1.add("Status: " + this.status);
      var1.add("Refills: " + this.refills + " · containers known: " + Restock.knownCount());
      return var1;
   }
}
