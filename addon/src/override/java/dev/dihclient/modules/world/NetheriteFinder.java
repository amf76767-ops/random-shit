package dev.dihclient.modules.world;

import dev.dihclient.module.Category;
import dev.dihclient.module.Module;
import dev.dihclient.modules.client.Performance;
import dev.dihclient.render.Render3D;
import dev.dihclient.scan.ChunkEvents;
import dev.dihclient.scan.ScanBudget;
import dev.dihclient.setting.BoolSetting;
import dev.dihclient.setting.ColorSetting;
import dev.dihclient.setting.DoubleSetting;
import dev.dihclient.setting.EnumSetting;
import dev.dihclient.setting.IntSetting;
import dev.dihclient.util.Compat;
import dev.dihclient.util.Notifications;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import net.minecraft.class_1297;
import net.minecraft.class_1542;
import net.minecraft.class_1799;
import net.minecraft.class_1802;
import net.minecraft.class_1923;
import net.minecraft.class_1937;
import net.minecraft.class_2246;
import net.minecraft.class_2338;
import net.minecraft.class_2350;
import net.minecraft.class_238;
import net.minecraft.class_2390;
import net.minecraft.class_243;
import net.minecraft.class_2680;
import net.minecraft.class_2806;
import net.minecraft.class_2818;
import net.minecraft.class_2826;
import net.minecraft.class_2846;
import net.minecraft.class_3414;
import net.minecraft.class_3417;
import net.minecraft.class_634;
import net.minecraft.class_2846.class_2847;

public class NetheriteFinder extends Module implements ChunkEvents.Listener {
   public final IntSetting minY = this.integer("Min Y", "Ignore debris below this height.", 8, -64, 320);
   public final IntSetting maxY = this.integer("Max Y", "Ignore debris above this height (most debris is between 8 and 22).", 119, -64, 320);
   public final BoolSetting onlyNether = this.bool("Only In Nether", "Only scans in the Nether.", true);
   public final BoolSetting antiXray = this.bool(
      "Anti-Xray Filter", "Hides buried debris in chunks that contain far more debris than vanilla generates (fake ores).", true
   );
   public final IntSetting fakeLimit = this.integer(
         "Fake Limit", "A chunk with more debris than this counts as fake-ore chunk. Vanilla rarely has more than ~6.", 10, 4, 40
      )
      .visibleWhen(this.antiXray::get);
   public final BoolSetting reveal = this.bool(
      "Anti-Xray Bypass",
      "Paper anti-xray sends the real blocks around every block you start mining. This sends short start/abort mining packets around you, so hidden debris is revealed and fake ores vanish. Off = only exposed debris is trustworthy on anti-xray servers.",
      true
   );
   public final EnumSetting<NetheriteFinder.RevealMode> revealMode = this.mode(
         "Bypass Mode",
         "Area: sweeps every spot around you (finds debris that is completely hidden) · Candidates: only checks debris the server showed you (fast, works against fake ores).",
         NetheriteFinder.RevealMode.AREA
      )
      .visibleWhen(this.reveal::get);
   public final DoubleSetting revealRange = this.dbl(
         "Bypass Range",
         "Radius around you that gets revealed. Up to ~5 looks like normal mining; more is faster but anti-cheats may notice.",
         5.0,
         2.0,
         12.0,
         0.5
      )
      .visibleWhen(this.reveal::get);
   public final IntSetting revealSpeed = this.integer("Bypass Speed", "Spots revealed per tick (each spot uncovers a 5x5x5 cube).", 2, 1, 10)
      .visibleWhen(this.reveal::get);
   public final BoolSetting onlyExposed = this.bool("Only Exposed", "Only show deposits that touch air or lava (100% real).", false);
   public final IntSetting maxShown = this.integer("Max Shown", "Maximum number of deposits rendered (nearest first).", 64, 1, 512);
   public final IntSetting fillAlpha = this.integer("Fill", "Fill opacity of deposit boxes.", 60, 0, 255);
   public final BoolSetting labels = this.bool("Labels", "Shows size and distance above each deposit.", true);
   public final BoolSetting tracers = this.bool("Tracers", "Lines to the nearest deposits.", true);
   public final IntSetting tracerCount = this.integer("Tracer Count", "How many of the nearest deposits get a tracer.", 3, 1, 20)
      .visibleWhen(this.tracers::get);
   public final BoolSetting notify = this.bool("Notify", "Toast with direction and distance when a new deposit is found.", true);
   public final BoolSetting onlyCertain = this.bool(
      "Only Certain Alerts",
      "Alerts only for debris that is 100% real: exposed debris (servers must send visible blocks truthfully, anti-xray can't fake them) or everything in singleplayer.",
      true
   );
   public final BoolSetting sound = this.bool("Sound", "Ping when a new deposit is found.", true).visibleWhen(this.notify::get);
   public final BoolSetting dropped = this.bool("Dropped Items", "Highlights dropped debris / scrap / netherite.", true);
   public final BoolSetting sectionHints = this.bool(
      "Section Hints", "Outlines 16³ sections whose palette mentions debris but no block is debris. Noisy on most servers.", false
   );
   public final IntSetting speed = this.integer("Scan Speed", "Chunks scanned per tick.", 4, 1, 32);
   public final ColorSetting buriedColor = this.color("Buried Color", "Colour of buried deposits.", -5214148);
   public final ColorSetting exposedColor = this.color("Exposed Color", "Colour of exposed (confirmed real) deposits.", -11731094);
   public final ColorSetting hintColor = this.color("Hint Color", "Colour of section hints.", -8750337).visibleWhen(this.sectionHints::get);
   private final Map<Long, NetheriteFinder.ChunkData> chunks = new HashMap<>();
   private final Deque<Long> queue = new ArrayDeque<>();
   private final Set<Long> queued = new HashSet<>();
   private final Set<class_2338> announced = new HashSet<>();
   private final Set<Long> confirmed = new HashSet<>();
   private final Set<Long> truthful = new HashSet<>();
   private final Set<Long> dug = new HashSet<>();
   private int revealed;
   private Object lastWorld;
   private long lastToast;
   private int suppressed;
   private List<NetheriteFinder.Deposit> sorted = List.of();
   private long lastParticles;

