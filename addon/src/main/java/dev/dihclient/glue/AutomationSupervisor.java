package dev.dihclient.glue;

import dev.dihclient.DIHClient;
import dev.dihclient.ai.SupervisorEngine;
import dev.dihclient.module.Category;
import dev.dihclient.module.Module;
import dev.dihclient.setting.BoolSetting;
import dev.dihclient.setting.IntSetting;
import dev.dihclient.setting.StringSetting;
import dev.dihclient.social.SocialManager;
import dev.dihclient.util.Notifications;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.class_1657;
import net.minecraft.class_243;

public class AutomationSupervisor extends Module {
    private static final int SAMPLE_EVERY_TICKS = 5;

    public final BoolSetting fixStuck = this.bool("Fix Stuck", "Restarts a module that shows no progress, and switches it off if that does not help.", true);
    public final IntSetting stuckAfter = this.integer("Stuck After", "Seconds without any progress before a module counts as stuck.", 45, 10, 300)
            .visibleWhen(this.fixStuck::get);
    public final IntSetting restarts = this.integer("Restarts", "How often a stuck module is restarted before it is switched off.", 2, 0, 5)
            .visibleWhen(this.fixStuck::get);
    public final BoolSetting lowHealth = this.bool("Pause On Low Health", "Pauses the automation when your health gets low.", true);
    public final IntSetting pauseHearts = this.integer("Pause Below", "Hearts (with absorption) under which everything is paused.", 4, 1, 19)
            .visibleWhen(this.lowHealth::get);
    public final IntSetting resumeHearts = this.integer("Resume At", "Hearts you need to be back at before things resume.", 8, 2, 20)
            .visibleWhen(this.lowHealth::get);
    public final BoolSetting players = this.bool("Pause For Players", "Pauses the automation when a player who is not your friend comes close.", true);
    public final IntSetting radius = this.integer("Player Radius", "How close a stranger has to be, in blocks.", 48, 8, 128)
            .visibleWhen(this.players::get);
    public final IntSetting resumeAfter = this.integer("Resume After", "Seconds it has to stay safe before the paused modules start again.", 10, 1, 120);
    public final StringSetting watched = this.text("Watched",
            "Modules that are paused in danger and checked for being stuck. Separate with commas.",
            "AutoMine,AutoFarm,Tunnel,Goto,StashSorter,AutoRestock,Terraform,AutoBuild,MapArt,SmartBridge", 300);
    public final StringSetting guarded = this.text("Only Pause",
            "Modules that are only paused in danger, because waiting is normal for them. Separate with commas.",
            "AutoFish,AutoCraft,AutoTrade,AutoSell,AuctionHouse,FlipFinder", 300);

    private static AutomationSupervisor instance;
    private static int lastPhysicalJump = -1000;
    private static boolean wasFlying;

    private final SupervisorEngine.Config cfg = new SupervisorEngine.Config();
    private final SupervisorEngine engine = new SupervisorEngine(this.cfg);
    private long tick;

    public AutomationSupervisor() {
        super("AutoSupervisor", Category.AUTOMATION,
                "Keeps AutoMine, AutoFarm, Tunnel and the other automation safe: pauses in danger, restarts what gets stuck.");
        instance = this;
    }

    public static void creativeGuard() {
        if (mc.field_1724 == null || mc.field_1690 == null) {
            wasFlying = false;
            return;
        }
        var abilities = mc.field_1724.method_31549();
        int now = mc.field_1724.field_6012;
        if (dev.dihclient.util.KeyUtil.isPhysicallyDown(mc.field_1690.field_1903)) {
            lastPhysicalJump = now;
        }
        boolean flying = abilities.field_7479;
        AutomationSupervisor s = instance;
        if (flying && !wasFlying && s != null && abilities.field_7477 && now - lastPhysicalJump > 10 && s.automationRunning()) {
            abilities.field_7479 = false;
            flying = false;
        }
        wasFlying = flying;
    }

    static boolean isPaused(Module module) {
        AutomationSupervisor s = instance;
        return s != null && s.engine.pausedIds().contains(module.id());
    }

