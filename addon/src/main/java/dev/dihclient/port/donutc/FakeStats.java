package dev.dihclient.port.donutc;

import dev.dihclient.DIHClient;
import dev.dihclient.module.Category;
import dev.dihclient.module.Module;
import dev.dihclient.port.donutc.FakeStatsLogic.Hit;
import dev.dihclient.setting.StringSetting;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.class_2561;
import net.minecraft.class_2583;
import net.minecraft.class_266;
import net.minecraft.class_268;
import net.minecraft.class_269;
import net.minecraft.class_274;
import net.minecraft.class_274.class_275;
import net.minecraft.class_5223;
import net.minecraft.class_5250;
import net.minecraft.class_5348;
import net.minecraft.class_746;
import net.minecraft.class_8646;
import net.minecraft.class_9011;
import net.minecraft.class_9013;
import net.minecraft.class_9014;
import net.minecraft.class_9015;
import net.minecraft.class_9021;
import net.minecraft.class_9022;
import net.minecraft.class_9025;

/** Ported from an open-source client (GPL-3.0). */
public class FakeStats extends Module {
    private static final String OBJECTIVE = "dih_fs";
    private static final int LINES = 15;
    private static final Comparator<class_9011> ORDER = Comparator.comparingInt(class_9011::comp_2128)
            .reversed()
            .thenComparing(class_9011::comp_2127, String.CASE_INSENSITIVE_ORDER);

    private static volatile FakeStats active;

    public final StringSetting playerName = this.text("Name", "The name shown for you on the sidebar. Empty keeps your real name.", "", 64);
    public final StringSetting money = this.text("Money", "Shown as your money. Empty scrambles the real amount.", "", 32);
    public final StringSetting shards = this.text("Shards", "Shown as your shards. Empty scrambles the real amount.", "", 32);
    public final StringSetting kills = this.text("Kills", "Shown as your kills. Empty scrambles the real amount.", "", 32);
    public final StringSetting deaths = this.text("Deaths", "Shown as your deaths. Empty scrambles the real amount.", "", 32);
    public final StringSetting playtime = this.text("Playtime", "Shown as your playtime. Empty scrambles the real amount.", "", 32);
    public final StringSetting team = this.text("Team", "Shown as your team. Empty scrambles the real value.", "", 32);

    private final StringSetting[] statValues = {this.money, this.shards, this.kills, this.deaths, this.playtime, this.team};
    private final List<String[]> markers = List.of(
            new String[] {"money", "balance", "$"},
            new String[] {"shards", "★"},
            new String[] {"kills", "🗡", "⚔"},
            new String[] {"deaths", "☠"},
            new String[] {"playtime", "⌚"},
            new String[] {"team"});

    private long seed;
    private boolean applying;
    private class_269 board;
    private class_266 fake;
    private class_266 serverSidebar;
    private String sidebarMoney;
    private boolean nameReported;

    public FakeStats() {
        super("Fake Stats", Category.DONUT, "Shows your own name and stats on the sidebar and tab list. Blank stats get scrambled.");
    }

    public static boolean redirect(class_269 board, class_8646 slot, class_266 objective) {
        FakeStats module = active;
        if (module == null || module.applying || slot != class_8646.field_45157) {
            return false;
        }
        try {
            if (objective != null && OBJECTIVE.equals(objective.method_1113())) {
                return false;
            }
            module.serverSidebar = objective;
            if (objective == null) {
                return false;
            }
            class_266 shown = module.install(board);
            if (shown == null) {
                return false;
            }
            module.applying = true;
            try {
                board.method_1158(class_8646.field_45157, shown);
            } finally {
                module.applying = false;
            }
            return true;
        } catch (Throwable t) {
            module.fail(t);
            return false;
        }
    }

    public static class_2561 tabName(UUID player, class_2561 name) {
        FakeStats module = active;
        try {
            return module != null && name != null && isSelf(player) ? module.fakeTabMoney(name) : name;
        } catch (Throwable t) {
            if (module != null) {
                module.fail(t);
            }
            return name;
        }
    }

