package dev.dihclient.modules.fun;

import dev.dihclient.DIHClient;
import dev.dihclient.module.Category;
import dev.dihclient.module.Module;
import dev.dihclient.module.ModuleManager;
import dev.dihclient.setting.BoolSetting;
import dev.dihclient.setting.ColorSetting;
import dev.dihclient.setting.DoubleSetting;
import dev.dihclient.setting.EnumSetting;
import dev.dihclient.setting.StringSetting;
import dev.dihclient.util.Compat;
import dev.dihclient.util.ImageLoader;
import dev.dihclient.util.Notifications;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Stream;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.class_10055;
import net.minecraft.class_1011;
import net.minecraft.class_1043;
import net.minecraft.class_11890;
import net.minecraft.class_156;
import net.minecraft.class_1657;
import net.minecraft.class_2960;
import net.minecraft.class_3532;
import net.minecraft.class_8685;
import net.minecraft.class_12079.class_10726;
import net.minecraft.class_12079.class_12081;

public class Cape extends Module {
   private static final class_2960 TEXTURE_ID = class_2960.method_60655("dihclient", "cape/current");
   private static final ExecutorService PAINTER = Executors.newSingleThreadExecutor(var0 -> {
      Thread var1 = new Thread(var0, "DIHClient-CapePainter");
      var1.setDaemon(true);
      var1.setPriority(1);
      return var1;
   });
   public final EnumSetting<Cape.Design> design = this.mode("Design", "What the cape looks like.", Cape.Design.DIH).onChange(this::rebuild);
   public final ColorSetting color1 = this.color("Color 1", "First colour (Gradient / DIH).", -7722014).onChange(this::rebuild);
   public final ColorSetting color2 = this.color("Color 2", "Second colour (Gradient / DIH).", -15597544).onChange(this::rebuild);
   public final StringSetting image = this.text(
         "Image", "Picture in .minecraft/dihclient/capes (empty = first file). PNG / GIF / JPG, 64x32, 128x64, 512x256 …", "", 200
      )
      .onChange(this::rebuild);
   public final BoolSetting self = this.bool("Self", "Cape on you (third person, F5).", true);
   public final BoolSetting friends = this.bool("Friends", "Friends get the cape too.", false);
   public final BoolSetting everyone = this.bool("Everyone", "Every player gets the cape.", false);
   public final BoolSetting overrideReal = this.bool("Override Real Capes", "Replace capes players really own (Minecon, Migrator …).", true);
   public final BoolSetting elytra = this.bool("Elytra", "Elytras use the cape design too.", true);
   public final DoubleSetting wind = this.dbl("Wind", "Extra flutter. 0 = only normal cape physics.", 0.6, 0.0, 2.0, 0.05);
   public final DoubleSetting flow = this.dbl("Flow", "How strongly the cape reacts to your movement.", 1.0, 0.0, 2.5, 0.05);
   private class_1043 texture;
   private class_1011 sheet;
   private int frames = 1;
   private int frameH;
   private boolean ready;
   private boolean animated;
   private int animTick;
   private int S = 8;
   private volatile int[] pending;
   private volatile boolean painting;
   private String loadedFile;
   private static final int[][] LETTERS = new int[][]{{6, 5, 5, 5, 6}, {7, 2, 2, 2, 7}, {5, 5, 7, 5, 5}};
   private static final String[] CREEPER_FACE = new String[]{"........", "........", ".XX..XX.", ".XX..XX.", "...XX...", "..XXXX..", "..XXXX..", "..X..X.."};

   public Cape() {
      super("Cape", Category.FUN, "Gives you a cape with physics: 19 HD designs (galaxy, aurora, black hole, lightning, cherry …) or your own PNG / GIF.");
      this.action("Next Design", "Switches to the next design.", () -> {
         Cape.Design[] var1 = Cape.Design.values();
         this.design.set(var1[(this.design.get().ordinal() + 1) % var1.length]);
      });
      this.action("Choose File", "Pick any PNG / GIF / JPG as cape (layout 64x32 or bigger in 2:1). Copied into the capes folder.", this::chooseFile);
      this.action("Open Capes Folder", "Opens .minecraft/dihclient/capes.", () -> class_156.method_668().method_672(capeDir().toFile()));
      this.action("Reload", "Reloads the image after you changed it.", this::rebuild);
   }

