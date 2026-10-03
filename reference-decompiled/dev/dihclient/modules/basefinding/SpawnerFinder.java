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
import net.minecraft.class_2246;
import net.minecraft.class_2338;
import net.minecraft.class_238;
import net.minecraft.class_2680;

public class SpawnerFinder extends ChunkMarkModule {
   public final BoolSetting spawners = this.bool("Spawners", "Monster spawners (dungeons, mineshafts, fortresses).", true);
   public final BoolSetting trial = this.bool("Trial Chambers", "Trial spawners and vaults.", true);
   public final ColorSetting color = this.color("Color", "Chunk / box colour.", -12288);
   public final BoolSetting boxes = this.bool("Block Boxes", "Also outlines each spawner.", true);
   public final IntSetting speed = this.integer("Scan Speed", "Chunks scanned per tick.", 4, 1, 16);
   private final BlockScanner scanner = new BlockScanner(this::matches, 4);
   private final Map<Long, Integer> marks = new HashMap<>();
   private final List<class_2338> found = new ArrayList<>();
   private final Set<Long> announced = new HashSet<>();
   private final Map<Long, Integer> counts = new HashMap<>();
   private final Map<Long, Double> heights = new HashMap<>();

   @Override
   protected String chunkLabel(long var1) {
      Integer var3 = this.counts.get(var1);
      return var3 == null ? null : (var3 == 1 ? "Spawner" : var3 + " spawners");
   }

   @Override
   protected double chunkY(long var1) {
      Double var3 = this.heights.get(var1);
      return var3 == null ? Double.NaN : var3 - 1.0;
   }

   @Override
   protected float chunkIntensity(long var1) {
      Integer var3 = this.counts.get(var1);
      return var3 == null ? 0.3F : Math.min(1.0F, 0.4F + var3.intValue() * 0.2F);
   }

   public SpawnerFinder() {
      super("Spawner Finder", Category.BASEFINDING, "Marks chunks with monster spawners, trial spawners and vaults.");
      this.spawners.onChange(this.scanner::clear);
      this.trial.onChange(this.scanner::clear);
   }

   private boolean matches(class_2680 var1) {
      return this.spawners.get() && var1.method_27852(class_2246.field_10260)
         ? true
         : this.trial.get() && (var1.method_27852(class_2246.field_47336) || var1.method_27852(class_2246.field_48851));
   }

   @Override
   public Map<Long, Integer> chunkMarks() {
      return this.marks;
   }

   @Override
   protected void onEnable() {
      this.scanner.clear();
      this.marks.clear();
      this.announced.clear();
   }

   @Override
   public void onWorldChange() {
      this.onEnable();
      this.found.clear();
   }

   @Override
   public void onChunkLoaded(int var1, int var2) {
      this.scanner.prioritize(var1, var2);
   }

   @Override
   public void onTick() {
      this.scanner.setChunksPerTick(this.speed.get());
      this.scanner.tick();
      if (mc.field_1724.field_6012 % 10 == 0) {
         this.marks.clear();
         this.found.clear();
         this.counts.clear();
         this.heights.clear();

         for (Entry var2 : this.scanner.results().entrySet()) {
            int var3 = 0;
            double var4 = 0.0;

            for (class_2338 var7 : (List)var2.getValue()) {
               if (this.matches(mc.field_1687.method_8320(var7))) {
                  this.found.add(var7);
                  var3++;
                  var4 += var7.method_10264();
               }
            }

            if (var3 != 0) {
               this.marks.put((Long)var2.getKey(), this.color.get());
               this.counts.put((Long)var2.getKey(), var3);
               this.heights.put((Long)var2.getKey(), var4 / var3);
               if (this.announced.add((Long)var2.getKey())) {
                  this.announce((Long)var2.getKey(), var3 + (var3 == 1 ? " spawner" : " spawners"));
               }
            }
         }
      }
   }

   @Override
   public void onRender3D(Render3D var1) {
      super.onRender3D(var1);
      if (this.boxes.get()) {
         for (class_2338 var3 : this.found) {
            var1.box(new class_238(var3), this.color.get(), 60, true);
         }
      }
   }
}
