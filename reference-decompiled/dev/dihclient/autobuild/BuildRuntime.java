package dev.dihclient.autobuild;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import dev.dihclient.DIHClient;
import dev.dihclient.module.ModuleManager;
import dev.dihclient.modules.player.AutoEat;
import dev.dihclient.modules.world.SafeRoute;
import dev.dihclient.render.Render3D;
import dev.dihclient.util.Hammer;
import dev.dihclient.util.HumanAim;
import dev.dihclient.util.Humanizer;
import dev.dihclient.util.InvUtil;
import dev.dihclient.util.ItemUtil;
import dev.dihclient.util.KeyUtil;
import dev.dihclient.util.Notifications;
import dev.dihclient.util.RotationUtil;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import java.util.function.Predicate;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.class_1268;
import net.minecraft.class_1269;
import net.minecraft.class_1747;
import net.minecraft.class_1792;
import net.minecraft.class_1799;
import net.minecraft.class_2246;
import net.minecraft.class_2248;
import net.minecraft.class_2323;
import net.minecraft.class_2338;
import net.minecraft.class_2346;
import net.minecraft.class_2349;
import net.minecraft.class_2350;
import net.minecraft.class_238;
import net.minecraft.class_2401;
import net.minecraft.class_2415;
import net.minecraft.class_243;
import net.minecraft.class_2470;
import net.minecraft.class_2533;
import net.minecraft.class_2680;
import net.minecraft.class_2741;
import net.minecraft.class_310;
import net.minecraft.class_3414;
import net.minecraft.class_3417;
import net.minecraft.class_3965;
import net.minecraft.class_742;
import net.minecraft.class_746;
import net.minecraft.class_7923;
import net.minecraft.class_2338.class_2339;
import net.minecraft.class_2350.class_2351;
import net.minecraft.class_239.class_240;
import net.minecraft.class_2828.class_2831;

public final class BuildRuntime {
   private static final class_310 mc = class_310.method_1551();
   private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
   private BuildPlan plan;
   private BuildRuntime.Phase phase = BuildRuntime.Phase.IDLE;
   private boolean followCrosshair = true;
   private String status = "Idle";
   private boolean[] done;
   private int[] attempts;
   private int[] retryAt;
   private int[] predictWaits;
   private final Set<Integer> lastDeferred = new HashSet<>();
   private boolean placeSneak;
   private int[] layerStart;
   private int[] layerEnd;
   private int[] layerOpen;
   private int doneCount;
   private int verifyCursor;
   private int tick;
   private int wait;
   private int giveCooldown;
   private int layer;
   private class_2338 mining;
   private int miningTicks;
   private class_2338 adjusting;
   private int adjustClicks;
   private int adjustWait;
   private final Deque<Long> placeTimes = new ArrayDeque<>();
   private long startedAt;
   private long activeMillis;
   private long lastActiveAt;
   private int doneAtStart;
   private final Map<class_1792, Integer> missing = new LinkedHashMap<>();
   private final AutoBuy autoBuy = new AutoBuy();
   private final Restock restock = new Restock();
   private boolean walking;
   private int stuckTicks;
   private class_243 lastPos;
   private int noProgress;
   private int waterTicks;
   private boolean escaping;
   private boolean prepareMaterials;
   private boolean prepareAnnounced;
   private boolean prepareBuying;
   private class_1792 prepareItem;
   private int prepareBuys;
   private final Set<class_1792> prepareFailed = new HashSet<>();
   private double waterBest = Double.MAX_VALUE;
   private int lastDoneSeen = -1;
   private int lastPlaceSeen;
   private boolean sneaking;
   private class_2338 towerBase;
   private int towerTicks;
   private byte[] verifyClass;
   private final int[] verifyCounts = new int[4];
   private int verifyLayer = -1;
   private boolean verifyAfterPreview;
   private static final BuildRuntime.Settings verifySettings = new BuildRuntime.Settings();
   private BuildRuntime.Settings lastSettings;
   private final Map<Long, Integer> indexCache = new HashMap<>();
   private PlacementSolver.Click pending;
   private boolean pendingBucket;
   private int pendingIndex = -1;
   private class_2338 pendingSupport;
   private int pendingSlot = -1;
   private int pendingTicks;
   private boolean pendingAligned;
   private final Set<class_2338> supports = new HashSet<>();
   private boolean towerAimed;
   private class_243 reposition;
   private int repositionTicks;
   private int lastHurt;
   private long damagePauseUntil;
   private String pausedFor;
   private boolean miningAligned;
   private final Humanizer humanizer = new Humanizer();
   private float pendingYaw;
   private float pendingPitch;
   private float pendingSpeed;
   private class_243 miningJitter = class_243.field_1353;
   private float miningSpeed = 32.0F;
   private int skippedCache = -1;
   private int skippedCacheTick;
   private long skippedCacheAt;
   private class_2338 problemCache;
   private int problemCacheTick = -1;
   private int problemCacheLayer;
   private long problemCacheAt;
   private static final String[] NEEDS_FLOOR = new String[]{
      "torch",
      "_sign",
      "banner",
      "carpet",
      "rail",
      "pressure_plate",
      "redstone_wire",
      "repeater",
      "comparator",
      "candle",
      "sapling",
      "flower",
      "tulip",
      "orchid",
      "allium",
      "bluet",
      "daisy",
      "dandelion",
      "poppy",
      "cornflower",
      "lily_of",
      "dead_bush",
      "fern",
      "short_grass",
      "tall_grass",
      "sea_pickle",
      "snow",
      "mushroom",
      "fungus",
      "roots",
      "azalea",
      "lantern",
      "pointed_dripstone",
      "cake",
      "flower_pot",
      "skull",
      "head",
      "bed",
      "door",
      "turtle_egg",
      "frogspawn",
      "lily_pad",
      "moss_carpet",
      "pale_moss_carpet",
      "leaf_litter",
      "wildflowers",
      "cactus",
      "sugar_cane",
      "bamboo",
      "kelp",
      "seagrass"
   };

   public BuildRuntime.Phase phase() {
      return this.phase;
   }

   public boolean isRunning() {
      return this.phase == BuildRuntime.Phase.BUILDING
         || this.phase == BuildRuntime.Phase.PAUSED
         || this.phase == BuildRuntime.Phase.PREVIEW
         || this.phase == BuildRuntime.Phase.VERIFY;
   }

   public int[] verifyCounts() {
      return this.verifyCounts;
   }

   public int verifyLayer() {
      return this.verifyLayer;
   }

   public void verifyLayerStep(int var1) {
      if (this.plan != null) {
         this.verifyLayer = Math.max(-1, Math.min(this.plan.sizeY - 1, this.verifyLayer + var1));
      }
   }

   public void previewVerify(Schematic var1, class_2470 var2, class_2415 var3) {
      this.preview(var1, var2, var3);
      this.verifyAfterPreview = true;
      this.status = "Verify preview – aim, then Enter";
   }

   public boolean verifyPending() {
      return this.verifyAfterPreview;
   }

   private void beginVerify() {
      this.begin();
      this.verifyClass = new byte[this.plan.blocks.size()];
      Arrays.fill(this.verifyClass, (byte)-1);
      Arrays.fill(this.verifyCounts, 0);
      this.verifyLayer = -1;
      this.phase = BuildRuntime.Phase.VERIFY;
      this.status = "Verifying " + this.plan.schematic.name();
      Notifications.info(
         "AutoBuild", "Verifier: red = wrong block, orange = wrong facing/state, ghost = missing · PgUp/PgDn layer · B build the rest · Backspace exit"
      );
   }

   public void buildFromVerify() {
      if (this.phase == BuildRuntime.Phase.VERIFY) {
         this.phase = BuildRuntime.Phase.BUILDING;
         this.status = "Building";
         this.doneAtStart = this.doneCount;
         this.activeMillis = 0L;
         this.lastActiveAt = 0L;
      }
   }

   private byte classify(int var1) {
      BuildPlan.Planned var2 = this.plan.blocks.get(var1);
      class_2680 var3 = mc.field_1687.method_8320(var2.pos());
      BuildRuntime.Status var4 = this.statusOf(var1, verifySettings);

      return switch (var4) {
         case DONE -> 0;
         case OPEN -> 1;
         case INCREMENT, ADJUST -> 3;
         case WRONG -> var3.method_26204() == var2.state().method_26204() ? 3 : 2;
         default -> 1;
      };
   }

   private void tickVerify() {
      int var1 = this.plan.blocks.size();

      for (int var2 = 0; var2 < Math.min(var1, 6000); var2++) {
         int var3 = this.verifyCursor;
         this.verifyCursor = (this.verifyCursor + 1) % var1;
         byte var4 = this.classify(var3);
         byte var5 = this.verifyClass[var3];
         if (var5 != var4) {
            if (var5 >= 0) {
               this.verifyCounts[var5]--;
            }

            this.verifyCounts[var4]++;
            this.verifyClass[var3] = var4;
         }

         this.verify(var3);
      }

      int var6 = this.verifyCounts[1] + this.verifyCounts[2] + this.verifyCounts[3];
      this.status = var6 == 0 && this.verifyCounts[0] == var1 ? "Verified: everything correct!" : "Verify: " + var6 + " to fix";
   }

   public class_2338 nearestProblem() {
      long var1 = System.currentTimeMillis();
      if (this.problemCacheTick == this.tick && this.problemCacheLayer == this.verifyLayer && var1 - this.problemCacheAt < 250L) {
         return this.problemCache;
      } else {
         this.problemCache = this.nearestProblemNow();
         this.problemCacheTick = this.tick;
         this.problemCacheLayer = this.verifyLayer;
         this.problemCacheAt = var1;
         return this.problemCache;
      }
   }

   private class_2338 nearestProblemNow() {
      if (this.verifyClass != null && mc.field_1724 != null) {
         class_2338 var1 = null;
         double var2 = Double.MAX_VALUE;
         class_243 var4 = mc.field_1724.method_73189();

         for (int var5 = 0; var5 < this.verifyClass.length; var5++) {
            if (this.verifyClass[var5] > 0 && (this.verifyLayer < 0 || this.plan.blocks.get(var5).layer() == this.verifyLayer)) {
               double var6 = distSq(this.plan.blocks.get(var5).pos(), var4);
               if (var6 < var2) {
                  var2 = var6;
                  var1 = this.plan.blocks.get(var5).pos();
               }
            }
         }

         return var1;
      } else {
         return null;
      }
   }

   public boolean isBuilding() {
      return this.phase == BuildRuntime.Phase.BUILDING;
   }

   public BuildPlan plan() {
      return this.plan;
   }

   public String status() {
      return this.status;
   }

   public int doneCount() {
      return this.doneCount;
   }

   public int totalCount() {
      return this.plan == null ? 0 : this.plan.blocks.size();
   }

   public int layer() {
      return this.layer;
   }

   public int layers() {
      return this.plan == null ? 0 : this.plan.sizeY;
   }

   public boolean followsCrosshair() {
      return this.followCrosshair;
   }

   public double rate() {
      long var1 = System.currentTimeMillis();

      while (!this.placeTimes.isEmpty() && var1 - this.placeTimes.peekFirst() > 30000L) {
         this.placeTimes.pollFirst();
      }

      if (this.placeTimes.isEmpty()) {
         return 0.0;
      } else {
         double var3 = Math.max(5.0, Math.min(30.0, (var1 - this.startedAt) / 1000.0));
         return this.placeTimes.size() / var3;
      }
   }

   public double averageRate() {
      int var1 = this.doneCount - this.doneAtStart;
      return this.activeMillis >= 10000L && var1 >= 5 ? var1 / (this.activeMillis / 1000.0) : 0.0;
   }

   public int eta() {
      double var1 = this.rate();
      double var3 = this.averageRate();
      double var5 = var3 <= 0.0 ? var1 : (var1 <= 0.05 ? var3 : var1 * 0.4 + var3 * 0.6);
      int var7 = this.totalCount() - this.doneCount - this.skippedCount();
      return var5 <= 0.05 ? -1 : (int)(Math.max(0, var7) / var5);
   }

   public int elapsed() {
      return (int)(this.activeMillis / 1000L);
   }

   public List<String> simulate() {
      ArrayList var1 = new ArrayList();
      if (this.plan != null && mc.field_1687 != null) {
         int var2 = 0;
         int var3 = 0;
         int var4 = 0;
         int var5 = 0;
         int[] var6 = new int[4];
         LinkedHashMap var7 = new LinkedHashMap();
         HashMap var8 = new HashMap();

         for (int var9 = 0; var9 < this.plan.blocks.size(); var9++) {
            BuildPlan.Planned var10 = this.plan.blocks.get(var9);
            if (this.inRange(var10.layer())) {
               BuildRuntime.Status var11 = this.statusOf(var9, this.lastSettings);
               if (var11 == BuildRuntime.Status.DONE) {
                  var2++;
               } else {
                  var8.merge(BuildPlan.itemOf(var10.state()), BuildPlan.pieces(var10.state()), Integer::sum);
                  if (var11 == BuildRuntime.Status.WRONG) {
                     var4++;
                  } else {
                     var3++;
                     if (BuildPlan.isFluid(var10.state())) {
                        var5++;
                     }

                     Predict.Verdict var12 = Predict.check(this.plan, var10, false);
                     if (var12 != null) {
                        var6[var12.kind()]++;
                        if (var12.kind() != 2 || !BuildPlan.isFluid(var10.state())) {
                           String var13 = var10.state().method_26204().method_9518().getString() + ": " + var12.why();
                           if (var7.size() < 6 || var7.containsKey(var13)) {
                              var7.merge(var13, 1, Integer::sum);
                           }
                        }
                     }
                  }
               }
            }
         }

         var1.add(
            String.format(Locale.ROOT, "Simulation: %d done · %d to place · %d wrong%s", var2, var3, var4, var5 > 0 ? " · " + var5 + " fluid sources" : "")
         );
         if (var6[1] + var6[2] + var6[3] == 0) {
            var1.add("Predict: nothing risky found.");
         } else {
            var1.add(String.format(Locale.ROOT, "Predict: %d wait · %d go last · %d would be skipped", var6[1], var6[2], var6[3]));
            var7.forEach((var1x, var2x) -> var1.add("  " + var2x + "x " + var1x));
         }

         int var15 = 0;

         for (Entry var17 : var8.entrySet().stream().sorted((var0, var1x) -> Integer.compare((Integer)var1x.getValue(), (Integer)var0.getValue())).toList()) {
            class_1792 var18 = (class_1792)var17.getKey();
            int var19 = InvUtil.count(var1x -> var1x.method_31574(var18));
            if (var19 < (Integer)var17.getValue() && var15++ < 10) {
               class_2338 var14 = Restock.whereIs(var18);
               var1.add(
                  String.format(
                     Locale.ROOT,
                     "  missing %d %s%s",
                     (Integer)var17.getValue() - var19,
                     var18.method_63680().getString(),
                     var14 == null ? "" : " (chest at " + var14.method_10263() + " " + var14.method_10264() + " " + var14.method_10260() + ")"
                  )
               );
            }
         }

         var1.add(
            1 + (var6[1] + var6[2] + var6[3] == 0 ? 1 : 1 + Math.min(6, var7.size())),
            var15 == 0 ? "Materials: you have everything." : "Materials: " + var15 + " kinds missing"
         );
         return var1;
      } else {
         var1.add("No build loaded – open the browser and preview a schematic first.");
         return var1;
      }
   }

