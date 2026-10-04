package dev.dihclient.port.vanish;

import dev.dihclient.DIHClient;
import dev.dihclient.module.Category;
import dev.dihclient.module.Module;
import dev.dihclient.module.ModuleManager;
import dev.dihclient.modules.misc.Watchlist;
import dev.dihclient.port.PacketBus;
import dev.dihclient.setting.BoolSetting;
import dev.dihclient.setting.EnumSetting;
import dev.dihclient.setting.StringSetting;
import dev.dihclient.util.Notifications;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.class_1109;
import net.minecraft.class_1299;
import net.minecraft.class_1297;
import net.minecraft.class_1657;
import net.minecraft.class_1676;
import net.minecraft.class_2338;
import net.minecraft.class_2561;
import net.minecraft.class_2588;
import net.minecraft.class_2596;
import net.minecraft.class_2639;
import net.minecraft.class_2664;
import net.minecraft.class_2675;
import net.minecraft.class_2703;
import net.minecraft.class_2739;
import net.minecraft.class_2765;
import net.minecraft.class_2767;
import net.minecraft.class_2805;
import net.minecraft.class_2824;
import net.minecraft.class_2846;
import net.minecraft.class_2879;
import net.minecraft.class_2885;
import net.minecraft.class_2886;
import net.minecraft.class_2680;
import net.minecraft.class_2741;
import net.minecraft.class_2960;
import net.minecraft.class_3417;
import net.minecraft.class_3965;
import net.minecraft.class_634;
import net.minecraft.class_640;
import net.minecraft.class_7439;
import net.minecraft.class_7828;
import net.minecraft.class_7923;

/**
 * Ported from Anubis Client 0.9.8 (GPL-3.0).
 * Detects vanished players: a tab list entry that disappears or is hidden without a leave message, a switch to spectator,
 * a name the server still completes for /msg although it is not in the tab list, plus sensors (invisible player entities,
 * chest/door/lever sounds and combat particles without a visible cause). The decisions live in {@link VanishEngine};
 * this class only reads packets and the game and reports through the DIH notifications.
 * <p>
 * Differences to Anubis: the Donut staff list is gone, so "who counts" is a setting (everyone, or a list of names; the
 * Watchlist Alarm names and your enemies are included), friends can be ignored, and the alert goes to the DIH
 * notifications instead of the Anubis chat and ghost overlay.
 */
public class AntiVanish extends Module {
    public enum Targets { EVERYONE, LISTED }

    public final EnumSetting<Targets> targets = this.mode("Targets",
            "Everyone: every player in the tab list · Listed: only the names below, the Watchlist Alarm names and your enemies.", Targets.EVERYONE);
    public final StringSetting names = this.text("Names", "Comma separated player names (staff, admins ...) to watch in Listed mode.", "", 512)
            .visibleWhen(() -> this.targets.get() == Targets.LISTED);
    public final BoolSetting ignoreFriends = this.bool("Ignore Friends", "Never report players on your friends list.", true);
    public final BoolSetting probe = this.bool("Completion Probe",
            "Every 5 s asks the server to complete /msg and reports names it offers that are not in the tab list (sends a command suggestion request).", true);
    public final BoolSetting sound = this.bool("Chime", "Plays a sound when somebody is found to be watching you.", true);
    public final BoolSetting bigAlert = this.bool("Big Alert", "Large warning text in the middle of the screen when somebody is watching you.", true);

    private final PacketBus.Netty netty = this::onNetty;
    private final VanishContext context = new VanishContext();
    private final VanishEngine engine = new VanishEngine(new GameEnv(), new Reporter());
    private volatile Set<String> listedNames = Set.of();
    private String lastNames = "";
    private String lastWatch = "";

    public AntiVanish() {
        super("Anti Vanish", Category.DONUT, "Detects vanished players.");
    }

    @Override
    protected void onEnable() {
        this.engine.reset();
        this.context.clear();
        refreshListed();
        PacketBus.netty(this.netty);
        if (mc.field_1724 != null && mc.field_1687 != null) {
            try {
                this.engine.primeFromTab();
            } catch (Throwable t) {
                DIHClient.LOG.warn("[DIHClient] Anti Vanish could not read the tab list", t);
            }
        }
    }

    @Override
    protected void onDisable() {
        PacketBus.remove(this.netty);
        this.engine.reset();
        this.context.clear();
    }

    @Override
    public void onWorldChange() {
        this.engine.reset();
        this.context.clear();
    }

