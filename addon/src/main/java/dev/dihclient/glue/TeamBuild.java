package dev.dihclient.glue;

import dev.dihclient.DIHClient;
import dev.dihclient.autobuild.BuildPlan;
import dev.dihclient.autobuild.BuildRuntime;
import dev.dihclient.autobuild.Schematic;
import dev.dihclient.autobuild.SchematicLoader;
import dev.dihclient.autobuild.TeamSlices;
import dev.dihclient.module.Category;
import dev.dihclient.module.Module;
import dev.dihclient.modules.world.AutoBuild;
import dev.dihclient.port.configs.ConfigStore;
import dev.dihclient.port.configs.Configs;
import dev.dihclient.setting.StringSetting;
import dev.dihclient.team.TeamStore;
import dev.dihclient.util.Notifications;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.class_2338;
import net.minecraft.class_2415;
import net.minecraft.class_2470;

public class TeamBuild extends Module {
    private static final long STALE_MILLIS = 15_000L;
    private static final int POLL_TICKS = 40;

    private final AutoBuild build;
    private final StringSetting codeField;
    private final TeamStore store = new TeamStore(TeamStore.defaultRoot());
    private final String memberId = UUID.randomUUID().toString().substring(0, 8);

    private String code;
    private TeamStore.Spec spec;
    private Schematic schematic;
    private BuildPlan currentPlan;
    private int curIdx = -1;
    private int curCount = -1;
    private int pendIdx = -1;
    private int pendCount = -1;
    private int stable;
    private boolean helping;
    private boolean announcedDone;
    private int ticks;

    public TeamBuild(AutoBuild build, StringSetting codeField) {
        super("TeamBuild", Category.AUTOMATION, "Internal: builds together with the alt accounts (team codes of AutoBuild).");
        this.build = build;
        this.codeField = codeField;
        this.setHidden(true);
        try {
            this.store.cleanup(System.currentTimeMillis(), 24L * 3600_000L);
        } catch (RuntimeException ignored) {

        }
    }

    public void create() {
        try {
            BuildRuntime rt = this.build.runtime();
            if (rt.plan() == null || !rt.isRunning()) {
                Notifications.warn("Team", "Pick a schematic first (Open Browser), then press Create Team.");
                return;
            }
            if (rt.phase() == BuildRuntime.Phase.PREVIEW) {
                rt.confirm();
            }
            BuildPlan plan = rt.plan();
            if (plan == null || (rt.phase() != BuildRuntime.Phase.BUILDING && rt.phase() != BuildRuntime.Phase.PAUSED)) {
                Notifications.warn("Team", "Start the build first (Enter in the preview), then press Create Team.");
                return;
            }
            this.leave(false);
            Path file = plan.schematic.file.toAbsolutePath();
            String code = TeamStore.newCode(new java.util.Random());
            long now = System.currentTimeMillis();
            this.spec = new TeamStore.Spec(code, playerName(), serverKey(), dimension(), file.toString(), plan.schematic.name(),
                    TeamStore.sha256(file), plan.anchor.method_10263(), plan.anchor.method_10264(), plan.anchor.method_10260(),
                    plan.rotation.name(), plan.mirror.name(), now);
            this.store.saveSpec(this.spec);
            this.store.heartbeat(code, this.memberId, playerName(), now);
            this.code = code;
            this.schematic = plan.schematic;
            this.currentPlan = plan;
            this.curIdx = 0;
            this.curCount = 1;
            this.helping = false;
            this.announcedDone = false;
            this.codeField.set(code);
            mc.field_1774.method_1455(code);
            Notifications.info("Team", "Team code " + code + " (copied). Type it into Team Code on the other accounts and press Join Team.");
        } catch (IOException | RuntimeException e) {
            DIHClient.LOG.warn("[DIHClient] could not create the team", e);
            Notifications.warn("Team", "Could not create the team: " + e.getMessage());
        }
    }

    public void join() {
        try {
            String typed = this.codeField.get();
            String code = TeamStore.normalizeCode(typed == null || typed.isBlank() ? clipboard() : typed);
            if (code.isEmpty()) {
                Notifications.warn("Team", "Type the team code (like ABCD-EFGH) into Team Code first.");
                return;
            }
            Optional<TeamStore.Spec> found = this.store.loadSpec(code);
            if (found.isEmpty()) {
                Notifications.warn("Team", "No team with the code " + code + " on this computer.");
                return;
            }
            TeamStore.Spec s = found.get();
            if (mc.field_1687 == null || mc.field_1724 == null) {
                Notifications.warn("Team", "Join a world or server first.");
                return;
            }
            if (!s.server().equals(serverKey()) || !s.dimension().equals(dimension())) {
                Notifications.warn("Team", "The team builds on " + s.server() + " in " + s.dimension() + ". Go there first.");
                return;
            }
            Path file = findSchematic(s);
            if (file == null) {
                Notifications.warn("Team", "The schematic \"" + s.schematicName() + "\" was not found, or it is not the same file as the host's.");
                return;
            }
            this.leave(false);
            this.spec = s;
            this.schematic = SchematicLoader.load(file);
            this.code = code;
            this.codeField.set(code);
            this.curIdx = -1;
            this.curCount = -1;
            this.pendIdx = -1;
            this.helping = false;
            this.announcedDone = false;
            this.currentPlan = null;
            this.store.heartbeat(code, this.memberId, playerName(), System.currentTimeMillis());
            this.poll(true);
            Notifications.info("Team", "Joined the team of " + s.host() + " (" + s.schematicName() + ").");
        } catch (IOException | RuntimeException e) {
            DIHClient.LOG.warn("[DIHClient] could not join the team", e);
            Notifications.warn("Team", "Could not join the team: " + e.getMessage());
        }
    }

