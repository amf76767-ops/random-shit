package dev.dihclient.modules.fun;

import dev.dihclient.module.Category;
import dev.dihclient.module.Module;
import dev.dihclient.module.ModuleManager;
import dev.dihclient.setting.BoolSetting;
import dev.dihclient.setting.DoubleSetting;
import dev.dihclient.setting.EnumSetting;
import dev.dihclient.setting.IntSetting;
import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.class_310;
import net.minecraft.class_3417;
import net.minecraft.class_3419;
import net.minecraft.class_1959.class_1963;

public class FakeWeather extends Module {
   public final EnumSetting<FakeWeather.Weather> weather = this.mode("Weather", "Weather you want to see.", FakeWeather.Weather.SNOW);
   public final DoubleSetting intensity = this.dbl("Intensity", "How strong the rain/snow is.", 1.0, 0.1, 1.0, 0.05);
   public final BoolSetting everywhere = this.bool("Everywhere", "Also in deserts and other dry biomes.", true);
   public final BoolSetting lightning = this.bool("Lightning", "Random sky flashes and thunder in Thunder/Blizzard.", true);
   public final IntSetting lightningChance = this.integer("Lightning Rate", "Average seconds between two flashes.", 15, 2, 60)
      .visibleWhen(() -> this.lightning.get() && this.stormy());
   public final BoolSetting thunderSound = this.bool("Thunder Sound", "Plays thunder with every flash.", true)
      .visibleWhen(() -> this.lightning.get() && this.stormy());
   private float oldRain = -1.0F;
   private float oldThunder = -1.0F;

   public FakeWeather() {
      super("Fake Weather", Category.MISC, "Rain, snow, thunder or a clear sky on your screen, no matter what the server says.");
   }

   private boolean stormy() {
      return this.weather.get() == FakeWeather.Weather.THUNDER || this.weather.get() == FakeWeather.Weather.BLIZZARD;
   }

   @Override
   protected void onEnable() {
      if (mc.field_1687 != null) {
         this.oldRain = mc.field_1687.method_8430(1.0F);
         this.oldThunder = mc.field_1687.method_8478(1.0F);
      }
   }

   @Override
   protected void onDisable() {
      if (mc.field_1687 != null && this.oldRain >= 0.0F) {
         mc.field_1687.method_8519(this.oldRain);
         mc.field_1687.method_8496(this.oldThunder);
      }

      this.oldRain = this.oldThunder = -1.0F;
   }

   @Override
   public void onWorldChange() {
      this.oldRain = this.oldThunder = 0.0F;
   }

   @Override
   public void onTick() {
      if (mc.field_1687 != null && mc.field_1724 != null) {
         float var1 = this.weather.get() == FakeWeather.Weather.CLEAR ? 0.0F : this.intensity.getFloat();
         mc.field_1687.method_8519(var1);
         mc.field_1687.method_8496(this.stormy() ? this.intensity.getFloat() : 0.0F);
         if (this.stormy() && this.lightning.get() && ThreadLocalRandom.current().nextInt(this.lightningChance.get() * 20) == 0) {
            mc.field_1687.method_8509(2);
            if (this.thunderSound.get()) {
               mc.field_1687
                  .method_8486(
                     mc.field_1724.method_23317(),
                     mc.field_1724.method_23318() + 20.0,
                     mc.field_1724.method_23321(),
                     class_3417.field_14865,
                     class_3419.field_15252,
                     3.0F,
                     0.8F + ThreadLocalRandom.current().nextFloat() * 0.2F,
                     false
                  );
            }
         }
      }
   }

   public static class_1963 precipitation(class_1963 var0) {
      if (ModuleManager.on(FakeWeather.class) && class_310.method_1551().method_18854()) {
         FakeWeather var1 = ModuleManager.of(FakeWeather.class);
         if (var0 == class_1963.field_9384 && !var1.everywhere.get()) {
            return null;
         } else {
            return switch ((FakeWeather.Weather)var1.weather.get()) {
               case CLEAR -> class_1963.field_9384;
               case RAIN, THUNDER -> class_1963.field_9382;
               case SNOW, BLIZZARD -> class_1963.field_9383;
            };
         }
      } else {
         return null;
      }
   }

   public static boolean forcePrecipitation() {
      if (ModuleManager.on(FakeWeather.class) && class_310.method_1551().method_18854()) {
         FakeWeather var0 = ModuleManager.of(FakeWeather.class);
         return var0.everywhere.get() && var0.weather.get() != FakeWeather.Weather.CLEAR;
      } else {
         return false;
      }
   }

   @Override
   public String getInfo() {
      return this.weather.displayValue();
   }

   public static enum Weather {
      CLEAR,
      RAIN,
      THUNDER,
      SNOW,
      BLIZZARD;
   }
}
