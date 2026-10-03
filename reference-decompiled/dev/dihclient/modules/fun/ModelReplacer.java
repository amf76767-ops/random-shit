package dev.dihclient.modules.fun;

import dev.dihclient.DIHClient;
import dev.dihclient.mixin.accessor.LimbAnimatorAccessor;
import dev.dihclient.module.Category;
import dev.dihclient.module.Module;
import dev.dihclient.module.ModuleManager;
import dev.dihclient.setting.BoolSetting;
import dev.dihclient.setting.EnumSetting;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import net.minecraft.class_1297;
import net.minecraft.class_1299;
import net.minecraft.class_1308;
import net.minecraft.class_1309;
import net.minecraft.class_1510;
import net.minecraft.class_1531;
import net.minecraft.class_1569;
import net.minecraft.class_1657;
import net.minecraft.class_3730;

public class ModelReplacer extends Module {
   private static final ModelReplacer.Into[] CUTE_POOL = new ModelReplacer.Into[]{
      ModelReplacer.Into.CHICKEN,
      ModelReplacer.Into.PIG,
      ModelReplacer.Into.SHEEP,
      ModelReplacer.Into.AXOLOTL,
      ModelReplacer.Into.FROG,
      ModelReplacer.Into.CAT,
      ModelReplacer.Into.FOX,
      ModelReplacer.Into.RABBIT,
      ModelReplacer.Into.ALLAY,
      ModelReplacer.Into.BEE,
      ModelReplacer.Into.PANDA,
      ModelReplacer.Into.ARMADILLO
   };
   private static final ModelReplacer.Into[] SCARY_POOL = new ModelReplacer.Into[]{
      ModelReplacer.Into.ZOMBIE,
      ModelReplacer.Into.SKELETON,
      ModelReplacer.Into.CREEPER,
      ModelReplacer.Into.ENDERMAN,
      ModelReplacer.Into.WARDEN,
      ModelReplacer.Into.WITCH,
      ModelReplacer.Into.PIGLIN,
      ModelReplacer.Into.BLAZE
   };
   public final EnumSetting<ModelReplacer.Mode> mode = this.mode(
         "Mode",
         "One Type: everything becomes the same mob. Random: each one gets its own. Cute / Scary: random from a cute or scary pool.",
         ModelReplacer.Mode.ONE_TYPE
      )
      .onChange(this::clear);
   public final EnumSetting<ModelReplacer.Into> into = this.mode("Into", "Mob used in One Type mode.", ModelReplacer.Into.CHICKEN).onChange(this::clear);
   public final BoolSetting players = this.bool("Players", "Other players.", true);
   public final BoolSetting self = this.bool("Self", "You (third person / F5).", false);
   public final BoolSetting hostile = this.bool("Hostile", "Hostile mobs.", false);
   public final BoolSetting passive = this.bool("Passive", "Animals, villagers and other mobs.", false);
   public final BoolSetting skipFriends = this.bool("Skip Friends", "Friends keep their normal look.", false);
   public final BoolSetting keepName = this.bool("Keep Name", "Players keep their name tag above the new model.", true).onChange(this::clear);
   public final BoolSetting baby = this.bool("Baby", "Use the baby version where the mob has one.", false).onChange(this::clear);
   private final Map<Integer, class_1297> standIns = new HashMap<>();
   private boolean failed;

   public ModelReplacer() {
      super("Model Replacer", Category.FUN, "Players and mobs look like other mobs – turn everyone into chickens or creepers into cats. Only you see it.");
   }

   @Override
   protected void onDisable() {
      this.clear();
   }

   @Override
   public void onWorldChange() {
      this.clear();
   }

   private void clear() {
      this.standIns.clear();
      this.failed = false;
   }

   @Override
   public void onTick() {
      if (mc.field_1724.field_6012 % 100 == 0) {
         Iterator var1 = this.standIns.entrySet().iterator();

         while (var1.hasNext()) {
            class_1297 var2 = mc.field_1687.method_8469((Integer)((Entry)var1.next()).getKey());
            if (var2 == null || var2.method_31481()) {
               var1.remove();
            }
         }
      }
   }

   public static class_1297 swap(class_1297 var0) {
      if (var0 instanceof class_1309 var1 && ModuleManager.on(ModelReplacer.class)) {
         ModelReplacer var2 = ModuleManager.of(ModelReplacer.class);
         if (!var2.failed && var2.applies(var1)) {
            try {
               ModelReplacer.Into var3 = var2.pick(var1);
               class_1297 var4 = var2.standIns.get(var0.method_5628());
               if (var4 == null || var4.method_5864() != var3.type() || var4.method_73183() != var0.method_73183()) {
                  var4 = var2.create(var1, var3);
                  if (var4 == null) {
                     return var0;
                  }

                  var2.standIns.put(var0.method_5628(), var4);
               }

               sync(var1, var4);
               return var4;
            } catch (Throwable var5) {
               var2.failed = true;
               DIHClient.LOG.error("[DIHClient] Model Replacer failed", var5);
               return var0;
            }
         } else {
            return var0;
         }
      } else {
         return var0;
      }
   }

   private boolean applies(class_1309 var1) {
      if (var1 instanceof class_1531 || var1 instanceof class_1510 || var1.method_5628() < 0) {
         return false;
      } else if (var1 == mc.field_1724) {
         return this.self.get();
      } else if (var1 instanceof class_1657 var2) {
         return this.players.get() && (!this.skipFriends.get() || !DIHClient.social().isFriend(var2));
      } else {
         return var1 instanceof class_1569 ? this.hostile.get() : this.passive.get();
      }
   }

