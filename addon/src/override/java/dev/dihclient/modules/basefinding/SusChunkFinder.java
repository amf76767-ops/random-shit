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
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import net.minecraft.class_1923;
import net.minecraft.class_2246;
import net.minecraft.class_2248;
import net.minecraft.class_2282;
import net.minecraft.class_2338;
import net.minecraft.class_238;
import net.minecraft.class_2393;
import net.minecraft.class_2680;

/**
 * As in 5.6, with one change: the "Amethyst" part no longer counts visible amethyst clusters (anti-xray hides them) but takes the
 * finds of the amethyst bypass (the former Amethyst Bypass module, now inside this module: Method, Min Cells, Tracer), see
 * {@link #amethystSource}.
 */
public class SusChunkFinder extends ChunkMarkModule {
   /** Chunk key -> {points, finds, average y} of the amethyst bypass; set by the add-on. Null = the old cluster counting. */
   public static volatile java.util.function.Supplier<Map<Long, float[]>> amethystSource;

   public final IntSetting sensitivity = this.integer(
      "Sensitivity",
      "1 = many sus chunks (little evidence needed) … 15 = only rare, very certain ones. Points needed ≈ 2 at 1, 5 at 3, 15 at 8, 38 at 15 (1 grown kelp = 1 point).",
      3,
      1,
      15
   );
   public final IntSetting maxChunks = this.integer(
      "Max Chunks", "Most sus chunks marked at the same time – only the strongest ones are shown (closest first on ties).", 10, 1, 100
   );
   public final BoolSetting kelp = this.bool("Kelp", "Fully grown kelp (age 25) – 1 point each.", true).legacy("sus.kelp");
   public final BoolSetting vines = this.bool("Vines", "Vines hanging 6+ blocks – 1 point per vine.", true).legacy("sus.vines");
   public final BoolSetting cocoa = this.bool("Cocoa Beans", "Fully grown cocoa – 1/3 point (can generate grown).", true).legacy("sus.cocoa");
   public final BoolSetting amethyst = this.bool("Amethyst", "Amethyst geodes that anti-xray hides (bypass, see Method below) – 1 point per geode chunk, 1/4 point per hidden bud.", true).legacy("sus.amethyst");
   public final BoolSetting maybe = this.bool("Show Maybe", "Chunks with at least half the needed score get a dim mark.", true);
   public final ColorSetting color = this.color("Color", "Marker colour.", -61424);
   public final ColorSetting maybeColor = this.color("Maybe Color", "Colour of \"maybe\" chunks.", -26064).visibleWhen(this.maybe::get);
   public final IntSetting speed = this.integer("Scan Speed", "Chunks scanned per tick.", 3, 1, 16);
   public final BoolSetting highlightBlocks = this.bool(
      "Highlight Blocks", "Also outlines the grown blocks (vines, kelp …) that triggered the mark. Off = only the chunk is marked.", false
   );
   private final BlockScanner scanner = new BlockScanner(this::candidate, 3);
   private final Map<Long, SusChunkFinder.Score> scores = new HashMap<>();
   private final Map<Long, Integer> marks = new HashMap<>();
   private final Set<Long> announced = new HashSet<>();

   public SusChunkFinder() {
      super(
         "Sus ChunkFinder",
         Category.BASEFINDING,
         "Marks chunks where crops grew while a player was there: grown kelp, long vines, cocoa, amethyst. Labels show what was found."
      );
      this.opacity.set(120);
      this.scanner.setMaxPerChunk(1024);

      for (BoolSetting var2 : List.of(this.kelp, this.vines, this.cocoa, this.amethyst)) {
         var2.onChange(this::reset);
      }

      this.action("Clear", "Removes all marks and scans again.", this::reset);
   }

   private void reset() {
      this.scanner.clear();
      this.scores.clear();
      this.marks.clear();
      this.announced.clear();
   }

   private boolean candidate(class_2680 var1) {
      class_2248 var2 = var1.method_26204();
      if (this.kelp.get() && var2 == class_2246.field_9993 && (Integer)var1.method_11654(class_2393.field_22509) >= 25) {
         return true;
      } else if (this.amethyst.get() && amethystSource == null && var2 == class_2246.field_27161) {
         return true;
      } else {
         return this.cocoa.get() && var2 == class_2246.field_10302 && var1.method_11654(class_2282.field_10779) >= 2
            ? true
            : this.vines.get() && var2 == class_2246.field_10597;
      }
   }

