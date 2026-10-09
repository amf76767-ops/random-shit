package dev.dihclient.glue;

import dev.dihclient.DIHClient;
import dev.dihclient.autobuild.BuildLog;
import dev.dihclient.autobuild.BuildRuntime;
import dev.dihclient.module.Category;
import dev.dihclient.module.Module;
import dev.dihclient.modules.world.AutoBuild;
import dev.dihclient.port.PacketBus;
import dev.dihclient.setting.BoolSetting;
import dev.dihclient.setting.DoubleSetting;
import dev.dihclient.setting.IntSetting;
import dev.dihclient.util.Notifications;
import net.minecraft.class_1657;
import net.minecraft.class_2596;
import net.minecraft.class_2761;

public class BuildGuard extends Module {
    private final AutoBuild build;
    private final BoolSetting enabled;
    private final IntSetting range;
    private final DoubleSetting health;
    private final DoubleSetting minTps;
    private final IntSetting resumeSeconds;
    private final PacketBus.Netty listener = this::onPacket;

    private boolean heldByUs;
    private int calm;
    private volatile double tps = 20.0;
    private volatile long lastNanos;
    private volatile long lastGame = -1;
    private int lowTpsTicks;

    public BuildGuard(AutoBuild build, BoolSetting enabled, IntSetting range, DoubleSetting health, DoubleSetting minTps, IntSetting resumeSeconds) {
        super("BuildGuard", Category.AUTOMATION, "Internal: pauses AutoBuild when it is not safe to build.");
        this.build = build;
        this.enabled = enabled;
        this.range = range;
        this.health = health;
        this.minTps = minTps;
        this.resumeSeconds = resumeSeconds;
        this.setHidden(true);
        PacketBus.netty(this.listener);
    }

    private boolean onPacket(class_2596<?> packet) {
        if (!this.build.isEnabled()) {
            this.lastGame = -1;
            return false;
        }
        if (packet instanceof class_2761 time) {
            long now = System.nanoTime();
            long game = time.comp_3219();
            long before = this.lastGame;
            long beforeNanos = this.lastNanos;
            this.lastGame = game;
            this.lastNanos = now;
            if (before >= 0 && game > before) {
                double seconds = (now - beforeNanos) / 1.0E9;
                if (seconds > 0.4 && seconds < 30.0) {
                    this.tps = Math.min(20.0, (game - before) / seconds);
                }
            }
        }
        return false;
    }

    @Override
    public void onWorldChange() {
        this.lastGame = -1;
        this.tps = 20.0;
        this.heldByUs = false;
        this.calm = 0;
        this.lowTpsTicks = 0;
    }

    @Override
    public void onTick() {
        try {
            this.tick();
        } catch (Throwable t) {
            DIHClient.LOG.warn("[DIHClient] build guard failed", t);
        }
    }

    private void tick() {
        if (mc.field_1724 == null || mc.field_1687 == null) {
            return;
        }
        BuildRuntime rt = this.build.runtime();
        if (this.heldByUs && rt.phase() != BuildRuntime.Phase.PAUSED) {
            this.heldByUs = false;
        }
        if (!this.enabled.get() || !rt.isRunning()) {
            this.heldByUs = false;
            return;
        }
        String danger = this.danger();
        if (this.tps < this.minTps.get() && this.minTps.get() > 0) {
            this.lowTpsTicks++;
        } else {
            this.lowTpsTicks = 0;
        }
        if (danger == null && this.lowTpsTicks >= 40) {
            danger = String.format("the server is slow (%.1f TPS)", this.tps);
        }
        if (danger != null) {
            this.calm = 0;
            if (!this.heldByUs && rt.phase() == BuildRuntime.Phase.BUILDING) {
                rt.pauseToggle();
                this.heldByUs = true;
                Notifications.warn("AutoBuild", "Paused: " + danger);
                BuildLog.add("guard paused the build: " + danger);
            }
        } else if (this.heldByUs && ++this.calm >= this.resumeSeconds.get() * 20) {
            this.heldByUs = false;
            this.calm = 0;
            if (rt.phase() == BuildRuntime.Phase.PAUSED) {
                rt.pauseToggle();
                Notifications.info("AutoBuild", "Safe again: building goes on");
                BuildLog.add("guard resumed the build");
            }
        }
    }

    private String danger() {
        double r = this.range.get();
        if (r > 0) {
            for (class_1657 p : mc.field_1687.method_18456()) {
                if (p != mc.field_1724 && !p.method_7325() && !DIHClient.social().isFriend(p) && p.method_5739(mc.field_1724) <= r) {
                    return p.method_5477().getString() + " is " + Math.round(p.method_5739(mc.field_1724)) + " blocks away";
                }
            }
        }
        double h = this.health.get();
        if (h > 0 && !mc.field_1724.method_31549().field_7477 && mc.field_1724.method_6032() <= h * 2.0) {
            return "your health is low (" + Math.round(mc.field_1724.method_6032() / 2.0F) + " hearts)";
        }
        return null;
    }
}
