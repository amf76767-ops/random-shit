package dev.dihclient.modules.basefinding;

import dev.dihclient.module.Category;
import dev.dihclient.render.Render3D;
import dev.dihclient.scan.BlockScanner;
import dev.dihclient.scan.ChunkMarkModule;
import dev.dihclient.setting.BoolSetting;
import dev.dihclient.setting.ColorSetting;
import dev.dihclient.setting.IntSetting;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import net.minecraft.class_1297;
import net.minecraft.class_1923;
import net.minecraft.class_2248;
import net.minecraft.class_2338;
import net.minecraft.class_238;
import net.minecraft.class_7923;

public class BaseTraces extends ChunkMarkModule {
   public final IntSetting threshold = this.integer("Threshold", "Trace points a chunk needs to be marked.", 8, 2, 60);
   public final BoolSetting blocks = this.bool("Blocks", "Player-only blocks (signs, banners, heads, beacons, shulkers, concrete …).", true)
      .onChange(this::rescan);
   public final BoolSetting entities = this.bool("Entities", "Item frames, armor stands, paintings, end crystals outside the End.", true);
   public final BoolSetting highlight = this.bool("Highlight", "Small boxes on the traces inside marked chunks.", true);
   public final ColorSetting color = this.color("Color", "Marker colour.", -26317);
   public final IntSetting speed = this.integer("Scan Speed", "Chunks scanned per tick.", 3, 1, 16);
   private final Map<class_2248, BaseTraces.Trace> cache = new IdentityHashMap<>();
   private final BlockScanner scanner = new BlockScanner(var1 -> this.blocks.get() && this.trace(var1.method_26204()) != null, 3);
   private final Map<Long, Integer> marks = new HashMap<>();
   private final Map<Long, BaseTraces.Result> results = new HashMap<>();
   private final Set<Long> announced = new HashSet<>();

   public BaseTraces() {
      super(
         "Base Traces",
         Category.BASEFINDING,
         "Marks chunks full of things only players make: signs, banners, heads, beacons, shulkers, concrete, item frames, armor stands … with a list of what was found. Anti-Xray safe."
      );
      this.scanner.setMaxPerChunk(768);
      this.threshold.onChange(this::recount);
      this.action("Clear", "Removes all marks and scans again.", () -> {
         this.scanner.clear();
         this.results.clear();
         this.marks.clear();
         this.announced.clear();
      });
   }

   private BaseTraces.Trace trace(class_2248 var1) {
      BaseTraces.Trace var2 = this.cache.get(var1);
      if (var2 == null) {
         var2 = classify(class_7923.field_41175.method_10221(var1).method_12832());
         this.cache.put(var1, var2);
      }

      return var2 == BaseTraces.Trace.NONE ? null : var2;
   }

   private static BaseTraces.Trace classify(String var0) {
      if (var0.endsWith("_sign") || var0.endsWith("_hanging_sign")) {
         return new BaseTraces.Trace("sign", 3, 12);
      } else if (var0.endsWith("_banner")) {
         return new BaseTraces.Trace("banner", 3, 12);
      } else if (var0.equals("player_head") || var0.equals("player_wall_head")) {
         return new BaseTraces.Trace("player head", 6, 30);
      } else if (var0.equals("beacon")) {
         return new BaseTraces.Trace("beacon", 10, 40);
      } else if (var0.endsWith("shulker_box")) {
         return new BaseTraces.Trace("shulker box", 8, 48);
      } else if (var0.equals("ender_chest")) {
         return new BaseTraces.Trace("ender chest", 6, 24);
      } else if (var0.equals("respawn_anchor")) {
         return new BaseTraces.Trace("respawn anchor", 6, 24);
      } else if (var0.equals("enchanting_table")) {
         return new BaseTraces.Trace("enchanting table", 5, 10);
      } else if (var0.equals("anvil") || var0.equals("chipped_anvil") || var0.equals("damaged_anvil")) {
         return new BaseTraces.Trace("anvil", 3, 6);
      } else if (var0.equals("brewing_stand")) {
         return new BaseTraces.Trace("brewing stand", 2, 4);
      } else if (var0.equals("conduit") || var0.equals("lodestone") || var0.equals("netherite_block") || var0.equals("diamond_block")) {
         return new BaseTraces.Trace(var0.replace('_', ' '), 6, 30);
      } else if (var0.endsWith("_concrete")) {
         return new BaseTraces.Trace("concrete", 1, 10);
      } else if (var0.endsWith("_glazed_terracotta")) {
         return new BaseTraces.Trace("glazed terracotta", 1, 8);
      } else if (var0.equals("observer") || var0.equals("hopper")) {
         return new BaseTraces.Trace("redstone", 2, 8);
      } else {
         return var0.endsWith("froglight") ? new BaseTraces.Trace("froglight", 1, 6) : BaseTraces.Trace.NONE;
      }
   }