   private float needed() {
      int var1 = this.sensitivity.get();
      return var1 + 1.0F + var1 * var1 / 10.0F;
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
            SusChunkFinder.Score var3 = this.score((List<class_2338>)var2.getValue());
            if (var3.points() > 0.0F) {
               this.scores.put((Long)var2.getKey(), var3);
            } else {
               this.scores.remove(var2.getKey());
            }
         }

         this.scores.keySet().removeIf(var1 -> !this.scanner.results().containsKey(var1));
         java.util.function.Supplier<Map<Long, float[]>> source = amethystSource;
         if (source != null && this.amethyst.get()) {
            Map<Long, float[]> geodes = source.get();
            if (geodes != null) {
               for (Entry<Long, float[]> e : geodes.entrySet()) {
                  float[] g = e.getValue();
                  SusChunkFinder.Score old = this.scores.get(e.getKey());
                  this.scores.put(e.getKey(), old == null
                     ? new SusChunkFinder.Score(g[0], 0, 0, 0, (int)g[1], g[2])
                     : new SusChunkFinder.Score(old.points() + g[0], old.kelp(), old.vines(), old.cocoa(), old.amethyst() + (int)g[1], old.avgY()));
               }
            }
         }
         float var11 = this.needed();
         this.marks.clear();
         ArrayList<Entry<Long, SusChunkFinder.Score>> var12 = new ArrayList<>(this.scores.entrySet());
         double var13 = mc.field_1724.method_23317();
         double var5 = mc.field_1724.method_23321();
         var12.sort((var4, var5x) -> {
            int var6 = Float.compare(((SusChunkFinder.Score)var5x.getValue()).points(), ((SusChunkFinder.Score)var4.getValue()).points());
            return var6 != 0 ? var6 : Double.compare(dist((Long)var4.getKey(), var13, var5), dist((Long)var5x.getKey(), var13, var5));
         });
         int var7 = this.maxChunks.get();

