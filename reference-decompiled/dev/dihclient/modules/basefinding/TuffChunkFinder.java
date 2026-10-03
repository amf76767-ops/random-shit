package dev.dihclient.modules.basefinding;

import dev.dihclient.module.Category;
import dev.dihclient.scan.BlockScanner;
import dev.dihclient.scan.ChunkMarkModule;
import dev.dihclient.setting.BoolSetting;
import dev.dihclient.setting.ColorSetting;
import dev.dihclient.setting.IntSetting;
import dev.dihclient.util.RegistryUtil;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import net.minecraft.class_1923;
import net.minecraft.class_2338;
import net.minecraft.class_2680;
import net.minecraft.class_4482;

public class TuffChunkFinder extends ChunkMarkModule {
   public final BoolSetting poweredRepeaters = this.bool("Powered Repeaters", "Chunks with switched-on repeaters – someone built running redstone there.", true);
   public final IntSetting poweredMin = this.integer("Powered Min", "Powered repeaters needed.", 3, 1, 32).visibleWhen(this.poweredRepeaters::get);
   public final BoolSetting repeaters = this.bool("Any Repeaters", "Repeaters, also switched off.", true);
   public final IntSetting repeaterMin = this.integer("Repeater Min", "Repeaters needed.", 3, 1, 64).visibleWhen(this.repeaters::get);
   public final BoolSetting hives = this.bool("Bee Hives", "Bee hives / nests that have bees inside.", true);
   public final BoolSetting deepslate = this.bool("Cobbled Deepslate", "Cobbled deepslate between Y 0 and 20 – only exists where someone mined.", true);
   public final IntSetting deepslateMin = this.integer("Deepslate Min", "Cobbled deepslate blocks needed.", 50, 5, 1000).visibleWhen(this.deepslate::get);
   public final BoolSetting vines = this.bool("Vines", "Lots of vines in one chunk.", true);
   public final IntSetting vinesMin = this.integer("Vines Min", "Vine blocks needed.", 150, 10, 1000).visibleWhen(this.vines::get);
   public final BoolSetting seagrass = this.bool("Seagrass", "Lots of seagrass below Y 70.", true);
   public final IntSetting seagrassMin = this.integer("Seagrass Min", "Seagrass blocks needed.", 30, 5, 500).visibleWhen(this.seagrass::get);
   public final IntSetting minChance = this.integer("Min Chance", "Only marks chunks with at least this base chance (%).", 40, 1, 100);
   public final IntSetting maxChunks = this.integer("Max Chunks", "Most chunks marked at the same time – the strongest are kept.", 10, 1, 100);
   public final ColorSetting color = this.color("Color", "Marker colour (normal finds).", -13447886);
   public final ColorSetting repeaterColor = this.color("Repeater Color", "Marker colour for chunks with powered repeaters.", -8585393);
   public final IntSetting speed = this.integer("Scan Speed", "Chunks scanned per tick.", 3, 1, 16);
   private final BlockScanner scanner = new BlockScanner(this::candidate, 3);
   private final Map<Long, TuffChunkFinder.Find> finds = new HashMap<>();
   private final Map<Long, Integer> marks = new HashMap<>();
   private final Set<Long> announced = new HashSet<>();
   private static final int NONE = 0;
   private static final int REPEATER = 1;
   private static final int REPEATER_POWERED = 2;
   private static final int HIVE = 3;
   private static final int DEEPSLATE = 4;
   private static final int VINE = 5;
   private static final int SEAGRASS = 6;
   private static final Map<class_2680, Integer> KINDS = new IdentityHashMap<>();

   public TuffChunkFinder() {
      super(
         "TuffChunkFinder",
         Category.BASEFINDING,
         "Finds base chunks by powered repeaters, bee hives with bees, cobbled deepslate deep down, vines and seagrass – with a real base chance."
      );
      this.scanner.setMaxPerChunk(2048);

      for (BoolSetting var2 : List.of(this.poweredRepeaters, this.repeaters, this.hives, this.deepslate, this.vines, this.seagrass)) {
         var2.onChange(this::reset);
      }

      this.action("Clear", "Removes all marks and scans again.", this::reset);
   }

   private void reset() {
      this.scanner.clear();
      this.finds.clear();
      this.marks.clear();
      this.announced.clear();
   }

   private static String id(class_2680 var0) {
      return RegistryUtil.blockId(var0);
   }

