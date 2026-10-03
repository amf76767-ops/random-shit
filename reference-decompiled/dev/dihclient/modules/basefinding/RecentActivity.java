package dev.dihclient.modules.basefinding;

import dev.dihclient.module.Category;
import dev.dihclient.module.Module;
import dev.dihclient.module.ModuleManager;
import dev.dihclient.render.Render3D;
import dev.dihclient.setting.BoolSetting;
import dev.dihclient.setting.IntSetting;
import dev.dihclient.util.Notifications;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.class_1297;
import net.minecraft.class_1299;
import net.minecraft.class_1308;
import net.minecraft.class_1657;
import net.minecraft.class_2246;
import net.minecraft.class_2248;
import net.minecraft.class_2338;
import net.minecraft.class_238;
import net.minecraft.class_243;
import net.minecraft.class_2680;
import net.minecraft.class_2741;
import net.minecraft.class_2769;
import net.minecraft.class_3414;
import net.minecraft.class_3417;
import net.minecraft.class_3481;
import net.minecraft.class_642;
import net.minecraft.class_6862;
import net.minecraft.class_7923;

public class RecentActivity extends Module {
   public final BoolSetting placed = this.bool("Placed Blocks", "Blocks only players place (chests, beds, torches, planks, rails, redstone …).", true);
   public final BoolSetting mined = this.bool("Mined Blocks", "Stone, ores, logs and player blocks disappearing (explosions and mobs are ignored).", true);
   public final BoolSetting used = this.bool("Interactions", "Doors, trapdoors, gates, levers, buttons, repeaters, candles …", true);
   public final BoolSetting containers = this.bool("Containers", "Chests, barrels and shulkers being opened.", true);
   public final IntSetting minDistance = this.integer("Min Distance", "Ignore changes closer than this (your own building).", 6, 0, 32);
   public final BoolSetting showBlocks = this.bool("Block Markers", "Box every changed block. Red < 1 min, orange < 5 min, yellow < 30 min, green older.", true);
   public final BoolSetting heatmap = this.bool("Heatmap", "Tints whole chunks by how much happened there.", true);
   public final BoolSetting labels = this.bool("Labels", "Count and age above every hotspot chunk.", true);
   public final IntSetting renderMinutes = this.integer("Show Minutes", "Hide markers older than this.", 120, 1, 1440);
   public final IntSetting renderRange = this.integer("Render Range", "Only draw markers within this many blocks.", 192, 32, 512);
   public final IntSetting hotspot = this.integer("Hotspot Size", "Changes in one chunk within 5 minutes that count as a hotspot.", 6, 2, 60);
   public final BoolSetting notifyHotspot = this.bool("Hotspot Alert", "Toast when a chunk becomes a hotspot.", true);
   public final BoolSetting notifyResume = this.bool(
      "Wake-up Alert", "Toast when a chunk that was quiet for Quiet Minutes is active again – someone came back.", true
   );
   public final IntSetting quietMinutes = this.integer("Quiet Minutes", "How long a chunk must be quiet before a wake-up alert.", 30, 5, 1440)
      .visibleWhen(this.notifyResume::get);
   public final BoolSetting sound = this.bool("Sound", "Plays a sound with alerts.", true);
   public final IntSetting keepDays = this.integer("Keep Days", "History stored per server and dimension (0 = this session only).", 7, 0, 90);
   private static final long HOTSPOT_WINDOW = 300000L;
   private static final int MAX_MARKS = 6000;
   private static final int MAX_SAVED_MARKS = 3000;
   private final LinkedHashMap<Long, RecentActivity.Mark> marks = new LinkedHashMap<>();
   private final Map<Long, RecentActivity.Spot> spots = new HashMap<>();
   private final Map<Long, Integer> chestViewers = new HashMap<>();
   private String key;
   private boolean dirty;
   private long lastSave;
   private class_243 lastExplosion;
   private long lastExplosionTime;
   private static final List<class_6862> PLAYER_TAGS = List.of(
      class_3481.field_16443,
      class_3481.field_41282,
      class_3481.field_21490,
      class_3481.field_15486,
      class_3481.field_15463,
      class_3481.field_15481,
      class_3481.field_15479,
      class_3481.field_26983,
      class_3481.field_15501,
      class_3481.field_15493,
      class_3481.field_15471,
      class_3481.field_15459,
      class_3481.field_15469,
      class_3481.field_15504,
      class_3481.field_16584,
      class_3481.field_15495,
      class_3481.field_15487,
      class_3481.field_25147,
      class_3481.field_23799,
      class_3481.field_61206
   );
   private static final Set<class_2248> PLAYER_BLOCKS = Set.of(
      class_2246.field_9980,
      class_2246.field_10181,
      class_2246.field_16333,
      class_2246.field_16334,
      class_2246.field_10034,
      class_2246.field_10380,
      class_2246.field_16328,
      class_2246.field_10443,
      class_2246.field_10312,
      class_2246.field_10200,
      class_2246.field_10228,
      class_2246.field_10336,
      class_2246.field_10099,
      class_2246.field_22092,
      class_2246.field_22093,
      class_2246.field_10523,
      class_2246.field_10301,
      class_2246.field_16541,
      class_2246.field_22110,
      class_2246.field_10485,
      class_2246.field_10333,
      class_2246.field_16329,
      class_2246.field_16336,
      class_2246.field_16331,
      class_2246.field_10083,
      class_2246.field_16335,
      class_2246.field_16337,
      class_2246.field_16330,
      class_2246.field_10327,
      class_2246.field_16492,
      class_2246.field_10091,
      class_2246.field_10450,
      class_2246.field_10377,
      class_2246.field_10560,
      class_2246.field_10615,
      class_2246.field_10282,
      class_2246.field_10363,
      class_2246.field_10375,
      class_2246.field_10033,
      class_2246.field_10285,
      class_2246.field_23152,
      class_2246.field_10316,
      class_2246.field_10223,
      class_2246.field_10179,
      class_2246.field_10183,
      class_2246.field_10343
   );
   private static final Pattern KEY_CHARS = Pattern.compile("[^a-z0-9._-]");
   private Object keyWorld;
   private class_642 keyServer;
   private String keyAddress;
   private String cachedKey;