         for (Entry var9 : var12) {
            if (this.marks.size() >= var7) {
               break;
            }

            float var10 = ((SusChunkFinder.Score)var9.getValue()).points();
            if (var10 >= var11) {
               this.marks.put((Long)var9.getKey(), var10 >= var11 * 2.0F ? hotter(this.color.get()) : this.color.get());
               if (this.announced.add((Long)var9.getKey())) {
                  this.announce((Long)var9.getKey(), "Suspicious chunk: " + describe((SusChunkFinder.Score)var9.getValue()));
               }
            } else if (this.maybe.get() && var10 >= var11 / 2.0F) {
               this.marks.put((Long)var9.getKey(), this.maybeColor.get());
            }
         }
      }
   }

   private SusChunkFinder.Score score(List<class_2338> var1) {
      int var2 = 0;
      int var3 = 0;
      int var4 = 0;
      int var5 = 0;
      HashSet var6 = new HashSet();
      double var7 = 0.0;
      int var9 = 0;

      for (class_2338 var11 : var1) {
         class_2680 var12 = mc.field_1687.method_8320(var11);
         if (this.candidate(var12)) {
            var7 += var11.method_10264();
            var9++;
            class_2248 var13 = var12.method_26204();
            if (var13 == class_2246.field_9993) {
               var2++;
            } else if (var13 == class_2246.field_10302) {
               var4++;
            } else if (var13 == class_2246.field_27161) {
               var5++;
            } else if (var13 == class_2246.field_10597 && !mc.field_1687.method_8320(var11.method_10074()).method_27852(class_2246.field_10597)) {
               int var14 = 1;

               while (var14 < 32 && mc.field_1687.method_8320(var11.method_10086(var14)).method_27852(class_2246.field_10597)) {
                  var14++;
               }

               if (var14 >= 6 && var6.add(class_2338.method_10064(var11.method_10263(), 0, var11.method_10260()))) {
                  var3++;
               }
            }
         }
      }

      float var15 = var2 + var3 + var4 / 3.0F + var5 / 4.0F;
      return new SusChunkFinder.Score(var15, var2, var3, var4, var5, var9 > 0 ? var7 / var9 : Double.NaN);
   }

   private static double dist(long var0, double var2, double var4) {
      double var6 = (class_1923.method_8325(var0) << 4) + 8 - var2;
      double var8 = (class_1923.method_8332(var0) << 4) + 8 - var4;
      return var6 * var6 + var8 * var8;
   }

   private static int hotter(int var0) {
      int var1 = var0 >> 16 & 0xFF;
      int var2 = var0 >> 8 & 0xFF;
      int var3 = var0 & 0xFF;
      return 0xFF000000 | Math.min(255, var1 + 40) << 16 | (int)(var2 * 0.55) << 8 | (int)(var3 * 0.55);
   }

   @Override
   protected double chunkY(long var1) {
      SusChunkFinder.Score var3 = this.scores.get(var1);
      return var3 == null ? Double.NaN : var3.avgY();
   }

   @Override
   protected float chunkIntensity(long var1) {
      SusChunkFinder.Score var3 = this.scores.get(var1);
      return var3 == null ? 0.3F : Math.min(1.0F, var3.points() / (this.needed() * 2.0F));
   }

   @Override
   protected String chunkSubLabel(long var1) {
      SusChunkFinder.Score var3 = this.scores.get(var1);
      return var3 == null ? null : describe(var3);
   }

   private static String describe(SusChunkFinder.Score var0) {
      ArrayList var1 = new ArrayList();
      if (var0.kelp() > 0) {
         var1.add(var0.kelp() + " kelp");
      }

      if (var0.vines() > 0) {
         var1.add(var0.vines() + " long vines");
      }

      if (var0.cocoa() > 0) {
         var1.add(var0.cocoa() + " cocoa");
      }

      if (var0.amethyst() > 0) {
         var1.add(var0.amethyst() + " amethyst");
      }

      return String.join(", ", var1);
   }

   @Override
   protected String chunkLabel(long var1) {
      SusChunkFinder.Score var3 = this.scores.get(var1);
      return var3 == null ? null : (var3.points() >= this.needed() ? "SUS " : "maybe ") + String.format("%.1f", var3.points());
   }

   @Override
   public void onRender3D(Render3D var1) {
      super.onRender3D(var1);
      if (this.highlightBlocks.get()) {
         int var2 = this.color.get() | 0xFF000000;

         for (Entry var4 : this.scanner.results().entrySet()) {
            if (this.marks.containsKey(var4.getKey())) {
               int var5 = 0;

               for (class_2338 var7 : (List<class_2338>)var4.getValue()) {
                  if (var5++ > 64) {
                     break;
                  }

                  var1.box(new class_238(var7).method_1011(0.02), var2, 35, true);
               }
            }
         }
      }
   }

   @Override
   public List<String> details() {
      ArrayList var1 = new ArrayList();
      int var2 = 0;

      for (Entry var4 : this.scores.entrySet()) {
         if (((SusChunkFinder.Score)var4.getValue()).points() >= this.needed()) {
            var2++;
         }
      }

      var1.add("Suspicious chunks: " + var2 + " · score needed: " + String.format("%.1f", this.needed()) + " · max " + this.maxChunks.get());
      this.scores
         .entrySet()
         .stream()
         .filter(var1x -> var1x.getValue().points() >= this.needed() / 2.0F)
         .sorted((var0, var1x) -> Float.compare(var1x.getValue().points(), var0.getValue().points()))
         .limit(5L)
         .forEach(var1x -> {
            int var2x = (class_1923.method_8325(var1x.getKey()) << 4) + 8;
            int var3 = (class_1923.method_8332(var1x.getKey()) << 4) + 8;
            int var4x = mc.field_1724 == null ? 0 : (int)Math.hypot(var2x - mc.field_1724.method_23317(), var3 - mc.field_1724.method_23321());
            var1.add(String.format("%.1f pts · %dm · %s", var1x.getValue().points(), var4x, describe(var1x.getValue())));
         });
      return var1;
   }

   private record Score(float points, int kelp, int vines, int cocoa, int amethyst, double avgY) {
   }
}
