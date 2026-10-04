package dev.dihclient.gui;

import dev.dihclient.DIHClient;
import dev.dihclient.glue.GuiSounds;
import dev.dihclient.gui.theme.Anim;
import dev.dihclient.gui.theme.Skin;
import dev.dihclient.module.Category;
import dev.dihclient.module.Module;
import dev.dihclient.module.ModuleManager;
import dev.dihclient.modules.client.ClickGui;
import dev.dihclient.port.configs.ConfigDock;
import dev.dihclient.render.Gfx;
import dev.dihclient.setting.ActionSetting;
import dev.dihclient.setting.BoolSetting;
import dev.dihclient.setting.ColorSetting;
import dev.dihclient.setting.DoubleSetting;
import dev.dihclient.setting.EnumSetting;
import dev.dihclient.setting.IdListSetting;
import dev.dihclient.setting.IntSetting;
import dev.dihclient.setting.Setting;
import dev.dihclient.setting.StringSetting;
import dev.dihclient.util.KeyUtil;
import java.awt.Color;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import net.minecraft.class_11905;
import net.minecraft.class_11908;
import net.minecraft.class_11909;
import net.minecraft.class_1109;
import net.minecraft.class_1113;
import net.minecraft.class_2561;
import net.minecraft.class_310;
import net.minecraft.class_332;
import net.minecraft.class_3417;
import net.minecraft.class_437;

/**
 * The Glass GUI: no window, only see-through cards floating over the blurred game. Left: one card per module (name, switch, gear),
 * right: the settings of the chosen module as plain rows (grey icon, white label, rounded value field on the right).
 * Used whenever the ClickGUI theme is "Glass".
 */
public class GlassGuiScreen extends class_437 implements TextInputScreen {
    private static final int CARD_H = 44;
    private static final int CARD_GAP = 6;
    private static final int ROW_H = 30;
    private static final int FIELD_W = 128;
    private static final int FIELD_H = 20;
    private static final int LIST_TOP = 32;
    private static final int BOTTOM = 26;

    private static final int TEXT = 0xFFF2F2F2;
    private static final int DIM = 0xFFA6A6A6;
    private static final int DIMMER = 0xFF7C7C7C;
    private static final int CARD = 0x1FFFFFFF;
    private static final int CARD_HOVER = 0x2AFFFFFF;
    private static final int CARD_ON = 0x33FFFFFF;
    private static final int CARD_LINE = 0x26FFFFFF;
    private static final int CARD_LINE_ON = 0x55FFFFFF;
    private static final int FIELD = 0x14FFFFFF;
    private static final int FIELD_LINE = 0x33FFFFFF;

    private static Category category;
    private static Module selected;
    private static final Set<ColorSetting> OPEN_COLORS = new HashSet<>();

    private final long openedAt = System.currentTimeMillis();
    private final List<Hit> hits = new ArrayList<>();
    private String search = "";
    private boolean searchFocused;
    private boolean binding;
    private StringSetting editing;
    private Drag drag;
    private String tooltip;
    private double mouseX;
    private double mouseY;
    private int sw;
    private int sh;
    private float fade = 1.0F;
    private float listScroll;
    private float listScrollTarget;
    private float paneScroll;
    private float paneScrollTarget;
    private int listContent;
    private int paneContent;
    private int listW;
    private int paneX;
    private int paneR;

    public GlassGuiScreen() {
        super(class_2561.method_43470("DIHClient"));
    }

    // ---------------------------------------------------------------- switching between the screens

    /** The ClickGUI theme or layout changed while a GUI is open: swap it for the one that fits. */
    public static void layoutChanged() {
        class_310 mc = class_310.method_1551();
        if (mc == null || !isOurs(mc.field_1755)) {
            return;
        }
        mc.execute(() -> {
            if (isOurs(mc.field_1755)) {
                if (DIHClient.config() != null) {
                    DIHClient.config().markDirty();
                }
                mc.method_1507(MeteorGuiScreen.create());
            }
        });
    }

    private static boolean isOurs(class_437 screen) {
        return screen instanceof ClickGuiScreen || screen instanceof MeteorGuiScreen || screen instanceof GlassGuiScreen;
    }

    /** True when the GUI should be the glass one. */
    public static boolean wanted() {
        ClickGui gui = gui();
        return gui != null && gui.layout.get() == ClickGui.Layout.GLASS;
    }

    private static ClickGui gui() {
        return ModuleManager.of(ClickGui.class);
    }

    private static float scale() {
        ClickGui gui = gui();
        return gui == null ? 1.0F : gui.scale.getFloat();
    }

    // ---------------------------------------------------------------- small helpers

    private int c(int argb) {
        int a = (int) ((argb >>> 24) * this.fade);
        return a << 24 | (argb & 0xFFFFFF);
    }

    private void sound() {
        ClickGui gui = gui();
        if (gui != null && gui.clickSound.get() && this.field_22787 != null) {
            this.field_22787.method_1483().method_4873((class_1113) GuiSounds.click(class_3417.field_15015, gui.clickPitch.getFloat()));
        }
    }