   public static Path capeDir() {
      Path var0 = FabricLoader.getInstance().getGameDir().resolve("dihclient").resolve("capes");

      try {
         Files.createDirectories(var0);
      } catch (Exception var2) {
      }

      return var0;
   }

   public static void apply(class_11890 var0, class_10055 var1) {
      if (ModuleManager.on(Cape.class)) {
         Cape var2 = ModuleManager.of(Cape.class);
         if (var2.ready && var2.wants(var0) && var1.field_53520 != null) {
            class_8685 var3 = var1.field_53520;
            if (var3.comp_1627() == null || var2.overrideReal.get()) {
               class_10726 var4 = new class_10726(TEXTURE_ID, TEXTURE_ID);
               var1.field_53520 = new class_8685(
                  var3.comp_1626(), var4, (class_12081)(var2.elytra.get() ? var4 : var3.comp_1628()), var3.comp_1629(), var3.comp_1630()
               );
               var1.field_53532 = !var1.field_53333;
               float var5 = var2.flow.getFloat();
               var1.field_53536 *= var5;
               var1.field_53537 *= var5;
               var1.field_53538 *= var5;
               double var6 = var2.wind.get();
               if (var6 > 0.0) {
                  float var8 = var1.field_53328 + var0.method_5628() * 7;
                  var1.field_53536 = var1.field_53536 + (float)(var6 * (6.0 + 4.0 * Math.sin(var8 * 0.21) + 2.0 * Math.sin(var8 * 0.53)));
                  var1.field_53538 = var1.field_53538 + (float)(var6 * 3.0 * Math.sin(var8 * 0.17));
               }
            }
         }
      }
   }

   private boolean wants(class_11890 var1) {
      if (var1 == mc.field_1724) {
         return this.self.get();
      } else {
         return this.everyone.get() ? true : this.friends.get() && var1 instanceof class_1657 var2 && DIHClient.social().isFriend(var2);
      }
   }

   @Override
   protected void onEnable() {
      this.rebuild();
   }

   @Override
   protected void onDisable() {
      this.ready = false;
   }

   @Override
   public void onTick() {
      if (!this.ready && mc.field_1724.field_6012 % 40 == 0) {
         this.rebuild();
      }

      int var1 = Compat.lowDetail() ? 4 : 2;
      if (this.ready && this.animated && ++this.animTick % var1 == 0) {
         if (this.design.get() == Cape.Design.CUSTOM) {
            if (this.sheet != null && this.frames > 1) {
               int var2 = this.animTick / 2 % this.frames;
               this.sheet
                  .method_47594(this.texture.method_4525(), 0, var2 * this.frameH, 0, -var2 * this.frameH, this.sheet.method_4307(), this.frameH, false, false);
               this.texture.method_4524();
            }
         } else {
            int[] var7 = this.pending;
            if (var7 != null) {
               this.pending = null;
               class_1011 var3 = this.texture.method_4525();
               if (var3 != null && var7.length == var3.method_4307() * var3.method_4323()) {
                  copy(var7, var3);
                  this.texture.method_4524();
               }
            }

            if (!this.painting) {
               this.painting = true;
               int var8 = 64 * this.S;
               int var4 = 32 * this.S;
               double var5 = time();
               PAINTER.execute(() -> {
                  try {
                     int[] var5x = new int[var8 * var4];
                     this.paint(var5x, var8, var5);
                     this.pending = var5x;
                  } catch (Throwable var9) {
                     DIHClient.LOG.warn("[DIHClient] cape paint failed", var9);
                  } finally {
                     this.painting = false;
                  }
               });
            }
         }
      }
   }

   private static double time() {
      return System.currentTimeMillis() % 3600000L / 1000.0;
   }

