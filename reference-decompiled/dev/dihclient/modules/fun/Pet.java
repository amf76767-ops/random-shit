package dev.dihclient.modules.fun;

import dev.dihclient.DIHClient;
import dev.dihclient.module.Category;
import dev.dihclient.module.Module;
import dev.dihclient.render.Render3D;
import dev.dihclient.render.Shapes;
import dev.dihclient.setting.BoolSetting;
import dev.dihclient.setting.ColorSetting;
import dev.dihclient.setting.DoubleSetting;
import dev.dihclient.setting.EnumSetting;
import dev.dihclient.setting.IntSetting;
import dev.dihclient.setting.StringSetting;
import dev.dihclient.util.ImageLoader;
import dev.dihclient.util.Notifications;
import java.awt.Color;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.class_1011;
import net.minecraft.class_1043;
import net.minecraft.class_1296;
import net.minecraft.class_1297;
import net.minecraft.class_1299;
import net.minecraft.class_1309;
import net.minecraft.class_156;
import net.minecraft.class_1920;
import net.minecraft.class_2338;
import net.minecraft.class_243;
import net.minecraft.class_2561;
import net.minecraft.class_2960;
import net.minecraft.class_3532;
import net.minecraft.class_3730;
import net.minecraft.class_761;
import net.minecraft.class_1297.class_5529;

public class Pet extends Module {
   private static final int PET_ID = -777777;
   private static final class_2960 TEXTURE_ID = class_2960.method_60655("dihclient", "pet/custom");
   public final EnumSetting<Pet.Mode> mode = this.mode(
         "Mode",
         "Creature: fantasy pets that don't exist in Minecraft · Mob: a Minecraft animal · Image: your own PNG from the pets folder.",
         Pet.Mode.CREATURE
      )
      .onChange(this::respawn);
   public final EnumSetting<Pet.Creature> creature = this.mode(
         "Creature",
         "Ghost · Dragon (mini dragon) · Drone · UFO · Jellyfish · Fairy · Penguin · Robot · Eyeball (looks at you) · Planet (with moon) · Snake · Duck (rubber duck) · Wisp (soul flame).",
         Pet.Creature.DRAGON
      )
      .visibleWhen(() -> this.mode.get() == Pet.Mode.CREATURE);
   public final EnumSetting<Pet.ColorMode> colorMode = this.mode(
         "Colors", "Classic: the creature's own colours · Custom: main colour from Color · Rainbow: slowly cycling colours.", Pet.ColorMode.CLASSIC
      )
      .visibleWhen(() -> this.mode.get() == Pet.Mode.CREATURE);
   public final ColorSetting color = this.color("Color", "Main colour of the creature (Colors = Custom).", -8497214)
      .visibleWhen(() -> this.mode.get() == Pet.Mode.CREATURE && this.colorMode.get() == Pet.ColorMode.CUSTOM);
   public final EnumSetting<Pet.Kind> kind = this.mode("Animal", "Which animal follows you.", Pet.Kind.CAT)
      .visibleWhen(() -> this.mode.get() == Pet.Mode.MOB)
      .onChange(this::respawn);
   public final BoolSetting baby = this.bool("Baby", "Baby version (where the animal has one).", true)
      .visibleWhen(() -> this.mode.get() == Pet.Mode.MOB)
      .onChange(this::respawn);
   public final StringSetting petName = this.text("Name", "Name tag above the pet (empty = none).", "", 32).onChange(this::respawn);
   public final StringSetting image = this.text(
         "Image", "File name in .minecraft/dihclient/pets (empty = first file). PNG, GIF, JPG – or use Choose File.", "", 200
      )
      .visibleWhen(() -> this.mode.get() == Pet.Mode.IMAGE)
      .onChange(this::imageChosen);
   public final IntSetting sheetFrames = this.integer(
         "Sheet Frames",
         "Only for PNG sprite sheets: number of frames stacked vertically. 0 = auto (file name contains anim/sheet/sprite/frames), 1 = never animate.",
         0,
         0,
         64
      )
      .visibleWhen(() -> this.mode.get() == Pet.Mode.IMAGE)
      .onChange(this::reloadImage);
   public final DoubleSetting size = this.dbl("Size", "Size of the creature / image pet.", 0.9, 0.2, 4.0, 0.05)
      .visibleWhen(() -> this.mode.get() != Pet.Mode.MOB);
   public final IntSetting fps = this.integer("Animation FPS", "Frames per second for sprite sheets (GIFs use their own speed).", 8, 1, 30)
      .visibleWhen(() -> this.mode.get() == Pet.Mode.IMAGE);
   public final BoolSetting fullBright = this.bool("Glow", "Image pet ignores darkness.", true).visibleWhen(() -> this.mode.get() == Pet.Mode.IMAGE);
   public final EnumSetting<Pet.Place> place = this.mode(
      "Position", "Follow: trails behind you · Orbit: circles around you · Shoulder: sits on your right shoulder.", Pet.Place.FOLLOW
   );
   public final DoubleSetting distance = this.dbl("Distance", "How far the pet stays from you.", 1.4, 0.6, 5.0, 0.1)
      .visibleWhen(() -> this.place.get() != Pet.Place.SHOULDER);
   public final DoubleSetting orbitSpeed = this.dbl("Orbit Speed", "Rounds per 10 seconds in Orbit position.", 1.5, 0.2, 10.0, 0.1)
      .visibleWhen(() -> this.place.get() == Pet.Place.ORBIT);
   public final BoolSetting sleep = this.bool("Sleep", "Creature closes its eyes and snores (zZz) when you stand still for 30 seconds.", true)
      .visibleWhen(() -> this.mode.get() == Pet.Mode.CREATURE);
   public final BoolSetting reactToDamage = this.bool("React To Damage", "Pet jumps and flashes red when you get hurt (the dragon breathes fire).", true);
   private class_1297 mob;
   private class_243 pos;
   private class_243 lastPos;
   private boolean textureLoaded;
   private int frames = 1;
   private float aspect = 1.0F;
   private int frameMs;
   private String loadedFile;
   private int hurtAnim;
   private int lastHurtTime;
   private float yaw;
   private float lastYaw;
   private double walk;
   private double lastWalk;
   private double walkAmt;
   private int idleTicks;
   private float lastPlayerYaw;
   private double eyeOpen = 1.0;
   private float renderYaw;
   private Render3D r;

   public Pet() {
      super(
         "Pet",
         Category.FUN,
         "A client-side pet follows you: 13 animated fantasy creatures (dragon, ghost, UFO, drone, jellyfish …), a Minecraft animal or any picture (PNG, GIF, JPG)."
      );
      this.action("Choose File", "Pick any PNG / GIF / JPG from your computer. It gets copied into the pets folder.", this::chooseFile);
      this.action("Next Image", "Switches to the next picture in the pets folder.", this::nextImage);
      this.action("Open Pets Folder", "Opens .minecraft/dihclient/pets.", () -> {
         petDir();
         class_156.method_668().method_672(petDir().toFile());
      });
      this.action("Reload Image", "Reloads the PNG after you changed it.", this::reloadImage);
   }

   public static Path petDir() {
      Path var0 = FabricLoader.getInstance().getGameDir().resolve("dihclient").resolve("pets");

      try {
         Files.createDirectories(var0);
         Path var1 = var0.resolve("example_ghost.png");
         if (!Files.exists(var1)) {
            try (InputStream var2 = Pet.class.getResourceAsStream("/assets/dihclient/pet/example_ghost.png")) {
               if (var2 != null) {
                  Files.copy(var2, var1);
               }
            }
         }
      } catch (Exception var7) {
      }

      return var0;
   }

   private static List<Path> images() {
      ArrayList var0 = new ArrayList();

      try (Stream var1 = Files.list(petDir())) {
         var1.filter(var0x -> Files.isRegularFile(var0x) && ImageLoader.supported(var0x)).sorted().forEach(var0::add);
      } catch (Exception var6) {
      }

      return var0;
   }

   @Override
   protected void onEnable() {
      if (!inGame()) {
         this.setEnabledSilently(false);
      } else {
         this.pos = this.lastPos = null;
         this.idleTicks = 0;
         this.respawn();
      }
   }

   @Override
   protected void onDisable() {
      this.despawnMob();
   }

   @Override
   public void onWorldChange() {
      this.mob = null;
      this.pos = this.lastPos = null;
   }

   private void respawn() {
      this.despawnMob();
      if (this.isEnabled() && inGame()) {
         if (this.mode.get() == Pet.Mode.MOB) {
            this.spawnMob();
         } else if (this.mode.get() == Pet.Mode.IMAGE) {
            this.reloadImage();
         }
      }
   }