    private void hit(int x, int y, int w, int h, int clipTop, int clipBottom, ClickAction action) {
        int top = Math.max(y, clipTop);
        int height = Math.min(y + h, clipBottom) - top;
        if (height > 0 && w > 0) {
            this.hits.add(new Hit(x, top, w, height, action));
        }
    }

    private void hit(int x, int y, int w, int h, ClickAction action) {
        this.hit(x, y, w, h, 0, Integer.MAX_VALUE, action);
    }

    private static String lower(String s) {
        return s.toLowerCase(Locale.ROOT);
    }

    private static String bold(String s) {
        return "§l" + s;
    }

    private List<Category> categories() {
        List<Category> out = new ArrayList<>();
        for (Category cat : Category.values()) {
            if (!this.modulesOf(cat).isEmpty() || this.search.isEmpty() && hasVisible(cat)) {
                out.add(cat);
            }
        }
        return out;
    }

    private static boolean hasVisible(Category cat) {
        for (Module m : DIHClient.modules().byCategory(cat)) {
            if (!m.isHidden()) {
                return true;
            }
        }
        return false;
    }

    private List<Module> modulesOf(Category cat) {
        List<Module> out = new ArrayList<>();
        String q = lower(this.search);
        for (Module m : DIHClient.modules().byCategory(cat)) {
            if (!m.isHidden() && (q.isEmpty() || lower(m.name()).contains(q) || lower(m.description()).contains(q))) {
                out.add(m);
            }
        }
        return out;
    }

    /** The cards of the left column: the chosen category, or every match while searching. */
    private List<Module> listed() {
        List<Module> out = new ArrayList<>();
        if (this.search.isEmpty()) {
            if (category != null) {
                out.addAll(this.modulesOf(category));
            }
        } else {
            for (Category cat : Category.values()) {
                out.addAll(this.modulesOf(cat));
            }
        }
        return out;
    }

    private void choose(Module m) {
        if (m != null) {
            category = m.category();
        }
        if (selected != m) {
            selected = m;
            this.paneScroll = this.paneScrollTarget = 0.0F;
            this.editing = null;
            this.binding = false;
        }
    }

    @Override
    public boolean isTyping() {
        return this.searchFocused || !this.search.isEmpty() || this.editing != null || this.binding || ConfigDock.typing();
    }

    public boolean method_25421() {
        return false;
    }

    public void method_25419() {
        DIHClient.config().save();
        super.method_25419();
    }

    // ---------------------------------------------------------------- drawing

    public void method_25420(class_332 g, int mx, int my, float delta) {
        ClickGui gui = gui();
        if (gui != null && gui.blur.get()) {
            super.method_25420(g, mx, my, delta);
        }
        float f = Anim.easeOutCubic((System.currentTimeMillis() - this.openedAt) / 250.0F);
        g.method_25296(0, 0, this.field_22789, this.field_22790, (int) (f * 40.0F) << 24 | 0x0A0A14, (int) (f * 95.0F) << 24 | 0x050510);
    }

    public void method_25394(class_332 g, int mx, int my, float delta) {
        super.method_25394(g, mx, my, delta);
        ClickGui gui = gui();
        if (gui != null) {
            Anim.setEnabled(gui.animations.get());
        }
        this.hits.clear();
        this.tooltip = null;
        float scale = scale();
        this.sw = (int) (this.field_22789 / scale);
        this.sh = (int) (this.field_22790 / scale);
        this.mouseX = mx / scale;
        this.mouseY = my / scale;
        this.fade = Anim.easeOutCubic((System.currentTimeMillis() - this.openedAt) / 220.0F);
        this.listW = this.sw < 700 ? 190 : 248;
        this.paneX = 12 + this.listW + 30;
        this.paneR = this.sw - 27;

        List<Category> cats = this.categories();
        if (category == null || !cats.contains(category) && this.search.isEmpty()) {
            category = cats.isEmpty() ? null : cats.get(0);
        }
        List<Module> cards = this.listed();
        if (selected == null || selected.isHidden() || !cards.contains(selected) && !this.search.isEmpty() && !cards.isEmpty()) {
            selected = cards.isEmpty() ? null : cards.get(0);
        } else if (selected.category() != category && this.search.isEmpty() && category != null) {
            selected = cards.isEmpty() ? null : cards.get(0);
        }

        g.method_51448().pushMatrix();
        g.method_51448().scale(scale, scale);
        this.drawTabs(g, cats);
        this.drawSearch(g);
        this.drawList(g, cards);
        this.drawPane(g);
        this.drawBottom(g);
        this.drawTooltip(g);
        g.method_51448().popMatrix();

        Skin.begin();
        try {
            ConfigDock.draw(g, mx, my, this.field_22789, this.field_22790);
        } finally {
            Skin.end();
        }
    }