   public List<String> materialList() {
      ArrayList var1 = new ArrayList();
      if (this.plan == null) {
         var1.add("No build loaded – preview a schematic first.");
         return var1;
      } else {
         HashMap var2 = new HashMap();

         for (int var3 = 0; var3 < this.plan.blocks.size(); var3++) {
            BuildPlan.Planned var4 = this.plan.blocks.get(var3);
            if (!this.doneAt(var3) && this.inRange(var4.layer())) {
               var2.merge(BuildPlan.itemOf(var4.state()), BuildPlan.pieces(var4.state()), Integer::sum);
            }
         }

         int var11 = 0;
         int var12 = 0;

         for (Entry var6 : var2.entrySet().stream().sorted((var0, var1x) -> Integer.compare((Integer)var1x.getValue(), (Integer)var0.getValue())).toList()) {
            class_1792 var7 = (class_1792)var6.getKey();
            int var8 = InvUtil.count(var1x -> var1x.method_31574(var7));
            int var9 = Math.max(0, (Integer)var6.getValue() - var8);
            var11 += var6.getValue();
            if (var9 > 0) {
               var12++;
            }

            class_2338 var10 = var9 > 0 ? Restock.whereIs(var7) : null;
            var1.add(
               String.format(
                  Locale.ROOT,
                  "%s%s: need %d · have %d%s%s",
                  var9 > 0 ? "§c" : "§a",
                  var7.method_63680().getString(),
                  var6.getValue(),
                  var8,
                  var9 > 0 ? " · missing " + var9 + " (" + (var9 + 63) / 64 + " stacks)" : " ✔",
                  var10 == null ? "" : " · chest " + var10.method_10263() + " " + var10.method_10264() + " " + var10.method_10260()
               )
            );
         }

         var1.add(
            0, String.format(Locale.ROOT, "Materials for %s: %d blocks, %d kinds, %d kinds missing", this.plan.schematic.name(), var11, var2.size(), var12)
         );
         return var1;
      }
   }

   private boolean prepareStep(BuildRuntime.Settings var1) {
      if (this.prepareBuying) {
         if (this.autoBuy.isActive()) {
            return true;
         }

         this.prepareBuying = false;
         if (!this.autoBuy.lastBought() && this.prepareItem != null) {
            this.prepareFailed.add(this.prepareItem);
            Notifications.warn("AutoBuild", this.autoBuy.lastResult());
         }
      }

      Map var2 = this.missingMaterials();
      var2.keySet().removeAll(this.prepareFailed);
      if (!this.prepareAnnounced) {
         this.prepareAnnounced = true;
         if (var2.isEmpty()) {
            this.prepareMaterials = false;
            return false;
         }

         Notifications.chat("§eAutoBuild: " + var2.size() + " kinds of blocks are missing:");
         int var3 = 0;

         for (Entry var5 : var2.entrySet()) {
            if (var3++ >= 12) {
               Notifications.chat("  … (AutoBuild → Material List shows all)");
               break;
            }

            Notifications.chat("  §c" + var5.getValue() + "x §f" + ((class_1792)var5.getKey()).method_63680().getString());
         }

         if (!var1.autoBuy) {
            Notifications.chat("§7Turn on AutoBuy (/ah) to buy them first, or restock from chests while building.");
         }
      }

      if (var1.restock) {
         var2.keySet().removeIf(var0 -> Restock.knowsNearby(var0, 48.0));
      }

      boolean var6 = mc.field_1724.method_31548().method_7376() < 0;
      if (!var2.isEmpty() && var1.autoBuy && !mc.field_1724.method_68878() && this.prepareBuys <= 200 && !var6) {
         class_1792 var7 = (class_1792)var2.keySet().iterator().next();
         this.prepareItem = var7;
         this.autoBuy.begin(var7);
         if (this.autoBuy.isActive()) {
            this.prepareBuying = true;
            this.prepareBuys++;
         }

         this.status = "Buying materials first: " + var7.method_63680().getString() + " (" + var2.get(var7) + " missing)";
         return true;
      } else {
         if (var6 && var1.autoBuy && !var2.isEmpty()) {
            Notifications.warn("AutoBuild", "Inventory full – not buying more materials");
         }

         if (var1.autoBuy && this.prepareAnnounced && this.prepareBuys > 0) {
            Notifications.info("AutoBuild", var2.isEmpty() ? "All materials bought – building" : "Could not buy everything – building with what there is");
         }

         this.prepareMaterials = false;
         return false;
      }
   }

   public Map<class_1792, Integer> missingMaterials() {
      HashMap var1 = new HashMap();
      if (this.plan == null) {
         return var1;
      } else {
         for (int var2 = 0; var2 < this.plan.blocks.size(); var2++) {
            if (!this.doneAt(var2) && this.inRange(this.plan.blocks.get(var2).layer())) {
               BuildPlan.Planned var3 = this.plan.blocks.get(var2);
               var1.merge(BuildPlan.itemOf(var3.state()), BuildPlan.pieces(var3.state()), Integer::sum);
            }
         }

         LinkedHashMap var4 = new LinkedHashMap();
         var1.entrySet().stream().sorted((var0, var1x) -> Integer.compare((Integer)var1x.getValue(), (Integer)var0.getValue())).forEach(var1x -> {
            int var2x = InvUtil.count(var1xx -> var1xx.method_31574((class_1792)var1x.getKey()));
            if (var2x < (Integer)var1x.getValue()) {
               var4.put((class_1792)var1x.getKey(), (Integer)var1x.getValue() - var2x);
            }
         });
         return var4;
      }
   }

   private boolean tracked() {
      return this.plan != null
         && this.done != null
         && this.attempts != null
         && this.done.length == this.plan.blocks.size()
         && this.phase != BuildRuntime.Phase.PREVIEW;
   }

   private boolean doneAt(int var1) {
      return this.tracked() ? this.done[var1] : mc.field_1687 != null && this.statusOf(var1, this.lastSettings) == BuildRuntime.Status.DONE;
   }

   public int skippedCount() {
      long var1 = System.currentTimeMillis();
      if (this.skippedCache >= 0 && this.skippedCacheTick == this.tick && var1 - this.skippedCacheAt < 250L) {
         return this.skippedCache;
      } else {
         this.skippedCache = this.skippedCountNow();
         this.skippedCacheTick = this.tick;
         this.skippedCacheAt = var1;
         return this.skippedCache;
      }
   }

   private int skippedCountNow() {
      if (!this.tracked()) {
         return 0;
      } else {
         int var1 = 0;

         for (int var2 = 0; var2 < this.attempts.length; var2++) {
            if (!this.done[var2] && this.attempts[var2] >= 1000 && this.inRange(this.plan.blocks.get(var2).layer())) {
               var1++;
            }
         }

         return var1;
      }
   }

   public void preview(Schematic var1) {
      this.preview(var1, class_2470.field_11467, class_2415.field_11302);
   }

   public void preview(Schematic var1, class_2470 var2, class_2415 var3) {
      this.stop();
      if (mc.field_1724 != null) {
         this.plan = new BuildPlan(var1, class_2338.field_10980, var2, var3);
         this.plan = this.plan.moved(this.inFrontOfPlayer(this.plan.sizeX, this.plan.sizeZ));
         this.followCrosshair = true;
         this.phase = BuildRuntime.Phase.PREVIEW;
         this.status = "Preview – aim, then Enter";
         Notifications.info("AutoBuild", "Preview: aim with the crosshair · arrows/PgUp/PgDn move · R rotate · M mirror · Enter build · Backspace cancel");
      }
   }

   public void start(Schematic var1) {
      this.start(var1, class_2470.field_11467, class_2415.field_11302);
   }

   public void start(Schematic var1, class_2470 var2, class_2415 var3) {
      this.stop();
      if (mc.field_1724 != null) {
         this.plan = new BuildPlan(var1, class_2338.field_10980, var2, var3);
         this.plan = this.plan.moved(this.inFrontOfPlayer(this.plan.sizeX, this.plan.sizeZ));
         this.begin();
      }
   }

   public void start(BuildPlan var1) {
      this.stop();
      this.plan = var1;
      this.begin();
   }

   public void confirm() {
      if (this.phase == BuildRuntime.Phase.PREVIEW && this.plan != null) {
         if (this.verifyAfterPreview) {
            this.beginVerify();
         } else {
            this.begin();
         }
      }
   }

   private void begin() {
      int var1 = this.plan.blocks.size();
      this.done = new boolean[var1];
      this.attempts = new int[var1];
      this.retryAt = new int[var1];
      this.predictWaits = new int[var1];
      this.lastDeferred.clear();
      this.layerStart = new int[this.plan.sizeY];
      this.layerEnd = new int[this.plan.sizeY];
      this.layerOpen = new int[this.plan.sizeY];
      Arrays.fill(this.layerStart, -1);

      for (int var2 = 0; var2 < var1; var2++) {
         int var3 = this.plan.blocks.get(var2).layer();
         if (this.layerStart[var3] < 0) {
            this.layerStart[var3] = var2;
         }

         this.layerEnd[var3] = var2 + 1;
         this.layerOpen[var3]++;
      }

      this.doneCount = 0;
      this.verifyCursor = 0;
      this.layer = 0;
      this.indexCache.clear();
      this.noProgress = 0;
      this.lastDoneSeen = -1;
      this.waterTicks = 0;
      this.escaping = false;
      this.waterBest = Double.MAX_VALUE;
      this.wait = 0;
      this.stuckTicks = 0;
      this.lastPos = null;
      this.placeTimes.clear();
      this.startedAt = System.currentTimeMillis();
      this.restock.forget();

      for (int var4 = 0; var4 < var1; var4++) {
         this.verify(var4);
      }

      this.doneAtStart = this.doneCount;
      this.activeMillis = 0L;
      this.lastActiveAt = 0L;
      this.phase = BuildRuntime.Phase.BUILDING;
      this.status = "Building " + this.plan.schematic.name();
      this.prepareMaterials = true;
      this.prepareAnnounced = false;
      this.prepareBuying = false;
      this.prepareItem = null;
      this.prepareBuys = 0;
      this.prepareFailed.clear();
      this.save();
      Notifications.info(
         "AutoBuild",
         "Building "
            + this.plan.schematic.name()
            + " at "
            + this.plan.anchor.method_10263()
            + " "
            + this.plan.anchor.method_10264()
            + " "
            + this.plan.anchor.method_10260()
      );
   }

   public void pauseToggle() {
      if (this.phase == BuildRuntime.Phase.BUILDING) {
         this.phase = BuildRuntime.Phase.PAUSED;
         this.status = "Paused";
         this.releaseKeys();
         this.sneakKey(false);
         this.pending = null;
         this.pendingSupport = null;
         this.pendingIndex = -1;
         this.mining = null;
         mc.field_1761.method_2925();
      } else if (this.phase == BuildRuntime.Phase.PAUSED) {
         this.phase = BuildRuntime.Phase.BUILDING;
         this.status = "Building";
      }
   }

   public void stop() {
      this.releaseKeys();
      this.sneakKey(false);
      PlacementSolver.sneakInteractive = false;
      if (this.mining != null && mc.field_1761 != null) {
         mc.field_1761.method_2925();
      }

      this.mining = null;
      this.adjusting = null;
      this.pending = null;
      this.pendingSupport = null;
      this.pendingIndex = -1;
      this.reposition = null;
      this.supports.clear();
      this.towerBase = null;
      this.indexCache.clear();
      this.autoBuy.reset();
      this.restock.reset();
      this.verifyAfterPreview = false;
      this.verifyClass = null;
      this.plan = null;
      this.phase = BuildRuntime.Phase.IDLE;
      this.status = "Idle";
   }

   public void skipLayer() {
      if (this.plan != null && this.phase == BuildRuntime.Phase.BUILDING && this.layer >= 0) {
         for (int var1 = this.layerStart[this.layer]; var1 >= 0 && var1 < this.layerEnd[this.layer]; var1++) {
            if (!this.done[var1]) {
               this.attempts[var1] = 1000;
            }
         }

         Notifications.info("AutoBuild", "Skipped layer " + (this.layer + 1));
      }
   }

   public void retrySkipped() {
      if (this.tracked()) {
         for (int var1 = 0; var1 < this.attempts.length; var1++) {
            if (!this.done[var1]) {
               this.attempts[var1] = 0;
               this.retryAt[var1] = 0;
               this.predictWaits[var1] = 0;
            }
         }

         this.prepareFailed.clear();
         if (this.phase == BuildRuntime.Phase.FINISHED) {
            this.phase = BuildRuntime.Phase.BUILDING;
         }
      }
   }

   private class_2338 inFrontOfPlayer(int var1, int var2) {
      class_2350 var3 = mc.field_1724.method_5735();
      class_2338 var4 = mc.field_1724.method_24515();
      int var5 = var4.method_10263() + var3.method_10148() * 2;
      int var6 = var4.method_10260() + var3.method_10165() * 2;
      if (var3.method_10148() < 0) {
         var5 -= var1 - 1;
      }

      if (var3.method_10165() < 0) {
         var6 -= var2 - 1;
      }

      if (var3.method_10166() == class_2351.field_11048) {
         var6 -= var2 / 2;
      } else {
         var5 -= var1 / 2;
      }

      return new class_2338(var5, var4.method_10264(), var6);
   }

   public void move(int var1, int var2, int var3) {
      if (this.plan != null) {
         this.followCrosshair = false;
         this.plan = this.plan.moved(this.plan.anchor.method_10069(var1, var2, var3));
      }
   }

   public void moveRelative(int var1, int var2) {
      class_2350 var3 = mc.field_1724.method_5735();
      class_2350 var4 = var3.method_10170();
      this.move(var3.method_10148() * var1 + var4.method_10148() * var2, 0, var3.method_10165() * var1 + var4.method_10165() * var2);
   }

   public void rotate() {
      if (this.plan != null) {
         this.plan = this.plan.rotated();
      }
   }

   public void mirror() {
      if (this.plan != null) {
         this.plan = this.plan.mirrored();
      }
   }

   public void toggleFollow() {
      this.followCrosshair = !this.followCrosshair;
   }

