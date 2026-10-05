package dev.dihclient.port.staff;

import com.mojang.authlib.GameProfile;
import dev.dihclient.DIHClient;
import dev.dihclient.gui.theme.Theme;
import dev.dihclient.hud.HudStyle;
import dev.dihclient.module.Category;
import dev.dihclient.module.Module;
import dev.dihclient.module.ModuleManager;
import dev.dihclient.modules.render.NavigationHud;
import dev.dihclient.port.PacketBus;
import dev.dihclient.render.Gfx;
import dev.dihclient.setting.BoolSetting;
import dev.dihclient.setting.DoubleSetting;
import dev.dihclient.setting.EnumSetting;
import dev.dihclient.setting.IntSetting;
import dev.dihclient.util.ColorUtil;
import dev.dihclient.util.Notifications;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Function;
import net.minecraft.class_124;
import net.minecraft.class_1109;
import net.minecraft.class_1934;
import net.minecraft.class_2561;
import net.minecraft.class_2596;
import net.minecraft.class_2703;
import net.minecraft.class_2703.class_5893;
import net.minecraft.class_2703.class_2705;
import net.minecraft.class_268;
import net.minecraft.class_310;
import net.minecraft.class_332;
import net.minecraft.class_3417;
import net.minecraft.class_634;
import net.minecraft.class_640;
import net.minecraft.class_742;
import net.minecraft.class_746;
import net.minecraft.class_7532;
import net.minecraft.class_8042;

/** Ported from an open-source client (GPL-3.0). */
public class StaffList extends Module {
    private static final int SCAN_INTERVAL_TICKS = 10;
    private static final int SETTLE_TICKS = 60;
    private static final int ALERT_GATHER_TICKS = 5;
    private static final int SWITCH_ECHO_TICKS = 20;
    private static final long SHARED_ALARM_MS = 5000L;
    private static final int MAX_ROWS = 8;
    private static final int GROUP_THRESHOLD = 3;
    private static final float ALARM_SECONDS = 3.0F;

    private static final int TITLE_DELAY_TICKS = 25;
    private static final StaffMatcher RANKS = new StaffMatcher(StaffMatcher.MODE_ALL, StaffMatcher.DEFAULT_RANK_WORDS,
            StaffMatcher.DEFAULT_MARKERS, false, StaffMatcher.DEFAULT_TEAM_RANK_MAX, String.join(",", StaffNames.DONUT_STAFF), "");
    private static final StaffMatcher.Match FALLBACK = new StaffMatcher.Match("Staff", 1000);

    public final BoolSetting regionAlert = this.bool("Region Alert",
            "Marks staff that are on your server as WATCHING YOU (game-mode switch or body nearby) and sounds an alarm.", true);
    public final EnumSetting<NavigationHud.Corner> corner = this.mode("Position", "Screen corner of the list.", NavigationHud.Corner.TOP_RIGHT);
    public final IntSetting offsetX = this.integer("Offset X", "Distance from the screen edge.", 4, 0, 600);
    public final IntSetting offsetY = this.integer("Offset Y", "Distance from the screen edge.", 40, 0, 600);
    public final DoubleSetting scale = this.dbl("Scale", "Size of the list.", 1.0, 0.5, 2.0, 0.05);

    private final Map<UUID, StaffEntry> staff = new LinkedHashMap<>();
    private List<StaffEntry> sorted = List.of();
    private boolean resort;
    private final Map<UUID, Classified> classified = new HashMap<>();
    private final Map<UUID, StaffSighting> sightings = new HashMap<>();

    private final Queue<GameModeSwitch> switches = new ConcurrentLinkedQueue<>();
    private final Map<UUID, String> pendingAlerts = new LinkedHashMap<>();
    private final StaffAlarm alarm = new StaffAlarm();
    private final ShardTracker shard = new ShardTracker();
    private final PacketBus.Netty netty = this::onPacketNetty;
    private class_634 connection;
    private int ticksToScan;
    private int settleTicks;
    private boolean primed;
    private int alertGather;
    private int tickCount;
    private int failures;

    private int titleTicks = -1;
    private boolean titleShown;
    private String titleText = "";

    public StaffList() {
        super("Staff List", Category.DONUT, "Lists online DonutSMP staff.");
        this.regionAlert.onChange(() -> {
            if (!this.regionAlert.get()) {
                this.forgetShard();
            }
        });
    }

