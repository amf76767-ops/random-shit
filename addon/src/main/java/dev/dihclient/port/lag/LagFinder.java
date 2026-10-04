package dev.dihclient.port.lag;

import dev.dihclient.module.Category;
import dev.dihclient.module.Module;
import dev.dihclient.port.PacketBus;
import dev.dihclient.render.Render3D;
import dev.dihclient.setting.BoolSetting;
import dev.dihclient.setting.ColorSetting;
import dev.dihclient.setting.DoubleSetting;
import dev.dihclient.setting.IntSetting;
import dev.dihclient.util.Notifications;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import net.minecraft.class_1297;
import net.minecraft.class_238;
import net.minecraft.class_243;
import net.minecraft.class_2596;
import net.minecraft.class_2761;
import net.minecraft.class_642;
import net.minecraft.class_746;

/**
 * Finds places where the server lags without anything happening there. The server sends its game time once a second; the game time
 * that passed divided by the real time that passed is the TPS. The TPS is noted at the place you stand (see {@link LagMap}). A server
 * only ticks what is near a player, so a big hidden base or farm under the ground slows the server down while you are near it and not
 * when you are away: neighbouring cells with a clearly lower TPS than usual become a lag zone. Samples taken in a crowd (many entities
 * around you), right after a teleport or while flying fast are dropped, because there the lag has an obvious reason.
 */
public class LagFinder extends Module {
    public final DoubleSetting threshold = this.dbl("Lag Threshold", "How many TPS below the usual TPS a place has to average to count as laggy.", 3.0, 0.5, 10.0, 0.5);
    public final IntSetting minSamples = this.integer("Min Samples", "Seconds spent in an area before it is judged (more = fewer false alarms).", 5, 2, 30);
    public final IntSetting crowd = this.integer("Crowd Limit", "Ignores samples with more entities than this within 48 blocks (they explain the lag). 0 = never ignore.", 25, 0, 200);
    public final BoolSetting fastIgnore = this.bool("Ignore Fast Travel", "Ignores samples while flying faster than 12 blocks per second (loading chunks causes lag).", true);
    public final BoolSetting notify = this.bool("Notify", "Toast when a new lag zone is found.", true);
    public final BoolSetting markers = this.bool("Markers", "Draws a column and a label at each lag zone.", true);
    public final BoolSetting tracers = this.bool("Tracers", "Draws a line to each lag zone.", false);
    public final ColorSetting color = this.color("Color", "Colour of the markers.", 0xFFFF5A5A);

    private final Map<String, LagMap> maps = new HashMap<>();
    private final Set<Long> told = new HashSet<>();
    private final Queue<Double> incoming = new ConcurrentLinkedQueue<>();
    private final PacketBus.Netty listener = this::onPacket;
    private volatile long lastNanos;
    private volatile long lastGame = -1;
    private String mapKey = "";
    private List<LagMap.Zone> zones = List.of();
    private double lastX;
    private double lastZ;
    private boolean haveLast;
    private int quiet;
    private int ticks;
    private double lastTps = 20.0;

    public LagFinder() {
        super("Lag Finder", Category.BASEFINDING, "Finds places where the server lags without anything happening there: a big hidden base or farm underground.");
        this.action("Show Zones", "Lists the lag zones found so far.", this::listZones);
        this.action("Clear", "Forgets everything measured so far.", this::clearAll);
    }

    @Override
    protected void onEnable() {
        this.lastGame = -1;
        this.incoming.clear();
        PacketBus.remove(this.listener);
        PacketBus.netty(this.listener);
    }

    @Override
    protected void onDisable() {
        PacketBus.remove(this.listener);
        this.incoming.clear();
    }

    @Override
    public void onWorldChange() {
        this.lastGame = -1;
        this.incoming.clear();
        this.haveLast = false;
        this.quiet = 100;
    }

    @Override
    public String getInfo() {
        return String.format(Locale.ROOT, "%.1f TPS", this.lastTps);
    }

    private void clearAll() {
        this.maps.clear();
        this.told.clear();
        this.zones = List.of();
        Notifications.info(this.name(), "Cleared");
    }

    private void listZones() {
        if (this.zones.isEmpty()) {
            Notifications.info(this.name(), "No lag zone found yet. Walk around; every area needs a few seconds.");
            return;
        }
        for (int i = 0; i < Math.min(5, this.zones.size()); i++) {
            Notifications.info(this.name(), describe(this.zones.get(i)));
        }
    }

