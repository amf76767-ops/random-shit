package dev.dihclient.modules.fun;

import dev.dihclient.DIHClient;
import dev.dihclient.hud.HudManager;
import dev.dihclient.module.Category;
import dev.dihclient.module.Module;
import dev.dihclient.modules.render.Freecam;
import dev.dihclient.render.Render3D;
import dev.dihclient.setting.BoolSetting;
import dev.dihclient.setting.ColorSetting;
import dev.dihclient.setting.DoubleSetting;
import dev.dihclient.setting.EnumSetting;
import dev.dihclient.setting.IntSetting;
import dev.dihclient.setting.StringSetting;
import dev.dihclient.util.ImageLoader;
import dev.dihclient.util.Notifications;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import javax.imageio.ImageIO;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.class_156;
import net.minecraft.class_1657;
import net.minecraft.class_238;
import net.minecraft.class_243;
import net.minecraft.class_3532;
import net.minecraft.class_5498;
import net.minecraft.class_742;
import org.joml.Vector3f;

public class Hats extends Module {
   public final EnumSetting<Hats.Hat> hat = this.mode(
      "Hat", "Which hat you wear. Custom = your own picture from .minecraft/dihclient/hats.", Hats.Hat.CHINA_HAT
   );
   public final StringSetting image = this.text(
         "Image", "Picture in .minecraft/dihclient/hats for the Custom hat (empty = first file). PNG with transparency works best.", "", 200
      )
      .onChange(this::reload);
   public final EnumSetting<Hats.CustomStyle> customStyle = this.mode(
         "Custom Style",
         "Pixel: the picture stands on your head as a 3D pixel figure (like an item). Round: the picture is the side view of a hat and gets spun into a round hat, its colours wrap around it.",
         Hats.CustomStyle.PIXEL
      )
      .onChange(this::reload);
   public final IntSetting resolution = this.integer("Resolution", "Pictures are shrunk to this many pixels at most (lower = faster).", 32, 8, 64)
      .onChange(this::reload);
   public final DoubleSetting thickness = this.dbl("Thickness", "Pixel style: how thick the figure is (in pixels).", 1.0, 0.25, 6.0, 0.25)
      .visibleWhen(() -> this.customStyle.get() == Hats.CustomStyle.PIXEL)
      .onChange(this::reload);
   public final EnumSetting<Hats.ColorMode> colorMode = this.mode(
      "Color Mode",
      "Static: Color 1 · Gradient: Color 1 → Color 2 · Rainbow · Theme: the client accent colours · Classic: each hat's own colours.",
      Hats.ColorMode.CLASSIC
   );
   public final ColorSetting color1 = this.color("Color 1", "First colour.", -7722014);
   public final ColorSetting color2 = this.color("Color 2", "Second colour.", -15597544);
   public final IntSetting opacity = this.integer("Opacity", "How solid the hat is.", 170, 20, 255);
   public final DoubleSetting size = this.dbl("Size", "Hat size.", 1.0, 0.5, 2.0, 0.05);
   public final DoubleSetting height = this.dbl("Height", "Moves the hat up or down on the head.", 0.0, -0.3, 0.6, 0.01);
   public final BoolSetting followHead = this.bool("Follow Head", "Tilts with the head when looking up / down.", true);
   public final BoolSetting spin = this.bool("Spin", "Slowly spins round hats (China hat, halo, propeller spins anyway).", true);
   public final BoolSetting outline = this.bool("Outline", "Thin edge lines.", true);
   public final BoolSetting self = this.bool("Self", "Hat on you (third person / Freecam).", true);
   public final BoolSetting friends = this.bool("Friends", "Friends wear hats too.", true);
   public final BoolSetting everyone = this.bool("Everyone", "Every player wears a hat.", false);
   public final BoolSetting randomOthers = this.bool("Random For Others", "Other players get their own random hat (always the same one per player).", true);
   private static final int SEG = 28;
   private Render3D r;
   private class_243 pivot;
   private float yawRad;
   private float pitchRad;
   private double scale;
   private int alpha;
   private Hats.Hat current;
   private float time;
   private int seed;
   private List<float[]> mesh = List.of();
   private List<Integer> meshColors = List.of();
   private boolean meshTried;
   private String loadedImage = "";

   public Hats() {
      super(
         "Hats",
         Category.FUN,
         "Hats on your head: China hat, top hat, crown, halo, witch, wizard, party, santa, cowboy, sombrero, viking, horns, bunny ears, propeller cap … or your own PNG."
      );
      this.action("Choose Image", "Pick a PNG / JPG / GIF from your computer – it becomes the Custom hat.", this::chooseImage);
      this.action("Open Hats Folder", "Opens .minecraft/dihclient/hats.", () -> class_156.method_668().method_672(dir().toFile()));
      this.action("Reload Image", "Loads the picture again (after you changed the file).", this::reload);
   }

   public static Path dir() {
      Path var0 = FabricLoader.getInstance().getGameDir().resolve("dihclient").resolve("hats");

      try {
         Files.createDirectories(var0);
      } catch (Exception var2) {
      }

      return var0;
   }

