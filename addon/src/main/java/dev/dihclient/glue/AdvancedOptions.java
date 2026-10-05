package dev.dihclient.glue;

import dev.dihclient.gui.SchematicBrowserScreen;
import dev.dihclient.module.Module;
import dev.dihclient.setting.Setting;
import java.lang.reflect.Field;
import java.util.Set;
import java.util.function.BooleanSupplier;
import net.minecraft.class_310;

public final class AdvancedOptions {

    public static final Set<String> AUTOBUILD_MAIN = Set.of(
            "Source", "Mode", "Order", "From Layer", "To Layer", "Reach", "Walk", "Smart Path", "Plan First", "Hotbar Refill", "Guard", "Render",
            "Progress HUD", "Team Code", "Create Team", "Join Team", "Leave Team", "Open Browser", "More Options", "Plan Route", "Pause / Resume", "Resume Last Build", "Stop");

    private AdvancedOptions() {
    }

    public static boolean inBrowser() {
        return class_310.method_1551().field_1755 instanceof SchematicBrowserScreen;
    }

    public static void apply(Module module, Set<String> main) throws ReflectiveOperationException {
        Field visibility = Setting.class.getDeclaredField("visibility");
        visibility.setAccessible(true);
        for (Setting<?> s : module.settings()) {
            if (main.contains(s.name())) {
                continue;
            }
            BooleanSupplier original = (BooleanSupplier) visibility.get(s);
            s.visibleWhen(() -> inBrowser() && original.getAsBoolean());
        }
    }
}