   private void rebuild() {
      this.ready = false;
      if (this.isEnabled() && mc.method_1531() != null) {
         try {
            if (this.sheet != null) {
               this.sheet.close();
               this.sheet = null;
            }

            this.frames = 1;
            class_1011 var1;
            if (this.design.get() == Cape.Design.CUSTOM) {
               var1 = this.loadCustom();
               if (var1 == null) {
                  return;
               }

               this.animated = this.frames > 1;
            } else {
               this.S = Compat.lowDetail() ? 4 : 8;
               this.pending = null;
               var1 = new class_1011(64 * this.S, 32 * this.S, true);
               int[] var2 = new int[64 * this.S * 32 * this.S];
               this.paint(var2, 64 * this.S, time());
               copy(var2, var1);
               Cape.Design var3 = this.design.get();
               this.animated = var3 != Cape.Design.GRADIENT
                  && var3 != Cape.Design.GERMANY
                  && var3 != Cape.Design.UK
                  && var3 != Cape.Design.CHECKER
                  && var3 != Cape.Design.CREEPER;
               this.loadedFile = null;
            }

            this.texture = new class_1043(() -> "dihclient cape", var1);
            mc.method_1531().method_4616(TEXTURE_ID, this.texture);
            this.ready = true;
         } catch (Exception var4) {
            DIHClient.LOG.error("[DIHClient] cape texture failed", var4);
            Notifications.error("Cape", "Could not create the cape texture");
         }
      }
   }

   private class_1011 loadCustom() throws Exception {
      ArrayList var1 = new ArrayList();

      try (Stream var2 = Files.list(capeDir())) {
         var2.filter(var0 -> Files.isRegularFile(var0) && ImageLoader.supported(var0)).sorted().forEach(var1::add);
      }

      Path var11 = null;

      for (Path var4 : var1) {
         if (var4.getFileName().toString().equalsIgnoreCase(this.image.get().trim())) {
            var11 = var4;
         }
      }

      if (var11 == null && !var1.isEmpty()) {
         var11 = (Path)var1.get(0);
      }

      if (var11 == null) {
         Notifications.warn("Cape", "No cape picture yet – use Choose File or put one into .minecraft/dihclient/capes");
         return null;
      } else {
         ImageLoader.Loaded var12 = ImageLoader.load(var11, 1024);
         class_1011 var13 = var12.image();
         int var5 = var13.method_4307();
         int var6 = var13.method_4323();
         this.loadedFile = var11.getFileName().toString();
         int var7 = var12.frames() > 1 ? var6 / var12.frames() : var5 / 2;
         if (var7 > 0 && var6 > var7 && var6 % var7 == 0) {
            this.sheet = var13;
            this.frames = var6 / var7;
            this.frameH = var7;
            class_1011 var8 = new class_1011(var5, var7, true);
            this.sheet.method_47594(var8, 0, 0, 0, 0, var5, var7, false, false);
            Notifications.info("Cape", "Loaded " + this.loadedFile + " (" + this.frames + " frames)");
            return var8;
         } else {
            Notifications.info("Cape", "Loaded " + this.loadedFile);
            return var13;
         }
      }
   }

   private void chooseFile() {
      ImageLoader.pickFile("Choose a cape picture (2:1, e.g. 64x32)").whenComplete((var1, var2) -> mc.execute(() -> {
         if (var2 != null) {
            Notifications.error("Cape", "File picker not available – copy the file into the capes folder instead.");
         } else if (var1 != null) {
            if (!ImageLoader.supported(var1)) {
               Notifications.warn("Cape", "Please choose a PNG, GIF or JPG");
            } else {
               try {
                  Path var3 = capeDir().resolve(var1.getFileName().toString());
                  if (!var1.toAbsolutePath().normalize().equals(var3.toAbsolutePath().normalize())) {
                     Files.copy(var1, var3, StandardCopyOption.REPLACE_EXISTING);
                  }

                  this.image.set(var3.getFileName().toString());
                  if (this.design.get() != Cape.Design.CUSTOM) {
                     this.design.set(Cape.Design.CUSTOM);
                  } else {
                     this.rebuild();
                  }
               } catch (Exception var4) {
                  Notifications.error("Cape", "Could not copy " + var1.getFileName());
               }
            }
         }
      }));
   }

   private static void copy(int[] var0, class_1011 var1) {
      int var2 = var1.method_4307();
      int var3 = var1.method_4323();

      for (int var4 = 0; var4 < var3; var4++) {
         int var5 = var4 * var2;

         for (int var6 = 0; var6 < var2; var6++) {
            int var7 = var0[var5 + var6];
            if (var7 != 0) {
               var1.method_61941(var6, var4, var7);
            }
         }
      }
   }