    private void drawTabs(class_332 g, List<Category> cats) {
        int total = -6;
        for (Category cat : cats) {
            total += Gfx.width(cat.title) + 22 + 6;
        }
        int x = Math.max(12, (this.sw - total) / 2);
        int y = 7;
        for (Category cat : cats) {
            int w = Gfx.width(cat.title) + 22;
            boolean on = cat == category && this.search.isEmpty();
            boolean over = Gfx.inside(this.mouseX, this.mouseY, x, y, w, 16);
            float a = Anim.get(cat, "gTab", on ? 1.0F : over ? 0.5F : 0.0F, 16.0F);
            Gfx.rect(g, x, y, w, 16, 8, this.c(blendAlpha(0x00FFFFFF, 0x38FFFFFF, a)));
            if (a > 0.05F) {
                Gfx.outline(g, x, y, w, 16, 8, this.c(blendAlpha(0x00FFFFFF, 0x44FFFFFF, a)));
            }
            Gfx.text(g, cat.title, x + 11, y + 4, this.c(on ? TEXT : over ? 0xFFD0D0D0 : DIM));
            final Category target = cat;
            this.hit(x, y, w, 16, (cx, cy, b) -> {
                category = target;
                this.search = "";
                this.listScroll = this.listScrollTarget = 0.0F;
                List<Module> now = this.modulesOf(target);
                this.choose(now.isEmpty() ? null : now.get(0));
                this.sound();
            });
            x += w + 6;
            if (x > this.sw - 160) {
                break;
            }
        }
    }

    private void drawSearch(class_332 g) {
        int w = 124;
        int x = this.sw - 12 - w;
        int y = 7;
        float a = Anim.get(this, "gSearch", this.searchFocused || !this.search.isEmpty() ? 1.0F : 0.0F, 14.0F);
        Gfx.rect(g, x, y, w, 16, 8, this.c(blendAlpha(0x14FFFFFF, 0x2EFFFFFF, a)));
        Gfx.outline(g, x, y, w, 16, 8, this.c(blendAlpha(0x26FFFFFF, 0x66FFFFFF, a)));
        String shown = this.search.isEmpty() ? "Search…" : this.search + (this.searchFocused && System.currentTimeMillis() / 500L % 2L == 0L ? "_" : "");
        Gfx.text(g, "⌕", x + 7, y + 4, this.c(DIM));
        Gfx.text(g, Gfx.trim(shown, w - 28), x + 18, y + 4, this.c(this.search.isEmpty() ? DIMMER : TEXT));
        this.hit(x, y, w, 16, (cx, cy, b) -> {
            this.searchFocused = true;
            if (b == 1) {
                this.search = "";
            }
        });
    }

    private void drawList(class_332 g, List<Module> cards) {
        int top = LIST_TOP;
        int bottom = this.sh - BOTTOM;
        int stride = CARD_H + CARD_GAP;
        this.listContent = cards.size() * stride;
        int view = bottom - top;
        this.listScrollTarget = Math.max(0.0F, Math.min(this.listScrollTarget, Math.max(0, this.listContent - view)));
        this.listScroll = Anim.get(this, "gListScroll", this.listScrollTarget, 18.0F);
        float slide = (1.0F - this.fade) * -24.0F;
        g.method_44379(0, top - 2, 12 + this.listW + 8, bottom);
        int y = top - (int) this.listScroll;
        int x = 12 + (int) slide;
        for (Module m : cards) {
            if (y + CARD_H >= top - 2 && y <= bottom) {
                this.drawCard(g, m, x, y, top, bottom);
            }
            y += stride;
        }
        g.method_44380();
        if (cards.isEmpty()) {
            Gfx.text(g, "Nothing found", 22, top + 8, this.c(DIMMER));
        }
    }