   private void updateFollow() {
      if (this.followCrosshair && mc.field_1765 instanceof class_3965 var1 && var1.method_17783() != class_240.field_1333) {
         class_2338 var6 = var1.method_17777().method_10093(var1.method_17780());
         class_2338 var3 = new class_2338(var6.method_10263() - this.plan.sizeX / 2, var6.method_10264(), var6.method_10260() - this.plan.sizeZ / 2);
         if (this.plan.schematic.mapGrid) {
            int var4 = Math.floorDiv(var6.method_10263() + 64, 128) * 128 - 64;
            int var5 = Math.floorDiv(var6.method_10260() + 64, 128) * 128 - 64;
            var3 = new class_2338(var4, var6.method_10264(), var5 - (this.plan.schematic.mapNoobline ? 1 : 0));
         }

         if (!var3.equals(this.plan.anchor)) {
            this.plan = this.plan.moved(var3);
         }
      }
   }

   private BuildRuntime.Status statusOf(int var1, BuildRuntime.Settings var2) {
      BuildPlan.Planned var3 = this.plan.blocks.get(var1);
      class_2680 var4 = mc.field_1687.method_8320(var3.pos());
      class_2680 var5 = var3.state();
      if (BuildPlan.isFluid(var5)) {
         if (var4.method_26204() == var5.method_26204() && var4.method_26227().method_15771()) {
            return BuildRuntime.Status.DONE;
         } else {
            return var4.method_45474() ? BuildRuntime.Status.OPEN : BuildRuntime.Status.WRONG;
         }
      } else if (var4.method_26204() != var5.method_26204()) {
         return var4.method_45474() ? BuildRuntime.Status.OPEN : BuildRuntime.Status.WRONG;
      } else if (!PlacementSolver.placementMatches(var4, var5)) {
         return BuildRuntime.Status.WRONG;
      } else if (BuildPlan.pieces(var4) < BuildPlan.pieces(var5)) {
         return BuildRuntime.Status.INCREMENT;
      } else {
         return var2 != null && var2.adjustStates && adjustClicks(var4, var5) > 0 ? BuildRuntime.Status.ADJUST : BuildRuntime.Status.DONE;
      }
   }

   private void verify(int var1) {
      boolean var2 = this.statusOf(var1, this.lastSettings) == BuildRuntime.Status.DONE;
      if (var2 != this.done[var1]) {
         this.done[var1] = var2;
         int var3 = this.plan.blocks.get(var1).layer();
         this.doneCount += var2 ? 1 : -1;
         this.layerOpen[var3] = this.layerOpen[var3] + (var2 ? -1 : 1);
      }
   }

   private static int adjustClicks(class_2680 var0, class_2680 var1) {
      class_2248 var2 = var1.method_26204();
      if (var1.method_28498(class_2741.field_12494)) {
         return Math.floorMod((Integer)var1.method_11654(class_2741.field_12494) - (Integer)var0.method_11654(class_2741.field_12494), 4);
      } else if (var1.method_28498(class_2741.field_12534)) {
         return var0.method_11654(class_2741.field_12534) != var1.method_11654(class_2741.field_12534) ? 1 : 0;
      } else if (var1.method_28498(class_2741.field_12524)) {
         return Math.floorMod((Integer)var1.method_11654(class_2741.field_12524) - (Integer)var0.method_11654(class_2741.field_12524), 25);
      } else if (var1.method_28498(class_2741.field_12501)) {
         return var0.method_11654(class_2741.field_12501) != var1.method_11654(class_2741.field_12501) ? 1 : 0;
      } else {
         boolean var3 = (var2 instanceof class_2323 || var2 instanceof class_2533 || var2 instanceof class_2349)
            && var2 != class_2246.field_9973
            && var2 != class_2246.field_10453;
         if (var3 && var1.method_28498(class_2741.field_12537)) {
            return var0.method_11654(class_2741.field_12537) != var1.method_11654(class_2741.field_12537) ? 1 : 0;
         } else if (var2 instanceof class_2401) {
            return var0.method_11654(class_2741.field_12484) != var1.method_11654(class_2741.field_12484) ? 1 : 0;
         } else {
            return 0;
         }
      }
   }

   public void tick(BuildRuntime.Settings var1) {
      if (this.plan != null && mc.field_1724 != null && mc.field_1687 != null) {
         this.lastSettings = var1;
         PlacementSolver.sneakInteractive = var1.sneakMode != 0;
         this.tick++;
         if (this.giveCooldown > 0) {
            this.giveCooldown--;
         }

         if (this.phase == BuildRuntime.Phase.PREVIEW) {
            this.updateFollow();
         } else if (this.phase == BuildRuntime.Phase.VERIFY) {
            this.tickVerify();
         } else if (this.phase == BuildRuntime.Phase.BUILDING) {
            long var2 = System.currentTimeMillis();
            if (this.lastActiveAt > 0L && mc.field_1755 == null) {
               this.activeMillis = this.activeMillis + Math.min(1000L, var2 - this.lastActiveAt);
            }

            this.lastActiveAt = var2;
            if (this.tick % 200 == 0) {
               this.save();
            }

            String var4 = this.pauseReason(var1);
            if (var4 != null) {
               this.status = var4;
               this.releaseKeys();
               this.sneakKey(false);
               this.pending = null;
               this.pendingSupport = null;
               this.pendingIndex = -1;
               this.reposition = null;
               if (this.mining != null) {
                  mc.field_1761.method_2925();
                  this.mining = null;
               }

               return;
            }

            if (this.autoBuy.isActive()) {
               this.status = this.autoBuy.tick();
            } else if (this.restock.isActive() && this.pending != null) {
               this.tickPending(var1);
            } else if (this.restock.isActive()) {
               class_2338 var5 = this.restock.target();
               if (var5 != null && var1.mode == BuildRuntime.Mode.AUTO) {
                  this.walkTo(class_243.method_24953(var5), var1.reach);
               } else {
                  this.releaseKeys();
               }

               this.restock.human = var1.human;
               this.restock.speed = var1.rotateSpeed;
               this.restock.maxStacks = var1.human ? Math.min(var1.restockPerTick, 2) : var1.restockPerTick;
               this.status = this.restock.tick(var1.reach);
            } else if (mc.field_1755 != null) {
               this.status = "Paused (screen open)";
               this.releaseKeys();
               this.sneakKey(false);
            } else if (this.prepareMaterials && var1.checkMaterials && this.prepareStep(var1)) {
               this.releaseKeys();
            } else if (var1.restock && var1.restockEarly && this.tick % 40 == 0 && this.earlyRestock(var1)) {
               this.releaseKeys();
            } else {
               for (int var14 = 0; var14 < 3000 && this.plan.blocks.size() > 0; var14++) {
                  this.verify(this.verifyCursor);
                  this.verifyCursor = (this.verifyCursor + 1) % this.plan.blocks.size();
               }

               this.layer = this.currentLayer();
               if (this.pending == null && this.mining == null && this.escapeWater(var1)) {
                  return;
               }

               if (this.watchdog(var1)) {
                  return;
               }

               if (this.mining != null) {
                  this.tickMining(var1);
               } else if (this.pending != null) {
                  this.tickPending(var1);
               } else if (this.reposition != null) {
                  this.tickReposition(var1);
               } else if (this.layer < 0) {
                  if (!this.cleanupSupports(var1)) {
                     this.finish();
                  }
               } else if (this.adjusting != null) {
                  this.tickAdjust();
               } else if (this.towerBase != null) {
                  this.tickTower(var1);
               } else if (this.wait > 0) {
                  this.wait--;
               } else {
                  List var15 = this.reachableCandidates(var1);
                  int var6 = 0;
                  this.missing.clear();

                  for (int var8 : var15) {
                     if (var6 >= var1.perTick) {
                        break;
                     }

                     if (this.retryAt[var8] <= this.tick) {
                        BuildPlan.Planned var9 = this.plan.blocks.get(var8);
                        BuildRuntime.Status var10 = this.statusOf(var8, var1);
                        switch (var10) {
                           case DONE:
                              this.verify(var8);
                              break;
                           case OPEN:
                           case INCREMENT:
                              if (this.tryPlace(var8, var10, var1)) {
                                 var6++;
                              }

                              if (this.pending != null) {
                                 this.status = "Placing " + var9.state().method_26204().method_9518().getString();
                                 return;
                              }
                              break;
                           case ADJUST:
                              this.releaseKeys();
                              if (++this.attempts[var8] <= var1.maxAttempts * 2) {
                                 this.miningAligned = false;
                                 this.adjusting = var9.pos();
                                 this.adjustClicks = adjustClicks(mc.field_1687.method_8320(var9.pos()), var9.state());
                                 this.adjustWait = 0;
                                 return;
                              }

                              this.attempts[var8] = 1000;
                              break;
                           case WRONG:
                              if (var1.fixWrong) {
                                 this.startMining(var9.pos());
                                 return;
                              }

                              this.attempts[var8] = 1000;
                        }
                     }
                  }

                  if (var6 > 0) {
                     this.wait = var1.delay;
                     this.releaseKeys();
                     this.status = String.format(Locale.ROOT, "Layer %d/%d · %d/%d", this.layer + 1, this.plan.sizeY, this.doneCount, this.plan.blocks.size());
                  } else if (!var1.clearArea || !this.tryClearArea(var1)) {
                     if (!this.missing.isEmpty()) {
                        class_1792 var16 = this.missing.keySet().iterator().next();
                        if (this.handleMissing(var16, var1)) {
                           return;
                        }

                        if (!this.status.startsWith("Inventory full")) {
                           this.status = "Missing: " + var16.method_63680().getString() + " x" + this.missing.get(var16);
                        }
                     }

                     if (var1.mode == BuildRuntime.Mode.PRINTER) {
                        this.releaseKeys();
                        if (this.missing.isEmpty()) {
                           this.status = "Printer: move near the build (" + this.doneCount + "/" + this.plan.blocks.size() + ")";
                        }
                     } else {
                        int var17 = this.nearestOpenInLayer();
                        if (var17 >= 0 && var1.walk) {
                           BuildPlan.Planned var18 = this.plan.blocks.get(var17);
                           class_243 var19 = class_243.method_24953(var18.pos());
                           double var20 = Math.hypot(var19.field_1352 - mc.field_1724.method_23317(), var19.field_1350 - mc.field_1724.method_23321());
                           double var12 = var18.pos().method_10264() - mc.field_1724.method_23318();
                           if (var1.pillar && var12 > var1.reach - 1.6 && var20 < var1.reach + 1.0 && mc.field_1724.method_24828()) {
                              this.startTower(var1);
                              return;
                           }

                           this.walkTo(var19, var1.reach);
                           if (this.missing.isEmpty()) {
                              this.status = "Walking to layer " + (this.layer + 1);
                           }
                        } else {
                           this.releaseKeys();
                           if (this.missing.isEmpty()) {
                              this.status = "Waiting (layer " + (this.layer + 1) + ")";
                           }
                        }
                     }
                  }
               }
            }
         }
      }
   }

   private boolean inRange(int var1) {
      BuildRuntime.Settings var2 = this.lastSettings;
      return var2 == null || var1 + 1 >= var2.fromLayer && (var2.toLayer <= 0 || var1 + 1 <= var2.toLayer);
   }

   private int currentLayer() {
      int var1 = -1;

      for (int var2 = 0; var2 < this.plan.sizeY; var2++) {
         if (this.layerStart[var2] >= 0 && this.inRange(var2)) {
            for (int var3 = this.layerStart[var2]; var3 < this.layerEnd[var2]; var3++) {
               if (!this.done[var3] && this.attempts[var3] < 1000) {
                  if (!this.lastDeferred.contains(var3)) {
                     return var2;
                  }

                  if (var1 < 0) {
                     var1 = var2;
                  }
               }
            }
         }
      }

      return var1;
   }

   private String pauseReason(BuildRuntime.Settings var1) {
      class_746 var2 = mc.field_1724;
      long var3 = System.currentTimeMillis();
      if (ModuleManager.on(AutoEat.class) && ModuleManager.of(AutoEat.class).isEating()) {
         return "Eating …";
      } else {
         if (var1.pauseDamage > 0) {
            int var5 = var2.field_6235;
            if (var5 > this.lastHurt) {
               this.damagePauseUntil = var3 + var1.pauseDamage * 1000L;
               Notifications.warn("AutoBuild", "Took damage – paused for " + var1.pauseDamage + "s");
            }

            this.lastHurt = var5;
            if (var3 < this.damagePauseUntil) {
               return "Paused – took damage (" + ((this.damagePauseUntil - var3) / 1000L + 1L) + "s)";
            }
         }

         if (var1.pausePlayers > 0) {
            for (class_742 var6 : mc.field_1687.method_18456()) {
               if (var6 != var2 && !DIHClient.social().isFriend(var6) && var2.method_5739(var6) <= var1.pausePlayers) {
                  String var7 = var6.method_7334().name();
                  if (!var7.equals(this.pausedFor)) {
                     this.pausedFor = var7;
                     Notifications.warn("AutoBuild", var7 + " is near – building paused");
                  }

                  return "Paused – " + var7 + " is " + (int)var2.method_5739(var6) + "m away";
               }
            }

            this.pausedFor = null;
         }

         return null;
      }
   }

   private void finish() {
      if (this.lastSettings != null && this.lastSettings.finishSound && mc.field_1724 != null) {
         mc.field_1724.method_5783((class_3414)class_3417.field_14622.comp_349(), 1.0F, 1.2F);
      }

      this.releaseKeys();
      this.sneakKey(false);
      int var1 = this.skippedCountNow();
      this.phase = BuildRuntime.Phase.FINISHED;
      this.status = var1 > 0 ? "Finished – " + var1 + " blocks skipped (Retry Skipped)" : "Finished " + this.plan.schematic.name();
      Notifications.push("AutoBuild", this.status, Notifications.Type.SUCCESS);
      deleteSave();
   }

   private List<Integer> reachableCandidates(BuildRuntime.Settings var1) {
      ArrayList var2 = new ArrayList();
      class_243 var3 = mc.field_1724.method_33571();
      int var4 = (int)Math.ceil(var1.reach) + 1;
      class_2338 var5 = class_2338.method_49638(var3);
      int var6 = var1.order == BuildRuntime.Order.LAYERS && var1.mode == BuildRuntime.Mode.AUTO ? this.layer : Integer.MAX_VALUE;
      class_2339 var7 = new class_2339();

      for (int var8 = -var4; var8 <= var4; var8++) {
         for (int var9 = -var4; var9 <= var4; var9++) {
            for (int var10 = -var4; var10 <= var4; var10++) {
               var7.method_10103(var5.method_10263() + var8, var5.method_10264() + var9, var5.method_10260() + var10);
               if (this.plan.contains(var7)) {
                  BuildPlan.Planned var11 = this.plan.at(var7);
                  if (var11 != null && var11.layer() <= var6 && !(distSq(var7, var3) > (var1.reach + 0.5) * (var1.reach + 0.5))) {
                     int var12 = this.indexOf(var11);
                     if (var12 >= 0 && !this.done[var12] && this.attempts[var12] < 1000 && this.inRange(var11.layer())) {
                        var2.add(var12);
                     }
                  }
               }
            }
         }
      }

      int var17 = var2.size();
      int[] var18 = new int[var17];
      double[] var19 = new double[var17];

      for (int var20 = 0; var20 < var17; var20++) {
         BuildPlan.Planned var22 = this.plan.blocks.get((Integer)var2.get(var20));
         var18[var20] = var22.layer() * 2 + (var22.state().method_26234(mc.field_1687, var22.pos()) ? 0 : 1);
         var19[var20] = distSq(var22.pos(), var3);
      }

      Integer[] var21 = new Integer[var17];

      for (int var23 = 0; var23 < var17; var23++) {
         var21[var23] = var23;
      }

      Arrays.sort(
         var21, (var2x, var3x) -> var18[var2x] != var18[var3x] ? Integer.compare(var18[var2x], var18[var3x]) : Double.compare(var19[var2x], var19[var3x])
      );
      ArrayList var24 = new ArrayList(var17);

      for (Integer var16 : var21) {
         var24.add((Integer)var2.get(var16));
      }

      return var24;
   }