   public NetheriteFinder() {
      super("Netherite Finder", Category.WORLD, "Finds ancient debris: deposit sizes, exposed (real) debris, anti-xray fake-ore filter, alerts with direction.");

      for (IntSetting var2 : List.of(this.minY, this.maxY, this.fakeLimit)) {
         var2.onChange(this::clear);
      }

      this.antiXray.onChange(this::clear);
      this.sectionHints.onChange(this::clear);
      this.action("Rescan", "Scans all loaded chunks again.", this::clear);
   }

   @Override
   protected void onEnable() {
      this.clear();
      this.announced.clear();
   }

   @Override
   public void onWorldChange() {
      this.clear();
      this.announced.clear();
   }

   private void clear() {
      this.confirmed.clear();
      this.truthful.clear();
      this.dug.clear();
      this.areaKey = Long.MIN_VALUE;
      this.revealed = 0;
      this.chunks.clear();
      this.queue.clear();
      this.queued.clear();
      this.sorted = List.of();
   }

   private boolean active() {
      return !this.onlyNether.get() || mc.field_1687.method_27983() == class_1937.field_25180;
   }

   @Override
   public void onChunkLoaded(int var1, int var2) {
      long var3 = class_1923.method_8331(var1, var2);
      if (this.queued.add(var3)) {
         this.queue.addFirst(var3);
      }
   }

   @Override
   public void onBlockUpdate(class_2338 var1, class_2680 var2) {
      if (mc.field_1687 != null && this.active()) {
         long var3 = var1.method_10063();
         this.truthful.add(var3);
         boolean var5 = var2.method_27852(class_2246.field_22109);
         boolean var6 = var5 ? this.confirmed.add(var3) : this.confirmed.remove(var3);
         if (var6 || var5) {
            long var10 = class_1923.method_8331(var1.method_10263() >> 4, var1.method_10260() >> 4);
            if (this.queued.add(var10)) {
               this.queue.addFirst(var10);
            }
         } else if (this.dug.size() > 0) {
            long var7 = class_1923.method_8331(var1.method_10263() >> 4, var1.method_10260() >> 4);
            NetheriteFinder.ChunkData var9 = this.chunks.get(var7);
            if (var9 != null && !var9.deposits.isEmpty() && this.queued.add(var7)) {
               this.queue.add(var7);
            }
         }

         if (this.truthful.size() > 400000) {
            this.truthful.clear();
         }
      }
   }