   private static int kind(class_2680 var0) {
      Integer var1 = KINDS.get(var0);
      if (var1 == null) {
         String var2 = id(var0);

         var1 = switch (var2) {
            case "minecraft:repeater" -> var0.toString().contains("powered=true") ? 2 : 1;
            case "minecraft:beehive", "minecraft:bee_nest" -> 3;
            case "minecraft:cobbled_deepslate" -> 4;
            case "minecraft:vine" -> 5;
            case "minecraft:seagrass", "minecraft:tall_seagrass" -> 6;
            default -> 0;
         };
         KINDS.put(var0, var1);
      }

      return var1;
   }

   private boolean candidate(class_2680 var1) {
      switch (kind(var1)) {
         case 1:
         case 2:
            return this.poweredRepeaters.get() || this.repeaters.get();
         case 3:
            return this.hives.get();
         case 4:
            return this.deepslate.get();
         case 5:
            return this.vines.get();
         case 6:
            return this.seagrass.get();
         default:
            return false;
      }
   }

   @Override
   public Map<Long, Integer> chunkMarks() {
      return this.marks;
   }

   @Override
   protected void onEnable() {
      this.reset();
   }

   @Override
   public void onWorldChange() {
      this.reset();
   }

   @Override
   public void onChunkLoaded(int var1, int var2) {
      this.scanner.prioritize(var1, var2);
   }

   @Override
   public void onTick() {
      this.scanner.setChunksPerTick(this.speed.get());
      this.scanner.tick();
      if (mc.field_1724.field_6012 % 20 == 0) {
         for (Entry var2 : this.scanner.results().entrySet()) {
            TuffChunkFinder.Find var3 = this.score((List<class_2338>)var2.getValue());
            if (var3.chance() > 0) {
               this.finds.put((Long)var2.getKey(), var3);
            } else {
               this.finds.remove(var2.getKey());
            }
         }

         this.finds.keySet().removeIf(var1 -> !this.scanner.results().containsKey(var1));
         this.marks.clear();
         ArrayList var9 = new ArrayList<>(this.finds.entrySet());
         double var10 = mc.field_1724.method_23317();
         double var4 = mc.field_1724.method_23321();
         var9.sort((var4x, var5) -> {
            int var6 = Integer.compare(((TuffChunkFinder.Find)var5.getValue()).chance(), ((TuffChunkFinder.Find)var4x.getValue()).chance());
            return var6 != 0 ? var6 : Double.compare(dist((Long)var4x.getKey(), var10, var4), dist((Long)var5.getKey(), var10, var4));
         });

         for (Entry var7 : var9) {
            if (this.marks.size() >= this.maxChunks.get()) {
               break;
            }

            TuffChunkFinder.Find var8 = (TuffChunkFinder.Find)var7.getValue();
            if (var8.chance() >= this.minChance.get()) {
               this.marks
                  .put(
                     (Long)var7.getKey(), var8.powered() >= this.poweredMin.get() && this.poweredRepeaters.get() ? this.repeaterColor.get() : this.color.get()
                  );
               if (this.announced.add((Long)var7.getKey())) {
                  this.announce((Long)var7.getKey(), "Base chance " + var8.chance() + "% · " + describe(var8));
               }
            }
         }
      }
   }

