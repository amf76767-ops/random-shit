package dev.dihclient.modules.world;

import dev.dihclient.autobuild.BuildPlan;
import dev.dihclient.autobuild.BuildRuntime;
import dev.dihclient.autobuild.PlacementSolver;
import dev.dihclient.autobuild.Schematic;
import dev.dihclient.gui.SchematicBrowserScreen;
import dev.dihclient.hud.HudManager;
import dev.dihclient.module.Category;
import dev.dihclient.module.Module;
import dev.dihclient.render.Gfx;
import dev.dihclient.render.Render3D;
import dev.dihclient.setting.BoolSetting;
import dev.dihclient.setting.DoubleSetting;
import dev.dihclient.setting.EnumSetting;
import dev.dihclient.setting.IdListSetting;
import dev.dihclient.setting.IntSetting;
import dev.dihclient.util.KeyUtil;
import dev.dihclient.util.Notifications;
import dev.dihclient.util.RegistryUtil;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.class_1792;
import net.minecraft.class_2338;
import net.minecraft.class_2415;
import net.minecraft.class_2470;
import net.minecraft.class_332;

public class AutoBuild extends Module {
   public final EnumSetting<BuildRuntime.Mode> mode = this.mode(
      "Mode",
      "Auto: walks, pillars up and builds by itself. Printer: you move, it places every block in reach (like Litematica printer).",
      BuildRuntime.Mode.AUTO
   );
   public final EnumSetting<BuildRuntime.Order> order = this.mode(
      "Order", "Layers: bottom to top, one layer at a time. Nearest: any reachable block, lower ones first.", BuildRuntime.Order.LAYERS
   );
   public final IntSetting fromLayer = this.integer(
      "From Layer", "Start building at this layer (1 = bottom). Handy to skip the base or split huge builds.", 1, 1, 384
   );
   public final IntSetting toLayer = this.integer("To Layer", "Stop after this layer (0 = up to the top).", 0, 0, 384);
   public final DoubleSetting reach = this.dbl("Reach", "Placement distance.", 4.5, 2.0, 6.0, 0.1);
   public final IntSetting perTick = this.integer("Blocks/Tick", "Maximum placements per tick.", 1, 1, 8).visibleWhen(() -> this.humanRotations.get());
   public final IntSetting delay = this.integer("Delay", "Ticks between placements.", 1, 0, 10).visibleWhen(() -> this.humanRotations.get());
   public final IntSetting fastPerTick = this.integer("Fast Blocks/Tick", "Placements per tick when Human Rotations is off (no delay between them).", 12, 1, 40)
      .visibleWhen(() -> !this.humanRotations.get());
   public final BoolSetting airPlace = this.bool(
         "Air Place", "When Human Rotations is off: places blocks anywhere – in mid-air, behind walls, from any side. No scaffolding needed.", true
      )
      .visibleWhen(() -> !this.humanRotations.get());
   public final BoolSetting humanRotations = this.bool(
      "Human Rotations",
      "Turns your real camera to every block and clicks when it's on target – smooth like a player, one block at a time. Off = instant silent server-side rotations.",
      true
   );
   public final IntSetting rotateSpeed = this.integer("Rotate Speed", "Maximum head turn per tick in degrees (lower = calmer, higher = faster).", 32, 8, 90)
      .visibleWhen(this.humanRotations::get);
   public final IntSetting randomness = this.integer(
         "Randomness",
         "0 = robot-exact. Higher = turn speed varies per block, it aims at random spots of the face and takes small random pauses (sometimes a longer one).",
         25,
         0,
         100
      )
      .visibleWhen(this.humanRotations::get);
   public final BoolSetting visibleOnly = this.bool(
      "Legit Placement",
      "Only placements a player could really do: the clicked face must be visible from where you stand, never behind or through blocks. If a block needs another angle, Auto mode walks around to a spot where it works.",
      true
   );
   public final BoolSetting legitFallback = this.bool(
         "Legit Fallback",
         "Human Rotations: when no legit angle is found after a few tries, it places the block anyway (still turning the camera like a player) instead of standing still / getting stuck.",
         true
      )
      .visibleWhen(this.humanRotations::get);
   public final BoolSetting ignoreAxis = this.bool(
      "Ignore Log Axis",
      "Logs, pillars, basalt, hay … count as correct whichever way they lie – they are never mined and placed again because of their direction.",
      false
   );
   public final BoolSetting silentRotate = this.bool("Silent Rotate", "Without human rotations: your camera snaps back after every placement.", true)
      .visibleWhen(() -> !this.humanRotations.get());
   public final BoolSetting predict = this.bool(
      "Predict",
      "Smart check before every block: what happens when it is placed NOW? Water / lava that would wash away or burn redstone, torches, rails, plants; flammable blocks next to lava; TNT next to a power source; power sources that would trigger pistons / dispensers too early. Risky blocks wait, go last, or are skipped with a warning.",
      true
   );
   public final BoolSetting fluids = this.bool(
      "Fluids",
      "Also builds the water and lava of the schematic with buckets (after all blocks, checked by Predict). Empty buckets are refilled from sources nearby. Reload the schematic after changing this.",
      false
   );
   public final EnumSetting<AutoBuild.SneakPlace> sneakPlace = this.mode(
      "Sneak Place",
      "Off: normal. When Needed: sneaks to place against chests, doors, crafting tables and other clickable blocks. Always: sneaks for every placement (never opens containers by accident).",
      AutoBuild.SneakPlace.OFF
   );
   public final IntSetting maxAttempts = this.integer("Max Attempts", "Tries per block before it is skipped (Retry Skipped brings them back).", 8, 2, 30);
   public final BoolSetting walk = this.bool("Walk", "Walks towards blocks that are out of reach (Auto mode).", true).legacy("humanMovement");
   public final IntSetting maxFall = this.integer(
      "Max Fall", "Never walks off an edge higher than this: puts a step block down or sneaks at the edge instead (0 = off).", 3, 0, 20
   );
   public final BoolSetting escapeWater = this.bool(
      "Escape Water",
      "When it ends up in a lake / the sea and can't build from there, it swims to the nearest land and climbs out on scaffold blocks if the bank is too high.",
      true
   );
   public final BoolSetting pillar = this.bool(
      "Pillar Up", "Jumps and places scaffold blocks under you when the layer is too high or a 2+ block wall is in the way.", true
   );
   public final BoolSetting supports = this.bool(
      "Supports",
      "Scaffold blocks where needed (outside the schematic): under floating blocks, above hanging lanterns, behind wall torches, and under your feet when walking over gaps.",
      true
   );
   public final BoolSetting removeSupports = this.bool("Remove Supports", "Mines the scaffold blocks and pillars again when the build is done.", true)
      .visibleWhen(this.supports::get);
   public final IdListSetting scaffold = this.ids(
      "Scaffold Blocks",
      "Blocks used for pillaring up and as supports.",
      IdListSetting.Kind.BLOCK,
      new String[]{"minecraft:scaffolding", "minecraft:dirt", "minecraft:cobblestone", "minecraft:netherrack", "minecraft:cobbled_deepslate"}
   );
   public final BoolSetting fixWrong = this.bool(
      "Fix Wrong Blocks", "Mines blocks that are wrong (other block or wrong facing) and places the right one.", false
   );
   public final BoolSetting clearArea = this.bool(
      "Clear Area", "Mines everything inside the build box that is not part of the build (terrain, grass …).", false
   );
   public final BoolSetting adjustStates = this.bool(
      "Adjust States", "Clicks placed blocks into shape: repeater delay, comparator mode, note block pitch, open doors / trapdoors / gates, levers.", true
   );
   public final BoolSetting restock = this.bool(
      "Restock From Chests", "Takes missing materials out of chests / barrels / shulker boxes nearby (remembers what's where).", true
   );
   public final BoolSetting restockEarly = this.bool(
         "Restock Early", "Goes to a known chest before a material runs out, not only when it is gone (uses the chest memory shared with AutoRestock).", true
      )
      .visibleWhen(this.restock::get);
   public final IntSetting restockAt = this.integer("Restock At", "Restocks early when fewer than this many of a needed block are left.", 16, 1, 128)
      .visibleWhen(() -> this.restock.get() && this.restockEarly.get());
   public final IntSetting restockSpeed = this.integer("Restock Speed", "Stacks taken out of a chest per tick.", 6, 1, 27).visibleWhen(this.restock::get);
   public final IntSetting pausePlayers = this.integer(
      "Pause Near Players", "Pauses while a non-friend player is closer than this (0 = off). Continues when they are gone.", 0, 0, 128
   );
   public final IntSetting pauseDamage = this.integer("Pause On Damage", "Pauses this many seconds when you get hurt (0 = off).", 0, 0, 120);
   public final BoolSetting finishSound = this.bool("Finish Sound", "Plays a sound when the build is done.", true);
   public final BoolSetting creativeStacks = this.bool("Creative Stacks", "In creative mode missing blocks are spawned into the hotbar.", true)
      .legacy("creativeGive");
   public final BoolSetting giveCommand = this.bool("/give", "Uses /give @s for missing blocks (needs OP).", false);
   public final BoolSetting autoBuy = this.bool(
      "AutoBuy (/ah)",
      "Buys missing materials in the auction house: exact item only, cheapest per piece, price limit and balance from the AuctionHouse module. Off by default.",
      false
   );
   public final BoolSetting checkMaterials = this.bool(
      "Check Materials", "When a build starts: lists everything that is missing and – with AutoBuy on – buys it before placing the first block.", true
   );
   public final BoolSetting hammer = this.bool(
      "3x3 Pickaxe",
      "Clear Area, removing supports and pillars: uses your 3x3 pickaxe (name set in the \"3x3 Pickaxe\" module) where its square only hits scaffold / junk – never next to planned blocks.",
      false
   );
   public final BoolSetting render = this.bool("Render", "Ghost blocks of the current layer, wrong blocks in red, bounds.", true);
   public final IntSetting ghostLimit = this.integer("Ghost Limit", "Maximum ghost blocks drawn (lower = less lag).", 1500, 100, 8000)
      .visibleWhen(this.render::get);
   public final BoolSetting hud = this.bool("Progress HUD", "Progress bar with layer, speed and time left.", true);
   private final BuildRuntime runtime = new BuildRuntime();
   private final BuildRuntime.Settings settings = new BuildRuntime.Settings();
   private final Map<Integer, Integer> keyHeld = new HashMap<>();