   private static BaseTraces.Trace entityTrace(String var0, boolean var1) {
      return switch (var0) {
         case "minecraft:item_frame", "minecraft:glow_item_frame" -> new BaseTraces.Trace("item frame", 3, 15);
         case "minecraft:armor_stand" -> new BaseTraces.Trace("armor stand", 3, 15);
         case "minecraft:painting" -> new BaseTraces.Trace("painting", 2, 10);
         case "minecraft:end_crystal" -> var1 ? null : new BaseTraces.Trace("end crystal", 5, 20);
         default -> null;
      };
   }

   @Override
   public Map<Long, Integer> chunkMarks() {
      return this.marks;
   }

   @Override
   protected void onEnable() {
      this.scanner.clear();
      this.results.clear();
      this.marks.clear();
      this.announced.clear();
   }

   @Override
   public void onWorldChange() {
      this.onEnable();
   }

   @Override
   public void onChunkLoaded(int var1, int var2) {
      this.scanner.prioritize(var1, var2);
   }

   private void rescan() {
      this.scanner.clear();
   }

   private void recount() {
      this.marks.clear();

      for (Entry var2 : this.results.entrySet()) {
         if (((BaseTraces.Result)var2.getValue()).score >= this.threshold.get()) {
            this.marks.put((Long)var2.getKey(), this.color.get());
         }
      }
   }

   @Override
   public void onTick() {
      this.scanner.setChunksPerTick(this.speed.get());
      this.scanner.tick();
      if (mc.field_1724.field_6012 % 20 == 0) {
         HashMap var1 = new HashMap();

         for (Entry var3 : this.scanner.results().entrySet()) {
            BaseTraces.Result var4 = new BaseTraces.Result();

            for (class_2338 var6 : (List)var3.getValue()) {
               BaseTraces.Trace var7 = this.trace(mc.field_1687.method_8320(var6).method_26204());
               if (var7 != null) {
                  var4.add(var7, var6.method_10264());
                  if (var4.spots.size() < 48) {
                     var4.spots.add(var6);
                  }
               }
            }

            if (var4.score > 0) {
               var1.put((Long)var3.getKey(), var4);
            }
         }

         if (this.entities.get()) {
            boolean var9 = mc.field_1687.method_27983().method_29177().method_12832().equals("the_end");

            for (class_1297 var13 : mc.field_1687.method_18112()) {
               BaseTraces.Trace var14 = entityTrace(class_7923.field_41177.method_10221(var13.method_5864()).toString(), var9);
               if (var14 != null) {
                  long var15 = class_1923.method_8331((int)Math.floor(var13.method_23317()) >> 4, (int)Math.floor(var13.method_23321()) >> 4);
                  BaseTraces.Result var8 = var1.computeIfAbsent(var15, var0 -> new BaseTraces.Result());
                  var8.add(var14, (int)var13.method_23318());
                  if (var8.spots.size() < 48) {
                     var8.spots.add(var13.method_24515());
                  }
               }
            }
         }

         this.results.clear();
         this.results.putAll(var1);
         this.marks.clear();

         for (Entry var12 : this.results.entrySet()) {
            if (((BaseTraces.Result)var12.getValue()).score >= this.threshold.get()) {
               this.marks.put((Long)var12.getKey(), this.color.get());
               if (this.announced.add((Long)var12.getKey())) {
                  this.announce((Long)var12.getKey(), "Base traces: " + ((BaseTraces.Result)var12.getValue()).describe(3));
               }
            }
         }
      }
   }

