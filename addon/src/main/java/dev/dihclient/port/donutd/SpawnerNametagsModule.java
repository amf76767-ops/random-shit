package dev.dihclient.port.donutd;

import dev.dihclient.DIHClient;
import dev.dihclient.mixin.port.DonutDSpawnerAccessorMixin;
import dev.dihclient.module.Category;
import dev.dihclient.module.Module;
import dev.dihclient.render.Render3D;
import dev.dihclient.setting.BoolSetting;
import dev.dihclient.setting.ColorSetting;
import dev.dihclient.setting.IntSetting;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.class_1299;
import net.minecraft.class_1923;
import net.minecraft.class_1952;
import net.minecraft.class_2338;
import net.minecraft.class_243;
import net.minecraft.class_2586;
import net.minecraft.class_2636;
import net.minecraft.class_2818;
import net.minecraft.class_638;

/** Ported from an open-source client (GPL-3.0). */
public final class SpawnerNametagsModule extends Module {
    private static final int MAX_LABELS = 48;
    private static final int RING_SEGMENTS = 64;
    private static final int SCAN_TICKS = 10;
    private static final double LABEL_HEIGHT = 1.2;
    private static final int TITLE_COLOR = 0xFFFFFFFF;
    private static final int DETAIL_COLOR = 0xFFC8C8C8;

    public final IntSetting range = this.integer("Range", "How far away spawners are labelled (blocks).", 64, 8, 256);
    public final BoolSetting activationRadius = this.bool("Activation Radius", "Draws the range in which the spawner works, and writes it in the label.", true);
    public final ColorSetting color = this.color("Color", "Colour of the ring, and of the range text while you are inside it.", 0xFF55FFFF);

    private final List<class_2338> spawners = new ArrayList<>();
    private class_638 lastWorld;
    private int tick;
    private boolean broken;

    public SpawnerNametagsModule() {
        super("Spawner Nametags", Category.DONUT, "Shows each spawner's mob and activation range over the block.");
    }

    @Override
    protected void onEnable() {
        this.broken = false;
        this.clear();
    }

    @Override
    protected void onDisable() {
        this.clear();
    }

    @Override
    public void onWorldChange() {
        this.clear();
    }

    @Override
    public String getInfo() {
        return Integer.toString(this.spawners.size());
    }

    private void clear() {
        this.spawners.clear();
        this.tick = SCAN_TICKS;
    }

    public static String mobName(class_2636 spawner) {
        class_1952 data = ((DonutDSpawnerAccessorMixin) spawner.method_11390()).dih$donutdNextSpawnData();
        if (data == null) {
            return null;
        }
        String id = data.comp_64().method_68564("id", "");
        return id.isEmpty() ? null : class_1299.method_5898(id).map(type -> type.method_5897().getString()).orElse(null);
    }

    public static int activationRange(class_2636 spawner) {
        return ((DonutDSpawnerAccessorMixin) spawner.method_11390()).dih$donutdRequiredPlayerRange();
    }

    @Override
    public void onTick() {
        class_638 level = mc.field_1687;
        if (level != this.lastWorld) {
            this.clear();
            this.lastWorld = level;
        }
        if (this.broken || level == null || mc.field_1724 == null) {
            return;
        }
        if (++this.tick < SCAN_TICKS) {
            return;
        }
        this.tick = 0;
        try {
            this.scan(level);
        } catch (Throwable t) {
            this.broken = true;
            this.spawners.clear();
            DIHClient.LOG.warn("[DIHClient] Spawner Nametags stopped after an error", t);
        }
    }

    private void scan(class_638 level) {
        this.spawners.clear();
        class_1923 center = mc.field_1724.method_31476();
        int radius = this.range.get() / 16 + 1;
        for (int x = center.field_9181 - radius; x <= center.field_9181 + radius; x++) {
            for (int z = center.field_9180 - radius; z <= center.field_9180 + radius; z++) {
                if (!level.method_2935().method_12123(x, z)) {
                    continue;
                }
                class_2818 chunk = level.method_8497(x, z);
                for (class_2586 entity : chunk.method_12214().values()) {
                    if (entity instanceof class_2636) {
                        this.spawners.add(entity.method_11016().method_10062());
                    }
                }
            }
        }
    }