    private void drawCard(class_332 g, Module m, int x, int y, int clipTop, int clipBottom) {
        int w = this.listW;
        boolean inClip = this.mouseY >= clipTop && this.mouseY < clipBottom;
        boolean over = inClip && Gfx.inside(this.mouseX, this.mouseY, x, y, w, CARD_H);
        boolean chosen = m == selected;
        boolean on = m.isEnabled() && m.isToggleable();
        float hov = Anim.get(m, "gHover", over ? 1.0F : 0.0F, 16.0F);
        float sel = Anim.get(m, "gSel", chosen ? 1.0F : 0.0F, 14.0F);
        float en = Anim.get(m, "gOn", on ? 1.0F : 0.0F, 14.0F);
        int fill = blendAlpha(blendAlpha(CARD, CARD_HOVER, hov), CARD_ON, sel);
        int line = blendAlpha(CARD_LINE, CARD_LINE_ON, sel);
        Gfx.shadow(g, x, y, w, CARD_H, 8, 4, 0.35F);
        Gfx.rect(g, x, y, w, CARD_H, 8, this.c(fill));
        Gfx.outline(g, x, y, w, CARD_H, 8, this.c(line));

        int nameColor = blendColor(blendColor(DIM, 0xFFD8D8D8, hov), TEXT, Math.max(sel, en));
        Gfx.text(g, Gfx.trim(m.name(), this.listW - 120), x + 10, y + CARD_H / 2 - 4, this.c(nameColor));

        if (m.isToggleable()) {
            int px = x + w - 100;
            int py = y + CARD_H / 2 - 8;
            this.drawSwitch(g, px, py, en);
            this.hit(px - 4, py - 4, 40, 24, clipTop, clipBottom, (cx, cy, b) -> {
                m.toggle();
                DIHClient.config().markDirty();
            });
        }
        boolean gearOver = inClip && Gfx.inside(this.mouseX, this.mouseY, x + w - 34, y + 8, 28, CARD_H - 16);
        float gear = Anim.get(m, "gGear", gearOver || chosen ? 1.0F : 0.0F, 16.0F);
        Gfx.text(g, "⚙", x + w - 25, y + CARD_H / 2 - 4, this.c(blendColor(DIMMER, TEXT, gear)));
        this.hit(x + w - 34, y + 8, 28, CARD_H - 16, clipTop, clipBottom, (cx, cy, b) -> {
            this.choose(m);
            this.sound();
        });
        this.hit(x, y, w, CARD_H, clipTop, clipBottom, (cx, cy, b) -> {
            if (b == 2) {
                this.choose(m);
                this.binding = true;
            } else if (b == 1 || !m.isToggleable()) {
                // right click: the settings of the module
                this.choose(m);
                this.sound();
            } else {
                // left click: switch it on / off, like in the other GUIs
                m.toggle();
                DIHClient.config().markDirty();
            }
        });
        if (over) {
            this.tooltip = m.description();
        }
    }

    /** The pill switch: a white knob that slides, on a track that lights up. */
    private void drawSwitch(class_332 g, int x, int y, float on) {
        Gfx.rect(g, x, y, 32, 16, 8, this.c(blendAlpha(0x22FFFFFF, 0x80FFFFFF, on)));
        Gfx.outline(g, x, y, 32, 16, 8, this.c(blendAlpha(0x2EFFFFFF, 0x99FFFFFF, on)));
        Gfx.rect(g, x + 2 + (int) (16 * on), y + 2, 12, 12, 6, this.c(0xFFFFFFFF));
    }

    private void drawPane(class_332 g) {
        Module m = selected;
        int left = this.paneX + (int) ((1.0F - this.fade) * 24.0F);
        int right = this.paneR;
        if (right - left < 200) {
            return;
        }
        if (m == null) {
            Gfx.text(g, "Pick a module", left, LIST_TOP + 12, this.c(DIM));
            return;
        }
        int top = LIST_TOP;
        Gfx.text(g, bold(m.name()), left, top + 2, this.c(TEXT), 1.25F);
        ClickGui gui = gui();
        if ((gui == null || gui.descriptions.get()) && m.description() != null && !m.description().isEmpty()) {
            Gfx.text(g, Gfx.trim(m.description(), (int) ((right - left) / 0.75F)), left, top + 17, this.c(DIMMER), 0.75F);
        }
        int divider = top + 30;
        g.method_25294(left, divider, right, divider + 1, this.c(0x2EFFFFFF));

        int viewTop = divider + 2;
        int viewBottom = this.sh - BOTTOM;
        List<Row> rows = this.rowsOf(m);
        int total = 7;
        for (Row r : rows) {
            total += r.height;
        }
        this.paneContent = total;
        this.paneScrollTarget = Math.max(0.0F, Math.min(this.paneScrollTarget, Math.max(0, total - (viewBottom - viewTop))));
        this.paneScroll = Anim.get(this, "gPaneScroll", this.paneScrollTarget, 18.0F);

        g.method_44379(left - 4, viewTop, right + 4, viewBottom);
        int y = viewTop + 7 - (int) this.paneScroll;
        boolean inClip = this.mouseY >= viewTop && this.mouseY < viewBottom;
        for (Row r : rows) {
            if (y + r.height >= viewTop && y <= viewBottom) {
                this.drawRow(g, r, left, right, y, viewTop, viewBottom, inClip);
            }
            y += r.height;
        }
        g.method_44380();
    }

    private List<Row> rowsOf(Module m) {
        List<Row> rows = new ArrayList<>();
        rows.add(new Row(null, "Keybind", ROW_H));
        if (m.isToggleable()) {
            rows.add(new Row(null, "Toast", ROW_H));
        }
        for (Setting<?> s : m.settings()) {
            if (s.isVisible()) {
                int h = ROW_H;
                if (s instanceof ColorSetting cs && OPEN_COLORS.contains(cs)) {
                    h += 80;
                }
                rows.add(new Row(s, s.name(), h));
            }
        }
        return rows;
    }