   private void paint(int[] var1, int var2, double var3) {
      int var5 = shade(this.pixel(0.03, 0.5, var3, 0, 0), 0.7F);
      if (this.design.get() == Cape.Design.GERMANY) {
         var5 = -15066598;
      }

      if (this.design.get() == Cape.Design.UK) {
         var5 = -16703127;
      }

      fill(var1, var2, this.S, 0, 20 * this.S, this.S, var5);
      fill(var1, var2, 0, this.S, this.S, 16 * this.S, var5);
      fill(var1, var2, 11 * this.S, this.S, this.S, 16 * this.S, var5);
      this.region(var1, var2, this.S, this.S, 10 * this.S, 16 * this.S, var3, false);
      this.region(var1, var2, 12 * this.S, this.S, 10 * this.S, 16 * this.S, var3, true);
      this.region(var1, var2, 22 * this.S, 0, 24 * this.S, 22 * this.S, var3, false);
   }

   private static void fill(int[] var0, int var1, int var2, int var3, int var4, int var5, int var6) {
      for (int var7 = var3; var7 < var3 + var5; var7++) {
         for (int var8 = var2; var8 < var2 + var4; var8++) {
            var0[var7 * var1 + var8] = var6;
         }
      }
   }

   private void region(int[] var1, int var2, int var3, int var4, int var5, int var6, double var7, boolean var9) {
      for (int var10 = 0; var10 < var6; var10++) {
         double var11 = (var10 + 0.5) / var6;

         for (int var13 = 0; var13 < var5; var13++) {
            double var14 = (var13 + 0.5) / var5;
            if (var9) {
               var14 = 1.0 - var14;
            }

            var1[(var4 + var10) * var2 + var3 + var13] = this.pixel(var14, var11, var7, (int)(var14 * var5 * 8.0 / this.S), var10 * 8 / this.S) | 0xFF000000;
         }
      }
   }

   private int pixel(double var1, double var3, double var5, int var7, int var8) {
      return switch ((Cape.Design)this.design.get()) {
         case DIH, CUSTOM -> this.dih(var1, var3, var5);
         case GRADIENT -> lerpColor(this.color1.get(), this.color2.get(), (float)var3);
         case RAINBOW -> hsv((float)(var3 * 0.8 + var1 * 0.15 - var5 * 0.25), 0.85F, 1.0F);
         case GALAXY -> galaxy(var1, var3, var5, var7, var8);
         case FIRE -> fire(var1, var3, var5);
         case MATRIX -> matrix(var1, var3, var5);
         case ENDER -> ender(var1, var3, var5);
         case OCEAN -> ocean(var1, var3, var5);
         case AURORA -> aurora(var1, var3, var5, var7, var8);
         case LIGHTNING -> lightning(var1, var3, var5);
         case CHERRY -> cherry(var1, var3, var5);
         case LAVA -> lava(var1, var3, var5);
         case VOID -> voidHole(var1, var3, var5, var7, var8);
         case NEON -> neon(var1, var3, var5);
         case CREEPER -> creeper(var1, var3);
         case CHECKER -> ((int)(var1 * 5.0) + (int)(var3 * 8.0)) % 2 == 0 ? this.color1.get() : this.color2.get();
         case STRIPES -> ((var1 * 10.0 + var3 * 16.0 - var5 * 3.0) % 4.0 + 4.0) % 4.0 < 2.0 ? this.color1.get() : this.color2.get();
         case GERMANY -> var3 < 0.3333333333333333 ? -15658735 : (var3 < 0.6666666666666666 ? -2293760 : -13312);
         case UK -> uk(var1, var3);
      };
   }