   public RecentActivity() {
      super(
         "Recent Activity",
         Category.BASEFINDING,
         "Maps where players recently built, mined, used doors or opened chests – age-coloured markers + chunk heatmap, saved per server. Alerts when a quiet base wakes up. Anti-Xray safe."
      );
      this.action("Clear History", "Deletes the stored activity for this server and dimension.", this::clearHistory);
   }

   public static RecentActivity active() {
      return mc.field_1687 != null && mc.method_18854() && ModuleManager.on(RecentActivity.class) ? ModuleManager.of(RecentActivity.class) : null;
   }

   private static boolean isPlayerBlock(class_2680 var0) {
      if (PLAYER_BLOCKS.contains(var0.method_26204())) {
         return true;
      } else {
         for (class_6862 var2 : PLAYER_TAGS) {
            if (var0.method_26164(var2)) {
               return true;
            }
         }

         return false;
      }
   }

   private static boolean isMineable(class_2680 var0) {
      return !var0.method_26164(class_3481.field_25806) && !var0.method_26164(class_3481.field_25807) && !var0.method_26164(class_3481.field_15475)
         ? class_7923.field_41175.method_10221(var0.method_26204()).method_12832().endsWith("_ore")
         : true;
   }

   private static boolean isEmpty(class_2680 var0) {
      return var0.method_26215() || var0.method_27852(class_2246.field_10382) || var0.method_27852(class_2246.field_10164);
   }

   private static boolean changed(class_2680 var0, class_2680 var1, class_2769 var2) {
      return var0.method_28498(var2) && var1.method_28498(var2) && !var0.method_11654(var2).equals(var1.method_11654(var2));
   }

   private static boolean flag(class_2680 var0, class_2769 var1) {
      return Boolean.TRUE.equals(var0.method_11654(var1));
   }

   private static String pretty(class_2248 var0) {
      return var0.method_9518().getString();
   }