   private void spawnMob() {
      class_1297 var1 = this.kind.get().type().method_5883(mc.field_1687, class_3730.field_52444);
      if (var1 != null) {
         var1.method_5838(-777777);
         var1.field_5960 = true;
         var1.method_5803(true);
         var1.method_5875(true);
         if (var1 instanceof class_1296 var2) {
            var2.method_7217(this.baby.get());
         }

         if (!this.petName.get().isBlank()) {
            var1.method_5665(class_2561.method_43470(this.petName.get()));
            var1.method_5880(true);
         }

         class_243 var3 = this.targetPos(1.0F);
         var1.method_5808(var3.field_1352, var3.field_1351, var3.field_1350, mc.field_1724.method_36454(), 0.0F);
         mc.field_1687.method_53875(var1);
         this.mob = var1;
      }
   }

   private void despawnMob() {
      if (this.mob != null && mc.field_1687 != null) {
         mc.field_1687.method_2945(this.mob.method_5628(), class_5529.field_26999);
      }

      this.mob = null;
   }

   private void nextImage() {
      List var1 = images();
      if (var1.isEmpty()) {
         Notifications.warn("Pet", "No picture in .minecraft/dihclient/pets – use Choose File");
      } else {
         int var2 = 0;

         for (int var3 = 0; var3 < var1.size(); var3++) {
            if (((Path)var1.get(var3)).getFileName().toString().equals(this.image.get())) {
               var2 = (var3 + 1) % var1.size();
            }
         }

         if (this.mode.get() != Pet.Mode.IMAGE) {
            this.mode.set(Pet.Mode.IMAGE);
         }

         this.image.set(((Path)var1.get(var2)).getFileName().toString());
      }
   }

   private void imageChosen() {
      if (!this.image.get().isBlank() && this.mode.get() != Pet.Mode.IMAGE) {
         this.mode.set(Pet.Mode.IMAGE);
      } else {
         this.reloadImage();
      }
   }

   private void reloadImage() {
      this.textureLoaded = false;
      this.loadedFile = null;
      if (this.isEnabled() && this.mode.get() == Pet.Mode.IMAGE && mc.method_1531() != null) {
         List var1 = images();
         Path var2 = null;

         for (Path var4 : var1) {
            if (var4.getFileName().toString().equalsIgnoreCase(this.image.get().trim())) {
               var2 = var4;
            }
         }

         if (var2 == null && !var1.isEmpty()) {
            var2 = (Path)var1.get(0);
         }

         if (var2 == null) {
            Notifications.warn("Pet", "No picture yet – use Choose File or put one into .minecraft/dihclient/pets");
         } else {
            try {
               ImageLoader.Loaded var9 = ImageLoader.load(var2, 512);
               class_1011 var11 = var9.image();
               int var5 = var11.method_4307();
               int var6 = var11.method_4323();
               String var7 = var2.getFileName().toString();
               this.frames = var9.frames();
               this.frameMs = var9.frameMs();
               if (this.frames <= 1) {
                  this.frameMs = 0;
                  this.frames = this.sheetFramesFor(var7, var5, var6);
               }

               this.aspect = (float)var5 / (var6 / this.frames);
               mc.method_1531().method_4616(TEXTURE_ID, new class_1043(() -> "dihclient pet " + var7, var11));
               this.textureLoaded = true;
               this.loadedFile = var7;
               Notifications.info(
                  "Pet", "Loaded " + var7 + " (" + var5 + "x" + var6 / this.frames + (this.frames > 1 ? ", " + this.frames + " frames" : "") + ")"
               );
            } catch (Throwable var8) {
               this.loadedFile = var2.getFileName().toString();
               String var10 = var8.getMessage() == null ? var8.getClass().getSimpleName() : var8.getMessage();
               Notifications.error("Pet", "Could not load " + var2.getFileName() + ": " + var10);
               DIHClient.LOG.error("[DIHClient] pet image failed", var8);
            }
         }
      }
   }

   private int sheetFramesFor(String var1, int var2, int var3) {
      int var4 = this.sheetFrames.get();
      if (var4 == 1) {
         return 1;
      } else if (var4 > 1) {
         return var3 % var4 == 0 ? var4 : 1;
      } else {
         String var5 = var1.toLowerCase(Locale.ROOT);
         boolean var6 = var5.contains("anim")
            || var5.contains("sheet")
            || var5.contains("sprite")
            || var5.contains("frames")
            || var5.equals("example_ghost.png");
         return var6 && var3 > var2 && var3 % var2 == 0 ? var3 / var2 : 1;
      }
   }

   private void chooseFile() {
      ImageLoader.pickFile("Choose a pet picture").whenComplete((var1, var2) -> mc.execute(() -> {
         if (var2 != null) {
            Notifications.error("Pet", "File picker not available – copy the file into the pets folder instead.");
         } else if (var1 != null) {
            if (!ImageLoader.supported(var1)) {
               Notifications.warn("Pet", "Please choose a PNG, GIF or JPG");
            } else {
               try {
                  Path var3 = petDir().resolve(var1.getFileName().toString());
                  if (!var1.toAbsolutePath().normalize().equals(var3.toAbsolutePath().normalize())) {
                     Files.copy(var1, var3, StandardCopyOption.REPLACE_EXISTING);
                  }

                  if (this.mode.get() != Pet.Mode.IMAGE) {
                     this.mode.set(Pet.Mode.IMAGE);
                  }

                  if (var3.getFileName().toString().equals(this.image.get())) {
                     this.reloadImage();
                  } else {
                     this.image.set(var3.getFileName().toString());
                  }
               } catch (Exception var4) {
                  Notifications.error("Pet", "Could not copy " + var1.getFileName());
               }
            }
         }
      }));
   }

   private boolean flies() {
      return switch ((Pet.Mode)this.mode.get()) {
         case MOB -> this.kind.get().flies();
         case CREATURE -> this.creature.get().flies;
         case IMAGE -> true;
      };
   }

   private boolean shoulder() {
      return this.place.get() == Pet.Place.SHOULDER;
   }

   private class_243 targetPos(float var1) {
      double var2 = class_3532.method_16436(var1, mc.field_1724.field_6038, mc.field_1724.method_23317());
      double var4 = class_3532.method_16436(var1, mc.field_1724.field_5971, mc.field_1724.method_23318());
      double var6 = class_3532.method_16436(var1, mc.field_1724.field_5989, mc.field_1724.method_23321());
      double var8 = Math.toRadians(mc.field_1724.method_73188());
      if (this.shoulder()) {
         double var18 = this.mode.get() == Pet.Mode.IMAGE ? 1.75 : (this.mode.get() == Pet.Mode.MOB ? 1.35 : 1.42);
         if (mc.field_1724.method_5715()) {
            var18 -= 0.3;
         }

         return new class_243(var2 - Math.cos(var8) * 0.42, var4 + var18, var6 - Math.sin(var8) * 0.42);
      } else {
         double var10 = this.distance.get();
         double var12;
         double var14;
         if (this.place.get() == Pet.Place.ORBIT) {
            double var16 = (mc.field_1724.field_6012 + var1) / 20.0 * this.orbitSpeed.get() * Math.PI / 5.0;
            var12 = Math.cos(var16) * var10;
            var14 = Math.sin(var16) * var10;
         } else {
            var12 = Math.sin(var8) * var10 * 0.7 - Math.cos(var8) * var10 * 0.7;
            var14 = -Math.cos(var8) * var10 * 0.7 - Math.sin(var8) * var10 * 0.7;
         }

         double var19 = !this.flies() ? 0.0 : (this.mode.get() == Pet.Mode.CREATURE ? 1.05 : 1.3);
         return new class_243(var2 + var12, var4 + var19, var6 + var14);
      }
   }

   private static class_243 ground(class_243 var0) {
      class_2338 var1 = class_2338.method_49637(var0.field_1352, var0.field_1351 + 0.5, var0.field_1350);

      for (int var2 = 0; var2 < 4 && mc.field_1687.method_8320(var1.method_10074()).method_26215(); var2++) {
         var1 = var1.method_10074();
      }

      return new class_243(var0.field_1352, var1.method_10264(), var0.field_1350);
   }

   private static float wrap(float var0) {
      float var1 = var0 % 360.0F;
      if (var1 >= 180.0F) {
         var1 -= 360.0F;
      }

      if (var1 < -180.0F) {
         var1 += 360.0F;
      }

      return var1;
   }