   public AutoBuild() {
      super(
         "AutoBuild",
         Category.AUTOMATION,
         "Builds .litematic / .schem / .schematic files: preview & rotate, exact block states, printer mode, restock, resume."
      );
      this.action("Open Browser", "Choose a schematic, rotate it, see the layers and materials.", () -> {
         BuildPlan.includeFluids = this.fluids.get();
         this.openBrowser();
      });
      this.action("Pause / Resume", "Pauses or continues the current build.", this.runtime::pauseToggle);
      this.action("Resume Last Build", "Continues the build you stopped last time (same place and rotation).", () -> {
         if (this.runtime.resume() && !this.isEnabled()) {
            this.setEnabled(true);
         }
      });
      this.action(
         "Verify Last Build",
         "Checks the last placed build in the world: missing, wrong blocks and wrong facings. For other schematics use VERIFY in the browser.",
         () -> {
            if (this.runtime.resumeVerify() && !this.isEnabled()) {
               this.setEnabled(true);
            }
         }
      );
      this.action(
         "Simulate",
         "Dry run: shows what is done, what Predict would hold back or skip, and which materials are missing (with the chest they are in).",
         () -> {
            BuildPlan.includeFluids = this.fluids.get();

            for (String var2 : this.runtime.simulate()) {
               Notifications.chat(var2);
            }
         }
      );
      this.action("Material List", "Shows every material the build still needs: how many you need, have and miss (and in which chest it is).", () -> {
         for (String var2 : this.runtime.materialList()) {
            Notifications.chat(var2);
         }
      });
      this.action("Skip Layer", "Skips the rest of the current layer.", this.runtime::skipLayer);
      this.action("Retry Skipped", "Tries the skipped blocks again.", this.runtime::retrySkipped);
      this.action("Stop", "Stops building.", () -> {
         this.runtime.stop();
         this.setEnabled(false);
      });
   }

