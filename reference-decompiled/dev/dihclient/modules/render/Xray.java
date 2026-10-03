package dev.dihclient.modules.render;

import dev.dihclient.mixin.accessor.SimpleOptionAccessor;
import dev.dihclient.module.Category;
import dev.dihclient.module.Module;
import dev.dihclient.module.ModuleManager;
import dev.dihclient.render.Render3D;
import dev.dihclient.scan.BlockScanner;
import dev.dihclient.setting.BoolSetting;
import dev.dihclient.setting.EnumSetting;
import dev.dihclient.setting.IdListSetting;
import dev.dihclient.setting.IntSetting;
import dev.dihclient.util.Compat;
import dev.dihclient.util.RegistryUtil;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import net.minecraft.class_2248;
import net.minecraft.class_2338;
import net.minecraft.class_238;
import net.minecraft.class_2680;

public class Xray extends Module {
   public final IdListSetting blocks = this.ids(
         "Blocks",
         "Blocks that stay visible.",
         IdListSetting.Kind.BLOCK,
         new String[]{
            "minecraft:diamond_ore",
            "minecraft:deepslate_diamond_ore",
            "minecraft:emerald_ore",
            "minecraft:deepslate_emerald_ore",
            "minecraft:gold_ore",
            "minecraft:deepslate_gold_ore",
            "minecraft:nether_gold_ore",
            "minecraft:iron_ore",
            "minecraft:deepslate_iron_ore",
            "minecraft:redstone_ore",
            "minecraft:deepslate_redstone_ore",
            "minecraft:lapis_ore",
            "minecraft:deepslate_lapis_ore",
            "minecraft:ancient_debris",
            "minecraft:spawner",
            "minecraft:chest",
            "minecraft:obsidian"
         }
      )
      .onChange(this::rebuild);
   public final EnumSetting<Xray.Mode> mode = this.mode(
         "Mode",
         "Auto: real chunk X-ray on the vanilla renderer, outlines with VulkanMod / Sodium. Chunk: hide all other blocks. Outline: keep the world, draw boxes around the selected blocks (no chunk reload, lag-friendly).",
         Xray.Mode.AUTO
      )
      .onChange(this::modeChanged);
   public final IntSetting range = this.integer("Outline Range", "Outline mode: max distance in blocks.", 64, 16, 256);
   public final IntSetting maxShown = this.integer("Outline Limit", "Outline mode: max number of boxes (nearest first).", 300, 20, 2000);
   public final BoolSetting fluids = this.bool("Show Fluids", "Keep water and lava visible.", false).onChange(this::reload);
   public final BoolSetting bright = this.bool("Fullbright", "Maximum brightness so ores deep underground are visible.", true);
   private static volatile Set<class_2248> visible = Set.of();
   private static volatile boolean active;
   private static volatile boolean showFluids;
   private Double previousGamma;
   private final BlockScanner scanner = new BlockScanner(var0 -> visible.contains(var0.method_26204()), 4);
   private List<class_2338> nearest = List.of();

   private boolean outline() {
      return this.mode.get() == Xray.Mode.OUTLINE || this.mode.get() == Xray.Mode.AUTO && !Compat.chunkXray();
   }

   private void modeChanged() {
      if (this.isEnabled()) {
         this.onDisable();
         this.onEnable();
      }
   }

   public Xray() {
      super("Xray", Category.RENDER, "Hides every block except the selected ones (ores, spawners, chests …).");
   }

   private void rebuild() {
      visible = RegistryUtil.blocks(this.blocks.get());
      this.scanner.clear();
      this.reload();
   }

   private void reload() {
      showFluids = this.fluids.get();
      if (this.isEnabled() && !this.outline() && mc.field_1769 != null) {
         mc.field_1769.method_3279();
      }
   }

   @Override
   protected void onEnable() {
      visible = RegistryUtil.blocks(this.blocks.get());
      showFluids = this.fluids.get();
      this.scanner.clear();
      this.nearest = List.of();
      if (!this.outline()) {
         active = true;
         if (mc.field_1769 != null) {
            mc.field_1769.method_3279();
         }
      }
   }

   @Override
   protected void onDisable() {
      boolean var1 = active;
      active = false;
      this.scanner.clear();
      if (var1 && mc.field_1769 != null) {
         mc.field_1769.method_3279();
      }

      if (this.previousGamma != null && mc.field_1690 != null && !ModuleManager.on(Fullbright.class)) {
         ((SimpleOptionAccessor)mc.field_1690.method_42473()).dih$setValueRaw(Math.min(1.0, this.previousGamma));
      }

      this.previousGamma = null;
   }

   @Override
   public void onTick() {
      if (this.outline()) {
         this.scanner.tick();
         if (mc.field_1724.field_6012 % 10 == 0) {
            double var1 = (double)this.range.get().intValue() * this.range.get().intValue();
            class_2338 var3 = mc.field_1724.method_24515();
            ArrayList var4 = new ArrayList();

            for (class_2338 var6 : this.scanner.all()) {
               if (var6.method_10262(var3) <= var1) {
                  var4.add(var6);
               }
            }

            var4.sort(Comparator.comparingDouble(var1x -> var1x.method_10262(var3)));
            this.nearest = var4.size() > this.maxShown.get() ? new ArrayList<>(var4.subList(0, this.maxShown.get())) : var4;
         }
      }

      if (this.bright.get()) {
         if (this.previousGamma == null) {
            this.previousGamma = (Double)mc.field_1690.method_42473().method_41753();
         }

         ((SimpleOptionAccessor)mc.field_1690.method_42473()).dih$setValueRaw(16.0);
      } else if (this.previousGamma != null) {
         if (!ModuleManager.on(Fullbright.class)) {
            ((SimpleOptionAccessor)mc.field_1690.method_42473()).dih$setValueRaw(Math.min(1.0, this.previousGamma));
         }

         this.previousGamma = null;
      }
   }

   @Override
   public void onWorldChange() {
      this.scanner.clear();
      this.nearest = List.of();
   }

   @Override
   public void onRender3D(Render3D var1) {
      if (this.outline()) {
         for (class_2338 var3 : this.nearest) {
            class_2680 var4 = mc.field_1687.method_8320(var3);
            if (visible.contains(var4.method_26204())) {
               int var5 = var4.method_26205(mc.field_1687, var3).field_16011 | 0xFF000000;
               var1.box(new class_238(var3), var5, 50, true);
            }
         }
      }
   }

   public static boolean active() {
      return active;
   }

   public static boolean isVisible(class_2680 var0) {
      return visible.contains(var0.method_26204());
   }

   public static boolean fluidsVisible() {
      return showFluids;
   }

   @Override
   public String getInfo() {
      return this.outline() ? "Outline " + this.nearest.size() : Integer.toString(this.blocks.get().size());
   }

   public static enum Mode {
      AUTO,
      CHUNK,
      OUTLINE;
   }
}