    private boolean automationRunning() {
        for (String csv : new String[]{this.watched.get(), this.guarded.get()}) {
            for (Module m : this.resolve(csv)) {
                if (m.isEnabled()) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    protected void onEnable() {
        this.engine.reset();
        this.tick = 0;
    }

    @Override
    protected void onDisable() {
        this.engine.reset();
    }

    @Override
    public void onWorldChange() {
        this.engine.reset();
    }

    @Override
    public void onTick() {
        if (mc.field_1724 == null || mc.field_1687 == null) {
            return;
        }
        this.tick++;
        if (this.tick % SAMPLE_EVERY_TICKS != 0) {
            return;
        }
        this.configure();
        List<Module> watch = this.resolve(this.watched.get() + ",Demolish");
        List<Module> guard = this.resolve(this.guarded.get());
        guard.removeAll(watch);
        if (watch.stream().noneMatch(Module::isEnabled) && guard.stream().noneMatch(Module::isEnabled)
                && this.engine.pausedIds().isEmpty()) {
            return;
        }

        List<SupervisorEngine.Sample> watchSamples = this.sample(watch);
        List<SupervisorEngine.Sample> guardSamples = this.sample(guard);
        List<SupervisorEngine.Action> actions = this.engine.step(this.tick, this.world(), watchSamples, guardSamples);
        for (SupervisorEngine.Action a : actions) {
            this.apply(a);
        }
    }

    private void configure() {
        this.cfg.detectStuck = this.fixStuck.get();
        this.cfg.stuckTicks = this.stuckAfter.get() * 20;
        this.cfg.maxRestarts = this.restarts.get();
        this.cfg.pauseOnLowHealth = this.lowHealth.get();
        this.cfg.pauseBelowHealth = this.pauseHearts.get() * 2f;
        this.cfg.resumeAtHealth = Math.max(this.resumeHearts.get(), this.pauseHearts.get() + 1) * 2f;
        this.cfg.pauseForPlayers = this.players.get();
        this.cfg.resumeDelayTicks = this.resumeAfter.get() * 20;
    }

    private SupervisorEngine.World world() {
        class_243 me = mc.field_1724.method_73189();
        class_1657 nearest = null;
        double best = Double.MAX_VALUE;
        int strangers = 0;
        double limit = (double) this.radius.get() * this.radius.get();
        String myName = SocialManager.name(mc.field_1724);
        for (class_1657 p : mc.field_1687.method_18456()) {
            if (p == mc.field_1724 || p.method_5628() < 0 || !p.method_5805() || p.method_7325()) {
                continue;
            }
            if (myName.equals(SocialManager.name(p)) || DIHClient.social().isFriend(p)) {
                continue;
            }
            double d = p.method_73189().method_1025(me);
            if (d <= limit) {
                strangers++;
                if (d < best) {
                    best = d;
                    nearest = p;
                }
            }
        }
        float hp = mc.field_1724.method_6032() + mc.field_1724.method_6067();
        return new SupervisorEngine.World(me.field_1352, me.field_1351, me.field_1350, hp, strangers,
                nearest == null ? null : SocialManager.name(nearest), mc.field_1724.method_29504());
    }

    private List<Module> resolve(String csv) {
        List<Module> out = new ArrayList<>();
        for (String name : csv.split(",")) {
            String n = name.trim();
            if (n.isEmpty()) {
                continue;
            }
            Module m = DIHClient.modules().get(n);
            if (m != null && m != this && !out.contains(m)) {
                out.add(m);
            }
        }
        return out;
    }

    private List<SupervisorEngine.Sample> sample(List<Module> modules) {
        List<SupervisorEngine.Sample> out = new ArrayList<>();
        for (Module m : modules) {
            String status = "";
            String progress = "";
            if (m.isEnabled()) {
                List<String> details = m.details();
                for (String line : details) {
                    if (line.startsWith("Status:")) {
                        status = line.substring(7).trim().toLowerCase(Locale.ROOT);
                    }
                }
                progress = String.join("|", details) + "#" + m.getInfo();
            }
            boolean benign = status.startsWith("idle") || status.startsWith("paused") || status.contains("wait");
            out.add(new SupervisorEngine.Sample(m.id(), m.isEnabled(), progress, benign));
        }
        return out;
    }

    private void apply(SupervisorEngine.Action a) {
        if (a.type() == SupervisorEngine.Type.WARN) {
            Notifications.warn(this.name(), a.text());
            return;
        }
        Module m = DIHClient.modules().get(a.id());
        if (m == null) {
            return;
        }

        m.setEnabledSilently(a.type() == SupervisorEngine.Type.ENABLE);
    }

    @Override
    public String getInfo() {
        return this.engine.inDanger() ? "paused" : null;
    }

    @Override
    public List<String> details() {
        List<String> out = new ArrayList<>();
        out.add("Status: " + (this.engine.inDanger() ? "Paused, waiting for it to be safe" : "Watching"));
        if (!this.engine.pausedIds().isEmpty()) {
            out.add("Paused: " + String.join(", ", this.engine.pausedIds()));
        }
        return out;
    }
}