   private void openBrowser() {
      mc.execute(() -> mc.method_1507(new SchematicBrowserScreen(mc.field_1755, this)));
   }

   public static Path schematicDir() {
      return FabricLoader.getInstance().getGameDir().resolve("dihclient").resolve("schematics");
   }

   public BuildRuntime runtime() {
      return this.runtime;
   }

   public void startBuild(Schematic var1) {
      this.runtime.start(var1);
      if (!this.isEnabled()) {
         this.setEnabled(true);
      }
   }

   public void startBuild(BuildPlan var1) {
      this.runtime.start(var1);
      if (!this.isEnabled()) {
         this.setEnabled(true);
      }
   }

   public void startPreview(Schematic var1) {
      this.runtime.preview(var1);
      if (!this.isEnabled()) {
         this.setEnabled(true);
      }
   }

   @Override
   protected void onEnable() {
      BuildPlan.includeFluids = this.fluids.get();
      if (!this.runtime.isRunning() && inGame()) {
         mc.execute(() -> {
            if (mc.field_1755 == null && !this.runtime.isRunning() && this.isEnabled()) {
               mc.method_1507(new SchematicBrowserScreen(null, this));
            }
         });
      }
   }

   @Override
   protected void onDisable() {
      this.runtime.stop();
   }

   @Override
   public void onWorldChange() {
      if (this.runtime.isRunning()) {
         this.runtime.stop();
      }

      if (this.isEnabled()) {
         this.setEnabledSilently(false);
      }
   }