   @Override
   public void onTick() {
      int var1 = mc.field_1724.field_6235;
      if (this.reactToDamage.get() && var1 > 0 && this.lastHurtTime == 0) {
         this.hurtAnim = 12;
         this.idleTicks = 0;
      }

      this.lastHurtTime = var1;
      if (this.hurtAnim > 0) {
         this.hurtAnim--;
      }

      double var2 = Math.abs(mc.field_1724.method_23317() - mc.field_1724.field_6038) + Math.abs(mc.field_1724.method_23321() - mc.field_1724.field_5989);
      float var4 = mc.field_1724.method_36454();
      if (!(var2 > 0.003) && !(Math.abs(wrap(var4 - this.lastPlayerYaw)) > 1.0F)) {
         this.idleTicks++;
      } else {
         this.idleTicks = 0;
      }

      this.lastPlayerYaw = var4;
      if (this.mode.get() == Pet.Mode.MOB) {
         if (this.mob == null || this.mob.method_31481()) {
            this.spawnMob();
            return;
         }

         class_243 var5 = this.targetPos(1.0F);
         if (var5.method_1025(this.mob.method_73189()) > 900.0) {
            this.mob.method_5808(var5.field_1352, var5.field_1351, var5.field_1350, this.mob.method_36454(), 0.0F);
         }

         double var6 = this.hurtAnim > 6 ? (12 - this.hurtAnim) * 0.12 : (this.hurtAnim > 0 ? this.hurtAnim * 0.12 : 0.0);
         if (this.shoulder()) {
            var6 = 0.0;
         } else if (this.kind.get().flies()) {
            var5 = var5.method_1031(0.0, Math.sin(mc.field_1724.field_6012 / 8.0) * 0.15, 0.0);
         } else {
            var5 = ground(var5);
         }

         class_243 var8 = var5.method_1020(this.mob.method_73189());
         float var9 = var8.method_37268() > 0.01 && !this.shoulder()
            ? (float)Math.toDegrees(Math.atan2(var8.field_1350, var8.field_1352)) - 90.0F
            : mc.field_1724.method_73188();
         this.mob.method_66246(var5.method_1031(0.0, var6, 0.0), var9, 0.0F);
         this.mob.method_5847(var9);
         if (this.mob instanceof class_1309 var10) {
            var10.method_5636(var9);
         }
      } else {
         if (this.mode.get() == Pet.Mode.IMAGE && !this.textureLoaded && this.loadedFile == null && mc.field_1724.field_6012 % 100 == 0) {
            this.reloadImage();
         }

         class_243 var14 = this.targetPos(1.0F);
         boolean var15 = this.mode.get() == Pet.Mode.CREATURE && !this.creature.get().flies && !this.shoulder();
         if (var15) {
            var14 = ground(var14);
         }

         if (this.pos == null || this.pos.method_1025(var14) > 900.0) {
            this.pos = var14;
            this.lastYaw = this.yaw = mc.field_1724.method_73188();
         }

         this.lastPos = this.pos;
         this.pos = this.shoulder() ? var14 : this.pos.method_35590(var14, var15 ? 0.22 : 0.18);
         double var7 = this.pos.field_1352 - this.lastPos.field_1352;
         double var16 = this.pos.field_1350 - this.lastPos.field_1350;
         double var17 = Math.sqrt(var7 * var7 + var16 * var16);
         float var13;
         if (this.shoulder()) {
            var13 = mc.field_1724.method_73188();
         } else if (var17 > 0.025) {
            var13 = (float)Math.toDegrees(Math.atan2(var16, var7)) - 90.0F;
         } else {
            var13 = (float)Math.toDegrees(Math.atan2(mc.field_1724.method_23321() - this.pos.field_1350, mc.field_1724.method_23317() - this.pos.field_1352))
               - 90.0F;
         }

         this.lastYaw = this.yaw;
         this.yaw = this.yaw + wrap(var13 - this.yaw) * (this.shoulder() ? 0.5F : 0.2F);
         this.lastWalk = this.walk;
         this.walk = this.walk + Math.min(0.6, var17 * 9.0);
         this.walkAmt = this.walkAmt * 0.8 + Math.min(1.0, var17 / 0.12) * 0.2;
      }
   }

   private boolean asleep() {
      return this.sleep.get() && this.mode.get() == Pet.Mode.CREATURE && this.idleTicks > 600 && this.hurtAnim == 0;
   }

   @Override
   public void onRender3D(Render3D var1) {
      if (this.mode.get() != Pet.Mode.MOB && this.pos != null && this.lastPos != null) {
         float var2 = var1.tickDelta();
         class_243 var3 = this.shoulder()
            ? this.targetPos(var2)
            : new class_243(
               class_3532.method_16436(var2, this.lastPos.field_1352, this.pos.field_1352),
               class_3532.method_16436(var2, this.lastPos.field_1351, this.pos.field_1351),
               class_3532.method_16436(var2, this.lastPos.field_1350, this.pos.field_1350)
            );
         if (this.mode.get() == Pet.Mode.CREATURE) {
            this.renderCreature(var1, var3, var2);
         } else if (this.textureLoaded) {
            this.renderImage(var1, var3, var2);
         }
      }
   }

   private void renderImage(Render3D var1, class_243 var2, float var3) {
      double var4 = (mc.field_1724.field_6012 + var3) / 10.0;
      if (!this.shoulder()) {
         var2 = var2.method_1031(0.0, Math.sin(var4) * 0.1, 0.0);
      }

      float var6 = 0.0F;
      int var7 = -1;
      if (this.hurtAnim > 0) {
         float var8 = this.hurtAnim / 12.0F;
         var2 = var2.method_1031(0.0, Math.sin(var8 * Math.PI) * 0.5, 0.0);
         var6 = (float)(Math.sin(this.hurtAnim * 1.7) * 0.3 * var8);
         int var9 = (int)(255.0F * (1.0F - 0.6F * var8));
         var7 = -65536 | var9 << 8 | var9;
      }

      long var15 = this.frameMs > 0 ? this.frameMs : 1000 / Math.max(1, this.fps.get());
      int var10 = this.frames > 1 ? (int)(System.currentTimeMillis() / Math.max(10L, var15) % this.frames) : 0;
      float var11 = (float)var10 / this.frames;
      float var12 = (float)(var10 + 1) / this.frames;
      int var13 = this.fullBright.get() ? 15728880 : class_761.method_23794((class_1920)mc.field_1687, class_2338.method_49638(var2));
      float var14 = this.size.getFloat() * (this.shoulder() ? 0.5F : 1.0F);
      var1.billboard(TEXTURE_ID, var2, var14 * this.aspect, var14, var11, var12, var7, var13, var6);
      this.nameTag(var1, var2.method_1031(0.0, var14 * 0.5 + 0.3, 0.0));
   }

   private void nameTag(Render3D var1, class_243 var2) {
      if (!this.petName.get().isBlank()) {
         var1.text(this.petName.get(), var2, -1, 0.8F);
      }
   }

   private void renderCreature(Render3D var1, class_243 var2, float var3) {
      Pet.Creature var4 = this.creature.get();
      double var5 = (mc.field_1724.field_6012 + var3) / 20.0;
      boolean var7 = this.asleep();
      double var8 = this.size.get() * var4.scale * (this.shoulder() ? 0.5 : 1.0);
      if (var4.flies && !this.shoulder()) {
         var2 = var2.method_1031(0.0, Math.sin(var5 * (var7 ? 0.9 : 2.2)) * (var7 ? 0.03 : 0.07) - (var7 ? 0.25 : 0.0), 0.0);
      }

      int var10 = -1;
      if (this.hurtAnim > 0) {
         float var11 = (this.hurtAnim - var3) / 12.0F;
         var11 = Math.max(0.0F, var11);
         var2 = var2.method_1031(0.0, Math.sin(var11 * Math.PI) * 0.4 * var8, 0.0);
         int var12 = (int)(255.0F * (1.0F - 0.65F * var11));
         var10 = -65536 | var12 << 8 | var12;
      }

      double var27 = (var5 + var4.ordinal() * 0.77) % 3.7;
      this.eyeOpen = var7 ? 0.12 : (var27 < 0.13 ? 0.12 : 1.0);
      this.renderYaw = this.lastYaw + wrap(this.yaw - this.lastYaw) * var3;
      this.r = var1;
      double var13 = this.walkAmt;
      double var15 = class_3532.method_16436(var3, this.lastWalk, this.walk);
      Shapes var17 = new Shapes(var1);
      var17.begin(var2, this.renderYaw, var8);
      var17.tint = var10;
      int var18 = this.mainColor(var4, var5);
      switch (var4) {
         case GHOST:
            this.ghost(var17, var5, var18, var13);
            break;
         case DRAGON:
            this.dragon(var17, var5, var18, var13);
            break;
         case DRONE:
            this.drone(var17, var5, var18, var13);
            break;
         case UFO:
            this.ufo(var17, var5, var18, var13);
            break;
         case JELLYFISH:
            this.jellyfish(var17, var5, var18);
            break;
         case FAIRY:
            this.fairy(var17, var5, var18, var13);
            break;
         case PENGUIN:
            this.penguin(var17, var5, var18, var13, var15);
            break;
         case ROBOT:
            this.robot(var17, var5, var18, var13, var15);
            break;
         case EYEBALL:
            this.eyeball(var17, var5, var18);
            break;
         case PLANET:
            this.planet(var17, var5, var18);
            break;
         case SNAKE:
            this.snake(var17, var5, var18, var13, var15);
            break;
         case DUCK:
            this.duck(var17, var5, var18, var13, var15);
            break;
         case WISP:
            this.wisp(var17, var5, var18, var13);
      }

      double var19 = var4.height * var8;
      this.nameTag(var1, var2.method_1031(0.0, var19 + 0.3, 0.0));
      if (var7) {
         for (int var21 = 0; var21 < 3; var21++) {
            double var22 = (var5 * 0.35 + var21 / 3.0) % 1.0;
            class_243 var24 = var17.world(-0.18 - var22 * 0.25, var4.height + 0.05 + var22 * 0.55, 0.0);
            int var25 = (int)(255.0 * Math.min(1.0, (1.0 - var22) * 2.0));
            var1.text(var21 == 1 ? "Z" : "z", var24, Math.max(4, var25) << 24 | 11587583, (float)(0.45 + var22 * 0.5));
         }
      }
   }