   private void chooseImage() {
      ImageLoader.pickFile("Choose a picture for your hat").whenComplete((var1, var2) -> mc.execute(() -> {
         if (var2 == null && var1 != null) {
            try {
               Path var3 = dir().resolve(var1.getFileName().toString());
               if (!var1.toAbsolutePath().normalize().equals(var3.toAbsolutePath().normalize())) {
                  Files.copy(var1, var3, StandardCopyOption.REPLACE_EXISTING);
               }

               this.image.set(var3.getFileName().toString());
               this.hat.set(Hats.Hat.CUSTOM);
               this.reload();
            } catch (Exception var4) {
               Notifications.error("Hats", "Could not copy " + var1.getFileName());
            }
         }
      }));
   }

   private void reload() {
      this.meshTried = false;
      this.mesh = List.of();
      this.meshColors = List.of();
   }

   private Path imageFile() {
      ArrayList var1 = new ArrayList();

      try (Stream var2 = Files.list(dir())) {
         var2.filter(var0 -> Files.isRegularFile(var0) && ImageLoader.supported(var0)).sorted().forEach(var1::add);
      } catch (Exception var7) {
      }

      for (Path var3 : var1) {
         if (var3.getFileName().toString().equalsIgnoreCase(this.image.get().trim())) {
            return var3;
         }
      }

      return var1.isEmpty() ? null : (Path)var1.get(0);
   }

   private void ensureMesh() {
      if (!this.meshTried) {
         this.meshTried = true;
         Path var1 = this.imageFile();
         if (var1 == null) {
            Notifications.warn("Hats", "No hat picture yet – use Choose Image or put a PNG into .minecraft/dihclient/hats");
         } else {
            try {
               BufferedImage var2 = ImageIO.read(var1.toFile());
               if (var2 == null) {
                  throw new IllegalArgumentException("unsupported picture");
               }

               var2 = shrink(trim(var2), this.resolution.get());
               ArrayList var3 = new ArrayList();
               ArrayList var4 = new ArrayList();
               if (this.customStyle.get() == Hats.CustomStyle.ROUND) {
                  lathe(var2, var3, var4);
               } else {
                  extrude(var2, this.thickness.get(), var3, var4);
               }

               this.mesh = var3;
               this.meshColors = var4;
               if (!var1.getFileName().toString().equals(this.loadedImage)) {
                  this.loadedImage = var1.getFileName().toString();
                  Notifications.info("Hats", "Hat loaded: " + this.loadedImage + " (" + var2.getWidth() + "x" + var2.getHeight() + ")");
               }
            } catch (Exception var5) {
               Notifications.error("Hats", "Could not load " + var1.getFileName() + ": " + var5.getMessage());
            }
         }
      }
   }

   private static boolean solid(BufferedImage var0, int var1, int var2) {
      return var1 >= 0 && var2 >= 0 && var1 < var0.getWidth() && var2 < var0.getHeight() && var0.getRGB(var1, var2) >>> 24 >= 128;
   }

   private static BufferedImage trim(BufferedImage var0) {
      int var1 = var0.getWidth();
      int var2 = var0.getHeight();
      int var3 = var1;
      int var4 = var2;
      int var5 = -1;
      int var6 = -1;

      for (int var7 = 0; var7 < var2; var7++) {
         for (int var8 = 0; var8 < var1; var8++) {
            if (solid(var0, var8, var7)) {
               var3 = Math.min(var3, var8);
               var4 = Math.min(var4, var7);
               var5 = Math.max(var5, var8);
               var6 = Math.max(var6, var7);
            }
         }
      }

      return var5 < 0 ? var0 : var0.getSubimage(var3, var4, var5 - var3 + 1, var6 - var4 + 1);
   }

   private static BufferedImage shrink(BufferedImage var0, int var1) {
      int var2 = var0.getWidth();
      int var3 = var0.getHeight();
      if (var2 <= var1 && var3 <= var1) {
         return var0;
      } else {
         double var4 = (double)var1 / Math.max(var2, var3);
         int var6 = Math.max(1, (int)Math.round(var2 * var4));
         int var7 = Math.max(1, (int)Math.round(var3 * var4));
         BufferedImage var8 = new BufferedImage(var6, var7, 2);

         for (int var9 = 0; var9 < var7; var9++) {
            for (int var10 = 0; var10 < var6; var10++) {
               var8.setRGB(var10, var9, var0.getRGB(Math.min(var2 - 1, (int)((var10 + 0.5) / var4)), Math.min(var3 - 1, (int)((var9 + 0.5) / var4))));
            }
         }

         return var8;
      }
   }

   private static void add(List<float[]> var0, List<Integer> var1, int var2, double... var3) {
      float[] var4 = new float[12];

      for (int var5 = 0; var5 < 12; var5++) {
         var4[var5] = (float)var3[var5];
      }

      var0.add(var4);
      var1.add(var2);
   }