   private int indexOf(BuildPlan.Planned var1) {
      if (this.indexCache.isEmpty() || this.indexCache.size() != this.plan.blocks.size()) {
         this.indexCache.clear();

         for (int var2 = 0; var2 < this.plan.blocks.size(); var2++) {
            this.indexCache.put(this.plan.blocks.get(var2).pos().method_10063(), var2);
         }
      }

      return this.indexCache.getOrDefault(var1.pos().method_10063(), -1);
   }

   private int nearestOpenInLayer() {
      if (this.layer >= 0 && this.layerStart[this.layer] >= 0) {
         class_243 var1 = mc.field_1724.method_33571();
         int var2 = -1;
         double var3 = Double.MAX_VALUE;
         int var5 = Integer.MAX_VALUE;

         for (int var6 = this.layerStart[this.layer]; var6 < this.layerEnd[this.layer]; var6++) {
            if (!this.done[var6] && this.attempts[var6] < 1000) {
               BuildPlan.Planned var7 = this.plan.blocks.get(var6);
               int var8 = this.lastDeferred.contains(var6) ? 2 : (!this.missing.isEmpty() && this.missing.containsKey(BuildPlan.itemOf(var7.state())) ? 1 : 0);
               if (var8 <= var5) {
                  double var9 = distSq(var7.pos(), var1);
                  if (var8 < var5 || var9 < var3) {
                     var5 = var8;
                     var3 = var9;
                     var2 = var6;
                  }
               }
            }
         }

         return var2;
      } else {
         return -1;
      }
   }

   private boolean tryPlace(int var1, BuildRuntime.Status var2, BuildRuntime.Settings var3) {
      BuildPlan.Planned var4 = this.plan.blocks.get(var1);
      if (var3.predict && var2 == BuildRuntime.Status.OPEN && !this.predictOk(var1, var4)) {
         return false;
      } else if (BuildPlan.isFluid(var4.state())) {
         return this.tryBucket(var1, var2, var4, var3);
      } else if (var2 == BuildRuntime.Status.OPEN
         && mc.field_1724.method_5829().method_994(new class_238(var4.pos()))
         && var4.state().method_26234(mc.field_1687, var4.pos())) {
         if (var3.mode == BuildRuntime.Mode.AUTO && var3.walk && this.reposition == null) {
            class_243 var11 = this.stepAway(var4.pos());
            if (var11 != null) {
               this.reposition = var11;
               this.repositionTicks = 0;
               this.status = "Stepping aside to place under / at my feet";
            }
         }

         this.retryAt[var1] = this.tick + 4;
         return false;
      } else if (!(var4.state().method_26204() instanceof class_2346) || !mc.field_1687.method_8320(var4.pos().method_10074()).method_45474()) {
         class_1792 var5 = BuildPlan.itemOf(var4.state());
         int var6 = this.ensureInHotbar(var5, var3);
         if (var6 < 0) {
            this.missing.merge(var5, 1, Integer::sum);
            this.retryAt[var1] = this.tick + 20;
            return false;
         } else {
            class_1799 var7 = mc.field_1724.method_31548().method_5438(var6);
            PlacementSolver.Click var8 = var2 == BuildRuntime.Status.INCREMENT
               ? PlacementSolver.solveIncrement(var4.pos(), mc.field_1687.method_8320(var4.pos()), var4.state(), var7, var3.reach, var3.strict)
               : PlacementSolver.solve(var4.pos(), var4.state(), var7, var3.reach, var3.strict);
            if (var8 != null && !var8.exact()) {
               var8 = null;
            }

            if (var8 == null && var2 == BuildRuntime.Status.OPEN) {
               int var9 = this.ensureAttachment(var1, var4, var3);
               if (var9 != 0) {
                  return var9 > 0;
               }
            }

            if (var8 == null && var2 == BuildRuntime.Status.OPEN && var3.airPlace && !var3.human) {
               var8 = airClick(var4.pos(), var4.state(), var3.reach);
            }

            if (var8 != null) {
               if (var3.human) {
                  this.beginPending(var1, null, var8, var6);
                  return true;
               } else {
                  InvUtil.select(var6);
                  if (this.needSneak(var8.hit(), var3) && !mc.field_1724.method_5715()) {
                     this.sneakKey(true);
                     this.retryAt[var1] = this.tick + 1;
                     this.status = "Sneaking to place";
                     return false;
                  } else if (!this.click(var8, var3)) {
                     this.attempts[var1]++;
                     this.retryAt[var1] = this.tick + 10;
                     return false;
                  } else {
                     this.attempts[var1]++;
                     this.retryAt[var1] = this.tick + 6;
                     if (this.attempts[var1] > var3.maxAttempts) {
                        this.attempts[var1] = 1000;
                     }

                     this.placeTimes.addLast(System.currentTimeMillis());
                     if (var3.sneakMode == 1) {
                        this.sneakKey(false);
                     }

                     return true;
                  }
               }
            } else {
               boolean var12 = var2 == BuildRuntime.Status.OPEN && PlacementSolver.hasNeighbor(var4.pos());
               if (var2 == BuildRuntime.Status.OPEN && !var12 && var3.supports && this.trySupport(var4.pos(), var3)) {
                  return true;
               } else {
                  if (var3.human && var3.legitFallback && var3.strict && this.attempts[var1] >= 2) {
                     PlacementSolver.Click var10 = var2 == BuildRuntime.Status.INCREMENT
                        ? PlacementSolver.solveIncrement(var4.pos(), mc.field_1687.method_8320(var4.pos()), var4.state(), var7, var3.reach, false)
                        : PlacementSolver.solve(var4.pos(), var4.state(), var7, var3.reach, false);
                     if (var10 != null && var10.exact()) {
                        this.beginPending(var1, null, var10, var6);
                        return true;
                     }
                  }

                  if (var3.strict
                     && var3.mode == BuildRuntime.Mode.AUTO
                     && var3.walk
                     && this.reposition == null
                     && (var12 || var2 == BuildRuntime.Status.INCREMENT)) {
                     class_243 var13 = this.findStandSpot(var4, var7, var3);
                     if (var13 != null) {
                        this.reposition = var13;
                        this.repositionTicks = 0;
                        this.retryAt[var1] = this.tick + 2;
                        if (++this.attempts[var1] > var3.maxAttempts * 4) {
                           this.attempts[var1] = 1000;
                        }

                        return false;
                     }
                  }

                  if (var2 == BuildRuntime.Status.OPEN && var3.supports && this.axisSupport(var4, var3)) {
                     return true;
                  } else {
                     this.retryAt[var1] = this.tick + 10;
                     if (++this.attempts[var1] > var3.maxAttempts * 4) {
                        this.attempts[var1] = 1000;
                     }

                     return false;
                  }
               }
            }
         }
      } else if (var3.supports && this.plan.at(var4.pos().method_10074()) == null && this.placeSupport(var4.pos().method_10074(), var3)) {
         return true;
      } else {
         this.retryAt[var1] = this.tick + 20;
         return false;
      }
   }

   private boolean needSneak(class_3965 var1, BuildRuntime.Settings var2) {
      return var2.sneakMode == 0 ? false : var2.sneakMode == 2 || PlacementSolver.interactive(mc.field_1687.method_8320(var1.method_17777()));
   }

   private void sneakKey(boolean var1) {
      if (mc.field_1690 != null) {
         if (var1) {
            mc.field_1690.field_1832.method_23481(true);
            this.placeSneak = true;
         } else if (this.placeSneak) {
            mc.field_1690.field_1832.method_23481(this.sneaking || KeyUtil.isPhysicallyDown(mc.field_1690.field_1832));
            this.placeSneak = false;
         }
      }
   }

   private boolean finalPhase(int var1) {
      for (int var2 = 0; var2 < this.plan.blocks.size(); var2++) {
         if (var2 != var1
            && !this.done[var2]
            && this.attempts[var2] < 1000
            && !this.lastDeferred.contains(var2)
            && this.inRange(this.plan.blocks.get(var2).layer())) {
            return false;
         }
      }

      return true;
   }

   private static boolean isItem(class_1799 var0, String var1) {
      return !var0.method_7960() && ItemUtil.id(var0).equals(var1);
   }

   private boolean tryBucket(int var1, BuildRuntime.Status var2, BuildPlan.Planned var3, BuildRuntime.Settings var4) {
      if (var2 != BuildRuntime.Status.OPEN) {
         this.retryAt[var1] = this.tick + 20;
         return false;
      } else {
         String var5 = BuildPlan.bucketId(var3.state());
         class_1792 var6 = BuildPlan.itemOf(var3.state());
         int var7 = InvUtil.findHotbar((Predicate<class_1799>)(var1x -> isItem(var1x, var5)));
         if (var7 < 0) {
            if (this.refillBucket(var1, var3.state(), var4)) {
               return true;
            }

            var7 = var6 == null ? -1 : this.ensureInHotbar(var6, var4);
            if (var7 < 0) {
               if (var6 != null) {
                  this.missing.merge(var6, 1, Integer::sum);
               }

               this.retryAt[var1] = this.tick + 20;
               return false;
            }
         }

         PlacementSolver.Click var8 = bucketClick(var3.pos(), var4.reach);
         if (var8 == null) {
            this.retryAt[var1] = this.tick + 10;
            if (++this.attempts[var1] > var4.maxAttempts * 4) {
               this.attempts[var1] = 1000;
            }

            this.status = "Bucket: no free face to pour " + var5.substring(10).replace("_bucket", "") + " into";
            return false;
         } else if (var4.human) {
            this.beginPending(var1, null, var8, var7);
            this.pendingBucket = true;
            return true;
         } else {
            InvUtil.select(var7);
            boolean var9 = this.useBucket(var8, var4);
            this.attempts[var1]++;
            this.retryAt[var1] = this.tick + (var9 ? 6 : 10);
            if (this.attempts[var1] > var4.maxAttempts) {
               this.attempts[var1] = 1000;
            }

            if (var9) {
               this.placeTimes.addLast(System.currentTimeMillis());
            }

            return var9;
         }
      }
   }

   private boolean refillBucket(int var1, class_2680 var2, BuildRuntime.Settings var3) {
      int var4 = InvUtil.findHotbar((Predicate<class_1799>)(var0 -> isItem(var0, "minecraft:bucket")));
      if (var4 < 0) {
         int var5 = InvUtil.findInventory(var0 -> isItem(var0, "minecraft:bucket"));
         if (var5 < 9) {
            return false;
         }

         var4 = this.freeHotbarSlot();
         InvUtil.swapToHotbar(var5, var4);
      }

      class_243 var18 = mc.field_1724.method_33571();
      int var6 = (int)Math.ceil(var3.reach);
      class_2338 var7 = class_2338.method_49638(var18);
      class_2338 var8 = null;
      double var9 = Double.MAX_VALUE;

      for (int var11 = -var6; var11 <= var6; var11++) {
         for (int var12 = -var6; var12 <= var6; var12++) {
            for (int var13 = -var6; var13 <= var6; var13++) {
               class_2338 var14 = var7.method_10069(var11, var12, var13);
               class_2680 var15 = mc.field_1687.method_8320(var14);
               if (var15.method_26204() == var2.method_26204() && var15.method_26227().method_15771() && this.plan.at(var14) == null) {
                  double var16 = class_243.method_24953(var14).method_1022(var18);
                  if (var16 <= var3.reach && var16 < var9) {
                     var8 = var14;
                     var9 = var16;
                  }
               }
            }
         }
      }

      if (var8 == null) {
         return false;
      } else {
         class_243 var19 = class_243.method_24953(var8);
         float[] var20 = RotationUtil.rotationsTo(var19);
         PlacementSolver.Click var21 = new PlacementSolver.Click(new class_3965(var19, class_2350.field_11036, var8, false), var20[0], var20[1], true);
         this.status = "Bucket: filling up";
         if (var3.human) {
            this.beginPending(var1, null, var21, var4);
            this.pendingBucket = true;
            return true;
         } else {
            InvUtil.select(var4);
            return this.useBucket(var21, var3);
         }
      }
   }

   private static PlacementSolver.Click bucketClick(class_2338 var0, double var1) {
      class_243 var3 = mc.field_1724.method_33571();
      PlacementSolver.Click var4 = null;
      double var5 = Double.MAX_VALUE;

      for (class_2350 var10 : class_2350.values()) {
         class_2338 var11 = var0.method_10093(var10);
         class_2680 var12 = mc.field_1687.method_8320(var11);
         if (!var12.method_45474()
            && var12.method_26227().method_15769()
            && !String.valueOf(var12).contains("waterlogged=")
            && !var12.method_26218(mc.field_1687, var11).method_1110()) {
            class_243 var13 = class_243.method_24953(var0).method_1031(var10.method_10148() * 0.5, var10.method_10164() * 0.5, var10.method_10165() * 0.5);
            double var14 = var13.method_1022(var3);
            class_2350 var16 = var10.method_10153();
            if (var14 <= var1 && var14 < var5 && PlacementSolver.visible(var13, var11, var16)) {
               float[] var17 = RotationUtil.rotationsTo(var13);
               var4 = new PlacementSolver.Click(new class_3965(var13, var16, var11, false), var17[0], var17[1], true);
               var5 = var14;
            }
         }
      }

      return var4;
   }

   private boolean useBucket(PlacementSolver.Click var1, BuildRuntime.Settings var2) {
      float var3 = mc.field_1724.method_36454();
      float var4 = mc.field_1724.method_36455();
      mc.field_1724.method_36456(var1.yaw());
      mc.field_1724.method_36457(var1.pitch());
      mc.field_1724.field_3944.method_52787(new class_2831(var1.yaw(), var1.pitch(), mc.field_1724.method_24828(), mc.field_1724.field_5976));
      class_1269 var5 = mc.field_1761.method_2919(mc.field_1724, class_1268.field_5808);
      if (var5.method_23665()) {
         mc.field_1724.method_6104(class_1268.field_5808);
      }

      if (var2.silentRotate) {
         mc.field_1724.method_36456(var3);
         mc.field_1724.method_36457(var4);
      }

      return var5.method_23665();
   }

