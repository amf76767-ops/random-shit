package dev.dihclient.glue;

import dev.dihclient.gui.SchematicBrowserScreen;
import dev.dihclient.module.Module;
import dev.dihclient.setting.Setting;
import java.lang.reflect.Field;
import java.util.Set;
import java.util.function.BooleanSupplier;
import net.minecraft.class_310;

/**
 * Keeps the module menu of AutoBuild short. Only the main options stay there; every other option (and button) is hidden in
 * the menu and shown in the OPTIONS tab of the schematic browser instead, which is the current screen while it is open.
 * The options keep their saved values, only their place changes.
 */
public final class AdvancedOptions {
    /** Names that stay in the module menu. */
    public static final Set<String> AUTOBUILD_MAIN = Set.of(
            "Source", "Mode", "Order", "From Layer", "To Layer", "Reach", "Walk", "Smart Path", "Plan First", "Hotbar Refill", "Render",
            "Progress HUD", "Open Browser", "More Options", "Plan Route", "Pause / Resume", "Resume Last Build", "Stop");

    private AdvancedOptions() {
    }

    /** True while the schematic browser is open. */
    public static boolean inBrowser() {
        return class_310.method_1551().field_1755 instanceof SchematicBrowserScreen;
    }

    /** Hides every setting of the module whose name is not in {@code main} unless the browser is open. */
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
