package dev.dihclient.gui;

import dev.dihclient.account.Account;
import dev.dihclient.account.AccountManager;
import dev.dihclient.account.AuthApi;
import dev.dihclient.hud.HudManager;
import dev.dihclient.render.Gfx;
import dev.dihclient.util.ColorUtil;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import net.minecraft.class_11905;
import net.minecraft.class_11908;
import net.minecraft.class_11909;
import net.minecraft.class_156;
import net.minecraft.class_2561;
import net.minecraft.class_310;
import net.minecraft.class_332;
import net.minecraft.class_437;

/**
 * The account menu behind the title screen button. A list of saved logins, and "Add account" opens the choice of login
 * types: Microsoft (code in the browser), Cracked (just a name), Session token, Refresh token.
 */
public class AccountsScreen extends class_437 implements TextInputScreen {
    private enum View { LIST, CHOOSE, INPUT, MICROSOFT }

    private enum Input { NAME, SESSION, REFRESH }

    private record Btn(String id, int x, int y, int w, int h, String label, boolean enabled) {
    }

    private static final int ROW_H = 24;
    private static final String[] ADJ = {"Swift", "Quiet", "Brave", "Lucky", "Sneaky", "Silent", "Cosmic", "Mighty", "Tiny", "Wild"};
    private static final String[] NOUN = {"Fox", "Wolf", "Panda", "Falcon", "Golem", "Creeper", "Otter", "Raven", "Tiger", "Miner"};

    private final class_437 parent;
    private final AccountManager manager = AccountManager.get();
    private View view = View.LIST;
    private Input inputKind;
    private String input = "";
    private String error = "";
    private int scroll;
    private int selected = -1;

    public AccountsScreen(class_437 parent) {
        super(class_2561.method_43470("DIHClient Accounts"));
        this.parent = parent;
    }

    @Override
    public boolean isTyping() {
        return this.view == View.INPUT;
    }

    public boolean method_25421() {
        return false;
    }

    private int panelW() {
        return Math.min(360, this.field_22789 - 24);
    }

    private int panelX() {
        return (this.field_22789 - this.panelW()) / 2;
    }

    private int listY() {
        return 58;
    }

    private int visibleRows() {
        return Math.max(1, (this.field_22790 - this.listY() - 96) / ROW_H);
    }

    private boolean selectionValid() {
        return this.selected >= 0 && this.selected < this.manager.accounts().size();
    }

    // ---------------------------------------------------------------- layout (one place for drawing and clicking)

    private List<Btn> buttons() {
        List<Btn> out = new ArrayList<>();
        int x = this.panelX();
        int w = this.panelW();
        int bottom = this.field_22790 - 66;
        switch (this.view) {
            case LIST -> {
                int bw = (w - 18) / 4;
                out.add(new Btn("add", x, bottom, bw, 20, "Add account", !this.manager.busy()));
                out.add(new Btn("login", x + bw + 6, bottom, bw, 20, "Log in", this.selectionValid() && !this.manager.busy()));
                out.add(new Btn("remove", x + 2 * (bw + 6), bottom, bw, 20, "Remove", this.selectionValid()));
                out.add(new Btn("original", x + 3 * (bw + 6), bottom, bw, 20, "Original", true));
            }
            case CHOOSE -> {
                String[] ids = {"microsoft", "cracked", "session", "refresh"};
                String[] names = {"Microsoft", "Cracked", "Session token", "Refresh token"};
                for (int i = 0; i < ids.length; i++) {
                    out.add(new Btn(ids[i], x, 58 + i * 50, w, 44, names[i], true));
                }
                out.add(new Btn("back", x, bottom, w, 20, "Back", true));
            }
            case INPUT -> {
                boolean name = this.inputKind == Input.NAME;
                int bw = (w - (name ? 18 : 12)) / (name ? 4 : 3);
                int i = 0;
                out.add(new Btn("paste", x + i++ * (bw + 6), 112, bw, 20, "Paste", true));
                if (name) {
                    out.add(new Btn("random", x + i++ * (bw + 6), 112, bw, 20, "Random", true));
                }
                out.add(new Btn("confirm", x + i++ * (bw + 6), 112, bw, 20, name ? "Log in" : "Add", !this.input.isBlank()));
                out.add(new Btn("cancel", x + i * (bw + 6), 112, bw, 20, "Back", true));
            }
            case MICROSOFT -> {
                int bw = (w - 12) / 3;
                boolean ready = this.manager.pendingCode() != null;
                out.add(new Btn("open", x, 130, bw, 20, "Open page", ready));
                out.add(new Btn("copy", x + bw + 6, 130, bw, 20, "Copy code", ready));
                out.add(new Btn("cancel", x + 2 * (bw + 6), 130, bw, 20, "Cancel", true));
            }
        }
        return out;
    }