   private boolean bypassAllowed() {
      return this.reveal.get() && !mc.method_1542() && mc.method_1562() != null && !mc.field_1724.method_31549().field_7477 && !mc.field_1724.method_7325();
   }

   private void dig(class_2338 var1) {
      class_634 var2 = mc.method_1562();
      var2.method_52787(new class_2846(class_2847.field_12968, var1, class_2350.field_11036, 0));
      var2.method_52787(new class_2846(class_2847.field_12971, var1, class_2350.field_11036, 0));
      this.revealed++;
   }

   private boolean diggable(class_2338 var1) {
      class_2680 var2 = mc.field_1687.method_8320(var1);
      return !var2.method_26215() && var2.method_26227().method_15769() && var2.method_26214(mc.field_1687, var1) >= 0.5F;
   }

   private void tickReveal() {
      if (this.bypassAllowed()) {
         int var1 = this.revealSpeed.get();
         class_243 var2 = mc.field_1724.method_33571();
         double var3 = this.revealRange.get();
         double var5 = var3 * var3;

         for (NetheriteFinder.Deposit var8 : this.sorted) {
            if (var1 <= 0) {
               return;
            }

            if (!var8.exposed() && !var8.confirmed()) {
               class_2338 var9 = var8.anchor();
               if (var9.method_19770(var2) > var5) {
                  break;
               }

               if (!this.truthful.contains(var9.method_10063()) && this.dug.add(var9.method_10063()) && this.diggable(var9)) {
                  this.dig(var9);
                  var1--;
               }
            }
         }

         if (this.revealMode.get() == NetheriteFinder.RevealMode.AREA) {
            this.sweepArea(var2, var3, var5, var1);
         }

         if (this.dug.size() > 200000) {
            this.dug.clear();
            this.areaKey = Long.MIN_VALUE;
         }
      }
   }

   // The area sweep: the spots around the player on a grid of 5 blocks, nearest first. They only change when the player moves into
   // another grid cell, so the list is built then (not every tick, with thousands of allocations) and walked with a pointer.
   private long[] areaSpots = new long[0];
   private int areaPos;
   private long areaKey = Long.MIN_VALUE;

   private void sweepArea(class_243 eye, double range, double rangeSq, int budget) {
      int reach = (int)Math.ceil(range);
      int cx = Math.floorDiv((int)Math.floor(eye.field_1352), 5) * 5 + 2;
      int cy = Math.floorDiv((int)Math.floor(eye.field_1351), 5) * 5 + 2;
      int cz = Math.floorDiv((int)Math.floor(eye.field_1350), 5) * 5 + 2;
      long key = class_2338.method_10064(cx, cy, cz) * 31L + reach * 1_000_003L + this.minY.get() * 7919L + this.maxY.get();
      if (key != this.areaKey) {
         this.areaKey = key;
         this.areaPos = 0;
         int low = this.minY.get();
         int high = this.maxY.get();
         java.util.ArrayList<long[]> found = new java.util.ArrayList<>();
         for (int dx = -reach - 5; dx <= reach + 5; dx += 5) {
            for (int dy = -reach - 5; dy <= reach + 5; dy += 5) {
               for (int dz = -reach - 5; dz <= reach + 5; dz += 5) {
                  int y = cy + dy;
                  if (y + 2 >= low && y - 2 <= high) {
                     double ex = cx + dx + 0.5 - eye.field_1352, ey = y + 0.5 - eye.field_1351, ez = cz + dz + 0.5 - eye.field_1350;
                     double d = ex * ex + ey * ey + ez * ez;
                     if (d <= (reach + 4.0) * (reach + 4.0)) {
                        found.add(new long[]{(long)(d * 1000.0), class_2338.method_10064(cx + dx, y, cz + dz)});
                     }
                  }
               }
            }
         }
         found.sort((p, q) -> Long.compare(p[0], q[0]));
         this.areaSpots = new long[found.size()];
         for (int i = 0; i < this.areaSpots.length; i++) {
            this.areaSpots[i] = found.get(i)[1];
         }
      }
      int left = budget;
      while (left > 0 && this.areaPos < this.areaSpots.length) {
         long packed = this.areaSpots[this.areaPos++];
         class_2338 spot = class_2338.method_10092(packed);
         if (spot.method_19770(eye) > rangeSq || !this.dug.add(packed)) {
            continue;
         }
         class_2338 target = this.solidNear(spot);
         if (target != null) {
            this.dig(target);
            left--;
         }
      }
   }

