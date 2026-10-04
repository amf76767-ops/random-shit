package dev.dihclient.port.chunks;

import dev.dihclient.util.Notifications;
import net.minecraft.class_310;
import net.minecraft.class_3417;

/**
 * Replaces the original challenge toast for the two chunk modules: a DIH warning toast (which Discord Alarm forwards) and,
 * if wanted, the same bell sound the original toast played. Game thread only.
 */
final class ChunkAlerts {
    private ChunkAlerts() {
    }

    static void warn(String module, String text, boolean sound) {
        Notifications.warn(module, text);
        if (sound) {
            class_310 mc = class_310.method_1551();
            if (mc.field_1724 != null) {
                mc.field_1724.method_5783(class_3417.field_14793.comp_349(), 1.0F, 1.0F);
            }
        }
    }
}