    // ---------------------------------------------------------------- drawing

    public void method_25394(class_332 g, int mx, int my, float delta) {
        super.method_25394(g, mx, my, delta);
        int accent = HudManager.accent();
        int x = this.panelX();
        int w = this.panelW();
        Gfx.panel(g, x - 8, 12, w + 16, this.field_22790 - 24, accent);
        Gfx.text(g, this.title(), x, 20, accent);
        Gfx.text(g, "Playing as: " + this.currentName(), x, 34, -7564380);

        switch (this.view) {
            case LIST -> this.drawList(g, mx, my, x, w);
            case CHOOSE -> this.drawChoose(g, mx, my, x, w);
            case INPUT -> this.drawInput(g, x, w);
            case MICROSOFT -> this.drawMicrosoft(g, x, w, accent);
        }
        for (Btn b : this.buttons()) {
            if (this.view == View.CHOOSE && !b.id().equals("back")) {
                continue; // the cards draw themselves
            }
            this.drawButton(g, mx, my, b);
        }

        Gfx.round(g, x, this.field_22790 - 40, w, 16, -14868182);
        String status = this.manager.status().isEmpty() ? "Ready" : this.manager.status();
        Gfx.text(g, Gfx.trim(status, w - 12), x + 6, this.field_22790 - 36, this.manager.busy() ? accent : -7564380);
        Gfx.textCentered(g, "Esc = back", this.field_22789 / 2, this.field_22790 - 18, -7564380);
    }

    private String title() {
        return switch (this.view) {
            case LIST -> "Accounts";
            case CHOOSE -> "Add account · choose how to log in";
            case INPUT -> switch (this.inputKind) {
                case NAME -> "Cracked account";
                case SESSION -> "Session token";
                case REFRESH -> "Refresh token";
            };
            case MICROSOFT -> "Microsoft login";
        };
    }

    private String currentName() {
        try {
            return class_310.method_1551().method_1548().method_1676();
        } catch (Throwable t) {
            return "?";
        }
    }

    private void drawButton(class_332 g, int mx, int my, Btn b) {
        boolean hover = b.enabled() && Gfx.inside(mx, my, b.x(), b.y(), b.w(), b.h());
        Gfx.round(g, b.x(), b.y(), b.w(), b.h(), b.enabled() ? (hover ? ColorUtil.withAlpha(HudManager.accent(), 150) : -14079703) : -13421773);
        Gfx.textCentered(g, b.label(), b.x() + b.w() / 2, b.y() + (b.h() - 8) / 2, b.enabled() ? -1 : -9539986);
    }

    private void drawList(class_332 g, int mx, int my, int x, int w) {
        List<Account> accounts = this.manager.accounts();
        if (accounts.isEmpty()) {
            Gfx.text(g, "No accounts yet – press \"Add account\".", x, this.listY() + 6, -7564380);
        }
        int rows = this.visibleRows();
        for (int i = 0; i < rows && this.scroll + i < accounts.size(); i++) {
            int index = this.scroll + i;
            Account a = accounts.get(index);
            int y = this.listY() + i * ROW_H;
            boolean hover = Gfx.inside(mx, my, x, y, w, ROW_H - 2);
            boolean picked = index == this.selected;
            Gfx.round(g, x, y, w, ROW_H - 2, picked ? ColorUtil.withAlpha(HudManager.accent(), 90) : (hover ? 822083583 : 419430399));
            Gfx.text(g, a.name, x + 8, y + 3, -1);
            Gfx.text(g, a.label() + "  ·  " + a.uuid.toString().substring(0, 8), x + 8, y + 13, -7564380);
        }
    }