   private int mainColor(Pet.Creature var1, double var2) {
      return switch ((Pet.ColorMode)this.colorMode.get()) {
         case CLASSIC -> var1.color;
         case CUSTOM -> this.color.get() | 0xFF000000;
         case RAINBOW -> Color.HSBtoRGB((float)(var2 * 0.08 % 1.0), 0.65F, 1.0F) | 0xFF000000;
      };
   }

   private static int shade(int var0, float var1) {
      return var0 & 0xFF000000
         | Math.min(255, (int)((var0 >> 16 & 0xFF) * var1)) << 16
         | Math.min(255, (int)((var0 >> 8 & 0xFF) * var1)) << 8
         | Math.min(255, (int)((var0 & 0xFF) * var1));
   }

   private static int blend(int var0, int var1, double var2) {
      var2 = Math.max(0.0, Math.min(1.0, var2));
      int var4 = (int)((var0 >> 16 & 0xFF) * (1.0 - var2) + (var1 >> 16 & 0xFF) * var2);
      int var5 = (int)((var0 >> 8 & 0xFF) * (1.0 - var2) + (var1 >> 8 & 0xFF) * var2);
      int var6 = (int)((var0 & 0xFF) * (1.0 - var2) + (var1 & 0xFF) * var2);
      return 0xFF000000 | var4 << 16 | var5 << 8 | var6;
   }

   private static double[] p(double var0, double var2, double var4) {
      return new double[]{var0, var2, var4};
   }

   private void dot(Shapes var1, double var2, double var4, double var6, double var8, int var10) {
      var1.sphere(var2, var4, var6, var8, var8 * 1.25 * this.eyeOpen, var8 * 0.55, var10, var10);
      if (this.eyeOpen > 0.5 && var8 > 0.012) {
         var1.sphere(var2 + var8 * 0.3, var4 + var8 * 0.45, var6 + var8 * 0.45, var8 * 0.32, var8 * 0.32, var8 * 0.2, -1, -1);
      }
   }

   private void ghost(Shapes var1, double var2, int var4, double var5) {
      int var7 = shade(var4, 0.84F);
      int var8 = -15066578;
      var1.tilt(0.5, var5 * 14.0, Math.sin(var2 * 1.7) * 6.0);
      var1.alpha = 228;
      var1.dome(0.0, 0.55, 0.0, 0.3, 0.32, 0.28, var4, var4);
      byte var9 = 16;

      for (int var10 = 0; var10 < var9; var10++) {
         double var11 = (Math.PI * 2) * var10 / var9;
         double var13 = (Math.PI * 2) * (var10 + 1) / var9;
         double var15 = 0.12 + Math.sin(var11 * 4.0 + var2 * 5.0) * 0.055;
         double var17 = 0.12 + Math.sin(var13 * 4.0 + var2 * 5.0) * 0.055;
         double var19 = 1.1 + var5 * 0.08;
         var1.quad(
            p(Math.sin(var11) * 0.3, 0.55, Math.cos(var11) * 0.28),
            p(Math.sin(var13) * 0.3, 0.55, Math.cos(var13) * 0.28),
            p(Math.sin(var13) * 0.3 * var19, var17, Math.cos(var13) * 0.28 * var19 - var5 * 0.08),
            p(Math.sin(var11) * 0.3 * var19, var15, Math.cos(var11) * 0.28 * var19 - var5 * 0.08),
            var10 % 2 == 0 ? var4 : var7,
            0.85F
         );
      }

      double var21 = Math.sin(var2 * 3.0) * 0.05;
      var1.sphere(0.31, 0.38 + var21, 0.04, 0.075, 0.1, 0.07, var4, var7);
      var1.sphere(-0.31, 0.38 - var21, 0.04, 0.075, 0.1, 0.07, var4, var7);
      var1.alpha = 255;
      this.dot(var1, 0.1, 0.61, 0.25, 0.05, var8);
      this.dot(var1, -0.1, 0.61, 0.25, 0.05, var8);
      double var12 = 0.03 + 0.015 * Math.sin(var2 * 2.0);
      var1.sphere(0.0, 0.475, 0.278, 0.038, var12, 0.018, var8, var8);
      var1.sphere(0.17, 0.52, 0.225, 0.04, 0.022, 0.015, -24396, -24396);
      var1.sphere(-0.17, 0.52, 0.225, 0.04, 0.022, 0.015, -24396, -24396);
   }

   private void dragon(Shapes var1, double var2, int var4, double var5) {
      int var7 = shade(var4, 0.68F);
      int var8 = shade(var4, 0.85F);
      int var9 = -794740;
      int var10 = -659236;
      double var11 = Math.sin(var2 * 7.0);
      var1.tilt(0.45, var5 * 10.0 - var11 * 3.0, Math.sin(var2 * 1.1) * 4.0);
      var1.sphere(0.0, 0.45, 0.0, 0.17, 0.17, 0.27, var4, var9);

      for (int var13 = 0; var13 < 4; var13++) {
         double var14 = 0.14 - var13 * 0.1;
         double var16 = 0.61 - var13 * 0.012;
         var1.tri(p(0.0, var16 - 0.01, var14 + 0.05), p(0.0, var16 - 0.01, var14 - 0.05), p(0.0, var16 + 0.08 - var13 * 0.012, var14 - 0.04), var7, 1.0F);
      }

      double var30 = Math.sin(var2 * 2.0) * 0.02;
      var1.rod(0.0, 0.5, 0.2, 0.0, 0.63 + var30, 0.33, 0.075, var4);
      var1.sphere(0.0, 0.67 + var30, 0.36, 0.13, 0.12, 0.14, var4, var4);
      var1.box(0.0, 0.635 + var30, 0.52, 0.08, 0.05, 0.08, var4);
      var1.box(0.0, 0.58 + var30 - (this.hurtAnim > 0 ? 0.02 : 0.0), 0.52, 0.075, 0.018, 0.075, var9);
      var1.box(0.035, 0.67 + var30, 0.601, 0.012, 0.01, 0.004, var7);
      var1.box(-0.035, 0.67 + var30, 0.601, 0.012, 0.01, 0.004, var7);
      var1.sphere(0.085, 0.715 + var30, 0.44, 0.042, 0.042 * this.eyeOpen, 0.03, -6092, -6092);
      var1.sphere(-0.085, 0.715 + var30, 0.44, 0.042, 0.042 * this.eyeOpen, 0.03, -6092, -6092);
      if (this.eyeOpen > 0.5) {
         var1.box(0.1, 0.715 + var30, 0.468, 0.008, 0.028, 0.006, -15658735);
         var1.box(-0.1, 0.715 + var30, 0.468, 0.008, 0.028, 0.006, -15658735);
      }

      var1.rod(0.06, 0.76 + var30, 0.33, 0.1, 0.9 + var30, 0.22, 0.02, var10);
      var1.rod(-0.06, 0.76 + var30, 0.33, -0.1, 0.9 + var30, 0.22, 0.02, var10);
      double var15 = var11 * 38.0;

      for (byte var17 = -1; var17 <= 1; var17 += 2) {
         var1.part(var17 * 0.12, 0.56, 0.02, 0.0, 0.0, var17 * (var15 + 12.0));
         double[] var18 = p(var17 * 0.12, 0.57, 0.15);
         double[] var19 = p(var17 * 0.64, 0.72, -0.02);
         double[] var20 = p(var17 * 0.48, 0.56, -0.24);
         double[] var21 = p(var17 * 0.3, 0.55, -0.2);
         double[] var22 = p(var17 * 0.12, 0.55, -0.13);
         var1.tri(var18, var19, var20, var8, 0.95F);
         var1.tri(var18, var20, var21, var8, 0.9F);
         var1.tri(var18, var21, var22, var8, 0.85F);
         var1.rod(var18[0], var18[1], var18[2], var19[0], var19[1], var19[2], 0.014, var7);
         var1.rod(var19[0], var19[1], var19[2], var20[0], var20[1], var20[2], 0.008, var7);
         var1.endPart();
      }

      double var31 = 0.0;
      double var32 = 0.42;
      double var33 = -0.22;

      for (int var23 = 1; var23 <= 6; var23++) {
         double var24 = Math.sin(var2 * 3.0 - var23 * 0.7) * 0.035 * var23;
         double var26 = 0.42 - var23 * 0.022 + Math.sin(var2 * 2.0 - var23 * 0.5) * 0.01 * var23;
         double var28 = -0.22 - var23 * 0.085;
         var1.rod(var31, var32, var33, var24, var26, var28, 0.07 - var23 * 0.009, var4);
         var31 = var24;
         var32 = var26;
         var33 = var28;
      }

      var1.tri(p(var31 - 0.07, var32, var33), p(var31 + 0.07, var32, var33), p(var31, var32, var33 - 0.12), var7, 1.0F);
      var1.tri(p(var31, var32 - 0.06, var33), p(var31, var32 + 0.06, var33), p(var31, var32, var33 - 0.12), var7, 0.85F);

      for (byte var34 = -1; var34 <= 1; var34 += 2) {
         double var36 = Math.sin(var2 * 3.0 + var34) * 0.02;
         var1.box(var34 * 0.1, 0.3 + var36, 0.13, 0.035, 0.06, 0.04, var4);
         var1.box(var34 * 0.1, 0.3 - var36, -0.12, 0.04, 0.065, 0.045, var4);
      }

      if (this.hurtAnim > 0) {
         for (int var35 = 0; var35 < 7; var35++) {
            double var37 = (var2 * 2.5 + var35 / 7.0) % 1.0;
            double var38 = 0.02 + var37 * 0.07;
            var1.box(
               Math.sin(var35 * 2.3) * var37 * 0.08,
               0.6 + var30 + Math.cos(var35 * 1.7) * var37 * 0.06,
               0.62 + var37 * 0.55,
               var38,
               var38,
               var38,
               blend(-10496, -1683200, var37)
            );
         }
      }
   }