    @Override
    public void onTick() {
        class_634 handler = mc.method_1562();
        if (mc.field_1724 == null || mc.field_1687 == null || handler == null) {
            this.engine.reset();
            this.context.clear();
            return;
        }
        try {
            if (this.context.moved(mc.field_1687, mc.field_1724, p -> ((class_1657) p).method_29504(), handler.method_52790())) {
                this.engine.reset();
            }
            this.engine.probeEnabled = this.probe.get();
            refreshListed();
            this.engine.tick();
        } catch (Throwable t) {
            DIHClient.LOG.warn("[DIHClient] Anti Vanish tick failed", t);
        }
    }

    @Override
    public boolean onPacketSend(class_2596<?> packet) {
        try {
            if (packet instanceof class_2885 useOn) {
                this.engine.localAction();
                class_3965 hit = useOn.method_12543();
                if (hit != null && hit.method_17777() != null) {
                    class_2338 pos = hit.method_17777();
                    class_2338 next = pos.method_10093(hit.method_17780());
                    String itemPath = "";
                    if (mc.field_1724 != null && useOn.method_12546() != null) {
                        class_2960 itemId = class_7923.field_41178.method_10221(mc.field_1724.method_5998(useOn.method_12546()).method_7909());
                        itemPath = VanishHeuristics.path(itemId == null ? "" : itemId.toString());
                    }
                    this.engine.selfInteract(pos.method_10263(), pos.method_10264(), pos.method_10260(),
                            next.method_10263(), next.method_10264(), next.method_10260(), itemPath);
                }
            } else if (packet instanceof class_2846 || packet instanceof class_2886 || packet instanceof class_2824 || packet instanceof class_2879) {
                this.engine.localAction();
            }
        } catch (Throwable t) {
            DIHClient.LOG.warn("[DIHClient] Anti Vanish outbound packet failed", t);
        }
        return false;
    }

    @Override
    public String getInfo() {
        List<VanishEngine.HudEntry> rows = this.engine.hudEntries();
        return rows.isEmpty() ? null : rows.get(0).value();
    }

    @Override
    public List<String> details() {
        List<String> lines = new ArrayList<>();
        for (VanishEngine.HudEntry row : this.engine.hudEntries()) {
            lines.add("[" + row.tag() + "] " + row.value());
        }
        return lines;
    }

    // ---------------------------------------------------------------- who counts

    private void refreshListed() {
        String own = this.names.get();
        Watchlist watch = ModuleManager.of(Watchlist.class);
        String other = watch == null ? "" : watch.names.get();
        if (!own.equals(this.lastNames) || !other.equals(this.lastWatch)) {
            this.lastNames = own;
            this.lastWatch = other;
            Set<String> out = new HashSet<>();
            for (String list : new String[] {own, other}) {
                for (String n : list.split("[,; ]+")) {
                    if (!n.isBlank()) {
                        out.add(n.trim().toLowerCase(Locale.ROOT));
                    }
                }
            }
            this.listedNames = out;
        }
    }

    private boolean isTarget(String name) {
        if (name == null) {
            return false;
        }
        try {
            if (this.ignoreFriends.get() && DIHClient.social().isFriend(name)) {
                return false;
            }
            if (this.targets.get() == Targets.EVERYONE) {
                return true;
            }
            return this.listedNames.contains(name.toLowerCase(Locale.ROOT)) || DIHClient.social().isEnemy(name);
        } catch (Throwable t) {
            return false;
        }
    }

    // ---------------------------------------------------------------- packets (network thread)