   private int dih(double var1, double var3, double var5) {
      int var7 = lerpColor(shade(this.color2.get(), 1.4F), this.color2.get(), (float)var3);
      double var8 = Math.min(Math.min(var1, 1.0 - var1) * 10.0, Math.min(var3, 1.0 - var3) * 16.0);
      float var10 = (float)(0.75 + 0.25 * Math.sin(var5 * 3.0));
      int var11 = shade(this.color1.get(), var10);
      if (var8 < 0.45) {
         return var11;
      } else {
         if (var8 < 1.1) {
            var7 = lerpColor(var11, var7, (float)((var8 - 0.45) / 0.65));
         }

         double var12 = var1 * 7.0 - 2.0;
         double var14 = var3 * 21.0 - 2.0;
         if (var12 >= 0.0 && var12 < 3.0 && var14 >= 0.0 && var14 < 17.0) {
            int var16 = (int)var14 / 6;
            int var17 = (int)var14 % 6;
            if (var17 < 5 && var16 < 3 && (LETTERS[var16][var17] >> 2 - (int)var12 & 1) == 1) {
               float var18 = (float)(0.8 + 0.2 * Math.sin(var5 * 4.0 - var3 * 8.0));
               return shade(lerpColor(this.color1.get(), -1, 0.35F), var18);
            }
         }

         return var7;
      }
   }

   private static int galaxy(double var0, double var2, double var4, int var6, int var7) {
      double var8 = fbm(var0 * 3.0 + var4 * 0.02, var2 * 4.5);
      double var10 = fbm(var0 * 5.0 + 13.0, var2 * 6.0 - var4 * 0.015);
      int var12 = lerpColor(-16448753, -15004870, (float)var2);
      int var13 = lerpColor(-9823334, -16730173, (float)var10);
      int var14 = lerpColor(var12, var13, (float)class_3532.method_15350((var8 - 0.45) * 2.2, 0.0, 0.85));
      long var15 = hash(var6 / 2, var7 / 2);
      if ((var15 & 127L) == 0L) {
         float var17 = (float)(0.5 + 0.5 * Math.sin(var4 * (2L + (var15 >> 8 & 7L)) + (var15 >> 12 & 63L)));
         var14 = lerpColor(var14, -1, var17);
      }

      return var14;
   }

   private static int fire(double var0, double var2, double var4) {
      double var6 = fbm(var0 * 4.0, var2 * 5.0 + var4 * 1.6);
      double var8 = class_3532.method_15350(var2 * 1.35 - 0.35 + (var6 - 0.5) * 0.9, 0.0, 1.0);
      if (var8 < 0.25) {
         return lerpColor(-15597568, -10876416, (float)(var8 / 0.25));
      } else if (var8 < 0.55) {
         return lerpColor(-10876416, -2082304, (float)((var8 - 0.25) / 0.3));
      } else {
         return var8 < 0.8 ? lerpColor(-2082304, -24576, (float)((var8 - 0.55) / 0.25)) : lerpColor(-24576, -3152, (float)((var8 - 0.8) / 0.2));
      }
   }

   private static int matrix(double var0, double var2, double var4) {
      int var6 = (int)(var0 * 10.0);
      long var7 = hash(var6, 99L);
      double var9 = 0.25 + (var7 & 15L) / 40.0;
      double var11 = (var4 * var9 + (var7 >> 4 & 255L) / 255.0) % 1.4 - 0.2;
      double var13 = var11 - var2;
      int var15 = (int)(var2 * 32.0);
      boolean var16 = (hash(var6 * 31 + var15, (long)(var4 * 4.0) + var15) & 3L) != 0L && var0 * 10.0 % 1.0 > 0.18 && var0 * 10.0 % 1.0 < 0.82;
      if (var13 < 0.0 || var13 > 0.6 || !var16) {
         return -16644094;
      } else {
         return var13 < 0.03 ? -2555944 : lerpColor(-14614720, -16639482, (float)(var13 / 0.6));
      }
   }

   private static int ender(double var0, double var2, double var4) {
      int var6 = lerpColor(-15070678, -16317939, (float)var2);
      double var7 = (var0 - 0.5) * 10.0;
      double var9 = (var2 - 0.33) * 16.0;
      double var11 = Math.hypot(var7, var9 * 0.9);
      if (var11 < 2.6) {
         if (Math.abs(var7) < 0.45 && Math.abs(var9) < 1.5) {
            return -16115180;
         } else {
            float var21 = (float)(0.8 + 0.2 * Math.sin(var4 * 2.0));
            return var11 < 1.9 ? shade(-12590936, var21) : -15309234;
         }
      } else {
         int var13 = (int)(var0 * 18.0);
         int var14 = (int)Math.floor(var2 * 30.0 + var4 * 2.2);
         long var15 = hash(var13, var14);
         if ((var15 & 15L) == 0L) {
            double var17 = var0 * 18.0 - var13 - 0.5;
            double var19 = var2 * 30.0 + var4 * 2.2 - Math.floor(var2 * 30.0 + var4 * 2.2) - 0.5;
            if (var17 * var17 + var19 * var19 < 0.12) {
               return (var15 & 16L) == 0L ? -2068225 : -5226241;
            }
         }

         return lerpColor(var6, -12971936, (float)Math.max(0.0, fbm(var0 * 3.0, var2 * 4.0 - var4 * 0.2) - 0.55) * 2.5F);
      }
   }