    public static class_5348 tabText(class_5348 text) {
        FakeStats module = active;
        try {
            return module != null && text instanceof class_2561 line ? module.fakeTabMoney(line) : text;
        } catch (Throwable t) {
            if (module != null) {
                module.fail(t);
            }
            return text;
        }
    }

    public static class_9013 tabScore(class_9015 holder, class_9013 real) {
        FakeStats module = active;
        class_746 player = mc.field_1724;
        if (module == null || real == null || player == null || !player.method_5820().equals(holder.method_5820())) {
            return real;
        }
        return new class_9013() {
            @Override
            public int method_55397() {
                return real.method_55397();
            }

            @Override
            public boolean method_1131() {
                return real.method_1131();
            }

            @Override
            public class_9022 method_55400() {
                return real.method_55400();
            }

            @Override
            public class_5250 method_55399(class_9022 fallback) {
                class_2561 original = real.method_55399(fallback);
                try {
                    return class_2561.method_43473().method_10852(module.fakeTabMoney(original));
                } catch (Throwable t) {
                    module.fail(t);
                    return class_2561.method_43473().method_10852(original);
                }
            }
        };
    }

    @Override
    protected void onEnable() {
        this.seed = ThreadLocalRandom.current().nextLong();
        this.nameReported = false;
        active = this;
        this.refresh();
    }

    @Override
    protected void onDisable() {
        active = null;
        try {
            this.restore();
        } catch (Throwable t) {
            DIHClient.LOG.warn("[DIHClient] Fake Stats could not restore the sidebar", t);
        }
    }

    @Override
    public void onWorldChange() {

        this.board = null;
        this.fake = null;
        this.serverSidebar = null;
        this.sidebarMoney = null;
        this.nameReported = false;
    }

    @Override
    public void onTick() {
        this.refresh();
    }

    private boolean failed;

    private void fail(Throwable t) {
        if (!this.failed) {
            this.failed = true;
            DIHClient.LOG.warn("[DIHClient] Fake Stats failed (reported once)", t);
        }
    }

    private void refresh() {
        try {
            if (mc.field_1687 == null) {
                this.board = null;
                this.fake = null;
                this.serverSidebar = null;
                this.sidebarMoney = null;
                return;
            }
            class_269 current = mc.field_1687.method_8428();
            if (current != this.board) {
                this.board = current;
                this.fake = null;
                this.serverSidebar = null;
            }
            class_266 shown = current.method_1189(class_8646.field_45157);
            if (this.serverSidebar == null || this.serverSidebar.method_1117() != current) {
                this.serverSidebar = shown != null && !OBJECTIVE.equals(shown.method_1113()) ? shown : null;
            }
            if (this.serverSidebar == null) {
                this.sidebarMoney = null;
                if (this.fake != null && shown == this.fake) {
                    this.setSidebar(current, null);
                }
                return;
            }
            class_266 installed = this.install(current);
            if (installed != null && current.method_1189(class_8646.field_45157) != installed) {
                this.setSidebar(current, installed);
            }
        } catch (Throwable t) {
            this.fail(t);
        }
    }

    private void setSidebar(class_269 current, class_266 objective) {
        this.applying = true;
        try {
            current.method_1158(class_8646.field_45157, objective);
        } finally {
            this.applying = false;
        }
    }

    private class_266 install(class_269 current) {
        class_266 server = this.serverSidebar;
        if (server != null && server.method_1117() == current) {
            class_266 objective = current.method_1170(OBJECTIVE);
            if (objective == null) {
                objective = current.method_1168(OBJECTIVE, class_274.field_1468, class_2561.method_43473(), class_275.field_1472, false,
                        server.method_55384());
            }
            this.fake = objective;
            this.board = current;
            objective.method_55382(server.method_55384());
            this.writeRows(current, objective, server);
            return objective;
        }
        return this.fake;
    }