    @Override
    protected void onEnable() {
        this.forget();
        this.connection = mc.method_1562();
        this.shard.clear();
        this.settleTicks = 0;
        this.ticksToScan = 0;
        this.primed = false;
        PacketBus.remove(this.netty);
        PacketBus.netty(this.netty);
    }

    @Override
    protected void onDisable() {
        PacketBus.remove(this.netty);
        this.forget();
        this.connection = null;
        this.shard.clear();
        this.titleTicks = -1;
    }

    @Override
    public String getInfo() {
        return this.sorted.isEmpty() ? null : String.valueOf(this.sorted.size());
    }

    private void forget() {
        this.staff.clear();
        this.sorted = List.of();
        this.classified.clear();
        this.forgetShard();
    }

    private void forgetShard() {
        this.switches.clear();
        this.sightings.clear();
        this.pendingAlerts.clear();
        this.alarm.stop();
        this.resort = true;
    }

    public static boolean isStaffName(String name) {
        return StaffNames.isStaffName(name);
    }

    public static boolean isInYourRegion(UUID id) {
        StaffList module = ModuleManager.of(StaffList.class);
        if (module == null || !module.isEnabled() || id == null) {
            return false;
        }
        StaffSighting seen = module.sightings.get(id);
        return seen != null && seen.marked(System.currentTimeMillis());
    }

    public static boolean alarmedRecently(UUID id) {
        StaffList module = ModuleManager.of(StaffList.class);
        if (module == null || !module.isEnabled() || id == null) {
            return false;
        }
        StaffSighting seen = module.sightings.get(id);
        return seen != null && seen.alertedAt != 0L && System.currentTimeMillis() - seen.alertedAt < SHARED_ALARM_MS;
    }

    private boolean onPacketNetty(class_2596<?> packet) {
        if (!this.regionAlert.get()) {
            return false;
        }
        if (packet instanceof class_2703 info) {
            this.queueSwitches(info);
        } else if (packet instanceof class_8042 bundle) {
            for (class_2596<?> inner : bundle.method_48324()) {
                if (inner instanceof class_2703 info) {
                    this.queueSwitches(info);
                }
            }
        }
        return false;
    }

    private void queueSwitches(class_2703 info) {
        EnumSet<class_5893> actions = info.method_46327();
        if (actions.contains(class_5893.field_29137) && !actions.contains(class_5893.field_29136)) {
            for (class_2705 entry : info.method_46329()) {
                if (entry != null && entry.comp_1106() != null) {
                    this.switches.offer(new GameModeSwitch(entry.comp_1106(), entry.comp_1110()));
                }
            }
        }
    }

    @Override
    public void onTick() {
        try {
            this.tick();
        } catch (Throwable t) {

            if (this.failures++ < 3) {
                DIHClient.LOG.warn("[DIHClient] Staff List tick failed", t);
            }
        }
    }

    private void tick() {
        this.alarm.tick();
        this.tickTitle();
        class_634 conn = mc.method_1562();
        if (conn == null || mc.field_1724 == null || mc.field_1687 == null) {
            if (this.connection != null || !this.staff.isEmpty() || !this.classified.isEmpty() || !this.sightings.isEmpty()) {
                this.forget();
            }
            this.switches.clear();
            this.connection = null;
            this.shard.clear();
            this.primed = false;
            return;
        }
        boolean moved = this.shard.moved(mc);
        if (conn != this.connection) {

            this.connection = conn;
            this.forget();
            this.settleTicks = SETTLE_TICKS;
            this.primed = false;
        } else if (moved) {
            this.forgetShard();
        }
        if (this.settleTicks > 0) {
            this.settleTicks--;
        }
        this.tickCount++;
        long now = System.currentTimeMillis();
        if (this.regionAlert.get()) {
            this.drainSwitches(conn, mc.field_1724.method_5667(), now);
            this.trackBodies(mc.field_1724, now);
        }
        if (--this.ticksToScan <= 0) {
            this.ticksToScan = SCAN_INTERVAL_TICKS;
            Changes changes = this.scan(conn, now);
            if (this.primed) {
                this.announce(changes);
            } else if (this.settleTicks == 0) {
                this.primed = true;
            }
        }
        if (!this.pendingAlerts.isEmpty() && --this.alertGather <= 0) {
            this.raise();
        }
        if (this.resort) {
            this.sortView(now);
        }
    }

