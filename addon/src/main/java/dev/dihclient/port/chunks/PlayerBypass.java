package dev.dihclient.port.chunks;

import dev.dihclient.module.Category;
import dev.dihclient.module.Module;
import dev.dihclient.render.Render3D;
import dev.dihclient.setting.BoolSetting;
import dev.dihclient.setting.ColorSetting;
import dev.dihclient.setting.IntSetting;
import dev.dihclient.util.Notifications;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.class_238;
import net.minecraft.class_243;
import net.minecraft.class_2561;
import net.minecraft.class_634;

/**
 * Ported from an open-source client (GPL-3.0).
 * Flags chunks where the server sent a fully dark sky-light section below Y 62: a covered, player-made space under the
 * surface. The light data comes from {@link PlayerBypassLightTracker}; this class is the settings, the alert and the drawing.
 * Replaces the old DIH module of the same name (PlayerActivity).
 */
public final class PlayerBypass extends Module {
    private static final int RENDER_DISTANCE_CHUNKS = 16;
    private static final double MARKER_Y = 64.0;

    public final BoolSetting notifications = this.bool("Notifications", "Toast when a base is detected.", true);
    public final ColorSetting chunkColor = this.color("Chunk Color", "Colour of the flagged chunks.", -55256);
    public final IntSetting opacity = this.integer("Opacity", "How solid the marker is.", 140, 10, 255);
    public final BoolSetting tracers = this.bool("Tracers", "Draws lines to flagged chunks.", false);
    public final BoolSetting sound = this.bool("Sound", "Plays a sound with the toast.", true).visibleWhen(() -> this.notifications.get());
    public final BoolSetting showCoordinates = this.bool("Show Coordinates", "Coordinates of the chunk in the toast and the disconnect message.", true)
            .visibleWhen(() -> this.notifications.get() || this.disconnect.get());
    public final BoolSetting disconnect = this.bool("Auto Disconnect", "Leaves the server as soon as a base is detected.", false);

    private final ChunkAreas chunkAreas = new ChunkAreas();
    private final LongOpenHashSet flagged = new LongOpenHashSet();

    public PlayerBypass() {
        super("Player Bypass", Category.BASEFINDING, "Flags chunks with signs of underground player bases from light data.");
        PlayerBypassLightTracker.install();
    }

    @Override
    protected void onEnable() {
        PlayerBypassLightTracker.setSpottedListener(this::onChunkSpotted);
    }

    @Override
    protected void onDisable() {
        PlayerBypassLightTracker.setSpottedListener(null);
        this.flagged.clear();
    }

    @Override
    public String getInfo() {
        return Integer.toString(this.flagged.size());
    }

    private void onChunkSpotted(int chunkX, int chunkZ) {
        int x = (chunkX << 4) + 8;
        int z = (chunkZ << 4) + 8;
        boolean showPos = this.showCoordinates.get();
        if (this.notifications.get()) {
            ChunkAlerts.warn(this.name(), "Base detected" + (showPos ? " at " + x + ", " + z : ""), this.sound.get());
        }
        if (this.disconnect.get()) {
            class_634 connection = mc.method_1562();
            if (connection != null) {
                Notifications.warn(this.name(), "Auto Disconnect triggered");
                connection.method_48296().method_10747(class_2561.method_43470("[Player Bypass] Base detected" + (showPos ? " at " + x + ", " + z : "")));
            }
        }
    }

    @Override
    public void onRender3D(Render3D r) {
        if (mc.field_1687 == null || mc.field_1724 == null) {
            return;
        }
        Long2ObjectMap<ChunkLightSnapshot> snapshots = PlayerBypassLightTracker.tracker().snapshots();
        if (snapshots.isEmpty()) {
            this.flagged.clear();
            return;
        }
        int color = this.chunkColor.get();
        float colorAlpha = (color >>> 24 & 0xFF) / 255.0F;
        int lineAlpha = Math.max(20, Math.min(255, this.opacity.get()));
        int fillAlpha = Math.max(8, Math.min(255, this.opacity.get() / 3));
        int lineColor = withAlpha(color, Math.round(lineAlpha * colorAlpha));
        int fillColor = withAlpha(color, Math.round(fillAlpha * colorAlpha));
        int centerX = mc.field_1724.method_31477() >> 4;
        int centerZ = mc.field_1724.method_31479() >> 4;
        this.flagged.clear();
        for (int cx = centerX - RENDER_DISTANCE_CHUNKS; cx <= centerX + RENDER_DISTANCE_CHUNKS; cx++) {
            for (int cz = centerZ - RENDER_DISTANCE_CHUNKS; cz <= centerZ + RENDER_DISTANCE_CHUNKS; cz++) {
                long key = ProtectedChunkStore.key(cx, cz);
                ChunkLightSnapshot snapshot = snapshots.get(key);
                if (snapshot != null && PlayerBypassLightTracker.tracker().isFlagged(key, snapshot)) {
                    double x = cx << 4;
                    double z = cz << 4;
                    class_238 slab = new class_238(x, MARKER_Y, z, x + 16.0, MARKER_Y + 1.0, z + 16.0);
                    r.boxFilled(slab, fillColor, true);
                    r.boxOutline(slab, lineColor, true);
                    this.flagged.add(key);
                }
            }
        }
        if (this.tracers.get() && !this.flagged.isEmpty()) {
            this.chunkAreas.update(this.flagged);
            int tracerColor = color | 0xFF000000;
            for (int i = 0; i < this.chunkAreas.count(); i++) {
                r.tracer(new class_243(this.chunkAreas.x(i), MARKER_Y + 0.5, this.chunkAreas.z(i)), tracerColor);
            }
        }
    }

    private static int withAlpha(int argb, int alpha) {
        return argb & 0xFFFFFF | Math.max(0, Math.min(255, alpha)) << 24;
    }
}