   private boolean mobNear(class_243 var1, double var2, class_1299... var4) {
      class_238 var5 = new class_238(var1, var1).method_1014(var2);

      for (Object var7 : mc.field_1687.method_8335(null, var5)) {
         class_1297 var8 = (class_1297)var7;
         if (var4.length == 0 && !(var8 instanceof class_1657) && var8.method_5805() && var8 instanceof class_1308) {
            return true;
         }

         for (class_1299 var12 : var4) {
            if (var8.method_5864() == var12) {
               return true;
            }
         }
      }

      return false;
   }

   private static boolean fireNear(class_2338 var0) {
      for (int var1 = -1; var1 <= 1; var1++) {
         for (int var2 = -1; var2 <= 1; var2++) {
            for (int var3 = -1; var3 <= 1; var3++) {
               if (mc.field_1687.method_8320(var0.method_10069(var1, var2, var3)).method_27852(class_2246.field_10036)) {
                  return true;
               }
            }
         }
      }

      return false;
   }

   private boolean tooClose(class_243 var1) {
      int var2 = this.minDistance.get();
      return mc.field_1724 == null || mc.field_1724.method_73189().method_1025(var1) < var2 * var2;
   }

   public void onExplosion(class_243 var1) {
      this.lastExplosion = var1;
      this.lastExplosionTime = System.currentTimeMillis();
   }

   private boolean explosionNear(class_243 var1) {
      return this.lastExplosion != null && System.currentTimeMillis() - this.lastExplosionTime < 3000L && this.lastExplosion.method_1025(var1) < 144.0;
   }

   public void onBlockChange(class_2338 var1, class_2680 var2) {
      if (mc.field_1687 != null) {
         class_2680 var3 = mc.field_1687.method_8320(var1);
         if (var3 != var2) {
            class_243 var4 = class_243.method_24953(var1);
            if (!this.tooClose(var4)) {
               if (var3.method_26204() == var2.method_26204()) {
                  if (var2.method_26204() == class_2246.field_16328) {
                     if (this.containers.get() && changed(var3, var2, class_2741.field_12537) && flag(var2, class_2741.field_12537)) {
                        this.record(var1, RecentActivity.Kind.CONTAINER, "Barrel opened");
                     }

                     return;
                  }

                  if (this.used.get()) {
                     class_2248 var5 = var2.method_26204();
                     String var6 = null;
                     RecentActivity.Kind var7 = RecentActivity.Kind.USED;
                     if (changed(var3, var2, class_2741.field_12537)) {
                        if (var5 == class_2246.field_16328) {
                           if (this.containers.get() && flag(var2, class_2741.field_12537)) {
                              this.record(var1, RecentActivity.Kind.CONTAINER, "Barrel opened");
                           }

                           return;
                        }

                        if (var2.method_26164(class_3481.field_15494) && this.mobNear(var4, 3.0, class_1299.field_6077, class_1299.field_17713)) {
                           return;
                        }

                        if (changed(var3, var2, class_2741.field_12484)) {
                           return;
                        }

                        var6 = pretty(var5) + (flag(var2, class_2741.field_12537) ? " opened" : " closed");
                     } else if ((var5 == class_2246.field_10363 || var2.method_26164(class_3481.field_15493))
                        && changed(var3, var2, class_2741.field_12484)
                        && flag(var2, class_2741.field_12484)) {
                        var6 = pretty(var5) + " pressed";
                     } else if (changed(var3, var2, class_2741.field_12494)
                        || changed(var3, var2, class_2741.field_12534)
                        || changed(var3, var2, class_2741.field_12501)) {
                        var6 = pretty(var5) + " adjusted";
                     } else if (changed(var3, var2, class_2741.field_12505)) {
                        var6 = "Cake eaten";
                     } else if (changed(var3, var2, class_2741.field_12544)
                        || changed(var3, var2, class_2741.field_17393)
                        || changed(var3, var2, class_2741.field_23187)) {
                        var6 = pretty(var5) + " used";
                     } else if ((var2.method_26164(class_3481.field_26983) || var2.method_26164(class_3481.field_23799))
                        && changed(var3, var2, class_2741.field_12548)
                        && flag(var2, class_2741.field_12548)) {
                        var6 = pretty(var5) + " lit";
                     }

                     if (var6 != null) {
                        this.record(var1, var7, var6);
                     }
                  }
               } else if (this.placed.get() && isPlayerBlock(var2) && !isPlayerBlock(var3) && !this.mobNear(var4, 3.0, class_1299.field_6091)) {
                  this.record(var1, RecentActivity.Kind.PLACED, pretty(var2.method_26204()) + " placed");
               } else if (this.mined.get()
                  && isEmpty(var2)
                  && (isMineable(var3) || isPlayerBlock(var3))
                  && !this.explosionNear(var4)
                  && !fireNear(var1)
                  && !this.mobNear(var4, 4.0, class_1299.field_6091, class_1299.field_6119, class_1299.field_6134)) {
                  this.record(var1, RecentActivity.Kind.MINED, pretty(var3.method_26204()) + " broken");
               }
            }
         }
      }
   }