   private void drone(Shapes var1, double var2, int var4, double var5) {
      int var7 = -13947082;
      int var8 = -12827061;
      int var9 = -6379597;
      var1.tilt(0.3, var5 * 18.0, Math.sin(var2 * 1.3) * 3.0);
      var1.box(0.0, 0.3, 0.0, 0.13, 0.05, 0.15, var7);
      var1.box(0.0, 0.365, -0.01, 0.09, 0.022, 0.1, var4);
      var1.box(0.0, 0.3, 0.152, 0.08, 0.018, 0.004, var4);
      var1.sphere(0.0, 0.225, 0.1, 0.05, 0.05, 0.05, -15395558, -15395558);
      var1.sphere(0.0, 0.225, 0.143, 0.025, 0.025, 0.012, -13607681, -13607681);

      for (byte var10 = -1; var10 <= 1; var10 += 2) {
         for (byte var11 = -1; var11 <= 1; var11 += 2) {
            double var12 = var10 * 0.26;
            double var14 = var11 * 0.26;
            var1.rod(var10 * 0.08, 0.31, var11 * 0.08, var12, 0.31, var14, 0.018, var8);
            var1.cylinder(var12, 0.3, var14, 0.035, 0.035, 0.06, var9, true);
            double var16 = var2 * 2400.0 * (var10 * var11 > 0 ? 1 : -1);
            var1.part(var12, 0.37, var14, 0.0, var16, 0.0);
            var1.box(var12, 0.372, var14, 0.14, 0.004, 0.016, -3090722);
            var1.endPart();
            var1.alpha = 55;
            var1.disc(var12, 0.37, var14, 0.145, -1);
            var1.alpha = 255;
         }

         var1.rod(var10 * 0.08, 0.25, 0.08, var10 * 0.09, 0.18, 0.08, 0.01, var8);
         var1.rod(var10 * 0.08, 0.25, -0.08, var10 * 0.09, 0.18, -0.08, 0.01, var8);
         var1.rod(var10 * 0.09, 0.18, -0.13, var10 * 0.09, 0.18, 0.14, 0.012, var8);
      }

      boolean var18 = (int)(var2 * 2.0) % 2 == 0;
      var1.box(0.1, 0.3, 0.152, 0.014, 0.012, 0.006, var18 ? -12517536 : -15712232);
      var1.box(-0.1, 0.3, 0.152, 0.014, 0.012, 0.006, var18 ? -12517536 : -15712232);
      var1.box(0.1, 0.3, -0.152, 0.014, 0.012, 0.006, !var18 ? -49088 : -12578800);
      var1.box(-0.1, 0.3, -0.152, 0.014, 0.012, 0.006, !var18 ? -49088 : -12578800);
   }

   private void ufo(Shapes var1, double var2, int var4, double var5) {
      int var7 = shade(var4, 0.62F);
      int var8 = blend(var4, -1, 0.35);
      var1.tilt(0.3, var5 * 12.0, Math.sin(var2 * 1.5) * 6.0);
      var1.cylinder(0.0, 0.22, 0.0, 0.16, 0.46, 0.08, var7, true);
      var1.cylinder(0.0, 0.3, 0.0, 0.46, 0.45, 0.03, var4, false);
      var1.cylinder(0.0, 0.33, 0.0, 0.45, 0.24, 0.06, var8, true);
      int var9 = (int)(160.0 + 90.0 * Math.sin(var2 * 5.0));
      var1.disc(0.0, 0.215, 0.0, 0.12, 0xFF000000 | var9 << 8 | 0xFF);

      for (int var10 = 0; var10 < 10; var10++) {
         double var11 = var10 / 10.0 * Math.PI * 2.0 + var2 * 1.8;
         float var13 = (float)((var10 / 10.0 + var2 * 0.3) % 1.0);
         var1.box(Math.sin(var11) * 0.455, 0.315, Math.cos(var11) * 0.455, 0.022, 0.018, 0.022, Color.HSBtoRGB(var13, 0.8F, 1.0F) | 0xFF000000);
      }

      int var14 = -8586116;
      var1.sphere(0.0, 0.47, 0.0, 0.08, 0.085, 0.08, var14, -10762149);
      var1.sphere(0.035, 0.49, 0.066, 0.024, 0.034 * this.eyeOpen, 0.014, -15658735, -15658735);
      var1.sphere(-0.035, 0.49, 0.066, 0.024, 0.034 * this.eyeOpen, 0.014, -15658735, -15658735);
      var1.rod(0.03, 0.54, 0.0, 0.05, 0.6, 0.0, 0.006, var14);
      var1.rod(-0.03, 0.54, 0.0, -0.05, 0.6, 0.0, 0.006, var14);
      var1.sphere(0.05, 0.605, 0.0, 0.015, 0.015, 0.015, -1, -1);
      var1.sphere(-0.05, 0.605, 0.0, 0.015, 0.015, 0.015, -1, -1);
      var1.alpha = 105;
      var1.dome(0.0, 0.37, 0.0, 0.2, 0.26, 0.2, -6297345, -6297345);
      if (var5 < 0.3) {
         var1.alpha = (int)(55.0 * (1.0 - var5 / 0.3) * (0.7 + 0.3 * Math.sin(var2 * 6.0)));
         var1.cylinder(0.0, -1.0, 0.0, 0.42, 0.13, 1.2, -8323152, false);
      }

      var1.alpha = 255;
   }

