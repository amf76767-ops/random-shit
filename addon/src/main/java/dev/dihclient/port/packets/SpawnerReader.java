package dev.dihclient.port.packets;

import dev.dihclient.DIHClient;
import dev.dihclient.module.Category;
import dev.dihclient.module.Module;
import dev.dihclient.port.PacketBus;
import dev.dihclient.render.Render3D;
import dev.dihclient.setting.BoolSetting;
import dev.dihclient.setting.ColorSetting;
import dev.dihclient.setting.DoubleSetting;
import dev.dihclient.setting.EnumSetting;
import dev.dihclient.setting.IntSetting;
import dev.dihclient.util.Notifications;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.class_2246;
import net.minecraft.class_2338;
import net.minecraft.class_238;
import net.minecraft.class_2586;
import net.minecraft.class_2596;
import net.minecraft.class_2626;
import net.minecraft.class_2636;
import net.minecraft.class_2637;
import net.minecraft.class_2672;
import net.minecraft.class_2680;
import net.minecraft.class_2818;
import net.minecraft.class_638;

public class SpawnerReader extends Module {
    public enum Highlight { CHUNK, BEAM }

    public enum Shape { SIDES, LINES, BOTH }

    private static final double WORLD_MIN_Y = -64;
    private static final double WORLD_MAX_Y = 320;

    public final IntSetting maxY = this.integer("Max Y", "Only report spawners below this Y level.", 16, -64, 320);
    public final BoolSetting chunkData = this.bool("Chunk Packets", "Read spawners listed in chunk packets.", true);
    public final BoolSetting blockUpdates = this.bool("Block Updates", "Read spawners in single and section block update packets.", true);
    public final BoolSetting render = this.bool("Render", "Highlight found spawners in the world.", true);
    public final EnumSetting<Highlight> highlight = this.mode("Highlight", "Chunk: outline the whole chunk the spawner is in · Beam: a vertical beam on the spawner.", Highlight.BEAM)
            .visibleWhen(this.render::get);
    public final DoubleSetting beamWidth = this.dbl("Beam Width", "Width of the beam in blocks.", 0.3, 0.05, 1.0, 0.05)
            .visibleWhen(() -> this.render.get() && this.highlight.get() == Highlight.BEAM);
    public final EnumSetting<Shape> shape = this.mode("Shape", "How the highlight is drawn.", Shape.BOTH)
            .visibleWhen(this.render::get);
    public final ColorSetting sideColor = this.color("Side Color", "Fill colour.", 0x28FFAA00)
            .visibleWhen(this.render::get);
    public final ColorSetting lineColor = this.color("Line Color", "Outline colour.", 0xFFFFAA00)
            .visibleWhen(this.render::get);

    private final Set<class_2338> spawners = new HashSet<>();
    private final PacketBus.Listener appliedListener = this::onApplied;

    public SpawnerReader() {
        super("Spawner Reader", Category.DEBUG, "Reads the server's packets and reports spawners below a Y level.");
    }

    @Override
    protected void onEnable() {
        this.spawners.clear();
        PacketBus.remove(this.appliedListener);
        PacketBus.applied(this.appliedListener);
    }

    @Override
    protected void onDisable() {
        PacketBus.remove(this.appliedListener);
        this.spawners.clear();
    }

    @Override
    public void onWorldChange() {
        this.spawners.clear();
    }

    @Override
    public String getInfo() {
        return Integer.toString(this.spawners.size());
    }

    private void onApplied(class_2596<?> packet) {
        try {
            if (this.blockUpdates.get() && packet instanceof class_2626 update) {
                this.check(update.method_11309(), update.method_11308());
            } else if (this.blockUpdates.get() && packet instanceof class_2637 section) {
                section.method_30621(this::check);
            } else if (this.chunkData.get() && packet instanceof class_2672 chunk) {
                this.scanChunk(chunk.method_11523(), chunk.method_11524());
            }
        } catch (Throwable t) {
            DIHClient.LOG.warn("[DIHClient] Spawner Reader could not read a packet", t);
        }
    }

    private void scanChunk(int chunkX, int chunkZ) {
        class_638 level = mc.field_1687;
        if (level == null || !level.method_2935().method_12123(chunkX, chunkZ)) {
            return;
        }
        class_2818 chunk = level.method_8497(chunkX, chunkZ);
        for (class_2586 entity : chunk.method_12214().values()) {
            if (entity instanceof class_2636) {
                this.report(entity.method_11016());
            }
        }
    }

    private void check(class_2338 pos, class_2680 state) {
        if (state.method_26204() == class_2246.field_10260) {
            this.report(pos);
        } else {
            this.spawners.remove(pos);
        }
    }

    private void report(class_2338 pos) {
        if (pos.method_10264() >= this.maxY.get()) {
            return;
        }

        if (!this.spawners.add(pos.method_10062())) {
            return;
        }
        String where = pos.method_10263() + ", " + pos.method_10264() + ", " + pos.method_10260();
        Notifications.chat("§7Spawner Reader: spawner at §f" + where);
        DIHClient.LOG.info("[SpawnerReader] spawner at {}", where);
    }

    @Override
    public void onRender3D(Render3D r) {
        if (!this.render.get() || this.spawners.isEmpty()) {
            return;
        }
        try {
            Shape mode = this.shape.get();
            int fill = this.sideColor.get();
            int line = this.lineColor.get();
            boolean chunks = this.highlight.get() == Highlight.CHUNK;
            double half = this.beamWidth.get() / 2;

            Set<Long> drawn = new HashSet<>();
            for (class_2338 pos : this.spawners) {
                class_238 box;
                if (chunks) {
                    int cx = pos.method_10263() >> 4;
                    int cz = pos.method_10260() >> 4;
                    if (!drawn.add(((long) cx << 32) | (cz & 0xFFFFFFFFL))) {
                        continue;
                    }
                    box = new class_238(cx << 4, WORLD_MIN_Y, cz << 4, (cx << 4) + 16, WORLD_MAX_Y, (cz << 4) + 16);
                } else {
                    double x = pos.method_10263() + 0.5;
                    double z = pos.method_10260() + 0.5;
                    box = new class_238(x - half, pos.method_10264(), z - half, x + half, WORLD_MAX_Y, z + half);
                }
                if (mode != Shape.LINES) {
                    r.boxFilled(box, fill, true);
                }
                if (mode != Shape.SIDES) {
                    r.boxOutline(box, line, true);
                }
            }
        } catch (Throwable t) {
            DIHClient.LOG.warn("[DIHClient] Spawner Reader could not draw", t);
        }
    }
}
