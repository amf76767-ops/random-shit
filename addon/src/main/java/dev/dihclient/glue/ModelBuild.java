package dev.dihclient.glue;

import dev.dihclient.DIHClient;
import dev.dihclient.autobuild.Schematic;
import dev.dihclient.autobuild.SchematicLoader;
import dev.dihclient.autobuild.SchematicWriter;
import dev.dihclient.model3d.BlockPalette;
import dev.dihclient.model3d.Model;
import dev.dihclient.model3d.ModelLoader;
import dev.dihclient.model3d.Voxelizer;
import dev.dihclient.module.Category;
import dev.dihclient.module.Module;
import dev.dihclient.module.ModuleManager;
import dev.dihclient.modules.world.AutoBuild;
import dev.dihclient.setting.BoolSetting;
import dev.dihclient.setting.EnumSetting;
import dev.dihclient.setting.IntSetting;
import dev.dihclient.setting.StringSetting;
import dev.dihclient.util.Notifications;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.class_156;

/**
 * Builds a 3D model out of blocks: the model from the CustomModel folder is turned into a block sculpture whose colours
 * come from its texture (concrete, wool, terracotta ...), saved as a schematic and handed to AutoBuild, which shows the
 * preview and builds it like any other schematic.
 */
public class ModelBuild extends Module {
    private static final int DATA_VERSION = 4671;
    private static final int MAX_BLOCKS = 120_000;

    public final StringSetting file = this.text("Model", "File in .minecraft/dihclient/models (.glb .gltf .obj). Empty = the first / default model.",
            "", 200);
    public final IntSetting height = this.integer("Height", "Height of the sculpture in blocks. Taller = more detail, much more to build.", 40, 8, 160);
    public final EnumSetting<BlockPalette.Set> palette = this.mode("Palette",
            "Mixed (concrete, wool, terracotta, planks, sand, snow) · Concrete (clean colours) · Wool · Terracotta (earthy) · Wood and stone.", BlockPalette.Set.MIXED);
    public final IntSetting types = this.integer("Block Types", "At most this many different blocks (the rarest are replaced by similar ones). Fewer = easier to collect.",
            16, 2, 40);
    public final BoolSetting solid = this.bool("Solid", "Fill the inside as well. Hollow is much cheaper.", false);

    private volatile boolean working;
    private String info = "";
    private Map<String, Integer> counts = Map.of();

    public ModelBuild() {
        super("ModelBuild", Category.WORLD, "Turns a 3D model into a block sculpture and gives it to AutoBuild (preview, then build).");
        this.action("Generate", "Makes the schematic and opens the AutoBuild preview (aim, rotate, press Enter).", this::generate);
        this.action("Next Model", "Switches to the next model in the folder.", this::next);
        this.action("Open Models Folder", "Opens .minecraft/dihclient/models.", () -> class_156.method_668().method_672(CustomModel.modelDir().toFile()));
    }

    private void next() {
        List<Path> all = ModelFiles.list();
        if (all.isEmpty()) {
            Notifications.warn(this.name(), "No model in .minecraft/dihclient/models");
            return;
        }
        Path cur = ModelFiles.pick(this.file.get());
        int at = all.indexOf(cur);
        this.file.set(all.get((at + 1) % all.size()).getFileName().toString());
    }