   private void jellyfish(Shapes var1, double var2, int var4) {
      double var5 = Math.sin(var2 * 3.2);
      double var7 = 0.27 * (1.0 + 0.1 * var5);
      double var9 = 0.21 * (1.0 - 0.12 * var5);
      int var11 = blend(var4, -1, 0.5);
      var1.sphere(0.0, 0.55, 0.0, 0.09, 0.07, 0.09, -6409, -6409);
      var1.alpha = 205;

      for (int var12 = 0; var12 < 8; var12++) {
         double var13 = var12 / 8.0 * Math.PI * 2.0 + 0.2;
         double var15 = Math.sin(var13) * var7 * 0.82;
         double var17 = Math.cos(var13) * var7 * 0.82;
         double var19 = var15;
         double var21 = 0.5;
         double var23 = var17;

         for (int var25 = 1; var25 <= 7; var25++) {
            double var26 = Math.sin(var2 * 2.2 + var25 * 0.6 + var12) * 0.022 * var25;
            double var28 = var15 * (1.0 - var25 * 0.035) + var26 * Math.cos(var13);
            double var30 = 0.5 - var25 * 0.075 - (1.0 - var5) * 0.01 * var25;
            double var32 = var17 * (1.0 - var25 * 0.035) - var26 * Math.sin(var13);
            var1.rod(var19, var21, var23, var28, var30, var32, 0.011, blend(var4, -1, var25 / 9.0));
            var19 = var28;
            var21 = var30;
            var23 = var32;
         }
      }

      for (int var34 = 0; var34 < 4; var34++) {
         double var36 = var34 / 4.0 * Math.PI * 2.0 + var2 * 0.4;
         double var38 = 0.0;
         double var39 = 0.48;
         double var40 = 0.0;

         for (int var41 = 1; var41 <= 5; var41++) {
            double var22 = Math.sin(var2 * 1.8 + var41 * 0.9 + var34 * 1.3) * 0.02 * var41;
            double var24 = Math.sin(var36) * (0.03 + var41 * 0.01) + var22;
            double var42 = 0.48 - var41 * 0.07;
            double var43 = Math.cos(var36) * (0.03 + var41 * 0.01) - var22;
            var1.rod(var38, var39, var40, var24, var42, var43, 0.026 - var41 * 0.003, var11);
            var38 = var24;
            var39 = var42;
            var40 = var43;
         }
      }

      var1.alpha = 170;
      var1.dome(0.0, 0.5, 0.0, var7, var9, var7, var4, var11);

      for (int var35 = 0; var35 < 12; var35++) {
         double var37 = var35 / 12.0 * Math.PI * 2.0;
         var1.sphere(Math.sin(var37) * var7, 0.5, Math.cos(var37) * var7, 0.035, 0.022, 0.035, var4, var4);
      }

      var1.alpha = 255;
      this.dot(var1, 0.075, 0.585, var7 * 0.9, 0.028, -15066578);
      this.dot(var1, -0.075, 0.585, var7 * 0.9, 0.028, -15066578);
   }

   private void fairy(Shapes var1, double var2, int var4, double var5) {
      short var7 = -8244;
      short var8 = -10929;
      short var9 = -2385;
      var1.tilt(0.4, var5 * 20.0, Math.sin(var2 * 2.0) * 5.0);
      double var10 = Math.sin(var2 * 3.0) * 0.02;
      var1.rod(0.025, 0.2, 0.0, 0.03, 0.08, 0.02 + var10, 0.013, var7);
      var1.rod(-0.025, 0.2, 0.0, -0.03, 0.09, 0.02 - var10, 0.013, var7);
      var1.cylinder(0.0, 0.18, 0.0, 0.1, 0.035, 0.17, var4, true);
      var1.rod(0.0, 0.33, 0.0, 0.0, 0.41, 0.0, 0.032, var4);
      double var12 = Math.sin(var2 * 4.0) * 0.03;
      var1.rod(0.035, 0.39, 0.0, 0.09, 0.3 + var12, 0.03, 0.012, var7);
      var1.rod(-0.035, 0.39, 0.0, -0.085, 0.45, 0.07, 0.012, var7);
      var1.rod(-0.085, 0.45, 0.07, -0.1, 0.58, 0.1, 0.008, -2646);
      int var14 = (int)(200.0 + 55.0 * Math.sin(var2 * 8.0));
      var1.sphere(-0.1, 0.59, 0.1, 0.025, 0.025, 0.025, -65536 | var14 << 8 | 128, -1);
      var1.sphere(0.0, 0.47, 0.0, 0.065, 0.065, 0.065, var7, var7);
      var1.dome(0.0, 0.475, -0.005, 0.073, 0.07, 0.073, var8, var8);
      var1.sphere(0.0, 0.46, -0.07, 0.035, 0.04, 0.035, var8, var8);
      var1.sphere(0.024, 0.472, 0.058, 0.011, 0.014 * this.eyeOpen, 0.006, -12965270, -12965270);
      var1.sphere(-0.024, 0.472, 0.058, 0.011, 0.014 * this.eyeOpen, 0.006, -12965270, -12965270);
      var1.sphere(0.04, 0.455, 0.052, 0.012, 0.007, 0.005, -24396, -24396);
      var1.sphere(-0.04, 0.455, 0.052, 0.012, 0.007, 0.005, -24396, -24396);
      double var15 = Math.sin(var2 * 22.0) * 28.0;

      for (int var17 = 0; var17 < 7; var17++) {
         double var18 = (var2 * 0.6 + var17 * 0.143) % 1.0;
         double var20 = var17 * 2.4 + var2 * 0.5;
         double var22 = 0.12 + var18 * 0.12;
         double var24 = 0.018 * (1.0 - var18);
         var1.box(
            Math.sin(var20) * var22,
            0.35 - var18 * 0.35,
            Math.cos(var20) * var22 - var18 * 0.1,
            var24,
            var24,
            var24,
            Color.HSBtoRGB((float)(0.12 + 0.05 * Math.sin(var17)), 0.5F, 1.0F) | 0xFF000000
         );
      }

      var1.alpha = 140;

      for (byte var26 = -1; var26 <= 1; var26 += 2) {
         var1.part(var26 * 0.02, 0.38, -0.03, 0.0, var26 * (var15 + 20.0), 0.0);
         double[] var27 = p(var26 * 0.02, 0.4, -0.03);
         var1.quad(var27, p(var26 * 0.19, 0.57, -0.07), p(var26 * 0.25, 0.46, -0.07), p(var26 * 0.05, 0.37, -0.04), -5052932, 1.0F);
         var1.quad(var27, p(var26 * 0.05, 0.36, -0.04), p(var26 * 0.17, 0.26, -0.06), p(var26 * 0.12, 0.22, -0.05), -3808257, 0.95F);
         var1.endPart();
      }

      var1.alpha = 22 + (int)(10.0 * Math.sin(var2 * 4.0));
      var1.sphere(0.0, 0.37, 0.0, 0.21, 0.25, 0.21, var9, var9);
      var1.alpha = 255;
   }

   private void penguin(Shapes var1, double var2, int var4, double var5, double var7) {
      int var9 = -657931;
      short var10 = -22746;
      double var11 = Math.sin(var7) * var5;
      var1.tilt(0.0, var5 * 6.0, var11 * 9.0 + Math.sin(var2 * 1.2) * 1.5);

      for (byte var13 = -1; var13 <= 1; var13 += 2) {
         double var14 = Math.max(0.0, Math.sin(var7 + var13 * Math.PI / 2.0)) * var5;
         var1.box(var13 * 0.08, 0.018 + var14 * 0.04, 0.06 + var14 * 0.02, 0.055, 0.018, 0.075, var10);
      }

      var1.sphere(0.0, 0.3, 0.0, 0.19, 0.28, 0.17, var4, var4);
      var1.sphere(0.0, 0.28, 0.045, 0.145, 0.23, 0.14, var9, var9);
      var1.sphere(0.0, 0.62, 0.01, 0.14, 0.13, 0.13, var4, var4);
      var1.sphere(0.0, 0.6, 0.07, 0.1, 0.08, 0.08, var9, var9);
      this.dot(var1, 0.045, 0.64, 0.13, 0.018, -15658735);
      this.dot(var1, -0.045, 0.64, 0.13, 0.018, -15658735);
      var1.box(0.0, 0.6, 0.165, 0.03, 0.017, 0.04, var10);
      var1.sphere(0.075, 0.585, 0.11, 0.02, 0.012, 0.01, -24907, -24907);
      var1.sphere(-0.075, 0.585, 0.11, 0.02, 0.012, 0.01, -24907, -24907);
      var1.cylinder(0.0, 0.48, 0.0, 0.152, 0.15, 0.04, -1754827, false);
      var1.box(-0.06, 0.4, 0.13, 0.025, 0.07, 0.012, -1754827);
      double var16 = 12.0 + Math.abs(var11) * 25.0 + (this.hurtAnim > 0 ? 50.0 : 0.0);

      for (byte var15 = -1; var15 <= 1; var15 += 2) {
         var1.part(var15 * 0.18, 0.48, 0.0, 0.0, 0.0, var15 * var16);
         var1.box(var15 * 0.2, 0.34, 0.0, 0.022, 0.14, 0.065, var4);
         var1.endPart();
      }
   }