    private void drawRow(class_332 g, Row row, int left, int right, int y, int clipTop, int clipBottom, boolean inClip) {
        int mid = y + ROW_H / 2;
        boolean over = inClip && Gfx.inside(this.mouseX, this.mouseY, left, y, right - left, ROW_H);
        int labelColor = over ? 0xFFFFFFFF : TEXT;
        Gfx.rect(g, left + 3, mid - 4, 10, 8, 3, this.c(over ? 0xFFC4C4C4 : 0xFFA8A8A8));
        int fx = right - 4 - FIELD_W;
        int fy = mid - FIELD_H / 2;
        Setting<?> s = row.setting;
        Module m = selected;

        if (s == null) {
            Gfx.text(g, bold(row.label), left + 19, mid - 4, this.c(labelColor));
            if (row.label.equals("Keybind")) {
                String text = this.binding ? "Press a key…" : m.keybind() >= 0 ? KeyUtil.keyName(m.keybind()) : "None";
                this.field(g, fx, fy, this.binding, text, this.binding ? TEXT : DIM, false);
                this.hit(fx, fy, FIELD_W, FIELD_H, clipTop, clipBottom, (cx, cy, b) -> {
                    if (b == 1) {
                        m.setKeybind(-1);
                        this.binding = false;
                    } else {
                        this.binding = true;
                    }
                    this.sound();
                });
            } else {
                float on = Anim.get(row, "gBool", m.showToggleNotification() ? 1.0F : 0.0F, 16.0F);
                this.drawSwitch(g, right - 4 - 32, mid - 8, on);
                this.hit(left, y, right - left, ROW_H, clipTop, clipBottom, (cx, cy, b) -> {
                    m.setShowToggleNotification(!m.showToggleNotification());
                    DIHClient.config().markDirty();
                    this.sound();
                });
            }
            return;
        }

        if (over && s.description() != null && !s.description().isEmpty()) {
            this.tooltip = s.description();
        }
        int labelMax = right - left - 19 - FIELD_W - 16;
        Gfx.text(g, bold(Gfx.trim(row.label, labelMax)), left + 19, mid - 4, this.c(labelColor));

        if (s instanceof BoolSetting b) {
            float on = Anim.get(b, "gBool", b.get() ? 1.0F : 0.0F, 16.0F);
            this.drawSwitch(g, right - 4 - 32, mid - 8, on);
            this.hit(left, y, right - left, ROW_H, clipTop, clipBottom, (cx, cy, btn) -> {
                if (btn == 1) {
                    b.reset();
                } else {
                    b.toggle();
                }
                DIHClient.config().markDirty();
                this.sound();
            });
        } else if (s instanceof DoubleSetting || s instanceof IntSetting) {
            double frac = s instanceof DoubleSetting d ? d.fraction() : ((IntSetting) s).fraction();
            float shown = Anim.get(s, "gSlider", (float) frac, 22.0F);
            this.field(g, fx, fy, false, "", DIM, false);
            int fillW = (int) ((FIELD_W - 2) * shown);
            if (fillW > 2) {
                Gfx.rect(g, fx + 1, fy + 1, fillW, FIELD_H - 2, 4, this.c(0x40FFFFFF));
            }
            String value = s.displayValue();
            Gfx.text(g, value, fx + (FIELD_W - Gfx.width(value)) / 2, fy + 6, this.c(TEXT));
            final int sx = fx;
            this.hit(fx, fy, FIELD_W, FIELD_H, clipTop, clipBottom, (cx, cy, btn) -> {
                if (btn == 1) {
                    s.reset();
                    DIHClient.config().markDirty();
                    return;
                }
                Drag d = (px, py) -> {
                    double fr = (px - sx) / (FIELD_W - 1.0);
                    if (s instanceof DoubleSetting ds) {
                        ds.setFraction(fr);
                    } else {
                        ((IntSetting) s).setFraction(fr);
                    }
                    DIHClient.config().markDirty();
                };
                this.drag = d;
                d.drag(cx, cy);
            });
        } else if (s instanceof EnumSetting<?> e) {
            this.field(g, fx, fy, false, e.displayValue(), DIM, true);
            this.hit(fx, fy, FIELD_W, FIELD_H, clipTop, clipBottom, (cx, cy, btn) -> {
                e.cycle(btn != 1);
                DIHClient.config().markDirty();
                this.sound();
            });
        } else if (s instanceof ColorSetting col) {
            int sx = right - 4 - 38;
            Gfx.rect(g, sx, fy, 38, FIELD_H, 5, this.c(0xFF3A3A3A));
            Gfx.rect(g, sx + 1, fy + 1, 36, FIELD_H - 2, 4, this.c(col.get()));
            Gfx.outline(g, sx, fy, 38, FIELD_H, 5, this.c(FIELD_LINE));
            this.hit(sx, fy, 38, FIELD_H, clipTop, clipBottom, (cx, cy, btn) -> {
                if (btn == 1) {
                    col.reset();
                } else if (!OPEN_COLORS.remove(col)) {
                    OPEN_COLORS.add(col);
                }
                DIHClient.config().markDirty();
                this.sound();
            });
            if (OPEN_COLORS.contains(col)) {
                this.drawPicker(g, col, right - 4 - FIELD_W, y + ROW_H, FIELD_W, clipTop, clipBottom);
            }
        } else if (s instanceof IdListSetting ids) {
            this.field(g, fx, fy, false, "Edit · " + ids.get().size(), DIM, true);
            this.hit(fx, fy, FIELD_W, FIELD_H, clipTop, clipBottom, (cx, cy, btn) -> {
                this.sound();
                if (this.field_22787 != null) {
                    this.field_22787.method_1507(new TargetSelectorScreen(this, ids));
                }
            });
        } else if (s instanceof StringSetting str) {
            boolean active = this.editing == str;
            String text = str.get();
            String shown = active ? text + (System.currentTimeMillis() / 500L % 2L == 0L ? "_" : "") : (text.isEmpty() ? "empty" : text);
            while (Gfx.width(shown) > FIELD_W - 16 && shown.length() > 1) {
                shown = shown.substring(1);
            }
            this.field(g, fx, fy, active, shown, text.isEmpty() && !active ? DIMMER : active ? TEXT : DIM, false);
            this.hit(fx, fy, FIELD_W, FIELD_H, clipTop, clipBottom, (cx, cy, btn) -> {
                if (btn == 1) {
                    str.reset();
                    this.editing = null;
                } else {
                    this.editing = str;
                }
                DIHClient.config().markDirty();
            });
        } else if (s instanceof ActionSetting act) {
            float hov = Anim.get(act, "gBtn", over ? 1.0F : 0.0F, 16.0F);
            Gfx.rect(g, fx, fy, FIELD_W, FIELD_H, 5, this.c(blendAlpha(FIELD, 0x33FFFFFF, hov)));
            Gfx.outline(g, fx, fy, FIELD_W, FIELD_H, 5, this.c(blendAlpha(FIELD_LINE, 0x66FFFFFF, hov)));
            Gfx.text(g, "Run", fx + (FIELD_W - Gfx.width("Run")) / 2, fy + 6, this.c(TEXT));
            this.hit(fx, fy, FIELD_W, FIELD_H, clipTop, clipBottom, (cx, cy, btn) -> {
                this.sound();
                act.run();
            });
        }
    }