    private void generate() {
        if (this.working) {
            return;
        }
        Path f = ModelFiles.pick(this.file.get());
        if (f == null) {
            Notifications.warn(this.name(), "No model yet. Put a .glb / .gltf / .obj into .minecraft/dihclient/models");
            return;
        }
        int h = this.height.get();
        BlockPalette.Set set = this.palette.get();
        int maxTypes = this.types.get();
        boolean fill = this.solid.get();
        this.working = true;
        Notifications.info(this.name(), "Working on " + f.getFileName() + " …");
        Thread t = new Thread(() -> {
            try {
                Model m = ModelLoader.load(f);
                Voxelizer.Grid g = Voxelizer.voxelize(m, h, fill, 160);
                if (g.count > MAX_BLOCKS) {
                    throw new IllegalStateException(g.count + " blocks is too many (limit " + MAX_BLOCKS + "). Use a lower Height or turn Solid off.");
                }
                List<BlockPalette.Entry> pal = BlockPalette.entries(set);
                Map<String, Integer> used = new HashMap<>();
                Map<Integer, String> cache = new HashMap<>();
                int[] ids = new int[g.rgb.length];
                String[] byIndex = new String[g.rgb.length];
                for (int i = 0; i < g.rgb.length; i++) {
                    if (g.rgb[i] != 0) {
                        String id = cache.computeIfAbsent(g.rgb[i] & 0xFFFFFF, c -> BlockPalette.nearest(pal, c).id());
                        byIndex[i] = id;
                        used.merge(id, 1, Integer::sum);
                    }
                }
                if (used.size() > maxTypes) { // keep the most used, give the rest the nearest of those
                    List<Map.Entry<String, Integer>> order = new ArrayList<>(used.entrySet());
                    order.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));
                    List<BlockPalette.Entry> kept = new ArrayList<>();
                    for (int i = 0; i < maxTypes; i++) {
                        String id = order.get(i).getKey();
                        pal.stream().filter(e -> e.id().equals(id)).findFirst().ifPresent(kept::add);
                    }
                    used.clear();
                    cache.clear();
                    for (int i = 0; i < g.rgb.length; i++) {
                        if (g.rgb[i] != 0) {
                            String id = cache.computeIfAbsent(g.rgb[i] & 0xFFFFFF, c -> BlockPalette.nearest(kept, c).id());
                            byIndex[i] = id;
                            used.merge(id, 1, Integer::sum);
                        }
                    }
                }
                List<SchematicWriter.Block> blocks = new ArrayList<>(g.count);
                for (int y = 0; y < g.sy; y++) {
                    for (int z = 0; z < g.sz; z++) {
                        for (int x = 0; x < g.sx; x++) {
                            String id = byIndex[g.index(x, y, z)];
                            if (id != null) {
                                blocks.add(new SchematicWriter.Block(x, y, z, id));
                            }
                        }
                    }
                }
                String name = ("model_" + m.name + "_" + h + (fill ? "_solid" : "")).replaceAll("[^A-Za-z0-9_-]", "_");
                Path out = AutoBuild.schematicDir().resolve(name + ".schem");
                SchematicWriter.writeSponge(out, blocks, DATA_VERSION);
                Schematic schem = SchematicLoader.load(out);
                String text = g.sx + "x" + g.sy + "x" + g.sz + ", " + blocks.size() + " blocks, " + used.size() + " types";
                mc.execute(() -> {
                    this.working = false;
                    this.info = text;
                    this.counts = used;
                    Notifications.info(this.name(), "Done: " + text + " – aim, rotate, press Enter to build");
                    ModuleManager.of(AutoBuild.class).startPreview(schem);
                });
            } catch (Throwable e) {
                DIHClient.LOG.warn("[DIHClient] ModelBuild failed", e);
                String msg = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
                mc.execute(() -> {
                    this.working = false;
                    Notifications.error(this.name(), "Failed: " + msg);
                });
            }
        }, "DIHClient-model-build");
        t.setDaemon(true);
        t.start();
    }

    @Override
    public String getInfo() {
        return this.working ? "…" : null;
    }

    @Override
    public List<String> details() {
        List<String> out = new ArrayList<>();
        Path f = ModelFiles.pick(this.file.get());
        out.add("Model: " + (f == null ? "none" : f.getFileName()));
        if (!this.info.isEmpty()) {
            out.add("Last: " + this.info);
            this.counts.entrySet().stream().sorted((a, b) -> Integer.compare(b.getValue(), a.getValue())).limit(8)
                    .forEach(e -> out.add(e.getValue() + " x " + e.getKey().replace("minecraft:", "")));
        }
        return out;
    }
}