   private void robot(Shapes var1, double var2, int var4, double var5, double var7) {
      int var9 = -13154481;
      int var10 = blend(var4, -1, 0.25);
      int var11 = -12525313;
      double var12 = Math.sin(var7) * 35.0 * var5;
      var1.tilt(0.0, 0.0, Math.sin(var7) * 3.0 * var5);

      for (byte var14 = -1; var14 <= 1; var14 += 2) {
         var1.part(var14 * 0.07, 0.22, 0.0, var14 * var12, 0.0, 0.0);
         var1.box(var14 * 0.07, 0.12, 0.0, 0.04, 0.1, 0.045, var9);
         var1.box(var14 * 0.07, 0.02, 0.025, 0.05, 0.02, 0.07, var4);
         var1.endPart();
      }

      double var19 = Math.abs(Math.sin(var7)) * 0.012 * var5;
      var1.box(0.0, 0.36 + var19, 0.0, 0.15, 0.13, 0.1, var4);
      var1.box(0.0, 0.38 + var19, 0.101, 0.09, 0.06, 0.004, var9);

      for (int var16 = 0; var16 < 3; var16++) {
         boolean var17 = (int)(var2 * 3.0 + var16) % 3 == 0;
         int var18 = var16 == 0 ? -49088 : (var16 == 1 ? -12517536 : -16736001);
         var1.box(-0.05 + var16 * 0.05, 0.4 + var19, 0.106, 0.014, 0.014, 0.003, var17 ? var18 : shade(var18, 0.35F));
      }

      var1.box(0.0, 0.35 + var19, 0.106, 0.07 * (0.55 + 0.45 * Math.sin(var2 * 2.0)), 0.008, 0.003, -10027162);

      for (byte var20 = -1; var20 <= 1; var20 += 2) {
         double var22 = this.hurtAnim > 0 ? -150.0 : -var20 * var12 + (this.asleep() ? 0.0 : Math.sin(var2 * 1.5) * 4.0);
         var1.part(var20 * 0.18, 0.46 + var19, 0.0, var22, 0.0, var20 * 6.0);
         var1.box(var20 * 0.19, 0.36 + var19, 0.0, 0.034, 0.11, 0.034, var10);
         var1.box(var20 * 0.19, 0.235 + var19, 0.0, 0.042, 0.02, 0.042, var9);
         var1.endPart();
      }

      var1.cylinder(0.0, 0.49 + var19, 0.0, 0.03, 0.03, 0.03, var9, false);
      double var21 = this.asleep() ? -0.03 : 0.0;
      var1.box(0.0, 0.6 + var19 + var21, 0.0, 0.12, 0.09, 0.1, var10);
      var1.box(0.0, 0.61 + var19 + var21, 0.101, 0.095, 0.035, 0.004, -15723496);
      var1.box(0.045, 0.612 + var19 + var21, 0.106, 0.022, 0.002 + 0.013 * this.eyeOpen, 0.003, var11);
      var1.box(-0.045, 0.612 + var19 + var21, 0.106, 0.022, 0.002 + 0.013 * this.eyeOpen, 0.003, var11);
      var1.box(0.125, 0.6 + var19 + var21, 0.0, 0.012, 0.035, 0.035, var9);
      var1.box(-0.125, 0.6 + var19 + var21, 0.0, 0.012, 0.035, 0.035, var9);
      var1.rod(0.0, 0.69 + var19 + var21, 0.0, Math.sin(var2 * 2.0) * 0.015, 0.8 + var19 + var21, -0.01, 0.008, var9);
      boolean var23 = (int)(var2 * 1.5) % 2 == 0;
      var1.sphere(Math.sin(var2 * 2.0) * 0.015, 0.82 + var19 + var21, -0.01, 0.025, 0.025, 0.025, var23 ? -49088 : -8388608, var23 ? -49088 : -8388608);
   }

   private void eyeball(Shapes var1, double var2, int var4) {
      class_243 var5 = var1.world(0.0, 0.4, 0.0);
      class_243 var6 = this.r.camera();
      double var7 = var6.field_1352 - var5.field_1352;
      double var9 = var6.field_1351 - var5.field_1351;
      double var11 = var6.field_1350 - var5.field_1350;
      float var13 = (float)Math.toDegrees(Math.atan2(var11, var7)) - 90.0F;
      double var14 = Math.max(-55.0, Math.min(55.0, (double)wrap(var13 - this.renderYaw)));
      double var16 = Math.max(-45.0, Math.min(45.0, -Math.toDegrees(Math.atan2(var9, Math.sqrt(var7 * var7 + var11 * var11)))));
      if (this.asleep()) {
         var14 = 0.0;
         var16 = 20.0;
      }

      var1.sphere(0.0, 0.4, 0.0, 0.24, 0.24, 0.24, -526345, -995112);

      for (int var18 = 0; var18 < 5; var18++) {
         double var19 = var18 * 1.26 + 0.4;
         double var21 = Math.sin(var19) * 0.2;
         double var23 = Math.cos(var19) * 0.2;
         var1.line(var21, 0.4 + var23, -0.13, var21 * 1.1, 0.4 + var23 * 1.05, -0.02, -3394765);
      }

      var1.part(0.0, 0.4, 0.0, var16, -var14, 0.0);
      double var28 = this.eyeOpen;
      var1.sphere(0.0, 0.4, 0.2, 0.11, 0.11 * var28, 0.055, var4, shade(var4, 0.8F));
      var1.sphere(0.0, 0.4, 0.245, 0.055, 0.055 * var28, 0.022, -16053493, -16053493);
      if (var28 > 0.5) {
         var1.sphere(0.03, 0.43, 0.262, 0.016, 0.016, 0.008, -1, -1);
      }

      var1.endPart();
      double var20 = Math.sin(var2 * (this.asleep() ? 2.0 : 9.0)) * 35.0;

      for (byte var22 = -1; var22 <= 1; var22 += 2) {
         var1.part(var22 * 0.2, 0.42, -0.03, 0.0, 0.0, var22 * (var20 + 15.0));
         double[] var29 = p(var22 * 0.2, 0.48, -0.03);
         double[] var24 = p(var22 * 0.56, 0.57, -0.08);
         double[] var25 = p(var22 * 0.47, 0.39, -0.07);
         double[] var26 = p(var22 * 0.34, 0.35, -0.05);
         double[] var27 = p(var22 * 0.2, 0.36, -0.03);
         var1.tri(var29, var24, var25, -11916198, 1.0F);
         var1.tri(var29, var25, var26, -11916198, 0.9F);
         var1.tri(var29, var26, var27, -11916198, 0.85F);
         var1.rod(var29[0], var29[1], var29[2], var24[0], var24[1], var24[2], 0.01, -14085860);
         var1.endPart();
      }
   }

   private void planet(Shapes var1, double var2, int var4) {
      int var5 = shade(var4, 0.7F);
      int var6 = blend(var4, -1, 0.3);
      var1.tilt(0.4, 0.0, 18.0);
      var1.sphere(0.0, 0.4, 0.0, 0.2, 0.2, 0.2, var4, var5);
      var1.cylinder(0.0, 0.44, 0.0, 0.197, 0.19, 0.025, var5, false);
      var1.cylinder(0.0, 0.33, 0.0, 0.19, 0.198, 0.02, var6, false);
      this.dot(var1, 0.06, 0.42, 0.185, 0.02, -14277082);
      this.dot(var1, -0.06, 0.42, 0.185, 0.02, -14277082);
      var1.sphere(0.0, 0.37, 0.198, 0.028, 0.012, 0.01, -9422567, -9422567);
      var1.part(0.0, 0.4, 0.0, 24.0, 0.0, 0.0);
      var1.alpha = 195;
      var1.ring(0.0, 0.4, 0.0, 0.27, 0.34, -1517136);
      var1.ring(0.0, 0.4, 0.0, 0.35, 0.41, -3557750);
      var1.alpha = 255;
      var1.endPart();
      double var7 = var2 * 1.6;
      var1.sphere(Math.sin(var7) * 0.55, 0.4 + Math.cos(var7) * 0.1, Math.cos(var7) * 0.55, 0.055, 0.055, 0.055, -2434342, -6381922);

      for (int var9 = 0; var9 < 3; var9++) {
         double var10 = var2 * 0.7 + var9 * 2.1;
         double var12 = 0.01 + 0.012 * Math.abs(Math.sin(var2 * 3.0 + var9));
         var1.box(Math.sin(var10) * 0.34, 0.62 + Math.sin(var10 * 2.0) * 0.05, Math.cos(var10) * 0.34, var12, var12, var12, -2398);
      }
   }