    private static String describe(LagMap.Zone z) {
        return String.format(Locale.ROOT, "X %d, Z %d (about %d blocks wide): %.1f TPS", (int) z.x(), (int) z.z(), (int) (z.radius() * 2), z.avgTps());
    }

    /** Network thread: the game time of the packet divided by the time since the last one is the TPS. */
    private boolean onPacket(class_2596<?> packet) {
        if (packet instanceof class_2761 time) {
            long now = System.nanoTime();
            long game = time.comp_3219();
            long before = this.lastGame;
            long beforeNanos = this.lastNanos;
            this.lastGame = game;
            this.lastNanos = now;
            if (before >= 0 && game > before) {
                double seconds = (now - beforeNanos) / 1.0E9;
                if (seconds > 0.4 && seconds < 30.0 && this.incoming.size() < 64) {
                    this.incoming.add((game - before) / seconds);
                }
            }
        }
        return false;
    }

    private LagMap map() {
        return this.maps.computeIfAbsent(this.mapKey, k -> new LagMap());
    }

    private String currentKey() {
        class_642 server = mc.method_1558();
        String where = server == null ? "single" : server.field_3761;
        String dimension = mc.field_1687 == null ? "" : mc.field_1687.method_27983().method_29177().toString();
        return where + "|" + dimension;
    }

    @Override
    public void onTick() {
        this.ticks++;
        class_746 player = mc.field_1724;
        if (player == null || mc.field_1687 == null) {
            this.incoming.clear();
            return;
        }
        String key = this.currentKey();
        if (!key.equals(this.mapKey)) {
            this.mapKey = key;
            this.zones = List.of();
            this.haveLast = false;
            this.quiet = 100;
            this.incoming.clear();
        }
        double x = player.method_23317();
        double z = player.method_23321();
        if (this.haveLast && Math.hypot(x - this.lastX, z - this.lastZ) > 24.0) {
            this.quiet = 100; // teleported: the server is busy with the new chunks
        }
        double speed = this.haveLast ? Math.hypot(x - this.lastX, z - this.lastZ) * 20.0 : 0.0;
        this.lastX = x;
        this.lastZ = z;
        this.haveLast = true;
        if (this.quiet > 0) {
            this.quiet--;
        }
        Double tps;
        while ((tps = this.incoming.poll()) != null) {
            this.lastTps = Math.min(20.0, tps);
            if (this.quiet > 0 || this.fastIgnore.get() && speed > 12.0 || this.crowded(player)) {
                continue;
            }
            this.map().add(x, z, tps);
        }
        if (this.ticks % 40 == 0) {
            this.zones = this.map().zones(this.threshold.get(), this.minSamples.get());
            for (LagMap.Zone zone : this.zones) {
                if (this.told.add(zone.id()) && this.notify.get()) {
                    Notifications.warn(this.name(), "Lag zone at " + describe(zone));
                }
            }
        }
    }

    private boolean crowded(class_746 player) {
        int limit = this.crowd.get();
        if (limit <= 0) {
            return false;
        }
        int count = 0;
        for (class_1297 e : mc.field_1687.method_18112()) {
            if (e != player && e.method_5739(player) < 48.0F && ++count > limit) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void onRender3D(Render3D r) {
        class_746 player = mc.field_1724;
        if (player == null || this.zones.isEmpty() || !this.markers.get() && !this.tracers.get()) {
            return;
        }
        int color = this.color.get() | 0xFF000000;
        double py = player.method_23318();
        for (LagMap.Zone z : this.zones) {
            double d = Math.hypot(z.x() - player.method_23317(), z.z() - player.method_23321());
            if (this.markers.get()) {
                r.boxOutline(new class_238(z.x() - 6, py - 40, z.z() - 6, z.x() + 6, py + 40, z.z() + 6), color, true);
                r.text(String.format(Locale.ROOT, "Lag zone · %.1f TPS · %.0fm", z.avgTps(), d), new class_243(z.x(), py + 3, z.z()), color, 1.6F);
            }
            if (this.tracers.get()) {
                r.tracer(new class_243(z.x(), py, z.z()), color);
            }
        }
    }
}