    private void writeRows(class_269 current, class_266 objective, class_266 server) {
        List<class_9011> rows = new ArrayList<>();
        for (class_9011 entry : current.method_1184(server)) {
            if (!entry.method_55385()) {
                rows.add(entry);
            }
        }
        rows.sort(ORDER);
        if (rows.size() > LINES) {
            rows.subList(LINES, rows.size()).clear();
        }

        class_2561 title = visible(server.method_1114());
        class_2561 renamedTitle = this.renamed(title);
        objective.method_1121(renamedTitle != null ? renamedTitle : this.mask(title, 0));
        boolean nameShown = renamedTitle != null;
        class_9022 format = server.method_55380(class_9025.field_47567);
        this.sidebarMoney = null;

        for (int i = 0; i < rows.size(); i++) {
            class_9011 row = rows.get(i);
            class_2561 name = visible(class_268.method_1142(current.method_1164(row.comp_2127()), row.method_55387()));
            class_2561 number = visible(row.method_55386(format));
            class_2561 renamed = this.renamed(name);
            Hit hit = renamed == null ? FakeStatsLogic.find(this.markers, name.getString(), number.getString()) : null;
            String custom = hit != null ? this.statValues[hit.stat()].get().strip() : "";
            String shown = null;
            if (renamed != null) {
                name = renamed;
                nameShown = true;
            } else if (custom.isEmpty()) {
                name = this.mask(name, i + 1);
                number = this.mask(number, i + 1);
                if (hit != null) {
                    shown = (hit.inNumber() ? number : name).getString().substring(hit.start(), hit.end());
                }
            } else {
                class_2561 target = hit.inNumber() ? number : name;
                shown = FakeStatsLogic.fit(target.getString().substring(hit.start(), hit.end()), custom);
                class_2561 swapped = replace(target, List.of(new int[] {hit.start(), hit.end()}), shown);
                name = hit.inNumber() ? this.mask(name, i + 1) : swapped;
                number = hit.inNumber() ? swapped : this.mask(number, i + 1);
            }
            if (hit != null && hit.stat() == 0) {
                this.sidebarMoney = shown;
            }

            class_9015 holder = class_9015.method_55422(FakeStatsLogic.holderName(i));
            current.method_1195(holder.method_5820());
            class_9014 access = current.method_1180(holder, objective);
            access.method_55410(row.comp_2128());
            access.method_55411(name);
            access.method_55412(new class_9021(number));
        }
        for (int i = rows.size(); i < LINES; i++) {
            class_9015 holder = class_9015.method_55422(FakeStatsLogic.holderName(i));
            if (current.method_55430(holder, objective) != null) {
                current.method_1155(holder, objective);
            }
        }

        if (!nameShown && !this.playerName.get().isBlank() && !this.nameReported) {

            this.nameReported = true;
            this.reportMissingName(current, rows, format, title);
        }
    }

    private void reportMissingName(class_269 current, List<class_9011> rows, class_9022 format, class_2561 title) {
        StringBuilder lines = new StringBuilder("title: ").append(FakeStatsLogic.escaped(title.getString()));
        for (class_9011 row : rows) {
            class_2561 name = visible(class_268.method_1142(current.method_1164(row.comp_2127()), row.method_55387()));
            lines.append("\n  ").append(FakeStatsLogic.escaped(name.getString())).append(" | ")
                    .append(FakeStatsLogic.escaped(visible(row.method_55386(format)).getString()));
        }
        DIHClient.LOG.info("[DIHClient] Fake Stats: no sidebar line shows the name {}\n{}", selfName(), lines);
    }

    private void restore() {
        class_269 current = this.board;
        class_266 server = this.serverSidebar;
        class_266 shown = this.fake;
        this.board = null;
        this.fake = null;
        this.serverSidebar = null;
        this.sidebarMoney = null;
        if (current == null) {
            return;
        }
        if (current.method_1189(class_8646.field_45157) == shown) {
            class_266 back = server != null && server.method_1117() == current ? server : null;
            current.method_1158(class_8646.field_45157, back);
        }
        class_266 leftover = current.method_1170(OBJECTIVE);
        if (leftover != null) {
            current.method_1194(leftover);
        }
    }