   private static void extrude(BufferedImage var0, double var1, List<float[]> var3, List<Integer> var4) {
      int var5 = var0.getWidth();
      int var6 = var0.getHeight();
      double var7 = 0.75 / Math.max(var5, var6);
      double var9 = var7 * var1 / 2.0;
      double var11 = -var5 * var7 / 2.0;
      double var13 = 0.45;

      for (int var15 = 0; var15 < var6; var15++) {
         double var16 = var13 + (var6 - 1 - var15) * var7;
         double var18 = var16 + var7;
         int var20 = 0;

         while (var20 < var5) {
            if (!solid(var0, var20, var15)) {
               var20++;
            } else {
               int var21 = var0.getRGB(var20, var15) | 0xFF000000;
               int var22 = var20;

               while (var22 + 1 < var5 && solid(var0, var22 + 1, var15) && (var0.getRGB(var22 + 1, var15) | 0xFF000000) == var21) {
                  var22++;
               }

               double var23 = var11 + var20 * var7;
               double var25 = var11 + (var22 + 1) * var7;
               add(var3, var4, var21, var23, var16, var9, var25, var16, var9, var25, var18, var9, var23, var18, var9);
               add(var3, var4, darker(var21, 0.8F), var23, var16, -var9, var25, var16, -var9, var25, var18, -var9, var23, var18, -var9);
               var20 = var22 + 1;
            }
         }

         for (int var27 = 0; var27 < var5; var27++) {
            if (solid(var0, var27, var15)) {
               int var28 = var0.getRGB(var27, var15) | 0xFF000000;
               double var29 = var11 + var27 * var7;
               double var30 = var29 + var7;
               if (!solid(var0, var27 - 1, var15)) {
                  add(var3, var4, darker(var28, 0.7F), var29, var16, -var9, var29, var16, var9, var29, var18, var9, var29, var18, -var9);
               }

               if (!solid(var0, var27 + 1, var15)) {
                  add(var3, var4, darker(var28, 0.7F), var30, var16, -var9, var30, var16, var9, var30, var18, var9, var30, var18, -var9);
               }

               if (!solid(var0, var27, var15 - 1)) {
                  add(var3, var4, darker(var28, 0.9F), var29, var18, -var9, var30, var18, -var9, var30, var18, var9, var29, var18, var9);
               }

               if (!solid(var0, var27, var15 + 1)) {
                  add(var3, var4, darker(var28, 0.6F), var29, var16, -var9, var30, var16, -var9, var30, var16, var9, var29, var16, var9);
               }
            }
         }
      }
   }

   private static void lathe(BufferedImage var0, List<float[]> var1, List<Integer> var2) {
      int var3 = var0.getWidth();
      int var4 = var0.getHeight();
      double var5 = 0.95 / var3;
      double var7 = var3 / 2.0;
      double[] var9 = new double[var4];

      for (int var10 = 0; var10 < var4; var10++) {
         double var11 = 0.0;

         for (int var13 = 0; var13 < var3; var13++) {
            if (solid(var0, var13, var10)) {
               var11 = Math.max(var11, Math.max(Math.abs(var13 - var7), Math.abs(var13 + 1 - var7)));
            }
         }

         var9[var10] = var11 * var5;
      }

      byte var28 = 28;

      for (int var29 = var4 - 1; var29 >= 0; var29--) {
         double var12 = var9[var29];
         if (!(var12 <= 0.0)) {
            double var14 = 0.45 + (var4 - 1 - var29) * var5;
            double var16 = var14 + var5;
            double var18 = var29 > 0 ? var9[var29 - 1] : 0.0;
            double var20 = var29 < var4 - 1 ? var9[var29 + 1] : 0.0;

            for (int var22 = 0; var22 < var28; var22++) {
               double var23 = (Math.PI * 2) * var22 / var28;
               double var25 = (Math.PI * 2) * (var22 + 1) / var28;
               int var27 = wrapColor(var0, var29, (var22 + 0.5) / var28);
               add(
                  var1,
                  var2,
                  var27,
                  Math.sin(var23) * var12,
                  var14,
                  Math.cos(var23) * var12,
                  Math.sin(var25) * var12,
                  var14,
                  Math.cos(var25) * var12,
                  Math.sin(var25) * var12,
                  var16,
                  Math.cos(var25) * var12,
                  Math.sin(var23) * var12,
                  var16,
                  Math.cos(var23) * var12
               );
               if (var18 < var12) {
                  add(
                     var1,
                     var2,
                     darker(var27, 0.9F),
                     Math.sin(var23) * var18,
                     var16,
                     Math.cos(var23) * var18,
                     Math.sin(var25) * var18,
                     var16,
                     Math.cos(var25) * var18,
                     Math.sin(var25) * var12,
                     var16,
                     Math.cos(var25) * var12,
                     Math.sin(var23) * var12,
                     var16,
                     Math.cos(var23) * var12
                  );
               }

               if (var20 < var12) {
                  add(
                     var1,
                     var2,
                     darker(var27, 0.6F),
                     Math.sin(var23) * var20,
                     var14,
                     Math.cos(var23) * var20,
                     Math.sin(var25) * var20,
                     var14,
                     Math.cos(var25) * var20,
                     Math.sin(var25) * var12,
                     var14,
                     Math.cos(var25) * var12,
                     Math.sin(var23) * var12,
                     var14,
                     Math.cos(var23) * var12
                  );
               }
            }
         }
      }
   }

