package dev.dihclient.modules.fun;

import dev.dihclient.module.Category;
import dev.dihclient.module.Module;
import dev.dihclient.module.ModuleManager;
import dev.dihclient.render.Render3D;
import dev.dihclient.setting.BoolSetting;
import dev.dihclient.setting.ColorSetting;
import dev.dihclient.setting.DoubleSetting;
import dev.dihclient.setting.EnumSetting;
import dev.dihclient.setting.IntSetting;
import dev.dihclient.util.Compat;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import net.minecraft.class_12076;
import net.minecraft.class_3532;
import net.minecraft.class_5636;
import net.minecraft.class_2874.class_12326;
import org.joml.Vector3f;
import org.joml.Vector4f;

public class CustomSky extends Module {
   public final EnumSetting<CustomSky.Preset> preset = this.mode(
      "Preset", "Colour style of sky and fog. Vanilla keeps the normal colours (only the effects).", CustomSky.Preset.GALAXY
   );
   public final ColorSetting skyColor = this.color("Sky Color", "Sky colour for the Custom preset.", -11567873)
      .visibleWhen(() -> this.preset.get() == CustomSky.Preset.CUSTOM);
   public final ColorSetting fogColor = this.color("Fog Color", "Horizon / fog colour for the Custom preset.", -5191425)
      .visibleWhen(() -> this.preset.get() == CustomSky.Preset.CUSTOM);
   public final BoolSetting dayCycle = this.bool("Day Cycle", "Sky gets darker at night. Off = same colour all day.", true);
   public final DoubleSetting fogTint = this.dbl("Fog Tint", "How much the horizon fog takes the sky colour.", 0.75, 0.0, 1.0, 0.05);
   public final BoolSetting aurora = this.bool("Aurora", "Waving northern lights in the northern sky.", true);
   public final EnumSetting<CustomSky.AuroraStyle> auroraStyle = this.mode("Aurora Style", "Colours of the northern lights.", CustomSky.AuroraStyle.GREEN)
      .visibleWhen(this.aurora::get);
   public final BoolSetting nebula = this.bool("Nebula", "Colourful milky way band across the sky.", true);
   public final BoolSetting stars = this.bool("More Stars", "Dense twinkling star field.", true);
   public final BoolSetting planet = this.bool("Planet", "A big ringed planet hangs in the sky.", true);
   public final BoolSetting meteors = this.bool("Shooting Stars", "Shooting stars now and then.", true);
   public final IntSetting meteorRate = this.integer("Meteors / Min", "Shooting stars per minute.", 6, 1, 60).visibleWhen(this.meteors::get);
   public final BoolSetting atDay = this.bool("Effects At Day", "Show aurora, stars and nebula during the day too.", false);
   public final BoolSetting clearSky = this.bool("Clear Sky", "Rain and thunder don't grey out the sky.", false);
   public final BoolSetting hideSun = this.bool("Hide Sun", "Removes the sun.", false);
   public final BoolSetting hideMoon = this.bool("Hide Moon", "Removes the moon.", false);
   public final DoubleSetting brightness = this.dbl("Effect Brightness", "Brightness of the drawn effects.", 1.0, 0.2, 2.0, 0.05);
   private static final int STAR_COUNT = 1400;
   private final Vector3f[] starDir = new Vector3f[1400];
   private final float[] starSize = new float[1400];
   private final float[] starPhase = new float[1400];
   private final int[] starColor = new int[1400];
   private final float[][] starCorners = new float[1400][];
   private final float[] quadBuf = new float[12];
   private final List<CustomSky.Meteor> active = new ArrayList<>();
   private final Random random = new Random();
   private boolean overworld;
   private float daylight = 1.0F;
   private float rain;