   public void onBlockEvent(class_2338 var1, class_2248 var2, int var3, int var4) {
      if (this.containers.get() && var3 == 1 && mc.field_1687 != null) {
         boolean var5 = var2 == class_2246.field_10034
            || var2 == class_2246.field_10380
            || var2 == class_2246.field_10443
            || var2.method_9564().method_26164(class_3481.field_21490)
            || var2.method_9564().method_26164(class_3481.field_61206);
         if (var5) {
            long var6 = pack(var1.method_10263(), var1.method_10264(), var1.method_10260());
            int var8 = this.chestViewers.getOrDefault(var6, 0);
            this.chestViewers.put(var6, var4);
            if (var4 > var8) {
               class_243 var9 = class_243.method_24953(var1);
               if (!this.tooClose(var9) && !this.mobNear(var9, 3.0, class_1299.field_61221)) {
                  this.record(var1, RecentActivity.Kind.CONTAINER, pretty(var2) + " opened");
               }
            }
         }
      }
   }

   private static long pack(int var0, int var1, int var2) {
      return (var0 & 67108863L) << 38 | (var2 & 67108863L) << 12 | var1 & 4095L;
   }

   private static long chunkKey(int var0, int var1) {
      return var0 & 4294967295L | (var1 & 4294967295L) << 32;
   }

   private void record(class_2338 var1, RecentActivity.Kind var2, String var3) {
      this.ensureLoaded();
      long var4 = System.currentTimeMillis();
      int var6 = var1.method_10263();
      int var7 = var1.method_10264();
      int var8 = var1.method_10260();
      long var9 = pack(var6, var7, var8);
      RecentActivity.Mark var11 = this.marks.remove(var9);
      if (var11 == null) {
         var11 = new RecentActivity.Mark(var6, var7, var8);
      }

      var11.kind = var2;
      var11.what = var3;
      var11.last = var4;
      var11.count++;
      this.marks.put(var9, var11);
      if (this.marks.size() > 6000) {
         Iterator var12 = this.marks.values().iterator();
         var12.next();
         var12.remove();
      }

      int var17 = var6 >> 4;
      int var13 = var8 >> 4;
      long var14 = chunkKey(var17, var13);
      RecentActivity.Spot var16 = this.spots.get(var14);
      if (var16 == null) {
         var16 = new RecentActivity.Spot(var17, var13);
         var16.first = var4;
         this.spots.put(var14, var16);
      } else if (this.notifyResume.get() && var16.last > 0L && var4 - var16.last >= this.quietMinutes.get().intValue() * 60000L) {
         this.alert("Base woke up", var3 + " at " + var6 + " " + var7 + " " + var8 + " – quiet for " + age(var4 - var16.last), true);
      }

      var16.count++;
      var16.last = var4;
      var16.y = var7;
      var16.mask = var16.mask | 1 << var2.ordinal();
      if (var4 - var16.windowStart > 300000L) {
         var16.windowStart = var4;
         var16.windowCount = 0;
      }

      var16.windowCount++;
      if (var16.windowCount >= this.hotspot.get() && var16.notified < var16.windowStart) {
         var16.notified = var4;
         if (this.notifyHotspot.get()) {
            this.alert(
               "Activity hotspot",
               var16.windowCount + " changes near " + (var17 * 16 + 8) + " " + var7 + " " + (var13 * 16 + 8) + " (" + kinds(var16.mask) + ")",
               false
            );
         }
      }

      this.dirty = true;
   }