   private ModelReplacer.Into pick(class_1309 var1) {
      int var2 = var1.method_5667().hashCode() & 2147483647;

      return switch ((ModelReplacer.Mode)this.mode.get()) {
         case ONE_TYPE -> (ModelReplacer.Into)this.into.get();
         case RANDOM -> ModelReplacer.Into.values()[var2 % ModelReplacer.Into.values().length];
         case CUTE -> CUTE_POOL[var2 % CUTE_POOL.length];
         case SCARY -> SCARY_POOL[var2 % SCARY_POOL.length];
      };
   }

   private class_1297 create(class_1309 var1, ModelReplacer.Into var2) {
      class_1297 var3 = var2.type().method_5883(var1.method_73183(), class_3730.field_52444);
      if (var3 == null) {
         return null;
      } else {
         var3.method_5803(true);
         var3.field_5960 = true;
         if (var3 instanceof class_1308 var4) {
            var4.method_5977(true);
            var4.method_7217(this.baby.get());
         }

         if (var1 instanceof class_1657 && this.keepName.get()) {
            var3.method_5665(var1.method_5477());
            var3.method_5880(true);
         } else if (var1.method_16914()) {
            var3.method_5665(var1.method_5797());
            var3.method_5880(var1.method_5807());
         }

         return var3;
      }
   }

   private static void sync(class_1309 var0, class_1297 var1) {
      var1.method_23327(var0.method_23317(), var0.method_23318(), var0.method_23321());
      var1.field_6014 = var0.field_6014;
      var1.field_6036 = var0.field_6036;
      var1.field_5969 = var0.field_5969;
      var1.field_6038 = var0.field_6038;
      var1.field_5971 = var0.field_5971;
      var1.field_5989 = var0.field_5989;
      var1.method_36456(var0.method_36454());
      var1.method_36457(var0.method_36455());
      var1.field_5982 = var0.field_5982;
      var1.field_6004 = var0.field_6004;
      var1.field_6012 = var0.field_6012;
      var1.method_18799(var0.method_18798());
      var1.method_24830(var0.method_24828());
      var1.method_5660(var0.method_5715());
      var1.method_5648(var0.method_5767());
      var1.method_5834(var0.method_5851());
      if (var1 instanceof class_1309 var2) {
         var2.field_6283 = var0.field_6283;
         var2.field_6220 = var0.field_6220;
         var2.field_6241 = var0.field_6241;
         var2.field_6259 = var0.field_6259;
         var2.field_6235 = var0.field_6235;
         var2.field_6213 = var0.field_6213;
         var2.field_6251 = var0.field_6251;
         var2.field_6229 = var0.field_6229;
         if (var0.method_29504()) {
            var2.method_6033(0.0F);
         }

         LimbAnimatorAccessor var3 = (LimbAnimatorAccessor)var0.field_42108;
         LimbAnimatorAccessor var4 = (LimbAnimatorAccessor)var2.field_42108;
         var4.dih$setLastSpeed(var3.dih$getLastSpeed());
         var4.dih$setSpeed(var3.dih$getSpeed());
         var4.dih$setProgress(var3.dih$getProgress());
      }
   }

   @Override
   public String getInfo() {
      return this.mode.get() == ModelReplacer.Mode.ONE_TYPE ? this.into.displayValue() : this.mode.displayValue();
   }

   public static enum Into {
      CHICKEN,
      PIG,
      COW,
      SHEEP,
      VILLAGER,
      CREEPER,
      ZOMBIE,
      SKELETON,
      ENDERMAN,
      IRON_GOLEM,
      SNOW_GOLEM,
      COPPER_GOLEM,
      WARDEN,
      ALLAY,
      AXOLOTL,
      FROG,
      PARROT,
      BEE,
      CAT,
      FOX,
      WOLF,
      RABBIT,
      PANDA,
      SNIFFER,
      CAMEL,
      ARMADILLO,
      SQUID,
      GHAST,
      HAPPY_GHAST,
      WITCH,
      PIGLIN,
      BLAZE;

      class_1299<?> type() {
         return switch (this) {
            case CHICKEN -> class_1299.field_6132;
            case PIG -> class_1299.field_6093;
            case COW -> class_1299.field_6085;
            case SHEEP -> class_1299.field_6115;
            case VILLAGER -> class_1299.field_6077;
            case CREEPER -> class_1299.field_6046;
            case ZOMBIE -> class_1299.field_6051;
            case SKELETON -> class_1299.field_6137;
            case ENDERMAN -> class_1299.field_6091;
            case IRON_GOLEM -> class_1299.field_6147;
            case SNOW_GOLEM -> class_1299.field_6047;
            case COPPER_GOLEM -> class_1299.field_61221;
            case WARDEN -> class_1299.field_38095;
            case ALLAY -> class_1299.field_38384;
            case AXOLOTL -> class_1299.field_28315;
            case FROG -> class_1299.field_37419;
            case PARROT -> class_1299.field_6104;
            case BEE -> class_1299.field_20346;
            case CAT -> class_1299.field_16281;
            case FOX -> class_1299.field_17943;
            case WOLF -> class_1299.field_6055;
            case RABBIT -> class_1299.field_6140;
            case PANDA -> class_1299.field_6146;
            case SNIFFER -> class_1299.field_42622;
            case CAMEL -> class_1299.field_40116;
            case ARMADILLO -> class_1299.field_47754;
            case SQUID -> class_1299.field_6114;
            case GHAST -> class_1299.field_6107;
            case HAPPY_GHAST -> class_1299.field_59668;
            case WITCH -> class_1299.field_6145;
            case PIGLIN -> class_1299.field_22281;
            case BLAZE -> class_1299.field_6099;
         };
      }
   }

   public static enum Mode {
      ONE_TYPE,
      RANDOM,
      CUTE,
      SCARY;
   }
}