   @Override
   protected String chunkLabel(long var1) {
      BaseTraces.Result var3 = this.results.get(var1);
      return var3 == null ? null : "Traces " + var3.score;
   }

   @Override
   protected String chunkSubLabel(long var1) {
      BaseTraces.Result var3 = this.results.get(var1);
      return var3 == null ? null : var3.describe(4);
   }

   @Override
   protected double chunkY(long var1) {
      BaseTraces.Result var3 = this.results.get(var1);
      return var3 != null && var3.count > 0 ? (double)var3.ySum / var3.count - 1.0 : Double.NaN;
   }

   @Override
   protected float chunkIntensity(long var1) {
      BaseTraces.Result var3 = this.results.get(var1);
      return var3 == null ? 0.3F : Math.min(1.0F, var3.score / (this.threshold.get().intValue() * 3.0F));
   }

   @Override
   public void onRender3D(Render3D var1) {
      super.onRender3D(var1);
      if (this.highlight.get()) {
         int var2 = this.color.get() | 0xFF000000;

         for (Entry var4 : this.results.entrySet()) {
            if (this.marks.containsKey(var4.getKey())) {
               for (class_2338 var6 : ((BaseTraces.Result)var4.getValue()).spots) {
                  var1.box(new class_238(var6).method_1011(0.03), var2, 30, this.throughWalls.get());
               }
            }
         }
      }
   }

   @Override
   public List<String> details() {
      ArrayList var1 = new ArrayList();
      var1.add("Marked chunks: " + this.marks.size() + " · threshold " + this.threshold.get());
      ArrayList var2 = new ArrayList<>(this.results.entrySet());
      var2.sort((var0, var1x) -> Integer.compare(((BaseTraces.Result)var1x.getValue()).score, ((BaseTraces.Result)var0.getValue()).score));

      for (int var3 = 0; var3 < Math.min(5, var2.size()); var3++) {
         Entry var4 = (Entry)var2.get(var3);
         int var5 = (class_1923.method_8325((Long)var4.getKey()) << 4) + 8;
         int var6 = (class_1923.method_8332((Long)var4.getKey()) << 4) + 8;
         int var7 = mc.field_1724 == null ? 0 : (int)Math.hypot(var5 - mc.field_1724.method_23317(), var6 - mc.field_1724.method_23321());
         var1.add(((BaseTraces.Result)var4.getValue()).score + " pts · " + var7 + "m · " + ((BaseTraces.Result)var4.getValue()).describe(3));
      }

      return var1;
   }

   private static final class Result {
      final LinkedHashMap<String, Integer> counts = new LinkedHashMap<>();
      final Map<String, Integer> points = new HashMap<>();
      final List<class_2338> spots = new ArrayList<>();
      int score;
      long ySum;
      int count;

      void add(BaseTraces.Trace var1, int var2) {
         this.counts.merge(var1.name(), 1, Integer::sum);
         int var3 = this.points.getOrDefault(var1.name(), 0);
         int var4 = Math.min(var1.cap(), var3 + var1.weight());
         this.score += var4 - var3;
         this.points.put(var1.name(), var4);
         this.ySum += var2;
         this.count++;
      }

      String describe(int var1) {
         ArrayList var2 = new ArrayList<>(this.counts.entrySet());
         var2.sort((var0, var1x) -> Integer.compare((Integer)var1x.getValue(), (Integer)var0.getValue()));
         StringBuilder var3 = new StringBuilder();

         for (int var4 = 0; var4 < Math.min(var1, var2.size()); var4++) {
            if (var4 > 0) {
               var3.append(", ");
            }

            var3.append(((Entry)var2.get(var4)).getValue()).append(' ').append((String)((Entry)var2.get(var4)).getKey());
         }

         if (var2.size() > var1) {
            var3.append(" …");
         }

         return var3.toString();
      }
   }

   private record Trace(String name, int weight, int cap) {
      static final BaseTraces.Trace NONE = new BaseTraces.Trace("", 0, 0);
   }
}
