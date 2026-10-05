package dev.dihclient.glue;

import dev.dihclient.hud.HudManager;
import dev.dihclient.render.Gfx;
import dev.dihclient.update.GithubReleases;
import dev.dihclient.update.UpdateManager;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.class_11908;
import net.minecraft.class_11909;
import net.minecraft.class_2561;
import net.minecraft.class_332;
import net.minecraft.class_437;

/** Asks the player whether DIHClient should update itself. */
public class UpdateScreen extends class_437 {
    private static final int W = 340;
    private static final int BTN_H = 20;
    private static final int MAX_NOTE_LINES = 7;

    private final class_437 parent;

    public UpdateScreen(class_437 parent) {
        super(class_2561.method_43470("DIHClient update"));
        this.parent = parent;
    }

    public boolean method_25421() {
        return false;
    }

    private int x() {
        return this.field_22789 / 2 - W / 2;
    }

    private List<String> notes(GithubReleases.Release r) {
        List<String> out = new ArrayList<>();
        if (r.notes() == null) {
            return out;
        }
        for (String raw : r.notes().split("\\r?\\n")) {
            String line = raw.replaceAll("^[#>\\s*-]+", "").replace("`", "").replace("**", "").trim();
            if (line.isEmpty()) {
                continue;
            }
            while (!line.isEmpty() && out.size() < MAX_NOTE_LINES) {
                int cut = line.length();
                while (cut > 1 && Gfx.width(line.substring(0, cut)) > W - 24) {
                    cut--;
                }
                out.add(line.substring(0, cut));
                line = line.substring(cut).trim();
            }
            if (out.size() >= MAX_NOTE_LINES) {
                break;
            }
        }
        return out;
    }

    /** The button captions: the first one opens the release page on GitHub (nothing is downloaded by the game). */
    private List<String> buttons() {
        List<String> b = new ArrayList<>();
        if (UpdateManager.state() == UpdateManager.State.AVAILABLE) {
            b.add("Open GitHub");
            b.add("Later");
            b.add("Skip this version");
        } else {
            b.add("Close");
        }
        return b;
    }

    public void method_25394(class_332 g, int mx, int my, float delta) {
        super.method_25394(g, mx, my, delta);
        GithubReleases.Release r = UpdateManager.release();
        if (r == null) {
            return;
        }
        int accent = HudManager.accent();
        List<String> notes = notes(r);
        int h = 112 + notes.size() * 11;
        int x = x();
        int y = Math.max(10, this.field_22790 / 2 - h / 2);
        Gfx.panel(g, x - 8, y - 8, W + 16, h + 16, accent);
        Gfx.text(g, "DIHClient update", x + 4, y + 2, accent);
        Gfx.text(g, UpdateManager.currentVersion().display() + "  ->  " + r.version().display(), x + 4, y + 18, -1);
        Gfx.text(g, Gfx.trim(r.title(), W - 8), x + 4, y + 32, -7564380);
        int ny = y + 48;
        for (String n : notes) {
            Gfx.text(g, n, x + 4, ny, -3355444);
            ny += 11;
        }
        int by = y + h - BTN_H - 4;
        Gfx.text(g, "Get the new version from the GitHub page and put it in your mods folder.", x + 4, by - 16, -7564380);
        List<String> btn = buttons();
        int bw = (W - 8 - (btn.size() - 1) * 6) / Math.max(1, btn.size());
        for (int i = 0; i < btn.size(); i++) {
            int bx = x + 4 + i * (bw + 6);
            boolean hover = Gfx.inside(mx, my, bx, by, bw, BTN_H);
            Gfx.round(g, bx, by, bw, BTN_H, hover ? accent : (i == 0 ? -14868182 : -14079703));
            Gfx.textCentered(g, btn.get(i), bx + bw / 2, by + 6, -1);
        }
    }

    public boolean method_25402(class_11909 click, boolean doubled) {
        GithubReleases.Release r = UpdateManager.release();
        if (r == null) {
            return super.method_25402(click, doubled);
        }
        List<String> btn = buttons();
        int notesN = notes(r).size();
        int h = 112 + notesN * 11;
        int y = Math.max(10, this.field_22790 / 2 - h / 2);
        int by = y + h - BTN_H - 4;
        int bw = (W - 8 - (btn.size() - 1) * 6) / Math.max(1, btn.size());
        for (int i = 0; i < btn.size(); i++) {
            int bx = x() + 4 + i * (bw + 6);
            if (Gfx.inside(click.comp_4798(), click.comp_4799(), bx, by, bw, BTN_H)) {
                press(btn.get(i));
                return true;
            }
        }
        return true;
    }

    private void press(String caption) {
        switch (caption) {
            case "Open GitHub" -> {
                openRelease();
                this.method_25419();
            }
            case "Skip this version" -> {
                UpdateManager.skipRelease();
                this.method_25419();
            }
            default -> this.method_25419();
        }
    }

    /** Opens the release page in the browser, only if it is an address on github.com. */
    private static void openRelease() {
        GithubReleases.Release r = UpdateManager.release();
        if (r == null || r.pageUrl() == null || !r.pageUrl().startsWith("https://github.com/")) {
            return;
        }
        try {
            net.minecraft.class_156.method_668().method_673(java.net.URI.create(r.pageUrl()));
        } catch (RuntimeException ignored) {
            // no browser: the player can find the page himself
        }
    }

    public boolean method_25404(class_11908 key) {
        if (key.comp_4795() == 256) {
            this.method_25419();
            return true;
        }
        return super.method_25404(key);
    }

    public void method_25419() {
        UpdateManager.dismiss();
        if (this.field_22787 != null) {
            this.field_22787.method_1507(this.parent);
        }
    }
}