   private TuffChunkFinder.Find score(List<class_2338> var1) {
      int var2 = 0;
      int var3 = 0;
      int var4 = 0;
      int var5 = 0;
      int var6 = 0;
      int var7 = 0;
      double var8 = 0.0;
      int var10 = 0;

      for (class_2338 var12 : var1) {
         class_2680 var13 = mc.field_1687.method_8320(var12);
         int var14 = var12.method_10264();
         switch (kind(var13)) {
            case 2:
               var2++;
            case 1:
               var3++;
               break;
            case 3:
               if (mc.field_1687.method_8321(var12) instanceof class_4482 var16 && var16.method_23903() > 0) {
                  var4++;
               }
               break;
            case 4:
               if (var14 >= 0 && var14 <= 20) {
                  var5++;
               }
               break;
            case 5:
               var6++;
               break;
            case 6:
               if (var14 <= 70) {
                  var7++;
               }
               break;
            default:
               continue;
         }

         var8 += var14;
         var10++;
      }

      double var17 = 0.0;
      if (this.poweredRepeaters.get() && var2 >= this.poweredMin.get()) {
         var17 += 70.0 + 15.0 * Math.min(1.0, (double)(var2 - this.poweredMin.get()) / this.poweredMin.get().intValue());
      } else if (this.repeaters.get() && var3 >= this.repeaterMin.get()) {
         var17 += 45.0 + 10.0 * Math.min(1.0, (double)(var3 - this.repeaterMin.get()) / this.repeaterMin.get().intValue());
      }

      if (this.hives.get() && var4 > 0) {
         var17 += 25.0 + Math.min(15.0, (var4 - 1) * 5.0);
      }

      if (this.deepslate.get() && var5 >= this.deepslateMin.get()) {
         var17 += 35.0 + 20.0 * Math.min(1.0, (double)(var5 - this.deepslateMin.get()) / this.deepslateMin.get().intValue());
      }

      if (this.vines.get() && var6 >= this.vinesMin.get()) {
         var17 += 20.0 + 10.0 * Math.min(1.0, (double)(var6 - this.vinesMin.get()) / this.vinesMin.get().intValue());
      }

      if (this.seagrass.get() && var7 >= this.seagrassMin.get()) {
         var17 += 15.0 + 10.0 * Math.min(1.0, (double)(var7 - this.seagrassMin.get()) / this.seagrassMin.get().intValue());
      }

      int var18 = (int)Math.round(Math.min(99.0, var17));
      return new TuffChunkFinder.Find(var18, var2, var3, var4, var5, var6, var7, var10 > 0 ? var8 / var10 : Double.NaN);
   }

   private static double dist(long var0, double var2, double var4) {
      double var6 = (class_1923.method_8325(var0) << 4) + 8 - var2;
      double var8 = (class_1923.method_8332(var0) << 4) + 8 - var4;
      return var6 * var6 + var8 * var8;
   }

   private static String describe(TuffChunkFinder.Find var0) {
      ArrayList var1 = new ArrayList();
      if (var0.powered() > 0) {
         var1.add(var0.powered() + " powered repeaters");
      } else if (var0.repeaters() > 0) {
         var1.add(var0.repeaters() + " repeaters");
      }

      if (var0.hives() > 0) {
         var1.add(var0.hives() + " hives with bees");
      }

      if (var0.deepslate() > 0) {
         var1.add(var0.deepslate() + " cobbled deepslate");
      }

      if (var0.vines() > 0) {
         var1.add(var0.vines() + " vines");
      }

      if (var0.seagrass() > 0) {
         var1.add(var0.seagrass() + " seagrass");
      }

      return String.join(", ", var1);
   }

   @Override
   protected double chunkY(long var1) {
      TuffChunkFinder.Find var3 = this.finds.get(var1);
      return var3 == null ? Double.NaN : var3.avgY();
   }

   @Override
   protected float chunkIntensity(long var1) {
      TuffChunkFinder.Find var3 = this.finds.get(var1);
      return var3 == null ? 0.3F : Math.max(0.3F, var3.chance() / 100.0F);
   }

   @Override
   protected String chunkLabel(long var1) {
      TuffChunkFinder.Find var3 = this.finds.get(var1);
      return var3 == null ? null : "Base " + var3.chance() + "%";
   }

   @Override
   protected String chunkSubLabel(long var1) {
      TuffChunkFinder.Find var3 = this.finds.get(var1);
      return var3 == null ? null : describe(var3);
   }

   @Override
   public List<String> details() {
      ArrayList var1 = new ArrayList();
      var1.add("Marked chunks: " + this.marks.size() + " · min chance " + this.minChance.get() + "%");
      this.finds
         .entrySet()
         .stream()
         .filter(var1x -> var1x.getValue().chance() >= this.minChance.get())
         .sorted((var0, var1x) -> Integer.compare(var1x.getValue().chance(), var0.getValue().chance()))
         .limit(5L)
         .forEach(var1x -> {
            int var2 = (class_1923.method_8325(var1x.getKey()) << 4) + 8;
            int var3 = (class_1923.method_8332(var1x.getKey()) << 4) + 8;
            int var4 = mc.field_1724 == null ? 0 : (int)Math.hypot(var2 - mc.field_1724.method_23317(), var3 - mc.field_1724.method_23321());
            var1.add(var1x.getValue().chance() + "% · " + var4 + "m · " + describe(var1x.getValue()));
         });
      return var1;
   }

   private record Find(int chance, int powered, int repeaters, int hives, int deepslate, int vines, int seagrass, double avgY) {
   }
}