   private boolean predictOk(int var1, BuildPlan.Planned var2) {
      Predict.Verdict var3 = Predict.check(this.plan, var2, BuildPlan.isFluid(var2.state()) && this.finalPhase(var1));
      if (var3 == null) {
         this.lastDeferred.remove(var1);
         this.predictWaits[var1] = 0;
         return true;
      } else {
         String var4 = var2.state().method_26204().method_9518().getString();
         if (var3.kind() == 2) {
            this.lastDeferred.add(var1);
            if (this.finalPhase(var1)) {
               return true;
            } else {
               this.retryAt[var1] = this.tick + 40;
               this.status = "Predict: " + var4 + " last – " + var3.why();
               return false;
            }
         } else if (var3.kind() == 1 && ++this.predictWaits[var1] <= 40) {
            this.retryAt[var1] = this.tick + 30;
            this.status = "Predict: waiting for " + var4 + " – " + var3.why();
            return false;
         } else {
            this.attempts[var1] = 1000;
            this.lastDeferred.remove(var1);
            Notifications.warn("AutoBuild Predict", var4 + " skipped: " + var3.why());
            this.status = "Predict: skipped " + var4 + " – " + var3.why();
            return false;
         }
      }
   }

   private static PlacementSolver.Click airClick(class_2338 var0, class_2680 var1, double var2) {
      class_243 var4 = class_243.method_24953(var0);
      if (var4.method_1022(mc.field_1724.method_33571()) > var2 + 0.5) {
         return null;
      } else {
         class_2350 var5 = class_2350.field_11036;
         String var6 = prop(var1, "axis");
         String var7 = prop(var1, "half");
         String var8 = prop(var1, "type");
         if ("x".equals(var6)) {
            var5 = class_2350.field_11034;
         } else if ("z".equals(var6)) {
            var5 = class_2350.field_11035;
         } else if ("top".equals(var7) || "top".equals(var8)) {
            var5 = class_2350.field_11033;
         }

         float[] var9 = RotationUtil.rotationsTo(var4);
         return new PlacementSolver.Click(new class_3965(var4, var5, var0, false), var9[0], var9[1], true);
      }
   }

   private boolean click(PlacementSolver.Click var1, BuildRuntime.Settings var2) {
      if (var2.human) {
         class_1269 var6 = mc.field_1761.method_2896(mc.field_1724, class_1268.field_5808, var1.hit());
         if (var6.method_23665()) {
            mc.field_1724.method_6104(class_1268.field_5808);
         }

         return var6.method_23665();
      } else {
         float var3 = mc.field_1724.method_36454();
         float var4 = mc.field_1724.method_36455();
         mc.field_1724.method_36456(var1.yaw());
         mc.field_1724.method_36457(var1.pitch());
         mc.field_1724.field_3944.method_52787(new class_2831(var1.yaw(), var1.pitch(), mc.field_1724.method_24828(), mc.field_1724.field_5976));
         class_1269 var5 = mc.field_1761.method_2896(mc.field_1724, class_1268.field_5808, var1.hit());
         if (var5.method_23665()) {
            mc.field_1724.method_6104(class_1268.field_5808);
         }

         if (var2.silentRotate) {
            mc.field_1724.method_36456(var3);
            mc.field_1724.method_36457(var4);
         }

         return var5.method_23665();
      }
   }

   private void tickAdjust() {
      BuildPlan.Planned var1 = this.plan.at(this.adjusting);
      if (var1 != null && this.adjustClicks > 0) {
         if (this.adjustWait-- <= 0) {
            class_2680 var2 = mc.field_1687.method_8320(this.adjusting);
            int var3 = adjustClicks(var2, var1.state());
            if (var3 <= 0 || var2.method_26204() != var1.state().method_26204()) {
               this.adjusting = null;
            } else if (class_243.method_24953(this.adjusting).method_1022(mc.field_1724.method_33571()) > 5.5) {
               this.adjusting = null;
            } else {
               class_243 var4 = class_243.method_24953(this.adjusting).method_1031(0.0, 0.4, 0.0);
               float[] var5 = RotationUtil.rotationsTo(var4);
               if (this.lastSettings != null && this.lastSettings.human) {
                  boolean var6 = HumanAim.step(var5[0], var5[1], this.lastSettings.rotateSpeed, 2.0F);
                  if (!var6 || !this.miningAligned) {
                     this.miningAligned = var6;
                     this.adjustWait = 0;
                     this.status = "Aiming at " + var1.state().method_26204().method_9518().getString();
                     return;
                  }
               } else {
                  mc.field_1724.field_3944.method_52787(new class_2831(var5[0], var5[1], mc.field_1724.method_24828(), mc.field_1724.field_5976));
               }

               if (this.placeSneak || this.sneaking) {
                  this.releaseKeys();
                  this.sneakKey(false);
                  this.adjustWait = 1;
                  return;
               }

               mc.field_1761.method_2896(mc.field_1724, class_1268.field_5808, new class_3965(var4, class_2350.field_11036, this.adjusting, false));
               mc.field_1724.method_6104(class_1268.field_5808);
               this.adjustClicks--;
               this.adjustWait = 3;
               this.status = "Adjusting " + var1.state().method_26204().method_9518().getString();
            }
         }
      } else {
         this.adjusting = null;
      }
   }

   private void startMining(class_2338 var1) {
      this.releaseKeys();
      this.mining = var1;
      this.miningTicks = 0;
      this.miningAligned = false;
      if (this.lastSettings != null && mc.field_1724 != null) {
         this.humanizer.amount = this.lastSettings.randomness;
         this.miningSpeed = this.humanizer.speed(this.lastSettings.rotateSpeed);
         class_2350 var2 = class_2350.method_58251(mc.field_1724.method_33571().method_1020(class_243.method_24953(var1)));
         this.miningJitter = this.humanizer.jitter(class_243.field_1353, var2, 0.35);
      }
   }

   private boolean hammerMay(class_2338 var1) {
      return this.supports.contains(var1) ? true : this.plan != null && this.plan.contains(var1) && this.plan.at(var1) == null;
   }

   private void tickMining(BuildRuntime.Settings var1) {
      class_2680 var2 = mc.field_1687.method_8320(this.mining);
      if (!var2.method_26215()
         && !var2.method_45474()
         && this.miningTicks <= 400
         && !(class_243.method_24953(this.mining).method_1022(mc.field_1724.method_33571()) > var1.reach + 1.0)) {
         this.miningTicks++;
         if (var1.hammer && Hammer.available() && Hammer.safe(this.mining, Hammer.face(this.mining, mc.field_1724.method_33571()), this::hammerMay)) {
            Hammer.select();
         } else if (var1.hammer) {
            Worker.selectBestTool(var2, true);
         } else {
            this.selectBestTool(var2);
         }

         class_243 var3 = class_243.method_24953(this.mining);
         class_2350 var4 = class_2350.method_58251(mc.field_1724.method_33571().method_1020(var3));
         if (var1.human) {
            this.releaseKeys();
            class_243 var5 = var3.method_1031(var4.method_10148() * 0.49, var4.method_10164() * 0.49, var4.method_10165() * 0.49)
               .method_1019(this.miningJitter);
            boolean var6 = HumanAim.stepTo(var5, this.miningSpeed, 3.0F, this.humanizer.noise());
            if (!var6 || !this.miningAligned) {
               this.miningAligned = var6;
               this.status = "Aiming at " + var2.method_26204().method_9518().getString();
               return;
            }
         } else {
            float[] var7 = RotationUtil.rotationsTo(var3);
            mc.field_1724.field_3944.method_52787(new class_2831(var7[0], var7[1], mc.field_1724.method_24828(), mc.field_1724.field_5976));
         }

         if (mc.field_1724.method_68878()) {
            mc.field_1761.method_2910(this.mining, var4);
         } else {
            mc.field_1761.method_2902(this.mining, var4);
         }

         mc.field_1724.method_6104(class_1268.field_5808);
         this.status = "Removing " + var2.method_26204().method_9518().getString();
      } else {
         mc.field_1761.method_2925();
         this.mining = null;
         this.miningAligned = false;
         if (var1.human) {
            this.wait = Math.max(this.wait, this.humanizer.pause());
         }
      }
   }

   private void selectBestTool(class_2680 var1) {
      int var2 = -1;
      float var3 = 1.0F;

      for (int var4 = 0; var4 < 9; var4++) {
         class_1799 var5 = mc.field_1724.method_31548().method_5438(var4);
         if (!var5.method_7963() || var5.method_7936() - var5.method_7919() >= 10) {
            float var6 = var5.method_7924(var1);
            if (var6 > var3) {
               var3 = var6;
               var2 = var4;
            }
         }
      }

      if (var2 >= 0) {
         InvUtil.select(var2);
      }
   }

   private boolean tryClearArea(BuildRuntime.Settings var1) {
      class_243 var2 = mc.field_1724.method_33571();
      int var3 = (int)Math.ceil(var1.reach);
      class_2338 var4 = class_2338.method_49638(var2);
      class_2338 var5 = null;
      double var6 = Double.MAX_VALUE;

      for (class_2338 var9 : class_2338.method_10097(var4.method_10069(-var3, -var3, -var3), var4.method_10069(var3, var3, var3))) {
         if (this.plan.contains(var9) && this.plan.at(var9) == null && var9.method_10264() <= this.plan.anchor.method_10264() + this.layer + 1) {
            class_2680 var10 = mc.field_1687.method_8320(var9);
            if (!var10.method_26215() && !var10.method_45474() && !(var10.method_26214(mc.field_1687, var9) < 0.0F)) {
               double var11 = class_243.method_24953(var9).method_1025(var2);
               if (var11 < var6 && var11 <= var1.reach * var1.reach) {
                  var6 = var11;
                  var5 = var9.method_10062();
               }
            }
         }
      }

      if (var5 == null) {
         return false;
      } else {
         this.startMining(var5);
         return true;
      }
   }

   private int ensureInHotbar(class_1792 var1, BuildRuntime.Settings var2) {
      int var3 = InvUtil.findHotbar(var1);
      if (var3 >= 0) {
         return var3;
      } else {
         int var4 = InvUtil.findInventory(var1x -> var1x.method_31574(var1));
         if (var4 >= 9) {
            int var7 = this.freeHotbarSlot();
            InvUtil.swapToHotbar(var4, var7);
            return var7;
         } else if (mc.field_1724.method_68878() && var2.creativeStacks) {
            int var5 = this.freeHotbarSlot();
            class_1799 var6 = new class_1799(var1, var1.method_7882());
            mc.field_1724.method_31548().method_5447(var5, var6);
            mc.field_1761.method_2909(var6, 36 + var5);
            return var5;
         } else {
            return -1;
         }
      }
   }

   private int freeHotbarSlot() {
      int var1 = InvUtil.firstEmptyHotbar();
      if (var1 >= 0) {
         return var1;
      } else {
         HashSet var2 = new HashSet();

         for (BuildPlan.Planned var4 : this.plan.blocks) {
            var2.add(BuildPlan.itemOf(var4.state()));
         }

         for (int var5 = 8; var5 >= 0; var5--) {
            class_1799 var6 = mc.field_1724.method_31548().method_5438(var5);
            if (!var2.contains(var6.method_7909()) && !var6.method_7963()) {
               return var5;
            }
         }

         return InvUtil.selectedSlot();
      }
   }

   private boolean earlyRestock(BuildRuntime.Settings var1) {
      if (!mc.field_1724.method_68878() && mc.field_1724.method_31548().method_7376() >= 0 && !this.restock.isActive()) {
         Map var2 = this.missingMaterials();

         for (class_1792 var4 : var2.keySet()) {
            int var5 = InvUtil.count(var1x -> var1x.method_31574(var4));
            if (var5 < var1.restockAt && Restock.knowsNearby(var4, 24.0) && this.restock.begin(var4, var2.keySet(), 24.0)) {
               this.status = "Restock early: " + var4.method_63680().getString() + " (" + var5 + " left)";
               return true;
            }
         }

         return false;
      } else {
         return false;
      }
   }

   private boolean handleMissing(class_1792 var1, BuildRuntime.Settings var2) {
      if (!this.autoBuy.isActive() && this.autoBuy.lastItem() == var1 && !this.autoBuy.lastBought() && !this.autoBuy.lastResult().isEmpty()) {
         this.prepareFailed.add(var1);
      }

      boolean var3 = mc.field_1724.method_31548().method_7376() >= 0;
      if (var2.restock && !mc.field_1724.method_68878() && var3 && this.restock.begin(var1, this.missingMaterials().keySet(), 24.0)) {
         this.status = "Restock: looking for " + var1.method_63680().getString();
         return true;
      } else if (!var3 && !mc.field_1724.method_68878()) {
         this.status = "Inventory full – can't take / buy " + var1.method_63680().getString();
         return false;
      } else if (var2.giveCommand && this.giveCooldown == 0) {
         this.giveCooldown = 60;
         mc.field_1724.field_3944.method_45730("give @s " + class_7923.field_41178.method_10221(var1) + " 64");
         this.status = "/give " + var1.method_63680().getString();
         return true;
      } else if (var2.autoBuy && !this.autoBuy.isActive() && !this.prepareFailed.contains(var1)) {
         this.autoBuy.begin(var1);
         return this.autoBuy.isActive();
      } else {
         return false;
      }
   }

   private void walkTo(class_243 var1, double var2) {
      this.sneakKey(false);
      double var4 = var1.field_1352 - mc.field_1724.method_23317();
      double var6 = var1.field_1350 - mc.field_1724.method_23321();
      double var8 = Math.sqrt(var4 * var4 + var6 * var6);
      if (var8 < var2 - 1.4) {
         this.releaseKeys();
      } else {
         String var10 = SafeRoute.blocked(var1, false);
         if (var10 != null) {
            this.releaseKeys();
            this.status = "SafeRoute: " + var10;
            SafeRoute.note("AutoBuild stopped: " + var10);
            return;
         }

         float var11 = (float)Math.toDegrees(Math.atan2(var6, var4)) - 90.0F;
         mc.field_1724.method_36456(RotationUtil.approachAngle(mc.field_1724.method_36454(), var11, 25.0F));
         if (this.lastSettings != null && this.lastSettings.human) {
            mc.field_1724.method_36457(RotationUtil.approachAngle(mc.field_1724.method_36455(), 15.0F, 6.0F));
         }

         if (var1.field_1351 >= mc.field_1724.method_23318() - 1.0 && this.bridge(var4, var6)) {
            return;
         }

         BuildRuntime.Settings var12 = this.lastSettings;
         int var13 = var12 != null && var12.maxFall > 0 && mc.field_1724.method_24828() && !inWater() ? dropAhead(var4, var6) : 0;
         if (var12 != null && var13 > var12.maxFall) {
            double var17 = Math.sqrt(var4 * var4 + var6 * var6);
            class_2338 var16 = class_2338.method_49637(
               mc.field_1724.method_23317() + var4 / var17 * 0.7, mc.field_1724.method_23318() - 0.5, mc.field_1724.method_23321() + var6 / var17 * 0.7
            );
            if (!var12.supports || !this.placeSupport(var16.method_10074(), var12) && !this.placeSupport(var16, var12)) {
               mc.field_1690.field_1832.method_23481(true);
               this.sneaking = true;
               this.status = "Edge ahead (" + var13 + " blocks deep) – not jumping down";
               mc.field_1690.field_1894.method_23481(true);
               mc.field_1690.field_1903.method_23481(false);
               mc.field_1690.field_1913.method_23481(false);
               this.walking = true;
               this.lastPos = mc.field_1724.method_73189();
               return;
            }

            this.holdAtEdge();
            this.status = "Edge ahead (" + var13 + " blocks) – placing a step";
            return;
         }

         if (this.sneaking && !this.placeSneak) {
            mc.field_1690.field_1832.method_23481(KeyUtil.isPhysicallyDown(mc.field_1690.field_1832));
            this.sneaking = false;
         }

         mc.field_1690.field_1894.method_23481(true);
         this.walking = true;
         class_243 var14 = mc.field_1724.method_73189();
         if (this.lastPos != null && var14.method_1025(this.lastPos) < 0.0025) {
            this.stuckTicks++;
         } else {
            this.stuckTicks = 0;
         }

         this.lastPos = var14;
         if (mc.field_1724.field_5976 && this.stuckTicks > 6 && mc.field_1724.method_24828() && this.climbWall(var4, var6)) {
            return;
         }

         mc.field_1690.field_1903.method_23481((mc.field_1724.field_5976 || this.stuckTicks > 8) && mc.field_1724.method_24828());
         mc.field_1690.field_1913.method_23481(this.stuckTicks > 30 && this.stuckTicks / 20 % 2 == 0);
      }
   }