   private class_2338 solidNear(class_2338 var1) {
      if (this.diggable(var1)) {
         return var1;
      } else {
         for (int var2 = -1; var2 <= 1; var2++) {
            for (int var3 = -1; var3 <= 1; var3++) {
               for (int var4 = -1; var4 <= 1; var4++) {
                  class_2338 var5 = var1.method_10069(var2, var3, var4);
                  if (this.diggable(var5)) {
                     return var5;
                  }
               }
            }
         }

         return null;
      }
   }

   @Override
   public void onTick() {
      if (this.lastWorld != mc.field_1687) {
         this.lastWorld = mc.field_1687;
         this.clear();
      }

      if (this.active()) {
         class_1923 var1 = mc.field_1724.method_31476();
         if (mc.field_1724.field_6012 % 20 == 0) {
            for (int var2 = -2; var2 <= 2; var2++) {
               for (int var3 = -2; var3 <= 2; var3++) {
                  long var4 = class_1923.method_8331(var1.field_9181 + var2, var1.field_9180 + var3);
                  if (this.queued.add(var4)) {
                     this.queue.addFirst(var4);
                  }
               }
            }
         }

         if (this.queue.isEmpty()) {
            int var8 = mc.field_1690.method_38521() + 1;

            for (int var10 = -var8; var10 <= var8; var10++) {
               for (int var12 = -var8; var12 <= var8; var12++) {
                  long var5 = class_1923.method_8331(var1.field_9181 + var10, var1.field_9180 + var12);
                  if (!this.chunks.containsKey(var5)
                     && mc.field_1687.method_2935().method_12123(var1.field_9181 + var10, var1.field_9180 + var12)
                     && this.queued.add(var5)) {
                     this.queue.add(var5);
                  }
               }
            }

            this.chunks.keySet().removeIf(var0 -> !mc.field_1687.method_2935().method_12123(class_1923.method_8325(var0), class_1923.method_8332(var0)));
         }

         for (int var9 = 0; var9 < this.speed.get() && !this.queue.isEmpty() && (var9 <= 0 || ScanBudget.hasTime()); var9++) {
            long var11 = this.queue.poll();
            this.queued.remove(var11);
            this.scan(class_1923.method_8325(var11), class_1923.method_8332(var11));
         }

         if (mc.field_1724.field_6012 % 4 == 0) {
            this.refreshSorted();
         }

         try {
            this.tickReveal();
         } catch (Throwable var7) {
            this.reveal.set(false);
            Notifications.error(this.name(), "Anti-xray bypass not supported in this version – turned off.");
         }
      }
   }

   private void refreshSorted() {
      this.sorted = this.visible(mc.field_1724.method_33571());
   }

   public List<class_243> depositCenters() {
      ArrayList var1 = new ArrayList();

      for (NetheriteFinder.Deposit var3 : this.sorted) {
         var1.add(var3.box().method_1005());
      }

      return var1;
   }