   private void sync() {
      this.settings.mode = this.mode.get();
      this.settings.order = this.order.get();
      this.settings.reach = this.reach.get();
      this.settings.perTick = this.perTick.get();
      this.settings.delay = this.delay.get();
      this.settings.silentRotate = this.silentRotate.get();
      this.settings.predict = this.predict.get();
      BuildPlan.includeFluids = this.fluids.get();
      this.settings.sneakMode = this.sneakPlace.get().ordinal();
      this.settings.human = this.humanRotations.get();
      this.settings.rotateSpeed = this.rotateSpeed.get().intValue();
      this.settings.randomness = this.randomness.get().intValue() / 100.0;
      this.settings.supports = this.supports.get();
      this.settings.legitFallback = this.legitFallback.get();
      this.settings.maxFall = this.maxFall.get();
      this.settings.escapeWater = this.escapeWater.get();
      this.settings.removeSupports = this.removeSupports.get();
      PlacementSolver.visibleOnly = true;
      this.settings.strict = this.visibleOnly.get();
      this.settings.maxAttempts = this.maxAttempts.get();
      this.settings.fromLayer = this.fromLayer.get();
      this.settings.toLayer = this.toLayer.get();
      this.settings.pausePlayers = this.pausePlayers.get();
      this.settings.pauseDamage = this.pauseDamage.get();
      this.settings.finishSound = this.finishSound.get();
      this.settings.walk = this.walk.get();
      this.settings.pillar = this.pillar.get();
      this.settings.scaffold = RegistryUtil.blocks(this.scaffold.get());
      this.settings.fixWrong = this.fixWrong.get();
      this.settings.clearArea = this.clearArea.get();
      this.settings.adjustStates = this.adjustStates.get();
      this.settings.restock = this.restock.get();
      this.settings.restockEarly = this.restockEarly.get();
      this.settings.restockAt = this.restockAt.get();
      this.settings.restockPerTick = this.restockSpeed.get();
      this.settings.creativeStacks = this.creativeStacks.get();
      this.settings.giveCommand = this.giveCommand.get();
      this.settings.autoBuy = this.autoBuy.get();
      this.settings.checkMaterials = this.checkMaterials.get();
      this.settings.hammer = this.hammer.get();
   }