   private static int wrapColor(BufferedImage var0, int var1, double var2) {
      int var4 = var0.getWidth();
      int var5 = Math.min(var4 - 1, (int)(var2 * var4));
      if (solid(var0, var5, var1)) {
         return var0.getRGB(var5, var1) | 0xFF000000;
      } else {
         long var6 = 0L;
         long var8 = 0L;
         long var10 = 0L;
         int var12 = 0;

         for (int var13 = 0; var13 < var4; var13++) {
            if (solid(var0, var13, var1)) {
               int var14 = var0.getRGB(var13, var1);
               var6 += var14 >> 16 & 0xFF;
               var8 += var14 >> 8 & 0xFF;
               var10 += var14 & 0xFF;
               var12++;
            }
         }

         return var12 == 0 ? -1 : 0xFF000000 | (int)(var6 / var12) << 16 | (int)(var8 / var12) << 8 | (int)(var10 / var12);
      }
   }

   private static int darker(int var0, float var1) {
      return var0 & 0xFF000000 | (int)((var0 >> 16 & 0xFF) * var1) << 16 | (int)((var0 >> 8 & 0xFF) * var1) << 8 | (int)((var0 & 0xFF) * var1);
   }

   private void drawCustom() {
      this.ensureMesh();
      List var1 = this.mesh;
      List var2 = this.meshColors;

      for (int var3 = 0; var3 < var1.size(); var3++) {
         float[] var4 = (float[])var1.get(var3);
         int var5 = this.col(0.5F, (Integer)var2.get(var3));
         this.quad(
            this.world(var4[0], var4[1], var4[2]),
            this.world(var4[3], var4[4], var4[5]),
            this.world(var4[6], var4[7], var4[8]),
            this.world(var4[9], var4[10], var4[11]),
            var5,
            var5,
            var5,
            var5
         );
      }
   }

   @Override
   public String getInfo() {
      return this.hat.displayValue();
   }

   @Override
   public void onRender3D(Render3D var1) {
      if (mc.field_1687 != null && mc.field_1724 != null) {
         this.r = var1;
         this.time = (float)(System.currentTimeMillis() % 1000000L) / 1000.0F;
         boolean var2 = mc.field_1690.method_31044() != class_5498.field_26664 || Freecam.active() != null;

         for (class_742 var4 : mc.field_1687.method_18456()) {
            if (!var4.method_5767()) {
               boolean var5 = var4 == mc.field_1724;
               Hats.Hat var6;
               if (var5) {
                  if (!this.self.get() || !var2) {
                     continue;
                  }

                  var6 = this.hat.get();
               } else {
                  boolean var7 = DIHClient.social().isFriend(var4);
                  if (!this.everyone.get() && (!var7 || !this.friends.get())) {
                     continue;
                  }

                  var6 = this.randomOthers.get()
                     ? Hats.Hat.values()[Math.floorMod(var4.method_5667().hashCode(), Hats.Hat.values().length - 1)]
                     : this.hat.get();
               }

               this.drawFor(var4, var6, var1.tickDelta());
            }
         }
      }
   }

   private void drawFor(class_1657 var1, Hats.Hat var2, float var3) {
      double var4 = class_3532.method_16436(var3, var1.field_6038, var1.method_23317());
      double var6 = class_3532.method_16436(var3, var1.field_5971, var1.method_23318());
      double var8 = class_3532.method_16436(var3, var1.field_5989, var1.method_23321());
      double var10 = var6 + var1.method_17682() + 0.06 + this.height.get();
      if (this.r.visible(new class_238(var4 - 1.0, var10 - 1.0, var8 - 1.0, var4 + 1.0, var10 + 1.5, var8 + 1.0), 0.0)) {
         float var12 = class_3532.method_16439(var3, var1.field_5982, var1.method_36454());
         float var13 = class_3532.method_16439(var3, var1.field_6004, var1.method_36455());
         this.scale = this.size.get();
         this.alpha = this.opacity.get();
         this.current = var2;
         this.seed = var1.method_5667().hashCode();
         this.yawRad = (float)Math.toRadians(var12);
         this.pitchRad = this.followHead.get() ? (float)Math.toRadians(var13) : 0.0F;
         this.pivot = new class_243(var4, var10 - 0.45, var8);
         this.build(var2);
      }
   }

   private class_243 world(double var1, double var3, double var5) {
      double var7 = var1 * this.scale;
      double var9 = (var3 - 0.45) * this.scale + 0.45;
      double var11 = var5 * this.scale;
      double var13 = Math.cos(this.pitchRad);
      double var15 = Math.sin(this.pitchRad);
      double var17 = var9 * var13 - var11 * var15;
      double var19 = var9 * var15 + var11 * var13;
      double var21 = Math.cos(this.yawRad);
      double var23 = Math.sin(this.yawRad);
      double var25 = var7 * var21 - var19 * var23;
      double var27 = var7 * var23 + var19 * var21;
      return new class_243(this.pivot.field_1352 + var25, this.pivot.field_1351 + var17, this.pivot.field_1350 + var27);
   }

