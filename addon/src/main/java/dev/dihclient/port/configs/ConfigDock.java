package dev.dihclient.port.configs;

import dev.dihclient.hud.HudManager;
import dev.dihclient.render.Gfx;
import dev.dihclient.util.ColorUtil;
import dev.dihclient.util.ConfigShare;
import dev.dihclient.util.Notifications;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import net.minecraft.class_310;
import net.minecraft.class_332;

/**
 * The config panel at the bottom centre of the DIH GUI: a small tab that opens upwards. It lists named configs (save,
 * load, overwrite, rename, delete), shares and imports codes, and binds a config to the servers it should load on.
 * It draws in screen pixels on top of the GUI and swallows the clicks that land on it.
 */
public final class ConfigDock {
    private enum Input { NONE, NEW, RENAME, SERVER }

    private record Btn(String id, int x, int y, int w, int h, String label, boolean enabled) {
    }

    private static final int W = 480;
    private static final int H = 206;
    private static final int ROW = 20;
    private static final int LIST_ROWS = 6;
    private static final int TAB_W = 120;
    private static final int TAB_H = 16;

    private static boolean open;
    private static int selected = -1;
    private static int scroll;
    private static Input input = Input.NONE;
    private static String text = "";
    private static int confirmDelete = -1;
    private static boolean swallowRelease;

    private ConfigDock() {
    }

    public static boolean typing() {
        return input != Input.NONE;
    }

    // ---------------------------------------------------------------- geometry

    private static int panelX(int sw) {
        return (sw - W) / 2;
    }

    private static int panelY(int sh) {
        return sh - H - TAB_H - 6;
    }

    private static boolean inTab(double mx, double my, int sw, int sh) {
        return Gfx.inside(mx, my, (sw - TAB_W) / 2, sh - TAB_H - 2, TAB_W, TAB_H);
    }

    private static boolean inPanel(double mx, double my, int sw, int sh) {
        return open && Gfx.inside(mx, my, panelX(sw), panelY(sh), W, H);
    }

    public static boolean covers(double mx, double my, int sw, int sh) {
        return inTab(mx, my, sw, sh) || inPanel(mx, my, sw, sh);
    }

    private static ConfigStore.Entry current() {
        return selected < 0 ? null : ConfigStore.class.cast(Configs.store()).byId(selected);
    }

    private static List<Btn> buttons(int sw, int sh) {
        List<Btn> out = new ArrayList<>();
        if (!open) {
            return out;
        }
        int x = panelX(sw);
        int y = panelY(sh);
        ConfigStore.Entry e = current();
        int rx = x + 200;
        if (input != Input.NONE) {
            out.add(new Btn("ok", rx, y + 96, 80, 20, "OK", !text.isBlank()));
            out.add(new Btn("cancel", rx + 88, y + 96, 80, 20, "Cancel", true));
            if (input == Input.SERVER) {
                out.add(new Btn("paste", rx + 176, y + 96, 80, 20, "Paste", true));
            }
            return out;
        }
        out.add(new Btn("new", x + 10, y + 26 + LIST_ROWS * ROW + 6, 180, 20, "+ Save current as new config", true));
        boolean has = e != null;
        int bw = 63;
        out.add(new Btn("load", rx, y + 52, bw, 18, "Load", has));
        out.add(new Btn("overwrite", rx + (bw + 6), y + 52, bw, 18, "Overwrite", has));
        out.add(new Btn("rename", rx + 2 * (bw + 6), y + 52, bw, 18, "Rename", has));
        out.add(new Btn("delete", rx + 3 * (bw + 6), y + 52, bw, 18, has && confirmDelete == e.id ? "Sure?" : "Delete", has));
        int sw3 = 86;
        out.add(new Btn("share", rx, y + 76, sw3, 18, "Share code", true));
        out.add(new Btn("import", rx + (sw3 + 6), y + 76, sw3, 18, "Import code", true));
        out.add(new Btn("importall", rx + 2 * (sw3 + 6), y + 76, sw3, 18, "Import all", true));
        if (has) {
            String server = Configs.currentServer();
            out.add(new Btn("thisserver", rx, y + H - 28, 130, 18, server == null ? "+ This server" : "+ " + shorten(ConfigStore.normalize(server), 14), server != null));
            out.add(new Btn("address", rx + 136, y + H - 28, 130, 18, "+ Address…", true));
        }
        return out;
    }

    private static String shorten(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }

    // ---------------------------------------------------------------- drawing

