package dev.dihclient.port.trident;

import it.unimi.dsi.fastutil.objects.Object2IntMap.Entry;
import net.minecraft.class_1657;
import net.minecraft.class_1799;
import net.minecraft.class_1802;
import net.minecraft.class_1887;
import net.minecraft.class_1893;
import net.minecraft.class_2338;
import net.minecraft.class_238;
import net.minecraft.class_2680;
import net.minecraft.class_310;
import net.minecraft.class_3612;
import net.minecraft.class_6880;

/**
 * Ported from Anubis Client 0.9.8 (GPL-3.0).
 * Client-side checks Riptide uses so it only fires where a server accepts a riptide: in water or rain.
 */
public final class GrimRiptide {
    private GrimRiptide() {
    }

    /** True when the player is in or touching water, or it is raining. Game thread only. */
    public static boolean clientAccepts() {
        class_310 mc = class_310.method_1551();
        class_1657 player = mc.field_1724;
        if (player == null || mc.field_1687 == null || !mc.method_18854()) {
            return false;
        }
        if (player.method_5721() || mc.field_1687.method_8419()) {
            return true;
        }
        // standing next to water also counts for the server (box grown by 0.1)
        class_238 box = player.method_5829().method_1014(0.1);
        class_2338 min = class_2338.method_49637(box.field_1323, box.field_1322, box.field_1321);
        class_2338 max = class_2338.method_49637(box.field_1320, box.field_1325, box.field_1324);
        for (class_2338 pos : class_2338.method_10097(min, max)) {
            class_2680 state = mc.field_1687.method_8320(pos);
            if (state.method_26227().method_39360(class_3612.field_15910) || state.method_26227().method_39360(class_3612.field_15909)) {
                return true;
            }
        }
        return false;
    }

    /** A trident with Riptide on it. */
    public static boolean isRiptide(class_1799 stack) {
        if (!stack.method_31574(class_1802.field_8547)) {
            return false;
        }
        for (Entry<class_6880<class_1887>> entry : stack.method_58657().method_57539()) {
            if (entry.getKey().method_40225(class_1893.field_9104) && entry.getIntValue() > 0) {
                return true;
            }
        }
        return false;
    }
}