    private boolean onNetty(class_2596<?> packet) {
        if (packet instanceof class_7828 remove) {
            this.engine.offerTabRemove(remove.comp_1105());
        } else if (packet instanceof class_2703 info) {
            if (info.method_46327().contains(class_2703.class_5893.field_40700)) {
                Map<UUID, Boolean> listed = new LinkedHashMap<>();
                for (class_2703.class_2705 entry : info.method_46329()) {
                    if (entry != null) {
                        listed.put(entry.comp_1106(), entry.comp_1108());
                    }
                }
                this.engine.offerTabListed(listed);
            } else if (info.method_46327().contains(class_2703.class_5893.field_29137) && !info.method_46327().contains(class_2703.class_5893.field_29136)) {
                Map<UUID, String> modes = new LinkedHashMap<>();
                for (class_2703.class_2705 entry : info.method_46329()) {
                    if (entry != null && entry.comp_1110() != null && entry.comp_1106() != null) {
                        modes.put(entry.comp_1106(), entry.comp_1110().method_8381());
                    }
                }
                this.engine.offerGamemodes(modes);
            }
        } else if (packet instanceof class_7439 chat) {
            class_2561 content = chat.comp_763();
            this.engine.offerChat(content == null ? "" : content.getString(), departedPlayerName(content));
        } else if (packet instanceof class_2639 suggestions) {
            List<String> texts = new ArrayList<>();
            for (class_2639.class_9177 s : suggestions.comp_2265()) {
                texts.add(s.comp_2266());
            }
            return this.engine.offerCompletion(suggestions.comp_2262(), texts);
        } else if (packet instanceof class_2739 metadata) {
            this.engine.offer(VanishEngine.Observation.entityMetadata(metadata.comp_1127()));
        } else if (packet instanceof class_2664 explosion) {
            this.engine.offer(VanishEngine.Observation.explosion(explosion.comp_2883().field_1352, explosion.comp_2883().field_1351,
                    explosion.comp_2883().field_1350, explosion.comp_4594()));
        } else if (packet instanceof class_2765 sound) {
            this.engine.offer(VanishEngine.Observation.entitySound(sound.method_11883(), soundId(sound.method_11882().comp_349().comp_3319())));
        } else if (packet instanceof class_2675 particles) {
            class_2960 id = class_7923.field_41180.method_10221(particles.method_11551().method_10295());
            this.engine.offer(VanishEngine.Observation.position(VanishEngine.ObservationType.PARTICLE,
                    particles.method_11544(), particles.method_11547(), particles.method_11546(), id == null ? "" : id.toString()));
        } else if (packet instanceof class_2767 sound) {
            if (sound.method_11894() != null && sound.method_11894().comp_349() != null) {
                this.engine.offer(VanishEngine.Observation.position(VanishEngine.ObservationType.POSITIONAL_SOUND,
                        sound.method_11890(), sound.method_11889(), sound.method_11893(), soundId(sound.method_11894().comp_349().comp_3319())));
            }
        }
        return false;
    }

    private static String soundId(class_2960 id) {
        return id == null ? "" : id.toString();
    }

    /** Argument of the vanilla "multiplayer.player.left" translation, or "". */
    private static String departedPlayerName(class_2561 component) {
        if (component != null && component.method_10851() instanceof class_2588 translated && "multiplayer.player.left".equals(translated.method_11022())) {
            Object[] args = translated.method_11023();
            if (args.length != 0 && args[0] != null) {
                return args[0] instanceof class_2561 name ? name.getString() : String.valueOf(args[0]);
            }
        }
        return "";
    }

    // ---------------------------------------------------------------- reporting

    private final class Reporter implements VanishEngine.Sink {
        @Override
        public void announce(String reason, String subject, boolean named) {
            Notifications.warn("Anti Vanish", subject == null ? reason : reason + " (" + subject + ")");
        }

        @Override
        public void news(String name, String what, String detail) {
            String text = name + " " + what + (detail == null || detail.isEmpty() ? "" : " (" + detail + ")");
            if (what.startsWith("is visible")) {
                Notifications.info("Anti Vanish", text);
            } else {
                Notifications.warn("Anti Vanish", text);
            }
        }

        @Override
        public void watching(UUID id, String name) {
            String shown = name == null ? "Someone" : name;
            Notifications.warn("Anti Vanish", shown + " is watching you");
            if (AntiVanish.this.bigAlert.get()) {
                Notifications.alert(shown + " is watching you", 100);
            }
            if (AntiVanish.this.sound.get() && mc.method_1483() != null) {
                mc.method_1483().method_4873(class_1109.method_4757(class_3417.field_15203, 1.0F, 1.0F));
            }
        }
    }

    // ---------------------------------------------------------------- the game as the engine sees it (game thread)

    private final class GameEnv implements VanishEngine.Env {
        @Override
        public long now() {
            return System.currentTimeMillis();
        }

        @Override
        public UUID selfId() {
            return mc.field_1724 == null ? null : mc.field_1724.method_5667();
        }

        @Override
        public String selfName() {
            return mc.field_1724 == null ? "" : mc.field_1724.method_5477().getString();
        }

        @Override
        public double[] selfPos() {
            var p = mc.field_1724.method_73189();
            return new double[] {p.field_1352, p.field_1351, p.field_1350};
        }

        @Override
        public boolean containerOpen() {
            return mc.field_1724 != null && mc.field_1724.field_7512 != mc.field_1724.field_7498;
        }

        @Override
        public String tabName(UUID id) {
            class_634 handler = mc.method_1562();
            class_640 info = handler == null ? null : handler.method_2871(id);
            return info == null || info.method_2966() == null ? null : info.method_2966().name();
        }