   public CustomSky() {
      super("Custom Sky", Category.FUN, "New sky: galaxy, neon, sunset … with northern lights, nebula, a ringed planet and shooting stars.");
      Random var1 = new Random(1337L);

      for (int var2 = 0; var2 < 1400; var2++) {
         double var3 = var1.nextDouble() * 1.25 - 0.25;
         double var5 = var1.nextDouble() * Math.PI * 2.0;
         double var7 = Math.sqrt(1.0 - var3 * var3);
         this.starDir[var2] = new Vector3f((float)(Math.cos(var5) * var7), (float)var3, (float)(Math.sin(var5) * var7));
         this.starSize[var2] = (float)(0.0012 + Math.pow(var1.nextDouble(), 6.0) * 0.0045);
         this.starPhase[var2] = (float)(var1.nextDouble() * Math.PI * 2.0);
         double var9 = var1.nextDouble();
         this.starColor[var2] = var9 < 0.7 ? 16777215 : (var9 < 0.85 ? 11061503 : (var9 < 0.95 ? 16769704 : 16756912));
         Vector3f var11 = this.starDir[var2];
         Vector3f var12 = tangent(var11).mul(this.starSize[var2]);
         Vector3f var13 = new Vector3f(tangent(var11)).cross(var11).mul(this.starSize[var2]);
         float[] var14 = new float[12];
         float[][] var15 = new float[][]{{-1.0F, -1.0F}, {1.0F, -1.0F}, {1.0F, 1.0F}, {-1.0F, 1.0F}};

         for (int var16 = 0; var16 < 4; var16++) {
            var14[var16 * 3] = var11.x + var12.x * var15[var16][0] + var13.x * var15[var16][1];
            var14[var16 * 3 + 1] = var11.y + var12.y * var15[var16][0] + var13.y * var15[var16][1];
            var14[var16 * 3 + 2] = var11.z + var12.z * var15[var16][0] + var13.z * var15[var16][1];
         }

         this.starCorners[var2] = var14;
      }
   }

   private CustomSky.Palette palette() {
      return switch ((CustomSky.Preset)this.preset.get()) {
         case VANILLA -> null;
         case GALAXY -> new CustomSky.Palette(-16054752, -16448754, -15069130, -16251622, -6599169);
         case SUNSET -> new CustomSky.Palette(-33185, -13952195, -19574, -12966848, -45488);
         case NEON -> new CustomSky.Palette(-54314, -15073229, -16718337, -14417850, -16711736);
         case BLOOD -> new CustomSky.Palette(-7667712, -14811136, -5103070, -14023419, -57344);
         case OCEAN -> new CustomSky.Palette(-16734506, -16770509, -8396560, -16766144, -16711728);
         case TOXIC -> new CustomSky.Palette(-9699526, -16045563, -3604629, -15582712, -4096);
         case RAINBOW -> {
            float var1 = (float)(System.currentTimeMillis() % 60000L) / 60000.0F;
            yield new CustomSky.Palette(
               Cape.hsv(var1, 0.65F, 1.0F),
               Cape.hsv(var1, 0.8F, 0.18F),
               Cape.hsv(var1 + 0.08F, 0.4F, 1.0F),
               Cape.hsv(var1 + 0.08F, 0.7F, 0.2F),
               Cape.hsv(var1 + 0.5F, 0.9F, 1.0F)
            );
         }
         case CUSTOM -> new CustomSky.Palette(
            this.skyColor.get(), Cape.shade(this.skyColor.get(), 0.12F), this.fogColor.get(), Cape.shade(this.fogColor.get(), 0.15F), 0
         );
      };
   }

   private float night() {
      return !this.atDay.get() && this.preset.get() != CustomSky.Preset.GALAXY
         ? class_3532.method_15363(1.0F - this.daylight * 1.3F, 0.0F, 1.0F) * (1.0F - this.rain * 0.8F)
         : 1.0F - this.rain * 0.6F;
   }

   public static void modify(class_12076 var0) {
      if (ModuleManager.on(CustomSky.class)) {
         CustomSky var1 = ModuleManager.of(CustomSky.class);
         var1.overworld = var0.field_64464 == class_12326.field_64386;
         if (var1.overworld) {
            int var2 = var0.field_63097;
            var1.daylight = class_3532.method_15363(Math.max(var2 >> 16 & 0xFF, Math.max(var2 >> 8 & 0xFF, var2 & 0xFF)) / 242.0F, 0.0F, 1.0F);
            var1.rain = var0.field_63093;
            if (var1.clearSky.get()) {
               var0.field_63093 = 0.0F;
               var1.rain = 0.0F;
            }

            CustomSky.Palette var3 = var1.palette();
            if (var3 != null) {
               var0.field_63097 = var1.dayCycle.get() ? Cape.lerpColor(var3.night(), var3.day(), var1.daylight) : var3.day();
               if (var3.sunset() != 0) {
                  var0.field_63095 = var0.field_63095 & 0xFF000000 | var3.sunset() & 16777215;
               }
            }

            if (var1.preset.get() == CustomSky.Preset.GALAXY || var1.atDay.get()) {
               var0.field_63094 = Math.max(var0.field_63094, 0.85F);
            }
         }
      }
   }