    private Changes scan(class_634 conn, long now) {
        UUID self = mc.field_1724.method_5667();
        Set<UUID> listed = new HashSet<>();
        for (class_640 info : conn.method_45732()) {
            listed.add(info.method_2966().id());
        }
        Changes changes = new Changes();
        Set<UUID> inTab = new HashSet<>();
        for (class_640 info : conn.method_2880()) {
            GameProfile profile = info.method_2966();
            UUID id = profile.id();
            String name = profile.name();
            if (id == null || id.equals(self) || !StaffNames.isStaffName(name) || !StaffNames.isAccount(id, name)) {
                continue;
            }
            inTab.add(id);
            StaffMatcher.Match rank = this.rank(info, id, name);

            StaffEntry.Presence presence = info.method_2958() == class_1934.field_9219 ? StaffEntry.Presence.SPECTATOR
                    : (listed.contains(id) ? StaffEntry.Presence.ONLINE : StaffEntry.Presence.VANISHED);
            StaffEntry tracked = this.staff.get(id);
            if (tracked == null) {
                tracked = new StaffEntry(id);
                this.staff.put(id, tracked);
                tracked.update(name, rank, presence);
                changes.joined.add(tracked);
            } else {
                StaffEntry.Presence before = tracked.presence;
                tracked.update(name, rank, presence);
                tracked.offTabScans = 0;
                if (before != presence) {
                    changes.changed.add(tracked);
                }
            }
        }
        this.classified.keySet().retainAll(inTab);
        Iterator<StaffEntry> it = this.staff.values().iterator();
        while (it.hasNext()) {
            StaffEntry tracked = it.next();
            if (inTab.contains(tracked.id)) {
                continue;
            }
            if (mc.field_1687.method_18470(tracked.id) != null) {

                if (++tracked.offTabScans >= 2 && tracked.presence != StaffEntry.Presence.VANISHED) {
                    tracked.presence = StaffEntry.Presence.VANISHED;
                    changes.changed.add(tracked);
                }
            } else {
                it.remove();
                changes.left.add(tracked);
            }
        }
        this.pruneSightings(now);
        this.sortView(now);
        return changes;
    }

    private void sortView(long now) {
        List<StaffEntry> view = new ArrayList<>(this.staff.values());
        view.sort(StaffEntry.order(this.sightings::get, now));
        this.sorted = List.copyOf(view);
        this.resort = false;
    }

    private StaffMatcher.Match rank(class_640 info, UUID id, String name) {
        class_268 team = info.method_2955();
        class_2561 display = info.method_2971();
        class_2561 prefix = team == null ? null : team.method_1144();
        class_2561 suffix = team == null ? null : team.method_1136();
        Classified last = this.classified.get(id);
        if (last != null && last.isFor(name, display, team, prefix, suffix)) {
            return last.match();
        }
        StaffMatcher.Match match = RANKS.match(name, team == null ? null : team.method_1197(), plain(display), plain(prefix), plain(suffix));
        if (match == null) {
            match = FALLBACK;
        }
        this.classified.put(id, new Classified(name, display, team, prefix, suffix, match));
        return match;
    }

    private static String plain(class_2561 component) {
        return component == null ? null : component.getString();
    }

    private void drainSwitches(class_634 conn, UUID self, long now) {
        GameModeSwitch change;
        while ((change = this.switches.poll()) != null) {
            UUID id = change.id();
            if (id.equals(self)) {
                continue;
            }
            StaffEntry tracked = this.staff.get(id);
            String name;
            if (tracked != null) {
                name = tracked.name;
            } else {
                class_640 info = conn.method_2871(id);
                name = info == null ? null : info.method_2966().name();
                if (!StaffNames.isStaffName(name) || !StaffNames.isAccount(id, name)) {
                    continue;
                }
            }
            StaffSighting seen = this.sightings.computeIfAbsent(id, key -> new StaffSighting());
            if (!seen.marked(now)) {
                this.resort = true;
            }
            seen.provenAt = now;
            if (!seen.coolingDown(now)) {
                seen.switchAlertTick = this.tickCount;
                seen.switchedToSpectator = change.mode() == class_1934.field_9219;
                this.alert(id, seen, now, name);
            }
        }
    }

