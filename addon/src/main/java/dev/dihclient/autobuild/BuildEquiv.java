package dev.dihclient.autobuild;

import dev.dihclient.util.RegistryUtil;
import net.minecraft.class_2248;
import net.minecraft.class_2680;

/**
 * Blocks that the game changes by itself must not count as wrong. A planned dirt block that has turned into grass is
 * done; "Fix Wrong Blocks" would otherwise mine it and put dirt back, over and over. Patched into
 * {@code BuildRuntime.statusOf}: it is asked before the block is judged.
 */
public final class BuildEquiv {
    private BuildEquiv() {
    }

    /** Called from the patched {@code statusOf}: the world has a different block, but one the planned block turns into by itself. */
    public static boolean accepts(class_2680 world, class_2680 planned) {
        try {
            class_2248 w = world.method_26204();
            class_2248 p = planned.method_26204();
            if (w == p) {
                return false;
            }
            return NaturalChanges.accepts(strip(RegistryUtil.blockId(planned)), strip(RegistryUtil.blockId(world)));
        } catch (Throwable t) {
            return false;
        }
    }

    private static String strip(String id) {
        int colon = id.indexOf(':');
        return colon >= 0 ? id.substring(colon + 1) : id;
    }
}
