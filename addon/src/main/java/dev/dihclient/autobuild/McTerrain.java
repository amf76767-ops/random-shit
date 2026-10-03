package dev.dihclient.autobuild;

import dev.dihclient.nav.Nav;
import dev.dihclient.util.RegistryUtil;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.class_2680;
import net.minecraft.class_310;
import net.minecraft.class_2338.class_2339;

/** The loaded world as the path search sees it. Answers are cached for one planning round. */
final class McTerrain implements Nav.Terrain {
    private static final String[] HAZARD = {"lava", "fire", "cactus", "magma", "campfire", "berry_bush", "wither_rose", "powder_snow",
            "cobweb", "pointed_dripstone", "bubble_column", "end_portal", "nether_portal"};
    private static final String[] NO_FLOOR = {"fence", "_wall", "iron_bars", "glass_pane"};
    private static final byte PASS = 1, SUPPORT = 2, HURTS = 4, KNOWN = 8;

    private final class_310 mc = class_310.method_1551();
    private final Map<Long, Byte> cache = new HashMap<>();
    private final class_2339 pos = new class_2339();

    void reset() {
        this.cache.clear();
    }

    private byte info(int x, int y, int z) {
        long k = Nav.key(x, y, z);
        Byte have = this.cache.get(k);
        if (have != null) {
            return have;
        }
        byte out = KNOWN;
        try {
            class_2680 state = this.mc.field_1687.method_8320(this.pos.method_10103(x, y, z));
            if (state.method_26215()) {
                out |= PASS;
            } else {
                String id = RegistryUtil.blockId(state);
                boolean hurts = false;
                for (String h : HAZARD) {
                    if (id.contains(h)) {
                        hurts = true;
                    }
                }
                boolean solid = !state.method_26218(this.mc.field_1687, this.pos).method_1110();
                if (hurts) {
                    out |= HURTS;
                } else if (!solid) {
                    out |= PASS;
                } else {
                    boolean floor = true;
                    for (String n : NO_FLOOR) {
                        if (id.contains(n)) {
                            floor = false;
                        }
                    }
                    if (floor) {
                        out |= SUPPORT;
                    }
                }
            }
        } catch (Throwable t) {
            out = (byte) (KNOWN | HURTS); // unknown: keep away
        }
        this.cache.put(k, out);
        return out;
    }

    @Override
    public boolean passable(int x, int y, int z) {
        return (this.info(x, y, z) & PASS) != 0;
    }

    @Override
    public boolean support(int x, int y, int z) {
        return (this.info(x, y, z) & SUPPORT) != 0;
    }

    @Override
    public boolean hazard(int x, int y, int z) {
        return (this.info(x, y, z) & HURTS) != 0;
    }
}