   private static int ocean(double var0, double var2, double var4) {
      int var6 = lerpColor(-14699050, -16506550, (float)var2);
      double var7 = Math.sin(var0 * 14.0 + var4 * 2.0 + Math.sin(var2 * 9.0 + var4 * 0.8) * 1.5);
      double var9 = fbm(var0 * 4.0 + var4 * 0.25, var2 * 6.0 - var4 * 0.15);
      int var11 = lerpColor(var6, -16101494, (float)(0.25 + 0.25 * var7));
      if (var9 > 0.6) {
         var11 = lerpColor(var11, -4196609, (float)((var9 - 0.6) * 2.2 * (1.0 - var2 * 0.7)));
      }

      if (var2 < 0.07 + 0.03 * Math.sin(var0 * 20.0 + var4 * 3.0)) {
         var11 = -1508097;
      }

      return var11;
   }

   private static int aurora(double var0, double var2, double var4, int var6, int var7) {
      int var8 = lerpColor(-16644590, -16049618, (float)var2);
      if ((hash(var6 / 2, var7 / 2) & 255L) == 0L) {
         var8 = -2561793;
      }

      for (int var9 = 0; var9 < 2; var9++) {
         double var10 = 0.28 + var9 * 0.25 + 0.1 * Math.sin(var0 * 4.0 + var4 * 0.9 + var9 * 2);
         double var12 = (var2 - var10) * 4.2;
         double var14 = 0.65 + 0.35 * Math.sin(var0 * 16.0 + var4 * 2.4 + var9) * Math.sin(var0 * 7.0 - var4 * 1.3);
         double var16 = Math.exp(-var12 * var12 * (var12 > 0.0 ? 3 : 1)) * var14;
         int var18 = lerpColor(-12714100, -6267905, (float)class_3532.method_15350(0.5 - var12 * 0.35, 0.0, 1.0));
         var8 = lerpColor(var8, var18, (float)Math.min(1.0, var16 * 1.1));
      }

      return var8;
   }

   private static int lightning(double var0, double var2, double var4) {
      double var6 = var4 % 2.6;
      int var8 = (int)(var4 / 2.6);
      double var9 = fbm(var0 * 3.0 + var4 * 0.1, var2 * 4.0);
      int var11 = lerpColor(-15987178, -13749180, (float)var9);
      if (var6 < 0.35) {
         double var12 = 0.5 + (hash(var8, 1L) % 100L - 50L) / 250.0;

         for (int var14 = 0; var14 < 6; var14++) {
            var12 += Math.sin(var2 * (5 + var14 * 3) + var8 * 7 + var14) * 0.05 / (var14 + 1);
         }

         double var17 = Math.abs(var0 - var12) * 10.0;
         float var16 = (float)(1.0 - var6 / 0.35);
         var11 = lerpColor(var11, -10852720, var16 * 0.5F);
         if (var17 < 0.35) {
            return lerpColor(var11, -1, var16);
         }

         if (var17 < 1.2) {
            var11 = lerpColor(var11, -6441217, (float)((1.2 - var17) / 1.2 * var16));
         }
      }

      return var11;
   }