   private void applyFast() {
      boolean var1 = !this.humanRotations.get();
      if (var1) {
         this.settings.perTick = this.fastPerTick.get();
         this.settings.delay = 0;
         this.settings.strict = false;
      }

      this.settings.airPlace = var1 && this.airPlace.get();
      this.settings.anyFace = var1 && this.airPlace.get();
   }

   @Override
   public void onTick() {
      if (mc.field_1724.field_6012 % 20 != 0 && !this.settings.scaffold.isEmpty()) {
         this.settings.mode = this.mode.get();
         this.settings.order = this.order.get();
         this.settings.reach = this.reach.get();
         this.settings.perTick = this.perTick.get();
         this.settings.delay = this.delay.get();
         this.settings.predict = this.predict.get();
         this.settings.sneakMode = this.sneakPlace.get().ordinal();
         this.settings.human = this.humanRotations.get();
         this.settings.rotateSpeed = this.rotateSpeed.get().intValue();
         this.settings.randomness = this.randomness.get().intValue() / 100.0;
         PlacementSolver.visibleOnly = true;
         this.settings.strict = this.visibleOnly.get();
      } else {
         this.sync();
      }

      if (this.runtime.phase() == BuildRuntime.Phase.PREVIEW && mc.field_1755 == null) {
         this.previewKeys();
      }

      if (this.runtime.phase() == BuildRuntime.Phase.VERIFY && mc.field_1755 == null) {
         this.verifyKeys();
      }

      this.applyFast();
      PlacementSolver.visibleOnly = !this.settings.anyFace;
      PlacementSolver.ignoreAxis = this.ignoreAxis.get();

      try {
         this.runtime.tick(this.settings);
      } finally {
         PlacementSolver.visibleOnly = true;
         PlacementSolver.ignoreAxis = false;
         PlacementSolver.sneakInteractive = false;
      }
   }

   private void previewKeys() {
      if (this.pressed(265)) {
         this.runtime.moveRelative(1, 0);
      }

      if (this.pressed(264)) {
         this.runtime.moveRelative(-1, 0);
      }

      if (this.pressed(263)) {
         this.runtime.moveRelative(0, -1);
      }

      if (this.pressed(262)) {
         this.runtime.moveRelative(0, 1);
      }

      if (this.pressed(266)) {
         this.runtime.move(0, 1, 0);
      }

      if (this.pressed(267)) {
         this.runtime.move(0, -1, 0);
      }

      if (this.pressedOnce(82)) {
         this.runtime.rotate();
      }

      if (this.pressedOnce(77)) {
         this.runtime.mirror();
      }

      if (this.pressedOnce(70)) {
         this.runtime.toggleFollow();
      }

      if (this.pressedOnce(257) || this.pressedOnce(335)) {
         this.runtime.confirm();
      }

      if (this.pressedOnce(259)) {
         this.runtime.stop();
         this.setEnabled(false);
         Notifications.info("AutoBuild", "Preview cancelled");
      }
   }

   private void verifyKeys() {
      if (this.pressed(266)) {
         this.runtime.verifyLayerStep(1);
      }

      if (this.pressed(267)) {
         this.runtime.verifyLayerStep(-1);
      }

      if (this.pressedOnce(66)) {
         this.runtime.buildFromVerify();
      }

      if (this.pressedOnce(259)) {
         this.runtime.stop();
         this.setEnabled(false);
      }
   }

   public void startVerifyPreview(Schematic var1, class_2470 var2, class_2415 var3) {
      this.runtime.previewVerify(var1, var2, var3);
      if (!this.isEnabled()) {
         this.setEnabled(true);
      }
   }