   private Vector3f v(class_243 var1) {
      class_243 var2 = this.r.camera();
      return new Vector3f((float)(var1.field_1352 - var2.field_1352), (float)(var1.field_1351 - var2.field_1351), (float)(var1.field_1350 - var2.field_1350));
   }

   private void quad(class_243 var1, class_243 var2, class_243 var3, class_243 var4, int var5, int var6, int var7, int var8) {
      this.r.skyQuad(this.v(var1), this.v(var2), this.v(var3), this.v(var4), var5, var6, var7, var8);
   }

   private void tri(class_243 var1, class_243 var2, class_243 var3, int var4, int var5, int var6) {
      this.quad(var1, var2, var3, var3, var4, var5, var6, var6);
   }

   private void edge(class_243 var1, class_243 var2, int var3) {
      if (this.outline.get()) {
         this.r.line(var1, var2, var3 | 0xFF000000, false);
      }
   }

   private int col(float var1, int var2) {
      return switch ((Hats.ColorMode)this.colorMode.get()) {
         case STATIC -> this.color1.get();
         case GRADIENT -> blend(this.color1.get(), this.color2.get(), var1);
         case RAINBOW -> Color.HSBtoRGB((var1 * 0.8F + this.time * 0.25F + (this.seed & 0xFF) / 255.0F) % 1.0F, 0.75F, 1.0F);
         case THEME -> blend(HudManager.accent(), this.color2.get(), var1 * 0.6F);
         default -> var2;
      } & 16777215 | this.alpha << 24;
   }

   private static int blend(int var0, int var1, float var2) {
      var2 = Math.max(0.0F, Math.min(1.0F, var2));
      int var3 = (int)((var0 >> 16 & 0xFF) + ((var1 >> 16 & 0xFF) - (var0 >> 16 & 0xFF)) * var2);
      int var4 = (int)((var0 >> 8 & 0xFF) + ((var1 >> 8 & 0xFF) - (var0 >> 8 & 0xFF)) * var2);
      int var5 = (int)((var0 & 0xFF) + ((var1 & 0xFF) - (var0 & 0xFF)) * var2);
      return 0xFF000000 | var3 << 16 | var4 << 8 | var5;
   }

   private float spinAngle(float var1) {
      return this.spin.get() ? this.time * var1 : 0.0F;
   }

   private class_243 ring(double var1, double var3, double var5, double var7, double var9) {
      return this.world(Math.sin(var9) * var1 + var5, var3, Math.cos(var9) * var1 + var7);
   }

   private void cone(double var1, double var3, double var5, double var7, double var9, int var11, int var12, float var13) {
      class_243 var14 = this.world(var7, var3 + var5, var9);

      for (int var15 = 0; var15 < 28; var15++) {
         double var16 = var13 + (Math.PI * 2) * var15 / 28.0;
         double var18 = var13 + (Math.PI * 2) * (var15 + 1) / 28.0;
         class_243 var20 = this.ring(var1, var3, 0.0, 0.0, var16);
         class_243 var21 = this.ring(var1, var3, 0.0, 0.0, var18);
         float var22 = var15 / 28.0F;
         int var23 = this.col(this.colorMode.get() == Hats.ColorMode.RAINBOW ? var22 : 0.0F, var11);
         int var24 = this.col(this.colorMode.get() == Hats.ColorMode.RAINBOW ? var22 : 1.0F, var12);
         this.tri(var20, var21, var14, var23, var23, var24);
         if (var15 % 4 == 0) {
            this.edge(var20, var14, var12);
         }

         this.edge(var20, var21, var11);
      }
   }

   private void ring(double var1, double var3, double var5, int var7, int var8, double var9) {
      for (int var11 = 0; var11 < 28; var11++) {
         double var12 = (Math.PI * 2) * var11 / 28.0;
         double var14 = (Math.PI * 2) * (var11 + 1) / 28.0;
         double var16 = var9 * Math.abs(Math.sin(var12));
         double var18 = var9 * Math.abs(Math.sin(var14));
         class_243 var20 = this.ring(var1, var5, 0.0, 0.0, var12);
         class_243 var21 = this.ring(var1, var5, 0.0, 0.0, var14);
         class_243 var22 = this.ring(var3, var5 + var16, 0.0, 0.0, var12);
         class_243 var23 = this.ring(var3, var5 + var18, 0.0, 0.0, var14);
         int var24 = this.col(0.0F, var7);
         int var25 = this.col(1.0F, var8);
         this.quad(var20, var21, var23, var22, var24, var24, var25, var25);
         this.edge(var22, var23, var8);
      }
   }

   private void cylinder(double var1, double var3, double var5, int var7, int var8) {
      for (int var9 = 0; var9 < 28; var9++) {
         double var10 = (Math.PI * 2) * var9 / 28.0;
         double var12 = (Math.PI * 2) * (var9 + 1) / 28.0;
         class_243 var14 = this.ring(var1, var3, 0.0, 0.0, var10);
         class_243 var15 = this.ring(var1, var3, 0.0, 0.0, var12);
         class_243 var16 = this.ring(var1, var5, 0.0, 0.0, var10);
         class_243 var17 = this.ring(var1, var5, 0.0, 0.0, var12);
         int var18 = this.col(0.0F, var7);
         int var19 = this.col(1.0F, var8);
         this.quad(var14, var15, var17, var16, var18, var18, var19, var19);
         this.edge(var16, var17, var8);
         this.edge(var14, var15, var7);
      }
   }