   private static int cherry(double var0, double var2, double var4) {
      int var6 = lerpColor(-7956, -1012298, (float)var2);
      double var7 = var2 * 9.0 - var4 * 0.9;
      int var9 = (int)Math.floor(var0 * 6.0 + Math.sin(var7 * 0.8) * 0.3);
      int var10 = (int)Math.floor(var7);
      long var11 = hash(var9, var10);
      if ((var11 & 3L) != 0L) {
         double var13 = var9 + 0.2 + (var11 >> 4 & 15L) / 25.0;
         double var15 = var10 + 0.2 + (var11 >> 8 & 15L) / 25.0;
         double var17 = var0 * 6.0 + Math.sin(var7 * 0.8) * 0.3 - var13;
         double var19 = (var7 - var15) * 1.4;
         double var21 = (var11 >> 12 & 7L) * 0.4 + var4;
         double var23 = var17 * Math.cos(var21) - var19 * Math.sin(var21);
         double var25 = var17 * Math.sin(var21) + var19 * Math.cos(var21);
         if (var23 * var23 / 0.09 + var25 * var25 / 0.03 < 1.0) {
            return (var11 & 64L) == 0L ? -36952 : -1;
         }
      }

      return var6;
   }

   private static int lava(double var0, double var2, double var4) {
      double var6 = fbm(var0 * 3.0 + Math.sin(var4 * 0.3) * 0.2, var2 * 4.5 + var4 * 0.25);
      double var8 = Math.abs(fbm(var0 * 6.0 - 3.0, var2 * 8.0 + var4 * 0.1) - 0.5);
      if (var6 > 0.58) {
         return lerpColor(-34304, -8080, (float)((var6 - 0.58) * 3.0));
      } else {
         return var8 < 0.035 ? lerpColor(-46592, -12973568, (float)(var8 / 0.035)) : lerpColor(-14939128, -12969456, (float)var6);
      }
   }

   private static int voidHole(double var0, double var2, double var4, int var6, int var7) {
      double var8 = (var0 - 0.5) * 10.0;
      double var10 = (var2 - 0.45) * 16.0;
      double var12 = Math.hypot(var8, var10);
      double var14 = Math.atan2(var10, var8);
      int var16 = (hash(var6 / 2, var7 / 2) & 127L) == 0L ? -3155713 : -16580088;
      if (var12 < 1.3) {
         return -16777216;
      } else {
         if (var12 < 4.2) {
            double var17 = 0.5 + 0.5 * Math.sin(var14 * 3.0 + var12 * 2.2 - var4 * 2.5);
            float var19 = (float)(1.0 - Math.abs(var12 - 2.4) / 1.8) * (float)(0.4 + 0.6 * var17);
            var16 = lerpColor(var16, lerpColor(-38374, -3664, (float)var17), class_3532.method_15363(var19, 0.0F, 1.0F));
         }

         if (var12 >= 1.3 && var12 < 1.55) {
            var16 = -5952;
         }

         return var16;
      }
   }

   private static int neon(double var0, double var2, double var4) {
      int var6 = hsv((float)(var4 * 0.12 + var2 * 0.3), 1.0F, 1.0F);
      double var7 = Math.min(Math.min(var0, 1.0 - var0) * 10.0, Math.min(var2, 1.0 - var2) * 16.0);
      double var9 = Math.abs(((var2 * 16.0 + var4) % 4.0 + 4.0) % 4.0 - 2.0) / 2.0;
      double var11 = Math.abs(var0 - (0.25 + var9 * 0.5)) * 10.0;
      double var13 = Math.min(Math.abs(var7 - 0.9), var11);
      if (var13 < 0.25) {
         return lerpColor(-1, var6, (float)(var13 / 0.25));
      } else {
         return var13 < 1.1 ? lerpColor(var6, -16448248, (float)((var13 - 0.25) / 0.85)) : -16448248;
      }
   }

   private static int creeper(double var0, double var2) {
      int var4 = (int)(var0 * 10.0);
      int var5 = (int)(var2 * 16.0);
      int var6 = var4 - 1;
      int var7 = var5 - 2;
      if (var6 >= 0 && var6 < 8 && var7 >= 0 && var7 < 8 && CREEPER_FACE[var7].charAt(var6) == 'X') {
         return -15787505;
      } else {
         long var8 = hash(var4, var5 + 1000);
         int[] var10 = new int[]{-10634397, -11753390, -9251720, -12611771, -7939190};
         return var10[(int)(var8 % var10.length)];
      }
   }