   private void scan(int var1, int var2) {
      class_2818 var3 = mc.field_1687.method_2935().method_2857(var1, var2, class_2806.field_12803, false);
      long var4 = class_1923.method_8331(var1, var2);
      if (var3 == null) {
         this.chunks.remove(var4);
      } else {
         NetheriteFinder.ChunkData var6 = new NetheriteFinder.ChunkData();
         HashSet<class_2338> var7 = new HashSet<>();
         class_2826[] var8 = var3.method_12006();
         int var9 = mc.field_1687.method_31607() >> 4;
         int var10 = this.minY.get();
         int var11 = this.maxY.get();

         for (int var12 = 0; var12 < var8.length; var12++) {
            class_2826 var13 = var8[var12];
            int var14 = var9 + var12 << 4;
            if (var13 != null
               && !var13.method_38292()
               && var14 + 15 >= var10
               && var14 <= var11
               && var13.method_19523(var0 -> ((class_2680)var0).method_27852(class_2246.field_22109))) {
               int var15 = var1 << 4;
               int var16 = var2 << 4;
               int var17 = 0;

               for (int var18 = 0; var18 < 16; var18++) {
                  int var19 = var14 + var18;
                  if (var19 >= var10 && var19 <= var11) {
                     for (int var20 = 0; var20 < 16; var20++) {
                        for (int var21 = 0; var21 < 16; var21++) {
                           if (var13.method_12254(var21, var18, var20).method_27852(class_2246.field_22109)) {
                              var7.add(new class_2338(var15 + var21, var19, var16 + var20));
                              var17++;
                           }
                        }
                     }
                  }
               }

               if (var17 == 0 && this.sectionHints.get()) {
                  var6.hints.add(new class_238(var15, var14, var16, var15 + 16, var14 + 16, var16 + 16));
               }
            }
         }

         var6.debris = var7.size();
         var6.fake = this.antiXray.get() && var7.size() > this.fakeLimit.get();

         for (NetheriteFinder.Deposit var23 : this.deposits(var7)) {
            if (!var6.fake || var23.exposed() || var23.confirmed()) {
               var6.deposits.add(var23);
               if (this.announced.add(var23.anchor())) {
                  this.announce(var23);
               }
            }
         }

         if (var6.deposits.isEmpty() && var6.hints.isEmpty() && !var6.fake) {
            this.chunks.remove(var4);
         } else {
            this.chunks.put(var4, var6);
         }
      }
   }

   private List<NetheriteFinder.Deposit> deposits(Set<class_2338> var1) {
      ArrayList var2 = new ArrayList();
      HashSet var3 = new HashSet();

      for (class_2338 var5 : var1) {
         if (var3.add(var5)) {
            int var6 = var5.method_10263();
            int var7 = var5.method_10264();
            int var8 = var5.method_10260();
            int var9 = var6;
            int var10 = var7;
            int var11 = var8;
            int var12 = 0;
            boolean var13 = false;
            boolean var14 = false;
            class_2338 var15 = var5;
            ArrayDeque var16 = new ArrayDeque();
            var16.push(var5);

            while (!var16.isEmpty()) {
               class_2338 var17 = (class_2338)var16.pop();
               var12++;
               if (var17.method_10063() < var15.method_10063()) {
                  var15 = var17;
               }

               if (!var13 && this.isExposed(var17)) {
                  var13 = true;
               }

               if (!var14 && this.confirmed.contains(var17.method_10063())) {
                  var14 = true;
               }

               var6 = Math.min(var6, var17.method_10263());
               var7 = Math.min(var7, var17.method_10264());
               var8 = Math.min(var8, var17.method_10260());
               var9 = Math.max(var9, var17.method_10263());
               var10 = Math.max(var10, var17.method_10264());
               var11 = Math.max(var11, var17.method_10260());

               for (int var18 = -1; var18 <= 1; var18++) {
                  for (int var19 = -1; var19 <= 1; var19++) {
                     for (int var20 = -1; var20 <= 1; var20++) {
                        class_2338 var21 = var17.method_10069(var18, var19, var20);
                        if (var1.contains(var21) && var3.add(var21)) {
                           var16.push(var21);
                        }
                     }
                  }
               }
            }

            var2.add(new NetheriteFinder.Deposit(new class_238(var6, var7, var8, var9 + 1, var10 + 1, var11 + 1), var12, var13, var15, var14));
         }
      }

      return var2;
   }

   private boolean isExposed(class_2338 var1) {
      for (class_2350 var5 : class_2350.values()) {
         class_2680 var6 = mc.field_1687.method_8320(var1.method_10093(var5));
         if (!var6.method_27852(class_2246.field_22109) && (var6.method_26215() || !var6.method_26227().method_15769() || !var6.method_26216())) {
            return true;
         }
      }

      return false;
   }