    public static void draw(class_332 g, int mx, int my, int sw, int sh) {
        int accent = HudManager.accent();
        int tx = (sw - TAB_W) / 2;
        int ty = sh - TAB_H - 2;
        boolean hover = inTab(mx, my, sw, sh);
        Gfx.round(g, tx, ty, TAB_W, TAB_H, open || hover ? ColorUtil.withAlpha(accent, 200) : -14079703);
        Gfx.textCentered(g, open ? "Configs  ▼" : "Configs  ▲", sw / 2, ty + 4, -1);
        if (!open) {
            return;
        }
        ConfigStore store = Configs.store();
        int x = panelX(sw);
        int y = panelY(sh);
        Gfx.panel(g, x, y, W, H, accent);
        Gfx.text(g, "Configs", x + 10, y + 8, accent);
        Gfx.text(g, "double-click loads", x + 70, y + 8, -7564380);

        List<ConfigStore.Entry> list = store.list();
        if (list.isEmpty()) {
            Gfx.text(g, "No configs yet.", x + 10, y + 32, -7564380);
        }
        for (int i = 0; i < LIST_ROWS && scroll + i < list.size(); i++) {
            ConfigStore.Entry entry = list.get(scroll + i);
            int ry = y + 26 + i * ROW;
            boolean over = Gfx.inside(mx, my, x + 10, ry, 180, ROW - 2);
            boolean picked = entry.id == selected;
            Gfx.round(g, x + 10, ry, 180, ROW - 2, picked ? ColorUtil.withAlpha(accent, 90) : (over ? 822083583 : 419430399));
            Gfx.text(g, shorten(entry.name, 22), x + 16, ry + 5, -1);
            if (!entry.servers.isEmpty()) {
                Gfx.text(g, "●", x + 178, ry + 5, accent);
            }
        }

        int rx = x + 200;
        ConfigStore.Entry e = current();
        if (input != Input.NONE) {
            String prompt = switch (input) {
                case NEW -> "Name for the new config:";
                case RENAME -> "New name:";
                default -> "Server address (e.g. play.example.net):";
            };
            Gfx.text(g, prompt, rx, y + 56, -1446670);
            Gfx.round(g, rx, y + 70, 270, 20, -14868182);
            boolean caret = (System.currentTimeMillis() / 500) % 2 == 0;
            Gfx.text(g, Gfx.trim(text + (caret ? "_" : ""), 258), rx + 6, y + 76, -1);
        } else if (e == null) {
            Gfx.text(g, "Pick a config on the left, or save the", rx, y + 30, -7564380);
            Gfx.text(g, "current settings as a new one.", rx, y + 42, -7564380);
        } else {
            Gfx.text(g, shorten(e.name, 30), rx, y + 26, -1);
            String when = e.saved > 0 ? new SimpleDateFormat("dd.MM.yyyy HH:mm").format(new Date(e.saved)) : "—";
            Gfx.text(g, "saved " + when, rx, y + 38, -7564380);
            Gfx.text(g, "Loads automatically when you join:", rx, y + 102, -1446670);
            if (e.servers.isEmpty()) {
                Gfx.text(g, "no server", rx, y + 116, -7564380);
            }
            for (int i = 0; i < Math.min(3, e.servers.size()); i++) {
                int sy = y + 116 + i * 14;
                boolean over = Gfx.inside(mx, my, rx, sy - 1, 270, 13);
                Gfx.text(g, shorten(e.servers.get(i), 36), rx + 4, sy, over ? Gfx.RED : -1);
                Gfx.text(g, over ? "click to remove" : "✕", rx + 200, sy, over ? Gfx.RED : -7564380);
            }
            if (e.servers.size() > 3) {
                Gfx.text(g, "+" + (e.servers.size() - 3) + " more", rx + 4, y + 158, -7564380);
            }
        }
        for (Btn b : buttons(sw, sh)) {
            boolean over = b.enabled() && Gfx.inside(mx, my, b.x(), b.y(), b.w(), b.h());
            boolean danger = b.id().equals("delete") && b.label().equals("Sure?");
            int fill = !b.enabled() ? -13421773 : (danger ? Gfx.RED : (over ? ColorUtil.withAlpha(accent, 150) : -14079703));
            Gfx.round(g, b.x(), b.y(), b.w(), b.h(), fill);
            Gfx.textCentered(g, b.label(), b.x() + b.w() / 2, b.y() + (b.h() - 8) / 2, b.enabled() ? -1 : -9539986);
        }
    }

    // ---------------------------------------------------------------- input

    /** @return true when the click belonged to the dock (the GUI must not see it) */
    public static boolean click(double mx, double my, boolean doubled, int sw, int sh) {
        if (inTab(mx, my, sw, sh)) {
            open = !open;
            input = Input.NONE;
            confirmDelete = -1;
            swallowRelease = true;
            return true;
        }
        if (!inPanel(mx, my, sw, sh)) {
            return false;
        }
        swallowRelease = true;
        for (Btn b : buttons(sw, sh)) {
            if (b.enabled() && Gfx.inside(mx, my, b.x(), b.y(), b.w(), b.h())) {
                press(b.id());
                return true;
            }
        }
        if (input != Input.NONE) {
            return true;
        }
        int x = panelX(sw);
        int y = panelY(sh);
        List<ConfigStore.Entry> list = Configs.store().list();
        for (int i = 0; i < LIST_ROWS && scroll + i < list.size(); i++) {
            if (Gfx.inside(mx, my, x + 10, y + 26 + i * ROW, 180, ROW - 2)) {
                ConfigStore.Entry entry = list.get(scroll + i);
                boolean again = entry.id == selected;
                selected = entry.id;
                confirmDelete = -1;
                if (doubled && again) {
                    press("load");
                }
                return true;
            }
        }
        ConfigStore.Entry e = current();
        if (e != null) {
            for (int i = 0; i < Math.min(3, e.servers.size()); i++) {
                if (Gfx.inside(mx, my, x + 200, y + 116 + i * 14 - 1, 270, 13)) {
                    Configs.store().removeServer(e, e.servers.get(i));
                    return true;
                }
            }
        }
        return true;
    }