   private void alert(String var1, String var2, boolean var3) {
      Notifications.warn(var1, var2);
      if (var3) {
         Notifications.alert(var1.toUpperCase() + ": " + var2, 60);
      }

      if (this.sound.get() && mc.field_1724 != null) {
         mc.field_1724.method_5783(var3 ? class_3417.field_17265 : (class_3414)class_3417.field_14622.comp_349(), 1.0F, var3 ? 0.8F : 1.4F);
      }
   }

   private String currentKey() {
      if (mc.field_1687 == null) {
         return null;
      } else {
         class_642 var1 = mc.method_1558();
         String var2 = var1 != null ? var1.field_3761 : "singleplayer";
         if (this.cachedKey == null || this.keyWorld != mc.field_1687 || this.keyServer != var1 || !var2.equals(this.keyAddress)) {
            String var3 = mc.field_1687.method_27983().method_29177().toString();
            this.cachedKey = KEY_CHARS.matcher((var2 + "_" + var3).toLowerCase()).replaceAll("_");
            this.keyWorld = mc.field_1687;
            this.keyServer = var1;
            this.keyAddress = var2;
         }

         return this.cachedKey;
      }
   }

   private static Path dir() {
      return FabricLoader.getInstance().getGameDir().resolve("dihclient").resolve("activity");
   }

   private void ensureLoaded() {
      String var1 = this.currentKey();
      if (var1 != null && !var1.equals(this.key)) {
         this.save();
         this.marks.clear();
         this.spots.clear();
         this.chestViewers.clear();
         this.key = var1;
         this.load();
      }
   }

   private void load() {
      if (this.keepDays.get() > 0 && this.key != null) {
         Path var1 = dir().resolve(this.key + ".txt");
         if (Files.exists(var1)) {
            long var2 = System.currentTimeMillis() - this.keepDays.get().intValue() * 86400000L;

            try {
               ArrayList var4 = new ArrayList();

               for (String var6 : Files.readAllLines(var1, StandardCharsets.UTF_8)) {
                  String[] var7 = var6.split(" ", 8);

                  try {
                     if (var7[0].equals("S") && var7.length >= 8) {
                        RecentActivity.Spot var13 = new RecentActivity.Spot(Integer.parseInt(var7[1]), Integer.parseInt(var7[2]));
                        var13.count = Integer.parseInt(var7[3]);
                        var13.first = Long.parseLong(var7[4]);
                        var13.last = Long.parseLong(var7[5]);
                        var13.mask = Integer.parseInt(var7[6]);
                        var13.y = Integer.parseInt(var7[7]);
                        if (var13.last >= var2) {
                           this.spots.put(chunkKey(var13.cx, var13.cz), var13);
                        }
                     } else if (var7[0].equals("B") && var7.length >= 8) {
                        RecentActivity.Mark var8 = new RecentActivity.Mark(Integer.parseInt(var7[1]), Integer.parseInt(var7[2]), Integer.parseInt(var7[3]));
                        var8.kind = RecentActivity.Kind.valueOf(var7[4]);
                        var8.last = Long.parseLong(var7[5]);
                        var8.count = Integer.parseInt(var7[6]);
                        var8.what = var7[7];
                        if (var8.last >= var2) {
                           var4.add(var8);
                        }
                     }
                  } catch (RuntimeException var9) {
                  }
               }

               var4.sort((var0, var1x) -> Long.compare(var0.last, var1x.last));

               for (RecentActivity.Mark var12 : var4) {
                  this.marks.put(pack(var12.x, var12.y, var12.z), var12);
               }
            } catch (Exception var10) {
               Notifications.error("Recent Activity", "Could not read history: " + var10.getMessage());
            }
         }
      }

      this.dirty = false;
      this.lastSave = System.currentTimeMillis();
   }