    private void trackBodies(class_746 self, long now) {
        for (class_742 body : mc.field_1687.method_18456()) {
            if (body == self) {
                continue;
            }
            StaffEntry tracked = this.staff.get(body.method_5667());
            if (tracked == null) {
                continue;
            }
            StaffSighting seen = this.sightings.computeIfAbsent(tracked.id, key -> new StaffSighting());
            seen.provenAt = now;
            seen.bodyTick = this.tickCount;
            seen.distance = self.method_5739(body);
            if (!seen.nearby) {
                seen.nearby = true;
                this.resort = true;
                boolean newAppearance = seen.bodyLostAt == 0L || now - seen.bodyLostAt > StaffSighting.NEARBY_GRACE_MS;
                if (newAppearance) {
                    this.alert(tracked.id, seen, now, tracked.name);
                }
            }
        }
        for (StaffSighting seen : this.sightings.values()) {
            if (seen.nearby && seen.bodyTick != this.tickCount) {
                seen.nearby = false;
                seen.bodyLostAt = now;
                this.resort = true;
            }
        }
    }

    private void pruneSightings(long now) {
        Iterator<Map.Entry<UUID, StaffSighting>> it = this.sightings.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, StaffSighting> entry = it.next();
            StaffSighting seen = entry.getValue();
            if (this.staff.containsKey(entry.getKey())) {
                seen.untrackedSince = 0L;
            } else if (seen.untrackedSince == 0L) {
                seen.untrackedSince = now;
            } else if (now - seen.untrackedSince > StaffSighting.NEARBY_GRACE_MS) {
                it.remove();
                this.resort = true;
            }
        }
    }

    private void alert(UUID id, StaffSighting seen, long now, String name) {
        seen.alertedAt = now;
        seen.highlightUntil = now + StaffSighting.HIGHLIGHT_MS;
        if (this.pendingAlerts.isEmpty()) {

            this.alertGather = ALERT_GATHER_TICKS;
        }
        this.pendingAlerts.put(id, name);
    }

    private void raise() {
        Map.Entry<UUID, String> first = this.pendingAlerts.entrySet().iterator().next();
        String name = first.getValue();
        int others = this.pendingAlerts.size() - 1;
        this.pendingAlerts.clear();
        this.alarm.start(AlarmPattern.GUARDIAN, ALARM_SECONDS, 1.0F);
        if (name != null) {
            this.titleText = others > 0 ? name + " +" + others : name;
            this.titleTicks = 0;
            this.titleShown = false;
        }
    }

    private void tickTitle() {
        if (this.titleTicks < 0) {
            return;
        }
        if (mc.field_1687 == null || mc.field_1705 == null) {
            this.titleTicks = -1;
            return;
        }
        this.titleTicks++;
        if (!this.titleShown && this.titleTicks >= TITLE_DELAY_TICKS) {
            this.titleShown = true;
            mc.field_1705.method_34001(10, 40, 15);
            mc.field_1705.method_34002(class_2561.method_43470("WATCHING YOU").method_27695(class_124.field_1061, class_124.field_1067));
            mc.field_1705.method_34004(class_2561.method_43470(this.titleText).method_27695(class_124.field_1061, class_124.field_1067));
        } else if (this.titleShown && this.titleTicks >= TITLE_DELAY_TICKS + 65) {
            mc.field_1705.method_1742();
            this.titleTicks = -1;
        }
    }

    private void announce(Changes changes) {
        changes.changed.removeIf(this::echoesSwitchAlert);
        if (changes.isEmpty()) {
            return;
        }
        List<String> lines = new ArrayList<>();
        describe(lines, changes.joined, "\u00a76joined", s -> {
            String note = note(s);
            return "\u00a76joined" + (note != null ? " \u00a7c" + note : "");
        });
        describe(lines, changes.changed, "\u00a76changed visibility", s -> switch (s.presence) {
            case ONLINE -> "is \u00a7avisible again";
            case SPECTATOR -> "switched to \u00a7cspectator";
            case VANISHED -> "\u00a7cvanished\u00a77" + (s.offTabScans > 0 ? " (off TAB, still nearby)" : " (hidden from TAB)");
        });
        describe(lines, changes.left, "\u00a7aleft", s -> s.presence == StaffEntry.Presence.VANISHED ? "is \u00a76out of sight" : "\u00a7aleft");
        for (String line : lines) {
            Notifications.chat("\u00a7fStaff List \u00a78: \u00a77" + line);
        }
        boolean urgent = !changes.joined.isEmpty() || changes.changed.stream().anyMatch(s -> s.presence != StaffEntry.Presence.ONLINE);
        if (urgent && mc.method_1483() != null) {
            mc.method_1483().method_4873(class_1109.method_47978(class_3417.field_14725, 1.25F));
        }
    }

    private boolean echoesSwitchAlert(StaffEntry s) {
        StaffSighting seen = this.sightings.get(s.id);
        if (seen == null || seen.switchAlertTick < 0 || this.tickCount - seen.switchAlertTick > SWITCH_ECHO_TICKS) {
            return false;
        }
        return s.presence == StaffEntry.Presence.SPECTATOR ? seen.switchedToSpectator
                : s.presence == StaffEntry.Presence.ONLINE && !seen.switchedToSpectator;
    }

    private static void describe(List<String> lines, List<StaffEntry> group, String groupVerb, Function<StaffEntry, String> verb) {
        if (group.isEmpty()) {
            return;
        }
        if (group.size() > GROUP_THRESHOLD) {
            StringBuilder out = new StringBuilder("\u00a7b").append(group.size()).append("\u00a77 staff ").append(groupVerb).append("\u00a77: ");
            for (int i = 0; i < group.size(); i++) {
                if (i > 0) {
                    out.append("\u00a77, ");
                }
                out.append(label(group.get(i)));
            }
            lines.add(out.toString());
        } else {
            for (StaffEntry s : group) {
                lines.add(label(s) + " \u00a77" + verb.apply(s));
            }
        }
    }

    private static String label(StaffEntry s) {
        return "\u00a7f" + s.name + "\u00a77 (" + s.rank + ")";
    }

    private static String note(StaffEntry s) {
        return switch (s.presence) {
            case ONLINE -> null;
            case SPECTATOR -> "spectating";
            case VANISHED -> "vanished";
        };
    }

    @Override
    public void onRender2D(class_332 g, float partialTicks) {
        if (this.sorted.isEmpty() || mc.field_1724 == null || mc.field_1690.field_1842) {
            return;
        }
        try {
            this.draw(g);
        } catch (Throwable t) {
            if (this.failures++ < 3) {
                DIHClient.LOG.warn("[DIHClient] Staff List draw failed", t);
            }
        }
    }

    private void draw(class_332 g) {
        List<StaffEntry> all = this.sorted;
        int shown = all.size() <= MAX_ROWS ? all.size() : MAX_ROWS - 1;
        int more = all.size() - shown;
        long now = System.currentTimeMillis();
        class_634 conn = mc.method_1562();

        final int face = 10;
        final int rowH = 12;
        final int headerH = 14;
        final int pad = 5;
        String header = "STAFF";
        String count = Integer.toString(all.size());

        String[] status = new String[shown];
        String[] tag = new String[shown];
        String[] value = new String[shown];
        StaffSighting[] sight = new StaffSighting[shown];
        int nameCol = 0;
        int rightCol = 0;
        for (int i = 0; i < shown; i++) {
            StaffEntry s = all.get(i);
            StaffSighting seen = this.sightings.get(s.id);
            sight[i] = seen;
            status[i] = switch (s.presence) {
                case VANISHED -> "VANISHED";
                case SPECTATOR -> "SPECTATOR";
                case ONLINE -> "";
            };
            int nameW = Gfx.width(s.name) + (status[i].isEmpty() ? 0 : 6 + Gfx.width(status[i]));
            nameCol = Math.max(nameCol, nameW);
            if (seen != null && seen.marked(now)) {
                tag[i] = "WATCHING YOU";
                value[i] = seen.nearby ? Math.round(seen.distance) + "m" : StaffNames.formatAge(now - seen.provenAt);
                rightCol = Math.max(rightCol, Gfx.width(tag[i]) + 4 + Gfx.width(value[i]));
            }
        }
        String moreText = more > 0 ? "+" + more + " more" : null;
        int headerW = Gfx.width(header) + 5 + Gfx.width(count);
        int rowW = face + 4 + nameCol + (rightCol > 0 ? 10 + rightCol : 0);
        int w = pad * 2 + Math.max(headerW, Math.max(rowW, moreText == null ? 0 : Gfx.width(moreText)));
        int h = headerH + rowH * shown + (moreText == null ? 0 : rowH) + 2;

        float sc = this.scale.getFloat();
        int sw = Math.round(w * sc);
        int sh = Math.round(h * sc);
        int screenW = g.method_51421();
        int screenH = g.method_51443();
        NavigationHud.Corner c = this.corner.get();
        int x = switch (c) {
            case TOP_LEFT, BOTTOM_LEFT -> this.offsetX.get();
            case TOP_RIGHT, BOTTOM_RIGHT -> screenW - sw - this.offsetX.get();
            case TOP_CENTER -> (screenW - sw) / 2 + this.offsetX.get();
        };
        int y = c == NavigationHud.Corner.BOTTOM_LEFT || c == NavigationHud.Corner.BOTTOM_RIGHT ? screenH - sh - this.offsetY.get() : this.offsetY.get();
        x = Math.max(0, Math.min(screenW - sw, x));
        y = Math.max(0, Math.min(screenH - sh, y));

        g.method_51448().pushMatrix();
        g.method_51448().translate(x, y);
        g.method_51448().scale(sc, sc);
        try {
            HudStyle.accentPanel(g, 0, 0, w, h);
            Gfx.text(g, header, pad, 3, Gfx.MUTED);
            Gfx.text(g, count, pad + Gfx.width(header) + 5, 3, Theme.accent());
            for (int i = 0; i < shown; i++) {
                StaffEntry s = all.get(i);
                int ry = headerH + rowH * i;
                StaffSighting seen = sight[i];
                if (tag[i] != null) {

                    float level = seen == null ? 0.0F : seen.alertLevel(now);
                    float wave = 0.5F + 0.5F * (float) Math.cos((1.0F - level) * StaffSighting.HIGHLIGHT_MS * 2.0 * Math.PI / 500.0);
                    float glow = 0.14F + 0.24F * level * wave;
                    Gfx.rect(g, 2, ry, w - 4, rowH, 2, ColorUtil.withAlpha(Gfx.ORANGE, Math.round(glow * 255.0F)));
                }
                class_640 info = conn == null ? null : conn.method_2871(s.id);
                if (info != null) {
                    class_7532.method_52722(g, info.method_52810(), pad, ry + 1, face);
                }
                int tx = pad + face + 4;
                Gfx.text(g, s.name, tx, ry + 2, Gfx.TEXT);
                if (!status[i].isEmpty()) {
                    int sx = tx + Gfx.width(s.name) + 6;
                    Gfx.text(g, status[i], sx, ry + 2, s.presence == StaffEntry.Presence.VANISHED ? Gfx.RED : Gfx.ORANGE);
                }
                if (tag[i] != null) {
                    int vx = w - pad - Gfx.width(value[i]);
                    Gfx.text(g, value[i], vx, ry + 2, Gfx.TEXT);
                    Gfx.text(g, tag[i], vx - 4 - Gfx.width(tag[i]), ry + 2, Gfx.ORANGE);
                }
            }
            if (moreText != null) {
                Gfx.text(g, moreText, pad, headerH + rowH * shown + 2, Gfx.MUTED);
            }
        } finally {
            g.method_51448().popMatrix();
        }
    }

    private static final class Changes {
        final List<StaffEntry> joined = new ArrayList<>();
        final List<StaffEntry> changed = new ArrayList<>();
        final List<StaffEntry> left = new ArrayList<>();

        boolean isEmpty() {
            return this.joined.isEmpty() && this.changed.isEmpty() && this.left.isEmpty();
        }
    }

    private record Classified(String name, class_2561 display, class_268 team, class_2561 prefix, class_2561 suffix, StaffMatcher.Match match) {
        boolean isFor(String name, class_2561 display, class_268 team, class_2561 prefix, class_2561 suffix) {
            return this.display == display && this.team == team && this.prefix == prefix && this.suffix == suffix && this.name.equals(name);
        }
    }

    private record GameModeSwitch(UUID id, class_1934 mode) {
    }
}