    /** The rounded value field of the right column. */
    private void field(class_332 g, int x, int y, boolean active, String text, int color, boolean chevron) {
        Gfx.rect(g, x, y, FIELD_W, FIELD_H, 5, this.c(active ? 0x26FFFFFF : FIELD));
        Gfx.outline(g, x, y, FIELD_W, FIELD_H, 5, this.c(active ? 0x77FFFFFF : FIELD_LINE));
        if (!text.isEmpty()) {
            Gfx.text(g, Gfx.trim(text, FIELD_W - (chevron ? 28 : 16)), x + 8, y + 6, this.c(color));
        }
        if (chevron) {
            Gfx.text(g, "⌄", x + FIELD_W - 14, y + 4, this.c(DIMMER));
        }
    }

    private void drawPicker(class_332 g, ColorSetting col, int x, int y, int w, int clipTop, int clipBottom) {
        int value = col.get();
        float[] hsb = Color.RGBtoHSB(value >> 16 & 0xFF, value >> 8 & 0xFF, value & 0xFF, null);
        int alpha = value >>> 24 & 0xFF;
        int boxW = w - 14;
        int boxH = 46;
        int py = y + 4;
        int pure = 0xFF000000 | Color.HSBtoRGB(hsb[0], 1.0F, 1.0F);
        for (int i = 0; i < boxW; i += 2) {
            int top = blendColor(0xFFFFFFFF, pure, (float) i / (boxW - 1));
            g.method_25296(x + i, py, x + Math.min(boxW, i + 2), py + boxH, this.c(top), this.c(0xFF000000));
        }
        Gfx.outline(g, x + (int) (hsb[1] * (boxW - 1)) - 2, py + (int) ((1.0F - hsb[2]) * (boxH - 1)) - 2, 5, 5, 1, 0xFFFFFFFF);
        int hx = x + boxW + 4;
        for (int i = 0; i < 6; i++) {
            int a = 0xFF000000 | Color.HSBtoRGB(i / 6.0F, 1.0F, 1.0F);
            int b = 0xFF000000 | Color.HSBtoRGB((i + 1) / 6.0F, 1.0F, 1.0F);
            g.method_25296(hx, py + i * boxH / 6, hx + 10, py + (i + 1) * boxH / 6, this.c(a), this.c(b));
        }
        g.method_25294(hx - 1, py + (int) (hsb[0] * (boxH - 1)), hx + 11, py + (int) (hsb[0] * (boxH - 1)) + 1, 0xFFFFFFFF);
        int ay = py + boxH + 5;
        Gfx.hGradient(g, x, ay, w, 7, this.c(value & 0xFFFFFF), this.c(value | 0xFF000000));
        int ax = x + (int) (alpha / 255.0F * (w - 1));
        g.method_25294(ax, ay - 1, ax + 1, ay + 8, 0xFFFFFFFF);
        Gfx.text(g, col.displayValue() + String.format(Locale.ROOT, "  %d%%", Math.round(alpha / 2.55F)), x, ay + 11, this.c(DIMMER), 0.7F);

        final float hue = hsb[0];
        this.hit(x, py, boxW, boxH, clipTop, clipBottom, (cx, cy, b) -> this.dragPicker(col, (px, pyy) -> {
            float sat = (float) Math.max(0.0, Math.min(1.0, (px - x) / (boxW - 1)));
            float bri = (float) Math.max(0.0, Math.min(1.0, 1.0 - (pyy - py) / (boxH - 1)));
            float[] now = Color.RGBtoHSB(col.get() >> 16 & 0xFF, col.get() >> 8 & 0xFF, col.get() & 0xFF, null);
            float h = now[1] > 0.001F && now[2] > 0.001F ? now[0] : hue;
            col.set(col.get() & 0xFF000000 | Color.HSBtoRGB(h, sat, bri) & 0xFFFFFF);
        }, cx, cy));
        this.hit(hx - 1, py, 12, boxH, clipTop, clipBottom, (cx, cy, b) -> this.dragPicker(col, (px, pyy) -> {
            float h = (float) Math.max(0.0, Math.min(0.999, (pyy - py) / (boxH - 1)));
            float[] now = Color.RGBtoHSB(col.get() >> 16 & 0xFF, col.get() >> 8 & 0xFF, col.get() & 0xFF, null);
            col.set(col.get() & 0xFF000000 | Color.HSBtoRGB(h, Math.max(now[1], 0.01F), Math.max(now[2], 0.01F)) & 0xFFFFFF);
        }, cx, cy));
        this.hit(x, ay - 2, w, 11, clipTop, clipBottom, (cx, cy, b) -> this.dragPicker(col, (px, pyy) ->
            col.setChannel(3, (int) Math.round(Math.max(0.0, Math.min(1.0, (px - x) / (w - 1))) * 255.0)), cx, cy));
    }