   private void announce(NetheriteFinder.Deposit var1) {
      if (this.notify.get() && mc.field_1724 != null && (!this.onlyExposed.get() || var1.exposed()) && (!this.onlyCertain.get() || this.certain(var1))) {
         long var2 = System.currentTimeMillis();
         if (var2 - this.lastToast < 2500L) {
            this.suppressed++;
         } else {
            this.lastToast = var2;
            class_243 var4 = var1.box().method_1005();
            double var5 = var4.field_1352 - mc.field_1724.method_23317();
            double var7 = var4.field_1350 - mc.field_1724.method_23321();
            String var9 = (var1.count() > 1 ? var1.count() + "x " : "")
               + "Ancient Debris"
               + (var1.exposed() ? " (exposed)" : "")
               + " · "
               + direction(var5, var7)
               + " "
               + (int)Math.sqrt(var5 * var5 + var7 * var7)
               + "m · Y "
               + (int)var1.box().field_1322;
            if (this.suppressed > 0) {
               var9 = var9 + " (+" + this.suppressed + " more)";
               this.suppressed = 0;
            }

            Notifications.warn(this.name(), var9);
            if (this.sound.get()) {
               mc.field_1724.method_5783((class_3414)class_3417.field_14725.comp_349(), 0.8F, var1.exposed() ? 1.6F : 1.1F);
            }
         }
      }
   }

   private boolean certain(NetheriteFinder.Deposit var1) {
      return var1.exposed() || var1.confirmed() || mc.method_1542();
   }

   private static String direction(double var0, double var2) {
      String[] var4 = new String[]{"S", "SW", "W", "NW", "N", "NE", "E", "SE"};
      double var5 = Math.toDegrees(Math.atan2(-var0, var2));
      return var4[Math.floorMod(Math.round((float)(var5 / 45.0)), 8)];
   }

   private List<NetheriteFinder.Deposit> visible(class_243 var1) {
      ArrayList<NetheriteFinder.Deposit> var2 = new ArrayList<>();

      for (NetheriteFinder.ChunkData var4 : this.chunks.values()) {
         for (NetheriteFinder.Deposit var6 : var4.deposits) {
            if (!this.onlyExposed.get() || var6.exposed()) {
               var2.add(var6);
            }
         }
      }

      var2.sort((var1x, var2x) -> Double.compare(var1x.box().method_1005().method_1025(var1), var2x.box().method_1005().method_1025(var1)));
      return var2;
   }

   @Override
   public void onRender3D(Render3D var1) {
      if (this.active()) {
         class_243 var2 = var1.camera();
         List var3 = this.sorted;
         this.particles(var3);
         int var4 = Math.min(this.maxShown.get(), var3.size());

         for (int var5 = 0; var5 < var4; var5++) {
            NetheriteFinder.Deposit var6 = (NetheriteFinder.Deposit)var3.get(var5);
            int var7 = !var6.exposed() && !var6.confirmed() ? this.buriedColor.get() | 0xFF000000 : this.exposedColor.get();
            var1.box(var6.box(), var7, this.fillAlpha.get(), true);
            if (this.tracers.get() && var5 < this.tracerCount.get()) {
               var1.tracer(var6.box().method_1005(), var7);
            }

            if (this.labels.get()) {
               class_243 var8 = new class_243(var6.box().method_1005().field_1352, var6.box().field_1325 + 0.35, var6.box().method_1005().field_1350);
               double var9 = var2.method_1022(var8);
               String var11 = var6.count()
                  + "x Debris · "
                  + (int)var9
                  + "m"
                  + (this.certain(var6) ? (var6.confirmed() && !var6.exposed() ? " · revealed" : " · 100%") : " · unsure");
               var1.text(var11, var8, var7, (float)Math.max(1.0, var9 / 10.0));
            }
         }

         if (this.sectionHints.get()) {
            for (NetheriteFinder.ChunkData var14 : this.chunks.values()) {
               for (class_238 var18 : var14.hints) {
                  var1.boxOutline(var18, this.hintColor.get(), true);
               }
            }
         }

         if (this.dropped.get()) {
            for (class_1297 var15 : mc.field_1687.method_18112()) {
               if (var15 instanceof class_1542 var17) {
                  class_1799 var19 = var17.method_6983();
                  if (var19.method_31574(class_1802.field_22019) || var19.method_31574(class_1802.field_22021) || var19.method_31574(class_1802.field_22020)) {
                     var1.box(var15.method_5829().method_1014(0.1), -10166, 70, true);
                  }
               }
            }
         }
      }
   }