   public static void fog(Vector4f var0, class_5636 var1) {
      if (ModuleManager.on(CustomSky.class) && var1 == class_5636.field_27888) {
         CustomSky var2 = ModuleManager.of(CustomSky.class);
         if (var2.overworld) {
            CustomSky.Palette var3 = var2.palette();
            if (var3 != null) {
               int var4 = var2.dayCycle.get() ? Cape.lerpColor(var3.fogNight(), var3.fogDay(), var2.daylight) : var3.fogDay();
               float var5 = var2.fogTint.getFloat();
               var0.x = class_3532.method_16439(var5, var0.x, (var4 >> 16 & 0xFF) / 255.0F);
               var0.y = class_3532.method_16439(var5, var0.y, (var4 >> 8 & 0xFF) / 255.0F);
               var0.z = class_3532.method_16439(var5, var0.z, (var4 & 0xFF) / 255.0F);
            }
         }
      }
   }

   public static boolean hideSun() {
      return ModuleManager.on(CustomSky.class) && ModuleManager.of(CustomSky.class).hideSun.get();
   }

   public static boolean hideMoon() {
      return ModuleManager.on(CustomSky.class) && ModuleManager.of(CustomSky.class).hideMoon.get();
   }

   @Override
   protected void onDisable() {
      this.active.clear();
   }

   @Override
   public void onTick() {
      if (this.meteors.get() && this.overworld && this.night() > 0.2F && this.random.nextFloat() < this.meteorRate.get().intValue() / 1200.0F) {
         CustomSky.Meteor var1 = new CustomSky.Meteor();
         double var2 = this.random.nextDouble() * Math.PI * 2.0;
         double var4 = 0.35 + this.random.nextDouble() * 0.8;
         var1.start = dir(var2, var4);
         Vector3f var6 = tangent(var1.start);
         Vector3f var7 = new Vector3f(var6).cross(var1.start);
         double var8 = this.random.nextDouble() * Math.PI * 2.0;
         var1.dir = var6.mul((float)Math.cos(var8)).add(var7.mul((float)(-Math.abs(Math.sin(var8))))).normalize();
         var1.life = 18 + this.random.nextInt(18);
         var1.speed = 0.012F + this.random.nextFloat() * 0.012F;
         this.active.add(var1);
      }

      Iterator var10 = this.active.iterator();

      while (var10.hasNext()) {
         CustomSky.Meteor var11 = (CustomSky.Meteor)var10.next();
         if (++var11.age > var11.life) {
            var10.remove();
         }
      }
   }

   @Override
   public void onRender3D(Render3D var1) {
      if (this.overworld && mc.field_1687 != null) {
         float var2 = this.night() * this.brightness.getFloat();
         float var3 = Math.max(64.0F, mc.field_1773.method_32796() * 0.5F);
         float var4 = (float)(System.currentTimeMillis() % 3600000L / 1000.0);
         float var5 = (float)(mc.field_1687.method_75260() % 24000L / 24000.0 * Math.PI * 2.0);
         if (this.nebula.get() && var2 > 0.01F) {
            this.drawNebula(var1, var3, var4, var2, var5);
         }

         if ((this.stars.get() || this.preset.get() == CustomSky.Preset.GALAXY) && var2 > 0.01F) {
            this.drawStars(var1, var3, var4, var2, var5);
         }

         if (this.aurora.get() && var2 > 0.01F) {
            this.drawAurora(var1, var3, var4, var2);
         }

         if (this.planet.get()) {
            float var6 = this.preset.get() == CustomSky.Preset.GALAXY ? 1.0F : Math.min(1.0F, 0.45F + var2);
            this.drawPlanet(var1, var3, var6 * (1.0F - this.rain * 0.7F));
         }

         if (this.meteors.get()) {
            this.drawMeteors(var1, var3, var1.tickDelta());
         }
      }
   }

   private static Vector3f dir(double var0, double var2) {
      return new Vector3f((float)(Math.sin(var0) * Math.cos(var2)), (float)Math.sin(var2), (float)(-Math.cos(var0) * Math.cos(var2)));
   }

   private static Vector3f tangent(Vector3f var0) {
      Vector3f var1 = new Vector3f(var0).cross(0.0F, 1.0F, 0.0F);
      if (var1.lengthSquared() < 1.0E-4) {
         var1 = new Vector3f(var0).cross(1.0F, 0.0F, 0.0F);
      }

      return var1.normalize();
   }