    public void leave() {
        this.leave(true);
    }

    private void leave(boolean say) {
        if (this.code != null) {
            this.store.leave(this.code, this.memberId);
            if (say) {
                Notifications.info("Team", "Left the team " + this.code + ". The build goes on alone until you stop it.");
            }
        }
        this.code = null;
        this.spec = null;
        this.schematic = null;
        this.currentPlan = null;
    }

    @Override
    public void onWorldChange() {
        this.leave(false);
    }

    @Override
    public void onTick() {
        if (this.code == null) {
            return;
        }
        try {
            BuildRuntime rt = this.build.runtime();
            if (this.currentPlan != null && rt.plan() != this.currentPlan) {
                this.leave(true);
                return;
            }
            if (this.currentPlan != null && rt.phase() == BuildRuntime.Phase.FINISHED) {
                this.finished();
            }
            if (++this.ticks % POLL_TICKS == 0) {
                this.store.heartbeat(this.code, this.memberId, playerName(), System.currentTimeMillis());
                this.poll(false);
            }
        } catch (IOException | RuntimeException e) {
            DIHClient.LOG.warn("[DIHClient] team build step failed", e);
        }
    }

    private void finished() {
        if (this.curCount > 1 && !this.helping) {
            this.helping = true;
            this.start(this.fullPlan(), 0, 1);
            Notifications.info("Team", "Your part is done. Helping with the rest of the schematic.");
        } else if (!this.announcedDone) {
            this.announcedDone = true;
            Notifications.info("Team", "The team build is finished.");
        }
    }

    private void poll(boolean first) {
        if (this.helping || this.spec == null) {
            return;
        }
        List<TeamStore.Member> members = this.store.active(this.code, System.currentTimeMillis(), STALE_MILLIS);
        int count = Math.max(1, members.size());
        int idx = 0;
        for (int i = 0; i < members.size(); i++) {
            if (members.get(i).id().equals(this.memberId)) {
                idx = i;
            }
        }
        if (idx == this.curIdx && count == this.curCount) {
            this.stable = 0;
            return;
        }
        if (idx == this.pendIdx && count == this.pendCount) {
            this.stable++;
        } else {
            this.pendIdx = idx;
            this.pendCount = count;
            this.stable = 0;
        }

        if (first || this.curIdx < 0 || this.stable >= 1) {
            this.start(count <= 1 ? this.fullPlan() : this.slicePlan(idx, count), idx, count);
        }
    }

    private BuildPlan fullPlan() {
        return new BuildPlan(this.schematic, new class_2338(this.spec.x(), this.spec.y(), this.spec.z()),
                class_2470.valueOf(this.spec.rotation()), class_2415.valueOf(this.spec.mirror()));
    }

    private BuildPlan slicePlan(int idx, int count) {
        return new BuildPlan(TeamSlices.slice(this.schematic, idx, count), new class_2338(this.spec.x(), this.spec.y(), this.spec.z()),
                class_2470.valueOf(this.spec.rotation()), class_2415.valueOf(this.spec.mirror()));
    }

    private void start(BuildPlan plan, int idx, int count) {
        this.currentPlan = plan;
        this.curIdx = idx;
        this.curCount = count;
        this.stable = 0;
        this.build.startBuild(plan);
        if (count > 1) {
            Notifications.info("Team", "Building part " + (idx + 1) + " of " + count + ".");
        }
    }

    private static Path findSchematic(TeamStore.Spec s) throws IOException {
        Path[] candidates = {Path.of(s.schematicPath()), AutoBuild.schematicDir().resolve(Path.of(s.schematicPath()).getFileName().toString())};
        for (Path p : candidates) {
            if (Files.isRegularFile(p) && TeamStore.sha256(p).equals(s.sha256())) {
                return p;
            }
        }
        return null;
    }

    private static String serverKey() {
        String address = Configs.currentServer();
        return address == null ? "singleplayer" : ConfigStore.normalize(address);
    }

    private static String dimension() {
        return mc.field_1687.method_27983().method_29177().toString();
    }

    private static String playerName() {
        return mc.field_1724 == null ? "?" : mc.field_1724.method_7334().name();
    }

    private static String clipboard() {
        try {
            String s = mc.field_1774.method_1460();
            return s == null ? "" : s.trim();
        } catch (Throwable t) {
            return "";
        }
    }
}
