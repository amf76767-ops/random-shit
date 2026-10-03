package dev.dihclient.modules.render;

import dev.dihclient.module.Category;
import dev.dihclient.module.Module;
import dev.dihclient.render.Render3D;
import dev.dihclient.scan.BlockScanner;
import dev.dihclient.scan.ChunkEvents;
import dev.dihclient.setting.BoolSetting;
import dev.dihclient.setting.ColorSetting;
import dev.dihclient.setting.IdListSetting;
import dev.dihclient.setting.IntSetting;
import dev.dihclient.util.RegistryUtil;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.minecraft.class_2248;
import net.minecraft.class_2338;
import net.minecraft.class_238;
import net.minecraft.class_243;
import net.minecraft.class_2680;

public class BlockEsp extends Module implements ChunkEvents.Listener {
   public final IdListSetting blocks = this.ids(
         "Blocks",
         "Block types to highlight.",
         IdListSetting.Kind.BLOCK,
         new String[]{"minecraft:diamond_ore", "minecraft:deepslate_diamond_ore", "minecraft:ancient_debris"}
      )
      .legacy("blockEsp.blocks")
      .onChange(this::rebuild);
   public final BoolSetting mapColors = this.bool("Block Colors", "Uses each block's map colour instead of the fixed colour.", true);
   public final ColorSetting color = this.color("Color", "Fixed highlight colour.", -11141121);
   public final IntSetting fillAlpha = this.integer("Fill", "Fill opacity.", 40, 0, 255);
   public final BoolSetting throughWalls = this.bool("Through Walls", "Visible behind terrain.", true);
   public final BoolSetting tracers = this.bool("Tracers", "Lines to the blocks.", false);
   public final IntSetting maxShown = this.integer("Max Shown", "Maximum number of highlighted blocks (nearest first).", 400, 16, 4000);
   public final IntSetting speed = this.integer("Scan Speed", "Chunks scanned per tick.", 4, 1, 32);
   private volatile Set<class_2248> blockSet = Set.of();
   private final BlockScanner scanner = new BlockScanner(var1 -> this.blockSet.contains(var1.method_26204()), 4);
   private List<class_2338> nearest;
   private long lastSort;

   public BlockEsp() {
      super("Block ESP", Category.RENDER, "Highlights selected block types in all loaded chunks.");
   }

   private void rebuild() {
      this.blockSet = RegistryUtil.blocks(this.blocks.get());
      this.scanner.clear();
      this.nearest = null;
   }

   @Override
   protected void onEnable() {
      this.rebuild();
   }

   @Override
   protected void onDisable() {
      this.scanner.clear();
      this.nearest = null;
   }

   @Override
   public void onWorldChange() {
      this.scanner.clear();
      this.nearest = null;
   }

   @Override
   public void onChunkLoaded(int var1, int var2) {
      this.scanner.prioritize(var1, var2);
   }

   @Override
   public void onTick() {
      this.scanner.setChunksPerTick(this.speed.get());
      this.scanner.tick();
   }

   @Override
   public void onRender3D(Render3D var1) {
      class_243 var2 = var1.camera();
      long var3 = System.currentTimeMillis();
      if (var3 - this.lastSort > 250L || this.nearest == null) {
         this.lastSort = var3;
         List var5 = this.scanner.all();
         var5.sort((var1x, var2x) -> Double.compare(var1x.method_19770(var2), var2x.method_19770(var2)));
         int var6 = Math.min(var5.size(), this.maxShown.get());
         this.nearest = (List<class_2338>)(var6 < var5.size() ? new ArrayList<>(var5.subList(0, var6)) : var5);
      }

      List var11 = this.nearest;
      int var12 = Math.min(var11.size(), this.maxShown.get());

      for (int var7 = 0; var7 < var12; var7++) {
         class_2338 var8 = (class_2338)var11.get(var7);
         class_2680 var9 = mc.field_1687.method_8320(var8);
         if (this.blockSet.contains(var9.method_26204())) {
            int var10 = this.mapColors.get() ? 0xFF000000 | var9.method_26205(mc.field_1687, var8).field_16011 : this.color.get();
            var1.box(new class_238(var8), var10, this.fillAlpha.get(), this.throughWalls.get());
            if (this.tracers.get()) {
               var1.tracer(class_243.method_24953(var8), var10);
            }
         }
      }
   }

   @Override
   public String getInfo() {
      return Integer.toString(this.scanner.count());
   }
}