   private static Vector3f rotZ(Vector3f var0, float var1) {
      float var2 = class_3532.method_15362(var1);
      float var3 = class_3532.method_15374(var1);
      return new Vector3f(var0.x * var2 - var0.y * var3, var0.x * var3 + var0.y * var2, var0.z);
   }

   private static int argb(float var0, int var1) {
      return class_3532.method_15340((int)(var0 * 255.0F), 0, 255) << 24 | var1 & 16777215;
   }

   private void drawStars(Render3D var1, float var2, float var3, float var4, float var5) {
      float var6 = class_3532.method_15362(var5);
      float var7 = class_3532.method_15374(var5);
      int var8 = Compat.lowDetail() ? 3 : 1;
      float[] var9 = this.quadBuf;

      for (int var10 = 0; var10 < 1400; var10 += var8) {
         float[] var11 = this.starCorners[var10];
         if (!(var11[1] * var6 + var11[0] * var7 < -0.12F)) {
            for (int var12 = 0; var12 < 4; var12++) {
               float var13 = var11[var12 * 3];
               float var14 = var11[var12 * 3 + 1];
               var9[var12 * 3] = (var13 * var6 - var14 * var7) * var2;
               var9[var12 * 3 + 1] = (var13 * var7 + var14 * var6) * var2;
               var9[var12 * 3 + 2] = var11[var12 * 3 + 2] * var2;
            }

            float var15 = 0.55F + 0.45F * class_3532.method_15374(var3 * (1.5F + var10 % 7 * 0.4F) + this.starPhase[var10]);
            var1.skyQuad(var9, argb(var4 * var15, this.starColor[var10]));
         }
      }
   }

   private void drawNebula(Render3D var1, float var2, float var3, float var4, float var5) {
      Vector3f var6 = new Vector3f(0.35F, 0.45F, 0.82F).normalize();
      Vector3f var7 = tangent(var6);
      Vector3f var8 = new Vector3f(var7).cross(var6).normalize();
      boolean var9 = Compat.lowDetail();
      int var10 = var9 ? 36 : 72;
      int var11 = var9 ? 4 : 7;
      float var12 = 0.2F;
      Vector3f[][] var13 = new Vector3f[var10 + 1][var11 + 1];
      int[][] var14 = new int[var10 + 1][var11 + 1];

      for (int var15 = 0; var15 <= var10; var15++) {
         double var16 = (double)var15 / var10 * Math.PI * 2.0;

         for (int var18 = 0; var18 <= var11; var18++) {
            float var19 = ((float)var18 / var11 * 2.0F - 1.0F) * var12;
            Vector3f var20 = new Vector3f(var7)
               .mul((float)Math.cos(var16))
               .add(new Vector3f(var8).mul((float)Math.sin(var16)))
               .add(new Vector3f(var6).mul(var19))
               .normalize();
            var13[var15][var18] = rotZ(var20, var5 * 0.5F).mul(var2);
            double var21 = Cape.fbm(Math.cos(var16) * 3.0 + 5.0 + var3 * 0.01, Math.sin(var16) * 3.0 + var18 * 0.9);
            float var23 = 1.0F - Math.abs(var19) / var12;
            float var24 = (float)(Math.max(0.0, var21 - 0.3) * 1.6) * var23 * var23 * var4 * 0.55F;
            int var25 = Cape.lerpColor(-10802554, -13712143, (float)Cape.fbm(Math.cos(var16) * 2.0 + 17.0, Math.sin(var16) * 2.0 + var18 * 0.5));
            if (var21 > 0.72) {
               var25 = Cape.lerpColor(var25, -34087, (float)((var21 - 0.72) * 3.0));
            }

            var14[var15][var18] = argb(var24, var25);
         }
      }

      for (int var26 = 0; var26 < var10; var26++) {
         for (int var27 = 0; var27 < var11; var27++) {
            var1.skyQuad(
               var13[var26][var27],
               var13[var26 + 1][var27],
               var13[var26 + 1][var27 + 1],
               var13[var26][var27 + 1],
               var14[var26][var27],
               var14[var26 + 1][var27],
               var14[var26 + 1][var27 + 1],
               var14[var26][var27 + 1]
            );
         }
      }
   }

