package dev.dihclient.glue;

import dev.dihclient.DIHClient;
import dev.dihclient.module.Module;
import dev.dihclient.module.ModuleManager;
import dev.dihclient.modules.client.ClickGui;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.class_1109;
import net.minecraft.class_1113;
import net.minecraft.class_2960;
import net.minecraft.class_310;
import net.minecraft.class_3414;
import net.minecraft.class_6880;

public final class GuiSounds {
    private static final Map<String, class_6880> ENTRIES = new HashMap<>();
    private static Method soundOf;
    private static Method holderOf;
    private static boolean broken;

    private GuiSounds() {
    }

    public static class_1109 click(class_6880 vanilla, float pitch) {
        class_6880 own = entry(event("click"));
        return class_1109.method_47978(own != null ? own : vanilla, pitch);
    }

    public static void toggle(Module module) {
        try {
            ClickGui gui = ModuleManager.of(ClickGui.class);
            if (gui == null || !gui.clickSound.get() || class_310.method_1551().method_1483() == null) {
                return;
            }
            class_6880 own = entry(event(module.isEnabled() ? "on" : "off"));
            if (own != null) {
                class_310.method_1551().method_1483().method_4873((class_1113) class_1109.method_47978(own, gui.clickPitch.getFloat()));
            }
        } catch (Throwable t) {
            broken = true;
        }
    }

    private static String event(String kind) {
        try {
            ClickGui gui = ModuleManager.of(ClickGui.class);
            if (gui == null || gui.soundSet.get() == ClickGui.SoundSet.GLASS) {
                return "ui.glass_" + kind;
            }
        } catch (Throwable ignored) {

        }
        return switch (kind) {
            case "on" -> "ui.toggle_on";
            case "off" -> "ui.toggle_off";
            default -> "ui.click";
        };
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static class_6880 entry(String event) {
        if (broken) {
            return null;
        }
        class_6880 cached = ENTRIES.get(event);
        if (cached != null) {
            return cached;
        }
        try {
            if (soundOf == null) {
                for (Method m : class_3414.class.getMethods()) {
                    if (Modifier.isStatic(m.getModifiers()) && m.getParameterCount() == 1 && m.getParameterTypes()[0] == class_2960.class
                            && m.getReturnType() == class_3414.class) {
                        soundOf = m;
                        break;
                    }
                }
                for (Method m : class_6880.class.getMethods()) {
                    if (Modifier.isStatic(m.getModifiers()) && m.getParameterCount() == 1 && m.getParameterTypes()[0] == Object.class
                            && m.getReturnType() == class_6880.class) {
                        holderOf = m;
                        break;
                    }
                }
            }
            class_3414 sound = (class_3414) soundOf.invoke(null, class_2960.method_60655("dihclient", event));
            class_6880 holder = (class_6880) holderOf.invoke(null, sound);
            ENTRIES.put(event, holder);
            return holder;
        } catch (Throwable t) {
            broken = true;
            DIHClient.LOG.warn("[DIHClient] own GUI sounds are not available, using the vanilla click", t);
            return null;
        }
    }
}