   private boolean pressed(int var1) {
      boolean var2 = KeyUtil.isKeyDown(var1);
      int var3 = var2 ? this.keyHeld.getOrDefault(var1, 0) + 1 : 0;
      this.keyHeld.put(var1, var3);
      return var3 == 1 || var3 > 8 && var3 % 2 == 0;
   }

   private boolean pressedOnce(int var1) {
      boolean var2 = KeyUtil.isKeyDown(var1);
      int var3 = var2 ? this.keyHeld.getOrDefault(var1, 0) + 1 : 0;
      this.keyHeld.put(var1, var3);
      return var3 == 1;
   }

   @Override
   public void onRender3D(Render3D var1) {
      if (this.render.get() || this.runtime.phase() == BuildRuntime.Phase.PREVIEW) {
         this.runtime.render(var1, HudManager.accent(), this.render.get(), this.ghostLimit.get());
      }
   }

   @Override
   public void onRender2D(class_332 var1, float var2) {
      if (this.hud.get() && this.runtime.plan() != null && !mc.field_1690.field_1842) {
         int var3 = HudManager.accent();
         short var4 = 220;
         int var5 = (mc.method_22683().method_4486() - var4) / 2;
         byte var6 = 4;
         BuildPlan var7 = this.runtime.plan();
         if (this.runtime.phase() == BuildRuntime.Phase.PREVIEW) {
            byte var8 = 40;
            Gfx.panel(var1, var5, var6, var4, var8, var3);
            Gfx.text(var1, Gfx.trim("Preview · " + var7.schematic.name(), var4 - 10), var5 + 5, var6 + 4, -1);
            Gfx.text(
               var1,
               "Rot "
                  + rot(var7)
                  + " · Mirror "
                  + var7.mirror.name().toLowerCase(Locale.ROOT)
                  + (this.runtime.followsCrosshair() ? " · follows crosshair" : ""),
               var5 + 5,
               var6 + 15,
               -7564380
            );
            Gfx.text(var1, "Enter build · R rotate · M mirror · F follow · Backspace cancel", var5 + 5, var6 + 26, var3);
         } else if (this.runtime.phase() == BuildRuntime.Phase.VERIFY) {
            int[] var16 = this.runtime.verifyCounts();
            int var9 = Math.max(1, this.runtime.totalCount());
            byte var10 = 46;
            Gfx.panel(var1, var5, var6, var4, var10, var3);
            Gfx.text(var1, Gfx.trim("Verify · " + var7.schematic.name(), var4 - 60), var5 + 5, var6 + 4, -1);
            String var11 = String.format(Locale.ROOT, "%.1f%%", var16[0] * 100.0F / var9);
            Gfx.text(var1, var11, var5 + var4 - 5 - Gfx.width(var11), var6 + 4, var16[0] == var9 ? -11870592 : var3);
            Gfx.bar(var1, var5 + 5, var6 + 15, var4 - 10, 3, (float)var16[0] / var9, 1090519039, -11870592);
            Gfx.text(var1, "missing " + var16[1], var5 + 5, var6 + 22, -4473857);
            Gfx.text(var1, "wrong " + var16[2], var5 + 80, var6 + 22, -44976);
            Gfx.text(var1, "state " + var16[3], var5 + 145, var6 + 22, -24544);
            class_2338 var12 = this.runtime.nearestProblem();
            String var13 = this.runtime.verifyLayer() < 0 ? "all layers" : "layer " + (this.runtime.verifyLayer() + 1) + "/" + this.runtime.layers();
            String var14 = var12 == null ? "" : " · next " + (int)Math.sqrt(var12.method_10262(mc.field_1724.method_24515())) + "m, Y " + var12.method_10264();
            Gfx.text(var1, Gfx.trim(var13 + var14 + " · B build rest", var4 - 10), var5 + 5, var6 + 34, -7564380);
         } else {
            boolean var17 = !this.runtime.status().startsWith("Layer") && !this.runtime.status().startsWith("Building");
            int var18 = var17 ? 44 : 34;
            Gfx.panel(var1, var5, var6, var4, var18, var3);
            int var19 = Math.max(1, this.runtime.totalCount());
            float var20 = (float)this.runtime.doneCount() / var19;
            String var21 = Gfx.trim(var7.schematic.name(), var4 - 60);
            Gfx.text(var1, var21, var5 + 5, var6 + 4, -1);
            String var22 = String.format(Locale.ROOT, "%.0f%%", var20 * 100.0F);
            Gfx.text(var1, var22, var5 + var4 - 5 - Gfx.width(var22), var6 + 4, var3);
            Gfx.bar(var1, var5 + 5, var6 + 15, var4 - 10, 3, var20, 1090519039, var3);
            int var23 = this.runtime.eta();
            String var15 = "Layer "
               + (this.runtime.layer() + 1)
               + "/"
               + this.runtime.layers()
               + " · "
               + String.format(Locale.ROOT, "%.1f", this.runtime.rate())
               + " bl/s"
               + (var23 >= 0 ? " · ~" + time(var23) + " left" : " · ETA …")
               + (this.runtime.phase() == BuildRuntime.Phase.PAUSED ? " · PAUSED" : "");
            Gfx.text(var1, Gfx.trim(var15, var4 - 10), var5 + 5, var6 + 22, -7564380);
            if (var17) {
               Gfx.text(var1, Gfx.trim(this.runtime.status(), var4 - 10), var5 + 5, var6 + 32, -4473857);
            }
         }
      }
   }