   private void save() {
      if (this.dirty && this.key != null && this.keepDays.get() > 0) {
         this.dirty = false;
         this.lastSave = System.currentTimeMillis();
         StringBuilder var1 = new StringBuilder();
         var1.append("# DIHClient Recent Activity – S cx cz count first last mask y / B x y z kind last count what\n");

         for (RecentActivity.Spot var3 : this.spots.values()) {
            var1.append("S ")
               .append(var3.cx)
               .append(' ')
               .append(var3.cz)
               .append(' ')
               .append(var3.count)
               .append(' ')
               .append(var3.first)
               .append(' ')
               .append(var3.last)
               .append(' ')
               .append(var3.mask)
               .append(' ')
               .append(var3.y)
               .append('\n');
         }

         int var6 = Math.max(0, this.marks.size() - 3000);

         for (RecentActivity.Mark var4 : this.marks.values()) {
            if (var6-- <= 0) {
               var1.append("B ")
                  .append(var4.x)
                  .append(' ')
                  .append(var4.y)
                  .append(' ')
                  .append(var4.z)
                  .append(' ')
                  .append(var4.kind.name())
                  .append(' ')
                  .append(var4.last)
                  .append(' ')
                  .append(var4.count)
                  .append(' ')
                  .append(var4.what.replace('\n', ' '))
                  .append('\n');
            }
         }

         try {
            Files.createDirectories(dir());
            Path var8 = dir().resolve(this.key + ".txt");
            Path var9 = dir().resolve(this.key + ".tmp");
            Files.writeString(var9, var1.toString(), StandardCharsets.UTF_8);
            Files.move(var9, var8, StandardCopyOption.REPLACE_EXISTING);
         } catch (Exception var5) {
            Notifications.error("Recent Activity", "Could not save history: " + var5.getMessage());
         }
      }
   }

   private void clearHistory() {
      this.marks.clear();
      this.spots.clear();
      this.chestViewers.clear();
      this.dirty = false;
      String var1 = this.key != null ? this.key : this.currentKey();
      if (var1 != null) {
         try {
            Files.deleteIfExists(dir().resolve(var1 + ".txt"));
         } catch (Exception var3) {
         }
      }

      Notifications.info("Recent Activity", "History cleared.");
   }

   @Override
   protected void onEnable() {
      this.key = null;
      this.lastExplosion = null;
   }

   @Override
   protected void onDisable() {
      this.save();
   }

   @Override
   public void onWorldChange() {
      this.save();
      this.key = null;
      this.marks.clear();
      this.spots.clear();
      this.chestViewers.clear();
      this.lastExplosion = null;
   }

   @Override
   public void onTick() {
      this.ensureLoaded();
      if (this.dirty && System.currentTimeMillis() - this.lastSave > 60000L) {
         this.save();
      }

      if (this.chestViewers.size() > 1024) {
         this.chestViewers.clear();
      }
   }

   private static int ageColor(long var0) {
      if (var0 < 60000L) {
         return -52429;
      } else if (var0 < 300000L) {
         return -28640;
      } else {
         return var0 < 1800000L ? -8128 : -12517536;
      }
   }

   private static String age(long var0) {
      long var2 = var0 / 1000L;
      if (var2 < 60L) {
         return var2 + "s";
      } else if (var2 < 3600L) {
         return var2 / 60L + "m";
      } else {
         return var2 < 86400L ? var2 / 3600L + "h " + var2 % 3600L / 60L + "m" : var2 / 86400L + "d " + var2 % 86400L / 3600L + "h";
      }
   }

   private static String kinds(int var0) {
      StringBuilder var1 = new StringBuilder();

      for (RecentActivity.Kind var5 : RecentActivity.Kind.values()) {
         if ((var0 & 1 << var5.ordinal()) != 0) {
            if (var1.length() > 0) {
               var1.append(", ");
            }

            var1.append(var5.label);
         }
      }

      return var1.toString();
   }