    private void drawChoose(class_332 g, int mx, int my, int x, int w) {
        String[] details = {
                "Sign in with a code at microsoft.com/link. Works everywhere (recommended).",
                "Only a name. Works on offline-mode servers and in single player, not on online-mode servers.",
                "Paste a Minecraft access token you already have.",
                "Paste a Microsoft refresh token you already have."};
        for (Btn b : this.buttons()) {
            int index = java.util.Arrays.asList("microsoft", "cracked", "session", "refresh").indexOf(b.id());
            if (index < 0) {
                continue;
            }
            boolean hover = Gfx.inside(mx, my, b.x(), b.y(), b.w(), b.h());
            Gfx.round(g, b.x(), b.y(), b.w(), b.h(), hover ? ColorUtil.withAlpha(HudManager.accent(), 120) : -14079703);
            Gfx.text(g, b.label(), b.x() + 10, b.y() + 8, -1);
            Gfx.text(g, Gfx.trim(details[index], b.w() - 20), b.x() + 10, b.y() + 24, -7564380);
        }
    }

    private void drawInput(class_332 g, int x, int w) {
        String prompt = switch (this.inputKind) {
            case NAME -> "Type a player name (3-16 letters, digits or _):";
            case SESSION -> "Paste a Minecraft session (access) token:";
            case REFRESH -> "Paste a Microsoft refresh token:";
        };
        Gfx.text(g, prompt, x, 56, -1446670);
        Gfx.round(g, x, 72, w, 22, -14868182);
        boolean plain = this.inputKind == Input.NAME;
        boolean caret = (System.currentTimeMillis() / 500) % 2 == 0;
        String shown = this.input.isEmpty() ? "click Paste or start typing…" : (plain ? this.input + (caret ? "_" : "") : mask(this.input));
        Gfx.text(g, Gfx.trim(shown, w - 12), x + 6, 79, this.input.isEmpty() ? -7564380 : -1);
        if (!this.error.isEmpty()) {
            Gfx.text(g, this.error, x, 98, Gfx.RED);
        } else if (plain) {
            Gfx.text(g, "Cracked accounts cannot join online-mode servers.", x, 98, -7564380);
        } else {
            Gfx.text(g, "Stored on this computer in dihclient/accounts.json.", x, 98, -7564380);
        }
    }

    private void drawMicrosoft(class_332 g, int x, int w, int accent) {
        AuthApi.DeviceCode code = this.manager.pendingCode();
        if (code == null) {
            Gfx.text(g, this.manager.busy() ? "Contacting Microsoft …" : "Starting …", x, 60, -1446670);
            return;
        }
        Gfx.round(g, x, 54, w, 66, -14868182);
        Gfx.text(g, "1. Open " + code.url(), x + 10, 62, -1446670);
        Gfx.text(g, "2. Enter this code:", x + 10, 76, -1446670);
        Gfx.text(g, code.userCode(), x + 10, 92, accent, 2.0F);
        Gfx.text(g, "3. Come back here – the login finishes by itself.", x + 10, 108, -7564380);
    }

    private static String mask(String value) {
        int n = value.length();
        return n <= 14 ? value.substring(0, Math.min(6, n)) + "…" : value.substring(0, 8) + "…(" + n + " chars)…" + value.substring(n - 4);
    }

    // ---------------------------------------------------------------- clicks

    public boolean method_25402(class_11909 click, boolean doubled) {
        int mx = (int) click.comp_4798();
        int my = (int) click.comp_4799();
        for (Btn b : this.buttons()) {
            if (b.enabled() && Gfx.inside(mx, my, b.x(), b.y(), b.w(), b.h())) {
                this.press(b.id());
                return true;
            }
        }
        if (this.view == View.LIST) {
            List<Account> accounts = this.manager.accounts();
            int x = this.panelX();
            for (int i = 0; i < this.visibleRows() && this.scroll + i < accounts.size(); i++) {
                if (Gfx.inside(mx, my, x, this.listY() + i * ROW_H, this.panelW(), ROW_H - 2)) {
                    int index = this.scroll + i;
                    if (doubled && index == this.selected && !this.manager.busy()) {
                        this.manager.login(accounts.get(index));
                    }
                    this.selected = index;
                    return true;
                }
            }
        }
        return super.method_25402(click, doubled);
    }

