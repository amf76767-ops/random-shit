package dev.dihclient.modules.player;

import dev.dihclient.module.Category;
import dev.dihclient.module.Module;
import dev.dihclient.setting.BoolSetting;
import dev.dihclient.setting.IntSetting;
import dev.dihclient.util.Notifications;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import net.minecraft.class_1304;
import net.minecraft.class_1799;
import net.minecraft.class_1802;
import net.minecraft.class_3414;
import net.minecraft.class_3417;

public class SurvivalAlerts extends Module {
   public final BoolSetting health = this.bool("Low Health", "Warn when health (incl. absorption) drops below the threshold.", true);
   public final IntSetting hearts = this.integer("Hearts", "Threshold in hearts.", 5, 1, 19).visibleWhen(this.health::get);
   public final BoolSetting durability = this.bool("Durability", "Warn when held items or armour are about to break.", true);
   public final IntSetting percent = this.integer("Durability %", "Threshold in percent of max durability.", 10, 1, 50).visibleWhen(this.durability::get);
   public final BoolSetting totem = this.bool("No Totem", "Warn when your off-hand has no totem (and you have none in your hotbar either).", false);
   public final BoolSetting hunger = this.bool("Low Hunger", "Warn when food drops to 6 (no more sprinting soon).", true);
   public final BoolSetting bigAlert = this.bool("Big Alert", "Large text in the middle of the screen.", true);
   public final BoolSetting sound = this.bool("Sound", "Warning sound.", true);
   public final IntSetting repeat = this.integer("Repeat", "Repeat the low-health sound every N seconds while it stays low (0 = once).", 3, 0, 30)
      .visibleWhen(this.health::get);
   private boolean lowHp;
   private boolean lowFood;
   private boolean noTotem;
   private final Map<class_1304, Boolean> lowDur = new EnumMap<>(class_1304.class);
   private int hpTimer;
   private static final class_1304[] SLOTS = new class_1304[]{
      class_1304.field_6173, class_1304.field_6171, class_1304.field_6169, class_1304.field_6174, class_1304.field_6172, class_1304.field_6166
   };

   public SurvivalAlerts() {
      super("Survival Alerts", Category.PLAYER, "Warns on low health, items about to break, missing totem and low hunger.");
   }

   @Override
   protected void onEnable() {
      this.lowHp = this.lowFood = this.noTotem = false;
      this.lowDur.clear();
      this.hpTimer = 0;
   }

   @Override
   public void onWorldChange() {
      this.onEnable();
   }

   private void warn(String var1, boolean var2) {
      Notifications.warn(this.name(), var1);
      if (this.bigAlert.get() && var2) {
         Notifications.alert(var1, 50);
      }

      if (this.sound.get()) {
         this.ping(var2);
      }
   }

   private void ping(boolean var1) {
      if (var1) {
         mc.field_1724.method_5783((class_3414)class_3417.field_14624.comp_349(), 1.0F, 0.6F);
      } else {
         mc.field_1724.method_5783((class_3414)class_3417.field_14622.comp_349(), 0.8F, 0.8F);
      }
   }

   @Override
   public void onTick() {
      if (!mc.field_1724.method_29504() && !mc.field_1724.method_68878() && !mc.field_1724.method_7325()) {
         if (this.health.get()) {
            float var1 = mc.field_1724.method_6032() + mc.field_1724.method_6067();
            float var2 = this.hearts.get().intValue() * 2.0F;
            if (var1 < var2) {
               if (!this.lowHp) {
                  this.lowHp = true;
                  this.warn("LOW HEALTH: " + String.format("%.1f", var1 / 2.0F) + " hearts", true);
                  this.hpTimer = 0;
               } else if (this.repeat.get() > 0 && this.sound.get() && ++this.hpTimer >= this.repeat.get() * 20) {
                  this.hpTimer = 0;
                  this.ping(true);
               }
            } else if (var1 >= var2 + 2.0F) {
               this.lowHp = false;
            }
         }

         if (this.durability.get()) {
            for (class_1304 var4 : SLOTS) {
               class_1799 var5 = mc.field_1724.method_6118(var4);
               boolean var6 = false;
               if (!var5.method_7960() && var5.method_7963() && var5.method_7936() > 0) {
                  int var7 = var5.method_7936() - var5.method_7919();
                  var6 = var7 * 100 < var5.method_7936() * this.percent.get();
                  if (var6 && !this.lowDur.getOrDefault(var4, false)) {
                     this.warn(var5.method_7964().getString() + " almost broken (" + var7 + " left)", false);
                  }
               }

               this.lowDur.put(var4, var6);
            }
         }

         if (this.totem.get()) {
            boolean var9 = !mc.field_1724.method_6079().method_31574(class_1802.field_8288);
            if (var9) {
               boolean var12 = false;

               for (int var13 = 0; var13 < 9; var13++) {
                  if (mc.field_1724.method_31548().method_5438(var13).method_31574(class_1802.field_8288)) {
                     var12 = true;
                  }
               }

               var9 = !var12;
            }

            if (var9 && !this.noTotem) {
               this.warn("No totem equipped!", true);
            }

            this.noTotem = var9;
         }

         if (this.hunger.get()) {
            int var10 = mc.field_1724.method_7344().method_7586();
            if (var10 <= 6 && !this.lowFood) {
               this.warn("Low hunger – eat something", false);
            }

            if (var10 <= 6) {
               this.lowFood = true;
            } else if (var10 >= 10) {
               this.lowFood = false;
            }
         }
      }
   }

   @Override
   public List<String> details() {
      ArrayList var1 = new ArrayList();
      var1.add(
         "Health "
            + (this.lowHp ? "LOW" : "ok")
            + " · Hunger "
            + (this.lowFood ? "LOW" : "ok")
            + (this.totem.get() ? " · Totem " + (this.noTotem ? "MISSING" : "ok") : "")
      );
      return var1;
   }
}