    private record Shown(class_2338 pos, double distance, String mob, int range, boolean inside) {
    }

    private List<Shown> inRange() {
        List<Shown> shown = new ArrayList<>();
        if (this.spawners.isEmpty() || mc.field_1687 == null || mc.field_1724 == null) {
            return shown;
        }
        class_243 player = mc.field_1724.method_73189();
        double limit = this.range.get();
        for (class_2338 pos : this.spawners) {
            double distanceSq = player.method_1028(pos.method_10263() + 0.5, pos.method_10264() + 0.5, pos.method_10260() + 0.5);
            if (distanceSq > limit * limit || !(mc.field_1687.method_8321(pos) instanceof class_2636 spawner)) {
                continue;
            }
            int activation = activationRange(spawner);
            shown.add(new Shown(pos, Math.sqrt(distanceSq), mobName(spawner), activation, DonutDLogic.isInside(distanceSq, activation)));
        }
        shown.sort(Comparator.comparingDouble(Shown::distance));
        return shown.size() > MAX_LABELS ? shown.subList(0, MAX_LABELS) : shown;
    }

    @Override
    public void onRender3D(Render3D r) {
        if (this.broken || mc.field_1687 == null || mc.field_1724 == null || this.spawners.isEmpty()) {
            return;
        }
        try {
            boolean ring = this.activationRadius.get();
            int rgb = this.color.get() & 0xFFFFFF;
            double limit = this.range.get();
            List<Shown> shown = this.inRange();

            for (int i = shown.size() - 1; i >= 0; i--) {
                Shown spawner = shown.get(i);
                float fade = DonutDLogic.fade(spawner.distance(), limit);
                if (ring && spawner.range() > 0) {
                    int ringColor = DonutDLogic.withAlpha(rgb, DonutDLogic.ringAlpha(spawner.inside(), fade));
                    if (ringColor >>> 24 >= 4) {
                        this.drawRing(r, class_243.method_24953(spawner.pos()), spawner.range(), ringColor);
                    }
                }
                this.drawLabel(r, spawner, ring, fade, rgb);
            }
        } catch (Throwable t) {
            this.broken = true;
            DIHClient.LOG.warn("[DIHClient] Spawner Nametags stopped after an error", t);
        }
    }

    private void drawLabel(Render3D r, Shown spawner, boolean showRange, float fade, int rgb) {
        int alpha = Math.round(fade * 255.0F);
        if (alpha < 4) {
            return;
        }
        float scale = DonutDLogic.textScale(spawner.distance());
        class_2338 pos = spawner.pos();
        double x = pos.method_10263() + 0.5;
        double z = pos.method_10260() + 0.5;
        double y = pos.method_10264() + LABEL_HEIGHT;
        boolean accent = showRange && spawner.range() > 0 && spawner.inside();

        r.text(DonutDLogic.title(spawner.mob()), new class_243(x, y + 0.25 * scale, z), DonutDLogic.withAlpha(TITLE_COLOR, alpha), scale);
        r.text(DonutDLogic.detail(spawner.distance(), spawner.range(), showRange), new class_243(x, y, z),
                DonutDLogic.withAlpha(accent ? rgb : DETAIL_COLOR, alpha), scale);
    }

    private void drawRing(Render3D r, class_243 centre, double radius, int color) {
        class_243 previous = null;
        for (int i = 0; i <= RING_SEGMENTS; i++) {
            class_243 point = centre.method_1031(DonutDLogic.ringX(i, RING_SEGMENTS, radius), 0.0, DonutDLogic.ringZ(i, RING_SEGMENTS, radius));
            if (previous != null) {
                r.line(previous, point, color, true);
            }
            previous = point;
        }
    }
}