    private void dragPicker(ColorSetting col, Drag d, double cx, double cy) {
        this.drag = (px, py) -> {
            d.drag(px, py);
            DIHClient.config().markDirty();
        };
        this.drag.drag(cx, cy);
    }

    private void drawBottom(class_332 g) {
        int y = this.sh - 20;
        Gfx.text(g, "Left click: switch · Right click: settings · Middle click: bind · Type to search", 14.0F, y + 5.0F, this.c(DIMMER), 0.75F);
        String label = "✎ HUD Editor";
        int w = Gfx.width(label) + 16;
        int x = this.sw - 12 - w;
        boolean over = Gfx.inside(this.mouseX, this.mouseY, x, y, w, 16);
        float a = Anim.get(this, "gHud", over ? 1.0F : 0.0F, 16.0F);
        Gfx.rect(g, x, y, w, 16, 8, this.c(blendAlpha(0x14FFFFFF, 0x30FFFFFF, a)));
        Gfx.outline(g, x, y, w, 16, 8, this.c(blendAlpha(0x26FFFFFF, 0x55FFFFFF, a)));
        Gfx.text(g, label, x + 8, y + 4, this.c(over ? TEXT : DIM));
        this.hit(x, y, w, 16, (cx, cy, b) -> {
            this.sound();
            if (this.field_22787 != null) {
                this.field_22787.method_1507(new HudEditorScreen(this));
            }
        });
    }

    private void drawTooltip(class_332 g) {
        if (this.tooltip == null || this.tooltip.isEmpty() || this.drag != null) {
            return;
        }
        List<String> lines = wrap(this.tooltip, 190);
        int w = 0;
        for (String line : lines) {
            w = Math.max(w, Gfx.width(line));
        }
        w += 14;
        int h = lines.size() * 10 + 10;
        int x = (int) this.mouseX + 12;
        int y = (int) this.mouseY + 12;
        if (x + w > this.sw - 4) {
            x = (int) this.mouseX - w - 8;
        }
        if (y + h > this.sh - 4) {
            y = (int) this.mouseY - h - 6;
        }
        Gfx.rect(g, x, y, w, h, 6, 0xDC101014);
        Gfx.outline(g, x, y, w, h, 6, 0x44FFFFFF);
        int ty = y + 5;
        for (String line : lines) {
            Gfx.text(g, line, x + 7, ty, TEXT);
            ty += 10;
        }
    }

