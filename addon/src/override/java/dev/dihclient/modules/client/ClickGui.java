package dev.dihclient.modules.client;

import dev.dihclient.config.GuiState;
import dev.dihclient.gui.MeteorGuiScreen;
import dev.dihclient.gui.theme.Anim;
import dev.dihclient.gui.theme.Theme;
import dev.dihclient.module.Category;
import dev.dihclient.module.Module;
import dev.dihclient.setting.BoolSetting;
import dev.dihclient.setting.ColorSetting;
import dev.dihclient.setting.DoubleSetting;
import dev.dihclient.setting.EnumSetting;
import dev.dihclient.util.BugReport;

public class ClickGui extends Module {
    public final EnumSetting<Layout> layout = this.mode(
            "Layout", "Modern: single window with sidebar. Meteor: one draggable, collapsible panel per category (like Meteor Client).", Layout.MODERN)
            .onChange(MeteorGuiScreen::onLayoutChanged);
    public final EnumSetting<Look> look = this.mode(
            "Theme",
            "Classic: the original look. Neon Circuit: dark with cyan / lime glow. Frost Glass: light, frosted and rounded. Pixel Forge: chunky stone blocks"
                    + " with bevelled edges. Glass: see-through dark panels over the blurred game with white text. Applies to both layouts, Modern and Meteor"
                    + " (Accent below is only used by Classic).",
            Look.CLASSIC);
    public final ColorSetting accent = this.color("Accent", "Primary accent colour of GUI and HUD.", -1754827).legacy("accent");
    public final ColorSetting accent2 = this.color("Accent 2", "Second colour for gradients.", -30147);
    public final EnumSetting<Theme.ColorMode> colorMode = this.mode(
            "Color Mode", "Static accent, flowing two-colour gradient or rainbow.", Theme.ColorMode.STATIC);
    public final DoubleSetting colorSpeed = this.dbl("Color Speed", "Speed of gradient / rainbow animation.", 1.0, 0.1, 4.0, 0.1)
            .visibleWhen(() -> this.colorMode.get() != Theme.ColorMode.STATIC);
    public final DoubleSetting scale = this.dbl("GUI Scale", "Size of the ClickGUI window.", 1.0, 0.6, 1.5, 0.05);
    public final BoolSetting animations = this.bool("Animations", "Smooth transitions, switches and scrolling.", true).onChange(this::applyAnim);
    public final BoolSetting blur = this.bool("Blur", "Blurs the game behind the GUI (the Glass theme looks best with it on).", true);
    public final BoolSetting descriptions = this.bool("Descriptions", "Shows a module's description on its card.", true);
    public final BoolSetting clickSound = this.bool("Click Sound", "Plays a click when toggling.", true).legacy("gui.clickSound");
    public final EnumSetting<SoundSet> soundSet = this.mode(
            "Sound Set", "Glass: soft glass taps and a rising / falling chime for on / off · Soft: the first set (a tick and two plain notes).", SoundSet.GLASS)
            .visibleWhen(this.clickSound::get);
    public final DoubleSetting clickPitch = this.dbl("Click Pitch", "Pitch of the click sound.", 1.0, 0.5, 2.0, 0.05)
            .legacy("gui.clickPitch")
            .visibleWhen(this.clickSound::get);

    public ClickGui() {
        super("ClickGUI", Category.CLIENT, "Look & feel and colour theme of the whole client. Open the GUI with Right Shift (rebind under Controls).");
        this.action("Reset Window", "Centers the ClickGUI window again and resets the Meteor panels.", () -> {
            GuiState.resetLayout();
            MeteorGuiScreen.resetLayout();
        });
        this.action(
                "Bug Report",
                "Writes a file with the status of all modules, changed settings, last messages and errors (for bug fixing). The path is copied.",
                BugReport::writeAndTell);
    }

    private void applyAnim() {
        Anim.setEnabled(this.animations.get());
    }

    @Override
    public boolean isToggleable() {
        return false;
    }

    public enum Layout {
        MODERN,
        METEOR;
    }

    public enum Look {
        CLASSIC,
        NEON,
        FROST,
        PIXEL,
        GLASS;
    }

    public enum SoundSet {
        GLASS,
        SOFT;
    }
}