    private static String selfName() {
        class_746 player = mc.field_1724;
        return player != null ? player.method_7334().name() : null;
    }

    private static boolean isSelf(UUID player) {
        class_746 self = mc.field_1724;
        return self != null && self.method_5667().equals(player);
    }

    private class_2561 renamed(class_2561 line) {
        String self = selfName();
        int at = self != null ? FakeStatsLogic.markerAt(line.getString(), self) : -1;
        if (at < 0) {
            return null;
        }
        String custom = this.playerName.get().strip();
        return custom.isEmpty() ? line : replace(line, List.of(new int[] {at, at + self.length()}), custom);
    }

    private class_2561 fakeTabMoney(class_2561 line) {
        String shown = FakeStatsLogic.tabMoney(this.money.get(), this.sidebarMoney);
        if (shown != null && line.getString().indexOf('$') >= 0) {
            class_2561 clean = visible(line);
            List<int[]> ranges = FakeStatsLogic.moneyRanges(clean.getString());
            return ranges.isEmpty() ? line : replace(clean, ranges, shown);
        }
        return line;
    }

    private static class_2561 visible(class_2561 line) {
        if (line.getString().indexOf('§') < 0) {
            return line;
        }
        class_5250 out = class_2561.method_43473();
        StringBuilder run = new StringBuilder();
        class_2583[] runStyle = new class_2583[] {null};
        class_5223.method_27476(line, class_2583.field_24360, (index, style, codePoint) -> {
            if (runStyle[0] != null && !style.equals(runStyle[0])) {
                out.method_10852(class_2561.method_43470(run.toString()).method_10862(runStyle[0]));
                run.setLength(0);
            }
            runStyle[0] = style;
            run.appendCodePoint(codePoint);
            return true;
        });
        if (!run.isEmpty()) {
            out.method_10852(class_2561.method_43470(run.toString()).method_10862(runStyle[0]));
        }
        return out;
    }

    private static class_5250 replace(class_2561 line, List<int[]> ranges, String text) {
        if (line.getString().isEmpty()) {
            return class_2561.method_43470(text);
        }
        class_5250 out = class_2561.method_43473();
        int[] pos = new int[] {0};
        int[] next = new int[] {0};
        line.method_27658((style, piece) -> {
            int start = pos[0];
            int end = start + piece.length();
            int cursor = start;
            while (cursor < end) {
                int[] range = next[0] < ranges.size() ? ranges.get(next[0]) : null;
                if (range == null || range[0] >= end) {
                    out.method_10852(class_2561.method_43470(piece.substring(cursor - start)).method_10862(style));
                    cursor = end;
                } else if (cursor < range[0]) {
                    out.method_10852(class_2561.method_43470(piece.substring(cursor - start, range[0] - start)).method_10862(style));
                    cursor = range[0];
                } else {
                    if (cursor == range[0]) {
                        out.method_10852(class_2561.method_43470(text).method_10862(style));
                    }
                    cursor = Math.min(end, range[1]);
                    if (cursor == range[1]) {
                        next[0]++;
                    }
                }
            }
            pos[0] = end;
            return Optional.empty();
        }, class_2583.field_24360);
        return out;
    }

    private class_2561 mask(class_2561 line, int row) {
        if (line == null) {
            return class_2561.method_43473();
        }
        class_5250 out = class_2561.method_43473();
        boolean[] changed = new boolean[] {false};
        int[] number = new int[] {0};
        line.method_27658((style, text) -> {
            String shown = FakeStatsLogic.maskText(this.seed, text, row, number);
            if (!shown.equals(text)) {
                changed[0] = true;
            }
            out.method_10852(class_2561.method_43470(shown).method_10862(style));
            return Optional.empty();
        }, class_2583.field_24360);
        return changed[0] ? out : line;
    }
}