    private static List<String> wrap(String text, int max) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        for (String word : text.split(" ")) {
            String next = cur.length() == 0 ? word : cur + " " + word;
            if (Gfx.width(next) > max && cur.length() > 0) {
                out.add(cur.toString());
                cur = new StringBuilder(word);
            } else {
                cur = new StringBuilder(next);
            }
        }
        if (cur.length() > 0) {
            out.add(cur.toString());
        }
        return out;
    }

    // ---------------------------------------------------------------- colour maths

    private static int blendAlpha(int a, int b, float t) {
        return blendColor(a, b, t);
    }

    private static int blendColor(int a, int b, float t) {
        t = Math.max(0.0F, Math.min(1.0F, t));
        int out = 0;
        for (int shift = 24; shift >= 0; shift -= 8) {
            int ca = a >>> shift & 0xFF;
            int cb = b >>> shift & 0xFF;
            out |= (int) (ca + (cb - ca) * t) << shift;
        }
        return out;
    }

    // ---------------------------------------------------------------- input

    private Hit hitAt(double x, double y) {
        for (int i = this.hits.size() - 1; i >= 0; i--) {
            if (this.hits.get(i).contains(x, y)) {
                return this.hits.get(i);
            }
        }
        return null;
    }

    public boolean method_25402(class_11909 click, boolean doubled) {
        if (ConfigDock.click(click.comp_4798(), click.comp_4799(), doubled, this.field_22789, this.field_22790)) {
            return true;
        }
        float scale = scale();
        double x = click.comp_4798() / scale;
        double y = click.comp_4799() / scale;
        int button = click.method_74245();
        if (this.binding) {
            this.binding = false;
            return true;
        }
        Hit hit = this.hitAt(x, y);
        if (hit == null || this.editing == null) {
            this.editing = null;
        }
        this.searchFocused = false;
        if (hit != null) {
            hit.action().click(x, y, button);
        }
        return true;
    }

    public boolean method_25403(class_11909 click, double dx, double dy) {
        if (ConfigDock.covers(click.comp_4798(), click.comp_4799(), this.field_22789, this.field_22790)) {
            return true;
        }
        if (this.drag != null) {
            float scale = scale();
            this.drag.drag(click.comp_4798() / scale, click.comp_4799() / scale);
            return true;
        }
        return super.method_25403(click, dx, dy);
    }

    public boolean method_25406(class_11909 click) {
        if (ConfigDock.swallowRelease()) {
            return true;
        }
        if (this.drag != null) {
            DIHClient.config().markDirty();
        }
        this.drag = null;
        return super.method_25406(click);
    }

    public boolean method_25401(double x, double y, double horizontal, double vertical) {
        if (ConfigDock.scroll(x, y, vertical, this.field_22789, this.field_22790)) {
            return true;
        }
        float scale = scale();
        if (x / scale < this.paneX - 12) {
            this.listScrollTarget -= (float) (vertical * 28.0);
        } else {
            this.paneScrollTarget -= (float) (vertical * 28.0);
        }
        return true;
    }

    public boolean method_25404(class_11908 key) {
        if (ConfigDock.key(key.comp_4795(), key.comp_4797())) {
            return true;
        }
        int code = key.comp_4795();
        if (this.binding) {
            if (selected != null) {
                selected.setKeybind(code != 256 && code != 261 && code != 259 ? code : -1);
            }
            this.binding = false;
            this.sound();
            return true;
        }
        if (this.editing != null) {
            if (code == 259) {
                String text = this.editing.get();
                if (!text.isEmpty()) {
                    this.editing.set(text.substring(0, text.length() - 1));
                }
            } else if (code == 257 || code == 335 || code == 256) {
                this.editing = null;
            } else if (code == 86 && (key.comp_4797() & 2) != 0 && this.field_22787 != null) {
                this.editing.set(this.editing.get() + this.field_22787.field_1774.method_1460());
            }
            DIHClient.config().markDirty();
            return true;
        }
        if (code == 259 && !this.search.isEmpty()) {
            this.search = this.search.substring(0, this.search.length() - 1);
            return true;
        }
        if (code == 256 && (!this.search.isEmpty() || this.searchFocused)) {
            this.search = "";
            this.searchFocused = false;
            return true;
        }
        if (this.search.isEmpty() && !this.searchFocused && DIHClient.clickGuiKey() != null && DIHClient.clickGuiKey().method_1417(key)) {
            this.method_25419();
            return true;
        }
        return super.method_25404(key);
    }

    public boolean method_25400(class_11905 chars) {
        if (ConfigDock.typing() && chars.method_74227()) {
            ConfigDock.typed(chars.method_74226());
            return true;
        }
        if (!chars.method_74227()) {
            return false;
        }
        String text = chars.method_74226();
        if (this.editing != null) {
            if (this.editing.get().length() < this.editing.maxLength()) {
                this.editing.set(this.editing.get() + text);
            }
            return true;
        }
        if (this.binding) {
            return true;
        }
        if (this.search.length() < 32) {
            this.search += text;
        }
        return true;
    }

    // ---------------------------------------------------------------- parts

    @FunctionalInterface
    private interface ClickAction {
        void click(double x, double y, int button);
    }

    @FunctionalInterface
    private interface Drag {
        void drag(double x, double y);
    }

    private record Hit(int x, int y, int w, int h, ClickAction action) {
        boolean contains(double px, double py) {
            return px >= this.x && px < this.x + this.w && py >= this.y && py < this.y + this.h;
        }
    }

    /** A line of the right column: a setting, or (setting == null) the keybind / toast rows of the module itself. */
    private record Row(Setting<?> setting, String label, int height) {
    }
}