   private static class_2350 horizontal(double var0, double var2) {
      if (Math.abs(var0) > Math.abs(var2)) {
         return var0 > 0.0 ? class_2350.field_11034 : class_2350.field_11039;
      } else {
         return var2 > 0.0 ? class_2350.field_11035 : class_2350.field_11043;
      }
   }

   private static boolean solidAt(class_2338 var0) {
      return !mc.field_1687.method_8320(var0).method_26218(mc.field_1687, var0).method_1110();
   }

   private boolean climbWall(double var1, double var3) {
      BuildRuntime.Settings var5 = this.lastSettings;
      if (var5 != null && var5.pillar) {
         class_2338 var6 = mc.field_1724.method_24515();
         class_2338 var7 = var6.method_10093(horizontal(var1, var3));
         if (solidAt(var7) && solidAt(var7.method_10084()) && free(var6.method_10084()) && free(var6.method_10086(2)) && this.plan.at(var6) == null) {
            this.status = "Wall in the way – pillaring up";
            this.startTower(var5);
            return this.towerBase != null;
         } else {
            return false;
         }
      } else {
         return false;
      }
   }

   private boolean bridge(double var1, double var3) {
      BuildRuntime.Settings var5 = this.lastSettings;
      if (var5 != null && var5.supports && mc.field_1724.method_24828()) {
         double var6 = Math.sqrt(var1 * var1 + var3 * var3);
         if (var6 < 0.01) {
            return false;
         } else {
            double var8 = mc.field_1724.method_23317() + var1 / var6 * 0.75;
            double var10 = mc.field_1724.method_23321() + var3 / var6 * 0.75;
            class_2338 var12 = class_2338.method_49637(var8, mc.field_1724.method_23318() - 0.5, var10);
            if (!free(var12) || !free(var12.method_10074())) {
               return false;
            } else if (free(var12.method_10084()) && free(var12.method_10086(2))) {
               BuildPlan.Planned var13 = this.plan.at(var12);
               if (var13 != null) {
                  int var14 = this.indexOf(var13);
                  if (var14 >= 0
                     && !this.done[var14]
                     && this.attempts[var14] < 1000
                     && var13.state().method_26234(mc.field_1687, var12)
                     && this.tryPlace(var14, BuildRuntime.Status.OPEN, var5)) {
                     this.holdAtEdge();
                     this.status = "Bridging";
                     return true;
                  } else {
                     return false;
                  }
               } else if (this.placeSupport(var12, var5)) {
                  this.holdAtEdge();
                  this.status = "Bridging over a gap";
                  return true;
               } else {
                  return false;
               }
            } else {
               return false;
            }
         }
      } else {
         return false;
      }
   }

   private void holdAtEdge() {
      mc.field_1690.field_1894.method_23481(false);
      mc.field_1690.field_1832.method_23481(true);
      this.sneaking = true;
      this.walking = true;
   }

   private boolean axisSupport(BuildPlan.Planned var1, BuildRuntime.Settings var2) {
      String var3 = prop(var1.state(), "axis");
      if (var3 == null) {
         return false;
      } else {
         class_2350[] var4 = "x".equals(var3)
            ? new class_2350[]{class_2350.field_11039, class_2350.field_11034}
            : (
               "z".equals(var3)
                  ? new class_2350[]{class_2350.field_11043, class_2350.field_11035}
                  : new class_2350[]{class_2350.field_11033, class_2350.field_11036}
            );

         for (class_2350 var8 : var4) {
            if (PlacementSolver.clickable(var1.pos().method_10093(var8))) {
               return false;
            }
         }

         for (class_2350 var13 : var4) {
            class_2338 var9 = var1.pos().method_10093(var13);
            if (this.supportAllowed(var9, var2) && this.placeSupport(var9, var2)) {
               this.status = "Support so " + var1.state().method_26204().method_9518().getString() + " gets the right direction";
               return true;
            }
         }

         return false;
      }
   }

   private static boolean water(class_2338 var0) {
      return !mc.field_1687.method_8316(var0).method_15769();
   }

   private static boolean inWater() {
      class_2338 var0 = mc.field_1724.method_24515();
      return water(var0) || water(var0.method_10084());
   }

   private static int dropAhead(double var0, double var2) {
      double var4 = Math.sqrt(var0 * var0 + var2 * var2);
      if (var4 < 0.01) {
         return 0;
      } else {
         double var6 = mc.field_1724.method_23317() + var0 / var4 * 0.7;
         double var8 = mc.field_1724.method_23321() + var2 / var4 * 0.7;
         class_2338 var10 = class_2338.method_49637(var6, mc.field_1724.method_23318() + 0.2, var8);
         if (!free(var10)) {
            return 0;
         } else {
            int var11 = 0;

            for (class_2338 var12 = var10.method_10074(); var11 < 10; var12 = var12.method_10074()) {
               if (!free(var12) || water(var12)) {
                  return var11;
               }

               var11++;
            }

            return var11;
         }
      }
   }

   private static class_243 findLand() {
      class_2338 var0 = mc.field_1724.method_24515();
      class_243 var1 = mc.field_1724.method_73189();
      class_243 var2 = null;
      double var3 = Double.MAX_VALUE;

      for (int var5 = -8; var5 <= 8; var5++) {
         for (int var6 = -8; var6 <= 8; var6++) {
            for (int var7 = -1; var7 <= 3; var7++) {
               class_2338 var8 = var0.method_10069(var5, var7, var6);
               if (free(var8)
                  && free(var8.method_10084())
                  && !water(var8)
                  && !water(var8.method_10084())
                  && !free(var8.method_10074())
                  && !water(var8.method_10074())) {
                  class_243 var9 = class_243.method_24955(var8);
                  double var10 = var9.method_1025(var1) + var7 * 2.0;
                  if (var10 < var3) {
                     var3 = var10;
                     var2 = var9;
                  }
               }
            }
         }
      }

      return var2;
   }

   private boolean escapeWater(BuildRuntime.Settings var1) {
      if (var1.escapeWater && var1.mode == BuildRuntime.Mode.AUTO && inWater()) {
         if (++this.waterTicks >= 60 && (this.noProgress >= 40 || this.escaping)) {
            if (this.waterTicks > 1800) {
               this.escaping = false;
               this.waterTicks = -1200;
               this.releaseKeys();
               Notifications.warn("AutoBuild", "Could not get out of the water – put scaffold blocks in your inventory or help it out");
               return false;
            } else {
               this.escaping = true;
               class_243 var2 = findLand();
               mc.field_1690.field_1903.method_23481(true);
               this.walking = true;
               double var3;
               double var5;
               if (var2 != null) {
                  var3 = var2.field_1352 - mc.field_1724.method_23317();
                  var5 = var2.field_1350 - mc.field_1724.method_23321();
               } else {
                  var3 = -Math.sin(Math.toRadians(mc.field_1724.method_36454()));
                  var5 = Math.cos(Math.toRadians(mc.field_1724.method_36454()));
               }

               double var7 = Math.sqrt(var3 * var3 + var5 * var5);
               if (var7 < this.waterBest - 0.1) {
                  this.waterBest = var7;
                  this.stuckTicks = 0;
               } else {
                  this.stuckTicks++;
               }

               float var9 = (float)Math.toDegrees(Math.atan2(var5, var3)) - 90.0F;
               mc.field_1724.method_36456(RotationUtil.approachAngle(mc.field_1724.method_36454(), var9, 20.0F));
               mc.field_1690.field_1894.method_23481(true);
               if (var2 == null || this.stuckTicks > 30) {
                  class_2338 var10 = class_2338.method_49637(
                     mc.field_1724.method_23317() + var3 / Math.max(0.01, var7) * 0.9,
                     mc.field_1724.method_23318() + 0.1,
                     mc.field_1724.method_23321() + var5 / Math.max(0.01, var7) * 0.9
                  );
                  if (var1.supports && free(var10.method_10084()) && (water(var10) || free(var10)) && this.placeSupport(var10, var1)) {
                     this.stuckTicks = 0;
                     this.status = "Getting out of the water (placing a step)";
                     return true;
                  }
               }

               this.status = var2 == null ? "In deep water – swimming to find land" : "Getting out of the water";
               return true;
            }
         } else {
            return false;
         }
      } else {
         if (this.escaping) {
            this.escaping = false;
            this.releaseKeys();
         }

         this.waterTicks = 0;
         this.waterBest = Double.MAX_VALUE;
         return false;
      }
   }

   private class_243 stepAway(class_2338 var1) {
      class_243 var2 = mc.field_1724.method_73189();
      class_243 var3 = null;
      double var4 = Double.MAX_VALUE;

      for (int var6 = 0; var6 < 16; var6++) {
         double var7 = Math.toRadians(var6 * 22.5);

         for (double var12 : new double[]{1.4, 2.1}) {
            double var14 = var1.method_10263() + 0.5 + Math.cos(var7) * var12;
            double var16 = var1.method_10260() + 0.5 + Math.sin(var7) * var12;

            for (int var18 = -1; var18 <= 1; var18++) {
               int var19 = mc.field_1724.method_24515().method_10264() + var18;
               class_2338 var20 = class_2338.method_49637(var14, var19, var16);
               if (free(var20)
                  && free(var20.method_10084())
                  && solidAt(var20.method_10074())
                  && !new class_238(var14 - 0.3, var19, var16 - 0.3, var14 + 0.3, var19 + 1.8, var16 + 0.3).method_994(new class_238(var1))) {
                  class_243 var21 = new class_243(var14, var19, var16);
                  double var22 = var21.method_1025(var2) + Math.abs(var18) * 3.0;
                  if (var22 < var4) {
                     var4 = var22;
                     var3 = var21;
                  }
               }
            }
         }
      }

      return var3;
   }

   private static String prop(class_2680 var0, String var1) {
      String var2 = var0.toString();
      int var3 = var2.indexOf(91);
      if (var3 < 0) {
         return null;
      } else {
         for (String var7 : var2.substring(var3 + 1, var2.length() - 1).split(",")) {
            int var8 = var7.indexOf(61);
            if (var8 > 0 && var7.substring(0, var8).equals(var1)) {
               return var7.substring(var8 + 1);
            }
         }

         return null;
      }
   }

   private static class_2350 dirNamed(String var0) {
      if (var0 == null) {
         return null;
      } else {
         return switch (var0) {
            case "north" -> class_2350.field_11043;
            case "south" -> class_2350.field_11035;
            case "west" -> class_2350.field_11039;
            case "east" -> class_2350.field_11034;
            case "up" -> class_2350.field_11036;
            case "down" -> class_2350.field_11033;
            default -> null;
         };
      }
   }

   private static class_2350 attachSide(class_2680 var0) {
      String var1 = class_7923.field_41175.method_10221(var0.method_26204()).method_12832();
      class_2350 var2 = dirNamed(prop(var0, "facing"));
      String var3 = prop(var0, "hanging");
      if (var3 != null) {
         return var3.equals("true") ? class_2350.field_11036 : class_2350.field_11033;
      } else {
         String var4 = prop(var0, "face");
         if (var4 == null) {
            var4 = prop(var0, "attachment");
         }

         if (var4 != null) {
            if (var4.equals("floor")) {
               return class_2350.field_11033;
            } else if (var4.equals("ceiling")) {
               return class_2350.field_11036;
            } else {
               return var2 == null ? null : var2.method_10153();
            }
         } else if (!var1.contains("wall_") && !var1.equals("ladder") && !var1.equals("tripwire_hook")) {
            if (var1.equals("cocoa")) {
               return var2;
            } else if (var1.endsWith("hanging_sign")) {
               return class_2350.field_11036;
            } else {
               if (!var1.endsWith("_block") && !var1.startsWith("infested")) {
                  for (String var8 : NEEDS_FLOOR) {
                     if (var1.contains(var8)) {
                        return class_2350.field_11033;
                     }
                  }
               }

               return null;
            }
         } else {
            return var2 == null ? null : var2.method_10153();
         }
      }
   }

   private int ensureAttachment(int var1, BuildPlan.Planned var2, BuildRuntime.Settings var3) {
      class_2350 var4 = attachSide(var2.state());
      if (var4 == null) {
         return 0;
      } else {
         class_2338 var5 = var2.pos().method_10093(var4);
         if (!mc.field_1687.method_8320(var5).method_45474()) {
            return 0;
         } else {
            BuildPlan.Planned var6 = this.plan.at(var5);
            if (var6 != null) {
               int var7 = this.indexOf(var6);
               if (var7 >= 0
                  && var7 != var1
                  && !this.done[var7]
                  && this.attempts[var7] < 1000
                  && var6.state().method_26234(mc.field_1687, var5)
                  && this.retryAt[var7] <= this.tick
                  && this.tryPlace(var7, BuildRuntime.Status.OPEN, var3)) {
                  this.status = "Placing the block " + var2.state().method_26204().method_9518().getString() + " hangs on";
                  return 1;
               } else {
                  this.retryAt[var1] = this.tick + 20;
                  return -1;
               }
            } else if (!this.placeSupport(var5, var3) && !this.trySupport(var5, var3)) {
               return 0;
            } else {
               this.status = "Placing a support for " + var2.state().method_26204().method_9518().getString();
               return 1;
            }
         }
      }
   }