   private static String rot(BuildPlan var0) {
      return switch (var0.rotation) {
         case field_11467 -> "0°";
         case field_11463 -> "90°";
         case field_11464 -> "180°";
         case field_11465 -> "270°";
         default -> throw new MatchException(null, null);
      };
   }

   public static String time(int var0) {
      if (var0 < 60) {
         return var0 + "s";
      } else {
         return var0 < 3600 ? var0 / 60 + "m " + var0 % 60 + "s" : var0 / 3600 + "h " + var0 % 3600 / 60 + "m";
      }
   }

   @Override
   public String getInfo() {
      if (this.runtime.plan() == null) {
         return null;
      } else if (this.runtime.phase() == BuildRuntime.Phase.PREVIEW) {
         return "Preview";
      } else {
         int var1 = this.runtime.phase() == BuildRuntime.Phase.BUILDING ? this.runtime.eta() : -1;
         return this.runtime.doneCount() + "/" + this.runtime.totalCount() + (var1 >= 0 ? " · " + time(var1) : "");
      }
   }

   @Override
   public List<String> details() {
      ArrayList var1 = new ArrayList();
      var1.add("Status: " + this.runtime.status());
      if (this.runtime.plan() != null && this.runtime.phase() != BuildRuntime.Phase.PREVIEW) {
         var1.add(
            "Layer "
               + (this.runtime.layer() + 1)
               + "/"
               + this.runtime.layers()
               + " · "
               + this.runtime.doneCount()
               + "/"
               + this.runtime.totalCount()
               + (this.runtime.skippedCount() > 0 ? " · skipped " + this.runtime.skippedCount() : "")
         );
         int var2 = this.runtime.eta();
         var1.add("Time left: " + (var2 >= 0 ? "~" + time(var2) : "calculating…") + " · building for " + time(this.runtime.elapsed()));
         int var3 = 0;

         for (Entry var5 : this.runtime.missingMaterials().entrySet()) {
            var1.add("Missing " + var5.getValue() + "x " + ((class_1792)var5.getKey()).method_63680().getString());
            if (++var3 >= 4) {
               break;
            }
         }
      }

      String var6 = BuildRuntime.savedInfo();
      if (var6 != null && this.runtime.plan() == null) {
         var1.add("Saved build: " + var6);
      }

      return var1;
   }

   public static enum SneakPlace {
      OFF,
      WHEN_NEEDED,
      ALWAYS;
   }
}
