package dev.dihclient.modules.player;

import dev.dihclient.mixin.accessor.FishingBobberEntityAccessor;
import dev.dihclient.module.Category;
import dev.dihclient.module.Module;
import dev.dihclient.module.ModuleManager;
import dev.dihclient.modules.automation.StashSorter;
import dev.dihclient.modules.world.Goto;
import dev.dihclient.setting.BoolSetting;
import dev.dihclient.setting.EnumSetting;
import dev.dihclient.setting.IdListSetting;
import dev.dihclient.setting.IntSetting;
import dev.dihclient.util.InvUtil;
import dev.dihclient.util.ItemUtil;
import dev.dihclient.util.JunkUtil;
import dev.dihclient.util.Notifications;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Predicate;
import net.minecraft.class_1268;
import net.minecraft.class_1536;
import net.minecraft.class_1799;
import net.minecraft.class_1802;
import net.minecraft.class_243;

public class AutoFish extends Module {
   public final IntSetting reelDelay = this.integer("Reel Delay", "Ticks to wait after the bite before reeling in.", 2, 0, 10);
   public final IntSetting castDelay = this.integer("Cast Delay", "Ticks before casting again.", 12, 4, 60);
   public final BoolSetting randomize = this.bool("Randomize", "Adds a few random ticks to both delays.", true);
   public final BoolSetting autoSwitch = this.bool("Auto Switch", "Takes a fishing rod from the hotbar if you hold none.", true);
   public final BoolSetting autoCast = this.bool("Auto Cast", "Casts automatically when no bobber is out.", true);
   public final IntSetting rodSaver = this.integer(
      "Rod Saver", "Never uses a rod with this little durability left – switches to another one (0 = off).", 5, 0, 64
   );
   public final BoolSetting dropJunk = this.bool("Drop Junk", "Throws the junk catches away.", false);
   public final IdListSetting junk = this.ids(
         "Junk",
         "Items thrown away with Drop Junk.",
         IdListSetting.Kind.ITEM,
         new String[]{
            "minecraft:rotten_flesh",
            "minecraft:stick",
            "minecraft:string",
            "minecraft:bone",
            "minecraft:bowl",
            "minecraft:leather",
            "minecraft:leather_boots",
            "minecraft:ink_sac",
            "minecraft:tripwire_hook",
            "minecraft:lily_pad",
            "minecraft:potion",
            "minecraft:bamboo"
         }
      )
      .visibleWhen(this.dropJunk::get);
   public final EnumSetting<AutoFish.WhenFull> whenFull = this.mode(
      "When Full",
      "Keep: go on (new catches are lost). Stop: turn off. Deposit: StashSorter empties the inventory into chests nearby, then it walks back and fishes on.",
      AutoFish.WhenFull.DEPOSIT
   );
   private int reelTimer = -1;
   private int catches;
   private class_243 spot;
   private float spotYaw;
   private float spotPitch;
   private int away;
   private int noDepositUntil;
   private int castTimer = -1;
   private class_1536 reeled;
   private int bobberTicks;

   public AutoFish() {
      super("AutoFish", Category.AUTOMATION, "Reels in automatically when a fish bites and recasts – AFK fishing.");
   }

   @Override
   protected void onEnable() {
      this.reelTimer = this.castTimer = -1;
      this.away = 0;
      this.reeled = null;
      this.bobberTicks = 0;
      if (mc.field_1724 != null) {
         this.spot = mc.field_1724.method_73189();
         this.spotYaw = mc.field_1724.method_36454();
         this.spotPitch = mc.field_1724.method_36455();
      }
   }

   private static boolean usable(class_1799 var0, int var1) {
      return var0.method_31574(class_1802.field_8378) && (var1 <= 0 || ItemUtil.durabilityLeft(var0) > var1);
   }

   private boolean tickTrip() {
      if (this.away != 1) {
         if (this.away == 2) {
            if (!Goto.running()) {
               this.away = 3;
            }

            return true;
         } else if (this.away == 3) {
            mc.field_1724.method_36456(this.spotYaw);
            mc.field_1724.method_36457(this.spotPitch);
            this.away = 0;
            this.castTimer = 10;
            if (JunkUtil.full()) {
               Notifications.warn("AutoFish", "Inventory still full after depositing – stopped");
               this.setEnabled(false);
            }

            return true;
         } else {
            return false;
         }
      } else {
         if (!ModuleManager.on(StashSorter.class)) {
            if (this.spot != null && mc.field_1724.method_73189().method_1022(this.spot) > 1.5) {
               Goto.start((int)Math.floor(this.spot.field_1352), (int)Math.floor(this.spot.field_1351), (int)Math.floor(this.spot.field_1350), "fishing spot");
               this.away = 2;
            } else {
               this.away = 3;
            }
         }

         return true;
      }
   }