   private void snake(Shapes var1, double var2, int var4, double var5, double var7) {
      int var9 = shade(var4, 0.7F);
      int var10 = -1643876;
      double var11 = 0.4 + var5 * 0.6;
      double var13 = var7 * 1.2 + var2 * 1.5 * (1.0 - var5);

      for (int var15 = 10; var15 >= 0; var15--) {
         double var16 = 0.22 - var15 * 0.075;
         double var18 = Math.sin(var13 - var15 * 0.9) * 0.06 * var11 * (var15 / 10.0 + 0.3);
         double var20 = 0.07 - var15 * 0.0045;
         var1.sphere(var18, var20, var16, var20, var20 * 0.85, var20 * 1.2, var15 % 3 == 0 ? var9 : var4, var10);
      }

      double var23 = Math.sin(var13) * 0.06 * var11 * 0.3;
      double var17 = this.asleep() ? 0.09 : 0.17;
      var1.rod(var23, 0.07, 0.22, 0.0, var17 - 0.03, 0.29, 0.055, var4);
      var1.sphere(0.0, var17, 0.32, 0.075, 0.058, 0.09, var4, var10);
      var1.sphere(0.045, var17 + 0.03, 0.36, 0.018, 0.02 * this.eyeOpen, 0.014, -5317, -5317);
      var1.sphere(-0.045, var17 + 0.03, 0.36, 0.018, 0.02 * this.eyeOpen, 0.014, -5317, -5317);
      if (this.eyeOpen > 0.5) {
         var1.box(0.05, var17 + 0.03, 0.372, 0.003, 0.012, 0.002, -16777216);
         var1.box(-0.05, var17 + 0.03, 0.372, 0.003, 0.012, 0.002, -16777216);
      }

      double var19 = var2 * 0.7 % 1.0;
      if (var19 < 0.15 && !this.asleep()) {
         double var21 = Math.sin(var19 / 0.15 * Math.PI) * 0.09;
         var1.rod(0.0, var17 - 0.01, 0.4, 0.0, var17 - 0.01, 0.4 + var21, 0.006, -1754827);
         var1.rod(0.0, var17 - 0.01, 0.4 + var21, 0.015, var17 - 0.01, 0.42 + var21, 0.005, -1754827);
         var1.rod(0.0, var17 - 0.01, 0.4 + var21, -0.015, var17 - 0.01, 0.42 + var21, 0.005, -1754827);
      }
   }

   private void duck(Shapes var1, double var2, int var4, double var5, double var7) {
      short var9 = -28928;
      int var10 = shade(var4, 0.9F);
      double var11 = Math.sin(var7) * var5;
      var1.tilt(0.0, 0.0, var11 * 10.0);

      for (byte var13 = -1; var13 <= 1; var13 += 2) {
         double var14 = Math.max(0.0, Math.sin(var7 + var13 * Math.PI / 2.0)) * var5;
         var1.box(var13 * 0.07, 0.015 + var14 * 0.035, 0.04, 0.045, 0.012, 0.06, var9);
      }

      var1.sphere(0.0, 0.2, -0.02, 0.19, 0.15, 0.24, var4, var10);
      var1.rod(0.0, 0.25, -0.2, 0.0, 0.33, -0.29, 0.045, var4);
      double var17 = this.hurtAnim > 0 ? 35.0 : Math.abs(var11) * 15.0;

      for (byte var15 = -1; var15 <= 1; var15 += 2) {
         var1.part(var15 * 0.17, 0.27, -0.03, 0.0, 0.0, var15 * var17);
         var1.sphere(var15 * 0.17, 0.22, -0.03, 0.04, 0.08, 0.13, var10, var10);
         var1.endPart();
      }

      double var18 = Math.sin(var2 * 1.4) * 0.01;
      var1.sphere(0.0, 0.43 + var18, 0.1, 0.12, 0.12, 0.12, var4, var4);
      var1.sphere(0.0, 0.405 + var18, 0.23, 0.065, 0.026, 0.06, var9, var9);
      this.dot(var1, 0.055, 0.46 + var18, 0.2, 0.018, -15658735);
      this.dot(var1, -0.055, 0.46 + var18, 0.2, 0.018, -15658735);
   }

   private void wisp(Shapes var1, double var2, int var4, double var5) {
      int var7 = blend(var4, -1, 0.6);
      var1.tilt(0.3, var5 * 25.0, Math.sin(var2 * 3.0) * 6.0);
      var1.sphere(0.0, 0.36, 0.0, 0.1, 0.12, 0.1, var7, var7);

      for (int var8 = 0; var8 < 6; var8++) {
         double var9 = (var2 * 0.8 + var8 / 6.0) % 1.0;
         double var11 = 0.03 * (1.0 - var9);
         var1.alpha = (int)(255.0 * (1.0 - var9));
         var1.box(Math.sin(var8 * 2.1 + var2) * 0.1, 0.45 + var9 * 0.5, Math.cos(var8 * 1.7 + var2) * 0.1, var11, var11, var11, var7);
      }

      for (int var14 = 0; var14 < 3; var14++) {
         double var16 = Math.sin(var2 * 9.0 + var14 * 2.0) * 0.03;
         double var18 = 0.14 + var14 * 0.05;
         int var13 = blend(var4, -1, 0.3 - var14 * 0.1);
         var1.alpha = 150 - var14 * 35;
         var1.sphere(0.0, 0.33, 0.0, var18, var18 * 0.9, var18, var13, var13);
         var1.cylinder(0.0, 0.33, 0.0, var18, 0.0, 0.3 + var14 * 0.08 + var16, var13, false);
      }

      var1.alpha = 120;

      for (int var15 = 0; var15 < 4; var15++) {
         double var17 = var15 * Math.PI / 2.0 + 0.6;
         double var19 = 0.16 + 0.07 * Math.sin(var2 * 11.0 + var15 * 1.9);
         var1.cylinder(Math.sin(var17) * 0.13, 0.36, Math.cos(var17) * 0.13, 0.07, 0.0, var19, blend(var4, -1, 0.15), false);
      }

      var1.alpha = 255;
      this.dot(var1, 0.06, 0.37, 0.235, 0.022, -15918294);
      this.dot(var1, -0.06, 0.37, 0.235, 0.022, -15918294);
   }

   @Override
   public String getInfo() {
      return switch ((Pet.Mode)this.mode.get()) {
         case MOB -> this.kind.displayValue();
         case CREATURE -> this.creature.displayValue();
         case IMAGE -> this.loadedFile;
      };
   }

   public static enum ColorMode {
      CLASSIC,
      CUSTOM,
      RAINBOW;
   }

   public static enum Creature {
      GHOST(-1184769, true, 0.9, 1.0),
      DRAGON(-11751600, true, 0.95, 1.0),
      DRONE(-1754827, true, 0.45, 1.15),
      UFO(-5194043, true, 0.62, 1.0),
      JELLYFISH(-32816, true, 0.75, 1.0),
      FAIRY(-32568, true, 0.65, 1.35),
      PENGUIN(-14273992, false, 0.8, 1.0),
      ROBOT(-7297874, false, 0.86, 1.0),
      EYEBALL(-12615728, true, 0.66, 1.0),
      PLANET(-2056112, true, 0.62, 1.15),
      SNAKE(-10043542, false, 0.3, 1.25),
      DUCK(-10182, false, 0.58, 1.0),
      WISP(-11549705, true, 0.85, 1.05);

      final int color;
      final boolean flies;
      final double height;
      final double scale;

      private Creature(int nullxx, boolean nullxxx, double nullxxxx, double nullxxxxx) {
         this.color = nullxx;
         this.flies = nullxxx;
         this.height = nullxxxx;
         this.scale = nullxxxxx;
      }
   }

   public static enum Kind {
      CAT,
      FOX,
      AXOLOTL,
      ALLAY,
      PARROT,
      BEE,
      FROG,
      WOLF,
      CHICKEN,
      PIG,
      RABBIT,
      SNIFFER,
      ARMADILLO;

      class_1299<?> type() {
         return switch (this) {
            case CAT -> class_1299.field_16281;
            case FOX -> class_1299.field_17943;
            case AXOLOTL -> class_1299.field_28315;
            case ALLAY -> class_1299.field_38384;
            case PARROT -> class_1299.field_6104;
            case BEE -> class_1299.field_20346;
            case FROG -> class_1299.field_37419;
            case WOLF -> class_1299.field_6055;
            case CHICKEN -> class_1299.field_6132;
            case PIG -> class_1299.field_6093;
            case RABBIT -> class_1299.field_6140;
            case SNIFFER -> class_1299.field_42622;
            case ARMADILLO -> class_1299.field_47754;
         };
      }

      boolean flies() {
         return this == ALLAY || this == PARROT || this == BEE;
      }
   }

   public static enum Mode {
      MOB,
      CREATURE,
      IMAGE;
   }

   public static enum Place {
      FOLLOW,
      ORBIT,
      SHOULDER;
   }
}