    private void press(String id) {
        switch (id) {
            case "add" -> this.view = View.CHOOSE;
            case "back" -> this.view = View.LIST;
            case "login" -> {
                if (this.selectionValid()) {
                    this.manager.login(this.manager.accounts().get(this.selected));
                }
            }
            case "remove" -> {
                if (this.selectionValid()) {
                    this.manager.remove(this.manager.accounts().get(this.selected));
                    this.selected = -1;
                }
            }
            case "original" -> this.manager.restoreOriginal();
            case "microsoft" -> {
                this.view = View.MICROSOFT;
                this.manager.startMicrosoft();
            }
            case "cracked" -> this.startInput(Input.NAME);
            case "session" -> this.startInput(Input.SESSION);
            case "refresh" -> this.startInput(Input.REFRESH);
            case "paste" -> this.paste();
            case "random" -> this.input = new Random().nextBoolean()
                    ? ADJ[new Random().nextInt(ADJ.length)] + NOUN[new Random().nextInt(NOUN.length)] + (10 + new Random().nextInt(90))
                    : NOUN[new Random().nextInt(NOUN.length)] + "_" + (100 + new Random().nextInt(900));
            case "confirm" -> this.confirm();
            case "cancel" -> {
                if (this.view == View.MICROSOFT) {
                    this.manager.cancel();
                }
                this.view = this.view == View.INPUT ? View.CHOOSE : View.LIST;
                this.input = "";
                this.error = "";
            }
            case "open" -> this.openPage();
            case "copy" -> {
                AuthApi.DeviceCode code = this.manager.pendingCode();
                if (code != null && this.field_22787 != null) {
                    this.field_22787.field_1774.method_1455(code.userCode());
                }
            }
            default -> {
            }
        }
    }

    private void startInput(Input kind) {
        this.inputKind = kind;
        this.input = "";
        this.error = "";
        this.view = View.INPUT;
    }

    private void confirm() {
        String value = this.input.trim();
        if (value.isEmpty()) {
            return;
        }
        if (this.inputKind == Input.NAME) {
            if (!value.matches("[A-Za-z0-9_]{3,16}")) {
                this.error = "A name has 3 to 16 letters, digits or _.";
                return;
            }
            this.manager.addOffline(value);
        } else {
            this.manager.addFromToken(this.inputKind == Input.SESSION ? Account.Kind.SESSION : Account.Kind.REFRESH, value);
        }
        this.input = "";
        this.error = "";
        this.view = View.LIST;
    }

    private void openPage() {
        AuthApi.DeviceCode code = this.manager.pendingCode();
        if (code != null) {
            try {
                class_156.method_668().method_670(code.url());
            } catch (Throwable ignored) {
                // the address is on the screen anyway
            }
        }
    }

    private void paste() {
        try {
            String text = class_310.method_1551().field_1774.method_1460();
            if (text != null) {
                this.input = text.trim();
            }
        } catch (Throwable ignored) {
            // no clipboard
        }
    }

    // ---------------------------------------------------------------- keys

    public boolean method_25401(double x, double y, double horizontal, double vertical) {
        int count = this.manager.accounts().size();
        this.scroll = Math.max(0, Math.min(Math.max(0, count - this.visibleRows()), this.scroll - (int) (vertical * 2.0)));
        return true;
    }

    public boolean method_25404(class_11908 key) {
        int code = key.comp_4795();
        if (this.view == View.INPUT) {
            if (code == 259 && !this.input.isEmpty()) { // backspace
                this.input = this.input.substring(0, this.input.length() - 1);
                return true;
            }
            if (code == 257 || code == 335) { // enter
                this.confirm();
                return true;
            }
            if (code == 256) { // escape
                this.press("cancel");
                return true;
            }
            if (code == 86 && (key.comp_4797() & 2) != 0) { // ctrl+V
                this.paste();
                return true;
            }
            return super.method_25404(key);
        }
        if (code == 256) {
            if (this.view == View.MICROSOFT) {
                this.press("cancel");
            } else if (this.view == View.CHOOSE) {
                this.view = View.LIST;
            } else {
                this.method_25419();
            }
            return true;
        }
        return super.method_25404(key);
    }

    public boolean method_25400(class_11905 chars) {
        if (this.view == View.INPUT && chars.method_74227()) {
            String text = chars.method_74226();
            if (text != null && !text.equals("\n") && this.input.length() < 4096) {
                this.input = this.input + text;
            }
            return true;
        }
        return false;
    }

    public void method_25419() {
        if (this.field_22787 != null) {
            this.field_22787.method_1507(this.parent);
        }
    }
}
