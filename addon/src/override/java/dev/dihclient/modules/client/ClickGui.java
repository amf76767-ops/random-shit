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
    public final EnumSetting<Layout> layout = this.mode("Style", "The ClickGUI style.", Layout.METEOR).visibleWhen(() -> false);
    public final ColorSetting accent = this.color("Accent", "Primary accent colour of GUI and HUD.", -1754827).legacy("accent");
    public final ColorSetting accent2 = this.color("Accent 2", "Second colour for gradients.", -30147);
    public final EnumSetting<Theme.ColorMode> colorMode = this.mode(
            "Color Mode", "Static accent, flowing two-colour gradient or rainbow.", Theme.ColorMode.STATIC);
    public final DoubleSetting colorSpeed = this.dbl("Color Speed", "Speed of gradient / rainbow animation.", 1.0, 0.1, 4.0, 0.1)
            .visibleWhen(() -> this.colorMode.get() != Theme.ColorMode.STATIC);
    public final DoubleSetting scale = this.dbl("GUI Scale", "Size of the ClickGUI window.", 1.0, 0.6, 1.5, 0.05);
    public final BoolSetting animations = this.bool("Animations", "Smooth transitions, switches and scrolling.", true).onChange(this::applyAnim);
    public final BoolSetting blur = this.bool("Blur", "Blurs the game behind the GUI .", true);
    public final BoolSetting smoothFont = this.bool("Smooth Font", "Draws the text of the GUI and HUD in a smooth font (Inter) instead of the pixel font of the game.", true);
    public final BoolSetting smoothCorners = this.bool("Smooth Corners", "Draws rounded corners smooth. Turn it off if the game gets slower with the GUI or HUD on screen.", true);
    public final BoolSetting descriptions = this.bool("Descriptions", "Shows a module's description on its card.", true);
    public final BoolSetting clickSound = this.bool("Click Sound", "Plays a click when toggling.", true).legacy("gui.clickSound");
    public final EnumSetting<SoundSet> soundSet = this.mode(
            "Sound Set", "Glass: soft glass taps and a rising / falling chime for on / off · Soft: the first set (a tick and two plain notes).", SoundSet.GLASS)
            .visibleWhen(this.clickSound::get);
    public final DoubleSetting clickPitch = this.dbl("Click Pitch", "Pitch of the click sound.", 1.0, 0.5, 2.0, 0.05)
            .legacy("gui.clickPitch")
            .visibleWhen(this.clickSound::get);

    public ClickGui() {
        super("ClickGUI", Category.CLIENT, "Colours and look of the whole client. Open the GUI with Right Shift (rebind under Controls).");
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
        METEOR,
        GLASS;
    }

    public enum SoundSet {
        GLASS,
        SOFT;
    }
}