   private int jitter() {
      return this.randomize.get() ? ThreadLocalRandom.current().nextInt(0, 4) : 0;
   }

   private boolean holdRod() {
      int var1 = this.rodSaver.get();
      if (usable(mc.field_1724.method_6047(), var1)) {
         return true;
      } else if (!this.autoSwitch.get()) {
         return false;
      } else {
         int var2 = InvUtil.findHotbar((Predicate<class_1799>)(var1x -> usable(var1x, var1)));
         if (var2 < 0) {
            int var3 = InvUtil.findInventory(var1x -> usable(var1x, var1));
            if (var3 < 9) {
               if (this.castTimer <= 0) {
                  Notifications.warn("AutoFish", "No usable fishing rod left" + (var1 > 0 ? " (Rod Saver)" : ""));
                  this.castTimer = 200;
               }

               return false;
            }

            var2 = InvUtil.selectedSlot();
            InvUtil.swapToHotbar(var3, var2);
         }

         InvUtil.select(var2);
         return true;
      }
   }

   private void use() {
      mc.field_1761.method_2919(mc.field_1724, class_1268.field_5808);
      mc.field_1724.method_6104(class_1268.field_5808);
   }

   @Override
   public void onTick() {
      if (mc.field_1724 == null || !this.tickTrip()) {
         if (mc.field_1755 == null && mc.field_1724.field_7513 == null && this.reelTimer < 0) {
            if (this.dropJunk.get() && JunkUtil.drop(this.junk.get(), 1) > 0) {
               return;
            }

            if (JunkUtil.full()) {
               if (this.whenFull.get() == AutoFish.WhenFull.STOP) {
                  Notifications.info("AutoFish", "Inventory full – stopped after " + this.catches + " catches");
                  this.setEnabled(false);
                  return;
               }

               if (this.whenFull.get() == AutoFish.WhenFull.DEPOSIT && mc.field_1724.field_6012 >= this.noDepositUntil) {
                  this.noDepositUntil = mc.field_1724.field_6012 + 1200;
                  StashSorter.run(StashSorter.Mode.DEPOSIT);
                  if (ModuleManager.on(StashSorter.class)) {
                     this.away = 1;
                     return;
                  }
               }
            }
         }

         if (mc.field_1755 == null) {
            class_1536 var1 = mc.field_1724.field_7513;
            if (this.reelTimer >= 0) {
               if (this.reelTimer-- == 0) {
                  if (var1 != null && this.holdRod()) {
                     this.use();
                     this.catches++;
                     this.reeled = var1;
                  }

                  this.castTimer = this.castDelay.get() + this.jitter();
               }
            } else if (var1 != null) {
               if (var1 == this.reeled) {
                  if (++this.bobberTicks > 40) {
                     this.reeled = null;
                     this.bobberTicks = 0;
                  }
               } else if ((Boolean)var1.method_5841().method_12789(FishingBobberEntityAccessor.dih$caughtFish())) {
                  this.reelTimer = this.reelDelay.get() + this.jitter();
                  this.bobberTicks = 0;
               } else if (++this.bobberTicks > 1800 && this.holdRod()) {
                  this.use();
                  this.reeled = var1;
                  this.bobberTicks = 0;
                  this.castTimer = this.castDelay.get() + this.jitter();
               }
            } else {
               this.reeled = null;
               this.bobberTicks = 0;
               if (this.autoCast.get()) {
                  if (this.castTimer > 0) {
                     this.castTimer--;
                  } else if (this.holdRod()) {
                     this.use();
                     this.castTimer = this.castDelay.get() + this.jitter();
                  }
               }
            }
         }
      }
   }

   @Override
   public String getInfo() {
      return this.away > 0 ? "Deposit" : (this.catches > 0 ? String.valueOf(this.catches) : null);
   }

   public static enum WhenFull {
      KEEP,
      STOP,
      DEPOSIT;
   }
}