   private void drawAurora(Render3D var1, float var2, float var3, float var4) {
      int var5 = Compat.lowDetail() ? 45 : 90;

      for (int var6 = 0; var6 < 3; var6++) {
         float var7 = var6 * 2.1F;
         Vector3f[] var8 = new Vector3f[var5 + 1];
         Vector3f[] var9 = new Vector3f[var5 + 1];
         Vector3f[] var10 = new Vector3f[var5 + 1];
         int[] var11 = new int[var5 + 1];
         int[] var12 = new int[var5 + 1];
         int[] var13 = new int[var5 + 1];

         for (int var14 = 0; var14 <= var5; var14++) {
            float var15 = (float)var14 / var5;
            double var16 = -1.35 + 2.7 * var15 + 0.1 * Math.sin(var3 * 0.25 + var14 * 0.12 + var7) + (var6 - 1) * 0.12;
            double var18 = 0.22 + var6 * 0.06 + 0.07 * Math.sin(var14 * 0.19 + var3 * 0.45 + var7);
            double var20 = 0.22 + 0.12 * Math.sin(var14 * 0.11 - var3 * 0.35 + var7 * 1.7);
            var8[var14] = dir(var16, var18).mul(var2);
            var9[var14] = dir(var16, var18 + var20 * 0.35).mul(var2);
            var10[var14] = dir(var16, var18 + var20).mul(var2);
            float var22 = 0.45F + 0.55F * (0.5F + 0.5F * class_3532.method_15374(var14 * 0.33F + var3 * 1.4F + var7));
            float var23 = class_3532.method_15374(var15 * (float) Math.PI);
            float var24 = var4 * var22 * var23 * (var6 == 0 ? 0.75F : 0.5F);
            int var25;
            int var26;
            switch ((CustomSky.AuroraStyle)this.auroraStyle.get()) {
               case PINK:
                  var25 = 16739288;
                  var26 = 5925887;
                  break;
               case RAINBOW:
                  var25 = Cape.hsv(var15 * 0.8F + var3 * 0.05F, 0.8F, 1.0F);
                  var26 = Cape.hsv(var15 * 0.8F + var3 * 0.05F + 0.2F, 0.8F, 1.0F);
                  break;
               default:
                  var25 = 4063116;
                  var26 = 10181631;
            }

            var11[var14] = argb(var24 * 0.35F, var25);
            var12[var14] = argb(var24 * 0.8F, var25);
            var13[var14] = argb(var24 * 0.08F, var26);
         }

         for (int var27 = 0; var27 < var5; var27++) {
            var1.skyQuad(var8[var27], var8[var27 + 1], var9[var27 + 1], var9[var27], var11[var27], var11[var27 + 1], var12[var27 + 1], var12[var27]);
            var1.skyQuad(var9[var27], var9[var27 + 1], var10[var27 + 1], var10[var27], var12[var27], var12[var27 + 1], var13[var27 + 1], var13[var27]);
         }
      }
   }

   private void drawPlanet(Render3D var1, float var2, float var3) {
      Vector3f var4 = dir(2.3, 0.42);
      Vector3f var5 = tangent(var4);
      Vector3f var6 = new Vector3f(var5).cross(var4).normalize();
      float var7 = 0.085F;
      Vector3f var8 = new Vector3f(-0.55F, 0.35F, 0.76F).normalize();
      float var9 = 0.38F;
      this.drawRing(var1, var2 * 0.985F, var4, var5, var6, var7, var9, var3, true);
      var2 *= 0.975F;
      byte var11 = 10;
      byte var12 = 40;

      for (int var13 = 0; var13 < var11; var13++) {
         for (int var14 = 0; var14 < var12; var14++) {
            float[] var15 = new float[]{(float)var13 / var11, (float)(var13 + 1) / var11};
            float[] var16 = new float[]{(float)var14 / var12 * (float) (Math.PI * 2), (float)(var14 + 1) / var12 * (float) (Math.PI * 2)};
            Vector3f[] var17 = new Vector3f[4];
            int[] var18 = new int[4];
            int[][] var19 = new int[][]{{0, 0}, {0, 1}, {1, 1}, {1, 0}};

            for (int var20 = 0; var20 < 4; var20++) {
               float var21 = var15[var19[var20][0]];
               float var22 = var16[var19[var20][1]];
               float var23 = class_3532.method_15362(var22) * var21;
               float var24 = class_3532.method_15374(var22) * var21;
               var17[var20] = new Vector3f(var4).add(new Vector3f(var5).mul(var23 * var7)).add(new Vector3f(var6).mul(var24 * var7)).normalize().mul(var2);
               float var25 = class_3532.method_15355(Math.max(0.0F, 1.0F - var21 * var21));
               float var26 = Math.max(0.07F, var23 * var8.x + var24 * var8.y + var25 * var8.z);
               float var27 = var23 * class_3532.method_15374(var9) + var24 * class_3532.method_15362(var9);
               int var28 = Cape.lerpColor(-1527696, -4889286, 0.5F + 0.5F * class_3532.method_15374(var27 * 14.0F));
               var18[var20] = argb(var3, Cape.shade(var28, var26 * 1.15F));
            }

            var1.skyQuad(var17[0], var17[1], var17[2], var17[3], var18[0], var18[1], var18[2], var18[3]);
         }
      }

      this.drawRing(var1, var2 * 0.965F, var4, var5, var6, var7, var9, var3, false);
   }