   private static int uk(double var0, double var2) {
      double var4 = (var0 - 0.5) * 10.0;
      double var6 = (var2 - 0.5) * 16.0;
      double var8 = 5.0;
      double var10 = 8.0;
      double var12 = Math.hypot(var8, var10);
      double var14 = Math.abs(var4 * var10 - var6 * var8) / var12;
      double var16 = Math.abs(var4 * var10 + var6 * var8) / var12;
      int var18 = -16703127;
      if (Math.min(var14, var16) < 1.3) {
         var18 = -1;
      }

      boolean var19 = var4 * var6 > 0.0;
      double var20 = var4 * var10 - var6 * var8;
      double var22 = var4 * var10 + var6 * var8;
      if (Math.abs(var20) / var12 < 0.55 && var20 > 0.0 == var19) {
         var18 = -3665874;
      }

      if (Math.abs(var22) / var12 < 0.55 && var22 < 0.0 == var19) {
         var18 = -3665874;
      }

      if (Math.abs(var4) < 1.6 || Math.abs(var6) < 1.6) {
         var18 = -1;
      }

      if (Math.abs(var4) < 0.95 || Math.abs(var6) < 0.95) {
         var18 = -3665874;
      }

      return var18;
   }

   static int hsv(float var0, float var1, float var2) {
      return class_3532.method_60599((var0 % 1.0F + 1.0F) % 1.0F, var1, var2, 255);
   }

   static int lerpColor(int var0, int var1, float var2) {
      var2 = class_3532.method_15363(var2, 0.0F, 1.0F);
      int var3 = class_3532.method_48781(var2, var0 >> 16 & 0xFF, var1 >> 16 & 0xFF);
      int var4 = class_3532.method_48781(var2, var0 >> 8 & 0xFF, var1 >> 8 & 0xFF);
      int var5 = class_3532.method_48781(var2, var0 & 0xFF, var1 & 0xFF);
      return 0xFF000000 | var3 << 16 | var4 << 8 | var5;
   }

   static int shade(int var0, float var1) {
      int var2 = Math.min(255, (int)((var0 >> 16 & 0xFF) * var1));
      int var3 = Math.min(255, (int)((var0 >> 8 & 0xFF) * var1));
      int var4 = Math.min(255, (int)((var0 & 0xFF) * var1));
      return 0xFF000000 | var2 << 16 | var3 << 8 | var4;
   }

   static long hash(long var0, long var2) {
      long var4 = var0 * -7046029254386353131L ^ var2 * -4417276706812531889L;
      var4 ^= var4 >>> 29;
      var4 *= -4658895280553007687L;
      return (var4 ^ var4 >>> 32) & Long.MAX_VALUE;
   }

   private static double noise(double var0, double var2) {
      long var4 = (long)Math.floor(var0);
      long var6 = (long)Math.floor(var2);
      double var8 = var0 - var4;
      double var10 = var2 - var6;
      var8 = var8 * var8 * (3.0 - 2.0 * var8);
      var10 = var10 * var10 * (3.0 - 2.0 * var10);
      double var12 = (hash(var4, var6) & 1023L) / 1023.0;
      double var14 = (hash(var4 + 1L, var6) & 1023L) / 1023.0;
      double var16 = (hash(var4, var6 + 1L) & 1023L) / 1023.0;
      double var18 = (hash(var4 + 1L, var6 + 1L) & 1023L) / 1023.0;
      return class_3532.method_16436(var10, class_3532.method_16436(var8, var12, var14), class_3532.method_16436(var8, var16, var18));
   }

   static double fbm(double var0, double var2) {
      return noise(var0, var2) * 0.55 + noise(var0 * 2.1, var2 * 2.1) * 0.3 + noise(var0 * 4.3, var2 * 4.3) * 0.15;
   }

   @Override
   public String getInfo() {
      return this.design.get() == Cape.Design.CUSTOM && this.loadedFile != null ? this.loadedFile : this.design.displayValue();
   }

   public static enum Design {
      DIH,
      GRADIENT,
      RAINBOW,
      GALAXY,
      FIRE,
      MATRIX,
      ENDER,
      OCEAN,
      AURORA,
      LIGHTNING,
      CHERRY,
      LAVA,
      VOID,
      NEON,
      CREEPER,
      CHECKER,
      STRIPES,
      GERMANY,
      UK,
      CUSTOM;
   }
}