   private void dome(double var1, double var3, double var5, int var7, int var8) {
      byte var9 = 5;

      for (int var10 = 0; var10 < var9; var10++) {
         double var11 = (Math.PI / 2) * var10 / var9;
         double var13 = (Math.PI / 2) * (var10 + 1) / var9;

         for (int var15 = 0; var15 < 28; var15++) {
            double var16 = (Math.PI * 2) * var15 / 28.0;
            double var18 = (Math.PI * 2) * (var15 + 1) / 28.0;
            class_243 var20 = this.ring(var1 * Math.cos(var11), var3 + var5 * Math.sin(var11), 0.0, 0.0, var16);
            class_243 var21 = this.ring(var1 * Math.cos(var11), var3 + var5 * Math.sin(var11), 0.0, 0.0, var18);
            class_243 var22 = this.ring(var1 * Math.cos(var13), var3 + var5 * Math.sin(var13), 0.0, 0.0, var16);
            class_243 var23 = this.ring(var1 * Math.cos(var13), var3 + var5 * Math.sin(var13), 0.0, 0.0, var18);
            int var24 = this.col((float)var10 / var9, var7);
            int var25 = this.col((float)(var10 + 1) / var9, var8);
            this.quad(var20, var21, var23, var22, var24, var24, var25, var25);
         }
      }
   }

   private void box(double var1, double var3, double var5, double var7, double var9, double var11, int var13) {
      class_243[] var14 = new class_243[8];

      for (int var15 = 0; var15 < 8; var15++) {
         var14[var15] = this.world(var1 + ((var15 & 1) == 0 ? -var7 : var7), var3 + ((var15 & 2) == 0 ? 0.0 : var9), var5 + ((var15 & 4) == 0 ? -var11 : var11));
      }

      int var21 = this.col(0.5F, var13);
      int[][] var16 = new int[][]{{0, 1, 3, 2}, {4, 5, 7, 6}, {0, 1, 5, 4}, {2, 3, 7, 6}, {0, 2, 6, 4}, {1, 3, 7, 5}};

      for (int[] var20 : var16) {
         this.quad(var14[var20[0]], var14[var20[1]], var14[var20[2]], var14[var20[3]], var21, var21, var21, var21);
      }
   }

   private void horn(double var1, double var3, double var5, double var7, double var9, double var11, int var13, int var14) {
      double var15 = var1;
      double var17 = var3;
      double var19 = var5;
      double var21 = var11;

      for (int var23 = 0; var23 < 4; var23++) {
         double var24 = var15 + var7 * 0.07;
         double var26 = var17 + 0.03 + var23 * 0.035;
         double var28 = var19 + var9 * 0.07;
         double var30 = var21 * 0.72;

         for (int var32 = 0; var32 < 10; var32++) {
            double var33 = (Math.PI * 2) * var32 / 10.0;
            double var35 = (Math.PI * 2) * (var32 + 1) / 10.0;
            class_243 var37 = this.world(var15 + Math.sin(var33) * var21, var17 + Math.cos(var33) * var21, var19);
            class_243 var38 = this.world(var15 + Math.sin(var35) * var21, var17 + Math.cos(var35) * var21, var19);
            class_243 var39 = this.world(var24 + Math.sin(var35) * var30, var26 + Math.cos(var35) * var30, var28);
            class_243 var40 = this.world(var24 + Math.sin(var33) * var30, var26 + Math.cos(var33) * var30, var28);
            int var41 = this.col(var23 / 4.0F, var13);
            int var42 = this.col((var23 + 1) / 4.0F, var14);
            this.quad(var37, var38, var39, var40, var41, var41, var42, var42);
         }

         var15 = var24;
         var17 = var26;
         var19 = var28;
         var21 = var30;
      }

      class_243 var43 = this.world(var15 + var7 * 0.05, var17 + 0.05, var19 + var9 * 0.05);

      for (int var44 = 0; var44 < 10; var44++) {
         double var25 = (Math.PI * 2) * var44 / 10.0;
         double var27 = (Math.PI * 2) * (var44 + 1) / 10.0;
         this.tri(
            this.world(var15 + Math.sin(var25) * var21, var17 + Math.cos(var25) * var21, var19),
            this.world(var15 + Math.sin(var27) * var21, var17 + Math.cos(var27) * var21, var19),
            var43,
            this.col(1.0F, var14),
            this.col(1.0F, var14),
            this.col(1.0F, var14)
         );
      }
   }