   private void drawRing(Render3D var1, float var2, Vector3f var3, Vector3f var4, Vector3f var5, float var6, float var7, float var8, boolean var9) {
      byte var10 = 60;
      float[] var11 = new float[]{1.35F, 1.7F, 1.78F, 2.25F};

      for (int var12 = 0; var12 < var11.length - 1; var12++) {
         if (var12 != 1) {
            for (int var13 = 0; var13 < var10; var13++) {
               float var14 = (float)var13 / var10 * (float) (Math.PI * 2);
               float var15 = (float)(var13 + 1) / var10 * (float) (Math.PI * 2);
               boolean var16 = class_3532.method_15374((var14 + var15) / 2.0F) > 0.0F;
               if (var16 == var9) {
                  Vector3f[] var17 = new Vector3f[4];
                  float[][] var18 = new float[][]{{var14, var11[var12]}, {var15, var11[var12]}, {var15, var11[var12 + 1]}, {var14, var11[var12 + 1]}};

                  for (int var19 = 0; var19 < 4; var19++) {
                     float var20 = class_3532.method_15362(var18[var19][0]) * var18[var19][1];
                     float var21 = class_3532.method_15374(var18[var19][0]) * var18[var19][1] * 0.26F;
                     float var22 = var20 * class_3532.method_15362(var7) - var21 * class_3532.method_15374(var7);
                     float var23 = var20 * class_3532.method_15374(var7) + var21 * class_3532.method_15362(var7);
                     var17[var19] = new Vector3f(var3)
                        .add(new Vector3f(var4).mul(var22 * var6))
                        .add(new Vector3f(var5).mul(var23 * var6))
                        .normalize()
                        .mul(var2);
                  }

                  int var24 = argb(var8 * (var12 == 0 ? 0.55F : 0.75F), var12 == 0 ? 13215612 : 15127216);
                  var1.skyQuad(var17[0], var17[1], var17[2], var17[3], var24, var24, var24, var24);
               }
            }
         }
      }
   }

   private void drawMeteors(Render3D var1, float var2, float var3) {
      for (CustomSky.Meteor var5 : this.active) {
         float var6 = var5.age + var3;
         float var7 = var6 * var5.speed;
         float var8 = Math.max(0.0F, var7 - 0.14F);
         float var9 = class_3532.method_15363(Math.min(var6 / 4.0F, (var5.life - var6) / 6.0F), 0.0F, 1.0F);
         Vector3f var10 = new Vector3f(var5.start).add(new Vector3f(var5.dir).mul(var7)).normalize();
         Vector3f var11 = new Vector3f(var5.start).add(new Vector3f(var5.dir).mul(var8)).normalize();
         Vector3f var12 = new Vector3f(var10).cross(var5.dir).normalize().mul(var2 * 0.0022F);
         Vector3f var13 = new Vector3f(var10).mul(var2);
         Vector3f var14 = new Vector3f(var11).mul(var2);
         Vector3f var15 = new Vector3f(var12).mul(0.2F);
         var1.skyQuad(
            new Vector3f(var14).sub(var15),
            new Vector3f(var14).add(var15),
            new Vector3f(var13).add(var12),
            new Vector3f(var13).sub(var12),
            argb(0.0F, 16777215),
            argb(0.0F, 16777215),
            argb(var9, 16777215),
            argb(var9, 16777215)
         );
      }
   }

   @Override
   public String getInfo() {
      return this.preset.displayValue();
   }

   public static enum AuroraStyle {
      GREEN,
      PINK,
      RAINBOW;
   }

   private static final class Meteor {
      Vector3f start;
      Vector3f dir;
      float age;
      float life;
      float speed;
   }

   private record Palette(int day, int night, int fogDay, int fogNight, int sunset) {
   }

   public static enum Preset {
      VANILLA,
      GALAXY,
      SUNSET,
      NEON,
      BLOOD,
      OCEAN,
      TOXIC,
      RAINBOW,
      CUSTOM;
   }
}