        @Override
        public boolean inTab(UUID id) {
            class_634 handler = mc.method_1562();
            return handler != null && handler.method_2871(id) != null;
        }

        @Override
        public Collection<VanishEngine.TabEntry> listedEntries() {
            List<VanishEngine.TabEntry> out = new ArrayList<>();
            class_634 handler = mc.method_1562();
            if (handler != null) {
                for (class_640 info : handler.method_45732()) {
                    if (info != null && info.method_2966() != null && info.method_2966().id() != null) {
                        out.add(new VanishEngine.TabEntry(info.method_2966().id(), info.method_2966().name()));
                    }
                }
            }
            return out;
        }

        @Override
        public Set<UUID> connectedIds() {
            Set<UUID> out = new HashSet<>();
            class_634 handler = mc.method_1562();
            if (handler != null) {
                for (class_640 info : handler.method_2880()) {
                    if (info != null && info.method_2966() != null) {
                        out.add(info.method_2966().id());
                    }
                }
            }
            return out;
        }

        @Override
        public Set<String> tabNamesLower() {
            Set<String> out = new HashSet<>();
            class_634 handler = mc.method_1562();
            if (handler != null) {
                for (class_640 info : handler.method_2880()) {
                    if (info != null && info.method_2966() != null && info.method_2966().name() != null) {
                        out.add(info.method_2966().name().toLowerCase(Locale.ROOT));
                    }
                }
            }
            return out;
        }

        private VanishEngine.Body body(class_1657 p) {
            String name = p.method_7334() == null ? p.method_5477().getString() : p.method_7334().name();
            var pos = p.method_73189();
            return new VanishEngine.Body(p.method_5667(), p.method_5628(), name, pos.field_1352, pos.field_1351, pos.field_1350,
                    p.method_5739(mc.field_1724), p.method_5767());
        }

        @Override
        public List<VanishEngine.Body> players() {
            List<VanishEngine.Body> out = new ArrayList<>();
            if (mc.field_1687 != null) {
                for (class_1657 p : mc.field_1687.method_18456()) {
                    if (p != null && p != mc.field_1724) {
                        out.add(body(p));
                    }
                }
            }
            return out;
        }

        @Override
        public VanishEngine.Body playerByUuid(UUID id) {
            class_1657 p = mc.field_1687 == null ? null : mc.field_1687.method_18470(id);
            return p != null && p != mc.field_1724 && !p.method_31481() ? body(p) : null;
        }

        @Override
        public VanishEngine.Body playerByEntityId(int entityId) {
            return mc.field_1687 != null && mc.field_1687.method_8469(entityId) instanceof class_1657 p && p != mc.field_1724 ? body(p) : null;
        }

        @Override
        public boolean projectileNear(double x, double y, double z, double radiusSq) {
            if (mc.field_1687 != null) {
                for (class_1297 e : mc.field_1687.method_18112()) {
                    if (e != mc.field_1724 && e instanceof class_1676 && sq(e, x, y, z) <= radiusSq) {
                        return true;
                    }
                }
            }
            return false;
        }

        @Override
        public boolean villagerNear(double x, double y, double z, double radiusSq) {
            if (mc.field_1687 != null) {
                for (class_1297 e : mc.field_1687.method_18112()) {
                    if (e.method_5864() == class_1299.field_6077 && sq(e, x, y, z) <= radiusSq) {
                        return true;
                    }
                }
            }
            return false;
        }

        private double sq(class_1297 e, double x, double y, double z) {
            var p = e.method_73189();
            double dx = p.field_1352 - x;
            double dy = p.field_1351 - y;
            double dz = p.field_1350 - z;
            return dx * dx + dy * dy + dz * dz;
        }

        @Override
        public String blockPath(int x, int y, int z) {
            class_2960 id = class_7923.field_41175.method_10221(mc.field_1687.method_8320(new class_2338(x, y, z)).method_26204());
            return id == null ? "" : id.toString();
        }

        @Override
        public boolean powered(int x, int y, int z) {
            class_2338 pos = new class_2338(x, y, z);
            class_2680 state = mc.field_1687.method_8320(pos);
            boolean powered = state.method_28498(class_2741.field_12484) && state.method_11654(class_2741.field_12484);
            return powered || mc.field_1687.method_49803(pos);
        }

        @Override
        public boolean isTarget(String name) {
            return AntiVanish.this.isTarget(name);
        }

        @Override
        public void sendCompletionRequest(int id, String command) {
            class_634 handler = mc.method_1562();
            if (handler != null) {
                handler.method_52787(new class_2805(id, command));
            }
        }
    }
}