   @Override
   public void onRender3D(Render3D var1) {
      if (mc.field_1724 != null) {
         long var2 = System.currentTimeMillis();
         long var4 = this.renderMinutes.get().intValue() * 60000L;
         class_243 var6 = mc.field_1724.method_73189();
         double var7 = (double)this.renderRange.get().intValue() * this.renderRange.get().intValue();
         if (this.showBlocks.get()) {
            int var9 = 0;

            for (RecentActivity.Mark var11 : this.marks.sequencedValues().reversed()) {
               long var12 = var2 - var11.last;
               if (var12 <= var4) {
                  double var14 = var11.x + 0.5 - var6.field_1352;
                  double var16 = var11.y + 0.5 - var6.field_1351;
                  double var18 = var11.z + 0.5 - var6.field_1350;
                  if (var14 * var14 + var16 * var16 + var18 * var18 <= var7) {
                     int var20 = ageColor(var12);
                     int var21 = var12 < 60000L ? 70 : 35;
                     var1.box(new class_238(var11.x, var11.y, var11.z, var11.x + 1, var11.y + 1, var11.z + 1), var20, var21, true);
                     if (++var9 >= 2000) {
                        break;
                     }
                  }
               }
            }
         }

         if (this.heatmap.get() || this.labels.get()) {
            for (RecentActivity.Spot var26 : this.spots.values()) {
               long var27 = var2 - var26.last;
               if (var27 <= var4) {
                  double var13 = var26.cx * 16 + 8;
                  double var15 = var26.cz * 16 + 8;
                  double var17 = var13 - var6.field_1352;
                  double var19 = var15 - var6.field_1350;
                  if (var17 * var17 + var19 * var19 <= var7) {
                     int var28 = ageColor(var27);
                     if (this.heatmap.get()) {
                        int var22 = Math.min(110, 12 + var26.count * 4);
                        double var23 = var26.y;
                        var1.box(new class_238(var26.cx * 16, var23, var26.cz * 16, var26.cx * 16 + 16, var23 + 0.05, var26.cz * 16 + 16), var28, var22, true);
                     }

                     if (this.labels.get() && var26.count >= 2) {
                        String var29 = var26.count + " changes · " + age(var27) + " ago";
                        double var30 = Math.sqrt(var17 * var17 + var19 * var19);
                        var1.text(var29, new class_243(var13, var26.y + 3.0, var15), var28, (float)Math.max(1.2, var30 / 9.0));
                     }
                  }
               }
            }
         }
      }
   }

   @Override
   public String getInfo() {
      long var1 = System.currentTimeMillis() - this.renderMinutes.get().intValue() * 60000L;
      int var3 = 0;

      for (RecentActivity.Spot var5 : this.spots.values()) {
         if (var5.last >= var1) {
            var3++;
         }
      }

      return Integer.toString(var3);
   }

   @Override
   public List<String> details() {
      ArrayList var1 = new ArrayList<>(this.spots.values());
      var1.sort((var0, var1x) -> Long.compare(var1x.last, var0.last));
      ArrayList var2 = new ArrayList();
      long var3 = System.currentTimeMillis();

      for (int var5 = 0; var5 < Math.min(8, var1.size()); var5++) {
         RecentActivity.Spot var6 = (RecentActivity.Spot)var1.get(var5);
         var2.add(
            var6.cx * 16
               + 8
               + " "
               + var6.y
               + " "
               + (var6.cz * 16 + 8)
               + " – "
               + var6.count
               + "× ("
               + kinds(var6.mask)
               + ") – "
               + age(var3 - var6.last)
               + " ago"
         );
      }

      if (var2.isEmpty()) {
         var2.add("No player activity recorded yet.");
      }

      return var2;
   }

   public static enum Kind {
      PLACED("placed"),
      MINED("mined"),
      USED("used"),
      CONTAINER("chests");

      final String label;

      private Kind(String nullxx) {
         this.label = nullxx;
      }
   }

   static final class Mark {
      final int x;
      final int y;
      final int z;
      RecentActivity.Kind kind;
      String what = "";
      long last;
      int count;

      Mark(int var1, int var2, int var3) {
         this.x = var1;
         this.y = var2;
         this.z = var3;
      }
   }

   static final class Spot {
      final int cx;
      final int cz;
      int count;
      long first;
      long last;
      int mask;
      int y;
      long windowStart;
      int windowCount;
      long notified;

      Spot(int var1, int var2) {
         this.cx = var1;
         this.cz = var2;
      }
   }
}