   private void particles(List<NetheriteFinder.Deposit> var1) {
      Performance var2 = Compat.perf();
      if (var2 != null && var2.particles() && !var1.isEmpty()) {
         long var3 = System.currentTimeMillis();
         if (var3 - this.lastParticles >= 400L) {
            this.lastParticles = var3;

            for (int var5 = 0; var5 < Math.min(8, var1.size()); var5++) {
               NetheriteFinder.Deposit var6 = (NetheriteFinder.Deposit)var1.get(var5);
               int var7 = var6.exposed() ? this.exposedColor.get() : this.buriedColor.get() & 16777215;
               class_2390 var8 = new class_2390(var7, 1.6F);
               class_243 var9 = var6.box().method_1005();

               for (int var10 = 0; var10 < 6; var10++) {
                  mc.field_1687.method_8466(var8, true, true, var9.field_1352, var9.field_1351 + var10 * 0.8, var9.field_1350, 0.0, 0.0, 0.0);
               }
            }
         }
      }
   }

   private int fakeChunks() {
      int var1 = 0;

      for (NetheriteFinder.ChunkData var3 : this.chunks.values()) {
         if (var3.fake) {
            var1++;
         }
      }

      return var1;
   }

   @Override
   public String getInfo() {
      int var1 = 0;

      for (NetheriteFinder.ChunkData var3 : this.chunks.values()) {
         for (NetheriteFinder.Deposit var5 : var3.deposits) {
            if (!this.onlyExposed.get() || var5.exposed()) {
               var1 += var5.count();
            }
         }
      }

      return Integer.toString(var1);
   }

   @Override
   public List<String> details() {
      ArrayList var1 = new ArrayList();
      if (mc.field_1724 != null && mc.field_1687 != null) {
         if (!this.active()) {
            var1.add("Idle – not in the Nether");
            return var1;
         } else {
            int var2 = 0;
            int var3 = 0;
            int var4 = 0;

            for (NetheriteFinder.ChunkData var6 : this.chunks.values()) {
               for (NetheriteFinder.Deposit var8 : var6.deposits) {
                  var2++;
                  var3 += var8.count();
                  if (var8.exposed()) {
                     var4++;
                  }
               }
            }

            var1.add(var3 + " debris in " + var2 + " deposits (" + var4 + " exposed)");
            List var11 = this.visible(mc.field_1724.method_73189());
            if (!var11.isEmpty()) {
               class_243 var12 = ((NetheriteFinder.Deposit)var11.get(0)).box().method_1005();
               double var14 = var12.field_1352 - mc.field_1724.method_23317();
               double var9 = var12.field_1350 - mc.field_1724.method_23321();
               var1.add(
                  String.format(
                     Locale.ROOT,
                     "Nearest: %s %dm, Y %d",
                     direction(var14, var9),
                     (int)Math.sqrt(var14 * var14 + var9 * var9),
                     (int)((NetheriteFinder.Deposit)var11.get(0)).box().field_1322
                  )
               );
            }

            int var13 = this.fakeChunks();
            if (var13 > 0) {
               var1.add("Anti-xray fake ores in " + var13 + " chunks – only exposed / revealed debris shown there");
            }

            if (this.bypassAllowed()) {
               var1.add("Anti-xray bypass: " + this.revealed + " spots revealed, " + this.confirmed.size() + " real debris confirmed");
            }

            return var1;
         }
      } else {
         return var1;
      }
   }

   private static final class ChunkData {
      final List<NetheriteFinder.Deposit> deposits = new ArrayList<>();
      final List<class_238> hints = new ArrayList<>();
      int debris;
      boolean fake;
   }

   private record Deposit(class_238 box, int count, boolean exposed, class_2338 anchor, boolean confirmed) {
   }

   public static enum RevealMode {
      AREA,
      CANDIDATES;
   }
}