    /** The mouse button went up (or moved while held) after the dock swallowed the press. */
    public static boolean swallowRelease() {
        boolean was = swallowRelease;
        swallowRelease = false;
        return was;
    }

    public static boolean scroll(double mx, double my, double amount, int sw, int sh) {
        if (!inPanel(mx, my, sw, sh)) {
            return false;
        }
        int max = Math.max(0, Configs.store().list().size() - LIST_ROWS);
        scroll = Math.max(0, Math.min(max, scroll - (int) Math.signum(amount)));
        return true;
    }

    private static void press(String id) {
        ConfigStore store = Configs.store();
        ConfigStore.Entry e = current();
        if (!id.equals("delete")) {
            confirmDelete = -1;
        }
        switch (id) {
            case "new" -> start(Input.NEW, "");
            case "rename" -> start(Input.RENAME, e == null ? "" : e.name);
            case "address" -> start(Input.SERVER, "");
            case "cancel" -> input = Input.NONE;
            case "paste" -> text = clipboard();
            case "ok" -> confirm();
            case "load" -> {
                if (e != null) {
                    boolean ok = store.apply(e);
                    Notifications.push("Configs", ok ? "Loaded \"" + e.name + "\"" : "\"" + e.name + "\" could not be loaded",
                            ok ? Notifications.Type.SUCCESS : Notifications.Type.WARNING);
                }
            }
            case "overwrite" -> {
                if (e != null) {
                    boolean ok = store.overwrite(e, System.currentTimeMillis());
                    Notifications.push("Configs", ok ? "Saved current settings into \"" + e.name + "\"" : "Saving failed",
                            ok ? Notifications.Type.SUCCESS : Notifications.Type.ERROR);
                }
            }
            case "delete" -> {
                if (e != null) {
                    if (confirmDelete == e.id) {
                        store.delete(e);
                        selected = -1;
                        confirmDelete = -1;
                        scroll = Math.max(0, Math.min(scroll, Math.max(0, store.list().size() - LIST_ROWS)));
                    } else {
                        confirmDelete = e.id;
                    }
                }
            }
            case "share" -> ConfigShare.export();
            case "import" -> ConfigShare.importClipboard(false);
            case "importall" -> ConfigShare.importClipboard(true);
            case "thisserver" -> {
                String server = Configs.currentServer();
                if (e != null && server != null && store.addServer(e, server)) {
                    Notifications.push("Configs", "\"" + e.name + "\" loads on " + ConfigStore.normalize(server), Notifications.Type.SUCCESS);
                }
            }
            default -> {
            }
        }
    }

    private static void start(Input kind, String initial) {
        input = kind;
        text = initial;
    }

    private static void confirm() {
        ConfigStore store = Configs.store();
        String value = text.trim();
        if (value.isEmpty()) {
            return;
        }
        ConfigStore.Entry e = current();
        switch (input) {
            case NEW -> {
                ConfigStore.Entry made = store.add(value, System.currentTimeMillis());
                if (made != null) {
                    selected = made.id;
                    scroll = Math.max(0, store.list().size() - LIST_ROWS);
                    Notifications.push("Configs", "Saved \"" + made.name + "\"", Notifications.Type.SUCCESS);
                } else {
                    Notifications.push("Configs", "Saving failed", Notifications.Type.ERROR);
                }
            }
            case RENAME -> {
                if (e != null) {
                    store.rename(e, value);
                }
            }
            case SERVER -> {
                if (e != null && !store.addServer(e, value)) {
                    Notifications.push("Configs", "That is not a server address", Notifications.Type.WARNING);
                    return; // stays in the input so it can be corrected
                }
            }
            default -> {
            }
        }
        input = Input.NONE;
        text = "";
    }

    private static String clipboard() {
        try {
            String s = class_310.method_1551().field_1774.method_1460();
            return s == null ? "" : s.trim();
        } catch (Throwable t) {
            return "";
        }
    }

    /** @return true when the key was used (typing mode swallows every key) */
    public static boolean key(int code, int modifiers) {
        if (input == Input.NONE) {
            return false;
        }
        if (code == 259) { // backspace
            if (!text.isEmpty()) {
                text = text.substring(0, text.length() - 1);
            }
        } else if (code == 257 || code == 335) { // enter
            confirm();
        } else if (code == 256) { // escape
            input = Input.NONE;
        } else if (code == 86 && (modifiers & 2) != 0) { // ctrl+V
            text = clipboard();
        }
        return true;
    }

    public static boolean typed(String chars) {
        if (input == Input.NONE) {
            return false;
        }
        if (chars != null && !chars.equals("\n") && text.length() < 64) {
            text = text + chars;
        }
        return true;
    }
}