   private boolean watchdog(BuildRuntime.Settings var1) {
      int var2 = this.placeTimes.size();
      boolean var3 = false;
      if (!this.missing.isEmpty()) {
         int var4 = this.nearestOpenInLayer();
         var3 = var4 < 0 || this.missing.containsKey(BuildPlan.itemOf(this.plan.blocks.get(var4).state()));
      }

      if (this.doneCount == this.lastDoneSeen && var2 == this.lastPlaceSeen && var1.mode == BuildRuntime.Mode.AUTO && !var3) {
         if (this.mining == null && (this.pending == null || this.pendingSupport != null) && this.towerBase == null && this.adjusting == null) {
            this.noProgress++;
            if (this.noProgress % 100 == 0 && this.reposition == null) {
               int var10 = this.nearestOpenInLayer();
               if (var10 < 0) {
                  return false;
               } else {
                  BuildPlan.Planned var5 = this.plan.blocks.get(var10);
                  if (this.noProgress >= 500) {
                     this.attempts[var10] = 1000;
                     this.noProgress = 0;
                     class_2338 var11 = var5.pos();
                     Notifications.warn(
                        "AutoBuild",
                        "Stuck at " + var11.method_10263() + " " + var11.method_10264() + " " + var11.method_10260() + " – skipped it (Retry Skipped)"
                     );
                     return false;
                  } else {
                     class_243 var6 = this.stepAway(var5.pos());
                     double var7 = Math.toRadians(this.tick * 47 % 360);
                     class_243 var9 = class_243.method_24953(var5.pos()).method_1031(Math.cos(var7) * 2.6, 0.0, Math.sin(var7) * 2.6);
                     this.reposition = var6 != null && this.noProgress < 250 ? var6 : var9;
                     this.repositionTicks = 0;
                     this.retryAt[var10] = this.tick + 30;
                     this.status = "Stuck – trying another spot";
                     return true;
                  }
               }
            } else {
               return false;
            }
         } else {
            return false;
         }
      } else {
         this.lastDoneSeen = this.doneCount;
         this.lastPlaceSeen = var2;
         this.noProgress = 0;
         return false;
      }
   }

   private void startTower(BuildRuntime.Settings var1) {
      class_1792 var2 = null;

      for (int var3 = 0; var3 < 36; var3++) {
         class_1792 var4 = mc.field_1724.method_31548().method_5438(var3).method_7909();
         if (var4 instanceof class_1747 var5 && var1.scaffold.contains(var5.method_7711())) {
            var2 = var4;
            break;
         }
      }

      if (var2 == null) {
         this.status = "Too high – put scaffold blocks (dirt, cobblestone …) in your inventory";
         this.releaseKeys();
      } else {
         this.towerBase = mc.field_1724.method_24515();
         this.towerTicks = 0;
         this.towerAimed = !var1.human;
         this.releaseKeys();
      }
   }

   private void tickTower(BuildRuntime.Settings var1) {
      if (!this.towerAimed) {
         this.status = "Looking down to pillar";
         if (HumanAim.step(mc.field_1724.method_36454(), 88.0F, var1.rotateSpeed, 2.5F)) {
            this.towerAimed = true;
         }
      } else {
         this.towerTicks++;
         if (this.towerTicks == 1) {
            mc.field_1690.field_1903.method_23481(true);
            this.walking = true;
            this.status = "Pillaring up";
         } else {
            mc.field_1690.field_1903.method_23481(false);
            if (mc.field_1724.method_23318() >= this.towerBase.method_10264() + 1.05 || this.towerTicks > 12) {
               if (mc.field_1687.method_8320(this.towerBase).method_45474()) {
                  int var2 = -1;

                  for (int var3 = 0; var3 < 36 && var2 < 0; var3++) {
                     if (mc.field_1724.method_31548().method_5438(var3).method_7909() instanceof class_1747 var4 && var1.scaffold.contains(var4.method_7711())) {
                        var2 = var3;
                     }
                  }

                  if (var2 >= 9) {
                     int var6 = this.freeHotbarSlot();
                     InvUtil.swapToHotbar(var2, var6);
                     var2 = var6;
                  }

                  if (var2 >= 0) {
                     InvUtil.select(var2);
                     class_2338 var7 = this.towerBase.method_10074();
                     class_243 var8 = new class_243(var7.method_10263() + 0.5, var7.method_10264() + 1.0, var7.method_10260() + 0.5);
                     if (this.click(
                           new PlacementSolver.Click(new class_3965(var8, class_2350.field_11036, var7, false), mc.field_1724.method_36454(), 90.0F, true),
                           var1
                        )
                        && this.plan.at(var7.method_10084()) == null) {
                        this.supports.add(var7.method_10084());
                     }
                  }
               }

               this.towerBase = null;
               this.releaseKeys();
            }
         }
      }
   }

   private void beginPending(int var1, class_2338 var2, PlacementSolver.Click var3, int var4) {
      this.pending = var3;
      this.pendingBucket = false;
      this.pendingIndex = var1;
      this.pendingSupport = var2;
      this.pendingSlot = var4;
      this.pendingTicks = 0;
      this.pendingAligned = false;
      BuildRuntime.Settings var5 = this.lastSettings;
      this.humanizer.amount = var5 == null ? 0.0 : var5.randomness;
      this.pendingSpeed = this.humanizer.speed(var5 == null ? 32.0F : var5.rotateSpeed);
      if (var3.aimed()) {
         float[] var6 = RotationUtil.rotationsTo(this.humanizer.jitter(var3.hit().method_17784(), var3.hit().method_17780(), 0.08));
         this.pendingYaw = var6[0];
         this.pendingPitch = var6[1];
      } else {
         this.pendingYaw = var3.yaw();
         this.pendingPitch = var3.pitch();
      }

      InvUtil.select(var4);
   }

   private void failPending() {
      this.sneakKey(false);
      if (this.pendingIndex >= 0 && this.pendingSupport == null) {
         this.attempts[this.pendingIndex]++;
         this.retryAt[this.pendingIndex] = this.tick + 10;
         if (this.lastSettings != null && this.attempts[this.pendingIndex] > this.lastSettings.maxAttempts) {
            this.attempts[this.pendingIndex] = 1000;
         }
      }

      this.pending = null;
      this.pendingSupport = null;
      this.pendingIndex = -1;
   }

   private void tickPending(BuildRuntime.Settings var1) {
      PlacementSolver.Click var2 = this.pending;
      if (++this.pendingTicks > 60) {
         this.failPending();
      } else {
         String var4;
         if (this.pendingSupport != null) {
            class_2338 var3 = this.pendingSupport;
            var4 = "support";
            if (!mc.field_1687.method_8320(var3).method_45474()) {
               this.pending = null;
               this.pendingSupport = null;
               return;
            }
         } else {
            BuildPlan.Planned var5 = this.plan.blocks.get(this.pendingIndex);
            class_2338 var10 = var5.pos();
            var4 = var5.state().method_26204().method_9518().getString();
            BuildRuntime.Status var6 = this.statusOf(this.pendingIndex, var1);
            if (var6 != BuildRuntime.Status.OPEN && var6 != BuildRuntime.Status.INCREMENT) {
               this.verify(this.pendingIndex);
               this.pending = null;
               this.pendingIndex = -1;
               return;
            }
         }

         class_2680 var11 = mc.field_1687.method_8320(var2.hit().method_17777());
         class_1799 var12 = mc.field_1724.method_31548().method_5438(this.pendingSlot);
         if ((!var11.method_26215() || this.pendingBucket)
            && !var12.method_7960()
            && (var12.method_7909() instanceof class_1747 || this.pendingBucket)
            && !(var2.hit().method_17784().method_1022(mc.field_1724.method_33571()) > var1.reach + 0.6)) {
            InvUtil.select(this.pendingSlot);
            this.releaseKeys(true);
            if (this.escaping && inWater()) {
               mc.field_1690.field_1903.method_23481(true);
               this.walking = true;
            }

            boolean var7 = HumanAim.step(this.pendingYaw, this.pendingPitch, this.pendingSpeed, 1.0F, this.humanizer.noise());
            if (var7 && this.pendingAligned && this.needSneak(var2.hit(), var1) && !mc.field_1724.method_5715()) {
               this.sneakKey(true);
               this.status = "Sneaking to place";
            } else if (var7 && this.pendingAligned) {
               class_1269 var8 = this.pendingBucket
                  ? mc.field_1761.method_2919(mc.field_1724, class_1268.field_5808)
                  : mc.field_1761.method_2896(mc.field_1724, class_1268.field_5808, var2.hit());
               if (var1.sneakMode == 1) {
                  this.sneakKey(false);
               }

               boolean var9 = var8.method_23665();
               if (var9) {
                  mc.field_1724.method_6104(class_1268.field_5808);
               }

               if (this.pendingSupport != null) {
                  if (var9) {
                     this.supports.add(this.pendingSupport.method_10062());
                     this.noProgress = 0;
                  }
               } else {
                  this.attempts[this.pendingIndex]++;
                  this.retryAt[this.pendingIndex] = this.tick + 6;
                  if (this.attempts[this.pendingIndex] > var1.maxAttempts) {
                     this.attempts[this.pendingIndex] = 1000;
                  }

                  if (var9) {
                     this.placeTimes.addLast(System.currentTimeMillis());
                  }
               }

               this.pending = null;
               this.pendingSupport = null;
               this.pendingIndex = -1;
               this.wait = var1.delay + this.humanizer.pause();
               this.status = String.format(Locale.ROOT, "Layer %d/%d · %d/%d", this.layer + 1, this.plan.sizeY, this.doneCount, this.plan.blocks.size());
            } else {
               this.pendingAligned = var7;
               this.status = "Placing " + var4;
            }
         } else {
            this.failPending();
         }
      }
   }

   private void tickReposition(BuildRuntime.Settings var1) {
      double var2 = this.reposition.field_1352 - mc.field_1724.method_23317();
      double var4 = this.reposition.field_1350 - mc.field_1724.method_23321();
      if (++this.repositionTicks <= 120 && !(var2 * var2 + var4 * var4 < 0.2025)) {
         this.walkTo(this.reposition, 1.75);
         this.status = "Walking to a spot where the block can be placed legit";
      } else {
         this.reposition = null;
         this.releaseKeys();
      }
   }

   private static boolean free(class_2338 var0) {
      return mc.field_1687.method_8320(var0).method_26218(mc.field_1687, var0).method_1110();
   }

   private class_243 findStandSpot(BuildPlan.Planned var1, class_1799 var2, BuildRuntime.Settings var3) {
      class_2338 var4 = var1.pos();
      double var5 = mc.field_1724.method_33571().field_1351 - mc.field_1724.method_23318();
      int[] var7 = new int[]{mc.field_1724.method_24515().method_10264(), var4.method_10264() - 1, var4.method_10264(), var4.method_10264() + 1};
      ArrayList var8 = new ArrayList();
      HashSet var9 = new HashSet();

      for (double var13 : new double[]{2.2, 3.2}) {
         for (int var15 = 0; var15 < 12; var15++) {
            double var16 = Math.toRadians(var15 * 30.0);
            double var18 = var4.method_10263() + 0.5 + Math.cos(var16) * var13;
            double var20 = var4.method_10260() + 0.5 + Math.sin(var16) * var13;

            for (int var25 : var7) {
               class_2338 var26 = class_2338.method_49637(var18, var25, var20);
               if (var9.add(var26.method_10063())
                  && free(var26)
                  && free(var26.method_10084())
                  && !free(var26.method_10074())
                  && !new class_238(var18 - 0.3, var25, var20 - 0.3, var18 + 0.3, var25 + 1.8, var20 + 0.3).method_994(new class_238(var4))) {
                  var8.add(new class_243(var18, var25, var20));
               }
            }
         }
      }

      class_243 var27 = mc.field_1724.method_73189();
      var8.sort(
         (var1x, var2x) -> Double.compare(
            var1x.method_1025(var27) + Math.abs(var1x.field_1351 - var27.field_1351) * 4.0,
            var2x.method_1025(var27) + Math.abs(var2x.field_1351 - var27.field_1351) * 4.0
         )
      );
      int var28 = 0;

      for (class_243 var30 : var8) {
         if (var28++ >= 20) {
            break;
         }

         class_243 var14 = var30.method_1031(0.0, var5, 0.0);
         if (PlacementSolver.solveFrom(var4, var1.state(), var2, var3.reach - 0.3, true, var14) != null) {
            return var30;
         }
      }

      return null;
   }

   private int scaffoldSlot(BuildRuntime.Settings var1) {
      for (int var2 = 0; var2 < 36; var2++) {
         class_1799 var3 = mc.field_1724.method_31548().method_5438(var2);
         if (var3.method_7909() instanceof class_1747 var4 && var1.scaffold.contains(var4.method_7711()) && ItemUtil.id(var3).equals("minecraft:scaffolding")) {
            if (var2 < 9) {
               return var2;
            }

            int var10 = this.freeHotbarSlot();
            InvUtil.swapToHotbar(var2, var10);
            return var10;
         }
      }

      for (int var6 = 0; var6 < 36; var6++) {
         if (mc.field_1724.method_31548().method_5438(var6).method_7909() instanceof class_1747 var7 && var1.scaffold.contains(var7.method_7711())) {
            if (var6 < 9) {
               return var6;
            }

            int var9 = this.freeHotbarSlot();
            InvUtil.swapToHotbar(var6, var9);
            return var9;
         }
      }

      return -1;
   }

   private boolean supportAllowed(class_2338 var1, BuildRuntime.Settings var2) {
      if (!mc.field_1687.method_8320(var1).method_45474()) {
         return false;
      } else if (this.plan.at(var1) != null) {
         return false;
      } else {
         return mc.field_1724.method_5829().method_994(new class_238(var1))
            ? false
            : !(class_243.method_24953(var1).method_1022(mc.field_1724.method_33571()) > var2.reach + 1.0);
      }
   }

   private boolean placeSupport(class_2338 var1, BuildRuntime.Settings var2) {
      if (!this.supportAllowed(var1, var2)) {
         return false;
      } else {
         int var3 = this.scaffoldSlot(var2);
         if (var3 < 0) {
            this.status = "Need support – put scaffold blocks (dirt, cobblestone …) in your inventory";
            return false;
         } else {
            class_1799 var4 = mc.field_1724.method_31548().method_5438(var3);
            class_2680 var5 = ((class_1747)var4.method_7909()).method_7711().method_9564();
            PlacementSolver.Click var6 = PlacementSolver.solve(var1, var5, var4, var2.reach, var2.strict);
            return var6 != null && var6.exact() ? this.doSupport(var1, var6, var3, var2) : false;
         }
      }
   }

   private boolean doSupport(class_2338 var1, PlacementSolver.Click var2, int var3, BuildRuntime.Settings var4) {
      if (var4.human) {
         this.beginPending(-1, var1.method_10062(), var2, var3);
         return true;
      } else {
         InvUtil.select(var3);
         if (this.click(var2, var4)) {
            this.supports.add(var1.method_10062());
            this.noProgress = 0;
            return true;
         } else {
            return false;
         }
      }
   }

