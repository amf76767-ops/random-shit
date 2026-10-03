package dev.dihclient.mixin;

import dev.dihclient.module.ModuleManager;
import dev.dihclient.modules.basefinding.PlayerActivity;
import dev.dihclient.modules.basefinding.RecentActivity;
import dev.dihclient.modules.client.DihChat;
import dev.dihclient.modules.combat.KnockbackControl;
import dev.dihclient.scan.ChunkEvents;
import net.minecraft.class_243;
import net.minecraft.class_2623;
import net.minecraft.class_2626;
import net.minecraft.class_2637;
import net.minecraft.class_2664;
import net.minecraft.class_2666;
import net.minecraft.class_2672;
import net.minecraft.class_2743;
import net.minecraft.class_2767;
import net.minecraft.class_310;
import net.minecraft.class_3414;
import net.minecraft.class_634;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({class_634.class})
public abstract class ClientPlayNetworkHandlerMixin {
   @Unique
   private class_243 dih$velocityBeforeExplosion;

   @Inject(
      method = {"method_11132"},
      at = {@At("TAIL")}
   )
   private void dih$velocity(class_2743 var1, CallbackInfo var2) {
      class_310 var3 = class_310.method_1551();
      if (var3.field_1724 != null && var1.method_11818() == var3.field_1724.method_5628() && ModuleManager.on(KnockbackControl.class)) {
         KnockbackControl var4 = ModuleManager.of(KnockbackControl.class);
         class_243 var5 = var1.method_73085();
         double[] var6 = var4.scale(false);
         var3.field_1724.method_18800(var5.field_1352 * var6[0], var5.field_1351 * var6[1], var5.field_1350 * var6[0]);
      }
   }

   @Inject(
      method = {"method_11124"},
      at = {@At("HEAD")}
   )
   private void dih$explosionHead(class_2664 var1, CallbackInfo var2) {
      class_310 var3 = class_310.method_1551();
      this.dih$velocityBeforeExplosion = var3.method_18854() && var3.field_1724 != null ? var3.field_1724.method_18798() : null;
      PlayerActivity var4 = dih$activity();
      if (var4 != null) {
         var4.onExplosion(var1.comp_2883());
      }

      RecentActivity var5 = RecentActivity.active();
      if (var5 != null) {
         var5.onExplosion(var1.comp_2883());
      }
   }

   @Inject(
      method = {"method_11124"},
      at = {@At("TAIL")}
   )
   private void dih$explosionTail(class_2664 var1, CallbackInfo var2) {
      class_310 var3 = class_310.method_1551();
      class_243 var4 = this.dih$velocityBeforeExplosion;
      this.dih$velocityBeforeExplosion = null;
      if (var4 != null && var3.field_1724 != null && ModuleManager.on(KnockbackControl.class)) {
         KnockbackControl var5 = ModuleManager.of(KnockbackControl.class);
         class_243 var6 = var3.field_1724.method_18798().method_1020(var4);
         double[] var7 = var5.scale(true);
         var3.field_1724.method_18799(var4.method_1031(var6.field_1352 * var7[0], var6.field_1351 * var7[1], var6.field_1350 * var7[0]));
      }
   }

   @Inject(
      method = {"method_11128"},
      at = {@At("TAIL")}
   )
   private void dih$chunk(class_2672 var1, CallbackInfo var2) {
      if (class_310.method_1551().method_18854()) {
         ChunkEvents.onChunkLoaded(var1.method_11523(), var1.method_11524());
      }
   }

   @Inject(
      method = {"method_11136"},
      at = {@At("TAIL")}
   )
   private void dih$blockUpdate(class_2626 var1, CallbackInfo var2) {
      if (class_310.method_1551().method_18854()) {
         ChunkEvents.onBlockUpdate(var1.method_11309(), var1.method_11308());
      }
   }

   @Inject(
      method = {"method_11100"},
      at = {@At("TAIL")}
   )
   private void dih$deltaUpdate(class_2637 var1, CallbackInfo var2) {
      if (class_310.method_1551().method_18854()) {
         var1.method_30621((var0, var1x) -> ChunkEvents.onBlockUpdate(var0.method_10062(), var1x));
      }
   }

   @Inject(
      method = {"method_11107"},
      at = {@At("TAIL")}
   )
   private void dih$unload(class_2666 var1, CallbackInfo var2) {
      if (class_310.method_1551().method_18854()) {
         ChunkEvents.onChunkUnloaded(var1.comp_1726().field_9181, var1.comp_1726().field_9180);
      }
   }

   @Unique
   private static PlayerActivity dih$activity() {
      class_310 var0 = class_310.method_1551();
      return var0.method_18854() && var0.field_1687 != null && ModuleManager.on(PlayerActivity.class) ? ModuleManager.of(PlayerActivity.class) : null;
   }

   @Inject(
      method = {"method_11136"},
      at = {@At("HEAD")}
   )
   private void dih$activityBlock(class_2626 var1, CallbackInfo var2) {
      PlayerActivity var3 = dih$activity();
      if (var3 != null) {
         var3.onBlockChange(var1.method_11309(), var1.method_11308());
      }

      RecentActivity var4 = RecentActivity.active();
      if (var4 != null) {
         var4.onBlockChange(var1.method_11309(), var1.method_11308());
      }
   }

   @Inject(
      method = {"method_11100"},
      at = {@At("HEAD")}
   )
   private void dih$activityDelta(class_2637 var1, CallbackInfo var2) {
      PlayerActivity var3 = dih$activity();
      if (var3 != null) {
         var1.method_30621((var1x, var2x) -> var3.onBlockChange(var1x.method_10062(), var2x));
      }

      RecentActivity var4 = RecentActivity.active();
      if (var4 != null) {
         var1.method_30621((var1x, var2x) -> var4.onBlockChange(var1x.method_10062(), var2x));
      }
   }

   @Inject(
      method = {"method_11158"},
      at = {@At("HEAD")}
   )
   private void dih$activityBlockEvent(class_2623 var1, CallbackInfo var2) {
      PlayerActivity var3 = dih$activity();
      if (var3 != null) {
         var3.onBlockEvent(var1.method_11298(), var1.method_11295(), var1.method_11294(), var1.method_11296());
      }

      RecentActivity var4 = RecentActivity.active();
      if (var4 != null) {
         var4.onBlockEvent(var1.method_11298(), var1.method_11295(), var1.method_11294(), var1.method_11296());
      }
   }

   @Inject(
      method = {"method_11146"},
      at = {@At("HEAD")}
   )
   private void dih$activitySound(class_2767 var1, CallbackInfo var2) {
      PlayerActivity var3 = dih$activity();
      if (var3 != null) {
         var3.onSound((class_3414)var1.method_11894().comp_349(), var1.method_11890(), var1.method_11889(), var1.method_11893());
      }
   }

   @Inject(
      method = {"method_45729"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void dih$chat(String var1, CallbackInfo var2) {
      if (DihChat.onOutgoing(var1)) {
         var2.cancel();
      }
   }
}