   private void build(Hats.Hat var1) {
      double var2 = 0.45;
      switch (var1) {
         case CHINA_HAT:
            this.cone(0.62, var2 + 0.02, 0.3, 0.0, 0.0, -1668544, -4686830, this.spinAngle(0.6F));
            break;
         case CHINA_HAT_2:
            this.cone(0.7, var2 + 0.02, 0.22, 0.0, 0.0, -16711681, -65281, this.spinAngle(0.8F));
            this.cone(0.45, var2 + 0.2, 0.2, 0.0, 0.0, -65281, -16711681, -this.spinAngle(0.8F));
            break;
         case TOP_HAT:
            this.ring(0.0, 0.42, var2, -15658735, -15658735, 0.0);
            this.cylinder(0.27, var2, var2 + 0.1, -5963776, -5963776);
            this.cylinder(0.27, var2 + 0.1, var2 + 0.48, -15658735, -14145496);
            this.ring(0.0, 0.27, var2 + 0.48, -14145496, -14145496, 0.0);
            break;
         case CROWN:
            this.cylinder(0.29, var2 - 0.02, var2 + 0.1, -10496, -2448096);

            for (int var22 = 0; var22 < 8; var22++) {
               double var5 = (Math.PI * 2) * var22 / 8.0 + this.spinAngle(0.3F);
               double var28 = (Math.PI * 2) * (var22 + 0.5) / 8.0 + this.spinAngle(0.3F);
               double var32 = (Math.PI * 2) * (var22 + 1) / 8.0 + this.spinAngle(0.3F);
               class_243 var36 = this.ring(0.29, var2 + 0.1, 0.0, 0.0, var5);
               class_243 var39 = this.ring(0.29, var2 + 0.1, 0.0, 0.0, var32);
               class_243 var42 = this.ring(0.29, var2 + 0.25, 0.0, 0.0, var28);
               this.tri(var36, var39, var42, this.col(0.0F, -10496), this.col(0.0F, -10496), this.col(1.0F, -2448096));
               this.edge(var36, var42, -2448096);
               this.edge(var39, var42, -2448096);
               this.box(Math.sin(var28) * 0.3, var2 + 0.02, Math.cos(var28) * 0.3, 0.025, 0.05, 0.025, var22 % 2 == 0 ? -65536 : -16741633);
            }
            break;
         case HALO:
            double var4 = this.spinAngle(1.2F);
            this.pitchRad = 0.0F;

            for (int var25 = 0; var25 < 28; var25++) {
               double var27 = var4 + (Math.PI * 2) * var25 / 28.0;
               double var31 = var4 + (Math.PI * 2) * (var25 + 1) / 28.0;
               class_243 var35 = this.ring(0.24, var2 + 0.28, 0.0, 0.0, var27);
               class_243 var38 = this.ring(0.24, var2 + 0.28, 0.0, 0.0, var31);
               class_243 var41 = this.ring(0.32, var2 + 0.28, 0.0, 0.0, var27);
               class_243 var43 = this.ring(0.32, var2 + 0.28, 0.0, 0.0, var31);
               int var45 = this.col(var25 / 28.0F, -8356);
               this.quad(var35, var38, var43, var41, var45, var45, var45, var45);
               this.edge(var41, var43, -2240);
            }
            break;
         case WITCH_HAT:
            this.ring(0.0, 0.58, var2, -14671840, -13553359, 0.0);
            this.cylinder(0.27, var2, var2 + 0.07, -7580696, -7580696);
            this.cone(0.27, var2 + 0.07, 0.62, 0.0, -0.18, -14671840, -12437418, 0.0F);
            break;
         case WIZARD_HAT:
            this.ring(0.0, 0.42, var2, -14666090, -14666090, 0.0);
            this.cone(0.3, var2, 0.8, 0.05, -0.08, -14666090, -9869313, 0.0F);

            for (int var24 = 0; var24 < 6; var24++) {
               double var26 = (Math.PI * 2) * var24 / 6.0;
               double var30 = 0.2 + var24 % 3 * 0.18;
               double var34 = 0.3 * (1.0 - var30 / 0.8) + 0.012;
               this.box(Math.sin(var26) * var34, var2 + var30, Math.cos(var26) * var34, 0.022, 0.044, 0.022, -2240);
            }
            break;
         case PARTY_HAT:
            for (int var23 = 0; var23 < 4; var23++) {
               double var7 = 0.22 * (1.0 - var23 / 4.0);
               double var29 = 0.22 * (1.0 - (var23 + 1) / 4.0);
               int var33 = var23 % 2 == 0 ? -49024 : -16728065;

               for (int var37 = 0; var37 < 28; var37++) {
                  double var40 = (Math.PI * 2) * var37 / 28.0;
                  double var44 = (Math.PI * 2) * (var37 + 1) / 28.0;
                  class_243 var17 = this.ring(var7, var2 + 0.5 * var23 / 4.0, 0.0, 0.0, var40);
                  class_243 var18 = this.ring(var7, var2 + 0.5 * var23 / 4.0, 0.0, 0.0, var44);
                  class_243 var19 = this.ring(var29, var2 + 0.5 * (var23 + 1) / 4.0, 0.0, 0.0, var44);
                  class_243 var20 = this.ring(var29, var2 + 0.5 * (var23 + 1) / 4.0, 0.0, 0.0, var40);
                  int var21 = this.col(var23 / 4.0F, var33);
                  this.quad(var17, var18, var19, var20, var21, var21, var21, var21);
               }
            }

            this.box(0.0, var2 + 0.5, 0.0, 0.05, 0.08, 0.05, -10496);
            break;
         case SANTA_HAT:
            this.cylinder(0.3, var2 - 0.02, var2 + 0.09, -1, -1);
            this.cone(0.28, var2 + 0.09, 0.42, 0.22, -0.18, -2883584, -3997696, 0.0F);
            this.box(0.22, var2 + 0.46, -0.18, 0.06, 0.1, 0.06, -1);
            break;
         case COWBOY_HAT:
            this.ring(0.24, 0.6, var2 + 0.02, -7581406, -9031895, 0.14);
            this.ring(0.0, 0.25, var2 + 0.02, -9031895, -9031895, 0.0);
            this.cylinder(0.25, var2 + 0.02, var2 + 0.26, -7581406, -8633823);
            this.cylinder(0.255, var2 + 0.03, var2 + 0.08, -13950169, -13950169);
            this.ring(0.0, 0.25, var2 + 0.26, -8633823, -8633823, 0.0);
            break;
         case SOMBRERO:
            this.ring(0.26, 0.88, var2 + 0.02, -1450675, -2643431, 0.08);
            this.cylinder(0.88, var2 + 0.1, var2 + 0.16, -14447820, -14447820);
            this.cone(0.26, var2 + 0.02, 0.42, 0.0, 0.0, -1450675, -2643431, 0.0F);
            break;
         case STRAW_HAT:
            this.ring(0.24, 0.62, var2 + 0.01, -1257079, -2446462, -0.05);
            this.dome(0.25, var2 + 0.01, 0.2, -1257079, -1982357);
            this.cylinder(0.252, var2 + 0.01, var2 + 0.06, -3663335, -3663335);
            break;
         case VIKING:
            this.dome(0.31, var2 - 0.08, 0.3, -7697782, -4210753);
            this.cylinder(0.315, var2 - 0.1, var2 - 0.02, -5210848, -5210848);
            this.horn(0.26, var2 + 0.02, 0.0, 1.0, 0.0, 0.07, -1383232, -2502451);
            this.horn(-0.26, var2 + 0.02, 0.0, -1.0, 0.0, 0.07, -1383232, -2502451);
            break;
         case DEVIL_HORNS:
            this.horn(0.14, var2 - 0.02, 0.05, 0.4, 0.2, 0.05, -2883584, -65536);
            this.horn(-0.14, var2 - 0.02, 0.05, -0.4, 0.2, 0.05, -2883584, -65536);
            break;
         case BUNNY_EARS:
            this.box(0.12, var2 - 0.02, -0.02, 0.05, 0.42, 0.018, -1);
            this.box(-0.12, var2 - 0.02, -0.02, 0.05, 0.42, 0.018, -1);
            this.box(0.12, var2 + 0.04, -0.005, 0.03, 0.32, 0.018, -26192);
            this.box(-0.12, var2 + 0.04, -0.005, 0.03, 0.32, 0.018, -26192);
            break;
         case PROPELLER_CAP:
            this.dome(0.3, var2 - 0.06, 0.18, -16733441, -65536);
            this.box(0.0, var2 + 0.1, 0.0, 0.015, 0.07, 0.015, -3355444);
            double var6 = this.time * 12.0;

            for (int var8 = 0; var8 < 2; var8++) {
               double var9 = var6 + Math.PI * var8;
               class_243 var11 = this.world(Math.sin(var9 - 0.2) * 0.04, var2 + 0.17, Math.cos(var9 - 0.2) * 0.04);
               class_243 var12 = this.world(Math.sin(var9 + 0.2) * 0.04, var2 + 0.17, Math.cos(var9 + 0.2) * 0.04);
               class_243 var13 = this.world(Math.sin(var9 + 0.12) * 0.3, var2 + 0.17, Math.cos(var9 + 0.12) * 0.3);
               class_243 var14 = this.world(Math.sin(var9 - 0.12) * 0.3, var2 + 0.17, Math.cos(var9 - 0.12) * 0.3);
               int var15 = this.col(0.5F, var8 == 0 ? -10496 : -16711809);
               this.quad(var11, var12, var13, var14, var15, var15, var15, var15);
            }
            break;
         case BEANIE:
            this.dome(0.305, var2 - 0.12, 0.26, -14540033, -11447809);
            this.cylinder(0.31, var2 - 0.13, var2 - 0.03, -1, -1);
            this.box(0.0, var2 + 0.12, 0.0, 0.07, 0.12, 0.07, -1);
            break;
         case CUSTOM:
            this.drawCustom();
      }
   }

   public static enum ColorMode {
      CLASSIC,
      STATIC,
      GRADIENT,
      RAINBOW,
      THEME;
   }

   public static enum CustomStyle {
      PIXEL,
      ROUND;
   }

   public static enum Hat {
      CHINA_HAT,
      CHINA_HAT_2,
      TOP_HAT,
      CROWN,
      HALO,
      WITCH_HAT,
      WIZARD_HAT,
      PARTY_HAT,
      SANTA_HAT,
      COWBOY_HAT,
      SOMBRERO,
      STRAW_HAT,
      VIKING,
      DEVIL_HORNS,
      BUNNY_EARS,
      PROPELLER_CAP,
      BEANIE,
      CUSTOM;
   }
}