   private boolean trySupport(class_2338 var1, BuildRuntime.Settings var2) {
      int var3 = this.scaffoldSlot(var2);
      if (var3 < 0) {
         this.status = "Need support – put scaffold blocks (dirt, cobblestone …) in your inventory";
         return false;
      } else {
         class_1799 var4 = mc.field_1724.method_31548().method_5438(var3);
         class_2680 var5 = ((class_1747)var4.method_7909()).method_7711().method_9564();
         class_2350[] var6 = new class_2350[]{
            class_2350.field_11033, class_2350.field_11043, class_2350.field_11035, class_2350.field_11039, class_2350.field_11034, class_2350.field_11036
         };
         ArrayList var7 = new ArrayList();
         HashSet var8 = new HashSet();
         var7.add(var1);
         var8.add(var1);

         for (int var9 = 0; var9 < 3; var9++) {
            ArrayList var10 = new ArrayList();

            for (class_2338 var12 : var7) {
               for (class_2350 var16 : var6) {
                  class_2338 var17 = var12.method_10093(var16);
                  if (var8.add(var17) && this.supportAllowed(var17, var2)) {
                     PlacementSolver.Click var18 = PlacementSolver.solve(var17, var5, var4, var2.reach, var2.strict);
                     if (var18 != null && var18.exact()) {
                        return this.doSupport(var17, var18, var3, var2);
                     }

                     var10.add(var17);
                  }
               }
            }

            var7 = var10;
            if (var10.isEmpty()) {
               break;
            }
         }

         return false;
      }
   }

   private boolean mustStay(class_2338 var1) {
      if (mc.field_1687.method_8320(var1.method_10084()).method_26204() instanceof class_2346) {
         return true;
      } else {
         for (class_2350 var5 : new class_2350[]{
            class_2350.field_11036, class_2350.field_11043, class_2350.field_11035, class_2350.field_11039, class_2350.field_11034, class_2350.field_11033
         }) {
            class_2338 var6 = var1.method_10093(var5);
            BuildPlan.Planned var7 = this.plan.at(var6);
            if (var7 != null
               && !var7.state().method_26234(mc.field_1687, var6)
               && mc.field_1687.method_8320(var6).method_26204() == var7.state().method_26204()) {
               return true;
            }
         }

         return false;
      }
   }

   private boolean cleanupSupports(BuildRuntime.Settings var1) {
      if (var1.removeSupports && !this.supports.isEmpty()) {
         class_243 var2 = mc.field_1724.method_33571();
         class_2338 var3 = null;
         double var4 = Double.MAX_VALUE;
         Iterator var6 = this.supports.iterator();

         while (var6.hasNext()) {
            class_2338 var7 = (class_2338)var6.next();
            class_2680 var8 = mc.field_1687.method_8320(var7);
            if (!var8.method_45474() && var1.scaffold.contains(var8.method_26204()) && this.plan.at(var7) == null && !this.mustStay(var7)) {
               double var9 = class_243.method_24953(var7).method_1025(var2);
               if (var9 < var4) {
                  var4 = var9;
                  var3 = var7;
               }
            } else {
               var6.remove();
            }
         }

         if (var3 == null) {
            return false;
         } else if (Math.sqrt(var4) <= var1.reach) {
            this.startMining(var3);
            this.status = "Removing supports (" + this.supports.size() + " left)";
            return true;
         } else if (var1.walk && var1.mode == BuildRuntime.Mode.AUTO) {
            this.walkTo(class_243.method_24953(var3), var1.reach);
            this.status = "Walking to supports (" + this.supports.size() + " left)";
            return true;
         } else {
            Notifications.info("AutoBuild", this.supports.size() + " support blocks left – walk near them or mine them yourself");
            this.supports.clear();
            return false;
         }
      } else {
         return false;
      }
   }

   private void releaseKeys() {
      this.releaseKeys(false);
   }

   private void releaseKeys(boolean var1) {
      if (mc.field_1690 != null) {
         if (this.walking) {
            mc.field_1690.field_1894.method_23481(KeyUtil.isPhysicallyDown(mc.field_1690.field_1894));
            mc.field_1690.field_1903.method_23481(KeyUtil.isPhysicallyDown(mc.field_1690.field_1903));
            mc.field_1690.field_1913.method_23481(KeyUtil.isPhysicallyDown(mc.field_1690.field_1913));
            this.walking = false;
            this.stuckTicks = 0;
         }

         if (this.sneaking && !var1) {
            mc.field_1690.field_1832.method_23481(this.placeSneak || KeyUtil.isPhysicallyDown(mc.field_1690.field_1832));
            this.sneaking = false;
         }
      }
   }

   public void render(Render3D var1, int var2, boolean var3, int var4) {
      if (this.plan != null) {
         var1.boxOutline(this.plan.bounds(), var2, false);
         class_243 var5 = var1.camera();
         double var6 = 2304.0;
         int var8 = 0;
         if (this.phase == BuildRuntime.Phase.PREVIEW) {
            for (BuildPlan.Planned var10 : this.plan.blocks) {
               if (var8 >= var4) {
                  break;
               }

               if (!(distSq(var10.pos(), var5) > var6)) {
                  int var11 = var10.state().method_26204().method_26403().field_16011;
                  var1.boxFilled(new class_238(var10.pos()).method_1011(0.05), 1879048192 | var11, false);
                  var8++;
               }
            }
         } else if (this.done != null) {
            if (this.phase == BuildRuntime.Phase.VERIFY && this.verifyClass != null) {
               for (int var17 = 0; var17 < this.verifyClass.length && var8 < var4; var17++) {
                  byte var20 = this.verifyClass[var17];
                  if (var20 > 0) {
                     BuildPlan.Planned var23 = this.plan.blocks.get(var17);
                     if ((this.verifyLayer < 0 || var23.layer() == this.verifyLayer) && !(distSq(var23.pos(), var5) > var6)) {
                        class_238 var24 = new class_238(var23.pos()).method_1011(0.06);
                        if (var20 == 2) {
                           var1.box(var24, -53200, 70, false);
                        } else if (var20 == 3) {
                           var1.box(var24, -24544, 40, false);
                        } else {
                           var1.boxFilled(var24, 1610612736 | var23.state().method_26204().method_26403().field_16011, false);
                        }

                        var8++;
                     }
                  }
               }
            } else {
               for (int var15 = Math.max(0, this.layer); var15 <= Math.min(this.plan.sizeY - 1, this.layer + 1) && var3; var15++) {
                  if (this.layerStart[var15] >= 0) {
                     for (int var18 = this.layerStart[var15]; var18 < this.layerEnd[var15] && var8 < var4; var18++) {
                        if (!this.done[var18]) {
                           BuildPlan.Planned var21 = this.plan.blocks.get(var18);
                           if (!(distSq(var21.pos(), var5) > var6)) {
                              class_2680 var12 = mc.field_1687.method_8320(var21.pos());
                              class_238 var13 = new class_238(var21.pos()).method_1011(0.08);
                              if (this.attempts[var18] >= 1000) {
                                 var1.boxOutline(var13, -8947849, false);
                              } else if (!var12.method_45474() && var12.method_26204() != var21.state().method_26204()) {
                                 var1.box(var13, -49088, 50, false);
                              } else if (var12.method_26204() == var21.state().method_26204()) {
                                 var1.boxOutline(var13, -22016, false);
                              } else {
                                 int var14 = var21.state().method_26204().method_26403().field_16011;
                                 var1.boxFilled(var13, (var15 == this.layer ? 1610612736 : 671088640) | var14, false);
                              }

                              var8++;
                           }
                        }
                     }
                  }
               }

               if (this.mining != null) {
                  var1.box(new class_238(this.mining), -57312, 60, false);
               }

               class_2338 var16 = this.pendingSupport != null
                  ? this.pendingSupport
                  : (this.pending != null && this.pendingIndex >= 0 ? this.plan.blocks.get(this.pendingIndex).pos() : null);
               if (var16 != null) {
                  var1.box(new class_238(var16).method_1011(0.03), -16721153, 45, false);
               }

               for (class_2338 var22 : this.supports) {
                  var1.boxOutline(new class_238(var22).method_1011(0.02), -7303024, false);
               }
            }
         }
      }
   }

   private static double distSq(class_2338 var0, class_243 var1) {
      double var2 = var0.method_10263() + 0.5 - var1.field_1352;
      double var4 = var0.method_10264() + 0.5 - var1.field_1351;
      double var6 = var0.method_10260() + 0.5 - var1.field_1350;
      return var2 * var2 + var4 * var4 + var6 * var6;
   }

   private static Path saveFile() {
      return FabricLoader.getInstance().getGameDir().resolve("dihclient").resolve("autobuild_last.json");
   }

   private void save() {
      if (this.plan != null && mc.field_1687 != null) {
         try {
            JsonObject var1 = new JsonObject();
            var1.addProperty("file", this.plan.schematic.file.toAbsolutePath().toString());
            var1.addProperty("x", this.plan.anchor.method_10263());
            var1.addProperty("y", this.plan.anchor.method_10264());
            var1.addProperty("z", this.plan.anchor.method_10260());
            var1.addProperty("rotation", this.plan.rotation.name());
            var1.addProperty("mirror", this.plan.mirror.name());
            var1.addProperty("dimension", mc.field_1687.method_27983().method_29177().toString());
            var1.addProperty("server", mc.method_1558() == null ? "singleplayer" : mc.method_1558().field_3761);
            Files.createDirectories(saveFile().getParent());
            Files.writeString(saveFile(), GSON.toJson(var1));
         } catch (Exception var2) {
         }
      }
   }

   private static void deleteSave() {
      try {
         Files.deleteIfExists(saveFile());
      } catch (Exception var1) {
      }
   }

   public static String savedInfo() {
      try {
         if (!Files.exists(saveFile())) {
            return null;
         } else {
            JsonObject var0 = (JsonObject)GSON.fromJson(Files.readString(saveFile()), JsonObject.class);
            String var1 = Path.of(var0.get("file").getAsString()).getFileName().toString();
            return var1 + " @ " + var0.get("x").getAsInt() + " " + var0.get("y").getAsInt() + " " + var0.get("z").getAsInt();
         }
      } catch (Exception var2) {
         return null;
      }
   }

   public boolean resume() {
      return this.resume(false);
   }

   public boolean resumeVerify() {
      return this.resume(true);
   }

   private boolean resume(boolean var1) {
      try {
         if (!Files.exists(saveFile())) {
            Notifications.warn("AutoBuild", "No saved build");
            return false;
         } else {
            JsonObject var2 = (JsonObject)GSON.fromJson(Files.readString(saveFile()), JsonObject.class);
            String var3 = var2.has("dimension") ? var2.get("dimension").getAsString() : null;
            if (var3 != null && mc.field_1687 != null && !var3.equals(mc.field_1687.method_27983().method_29177().toString())) {
               Notifications.warn("AutoBuild", "The saved build is in " + var3);
               return false;
            } else {
               Schematic var4 = SchematicLoader.load(Path.of(var2.get("file").getAsString()));
               BuildPlan var5 = new BuildPlan(
                  var4,
                  new class_2338(var2.get("x").getAsInt(), var2.get("y").getAsInt(), var2.get("z").getAsInt()),
                  class_2470.valueOf(var2.get("rotation").getAsString()),
                  class_2415.valueOf(var2.get("mirror").getAsString())
               );
               if (var1) {
                  this.stop();
                  this.plan = var5;
                  this.beginVerify();
               } else {
                  this.start(var5);
               }

               return true;
            }
         }
      } catch (Exception var6) {
         Notifications.error("AutoBuild", "Could not resume: " + var6.getMessage());
         return false;
      }
   }

   public static Path exportMaterials(BuildPlan var0, Path var1) throws IOException {
      StringBuilder var2 = new StringBuilder();
      var2.append("Materials for ")
         .append(var0.schematic.name())
         .append(" (")
         .append(var0.sizeX)
         .append('x')
         .append(var0.sizeY)
         .append('x')
         .append(var0.sizeZ)
         .append(", ")
         .append(var0.blocks.size())
         .append(" blocks)\n\n");
      Map var3 = var0.materials();
      ArrayList var4 = new ArrayList(var3.entrySet());
      var4.sort((var0x, var1x) -> Integer.compare((Integer)var1x.getValue(), (Integer)var0x.getValue()));
      int var5 = 0;

      for (Entry var7 : var4) {
         int var8 = (Integer)var7.getValue();
         var5 += var8;
         int var9 = ((class_1792)var7.getKey()).method_7882();
         int var10 = var8 / var9;
         int var11 = var8 % var9;
         double var12 = var8 / (27.0 * var9);
         var2.append(
            String.format(
               Locale.ROOT,
               "%-32s %7d  = %d x %d + %d   (%.2f shulker boxes)%n",
               ((class_1792)var7.getKey()).method_63680().getString(),
               var8,
               var10,
               var9,
               var11,
               var12
            )
         );
      }

      var2.append(String.format(Locale.ROOT, "%nTotal: %d items%n", var5));
      Files.createDirectories(var1);
      Path var14 = var1.resolve(var0.schematic.name() + "_materials.txt");
      Files.writeString(var14, var2.toString());
      return var14;
   }

   public static enum Mode {
      AUTO,
      PRINTER;
   }

   public static enum Order {
      LAYERS,
      NEAREST;
   }

   public static enum Phase {
      IDLE,
      PREVIEW,
      BUILDING,
      PAUSED,
      FINISHED,
      VERIFY;
   }

   public static final class Settings {
      public BuildRuntime.Mode mode = BuildRuntime.Mode.AUTO;
      public BuildRuntime.Order order = BuildRuntime.Order.LAYERS;
      public double reach = 4.5;
      public int perTick = 1;
      public int delay = 1;
      public boolean silentRotate = true;
      public boolean walk = true;
      public boolean pillar = true;
      public Set<class_2248> scaffold = Set.of();
      public boolean fixWrong = false;
      public boolean clearArea = false;
      public boolean adjustStates = true;
      public boolean creativeStacks = true;
      public boolean giveCommand = false;
      public boolean autoBuy = false;
      public boolean restock = true;
      public boolean restockEarly = true;
      public int restockAt = 16;
      public int restockPerTick = 6;
      public int maxAttempts = 8;
      public boolean human = true;
      public float rotateSpeed = 32.0F;
      public boolean supports = true;
      public boolean removeSupports = true;
      public double randomness = 0.25;
      public boolean strict = true;
      public int fromLayer = 1;
      public int toLayer = 0;
      public int pausePlayers = 0;
      public int pauseDamage = 0;
      public boolean finishSound = true;
      public boolean airPlace = false;
      public boolean anyFace = false;
      public boolean predict = true;
      public int sneakMode = 0;
      public boolean legitFallback = true;
      public int maxFall = 3;
      public boolean escapeWater = true;
      public boolean checkMaterials = true;
      public boolean hammer = false;
   }

   public static enum Status {
      DONE,
      OPEN,
      INCREMENT,
      ADJUST,
      WRONG,
      SKIPPED;
   }
}
